package com.durdencorp.pswmanager.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.durdencorp.pswmanager.model.AppConfig;
import com.durdencorp.pswmanager.repository.AppConfigRepository;

@Service
@Transactional
public class AppConfigService {
    private static final String MASTER_PASSWORD_HASH_KEY = "master_password_hash";
    private static final String ENCRYPTION_SALT_KEY = "encryption_salt";
    private static final int ITERATIONS = 600_000;
    private final AppConfigRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppConfigService(AppConfigRepository repository) { this.repository = repository; }

    public void saveMasterPasswordHash(String password) {
        byte[] salt = randomBytes(16);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, 256);
        String value = "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
        repository.save(new AppConfig(MASTER_PASSWORD_HASH_KEY, value));
    }

    public boolean verifyMasterPassword(String attemptedPassword) {
        Optional<AppConfig> config = repository.findByConfigKey(MASTER_PASSWORD_HASH_KEY);
        if (config.isEmpty()) return true;
        String stored = config.get().getConfigValue();
        try {
            if (stored.startsWith("pbkdf2-sha256$")) {
                String[] parts = stored.split("\\$");
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected, pbkdf2(attemptedPassword, salt, iterations, expected.length * 8));
            }
            byte[] expected = Base64.getDecoder().decode(stored);
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(attemptedPassword.getBytes(StandardCharsets.UTF_8));
            boolean valid = MessageDigest.isEqual(expected, actual);
            if (valid) saveMasterPasswordHash(attemptedPassword);
            return valid;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isFirstAccess() { return repository.findByConfigKey(MASTER_PASSWORD_HASH_KEY).isEmpty(); }

    public byte[] getOrCreateEncryptionSalt() {
        return repository.findByConfigKey(ENCRYPTION_SALT_KEY)
                .map(c -> Base64.getDecoder().decode(c.getConfigValue()))
                .orElseGet(() -> {
                    byte[] salt = randomBytes(16);
                    repository.save(new AppConfig(ENCRYPTION_SALT_KEY, Base64.getEncoder().encodeToString(salt)));
                    return salt;
                });
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations, int bits) {
        char[] chars = password.toCharArray();
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(chars, salt, iterations, bits)).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile derivare la chiave", e);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
