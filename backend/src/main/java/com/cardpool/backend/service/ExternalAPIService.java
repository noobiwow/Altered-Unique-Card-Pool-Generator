package com.cardpool.backend.service;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.cardpool.backend.model.externalApi.APICard;
import com.cardpool.backend.model.externalApi.CardAPIOutput;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Service
public class ExternalAPIService {
    private final WebClient webClient;
    private static final String API_MIME_TYPE = "application/json";
    private static final String BASE_URL = "https://cards.alteredcore.org/api/";

    public ExternalAPIService() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, API_MIME_TYPE)
                .build();
    }

    public Flux<APICard> streamAllCards() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cards")
                        .queryParam("rarity[]", "UNIQUE")
                        .queryParam("itemsPerPage", 1000)
                        .build())
                .retrieve()
                .bodyToMono(CardAPIOutput.class)
                .flatMapMany(firstPage -> {
                    int totalPages = firstPage.getLastPage();
                    Flux<APICard> firstPageFlux = Flux.fromIterable(firstPage.getMember());
                    Flux<APICard> remainingPagesFlux = Flux
                            .range(2, totalPages - 1)
                            .flatMap(page -> fetchPage(page)
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

    private Flux<APICard> fetchPage(int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cards")
                        .queryParam("page", page)
                        .build())
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
