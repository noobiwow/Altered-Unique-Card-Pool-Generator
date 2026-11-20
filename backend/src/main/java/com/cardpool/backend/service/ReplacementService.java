package com.cardpool.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cardpool.backend.dto.ReplacementRequest;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.ReservoirSampler;
import com.cardpool.backend.model.rule.RuleMatcher;
import com.cardpool.backend.model.verdict.Verdict;
import com.cardpool.backend.source.Candidate;
import com.cardpool.backend.source.CardSource;

/**
 * Proposes replacement candidates for a card of a pool: same faction and same
 * set (which keeps per-set weights intact), matching the pool filters when they
 * are known (otherwise the same card type), not already in the pool, not
 * rejected and without unreviewed warnings.
 */
@Service
public class ReplacementService {

    static final int DEFAULT_COUNT = 24;
    static final int MAX_COUNT = 100;
    /** Candidates are over-sampled, then the flagged ones are dropped. */
    private static final int OVERSAMPLING = 4;

    private final CardSource cardSource;
    private final PoolService poolService;
    private final AnalysisService analysisService;
    private final RuleService ruleService;
    private final VerdictService verdictService;

    public ReplacementService(CardSource cardSource, PoolService poolService,
            AnalysisService analysisService, RuleService ruleService, VerdictService verdictService) {
        this.cardSource = cardSource;
        this.poolService = poolService;
        this.analysisService = analysisService;
        this.ruleService = ruleService;
        this.verdictService = verdictService;
    }

    public List<Card> candidates(ReplacementRequest request) {
        if (request == null || request.card() == null || request.card().getReference() == null) {
            throw new IllegalArgumentException("The card to replace is required");
        }
        Card target = request.card();
        String faction = target.getMainFaction() == null ? null : target.getMainFaction().getCode();
        if (faction == null) {
            throw new IllegalArgumentException("The card to replace has no faction");
        }
        String setCode = target.getSetCode();
        int count = request.count() == null || request.count() <= 0 ? DEFAULT_COUNT
                : Math.min(request.count(), MAX_COUNT);

        Set<String> excluded = new HashSet<>(verdictService.rejectedReferences());
        excluded.add(target.getReference());
        if (request.poolReferences() != null) {
            excluded.addAll(request.poolReferences());
        }
        CardFilter filter;
        if (request.filter() != null) {
            filter = poolService.buildFilter(request.filter(), faction, excluded);
        } else {
            CardFilter.Builder builder = CardFilter.builder().faction(faction).excludeReferences(excluded);
            if (target.getCardType() != null && target.getCardType().getName() != null) {
                builder.cardType(target.getCardTypeName());
            }
            filter = builder.build();
        }

        // Same set as the replaced card: keeps per-set weights and only scans one set.
        ReservoirSampler<Candidate> sampler = new ReservoirSampler<>(count * OVERSAMPLING);
        cardSource.scan(filter, setCode, sampler::add);
        List<Card> sampled = sampler.getItems().stream().map(Candidate::card).toList();

        List<RuleMatcher> matchers = ruleService.activeMatchers();
        Map<String, Verdict> verdicts = verdictService.snapshot();
        List<Card> result = new ArrayList<>();
        for (Card card : sampled) {
            if (analysisService.isClean(card, matchers, verdicts)) {
                result.add(card);
                if (result.size() == count) {
                    break;
                }
            }
        }
        return result;
    }
}
