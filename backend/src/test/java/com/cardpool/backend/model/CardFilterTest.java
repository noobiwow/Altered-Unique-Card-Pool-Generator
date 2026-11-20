package com.cardpool.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.card_elements.CardSet;
import com.cardpool.backend.model.card_elements.Faction;
import com.cardpool.backend.model.card_elements.LocalizedElement;
import com.cardpool.backend.model.card_elements.LocalizedNamedEntity;

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
        List<Card> filteredPool = pool.stream().filter(cardFilter::test).toList();

        // Then
        assertEquals(1, filteredPool.size());

    }

    @Test
    public void test_apply_faction_set() {
        // Given
        CardSet cardSet1 = new CardSet();
        cardSet1.setName("Beyond the Gates");
        cardSet1.setCode("CORE");
        cardSet1.setReference("CORE");
        CardSet cardSet2 = new CardSet();
        cardSet2.setName("Trial by Frost");
        cardSet2.setCode("BISE");
        cardSet2.setReference("BISE");

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
        List<Card> filteredPool = pool.stream().filter(cardFilter::test).toList();

        // Then
        assertEquals(1, filteredPool.size());

    }

    @Test
    public void test_apply_multiple_sub_types() {
        // Given
        Card warriorCard = new Card();
        warriorCard.setCardSubTypes(List.of(subType("Warrior")));
        Card mageCard = new Card();
        mageCard.setCardSubTypes(List.of(subType("Mage")));
        Card rogueCard = new Card();
        rogueCard.setCardSubTypes(List.of(subType("Rogue")));
        List<Card> pool = List.of(warriorCard, mageCard, rogueCard);

        CardFilter.Builder builder = CardFilter.builder();
        builder.subTypes(List.of("Warrior", "Mage"));
        CardFilter cardFilter = builder.build();

        // When
        List<Card> filteredPool = pool.stream().filter(cardFilter::test).toList();

        // Then
        assertEquals(2, filteredPool.size());

    }

    @Test
    public void test_apply_empty_sub_types_matches_all() {
        // Given
        Card warriorCard = new Card();
        warriorCard.setCardSubTypes(List.of(subType("Warrior")));
        Card mageCard = new Card();
        mageCard.setCardSubTypes(List.of(subType("Mage")));
        List<Card> pool = List.of(warriorCard, mageCard);

        CardFilter.Builder builder = CardFilter.builder();
        builder.subTypes(List.of());
        CardFilter cardFilter = builder.build();

        // When
        List<Card> filteredPool = pool.stream().filter(cardFilter::test).toList();

        // Then
        assertEquals(2, filteredPool.size());

    }

    @Test
    public void test_cost_constraint_parse() {
        CostConstraint atLeastFour = CostConstraint.parse("4+");
        assertEquals(4, atLeastFour.min());
        assertNull(atLeastFour.max());
        assertTrue(atLeastFour.matches(4));
        assertTrue(atLeastFour.matches(6));
        assertFalse(atLeastFour.matches(3));

        CostConstraint atMostTwo = CostConstraint.parse("2-");
        assertNull(atMostTwo.min());
        assertEquals(2, atMostTwo.max());
        assertTrue(atMostTwo.matches(2));
        assertFalse(atMostTwo.matches(3));

        CostConstraint exactlyThree = CostConstraint.parse("3");
        assertEquals(3, exactlyThree.min());
        assertEquals(3, exactlyThree.max());
        assertTrue(exactlyThree.matches(3));
        assertFalse(exactlyThree.matches(2));

        assertNull(CostConstraint.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> CostConstraint.parse("four+"));
    }

    @Test
    public void test_apply_hand_and_reserve_cost() {
        // Given
        Card cheapCard = cardWithCosts("1", "5");
        Card expensiveCard = cardWithCosts("4", "2");
        List<Card> pool = List.of(cheapCard, expensiveCard);

        // When
        CardFilter cardFilter = CardFilter.builder()
                .handCost(CostConstraint.parse("4+"))
                .reserveCost(CostConstraint.parse("2-"))
                .build();

        // Then
        List<Card> filteredPool = pool.stream().filter(cardFilter::test).toList();
        assertEquals(1, filteredPool.size());
        assertEquals(expensiveCard, filteredPool.get(0));
    }

    @Test
    public void test_cost_filter_excludes_cards_without_cost() {
        // Given
        Card cardWithoutCosts = new Card();
        Card cardWithCosts = cardWithCosts("2", "3");

        // When
        CardFilter cardFilter = CardFilter.builder()
                .handCost(CostConstraint.parse("2+"))
                .build();

        // Then
        List<Card> filteredPool = List.of(cardWithoutCosts, cardWithCosts).stream().filter(cardFilter::test).toList();
        assertEquals(1, filteredPool.size());
        assertEquals(cardWithCosts, filteredPool.get(0));
    }

    private static Card cardWithCosts(String handCost, String reserveCost) {
        Card card = new Card();
        card.setElements(Map.of("MAIN_COST", handCost, "RECALL_COST", reserveCost));
        return card;
    }

    private static LocalizedNamedEntity subType(String name) {
        LocalizedElement localizedName = new LocalizedElement();
        localizedName.setEn_label(name);
        LocalizedNamedEntity entity = new LocalizedNamedEntity();
        entity.setName(localizedName);
        return entity;
    }
}
