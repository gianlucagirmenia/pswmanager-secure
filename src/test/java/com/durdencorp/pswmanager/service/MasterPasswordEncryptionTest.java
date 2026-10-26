package com.durdencorp.pswmanager.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MasterPasswordEncryptionTest {
    private static final String MASTER = "correct horse battery staple";
    private MasterPasswordEncryption encryption;

    @BeforeEach
    void setUp() {
        AppConfigService config = mock(AppConfigService.class);
        when(config.isFirstAccess()).thenReturn(false);
        when(config.verifyMasterPassword(MASTER)).thenReturn(true);
        when(config.getOrCreateEncryptionSalt()).thenReturn(new byte[16]);
        encryption = new MasterPasswordEncryption(new SessionService(), config);
        assertTrue(encryption.setAndVerifyMasterPassword(MASTER));
    }

    @Test
    void gcmRoundTripAndRandomNonce() {
        String first = encryption.encrypt("segreto");
        String second = encryption.encrypt("segreto");
        assertTrue(first.startsWith("v2$"));
        assertNotEquals(first, second);
        assertEquals("segreto", encryption.decrypt(first));
    }

    @Test
    void gcmRejectsTampering() {
        String value = encryption.encrypt("segreto");
        byte[] bytes = Base64.getDecoder().decode(value.substring(3));
        bytes[bytes.length - 1] ^= 1;
        String tampered = "v2$" + Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalArgumentException.class, () -> encryption.decrypt(tampered));
    }

    @Test
    void readsLegacyCbcRecords() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(MASTER.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec legacyKey = new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
        byte[] iv = new byte[16];
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal("vecchio-segreto".getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        assertEquals("vecchio-segreto", encryption.decrypt(Base64.getEncoder().encodeToString(combined)));
    }
}
