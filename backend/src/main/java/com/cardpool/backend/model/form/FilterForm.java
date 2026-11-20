package com.cardpool.backend.model.form;

import java.util.List;
import java.util.Map;

/**
 * Generator form, as sent by the frontend. Unknown properties are ignored.
 *
 * @param faction               restrict to one faction code (unused by the generator, which draws every faction)
 * @param set                   set reference
 * @param subTypes              sub type names, any of them
 * @param type                  card type name
 * @param checkExcludeBanned    drop banned cards
 * @param checkExcludeSuspended drop suspended cards
 * @param handCost              cost expression such as {@code 4+}, {@code 2-} or {@code 3}
 * @param reserveCost           cost expression
 * @param fieldSearch           text searched in name, reference and main effect
 * @param setWeights            share of each set code in the pool
 * @param excludeRejected       exclude cards rejected by the team (null = true)
 */
public record FilterForm(
        String faction,
        String set,
        List<String> subTypes,
        String type,
        boolean checkExcludeBanned,
        boolean checkExcludeSuspended,
        String handCost,
        String reserveCost,
        String fieldSearch,
        Map<String, Double> setWeights,
        Boolean excludeRejected) {

    public boolean shouldExcludeRejected() {
        return excludeRejected == null || excludeRejected;
    }
}
