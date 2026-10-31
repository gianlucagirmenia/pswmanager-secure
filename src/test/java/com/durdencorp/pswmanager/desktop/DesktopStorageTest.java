package com.durdencorp.pswmanager.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopStorageTest {
    @TempDir Path temporary;

    @Test void locksTheVaultAndReleasesItOnClose() throws Exception {
        Path directory = temporary.resolve("vault");
        try (var first = new DesktopStorage(directory)) {
            assertThrows(IOException.class, () -> new DesktopStorage(directory));
            assertFalse(Files.exists(first.database()));
        }
        try (var reopened = new DesktopStorage(directory)) {
            assertEquals(directory.resolve("passwords.db"), reopened.database());
        }
    }

    @Test void importsAConsistentCopyIncludingWalWithoutChangingOriginal() throws Exception {
        Path source = temporary.resolve("originale con spazi.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             var statement = connection.createStatement();
             var storage = new DesktopStorage(temporary.resolve("desktop"))) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE app_config(config_key TEXT, config_value TEXT)");
            statement.execute("CREATE TABLE password_entry(encrypted_password TEXT)");
            statement.execute("INSERT INTO app_config VALUES ('master', 'hash-esistente')");
            statement.execute("INSERT INTO password_entry VALUES ('ciphertext-esistente')");
            storage.importDatabase(source);
            try (var copied = DriverManager.getConnection("jdbc:sqlite:" + storage.database());
                 var query = copied.createStatement();
                 var result = query.executeQuery("SELECT encrypted_password FROM password_entry")) {
                assertTrue(result.next());
                assertEquals("ciphertext-esistente", result.getString(1));
            }
            try (var original = statement.executeQuery("SELECT config_value FROM app_config")) {
                assertTrue(original.next());
                assertEquals("hash-esistente", original.getString(1));
            }
        }
    }

    @Test void neverOverwritesAnExistingVault() throws Exception {
        try (var storage = new DesktopStorage(temporary.resolve("desktop"))) {
            Files.writeString(storage.database(), "original content");
            assertThrows(IOException.class, () -> storage.importDatabase(temporary.resolve("other.db")));
            assertEquals("original content", Files.readString(storage.database()));
        }
    }

    @Test void rejectsUnrelatedDatabaseWithoutLeavingAVault() throws Exception {
        Path source = temporary.resolve("unrelated.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            connection.createStatement().execute("CREATE TABLE unrelated(id INTEGER)");
        }
        try (var storage = new DesktopStorage(temporary.resolve("desktop"))) {
            assertThrows(SQLException.class, () -> storage.importDatabase(source));
            assertFalse(Files.exists(storage.database()));
        }
    }

    @Test void missingSourceDoesNotCreateAnEmptyDatabase() throws Exception {
        Path source = temporary.resolve("missing.db");
        try (var storage = new DesktopStorage(temporary.resolve("desktop"))) {
            assertThrows(IOException.class, () -> storage.importDatabase(source));
            assertFalse(Files.exists(source));
            assertFalse(Files.exists(storage.database()));
        }
    }
}
