package com.cardpool.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uniform random sample of at most {@code maxSize} items from a stream of
 * unknown length (Algorithm R). Not thread-safe: feed it from one thread.
 */
public class ReservoirSampler<T> {

    private final List<T> items;
    private final int maxSize;
    private long seen;

    public ReservoirSampler(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must be >= 0");
        }
        this.maxSize = maxSize;
        this.items = new ArrayList<>(Math.min(maxSize, 1024));
    }

    public void add(T item) {
        seen++;
        if (items.size() < maxSize) {
            items.add(item);
            return;
        }
        // ThreadLocalRandom.current() must be called by the thread using it, never cached.
        long index = ThreadLocalRandom.current().nextLong(seen);
        if (index < maxSize) {
            items.set((int) index, item);
        }
    }

    public List<T> getItems() {
        return items;
    }
}
