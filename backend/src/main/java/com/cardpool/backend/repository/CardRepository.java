package com.cardpool.backend.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.ReservoirSampler;
import com.cardpool.backend.service.CardCacheService;

import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

@Repository
public class CardRepository {

    private CardCacheService cacheService = new CardCacheService();

    private Map<String, Integer> computeQuotas(
            Map<String, Double> weights,
            int totalCount) {
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
     * over the card cache, then returns the combined (shuffled) global pool.
     * <p>
     * The given filter must not contain a faction criterion; faction selection
     * is handled by this method so that all factions share the same filters and
     * set weights.
     */
    public Mono<List<Card>> drawFilteredPerFactionV3(CardFilter filter, List<String> factionCodes, int count,
            String locale) {

        CardFilter.Criteria criteria = filter.getCriteria();
        Map<String, Double> weights = criteria.setWeights();
        boolean weighted = weights != null && !weights.isEmpty();

        Map<String, ReservoirSampler<Card>> factionSamplers = new LinkedHashMap<>();
        Map<String, Map<String, ReservoirSampler<Card>>> factionSetSamplers = new LinkedHashMap<>();

        for (String rawCode : factionCodes) {
            String code = rawCode.toUpperCase(Locale.ROOT);
            if (weighted) {
                Map<String, ReservoirSampler<Card>> perSet = new LinkedHashMap<>();
                computeQuotas(weights, count)
                        .forEach((setCode, quota) -> perSet.put(setCode, new ReservoirSampler<>(quota)));
                factionSetSamplers.put(code, perSet);
            } else {
                factionSamplers.put(code, new ReservoirSampler<>(count));
            }
        }

        return cacheService.getAllUniqueCards()
                .filter(filter::test)
                .doOnNext(card -> {
                    String code = card.getMainFaction() != null && card.getMainFaction().getCode() != null
                            ? card.getMainFaction().getCode().toUpperCase(Locale.ROOT)
                            : null;
                    if (code == null) {
                        return;
                    }

                    if (weighted) {
                        Map<String, ReservoirSampler<Card>> perSet = factionSetSamplers.get(code);
                        if (perSet == null || card.getCardSet() == null || card.getCardSet().getCode() == null) {
                            return;
                        }
                        ReservoirSampler<Card> sampler = perSet.get(card.getCardSet().getCode());
                        if (sampler != null) {
                            sampler.add(card);
                        }
                    } else {
                        ReservoirSampler<Card> sampler = factionSamplers.get(code);
                        if (sampler != null) {
                            sampler.add(card);
                        }
                    }
                })
                .then(Mono.fromSupplier(() -> {
                    List<Card> result = new ArrayList<>();
                    if (weighted) {
                        factionSetSamplers.values().forEach(
                                perSet -> perSet.values().forEach(sampler -> result.addAll(sampler.getItems())));
                    } else {
                        factionSamplers.values().forEach(sampler -> result.addAll(sampler.getItems()));
                    }
                    Collections.shuffle(result);
                    return result;
                }));
    }

    public Mono<List<Card>> drawFilteredV3(CardFilter filter, int count, String locale) {

        CardFilter.Criteria criteria = filter.getCriteria();
        Map<String, Double> weights = criteria.setWeights();
        /*
         * No gauge system:
         * fallback to global reservoir sampling
         */
        if (weights == null || weights.isEmpty()) {
            return cacheService.getAllUniqueCards()
                    .filter(card -> filter.test(card))
                    .collect(() -> new ReservoirSampler<Card>(count), ReservoirSampler::add)
                    .map(ReservoirSampler::getItems)
                    .doOnNext(Collections::shuffle);
        }
        /*
         * Compute quotas per set
         */
        Map<String, Integer> quotas = computeQuotas(weights, count);
        /*
         * One sampler per set
         */
        Map<String, ReservoirSampler<Card>> samplers = new HashMap<>();
        quotas.forEach((setCode, quota) -> {
            samplers.put(setCode, new ReservoirSampler<>(quota));
        });
        return cacheService.getAllUniqueCards()
                .filter(card -> filter.test(card))
                .doOnNext(card -> {
                    if (card.getCardSet() == null) {
                        return;
                    }
                    String setCode = card.getCardSet().getCode();
                    if (setCode == null) {
                        return;
                    }
                    ReservoirSampler<Card> sampler = samplers.get(setCode);
                    if (sampler != null) {
                        sampler.add(card);
                    }
                })
                /*
                 * Final assembly
                 */
                .then(Mono.fromSupplier(() -> {
                    List<Card> result = samplers.values()
                            .stream()
                            .flatMap(s -> s.getItems().stream())
                            .collect(Collectors.toList());
                    Collections.shuffle(result);
                    return result;
                }));
    }

}