package com.cardpool.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cardpool.backend.TestCards;
import com.cardpool.backend.exception.CardDataUnavailableException;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.service.AnalysisService;
import com.cardpool.backend.service.ExcelExportService;
import com.cardpool.backend.service.PoolDrawService;
import com.cardpool.backend.service.PoolService;
import com.cardpool.backend.service.ReplacementService;
import com.cardpool.backend.service.VerdictService;

@WebMvcTest(CardsController.class)
@Import({ PoolService.class, ExcelExportService.class, ApiExceptionHandler.class })
class CardsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PoolDrawService poolDrawService;
    @MockitoBean
    AnalysisService analysisService;
    @MockitoBean
    ReplacementService replacementService;
    @MockitoBean
    VerdictService verdictService;

    private static final String FORM = "{\"handCost\":\"3-\",\"fieldSearch\":\"draw\",\"unknownField\":1}";

    @Test
    void generate_draws_every_faction_and_leaves_rejected_cards_out() throws Exception {
        when(verdictService.rejectedReferences()).thenReturn(Set.of("REJ_1"));
        when(poolDrawService.drawPerFaction(any(), any(), anyInt()))
                .thenReturn(List.of(TestCards.card("R1", "Knight", "MU", "[] +1")));

        mvc.perform(post("/api/pool/generate?size=10").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("R1"));

        ArgumentCaptor<CardFilter> filter = ArgumentCaptor.forClass(CardFilter.class);
        verify(poolDrawService).drawPerFaction(filter.capture(), eq(List.of("AX", "BR", "LY", "MU", "OR", "YZ")), eq(10));
        CardFilter.Criteria criteria = filter.getValue().getCriteria();
        org.junit.jupiter.api.Assertions.assertEquals(Set.of("REJ_1"), criteria.excludedReferences());
        org.junit.jupiter.api.Assertions.assertEquals(3, criteria.maxCost());
        org.junit.jupiter.api.Assertions.assertEquals("draw", criteria.textSearch());
    }

    @Test
    void generate_can_keep_rejected_cards() throws Exception {
        mvc.perform(post("/api/pool/generate?size=1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"excludeRejected\":false}"))
                .andExpect(status().isOk());
        verifyNoInteractions(verdictService);
    }

    @Test
    void generate_rejects_invalid_input_with_400() throws Exception {
        mvc.perform(post("/api/pool/generate?size=0").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("size")));
        mvc.perform(post("/api/pool/generate?size=abc").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pool/generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pool/generate?size=5").contentType(MediaType.APPLICATION_JSON)
                .content("{\"handCost\":\"cheap\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("handCost")));
    }

    @Test
    void unavailable_card_data_is_a_503() throws Exception {
        when(poolDrawService.drawPerFaction(any(), any(), anyInt()))
                .thenThrow(new CardDataUnavailableException("Card data unavailable: no CardsData", null));
        mvc.perform(post("/api/pool/generate?size=1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("CardsData")));
    }

    @Test
    void export_returns_an_xlsx_attachment() throws Exception {
        mvc.perform(post("/api/pool/export").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"reference\":\"R1\",\"name\":\"Knight\"}]"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("cards.xlsx")))
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")));
    }
}
