package com.cardpool.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cardpool.backend.dto.VerdictRequest;
import com.cardpool.backend.model.verdict.Verdict;
import com.cardpool.backend.model.verdict.VerdictStatus;

/** Persists team verdicts in {@code verdicts.json} of the data directory. */
@Service
public class VerdictService {

    static final String FILE = "verdicts.json";

    /** On-disk layout. */
    public record VerdictFile(int version, Map<String, Verdict> verdicts) {
    }

    private final JsonFileStore store;
    private Map<String, Verdict> verdicts;

    public VerdictService(JsonFileStore store) {
        this.store = store;
    }

    private Map<String, Verdict> verdicts() {
        if (verdicts == null) {
            verdicts = new LinkedHashMap<>(store.read(FILE, VerdictFile.class)
                    .map(VerdictFile::verdicts)
                    .filter(m -> m != null)
                    .orElse(Map.of()));
        }
        return verdicts;
    }

    public synchronized List<Verdict> all() {
        return List.copyOf(verdicts().values());
    }

    public synchronized Optional<Verdict> get(String reference) {
        return Optional.ofNullable(verdicts().get(reference));
    }

    /** Snapshot of all verdicts, for analysing many cards at once. */
    public synchronized Map<String, Verdict> snapshot() {
        return Map.copyOf(verdicts());
    }

    public synchronized Set<String> rejectedReferences() {
        return verdicts().values().stream()
                .filter(v -> v.status() == VerdictStatus.REJECTED)
                .map(Verdict::reference)
                .collect(Collectors.toUnmodifiableSet());
    }

    public synchronized Verdict set(String reference, VerdictRequest request) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("A card reference is required");
        }
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("A status (APPROVED or REJECTED) is required");
        }
        Verdict verdict = new Verdict(reference, request.name(), request.status(),
                request.note() == null ? "" : request.note().strip(),
                request.reviewedRuleIds() == null ? List.of() : List.copyOf(request.reviewedRuleIds()),
                Instant.now().toString());
        verdicts().put(reference, verdict);
        persist();
        return verdict;
    }

    /** Removes the verdict, putting the card back to pending. */
    public synchronized boolean clear(String reference) {
        boolean removed = verdicts().remove(reference) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    private void persist() {
        store.write(FILE, new VerdictFile(1, verdicts()));
    }
}
