package com.cardpool.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.rule.AbilityPattern;
import com.cardpool.backend.model.rule.EffectRule;
import com.cardpool.backend.model.rule.MatchMode;
import com.cardpool.backend.model.rule.PartMatch;
import com.cardpool.backend.model.rule.PartMatch.FragmentRef;
import com.cardpool.backend.model.rule.RuleCondition;
import com.cardpool.backend.model.rule.RuleField;
import com.cardpool.backend.model.rule.Severity;

class RuleServiceDuplicatesTest {

    private RuleService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new RuleService(new JsonFileStore(Files.createTempDirectory("cardpool-rules")));
    }

    private static EffectRule rule(String label, Boolean enabled, List<String> factions, RuleCondition... conditions) {
        return new EffectRule(null, label, null, Severity.HIGH, null, enabled, List.of(conditions), factions, null,
                null, null);
    }

    private static RuleCondition text(RuleField field, MatchMode mode, String value) {
        return new RuleCondition(field, mode, value, false);
    }

    private static final FragmentRef TRIGGER = new FragmentRef(250,
            "When a card goes from your deck or hand to Reserve —");
    private static final FragmentRef TWO_BOOSTS = new FragmentRef(490, "Target Character in Reserve gains 2 boosts.");
    private static final FragmentRef BRASSBUG = new FragmentRef(118,
            "Create a [Brassbug 2/2/2] Robot token in target Expedition.");

    private static RuleCondition combo(PartMatch effect) {
        return RuleCondition.ability(new AbilityPattern(null,
                new PartMatch(MatchMode.FRAGMENTS, null, List.of(TRIGGER)), null, effect), false);
    }

    private static PartMatch fragments(FragmentRef... refs) {
        return new PartMatch(MatchMode.FRAGMENTS, null, List.of(refs));
    }

    @Test
    void the_same_text_cannot_be_flagged_twice() {
        service.create(rule("Ramp", true, null, text(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "Zzz unique ramp text")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.create(
                rule("Other label", true, null, text(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "  zzz UNIQUE   ramp text"))));
        assertTrue(e.getMessage().contains("Ramp"), e.getMessage());
        // another field, mode or faction scope is another rule
        assertDoesNotThrow(() -> service.create(
                rule("Echo", true, null, text(RuleField.ECHO_EFFECT, MatchMode.CONTAINS, "zzz unique ramp text"))));
        assertDoesNotThrow(() -> service.create(
                rule("Exact", true, null, text(RuleField.MAIN_EFFECT, MatchMode.EXACT, "zzz unique ramp text"))));
        assertDoesNotThrow(() -> service.create(rule("Lyra only", true, List.of("LY"),
                text(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "zzz unique ramp text"))));
    }

    @Test
    void the_same_combination_cannot_be_flagged_twice_whatever_the_order() {
        service.create(rule("Reserve combo", true, List.of("AX", "LY"), combo(fragments(TWO_BOOSTS, BRASSBUG))));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(rule("Again", true, List.of("ly", "AX"), combo(fragments(BRASSBUG, TWO_BOOSTS)))));
        // a subset is a different rule
        assertDoesNotThrow(() -> service.create(rule("Boosts", true, List.of("AX", "LY"), combo(fragments(TWO_BOOSTS)))));
        // one fragment and "is exactly" its text are the same expectation
        assertThrows(IllegalArgumentException.class, () -> service.create(rule("Boosts text", true, List.of("AX", "LY"),
                combo(new PartMatch(MatchMode.EXACT, "Target Character in Reserve gains 2 boosts")))));
    }

    @Test
    void disabled_rules_count_and_a_rule_can_be_saved_unchanged() {
        EffectRule disabled = service.create(
                rule("Off", false, null, text(RuleField.NAME, MatchMode.EXACT, "Zzz Card Name")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.create(rule("On", true, null, text(RuleField.NAME, MatchMode.EXACT, "zzz card name"))));
        assertTrue(e.getMessage().contains("disabled"), e.getMessage());
        assertDoesNotThrow(() -> service.update(disabled.id(),
                rule("Off renamed", true, null, text(RuleField.NAME, MatchMode.EXACT, "Zzz Card Name"))));
    }

    @Test
    void an_update_cannot_turn_a_rule_into_a_duplicate() {
        service.create(rule("A", true, null, text(RuleField.ANY_EFFECT, MatchMode.CONTAINS, "zzz alpha")));
        EffectRule b = service.create(rule("B", true, null, text(RuleField.ANY_EFFECT, MatchMode.CONTAINS, "zzz beta")));
        assertThrows(IllegalArgumentException.class, () -> service.update(b.id(),
                rule("B", true, null, text(RuleField.ANY_EFFECT, MatchMode.CONTAINS, "zzz alpha"))));
    }
}
