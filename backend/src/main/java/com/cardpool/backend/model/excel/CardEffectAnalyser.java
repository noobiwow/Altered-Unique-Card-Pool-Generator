package com.cardpool.backend.model.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CardEffectAnalyser {

    public interface CardRecord {
        String getMainEffect();

        String getSet();
    }

    private final CardEffectParser parser = new CardEffectParser();

    public EffectInventory analyse(Iterable<? extends CardRecord> cards) {
        Objects.requireNonNull(cards, "cards must not be null");

        List<EffectEntry> entries = new ArrayList<>();

        for (CardRecord card : cards) {
            if (card == null)
                continue;

            String set = card.getSet() == null ? "Unknown" : card.getSet().strip();
            List<ParsedAbility> abilities = parser.parse(card.getMainEffect());

            for (ParsedAbility ability : abilities) {
                entries.add(new EffectEntry(set, ability));
            }
        }

        return new EffectInventory(entries);
    }

    public EffectInventory analyseMap(java.util.Map<String, String> effectToSet) {
        List<EffectEntry> entries = new ArrayList<>();
        effectToSet.forEach((effect, set) -> {
            List<ParsedAbility> abilities = parser.parse(effect);
            String setName = set == null ? "Unknown" : set.strip();
            for (ParsedAbility ability : abilities) {
                entries.add(new EffectEntry(setName, ability));
            }
        });
        return new EffectInventory(entries);
    }
}
