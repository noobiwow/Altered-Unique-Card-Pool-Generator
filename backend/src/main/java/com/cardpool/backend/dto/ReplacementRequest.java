package com.cardpool.backend.dto;

import java.util.List;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.form.FilterForm;

/**
 * @param card            the card to replace
 * @param poolReferences  references currently in the pool (never proposed)
 * @param filter          the filters the pool was generated with, if known
 * @param count           number of candidates wanted
 */
public record ReplacementRequest(Card card, List<String> poolReferences, FilterForm filter, Integer count) {
}
