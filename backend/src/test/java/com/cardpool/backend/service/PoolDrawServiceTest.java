package com.cardpool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.source.csv.CardsData;
import com.cardpool.backend.source.csv.CsvCardSource;

class PoolDrawServiceTest {

    private final PoolDrawService draw;

    PoolDrawServiceTest() throws Exception {
        draw = new PoolDrawService(new CsvCardSource(CardsData.load(Path.of("src/test/resources/cardsdata"))));
    }

    @Test
    void draws_per_faction() {
        List<Card> pool = draw.drawPerFaction(CardFilter.builder().build(), List.of("MU", "AX"), 1);
        assertEquals(2, pool.size());
        assertEquals(1, pool.stream().filter(c -> c.getMainFaction().getCode().equals("MU")).count());
        assertEquals(1, pool.stream().filter(c -> c.getMainFaction().getCode().equals("AX")).count());
    }

    @Test
    void respects_set_weights() {
        // MU has one EOLE and one CORE card: 50/50 over 2 cards must return both
        CardFilter filter = CardFilter.builder().setWeights(Map.of("EOLE", 0.5, "CORE", 0.5)).build();
        List<Card> pool = draw.drawPerFaction(filter, List.of("MU"), 2);
        assertEquals(2, pool.size());
        assertTrue(pool.stream().anyMatch(c -> c.getSetCode().equals("CORE")));
        assertTrue(pool.stream().anyMatch(c -> c.getSetCode().equals("EOLE")));
    }
}
