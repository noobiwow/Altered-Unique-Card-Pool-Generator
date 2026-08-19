package com.cardpool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.dto.CardWarning;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.card_elements.Faction;

public class EffectWarningServiceTest {

    private final EffectWarningService service = new EffectWarningService();

    @Test
    public void test_analyze_flags_pattern() {
        Card card = new Card();
        card.setReference("REF_2");
        card.setName("Looter");
        card.setMainEffect("Put the top two cards of your deck in your Mana zone (as exhausted Mana Orbs).");

        List<CardWarning> warnings = service.analyze(List.of(card));

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).warnings().stream()
                .anyMatch(w -> w.type().equals("PATTERN")));
    }

    @Test
    public void test_analyze_ignores_clean_cards() {
        Card clean = new Card();
        clean.setReference("REF_3");
        clean.setName("Knight");
        clean.setMainEffect("[] +2 attack.");

        Card nullEffect = new Card();
        nullEffect.setReference("REF_4");
        nullEffect.setName("No effect");

        List<CardWarning> warnings = service.analyze(List.of(clean, nullEffect));

        assertTrue(warnings.isEmpty());
    }

    @Test
    public void test_analyze_flags_watched_card_name() {
        Card card = new Card();
        card.setReference("REF_5");
        card.setName("Moonlight Jellyfish");
        card.setMainEffect("[] +2 attack.");

        List<CardWarning> warnings = service.analyze(List.of(card));

        assertEquals(1, warnings.size());
        assertEquals("REF_5", warnings.get(0).reference());
        assertTrue(warnings.get(0).warnings().stream()
                .anyMatch(w -> w.type().equals("CARD_NAME")));
    }

    @Test
    public void test_analyze_flags_effect_line() {
        Card card = new Card();
        card.setReference("REF_7");
        card.setName("Rigger");
        card.setMainEffect("[] You may target a Character with Hand Cost {3} or less other than me, it gains [[Anchored]].");

        List<CardWarning> warnings = service.analyze(List.of(card));

        assertEquals(1, warnings.size());
        assertEquals("REF_7", warnings.get(0).reference());
        assertTrue(warnings.get(0).warnings().stream()
                .anyMatch(w -> w.type().equals("EFFECT_LINE")));
    }

    @Test
    public void test_analyze_does_not_flag_similar_effect_lines() {
        Card card = new Card();
        card.setReference("REF_8");
        card.setName("Snoozer");
        card.setMainEffect("[] You may target a Character with Hand Cost {3} or less other than me, it gains [[Fleeting]].");

        List<CardWarning> warnings = service.analyze(List.of(card));

        assertTrue(warnings.stream()
                .noneMatch(w -> w.warnings().stream()
                        .anyMatch(warn -> warn.type().equals("EFFECT_LINE"))));
    }

    @Test
    public void test_analyze_flags_card_name_only_for_matching_faction() {
        Faction bravos = new Faction();
        bravos.setCode("BR");
        bravos.setName("Bravos");

        Card bravosCard = new Card();
        bravosCard.setReference("REF_9");
        bravosCard.setName("Foundry Armorer");
        bravosCard.setMainFaction(bravos);
        bravosCard.setMainEffect("[] +2 attack.");

        Faction axiom = new Faction();
        axiom.setCode("AX");
        axiom.setName("Axiom");

        Card axiomCard = new Card();
        axiomCard.setReference("REF_10");
        axiomCard.setName("Foundry Armorer");
        axiomCard.setMainFaction(axiom);
        axiomCard.setMainEffect("[] +2 attack.");

        List<CardWarning> warnings = service.analyze(List.of(bravosCard, axiomCard));

        assertEquals(1, warnings.size());
        assertEquals("REF_9", warnings.get(0).reference());
        assertTrue(warnings.get(0).warnings().stream()
                .anyMatch(w -> w.type().equals("CARD_NAME")));
    }

    @Test
    public void test_analyze_ignores_effect_line_in_echo_effect() {
        Card card = new Card();
        card.setReference("REF_11");
        card.setName("Rigger");
        card.setMainEffect("[] +2 attack.");
        card.setEchoEffect("[] You may target a Character with Hand Cost {3} or less other than me, it gains [[Anchored]].");

        List<CardWarning> warnings = service.analyze(List.of(card));

        assertTrue(warnings.stream()
                .noneMatch(w -> w.warnings().stream()
                        .anyMatch(warn -> warn.type().equals("EFFECT_LINE"))));
    }
}
