package com.cardpool.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cardpool.backend.dto.AnalysisStatus;
import com.cardpool.backend.dto.CardAnalysis;
import com.cardpool.backend.dto.DormantMatch;
import com.cardpool.backend.dto.RuleMatch;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.rule.CardContext;
import com.cardpool.backend.model.rule.EffectRule;
import com.cardpool.backend.model.rule.RuleMatcher;
import com.cardpool.backend.model.verdict.Verdict;
import com.cardpool.backend.model.verdict.VerdictStatus;

/**
 * Runs the enabled rules over cards and combines the matches with the team
 * verdicts to decide whether each card is flagged, cleared or rejected.
 */
@Service
public class AnalysisService {

    private final RuleService ruleService;
    private final VerdictService verdictService;

    public AnalysisService(RuleService ruleService, VerdictService verdictService) {
        this.ruleService = ruleService;
        this.verdictService = verdictService;
    }

    /**
     * Returns an entry for every card that matches at least one enabled rule or
     * was rejected by the team. Cards without findings are omitted.
     */
    public List<CardAnalysis> analyze(List<Card> cards) {
        List<CardAnalysis> result = new ArrayList<>();
        if (cards == null) {
            return result;
        }
        List<RuleMatcher> matchers = ruleService.activeMatchers();
        Map<String, Verdict> verdicts = verdictService.snapshot();
        for (Card card : cards) {
            if (card == null || card.getReference() == null) {
                continue;
            }
            CardAnalysis analysis = analyze(card, matchers, verdicts.get(card.getReference()));
            if (analysis != null) {
                result.add(analysis);
            }
        }
        return result;
    }

    /**
     * Same as {@link #analyze(List)}, each entry also listing the dormant
     * matches (disabled rules, rules restricted to other factions). Cards with
     * only dormant matches get an entry whose status is null.
     */
    public List<CardAnalysis> analyzeWithDormant(List<Card> cards) {
        List<CardAnalysis> result = new ArrayList<>();
        if (cards == null) {
            return result;
        }
        List<RuleMatcher> all = ruleService.allMatchers();
        List<RuleMatcher> active = all.stream().filter(m -> m.rule().active()).toList();
        Map<String, Verdict> verdicts = verdictService.snapshot();
        for (Card card : cards) {
            if (card == null || card.getReference() == null) {
                continue;
            }
            CardContext ctx = new CardContext(card);
            CardAnalysis analysis = analyze(ctx, active, verdicts.get(card.getReference()));
            List<DormantMatch> dormant = dormant(ctx, all);
            if (analysis == null && !dormant.isEmpty()) {
                analysis = new CardAnalysis(card.getReference(), card.getName(), factionCode(card), null, List.of(),
                        verdicts.get(card.getReference()));
            }
            if (analysis != null) {
                result.add(dormant.isEmpty() ? analysis : analysis.withDormant(dormant));
            }
        }
        return result;
    }

    private static List<DormantMatch> dormant(CardContext ctx, List<RuleMatcher> matchers) {
        List<DormantMatch> result = new ArrayList<>();
        for (RuleMatcher matcher : matchers) {
            EffectRule rule = matcher.rule();
            boolean applies = matcher.appliesTo(ctx);
            if (rule.active() && applies) {
                continue; // a real warning, if it matches
            }
            matcher.matchIgnoringFactions(ctx).ifPresent(m -> result.add(new DormantMatch(m.ruleId(), m.label(),
                    m.severity(), m.matchedText(), m.source(), m.ability(), !rule.active(),
                    applies || rule.factions() == null ? List.of() : List.copyOf(rule.factions()))));
        }
        return result;
    }

    /** True when the card has no unreviewed warning and is not rejected. */
    public boolean isClean(Card card, List<RuleMatcher> matchers, Map<String, Verdict> verdicts) {
        CardAnalysis analysis = analyze(card, matchers, verdicts.get(card.getReference()));
        return analysis == null || analysis.status() == AnalysisStatus.CLEARED;
    }

    /** Matches of a single (possibly not yet saved) rule over the given cards. */
    public List<CardAnalysis> preview(EffectRule rule, List<Card> cards) {
        RuleMatcher matcher = new RuleMatcher(rule);
        List<CardAnalysis> result = new ArrayList<>();
        if (cards == null) {
            return result;
        }
        for (Card card : cards) {
            if (card == null) {
                continue;
            }
            matcher.match(card).ifPresent(match -> result.add(new CardAnalysis(card.getReference(), card.getName(),
                    factionCode(card), AnalysisStatus.FLAGGED, List.of(match), null)));
        }
        return result;
    }

    private CardAnalysis analyze(Card card, List<RuleMatcher> matchers, Verdict verdict) {
        return analyze(new CardContext(card), matchers, verdict);
    }

    private CardAnalysis analyze(CardContext ctx, List<RuleMatcher> matchers, Verdict verdict) {
        Card card = ctx.card();
        boolean approved = verdict != null && verdict.status() == VerdictStatus.APPROVED;
        Set<String> reviewed = approved && verdict.reviewedRuleIds() != null
                ? new HashSet<>(verdict.reviewedRuleIds())
                : Set.of();

        List<RuleMatch> matches = new ArrayList<>();
        for (RuleMatcher matcher : matchers) {
            matcher.match(ctx).ifPresent(m -> matches.add(m.withReviewed(reviewed.contains(m.ruleId()))));
        }

        AnalysisStatus status;
        if (verdict != null && verdict.status() == VerdictStatus.REJECTED) {
            status = AnalysisStatus.REJECTED;
        } else if (matches.isEmpty()) {
            return null;
        } else if (approved && matches.stream().allMatch(RuleMatch::reviewed)) {
            status = AnalysisStatus.CLEARED;
        } else {
            status = AnalysisStatus.FLAGGED;
        }
        return new CardAnalysis(card.getReference(), card.getName(), factionCode(card), status,
                List.copyOf(matches), verdict);
    }

    private static String factionCode(Card card) {
        return card.getMainFaction() == null ? null : card.getMainFaction().getCode();
    }
}
