package com.cardpool.backend.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.externalApi.APICard;
import com.cardpool.backend.model.index.FilterIndex;
import com.cardpool.backend.service.ExternalAPIService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CardRepository {

    private ExternalAPIService externalAPIService = new ExternalAPIService();
    private List<Card> cards = Collections.emptyList();
    private FilterIndex index = null;

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /** All cards matching the filter, using the index. */
    public List<Card> findAll(CardFilter filter) {
        return index != null ? index.query(filter) : filter.apply(cards);
    }

    /** All cards (no filter). */
    public List<Card> getAll() {
        return Collections.unmodifiableList(cards);
    }

    public Mono<List<Card>> drawFiltered(CardFilter filter, int count, String locale) {
        return externalAPIService.streamAllCards(filter)
                .map(apiCard -> toCard(apiCard, locale)) // AlteredCard → votre Card
                // Filtre en streaming — pas de collectList() ici
                .filter(card -> matchesFilter(card, filter))
                // collectList() justifié : Fisher-Yates a besoin du pool complet
                .collectList()
                .map(pool -> drawRandom(pool, count));
    }

    // Variante : findAll seul sans tirage (compatible streaming) // A garder ?
    public Flux<Card> findAllV2(CardFilter filter, String locale) {
        return externalAPIService.streamAllCards(filter)
                .map(apiCard -> toCard(apiCard, locale))
                .filter(card -> matchesFilter(card, filter));
        // Pas de collectList() ici — le caller décide ce qu'il fait du Flux
    }

    private boolean matchesFilter(Card card, CardFilter filter) {
        // Délègue à votre logique existante
        // Si CardFilter.apply() accepte un seul élément, adaptez-le
        // Sinon extrayez les prédicats de CardFilter
        return filter.apply(List.of(card)).contains(card);
    }

    private List<Card> drawRandom(List<Card> pool, int count) {
        if (pool.isEmpty())
            return Collections.emptyList();
        int n = Math.min(count, pool.size());

        // Partial Fisher-Yates on an index array — avoids copying the full pool
        int[] idx = new int[pool.size()];
        for (int i = 0; i < idx.length; i++)
            idx[i] = i;

        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            int j = i + rng.nextInt(idx.length - i);
            int tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }

        List<Card> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            result.add(pool.get(idx[i]));
        return result;
    }

    // -------------------------------------------------------------------------
    // Distinct values (from index)
    // -------------------------------------------------------------------------

    public Set<String> distinctFactions() {
        return index != null ? index.distinctFactions() : Set.of();
    }

    public Set<String> distinctRarities() {
        return index != null ? index.distinctRarities() : Set.of();
    }

    public Set<String> distinctSets() {
        return index != null ? index.distinctSets() : Set.of();
    }

    public Set<String> distinctSubTypes() {
        return index != null ? index.distinctSubTypes() : Set.of();
    }

    public Set<String> distinctCardTypes() {
        return index != null ? index.distinctCardTypes() : Set.of();
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private Card toCard(APICard apiCard, String locale) {
        Card card = new Card();
        card.setId(apiCard.getId());
        card.setReference(apiCard.getReference());
        card.setName(apiCard.getName().get(locale));
        card.setCardType(apiCard.getCardType());
        card.setCardSet(apiCard.getSet());
        card.setCardSubTypes(apiCard.getCardSubTypes());
        card.setMainFaction(apiCard.getFaction());
        card.setRarity(apiCard.getRarity());
        Map<String, String> elements = new HashMap<>();
        elements.put("MAIN_COST", String.valueOf(apiCard.getMainCost()));
        elements.put("RECALL_COST", String.valueOf(apiCard.getRecallCost()));
        elements.put("oceanPower", String.valueOf(apiCard.getOceanPower()));
        elements.put("forestPower", String.valueOf(apiCard.getForestPower()));
        elements.put("mountainPower", String.valueOf(apiCard.getMountainPower()));
        card.setElements(elements);
        card.setMainEffect(apiCard.getMainEffect().get(locale));
        card.setEchoEffect(apiCard.getEchoEffect().get(locale));
        card.setImagePath(apiCard.getImagePath().get(locale));
        card.setBanned(apiCard.isBanned());
        card.setSuspended(apiCard.isSuspended());
        card.setExclusive(apiCard.isExclusive());
        return card;
    }
}