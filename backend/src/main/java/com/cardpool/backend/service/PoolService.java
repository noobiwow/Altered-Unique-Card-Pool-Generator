package com.cardpool.backend.service;

import org.springframework.stereotype.Service;

import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.form.FilterForm;

@Service
public class PoolService {

    public CardFilter buildFilter(FilterForm filterForm) {
        CardFilter.Builder builder = CardFilter.builder();

        if (filterForm.getFaction() != null)
            builder.faction(filterForm.getFaction());
        if (filterForm.getSet() != null)
            builder.set(filterForm.getSet());
        if (filterForm.getSubType() != null)
            builder.subType(filterForm.getSubType());
        if (filterForm.getType() != null)
            builder.cardType(filterForm.getType());
        if (filterForm.isCheckExcludeBanned())
            builder.excludeBanned();
        if (filterForm.isCheckExcludeSuspended())
            builder.excludeSuspended();
        if (filterForm.getMinCost() != null)
            builder.minMainCost(filterForm.getMinCost());
        if (filterForm.getMaxCost() != null)
            builder.maxMainCost(filterForm.getMaxCost());

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
}
