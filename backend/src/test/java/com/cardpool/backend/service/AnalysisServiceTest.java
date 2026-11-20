package com.cardpool.backend.service;

import static com.cardpool.backend.TestCards.card;
import static com.cardpool.backend.TestCards.withEcho;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cardpool.backend.dto.AnalysisStatus;
import com.cardpool.backend.dto.CardAnalysis;
import com.cardpool.backend.dto.CreateReportRequest;
import com.cardpool.backend.dto.VerdictRequest;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.report.Report;
import com.cardpool.backend.model.rule.EffectRule;
import com.cardpool.backend.model.rule.MatchMode;
import com.cardpool.backend.model.rule.RuleCondition;
import com.cardpool.backend.model.rule.RuleField;
import com.cardpool.backend.model.rule.Severity;
import com.cardpool.backend.model.verdict.VerdictStatus;

class AnalysisServiceTest {

    @TempDir
    Path dataDir;

    private RuleService rules;
    private VerdictService verdicts;
    private AnalysisService analysis;
    private JsonFileStore store;

    @BeforeEach
    void setUp() {
        store = new JsonFileStore(dataDir);
        rules = new RuleService(store);
        verdicts = new VerdictService(store);
        analysis = new AnalysisService(rules, verdicts);
    }

    private static final String ANCHORED = "[] You may target a Character with Hand Cost {3} or less other than me, it gains [[Anchored]].";

    // --- legacy effect-warnings.json behaviour, now through migrated rules ---

    @Test
    void seeds_rules_file_from_legacy_config() {
        assertFalse(rules.list().isEmpty());
        assertTrue(Files.exists(dataDir.resolve("rules.json")));
        // reloading from disk gives the same rules
        assertEquals(rules.list().size(), new RuleService(store).list().size());
    }

    @Test
    void flags_legacy_pattern() {
        List<CardAnalysis> result = analysis.analyze(List.of(card("REF_2", "Looter", null,
                "Put the top two cards of your deck in your Mana zone (as exhausted Mana Orbs).")));
        assertEquals(1, result.size());
        assertEquals(AnalysisStatus.FLAGGED, result.get(0).status());
    }

    @Test
    void ignores_clean_cards() {
        assertTrue(analysis.analyze(List.of(card("REF_3", "Knight", null, "[] +2 attack."),
                card("REF_4", "No effect", null, null))).isEmpty());
    }

    @Test
    void flags_watched_card_name_only_for_its_faction() {
        List<CardAnalysis> result = analysis.analyze(List.of(
                card("REF_9", "Foundry Armorer", "BR", "[] +2 attack."),
                card("REF_10", "Foundry Armorer", "AX", "[] +2 attack."),
                card("REF_5", "Moonlight Jellyfish", null, "[] +2 attack.")));
        assertEquals(List.of("REF_9", "REF_5"), result.stream().map(CardAnalysis::reference).toList());
    }

    @Test
    void flags_effect_line_in_main_but_not_similar_or_echo() {
        Card main = card("REF_7", "Rigger", null, ANCHORED);
        Card similar = card("REF_8", "Snoozer", null, ANCHORED.replace("Anchored", "Fleeting"));
        Card echo = card("REF_11", "Rigger", null, "[] +2 attack.");
        withEcho(echo, ANCHORED);
        List<CardAnalysis> result = analysis.analyze(List.of(main, similar, echo));
        assertEquals(List.of("REF_7"), result.stream().map(CardAnalysis::reference).toList());
    }

    // --- rules management ---

    @Test
    void created_rule_is_persisted_and_disabled_rule_is_ignored() {
        EffectRule created = rules.create(new EffectRule(null, "Draw", "Too much card advantage", Severity.HIGH,
                "Card draw", true, List.of(new RuleCondition(RuleField.MAIN_EFFECT, MatchMode.CONTAINS,
                        "draw three", false)), List.of(), "REF_X", null, null));
        Card card = card("REF_12", "Scholar", "LY", "[] Draw three cards.");
        assertEquals(1, analysis.analyze(List.of(card)).size());
        assertTrue(new RuleService(store).get(created.id()).isPresent());

        rules.update(created.id(), new EffectRule(null, "Draw", null, Severity.HIGH, null, false,
                created.conditions(), null, null, null, null));
        assertTrue(analysis.analyze(List.of(card)).isEmpty());
        assertEquals(created.createdAt(), rules.get(created.id()).orElseThrow().createdAt());
    }

    // --- dormant matches ---

    @Test
    void disabled_and_other_faction_rules_are_reported_as_dormant_only() {
        EffectRule off = rules.create(new EffectRule(null, "Draw four", null, Severity.HIGH, null, false,
                List.of(new RuleCondition(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "draw four", false)), List.of(),
                null, null, null));
        EffectRule lyraOnly = rules.create(new EffectRule(null, "Draw four (Lyra)", null, Severity.MEDIUM, null, true,
                List.of(new RuleCondition(RuleField.MAIN_EFFECT, MatchMode.CONTAINS, "four cards", false)),
                List.of("LY"), null, null, null));
        Card axiom = card("REF_20", "Archivist", "AX", "[] Draw four cards.");

        // the real analysis is unchanged: nothing applies to this card
        assertTrue(analysis.analyze(List.of(axiom)).isEmpty());

        List<CardAnalysis> result = analysis.analyzeWithDormant(List.of(axiom));
        assertEquals(1, result.size());
        CardAnalysis a = result.get(0);
        assertNull(a.status());
        assertTrue(a.warnings().isEmpty());
        assertEquals(2, a.dormant().size());
        var byRule = a.dormant().stream().collect(java.util.stream.Collectors.toMap(d -> d.ruleId(), d -> d));
        assertTrue(byRule.get(off.id()).disabled());
        assertTrue(byRule.get(off.id()).factions().isEmpty());
        assertFalse(byRule.get(lyraOnly.id()).disabled());
        assertEquals(List.of("LY"), byRule.get(lyraOnly.id()).factions());

        // on a Lyra card the faction rule is a real warning, the disabled one stays dormant
        CardAnalysis lyra = analysis.analyzeWithDormant(List.of(card("REF_21", "Seer", "LY", "[] Draw four cards.")))
                .get(0);
        assertEquals(AnalysisStatus.FLAGGED, lyra.status());
        assertEquals(List.of(lyraOnly.id()), lyra.warnings().stream().map(w -> w.ruleId()).toList());
        assertEquals(List.of(off.id()), lyra.dormant().stream().map(d -> d.ruleId()).toList());
    }

    // --- verdicts ---

    @Test
    void approved_card_is_cleared_until_a_new_rule_matches_it() {
        Card card = card("REF_7", "Rigger", null, ANCHORED);
        List<String> ruleIds = analysis.analyze(List.of(card)).get(0).warnings().stream()
                .map(w -> w.ruleId()).toList();
        verdicts.set("REF_7", new VerdictRequest("Rigger", VerdictStatus.APPROVED, "Fine in this format", ruleIds));

        assertEquals(AnalysisStatus.CLEARED, analysis.analyze(List.of(card)).get(0).status());

        rules.create(new EffectRule(null, "Anchored", null, Severity.LOW, null, true,
                List.of(new RuleCondition(RuleField.KEYWORD, MatchMode.EXACT, "Anchored", false)), null, null,
                null, null));
        CardAnalysis again = analysis.analyze(List.of(card)).get(0);
        assertEquals(AnalysisStatus.FLAGGED, again.status());
        assertEquals(1, again.warnings().stream().filter(w -> !w.reviewed()).count());
    }

    @Test
    void rejected_cards_are_reported_even_without_warnings() {
        verdicts.set("REF_3", new VerdictRequest("Knight", VerdictStatus.REJECTED, "Broken with X", null));
        List<CardAnalysis> result = analysis.analyze(List.of(card("REF_3", "Knight", null, "[] +2 attack.")));
        assertEquals(AnalysisStatus.REJECTED, result.get(0).status());
        assertEquals(java.util.Set.of("REF_3"), new VerdictService(store).rejectedReferences());

        verdicts.clear("REF_3");
        assertTrue(analysis.analyze(List.of(card("REF_3", "Knight", null, "[] +2 attack."))).isEmpty());
    }

    // --- reports ---

    @Test
    void report_snapshots_findings_and_can_be_reloaded() {
        ReportService reports = new ReportService(store, analysis);
        Report report = reports.create(new CreateReportRequest("Season 2 review", "frontier-next", null,
                List.of(card("REF_7", "Rigger", "MU", ANCHORED), card("REF_3", "Knight", "MU", "[] +2 attack.")),
                List.of(), false));

        assertEquals(2, report.poolSize());
        assertEquals(1, report.cards().size());
        assertEquals(1, report.summary().flagged());
        assertEquals(1, report.summary().bySeverity().get("MEDIUM"));
        assertEquals("Season 2 review", reports.get(report.id()).orElseThrow().title());
        assertEquals(1, reports.list().size());
        assertTrue(reports.get("../rules").isEmpty());
        assertTrue(reports.delete(report.id()));
    }
}
