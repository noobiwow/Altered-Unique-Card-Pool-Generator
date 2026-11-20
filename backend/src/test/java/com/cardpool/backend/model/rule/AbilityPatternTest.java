package com.cardpool.backend.model.rule;

import static com.cardpool.backend.TestCards.card;
import static com.cardpool.backend.TestCards.withEcho;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.dto.RuleMatch;
import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.AbilitySlot;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.rule.AbilityPattern.Scope;

class AbilityPatternTest {

    private static PartMatch contains(String v) {
        return new PartMatch(MatchMode.CONTAINS, v);
    }

    private static final PartMatch ABSENT = new PartMatch(MatchMode.ABSENT, null);

    private static RuleMatcher matcher(AbilityPattern pattern) {
        return new RuleMatcher(new EffectRule("r-1", "Combo", null, Severity.HIGH, null, true,
                List.of(RuleCondition.ability(pattern, false)), null, null, null, null));
    }

    /** Structured card, as built by the CardsData source. */
    private static Card structured() {
        Card c = card("S1", "Scholar", "LY", null);
        c.setAbilities(List.of(
                new Ability(AbilitySlot.MAIN, "{J}", null, "Draw a card."),
                new Ability(AbilitySlot.MAIN, "{H}", "If you control a token:", "I gain 2 boosts."),
                new Ability(AbilitySlot.ECHO, "{D} :", null, "Draw a card.")));
        return c;
    }

    @Test
    void all_given_parts_must_match_the_same_ability() {
        // {H} has a condition and {J} draws, but no single ability is "{H} ... draw"
        assertFalse(matcher(new AbilityPattern(null, contains("{H}"), null, contains("draw"))).match(structured()).isPresent());
        assertTrue(matcher(new AbilityPattern(null, contains("{J}"), null, contains("draw"))).match(structured()).isPresent());
    }

    @Test
    void reports_the_matched_ability_split_into_parts() {
        RuleMatch m = matcher(new AbilityPattern(null, null, contains("token"), contains("boost")))
                .match(structured()).orElseThrow();
        assertEquals(new Ability(AbilitySlot.MAIN, "{H}", "If you control a token:", "I gain 2 boosts."), m.ability());
        assertEquals("{H} If you control a token: I gain 2 boosts.", m.matchedText());
        assertEquals("MAIN", m.source());
    }

    @Test
    void absent_part_means_no_condition() {
        assertTrue(matcher(new AbilityPattern(null, contains("{H}"), ABSENT, null)).match(structured()).isEmpty());
        assertTrue(matcher(new AbilityPattern(null, contains("{J}"), ABSENT, null)).match(structured()).isPresent());
    }

    @Test
    void scope_restricts_to_main_or_echo() {
        AbilityPattern echoDraw = new AbilityPattern(Scope.ECHO, null, null, contains("draw a card"));
        RuleMatch m = matcher(echoDraw).match(structured()).orElseThrow();
        assertEquals(AbilitySlot.ECHO, m.ability().slot());
        assertFalse(matcher(new AbilityPattern(Scope.ECHO, contains("{J}"), null, null)).match(structured()).isPresent());
    }

    @Test
    void cards_written_as_text_are_split_into_parts() {
        Card textCard = card("T1", "Rigger", "MU",
                "{J} []Discard a card from your hand.  {H} If you control a token: [][Sabotage].");
        withEcho(textCard, "{D} : []Draw a card.");
        assertTrue(matcher(new AbilityPattern(Scope.MAIN, new PartMatch(MatchMode.EXACT, "{H}"), contains("token"),
                contains("[[Sabotage]]"))).match(textCard).isPresent());
        assertTrue(matcher(new AbilityPattern(Scope.ECHO, null, ABSENT, contains("draw"))).match(textCard).isPresent());
        assertFalse(matcher(new AbilityPattern(Scope.MAIN, contains("{J}"), contains("token"), null)).match(textCard).isPresent());
    }

    @Test
    void static_marker_stored_as_condition_is_not_a_condition() {
        // As found in CardsData: {D} : + [] (in the condition slot) + effect
        Ability raw = new Ability(AbilitySlot.ECHO, "{D} :", "[]", "Pay {1} less for the next Permanent.");
        Ability normalized = Ability.of(raw.slot(), raw.trigger(), raw.condition(), raw.effect());
        assertNull(normalized.condition());
        assertEquals(raw.text(), normalized.text());
        assertEquals("{D} : []Pay {1} less for the next Permanent.", normalized.text());

        Card c = card("M1", "X", "AX", null);
        c.setAbilities(List.of(raw));
        assertTrue(matcher(new AbilityPattern(Scope.ECHO, null, ABSENT,
                new PartMatch(MatchMode.EXACT, "Pay {1} less for the next Permanent"))).match(c).isPresent());
    }

    @Test
    void exact_trigger_ignores_the_trailing_dash() {
        Card c = card("D1", "X", "MU", null);
        c.setAbilities(List.of(new Ability(AbilitySlot.MAIN, "At Dusk —", null, "Draw a card.")));
        assertTrue(matcher(new AbilityPattern(null, new PartMatch(MatchMode.EXACT, "At Dusk"), null, null)).match(c).isPresent());
    }

    @Test
    void negated_ability_condition_holds_when_no_ability_matches() {
        RuleMatcher noDraw = new RuleMatcher(new EffectRule("r-2", "No draw", null, Severity.LOW, null, true,
                List.of(RuleCondition.ability(new AbilityPattern(null, null, null, contains("draw")), true)),
                null, null, null, null));
        assertFalse(noDraw.match(structured()).isPresent());
        RuleMatch m = noDraw.match(card("N1", "Knight", "MU", "[] +2 attack.")).orElseThrow();
        assertNull(m.ability());
    }

    @Test
    void invalid_patterns_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> matcher(null));
        assertThrows(IllegalArgumentException.class, () -> matcher(new AbilityPattern(null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> matcher(new AbilityPattern(null, new PartMatch(MatchMode.REGEX, "(oops"), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> matcher(new AbilityPattern(null, new PartMatch(MatchMode.CONTAINS, " "), null, null)));
        // ABSENT only makes sense for ability parts
        assertThrows(IllegalArgumentException.class, () -> new RuleMatcher(new EffectRule("r", "x", null, null, null,
                true, List.of(new RuleCondition(RuleField.MAIN_EFFECT, MatchMode.ABSENT, "x", false)), null, null,
                null, null)));
    }

    // ------------------------------------------------------------------ fragments

    private static PartMatch fragments(PartMatch.FragmentRef... refs) {
        return new PartMatch(MatchMode.FRAGMENTS, null, List.of(refs));
    }

    private static final PartMatch.FragmentRef RESERVE_TRIGGER = new PartMatch.FragmentRef(250,
            "When a card goes from your deck or hand to Reserve\u00a0—");
    private static final PartMatch.FragmentRef TWO_BOOSTS = new PartMatch.FragmentRef(490,
            "Target Character in Reserve gains 2 boosts.");
    private static final PartMatch.FragmentRef BRASSBUG = new PartMatch.FragmentRef(118,
            "Create a [Brassbug 2/2/2] Robot token in target Expedition.");

    /** CardsData card: abilities carry their fragment ids. */
    private static Card withIds(Integer triggerId, Integer conditionId, Integer effectId, String effect) {
        Card c = card("F1", "Foundry", "AX", null);
        c.setAbilities(List.of(new Ability(AbilitySlot.MAIN, "When a card goes from your deck or hand to Reserve —",
                conditionId == null ? "[]" : "You may pay {1}. If you do:", effect, triggerId, conditionId, effectId)));
        return c;
    }

    @Test
    void fragments_match_by_id_on_cardsdata_cards() {
        RuleMatcher m = matcher(new AbilityPattern(null, fragments(RESERVE_TRIGGER), null,
                fragments(TWO_BOOSTS, BRASSBUG)));
        assertTrue(m.match(withIds(250, null, 490, "Target Character in Reserve gains 2 boosts.")).isPresent());
        assertTrue(m.match(withIds(250, 186, 118, "anything")).isPresent());
        // same text, other id: ids win over text
        assertFalse(m.match(withIds(250, null, 491, "Target Character in Reserve gains 2 boosts.")).isPresent());
        assertFalse(m.match(withIds(24, null, 490, "Target Character in Reserve gains 2 boosts.")).isPresent());
    }

    @Test
    void fragments_combine_with_other_modes() {
        // the static marker stored as condition is not a condition
        RuleMatcher free = matcher(new AbilityPattern(null, fragments(RESERVE_TRIGGER), ABSENT, fragments(TWO_BOOSTS)));
        assertTrue(free.match(withIds(250, null, 490, "Target Character in Reserve gains 2 boosts.")).isPresent());
        assertFalse(free.match(withIds(250, 186, 490, "Target Character in Reserve gains 2 boosts.")).isPresent());
    }

    @Test
    void fragments_fall_back_to_text_without_ids() {
        RuleMatcher m = matcher(new AbilityPattern(null, fragments(RESERVE_TRIGGER), null, fragments(TWO_BOOSTS)));
        Card text = card("T1", "Foundry", "AX",
                "When a card goes from your deck or hand to Reserve — []Target Character in Reserve gains 2 boosts.");
        assertTrue(m.match(text).isPresent());
        Card other = card("T2", "Foundry", "AX",
                "When a card goes from your deck or hand to Reserve — []Target Character in Reserve gains 3 boosts.");
        assertFalse(m.match(other).isPresent());
    }

    @Test
    void fragments_need_at_least_one_fragment_and_an_ability_part() {
        assertThrows(IllegalArgumentException.class,
                () -> matcher(new AbilityPattern(null, new PartMatch(MatchMode.FRAGMENTS, null, List.of()), null, null)));
        assertThrows(IllegalArgumentException.class, () -> new RuleMatcher(new EffectRule("r", "x", null, null, null,
                true, List.of(new RuleCondition(RuleField.MAIN_EFFECT, MatchMode.FRAGMENTS, "x", false)), null, null,
                null, null)));
    }
}
