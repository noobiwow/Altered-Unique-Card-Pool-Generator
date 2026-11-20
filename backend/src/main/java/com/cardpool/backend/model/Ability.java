package com.cardpool.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One ability of a card, split into its parts. Any part may be empty: a static
 * ability has the trigger {@code []}, many abilities have no condition.
 *
 * @param slot      main or echo effect
 * @param trigger   e.g. {@code {J}}, {@code At Dusk —}, {@code []}
 * @param condition e.g. {@code If you control a token:}
 * @param effect    what the ability does
 * @param triggerId   CardsData fragment id of the trigger (null when unknown, e.g. text-only sources)
 * @param conditionId CardsData fragment id of the condition
 * @param effectId    CardsData fragment id of the effect
 */
public record Ability(AbilitySlot slot, String trigger, String condition, String effect,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer triggerId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer conditionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer effectId) {

    /** An ability without fragment ids (text-only sources). */
    public Ability(AbilitySlot slot, String trigger, String condition, String effect) {
        this(slot, trigger, condition, effect, null, null, null);
    }

    /** The static marker printed before an effect, e.g. {@code {J} []Draw a card.} */
    public static final String STATIC_MARKER = "[]";

    /**
     * Builds an ability from raw parts. CardsData sometimes stores the static
     * marker as the condition ({@code {D} :} + {@code []} + effect): it is not a
     * condition, so it is moved in front of the effect (the text is unchanged).
     */
    public static Ability of(AbilitySlot slot, String trigger, String condition, String effect) {
        return of(slot, trigger, condition, effect, null, null, null);
    }

    /** Same as {@link #of(AbilitySlot, String, String, String)}, keeping the fragment ids. */
    public static Ability of(AbilitySlot slot, String trigger, String condition, String effect,
            Integer triggerId, Integer conditionId, Integer effectId) {
        if (condition != null && condition.strip().equals(STATIC_MARKER)) {
            return new Ability(slot, trigger, null, effect == null ? STATIC_MARKER : STATIC_MARKER + effect.strip(),
                    triggerId, null, effectId);
        }
        return new Ability(slot, trigger, condition, effect, triggerId, conditionId, effectId);
    }

    /** True when the ability comes from CardsData fragments (rules can then compare ids). */
    public boolean hasFragmentIds() {
        return triggerId != null || conditionId != null || effectId != null;
    }

    /** The effect without a leading static marker, as compared by rules. */
    public String effectWithoutMarker() {
        if (effect == null) {
            return null;
        }
        String e = effect.strip();
        while (e.startsWith(STATIC_MARKER)) {
            e = e.substring(STATIC_MARKER.length()).strip();
        }
        return e.isEmpty() ? null : e;
    }

    /** The ability as printed: parts separated by a space, none after the static marker {@code []}. */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (String part : new String[] { trigger, condition, effect }) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (out.length() > 0 && !(out.length() >= 2 && out.charAt(out.length() - 2) == '['
                    && out.charAt(out.length() - 1) == ']')) {
                out.append(' ');
            }
            out.append(part.strip());
        }
        return out.toString();
    }
}
