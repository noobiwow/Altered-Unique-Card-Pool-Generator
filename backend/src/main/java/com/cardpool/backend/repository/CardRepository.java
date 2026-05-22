package com.cardpool.backend.repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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