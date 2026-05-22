package com.cardpool.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cardpool.backend.model.Card;

import lombok.extern.slf4j.Slf4j;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.excel.CardEffectAnalyser;
import com.cardpool.backend.model.excel.StatsFormatter;
import com.cardpool.backend.model.form.FilterForm;
import com.cardpool.backend.repository.CardRepository;
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

    public CardsController(PoolService poolService, ExcelCardReaderService excelCardReaderService, CardRepository repo) {
        this.poolService = poolService;
        this.excelCardReaderService = excelCardReaderService;
        this.repo = repo;
    }

    @PostMapping("/generate")
    public Mono<List<Card>> generatePoolV2(
            @RequestParam("size") String poolSize,
            @RequestParam("locale") String locale,
            @RequestBody FilterForm filterForm) {
        CardFilter cardFilter = poolService.buildFilter(filterForm);
        return repo.drawFilteredV3(
                cardFilter,
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
