package com.cardpool.backend.dto;

/**
 * A single configured warning rule from {@code effect-warnings.json}.
 *
 * @param type  the warning type ({@code CARD_NAME}, {@code EFFECT_LINE} or
 *              {@code PATTERN})
 * @param value the raw configured value used for matching
 * @param label a human-readable label for display in dropdowns
 */
public record WarningRule(String type, String value, String label) {
}
