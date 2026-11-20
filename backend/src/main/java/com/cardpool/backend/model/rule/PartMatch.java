package com.cardpool.backend.model.rule;

import java.util.List;

/**
 * How one part of an ability (trigger, condition or effect) must look.
 *
 * @param mode      CONTAINS, EXACT, REGEX, ABSENT (the part must be empty) or FRAGMENTS
 * @param value     the text or regex; ignored for ABSENT and FRAGMENTS
 * @param fragments for FRAGMENTS: the accepted fragments (any of them)
 */
public record PartMatch(MatchMode mode, String value, List<FragmentRef> fragments) {

    public PartMatch(MatchMode mode, String value) {
        this(mode, value, null);
    }

    /**
     * A CardsData effect fragment.
     *
     * @param id   fragment id ({@code IdGd} in EffectFragments.csv)
     * @param text its text when the rule was written: shown in the editor and
     *             compared on cards without fragment ids
     */
    public record FragmentRef(int id, String text) {
    }
}
