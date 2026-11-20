package com.cardpool.backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;

/**
 * Stores application data (rules, verdicts, reports) as pretty-printed JSON
 * files under a data directory ({@code cardpool.data-dir}, default
 * {@code data} relative to the working directory). Writes are atomic so a
 * crash never leaves a half-written file.
 */
@Slf4j
@Component
public class JsonFileStore {

    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Autowired
    public JsonFileStore(@Value("${cardpool.data-dir:data}") String dataDir) {
        this(Path.of(dataDir));
        log.info("Card pool data directory: {}", root.toAbsolutePath());
    }

    public JsonFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public synchronized <T> Optional<T> read(String relativePath, Class<T> type) {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readValue(file.toFile(), type));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    public synchronized void write(String relativePath, Object value) {
        Path file = resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(tmp.toFile(), value);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    public synchronized boolean delete(String relativePath) {
        try {
            return Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + relativePath, e);
        }
    }

    /** Lists the file names (without directory) ending with {@code suffix}. */
    public synchronized List<String> list(String relativeDir, String suffix) {
        Path dir = resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + dir, e);
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes data directory: " + relativePath);
        }
        return resolved;
    }
}
