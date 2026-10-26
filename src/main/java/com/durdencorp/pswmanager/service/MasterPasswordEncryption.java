package com.durdencorp.pswmanager.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MasterPasswordEncryption {
    private static final String PREFIX = "v2$";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;
    private final SessionService sessionService;
    private final AppConfigService appConfigService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MasterPasswordEncryption(SessionService sessionService, AppConfigService appConfigService) {
        this.sessionService = sessionService;
        this.appConfigService = appConfigService;
    }

    public boolean setAndVerifyMasterPassword(String password) {
        if (password == null || password.isBlank()) return false;
        boolean firstAccess = appConfigService.isFirstAccess();
        if (!appConfigService.verifyMasterPassword(password)) return false;
        if (firstAccess) appConfigService.saveMasterPasswordHash(password);
        return sessionService.setMasterPassword(password, appConfigService.getOrCreateEncryptionSalt());
    }

    public boolean isMasterPasswordSet() { return sessionService.isMasterPasswordSet(); }

    public String encrypt(String data) {
        if (data == null) return null;
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(sessionService.getCurrentKey()), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Errore nella cifratura", e);
        }
    }

    public String decrypt(String data) {
        if (data == null) return null;
        return data.startsWith(PREFIX) ? decryptGcm(data.substring(PREFIX.length())) : decryptLegacyCbc(data);
    }

    public boolean isLegacyFormat(String data) { return data != null && !data.startsWith(PREFIX); }

    private String decryptGcm(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= GCM_NONCE_BYTES + 16) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(sessionService.getCurrentKey()),
                    new GCMParameterSpec(GCM_TAG_BITS, Arrays.copyOfRange(combined, 0, GCM_NONCE_BYTES)));
            return new String(cipher.doFinal(Arrays.copyOfRange(combined, GCM_NONCE_BYTES, combined.length)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Dati cifrati non validi o manomessi", e);
        }
    }

    private String decryptLegacyCbc(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length < 32 || (combined.length - 16) % 16 != 0) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(sessionService.getLegacyKey()),
                    new IvParameterSpec(Arrays.copyOfRange(combined, 0, 16)));
            return new String(cipher.doFinal(Arrays.copyOfRange(combined, 16, combined.length)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Dati legacy non decifrabili", e);
        }
    }

    private SecretKeySpec requireKey(SecretKeySpec key) {
        if (key == null) throw new IllegalStateException("Master password non impostata");
        return key;
    }

    public void clear() { sessionService.clear(); }
}
