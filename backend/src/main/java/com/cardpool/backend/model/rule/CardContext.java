package com.cardpool.backend.model.rule;

import java.util.ArrayList;
import java.util.List;

import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.AbilitySlot;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.effect.CardEffectParser;

/**
 * A card with its abilities prepared once, so that many rules can be evaluated
 * against it. Abilities come from CardsData, split into trigger, condition
 * and effect.
 */
public final class CardContext {

    /**
     * An ability as seen by rules.
     *
     * @param effect   the effect without a leading static marker
     * @param text     the whole ability as printed
     * @param keywords named effects and statuses, without brackets
     * @param source   the ability as displayed (reported in matches)
     */
    public record AbilityView(AbilitySlot slot, String trigger, String condition, String effect, String text,
            List<String> keywords, Ability source) {

        /** True when the parts can be compared by CardsData fragment id. */
        public boolean hasFragmentIds() {
            return source != null && source.hasFragmentIds();
        }

        public Integer triggerId() {
            return source == null ? null : source.triggerId();
        }

        public Integer conditionId() {
            return source == null ? null : source.conditionId();
        }

        public Integer effectId() {
            return source == null ? null : source.effectId();
        }

        public Ability toAbility() {
            return source;
        }
    }

    private final Card card;
    private List<AbilityView> abilities;

    public CardContext(Card card) {
        this.card = card;
    }

    public Card card() {
        return card;
    }

    public String factionCode() {
        return card.getMainFaction() == null ? null : card.getMainFaction().getCode();
    }

    public List<AbilityView> abilities(AbilitySlot slot) {
        return abilities().stream().filter(a -> a.slot() == slot).toList();
    }

    public List<AbilityView> abilities() {
        if (abilities == null) {
            abilities = card.getAbilities() != null ? fromStructured(card.getAbilities()) : List.of();
        }
        return abilities;
    }

    private static List<AbilityView> fromStructured(List<Ability> structured) {
        List<AbilityView> views = new ArrayList<>();
        for (Ability a : structured) {
            if (a == null) {
                continue;
            }
            String text = a.text();
            Ability normalized = Ability.of(a.slot() == null ? AbilitySlot.MAIN : a.slot(), a.trigger(), a.condition(),
                    a.effect(), a.triggerId(), a.conditionId(), a.effectId());
            views.add(new AbilityView(normalized.slot(), blankToNull(normalized.trigger()),
                    blankToNull(normalized.condition()), normalized.effectWithoutMarker(), text,
                    CardEffectParser.keywords(text), normalized));
        }
        return List.copyOf(views);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
