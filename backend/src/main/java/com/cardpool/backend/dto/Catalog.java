package com.cardpool.backend.dto;

import java.util.List;

/** What the generator form can filter on. */
public record Catalog(List<FactionDTO> factions, List<SetDTO> sets, List<SubtypeDTO> subtypes) {
}
