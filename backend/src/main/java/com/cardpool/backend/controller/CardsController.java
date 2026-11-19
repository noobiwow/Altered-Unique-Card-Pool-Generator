package com.cardpool.backend.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cardpool.backend.dto.CardWarning;
import com.cardpool.backend.dto.WarningRule;
import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.model.Card;

import lombok.extern.slf4j.Slf4j;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.excel.CardEffectAnalyser;
import com.cardpool.backend.model.excel.StatsFormatter;
import com.cardpool.backend.model.form.FilterForm;
import com.cardpool.backend.repository.CardRepository;
import com.cardpool.backend.service.EffectWarningService;
import com.cardpool.backend.service.ExcelCardReaderService;
import com.cardpool.backend.service.ExcelExportService;
import com.cardpool.backend.service.PoolService;

import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/pool")
public class CardsController {

    private final PoolService poolService;
    private final ExcelCardReaderService excelCardReaderService;
    private final CardRepository repo;
    private final EffectWarningService effectWarningService;
    private final com.cardpool.backend.service.CardCacheService cardCacheService;

    public CardsController(PoolService poolService, ExcelCardReaderService excelCardReaderService, CardRepository repo,
            EffectWarningService effectWarningService,
            com.cardpool.backend.service.CardCacheService cardCacheService) {
        this.poolService = poolService;
        this.excelCardReaderService = excelCardReaderService;
        this.repo = repo;
        this.effectWarningService = effectWarningService;
        this.cardCacheService = cardCacheService;
    }

    @PostMapping("/generate")
    public Mono<List<Card>> generatePoolV2(
            @RequestParam("size") String poolSize,
            @RequestParam("locale") String locale,
            @RequestBody FilterForm filterForm) {
        // Generate one pool per faction, then combine them into the global pool.
        CardFilter cardFilter = poolService.buildFilter(filterForm, null);
        List<String> factionCodes = Arrays.stream(FactionEnum.values())
                .map(FactionEnum::getCode)
                .toList();
        return repo.drawFilteredPerFactionV3(
                cardFilter,
                factionCodes,
                Integer.parseInt(poolSize),
                locale);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportExcel(@RequestBody List<Card> pool) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cards.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(ExcelExportService.exportCards(pool));
    }

    @PostMapping("/analyze")
    public List<CardWarning> analyzePool(@RequestBody List<Card> pool) {
        return effectWarningService.analyze(pool);
    }

    @PostMapping("/resolve")
    public List<Card> resolveRefs(@RequestBody List<String> references) {
        return cardCacheService.findByReferences(references);
    }

    @GetMapping("/warningRules")
    public List<WarningRule> getWarningRules() {
        return effectWarningService.getRules();
    }

    @PostMapping("/import/stats")
    public ResponseEntity<String> getEffectsStatsFromExcelFile(@RequestParam("file") MultipartFile file) {
        try {
            List<CardEffectAnalyser.CardRecord> cards = excelCardReaderService.read(file.getInputStream());
            StatsFormatter formatter = new StatsFormatter();
            String statsJson = formatter.formatJson(new CardEffectAnalyser().analyse(cards));
            return ResponseEntity.ok(statsJson);
        } catch (Exception e) {
            log.error("Failed to process stats from Excel file", e);
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
