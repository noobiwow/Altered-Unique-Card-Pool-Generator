package com.cardpool.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ReservoirSampler<T> {

    private final List<T> items;

    private final int maxSize;

    private long seen = 0;

    private final Random random = ThreadLocalRandom.current();

    public ReservoirSampler(int maxSize) {

        this.maxSize = maxSize;

        this.items = new ArrayList<>(maxSize);
    }

    public void add(T item) {

        seen++;

        if (items.size() < maxSize) {

            items.add(item);

            return;
        }

        long index = random.nextLong(seen);

        if (index < maxSize) {

            items.set((int) index, item);
        }
    }

    public List<T> getItems() {
        return items;
    }
}
