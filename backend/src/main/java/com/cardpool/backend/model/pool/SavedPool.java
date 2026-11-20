package com.cardpool.backend.model.pool;

import java.util.List;

import com.cardpool.backend.model.report.Replacement;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The pool being worked on, as kept in {@code current-pool.json}: its cards by
 * reference (in pool order) and what the frontend needs to resume the work.
 *
 * @param name         display name, e.g. "Generated pool 19/11/2026"
 * @param filter       generator form the pool was drawn with (reused for replacements)
 * @param replacements cards swapped since generation, oldest first
 * @param generatedAt  ISO date of the generation
 * @param updatedAt    ISO date of the last save (set by the backend)
 * @param references   card references, in pool order
 */
public record SavedPool(String name, JsonNode filter, List<Replacement> replacements, String generatedAt,
        String updatedAt, List<String> references) {

    public SavedPool {
        replacements = replacements == null ? List.of() : List.copyOf(replacements);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public SavedPool withUpdatedAt(String date) {
        return new SavedPool(name, filter, replacements, generatedAt, date, references);
    }
}
