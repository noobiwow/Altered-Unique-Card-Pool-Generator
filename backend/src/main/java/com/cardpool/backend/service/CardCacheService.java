package com.cardpool.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.externalApi.APICard;
import reactor.core.publisher.Mono;

@Service
public class CardCacheService {

    private final ExternalAPIService externalAPIService = new ExternalAPIService();
    private volatile Mono<List<APICard>> cache;
    private volatile Instant cacheExpiry = Instant.MIN;
    private static final Duration TTL = Duration.ofHours(1);

    public Mono<List<APICard>> getAllCards(CardFilter cardFilter) {
        if (cache == null || Instant.now().isAfter(cacheExpiry)) {
            synchronized (this) {
                if (cache == null || Instant.now().isAfter(cacheExpiry)) {
                    cache = externalAPIService.streamAllCards(cardFilter)
                            .collectList()
                            .cache();
                    cacheExpiry = Instant.now().plus(TTL);
                }
            }
        }
        return cache;
    }
}
