package com.cardpool.backend.model.rule;

import static com.cardpool.backend.TestCards.card;
import static com.cardpool.backend.TestCards.withEcho;
import static com.cardpool.backend.TestCards.withCosts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.dto.RuleMatch;
import com.cardpool.backend.model.Card;

class RuleMatcherTest {

    private static EffectRule rule(List<String> factions, RuleCondition... conditions) {
        return new EffectRule("r-test", "Test", "because", Severity.HIGH, "Cat", true, List.of(conditions),
                factions, null, null, null);
    }

    private static RuleCondition cond(RuleField field, MatchMode mode, String value) {
        return new RuleCondition(field, mode, value, false);
    }

    @Test
    void contains_ignores_case_and_whitespace_and_reports_matched_ability() {
        Card card = card("R1", "Looter", "MU",
                "[] +1 power.  When I'm played — Return a card from your\u00a0RESERVE to your hand.");
        RuleMatcher matcher = new RuleMatcher(rule(null,
                cond(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "return a card from your reserve")));

        RuleMatch match = matcher.match(card).orElseThrow();

        assertEquals("MAIN", match.source());
        assertTrue(match.matchedText().startsWith("When I'm played"));
        assertEquals(Severity.HIGH, match.severity());
    }

    @Test
    void exact_matches_a_whole_ability_ignoring_trailing_period() {
        Card card = card("R1", "X", "MU", "[] Draw a card.  [] Discard a card.");
        assertTrue(new RuleMatcher(rule(null, cond(RuleField.MAIN_EFFECT, MatchMode.EXACT, "Draw a card")))
                .match(card).isPresent());
        assertFalse(new RuleMatcher(rule(null, cond(RuleField.MAIN_EFFECT, MatchMode.EXACT, "Draw")))
                .match(card).isPresent());
    }

    @Test
    void echo_and_main_fields_are_separate() {
        Card card = card("R1", "X", "MU", "[] +2 attack.");
        withEcho(card, "[] Draw two cards.");
        assertFalse(new RuleMatcher(rule(null, cond(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "draw two")))
                .match(card).isPresent());
        RuleMatch echo = new RuleMatcher(rule(null, cond(RuleField.ANY_EFFECT, MatchMode.CONTAINS, "draw two")))
                .match(card).orElseThrow();
        assertEquals("ECHO", echo.source());
    }

    @Test
    void regex_is_case_insensitive() {
        Card card = card("R1", "X", "MU", "[] Put the top TWO cards of your deck in your Mana zone.");
        assertTrue(new RuleMatcher(rule(null, cond(RuleField.ANY_EFFECT, MatchMode.REGEX, "two cards? of your deck")))
                .match(card).isPresent());
    }

    @Test
    void keyword_matches_with_or_without_brackets() {
        Card card = card("R1", "X", "MU", "[] You may target a Character, it gains [[Anchored]].");
        assertTrue(new RuleMatcher(rule(null, cond(RuleField.KEYWORD, MatchMode.EXACT, "[[Anchored]]")))
                .match(card).isPresent());
        assertTrue(new RuleMatcher(rule(null, cond(RuleField.KEYWORD, MatchMode.EXACT, "anchored")))
                .match(card).isPresent());
        assertFalse(new RuleMatcher(rule(null, cond(RuleField.KEYWORD, MatchMode.EXACT, "Asleep")))
                .match(card).isPresent());
    }

    @Test
    void single_and_double_brackets_are_equivalent() {
        Card textCard = card("R1", "X", "MU", "[] You may target a Character, it gains [[Anchored]].");
        Card csv = card("R2", "X", "MU", "[] You may target a Character, it gains [Anchored].");
        for (String value : List.of("it gains [[Anchored]]", "it gains [Anchored]")) {
            RuleMatcher matcher = new RuleMatcher(rule(null, cond(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, value)));
            assertTrue(matcher.match(textCard).isPresent(), value);
            assertTrue(matcher.match(csv).isPresent(), value);
        }
        RuleMatcher regex = new RuleMatcher(rule(null, cond(RuleField.MAIN_EFFECT, MatchMode.REGEX, "gains \\[\\[anchored")));
        assertTrue(regex.match(textCard).isPresent());
        assertTrue(new RuleMatcher(rule(null, cond(RuleField.KEYWORD, MatchMode.EXACT, "Anchored"))).match(csv).isPresent());
    }

    @Test
    void trigger_matches_parsed_trigger() {
        Card card = card("R1", "X", "MU", "When I'm played — Draw a card.");
        assertTrue(new RuleMatcher(rule(null, cond(RuleField.TRIGGER, MatchMode.CONTAINS, "when i'm played")))
                .match(card).isPresent());
        assertFalse(new RuleMatcher(rule(null, cond(RuleField.TRIGGER, MatchMode.CONTAINS, "at dusk")))
                .match(card).isPresent());
    }

    @Test
    void all_conditions_must_hold_including_costs_and_negation() {
        Card cheap = withCosts(card("R1", "X", "MU", "[] Draw a card."), 2, 1);
        Card expensive = withCosts(card("R2", "X", "MU", "[] Draw a card."), 5, 4);
        Card discard = withCosts(card("R3", "X", "MU", "[] Draw a card, then discard a card."), 2, 1);
        RuleMatcher matcher = new RuleMatcher(rule(null,
                cond(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "draw a card"),
                cond(RuleField.HAND_COST, null, "3-"),
                new RuleCondition(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "discard", true)));

        assertTrue(matcher.match(cheap).isPresent());
        assertFalse(matcher.match(expensive).isPresent());
        assertFalse(matcher.match(discard).isPresent());
    }

    @Test
    void faction_scope_restricts_matches() {
        RuleMatcher matcher = new RuleMatcher(rule(List.of("br"),
                cond(RuleField.NAME, MatchMode.EXACT, "Foundry Armorer")));
        assertTrue(matcher.match(card("R1", "Foundry Armorer", "BR", "")).isPresent());
        assertFalse(matcher.match(card("R2", "Foundry Armorer", "AX", "")).isPresent());
    }

    @Test
    void invalid_rules_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new RuleMatcher(rule(null)));
        assertThrows(IllegalArgumentException.class,
                () -> new RuleMatcher(rule(null, cond(RuleField.ANY_EFFECT, MatchMode.REGEX, "(unclosed"))));
        assertThrows(IllegalArgumentException.class,
                () -> new RuleMatcher(rule(null, cond(RuleField.HAND_COST, null, "cheap"))));
        assertThrows(IllegalArgumentException.class,
                () -> new RuleMatcher(rule(null, cond(RuleField.NAME, MatchMode.CONTAINS, "  "))));
    }
}
