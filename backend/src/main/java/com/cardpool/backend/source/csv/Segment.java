package com.cardpool.backend.source.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.cardpool.backend.model.card_elements.CardSet;

/**
 * The unique prints of one set, stored column by column in primitive arrays
 * (about 80 bytes per card instead of several KB for a {@code Card} object).
 */
final class Segment {

    static final byte FLAG_SUSPENDED = 1, FLAG_BANNED = 2, FLAG_EXCLUSIVE = 4;
    static final byte MISSING = -1;

    final String setCode;
    final CardSet cardSet;
    int size;

    // reference bytes (ASCII), row i spans [refOffset[i], refOffset[i + 1])
    byte[] refBytes;
    int[] refOffset;
    short[] family;
    byte[] faction;
    byte[] flags;
    byte[] mainCost, recallCost, forest, mountain, ocean;

    // effects of row i: entries [effStart[i], effStart[i + 1]), 4 shorts each:
    // DisplayIndex, TriggerId, ConditionId, EffectId
    int[] effStart;
    short[] effects;

    // open-addressing hash of references -> row + 1
    private int[] refTable;

    private Segment(String setCode, CardSet cardSet) {
        this.setCode = setCode;
        this.cardSet = cardSet;
    }

    static Segment load(Path setDir, Referentials ref) throws IOException {
        String setCode = setDir.getFileName().toString();
        Segment s = new Segment(setCode, ref.set(setCode));
        int[] idToRow = s.loadPrints(setDir.resolve("UniquePrints.csv"), ref);
        s.loadEffects(setDir.resolve("UniquePrintEffectFragments.csv"), idToRow);
        s.buildRefTable();
        return s;
    }

    // ------------------------------------------------------------------ loading

    private int[] loadPrints(Path file, Referentials ref) throws IOException {
        int capacity = 1 << 16;
        refBytes = new byte[capacity * 24];
        refOffset = new int[capacity + 1];
        family = new short[capacity];
        faction = new byte[capacity];
        flags = new byte[capacity];
        mainCost = new byte[capacity];
        recallCost = new byte[capacity];
        forest = new byte[capacity];
        mountain = new byte[capacity];
        ocean = new byte[capacity];
        int[] ids = new int[capacity];
        int maxId = 0;
        int refLen = 0;
        Map<String, Byte> factionCache = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Integer> header = CsvReader.header(reader, file);
            int cId = CsvReader.column(header, "Id", file);
            int cRef = CsvReader.column(header, "Reference", file);
            int cFamily = CsvReader.column(header, "CardFamilyId", file);
            int cFaction = CsvReader.column(header, "FactionReference", file);
            int cSuspended = CsvReader.column(header, "IsSuspended", file);
            int cBanned = CsvReader.column(header, "IsBanned", file);
            int cExclusive = CsvReader.column(header, "IsExclusive", file);
            int cMain = CsvReader.column(header, "MAIN_COST", file);
            int cRecall = CsvReader.column(header, "RECALL_COST", file);
            int cForest = CsvReader.column(header, "FOREST_POWER", file);
            int cMountain = CsvReader.column(header, "MOUNTAIN_POWER", file);
            int cOcean = CsvReader.column(header, "OCEAN_POWER", file);
            int[] ends = new int[header.size() + 1];

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (line.indexOf('"') >= 0) {
                    throw new IOException("Unexpected quoted field in " + file + ": " + line);
                }
                CsvReader.splitUnquoted(line, ends);
                if (size == family.length) {
                    capacity = family.length * 2;
                    refOffset = Arrays.copyOf(refOffset, capacity + 1);
                    family = Arrays.copyOf(family, capacity);
                    faction = Arrays.copyOf(faction, capacity);
                    flags = Arrays.copyOf(flags, capacity);
                    mainCost = Arrays.copyOf(mainCost, capacity);
                    recallCost = Arrays.copyOf(recallCost, capacity);
                    forest = Arrays.copyOf(forest, capacity);
                    mountain = Arrays.copyOf(mountain, capacity);
                    ocean = Arrays.copyOf(ocean, capacity);
                    ids = Arrays.copyOf(ids, capacity);
                }
                int row = size++;
                int from = CsvReader.start(ends, cRef), to = ends[cRef];
                if (refLen + (to - from) > refBytes.length) {
                    refBytes = Arrays.copyOf(refBytes, refBytes.length * 2);
                }
                for (int i = from; i < to; i++) {
                    refBytes[refLen++] = (byte) line.charAt(i);
                }
                refOffset[row + 1] = refLen;

                ids[row] = CsvReader.parseInt(line, ends, cId, -1);
                maxId = Math.max(maxId, ids[row]);
                family[row] = (short) CsvReader.parseInt(line, ends, cFamily, -1);
                String factionCode = CsvReader.text(line, ends, cFaction);
                Byte f = factionCache.get(factionCode);
                if (f == null) {
                    f = ref.factionIndex(factionCode, null);
                    factionCache.put(factionCode, f);
                }
                faction[row] = f;
                flags[row] = (byte) ((CsvReader.isOne(line, ends, cSuspended) ? FLAG_SUSPENDED : 0)
                        | (CsvReader.isOne(line, ends, cBanned) ? FLAG_BANNED : 0)
                        | (CsvReader.isOne(line, ends, cExclusive) ? FLAG_EXCLUSIVE : 0));
                mainCost[row] = (byte) CsvReader.parseInt(line, ends, cMain, MISSING);
                recallCost[row] = (byte) CsvReader.parseInt(line, ends, cRecall, MISSING);
                forest[row] = (byte) CsvReader.parseInt(line, ends, cForest, MISSING);
                mountain[row] = (byte) CsvReader.parseInt(line, ends, cMountain, MISSING);
                ocean[row] = (byte) CsvReader.parseInt(line, ends, cOcean, MISSING);
            }
        }
        refBytes = Arrays.copyOf(refBytes, refLen);
        int[] idToRow = new int[maxId + 1];
        Arrays.fill(idToRow, -1);
        for (int row = 0; row < size; row++) {
            if (ids[row] >= 0) {
                idToRow[ids[row]] = row;
            }
        }
        return idToRow;
    }

    /** Two passes so the file does not need to be sorted by print. */
    private void loadEffects(Path file, int[] idToRow) throws IOException {
        int capacity = Math.max(1024, size * 3);
        int[] rows = new int[capacity];
        short[] values = new short[capacity * 4];
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Integer> header = CsvReader.header(reader, file);
            int cPrint = CsvReader.column(header, "UniquePrintId", file);
            int cDisplay = CsvReader.column(header, "DisplayIndex", file);
            int cTrigger = CsvReader.column(header, "TriggerId", file);
            int cCondition = CsvReader.column(header, "ConditionId", file);
            int cEffect = CsvReader.column(header, "EffectId", file);
            int[] ends = new int[header.size() + 1];
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                CsvReader.splitUnquoted(line, ends);
                int printId = CsvReader.parseInt(line, ends, cPrint, -1);
                int row = printId >= 0 && printId < idToRow.length ? idToRow[printId] : -1;
                if (row < 0) {
                    continue; // fragment of an unknown print
                }
                if (count == rows.length) {
                    rows = Arrays.copyOf(rows, rows.length * 2);
                    values = Arrays.copyOf(values, values.length * 2);
                }
                rows[count] = row;
                int v = count * 4;
                values[v] = (short) CsvReader.parseInt(line, ends, cDisplay, 0);
                values[v + 1] = (short) CsvReader.parseInt(line, ends, cTrigger, Referentials.NO_FRAGMENT);
                values[v + 2] = (short) CsvReader.parseInt(line, ends, cCondition, Referentials.NO_FRAGMENT);
                values[v + 3] = (short) CsvReader.parseInt(line, ends, cEffect, Referentials.NO_FRAGMENT);
                count++;
            }
        }
        effStart = new int[size + 1];
        for (int i = 0; i < count; i++) {
            effStart[rows[i] + 1]++;
        }
        for (int i = 0; i < size; i++) {
            effStart[i + 1] += effStart[i];
        }
        effects = new short[count * 4];
        int[] cursor = Arrays.copyOf(effStart, size);
        for (int i = 0; i < count; i++) {
            int dst = cursor[rows[i]]++ * 4;
            System.arraycopy(values, i * 4, effects, dst, 4);
        }
    }

    private void buildRefTable() {
        int tableSize = Integer.highestOneBit(Math.max(16, size * 2)) << 1;
        refTable = new int[tableSize];
        int mask = tableSize - 1;
        for (int row = 0; row < size; row++) {
            int slot = hash(refBytes, refOffset[row], refOffset[row + 1]) & mask;
            while (refTable[slot] != 0) {
                slot = (slot + 1) & mask;
            }
            refTable[slot] = row + 1;
        }
    }

    // ------------------------------------------------------------------ access

    String reference(int row) {
        return new String(refBytes, refOffset[row], refOffset[row + 1] - refOffset[row], StandardCharsets.ISO_8859_1);
    }

    /** Row of a reference, or -1. */
    int find(String reference) {
        byte[] key = reference.getBytes(StandardCharsets.ISO_8859_1);
        int mask = refTable.length - 1;
        int slot = hash(key, 0, key.length) & mask;
        while (refTable[slot] != 0) {
            int row = refTable[slot] - 1;
            if (Arrays.equals(refBytes, refOffset[row], refOffset[row + 1], key, 0, key.length)) {
                return row;
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    /** ASCII case-insensitive "reference contains" without allocating. */
    boolean referenceContains(int row, byte[] lowerNeedle) {
        int from = refOffset[row], to = refOffset[row + 1] - lowerNeedle.length;
        outer: for (int i = from; i <= to; i++) {
            for (int j = 0; j < lowerNeedle.length; j++) {
                byte b = refBytes[i + j];
                if (b >= 'A' && b <= 'Z') {
                    b += 32;
                }
                if (b != lowerNeedle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    long memoryBytes() {
        return refBytes.length + 4L * refOffset.length + 2L * family.length + 8L * faction.length
                + 4L * effStart.length + 2L * effects.length + 4L * refTable.length;
    }

    private static int hash(byte[] bytes, int from, int to) {
        int h = 0x811C9DC5;
        for (int i = from; i < to; i++) {
            h = (h ^ bytes[i]) * 0x01000193;
        }
        return h ^ (h >>> 16);
    }
}
