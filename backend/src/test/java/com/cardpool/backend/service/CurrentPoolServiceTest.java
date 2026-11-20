package com.cardpool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.pool.SavedPool;
import com.cardpool.backend.model.report.Replacement;
import com.fasterxml.jackson.databind.ObjectMapper;

class CurrentPoolServiceTest {

    private Path dir;
    private CurrentPoolService service;

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("cardpool-current-pool");
        service = new CurrentPoolService(new JsonFileStore(dir));
    }

    private static SavedPool pool(List<String> references) throws Exception {
        return new SavedPool("Generated pool", new ObjectMapper().readTree("{\"set\":\"CORE\"}"),
                List.of(new Replacement("A", "Card A", "C", "Card C", "2026-11-19T10:00:00Z")),
                "2026-11-19T09:00:00Z", null, references);
    }

    @Test
    void no_pool_until_one_is_saved() {
        assertTrue(service.get().isEmpty());
    }

    @Test
    void saved_pool_is_read_back_by_a_new_service_in_order() throws Exception {
        SavedPool saved = service.save(pool(List.of("C", "B")));
        assertNotNull(saved.updatedAt());

        SavedPool read = new CurrentPoolService(new JsonFileStore(dir)).get().orElseThrow();
        assertEquals(List.of("C", "B"), read.references());
        assertEquals("CORE", read.filter().get("set").asText());
        assertEquals("C", read.replacements().get(0).toReference());
        assertEquals(saved.updatedAt(), read.updatedAt());
    }

    @Test
    void saving_replaces_the_previous_pool() throws Exception {
        service.save(pool(List.of("A", "B")));
        service.save(pool(List.of("D")));
        assertEquals(List.of("D"), service.get().orElseThrow().references());
    }

    @Test
    void empty_pool_is_refused_and_clear_removes_the_pool() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.save(pool(List.of())));
        service.save(pool(List.of("A")));
        assertTrue(service.clear());
        assertTrue(service.get().isEmpty());
        assertFalse(service.clear());
    }
}
