package com.durdencorp.pswmanager.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

@Service
@SessionScope
public class SessionService {
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int AES_KEY_BITS = 256;
    private byte[] currentKey;
    private byte[] legacyKey;

    public boolean setMasterPassword(String masterPassword, byte[] salt) {
        if (masterPassword == null || masterPassword.isBlank() || salt == null || salt.length < 16) return false;
        char[] chars = masterPassword.toCharArray();
        try {
            KeySpec spec = new PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
            currentKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(masterPassword.getBytes(StandardCharsets.UTF_8));
            legacyKey = Arrays.copyOf(digest, 16);
            Arrays.fill(digest, (byte) 0);
            return true;
        } catch (Exception e) {
            clear();
            return false;
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    public SecretKeySpec getCurrentKey() { return currentKey == null ? null : new SecretKeySpec(currentKey, "AES"); }
    public SecretKeySpec getLegacyKey() { return legacyKey == null ? null : new SecretKeySpec(legacyKey, "AES"); }
    public boolean isMasterPasswordSet() { return currentKey != null; }

    public void clear() {
        if (currentKey != null) Arrays.fill(currentKey, (byte) 0);
        if (legacyKey != null) Arrays.fill(legacyKey, (byte) 0);
        currentKey = null;
        legacyKey = null;
    }
}
