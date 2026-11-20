package com.cardpool.backend.source.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.CostConstraint;

/**
 * Checks the column filters against {@link CardFilter#test} on a real set of
 * the CardsData export. Skipped when backend/cardsdata/ is not filled.
 */
class RealCardsDataTest {

    private static final Path CARDS_DATA = Path.of("cardsdata");

    @Test
    void column_filters_match_card_filter_on_a_real_set() throws Exception {
        assumeTrue(Files.isDirectory(CARDS_DATA.resolve("Unique/EOLE")), "CardsData not available");
        CardsData data = CardsData.load(CARDS_DATA);
        assertTrue(data.size() > 1_000_000, "expected the full export");
        Segment eole = data.segment("EOLE");

        List<CardFilter> filters = List.of(
                CardFilter.builder().faction("MU").excludeBanned().build(),
                CardFilter.builder().handCost(CostConstraint.parse("2-")).reserveCost(CostConstraint.parse("3+")).build(),
                CardFilter.builder().subTypes(List.of("Animal")).cardType("Character").build(),
                CardFilter.builder().textSearch("sabotage").build(),
                CardFilter.builder().textSearch("draw a card").build(),
                CardFilter.builder().textSearch("I gain 2 boosts").build(),
                CardFilter.builder().excludeReferences(Set.of(eole.reference(0), eole.reference(1))).build());

        CsvCardSource.Matcher[] matchers = filters.stream()
                .map(f -> new CsvCardSource.Matcher(data, f.getCriteria())).toArray(CsvCardSource.Matcher[]::new);
        long[] expected = new long[filters.size()], actual = new long[filters.size()];
        boolean[][] excluded = new boolean[filters.size()][];
        for (int i = 0; i < filters.size(); i++) {
            excluded[i] = matchers[i].excludedRows(eole);
        }
        for (int row = 0; row < eole.size; row++) {
            Card card = data.card(eole, row);
            for (int i = 0; i < filters.size(); i++) {
                boolean byCard = filters.get(i).test(card);
                boolean byColumns = (excluded[i] == null || !excluded[i][row]) && matchers[i].accepts(eole, row);
                assertEquals(byCard, byColumns, "row " + row + " (" + card.getReference() + "), filter " + i);
                if (byCard) {
                    expected[i]++;
                }
                if (byColumns) {
                    actual[i]++;
                }
            }
        }
        for (int i = 0; i < filters.size(); i++) {
            assertTrue(expected[i] > 0, "filter " + i + " matched nothing: weak test");
        }
    }
}
