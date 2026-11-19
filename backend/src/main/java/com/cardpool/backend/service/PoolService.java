package com.cardpool.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.CostConstraint;
import com.cardpool.backend.model.form.FilterForm;

@Service
public class PoolService {

    public CardFilter buildFilter(FilterForm filterForm) {
        return buildFilter(filterForm, filterForm.getFaction());
    }

    /**
     * Builds a filter, optionally restricting it to a single faction. Passing
     * {@code null} builds the global filter used to generate every faction pool.
     */
    public CardFilter buildFilter(FilterForm filterForm, String faction) {
        CardFilter.Builder builder = CardFilter.builder();

        if (faction != null)
            builder.faction(faction);
        if (filterForm.getSet() != null)
            builder.set(filterForm.getSet());
        if (filterForm.getSubTypes() != null)
            builder.subTypes(filterForm.getSubTypes());
        if (filterForm.getType() != null)
            builder.cardType(filterForm.getType());
        if (filterForm.isCheckExcludeBanned())
            builder.excludeBanned();
        if (filterForm.isCheckExcludeSuspended())
            builder.excludeSuspended();
        CostConstraint handCost = parseCost("handCost", filterForm.getHandCost());
        if (handCost != null) {
            builder.handCost(handCost);
        } else {
            // Legacy min/max hand-cost fields.
            if (filterForm.getMinCost() != null)
                builder.minMainCost(filterForm.getMinCost());
            if (filterForm.getMaxCost() != null)
                builder.maxMainCost(filterForm.getMaxCost());
        }
        builder.reserveCost(parseCost("reserveCost", filterForm.getReserveCost()));
        if (filterForm.getSetWeights() != null)
            builder.setWeights(filterForm.getSetWeights());

        if (filterForm.getFieldSearch() != null) {
            String search = filterForm.getFieldSearch().trim().toLowerCase();
            if (!search.isEmpty()) {
                builder.custom(c -> {
                    // TODO Change way to handle search on name
                    String name = c.getName() != null ? c.getName().toLowerCase() : "";
                    String ref = c.getReference() != null ? c.getReference().toLowerCase() : "";
                    String effect = c.getMainEffect() != null ? c.getMainEffect().toLowerCase() : "";
                    return name.contains(search) || ref.contains(search) || effect.contains(search);
                });
            }
        }

        return builder.build();
    }

    private CostConstraint parseCost(String field, String raw) {
        try {
            return CostConstraint.parse(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + ": " + e.getMessage(), e);
        }
    }
}
