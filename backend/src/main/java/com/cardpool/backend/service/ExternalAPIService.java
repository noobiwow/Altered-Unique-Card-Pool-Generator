package com.cardpool.backend.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.CardFilter.Criteria;
import com.cardpool.backend.model.externalApi.APICard;
import com.cardpool.backend.model.externalApi.CardAPIOutput;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Service
public class ExternalAPIService {
    private final WebClient webClient;
    private static final String API_MIME_TYPE = "application/json";
    private static final String BASE_URL = "https://cards.alteredcore.org/api";

    public ExternalAPIService() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, API_MIME_TYPE)
                .build();
    }

    public URI buildURI(CardFilter cardFilter, UriBuilder uriBuilder, int page) {
        Criteria criteria = cardFilter.getCriteria();
        uriBuilder.path("/cards");
        uriBuilder.queryParam("rarity[]", "UNIQUE");
        uriBuilder.queryParam("itemsPerPage", 1000);
        if (page > 1) {
            uriBuilder.queryParam("page", page);
        }
        if (criteria.set() != null) {
            uriBuilder.queryParam("set.reference", cardFilter.getCriteria().set());
        }
        if (criteria.faction() != null) {
            uriBuilder.queryParam("faction.code[]", cardFilter.getCriteria().faction());
        }
        System.out.println(uriBuilder.toUriString());
        return uriBuilder.build();
    }

    public Flux<APICard> streamAllCards(CardFilter cardFilter) {
        return webClient.get()
                .uri(uriBuilder -> buildURI(cardFilter, uriBuilder, 1))
                .retrieve()
                .bodyToMono(CardAPIOutput.class)
                .flatMapMany(firstPage -> {
                    int totalPages = firstPage.getLastPage();
                    Flux<APICard> firstPageFlux = Flux.fromIterable(firstPage.getMember());
                    Flux<APICard> remainingPagesFlux = Flux
                            .range(2, totalPages - 1)
                            .flatMap(page -> fetchPage(page, cardFilter)
                                    .retryWhen(
                                            Retry.backoff(3, Duration.ofSeconds(1)))
                                    .onErrorResume(e -> {
                                        // skip failed page (log if needed)
                                        return Flux.empty();
                                    }),
                                    15 // concurrency limit (tune: 5–15)
                    );
                    return Flux.concat(firstPageFlux, remainingPagesFlux);
                });
    }

    private Flux<APICard> fetchPage(int page, CardFilter cardFilter) {
        return webClient.get()
                .uri(uriBuilder -> buildURI(cardFilter, uriBuilder, page))
                .retrieve()
                .bodyToMono(CardAPIOutput.class)

                .flatMapMany(response -> {
                    if (response == null || response.getMember() == null) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(response.getMember());
                });
    }
}
