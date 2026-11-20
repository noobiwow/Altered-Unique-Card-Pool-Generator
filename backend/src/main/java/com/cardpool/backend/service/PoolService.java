package com.cardpool.backend.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.CostConstraint;
import com.cardpool.backend.model.form.FilterForm;

/** Turns the generator form into a {@link CardFilter}. */
@Service
public class PoolService {

    /**
     * Builds the filter of a generator form.
     *
     * @param form               the form
     * @param faction            restrict to one faction, or null for every faction
     * @param excludedReferences references never to return (rejected cards, cards already in the pool...)
     * @throws IllegalArgumentException for an invalid cost expression
     */
    public CardFilter buildFilter(FilterForm form, String faction, Set<String> excludedReferences) {
        return CardFilter.builder()
                .faction(faction)
                .set(form.set())
                .subTypes(form.subTypes())
                .cardType(form.type())
                .handCost(parseCost("handCost", form.handCost()))
                .reserveCost(parseCost("reserveCost", form.reserveCost()))
                .setWeights(form.setWeights())
                .textSearch(form.fieldSearch())
                .excludeReferences(excludedReferences)
                .excludeBanned(form.checkExcludeBanned())
                .excludeSuspended(form.checkExcludeSuspended())
                .build();
    }

    private static CostConstraint parseCost(String field, String raw) {
        try {
            return CostConstraint.parse(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + ": " + e.getMessage(), e);
        }
    }
}
