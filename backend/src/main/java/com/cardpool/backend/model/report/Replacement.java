package com.cardpool.backend.model.report;

/** A card swapped in the pool from the analysis view. */
public record Replacement(String fromReference, String fromName, String toReference, String toName,
        String replacedAt) {
}
