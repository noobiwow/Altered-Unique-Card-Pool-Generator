package com.cardpool.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.card_elements.CardSet;
import com.cardpool.backend.model.card_elements.Faction;

public class CardFilterTest {

    @Test
    public void test_apply_faction() {
        // Given
        Faction munaFaction = new Faction();
        munaFaction.setName("Muna");
        munaFaction.setCode("MU");
        Faction ordisFaction = new Faction();
        ordisFaction.setName("Ordis");
        ordisFaction.setCode("OR");

        Card munaCard = new Card();
        munaCard.setMainFaction(munaFaction);
        Card ordisCard = new Card();
        ordisCard.setMainFaction(ordisFaction);
        List<Card> pool = List.of(munaCard, ordisCard);

        CardFilter.Builder builder = CardFilter.builder();
        builder.faction("OR");
        CardFilter cardFilter = builder.build();

        // When
        List<Card> filteredPool = cardFilter.apply(pool);

        // Then
        assertEquals(1, filteredPool.size());

    }

    @Test
    public void test_apply_faction_set() {
        // Given
        CardSet cardSet1 = new CardSet();
        cardSet1.setName("Beyond the Gates");
        cardSet1.setCode("CORE");
        CardSet cardSet2 = new CardSet();
        cardSet2.setName("Trial by Frost");
        cardSet2.setCode("BISE");

        Faction munaFaction = new Faction();
        munaFaction.setName("Muna");
        munaFaction.setCode("MU");
        Faction ordisFaction = new Faction();
        ordisFaction.setName("Ordis");
        ordisFaction.setCode("OR");

        Card munaCard = new Card();
        munaCard.setMainFaction(munaFaction);
        munaCard.setCardSet(cardSet1);

        Card munaCard2 = new Card();
        munaCard2.setMainFaction(munaFaction);
        munaCard2.setCardSet(cardSet2);

        Card ordisCard = new Card();
        ordisCard.setMainFaction(ordisFaction);
        ordisCard.setCardSet(cardSet2);

        List<Card> pool = List.of(munaCard, ordisCard, munaCard2);

        CardFilter.Builder builder = CardFilter.builder();
        builder.faction("OR");
        builder.set("BISE");
        CardFilter cardFilter = builder.build();

        // When
        List<Card> filteredPool = cardFilter.apply(pool);

        // Then
        assertEquals(1, filteredPool.size());

    }
}
