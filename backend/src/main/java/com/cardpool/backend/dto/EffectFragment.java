package com.cardpool.backend.dto;

/**
 * A CardsData effect fragment as it can be used in an ability rule.
 *
 * @param id   fragment id ({@code IdGd} in EffectFragments.csv)
 * @param part where unique cards use it: TRIGGER, CONDITION or EFFECT
 * @param text English text, as shown on cards
 * @param uses number of unique card abilities using it in that part
 */
public record EffectFragment(int id, String part, String text, int uses) {
}
