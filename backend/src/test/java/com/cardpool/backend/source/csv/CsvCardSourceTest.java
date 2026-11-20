package com.cardpool.backend.source.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.AbilitySlot;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.CostConstraint;

class CsvCardSourceTest {

    static CardsData data;
    static CsvCardSource source;

    @BeforeAll
    static void load() throws Exception {
        data = CardsData.load(Path.of("src/test/resources/cardsdata"));
        source = new CsvCardSource(data);
    }

    private static Card card(String reference) {
        return source.findByReferences(List.of(reference)).get(0);
    }

    private static List<String> scan(CardFilter filter, String setCode) {
        List<String> refs = new ArrayList<>();
        source.scan(filter, setCode, c -> refs.add(c.card().getReference()));
        return refs;
    }

    /** Reference implementation: build every card and apply CardFilter.test. */
    private static List<String> expected(CardFilter filter) {
        List<String> refs = new ArrayList<>();
        for (Segment s : data.segments) {
            for (int row = 0; row < s.size; row++) {
                Card c = data.card(s, row);
                if (filter.test(c)) {
                    refs.add(c.getReference());
                }
            }
        }
        return refs;
    }

    @Test
    void loads_every_set() {
        assertEquals(4, data.size());
        assertEquals(List.of("CORE", "EOLE"), data.setCodes());
    }

    @Test
    void rebuilds_cards_from_the_csv_rows() {
        Card c = card("ALT_EOLE_B_MU_107_U_1");
        assertEquals("Brave Cub", c.getName());
        assertEquals("MU", c.getMainFaction().getCode());
        assertEquals("Muna", c.getMainFaction().getName());
        assertEquals("EOLE", c.getCardSet().getCode());
        assertEquals("Roots of Corruption", c.getSetName());
        assertEquals("Character", c.getCardTypeName());
        assertEquals("Personnage", c.getCardType().getName().getFr_label());
        assertTrue(c.hasSubType("animal"));
        assertEquals("UNIQUE", c.getRarityName());
        assertEquals(2, c.getMainCost());
        assertEquals(1, c.getRecallCost());
        assertEquals("3", c.getElementValue("MOUNTAIN_POWER"));
        // abilities ordered by DisplayIndex, no space after the static marker, keywords displayed
        assertEquals("[]Draw a card.  {J} If you control a token: You may target a Character, it gains [Anchored].",
                c.getMainEffect());
        assertEquals("{D} : I gain 2 boosts.", c.getEchoEffect());
        assertEquals(List.of(
                new Ability(AbilitySlot.MAIN, "[]", null, "Draw a card.", 2, null, 20),
                new Ability(AbilitySlot.MAIN, "{J}", "If you control a token:",
                        "You may target a Character, it gains [Anchored].", 1, 10, 21),
                new Ability(AbilitySlot.ECHO, "{D} :", null, "I gain 2 boosts.", 3, null, -5)), c.getAbilities());
        assertEquals(c.getMainEffect(), c.getElementValue("MAIN_EFFECT"));
        assertNull(c.getImagePath());
    }

    @Test
    void handles_flags_markers_and_missing_values() {
        Card banned = card("ALT_EOLE_B_MU_107_U_2");
        assertTrue(banned.isBanned());
        assertEquals("AX", banned.getMainFaction().getCode());
        assertEquals("When I'm played — Draw a card.", banned.getMainEffect());
        assertEquals("", banned.getEchoEffect());

        Card spell = card("ALT_EOLE_B_AX_120_U_3");
        assertTrue(spell.isSuspended());
        assertEquals("[][Sabotage].", spell.getMainEffect());
        assertNull(spell.getElementValue("FOREST_POWER"));

        Card noEffect = card("ALT_CORE_B_MU_01_U_9");
        assertEquals("", noEffect.getMainEffect());
    }

    @Test
    void finds_references_in_request_order_and_skips_unknown_ones() {
        List<Card> cards = source.findByReferences(
                List.of("ALT_CORE_B_MU_01_U_9", "ALT_NOPE_B_MU_1_U_1", "ALT_EOLE_B_MU_107_U_1"));
        assertEquals(List.of("ALT_CORE_B_MU_01_U_9", "ALT_EOLE_B_MU_107_U_1"),
                cards.stream().map(Card::getReference).toList());
    }

    @Test
    void column_filters_agree_with_card_filter_test() {
        List<CardFilter> filters = List.of(
                CardFilter.builder().build(),
                CardFilter.builder().faction("mu").build(),
                CardFilter.builder().faction("NE").build(),
                CardFilter.builder().excludeBanned().excludeSuspended().build(),
                CardFilter.builder().handCost(CostConstraint.parse("3-")).build(),
                CardFilter.builder().handCost(CostConstraint.parse("3+")).build(),
                CardFilter.builder().reserveCost(CostConstraint.parse("3")).build(),
                CardFilter.builder().subTypes(List.of("Animal", "Robot")).build(),
                CardFilter.builder().cardType("spell").build(),
                CardFilter.builder().set("CORE").build(),
                CardFilter.builder().setCode("eole").build(),
                CardFilter.builder().excludeReferences(Set.of("ALT_EOLE_B_MU_107_U_1")).build(),
                CardFilter.builder().textSearch("cub").build(),
                CardFilter.builder().textSearch("u_3").build(),
                CardFilter.builder().textSearch("anchored").build(),
                CardFilter.builder().textSearch("draw a card").build(),
                CardFilter.builder().textSearch("[]draw").build(),
                CardFilter.builder().textSearch("boosts").build(), // echo only: not searched
                CardFilter.builder().faction("MU").custom(c -> c.getMainCost() == 1).build());
        for (CardFilter filter : filters) {
            assertEquals(expected(filter), scan(filter, null), "filter " + filter.getCriteria());
        }
    }

    @Test
    void scan_can_be_restricted_to_one_set() {
        assertEquals(List.of("ALT_CORE_B_MU_01_U_9"), scan(CardFilter.builder().build(), "CORE"));
        assertFalse(scan(CardFilter.builder().build(), "EOLE").contains("ALT_CORE_B_MU_01_U_9"));
    }
}
