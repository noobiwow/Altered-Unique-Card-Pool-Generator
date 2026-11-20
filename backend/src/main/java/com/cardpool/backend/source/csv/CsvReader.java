package com.cardpool.backend.source.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small RFC 4180 CSV reader (quoted fields, escaped quotes, embedded newlines, BOM). */
public final class CsvReader {

    private CsvReader() {
    }

    /** Reads a whole (small) file as rows keyed by header name. */
    public static List<Map<String, String>> readRows(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<List<String>> rows = parse(reader);
            List<Map<String, String>> out = new ArrayList<>();
            if (rows.isEmpty()) {
                return out;
            }
            List<String> header = rows.get(0);
            for (List<String> row : rows.subList(1, rows.size())) {
                if (row.size() == 1 && row.get(0).isEmpty()) {
                    continue;
                }
                Map<String, String> map = new HashMap<>();
                for (int i = 0; i < header.size() && i < row.size(); i++) {
                    map.put(header.get(i), row.get(i));
                }
                out.add(map);
            }
            return out;
        }
    }

    /** Header name -> column index of a (large) file, read from its first line. */
    public static Map<String, Integer> header(BufferedReader reader, Path file) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Empty file " + file);
        }
        List<String> names = parseLine(stripBom(line));
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            index.put(names.get(i), i);
        }
        return index;
    }

    public static int column(Map<String, Integer> header, String name, Path file) throws IOException {
        Integer index = header.get(name);
        if (index == null) {
            throw new IOException("Column " + name + " missing in " + file);
        }
        return index;
    }

    /** Parses one line that contains no embedded newline. */
    public static List<String> parseLine(String line) throws IOException {
        List<List<String>> rows = parse(new java.io.StringReader(line));
        return rows.isEmpty() ? List.of() : rows.get(0);
    }

    static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }

    static List<List<String>> parse(Reader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean first = true;
        int pending = -1;
        while (true) {
            int c = pending >= 0 ? pending : reader.read();
            pending = -1;
            if (c == -1) {
                break;
            }
            if (first) {
                first = false;
                if (c == '﻿') {
                    continue;
                }
            }
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        pending = next;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Allocation-light helpers for the large, unquoted print files.

    /**
     * Fills {@code ends} with the end index of each comma-separated field of
     * {@code line}; returns the number of fields.
     */
    public static int splitUnquoted(String line, int[] ends) {
        int n = 0;
        int len = line.length();
        for (int i = 0; i < len && n < ends.length - 1; i++) {
            if (line.charAt(i) == ',') {
                ends[n++] = i;
            }
        }
        ends[n++] = len;
        return n;
    }

    public static int start(int[] ends, int field) {
        return field == 0 ? 0 : ends[field - 1] + 1;
    }

    /** Parses an int field; returns {@code missing} when empty. */
    public static int parseInt(String line, int[] ends, int field, int missing) {
        int from = start(ends, field);
        int to = ends[field];
        if (from >= to) {
            return missing;
        }
        boolean negative = line.charAt(from) == '-';
        int i = negative ? from + 1 : from;
        int value = 0;
        for (; i < to; i++) {
            char ch = line.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new NumberFormatException("Not a number: " + line.substring(from, to));
            }
            value = value * 10 + (ch - '0');
        }
        return negative ? -value : value;
    }

    public static boolean isOne(String line, int[] ends, int field) {
        int from = start(ends, field);
        return ends[field] - from == 1 && line.charAt(from) == '1';
    }

    public static String text(String line, int[] ends, int field) {
        return line.substring(start(ends, field), ends[field]);
    }
}
