package com.cardpool.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.AbilitySlot;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.card_elements.CardSet;
import com.cardpool.backend.model.card_elements.Faction;
import com.cardpool.backend.model.effect.CardEffectParser;
import com.cardpool.backend.model.effect.ParsedAbility;

/**
 * Cards for tests, written as effect text: their abilities are split into
 * trigger / condition / effect like CardsData does.
 */
public final class TestCards {

    private static final CardEffectParser PARSER = new CardEffectParser();

    private TestCards() {
    }

    public static Card card(String reference, String name, String factionCode, String mainEffect) {
        Card card = new Card();
        card.setReference(reference);
        card.setName(name);
        card.setMainEffect(mainEffect);
        card.setAbilities(abilities(card));
        if (factionCode != null) {
            Faction faction = new Faction();
            faction.setCode(factionCode);
            faction.setName(factionCode);
            card.setMainFaction(faction);
        }
        return card;
    }

    public static Card withCosts(Card card, int hand, int reserve) {
        Map<String, String> elements = new HashMap<>();
        elements.put("MAIN_COST", String.valueOf(hand));
        elements.put("RECALL_COST", String.valueOf(reserve));
        card.setElements(elements);
        return card;
    }

    public static Card withSet(Card card, String code) {
        CardSet set = new CardSet();
        set.setCode(code);
        set.setReference(code);
        set.setName(code);
        card.setCardSet(set);
        return card;
    }

    public static Card withEcho(Card card, String echoEffect) {
        card.setEchoEffect(echoEffect);
        card.setAbilities(abilities(card));
        return card;
    }

    private static List<Ability> abilities(Card card) {
        List<Ability> abilities = new ArrayList<>();
        addParsed(abilities, AbilitySlot.MAIN, card.getMainEffect());
        addParsed(abilities, AbilitySlot.ECHO, card.getEchoEffect());
        return abilities;
    }

    private static void addParsed(List<Ability> abilities, AbilitySlot slot, String effect) {
        for (ParsedAbility p : PARSER.parse(effect)) {
            // The parser strips the static marker; keep it as the trigger, like CardsData does.
            String trigger = p.getTrigger() != null ? p.getTrigger() : p.getRawText().startsWith("[]") ? "[]" : null;
            abilities.add(Ability.of(slot, trigger, blank(p.getCondition()), blank(p.getEffectBody())));
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
