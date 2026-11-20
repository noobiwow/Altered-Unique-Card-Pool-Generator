package com.cardpool.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cardpool.backend.config.CardPoolProperties;
import com.cardpool.backend.dto.Catalog;
import com.cardpool.backend.dto.FactionDTO;
import com.cardpool.backend.dto.SetDTO;
import com.cardpool.backend.dto.SubtypeDTO;
import com.cardpool.backend.source.CardSource;

@WebMvcTest({ FormValueController.class, SpaController.class })
@Import(FormAndSpaControllerTest.Properties.class)
class FormAndSpaControllerTest {

    @TestConfiguration
    static class Properties {
        @Bean
        CardPoolProperties cardPoolProperties() {
            return new CardPoolProperties(Map.of("EOLE", 0.25));
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CardSource cardSource;

    @Test
    void form_values_come_from_the_card_source_with_configured_weights() throws Exception {
        when(cardSource.catalog()).thenReturn(new Catalog(
                List.of(new FactionDTO("1", "AX", "Axiom")),
                List.of(new SetDTO("EOLE", "Roots of Corruption", 0), new SetDTO("CORE", "Beyond the Gates", 0)),
                List.of(new SubtypeDTO("Animal"))));
        mvc.perform(get("/api/form/formValues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets[0].defaultWeight").value(0.25))
                .andExpect(jsonPath("$.sets[1].defaultWeight").value(0.0))
                .andExpect(jsonPath("$.subtypes[0].name").value("Animal"));
    }

    @Test
    void client_side_routes_serve_the_app() throws Exception {
        mvc.perform(get("/report/20261119-1"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
