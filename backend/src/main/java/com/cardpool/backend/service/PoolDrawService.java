package com.cardpool.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.ReservoirSampler;
import com.cardpool.backend.source.Candidate;
import com.cardpool.backend.source.CardSource;

/** Draws random pools from the card source. */
@Service
public class PoolDrawService {

    private final CardSource source;

    public PoolDrawService(CardSource source) {
        this.source = source;
    }

    static Map<String, Integer> computeQuotas(Map<String, Double> weights, int totalCount) {
        Map<String, Integer> quotas = new LinkedHashMap<>();
        Map<String, Double> remainders = new LinkedHashMap<>();
        int allocated = 0;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double exact = entry.getValue() * totalCount;
            int floor = (int) Math.floor(exact);
            quotas.put(entry.getKey(), floor);
            remainders.put(entry.getKey(), exact - floor);
            allocated += floor;
        }

        int delta = totalCount - allocated;
        if (delta > 0) {
            remainders.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(delta)
                    .forEach(e -> quotas.merge(e.getKey(), 1, Integer::sum));
        }
        return quotas;
    }

    /**
     * Draws {@code count} cards for every requested faction in a single pass
     * over the card source, then returns the combined (shuffled) global pool.
     * With set weights, each faction's cards are split across sets by quota.
     * <p>
     * The given filter must not contain a faction criterion; faction selection
     * is handled by this method so that all factions share the same filters and
     * set weights.
     */
    public List<Card> drawPerFaction(CardFilter filter, List<String> factionCodes, int count) {
        Map<String, Double> weights = filter.getCriteria().setWeights();
        boolean weighted = weights != null && !weights.isEmpty();

        // faction -> (set code or "*") -> sampler
        Map<String, Map<String, ReservoirSampler<Candidate>>> samplers = new LinkedHashMap<>();
        for (String rawCode : factionCodes) {
            Map<String, ReservoirSampler<Candidate>> perSet = new LinkedHashMap<>();
            if (weighted) {
                computeQuotas(weights, count).forEach((setCode, quota) -> perSet.put(setCode, new ReservoirSampler<>(quota)));
            } else {
                perSet.put("*", new ReservoirSampler<>(count));
            }
            samplers.put(rawCode.toUpperCase(Locale.ROOT), perSet);
        }

        source.scan(filter, null, candidate -> {
            String faction = candidate.factionCode();
            Map<String, ReservoirSampler<Candidate>> perSet = faction == null ? null
                    : samplers.get(faction.toUpperCase(Locale.ROOT));
            if (perSet == null) {
                return;
            }
            ReservoirSampler<Candidate> sampler = weighted
                    ? (candidate.setCode() == null ? null : perSet.get(candidate.setCode()))
                    : perSet.get("*");
            if (sampler != null) {
                sampler.add(candidate);
            }
        });

        List<Card> result = new ArrayList<>();
        samplers.values().forEach(perSet -> perSet.values()
                .forEach(sampler -> sampler.getItems().forEach(c -> result.add(c.card()))));
        Collections.shuffle(result);
        return result;
    }
}
