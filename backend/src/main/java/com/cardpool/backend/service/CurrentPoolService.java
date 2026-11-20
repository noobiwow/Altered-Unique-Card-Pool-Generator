package com.cardpool.backend.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cardpool.backend.model.pool.SavedPool;

/**
 * Keeps the pool being worked on in {@code current-pool.json} of the data
 * directory, so it survives page changes, reloads and backend restarts. There
 * is a single current pool: generating a new one replaces it.
 */
@Service
public class CurrentPoolService {

    static final String FILE = "current-pool.json";

    private final JsonFileStore store;

    public CurrentPoolService(JsonFileStore store) {
        this.store = store;
    }

    public synchronized Optional<SavedPool> get() {
        return store.read(FILE, SavedPool.class).filter(p -> !p.references().isEmpty());
    }

    public synchronized SavedPool save(SavedPool pool) {
        if (pool == null || pool.references().isEmpty()) {
            throw new IllegalArgumentException("A pool needs at least one card");
        }
        if (pool.references().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Card references cannot be null");
        }
        SavedPool saved = pool.withUpdatedAt(Instant.now().toString());
        store.write(FILE, saved);
        return saved;
    }

    public synchronized boolean clear() {
        return store.delete(FILE);
    }
}
