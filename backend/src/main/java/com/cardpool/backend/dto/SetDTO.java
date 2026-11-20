package com.cardpool.backend.dto;

/**
 * @param reference     set code, e.g. {@code EOLE}
 * @param name          English name
 * @param defaultWeight default share in a generated pool (0 when not configured)
 */
public record SetDTO(String reference, String name, double defaultWeight) {
}
