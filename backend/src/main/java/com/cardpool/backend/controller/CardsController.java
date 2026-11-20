package com.cardpool.backend.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.dto.CardAnalysis;
import com.cardpool.backend.dto.ReplacementRequest;
import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.form.FilterForm;
import com.cardpool.backend.service.AnalysisService;
import com.cardpool.backend.service.ExcelExportService;
import com.cardpool.backend.service.PoolDrawService;
import com.cardpool.backend.service.PoolService;
import com.cardpool.backend.service.ReplacementService;
import com.cardpool.backend.service.VerdictService;

@RestController
@RequestMapping("/api/pool")
public class CardsController {

    /** Upper bound of cards per faction, to keep requests reasonable. */
    static final int MAX_CARDS_PER_FACTION = 10_000;

    private static final List<String> FACTION_CODES = Arrays.stream(FactionEnum.values())
            .map(FactionEnum::getCode)
            .toList();

    private final PoolService poolService;
    private final PoolDrawService poolDrawService;
    private final AnalysisService analysisService;
    private final ReplacementService replacementService;
    private final VerdictService verdictService;
    private final ExcelExportService excelExportService;

    public CardsController(PoolService poolService, PoolDrawService poolDrawService, AnalysisService analysisService,
            ReplacementService replacementService, VerdictService verdictService,
            ExcelExportService excelExportService) {
        this.poolService = poolService;
        this.poolDrawService = poolDrawService;
        this.analysisService = analysisService;
        this.replacementService = replacementService;
        this.verdictService = verdictService;
        this.excelExportService = excelExportService;
    }

    /**
     * Draws {@code size} cards for every faction with the same filters and
     * returns them as one shuffled pool. Cards rejected by the team are left
     * out unless the form says otherwise.
     */
    @PostMapping("/generate")
    public List<Card> generate(@RequestParam int size, @RequestBody FilterForm form) {
        if (size < 1 || size > MAX_CARDS_PER_FACTION) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_CARDS_PER_FACTION);
        }
        Set<String> rejected = form.shouldExcludeRejected() ? verdictService.rejectedReferences() : Set.of();
        CardFilter filter = poolService.buildFilter(form, null, rejected);
        return poolDrawService.drawPerFaction(filter, FACTION_CODES, size);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportExcel(@RequestBody List<Card> pool) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("cards.xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelExportService.exportCards(pool));
    }

    @PostMapping("/analyze")
    public List<CardAnalysis> analyze(@RequestBody List<Card> pool) {
        return analysisService.analyzeWithDormant(pool);
    }

    /** Candidates to replace a card of the pool, see {@link ReplacementService}. */
    @PostMapping("/replacements")
    public List<Card> replacements(@RequestBody ReplacementRequest request) {
        return replacementService.candidates(request);
    }
}
