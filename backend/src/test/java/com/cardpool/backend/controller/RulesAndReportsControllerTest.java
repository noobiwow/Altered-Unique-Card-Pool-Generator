package com.cardpool.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cardpool.backend.service.AnalysisService;
import com.cardpool.backend.service.JsonFileStore;
import com.cardpool.backend.service.ReportService;
import com.cardpool.backend.service.RuleService;
import com.cardpool.backend.service.VerdictService;

/** Rules, verdicts and reports over real services writing to a temporary folder. */
@WebMvcTest({ RulesController.class, VerdictsController.class, ReportsController.class })
@Import({ RuleService.class, VerdictService.class, AnalysisService.class, ReportService.class,
        ApiExceptionHandler.class, RulesAndReportsControllerTest.TempStore.class })
class RulesAndReportsControllerTest {

    @TestConfiguration
    static class TempStore {
        @Bean
        JsonFileStore jsonFileStore() throws IOException {
            return new JsonFileStore(Files.createTempDirectory("cardpool-test"));
        }
    }

    @Autowired
    MockMvc mvc;

    private static final String RULE = """
            {"label":"Draw","severity":"HIGH","conditions":[{"field":"MAIN_EFFECT","mode":"CONTAINS","value":"draw a card"}]}""";
    private static final String CARD = """
            [{"reference":"R1","name":"Scholar","mainFaction":{"code":"LY"},"mainEffect":"[] Draw a card."}]""";

    @Test
    void rule_lifecycle_and_validation() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/rules")
                .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", startsWith("r-")))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/rules/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule\":" + RULE + ",\"cards\":" + CARD + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conditions\":[{\"field\":\"ANY_EFFECT\",\"mode\":\"REGEX\",\"value\":\"(oops\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("regular expression")));

        mvc.perform(put("/api/rules/unknown").contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/rules/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rules/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void ability_combination_rules_round_trip_through_json() throws Exception {
        String rule = """
                {"label":"Free draw","conditions":[{"field":"ABILITY","ability":{"scope":"MAIN",
                 "trigger":{"mode":"EXACT","value":"{J}"},"condition":{"mode":"ABSENT"},
                 "effect":{"mode":"CONTAINS","value":"draw"}}}]}""";
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON).content(rule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions[0].ability.trigger.value").value("{J}"))
                .andExpect(jsonPath("$.conditions[0].ability.condition.mode").value("ABSENT"));
        String cards = """
                [{"reference":"R1","name":"Scholar","mainFaction":{"code":"LY"},
                  "abilities":[{"slot":"MAIN","trigger":"{H}","condition":"If x:","effect":"Draw a card."},
                               {"slot":"MAIN","trigger":"{J}","effect":"Draw a card."}]}]""";
        mvc.perform(post("/api/rules/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule\":" + rule + ",\"cards\":" + cards + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].warnings[0].ability.trigger").value("{J}"))
                .andExpect(jsonPath("$[0].warnings[0].matchedText").value("{J} Draw a card."));
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conditions\":[{\"field\":\"ABILITY\",\"ability\":{}}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("at least one")));
    }

    @Test
    void verdict_requires_a_status() throws Exception {
        mvc.perform(put("/api/verdicts/R1").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/verdicts/R1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reports_are_created_and_unknown_ones_are_404() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Review\",\"cards\":" + CARD + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Review"))
                .andExpect(jsonPath("$.poolSize").value(1));
        mvc.perform(get("/api/reports/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports/..%2Frules")).andExpect(status().isNotFound());
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{\"cards\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
