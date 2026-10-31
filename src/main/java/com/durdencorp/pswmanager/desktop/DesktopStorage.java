package com.durdencorp.pswmanager.desktop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Owns the desktop vault directory and its process lock. */
public final class DesktopStorage implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    public static Path defaultDirectory() {
        String override = System.getProperty("pswmanager.data-dir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String local = System.getenv("LOCALAPPDATA");
        return local != null && !local.isBlank() ? Path.of(local, "PswManager")
                : Path.of(System.getProperty("user.home"), ".pswmanager");
    }

    public DesktopStorage(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        channel = FileChannel.open(this.directory.resolve("desktop.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Password123 è già aperto per questo archivio.", e);
        }
        if (acquired == null) {
            channel.close();
            throw new IOException("Password123 è già aperto per questo archivio.");
        }
        lock = acquired;
    }

    public Path directory() { return directory; }
    public Path database() { return directory.resolve("passwords.db"); }

    /** SQLite creates a consistent snapshot, including committed WAL transactions. */
    public void importDatabase(Path source) throws IOException, SQLException {
        if (Files.exists(database())) throw new IOException("Esiste già un archivio desktop: non verrà sovrascritto.");
        if (!Files.isRegularFile(source)) throw new IOException("Database di origine non trovato.");
        Path staging = directory.resolve("import-" + java.util.UUID.randomUUID() + ".db");
        try {
            String sourceUrl = "jdbc:sqlite:" + source.toAbsolutePath().toUri().toASCIIString() + "?mode=ro";
            try (var connection = DriverManager.getConnection(sourceUrl);
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA quick_check")) {
                    if (!result.next() || !"ok".equals(result.getString(1)))
                        throw new SQLException("Il database selezionato non supera il controllo di integrità.");
                }
                // Reject unrelated SQLite files before starting Hibernate against the copy.
                statement.executeQuery("SELECT config_key, config_value FROM app_config LIMIT 0").close();
                statement.executeQuery("SELECT encrypted_password FROM password_entry LIMIT 0").close();
                statement.execute("VACUUM INTO '" + staging.toString().replace("'", "''") + "'");
            }
            Files.move(staging, database());
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
