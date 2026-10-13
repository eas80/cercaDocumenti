package com.example.documentstore.repository.disk;

import com.example.documentstore.repository.SettingsStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Stores each settings blob as {@code settings/<key>.json} under the document storage directory. */
@Component
@ConditionalOnProperty(prefix = "documentstore.storage", name = "type", havingValue = "disk", matchIfMissing = true)
public class DiskSettingsStore implements SettingsStore {

    private final Path settingsDir;

    public DiskSettingsStore(@Value("${documentstore.storage.disk.directory:./data/documents}") String directory) {
        this.settingsDir = Path.of(directory).resolve("settings");
        try {
            Files.createDirectories(settingsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create settings directory " + settingsDir, e);
        }
    }

    @Override
    public Optional<byte[]> read(String key) {
        Path path = path(key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    @Override
    public void write(String key, byte[] content) {
        Path target = path(key);
        Path tmp = null;
        try {
            tmp = Files.createTempFile(settingsDir, target.getFileName().toString(), ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort cleanup, the move already succeeded or failed above
                }
            }
        }
    }

    private Path path(String key) {
        return settingsDir.resolve(key + ".json");
    }
}
