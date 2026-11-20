package com.cardpool.backend.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.cardpool.backend.config.CardPoolProperties;
import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.model.form.FilterForm;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs a few pool draws and analyses in the background once the application
 * is up, so the JIT has compiled the scan of the card data before the first
 * real request. Without it, the first pools after a restart are several times
 * slower than the following ones. Disable with {@code cardpool.warm-up=false}.
 */
@Slf4j
@Service
public class WarmUpService {

    private static final int ROUNDS = 6;
    private static final int CARDS_PER_FACTION = 10;

    private final PoolService poolService;
    private final PoolDrawService poolDrawService;
    private final AnalysisService analysisService;
    private final CardPoolProperties properties;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public WarmUpService(PoolService poolService, PoolDrawService poolDrawService, AnalysisService analysisService,
            CardPoolProperties properties, ObjectMapper objectMapper,
            @Value("${cardpool.warm-up:true}") boolean enabled) {
        this.poolService = poolService;
        this.poolDrawService = poolDrawService;
        this.analysisService = analysisService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            return;
        }
        Thread thread = new Thread(this::run, "warm-up");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void run() {
        long start = System.nanoTime();
        try {
            List<String> factions = Arrays.stream(FactionEnum.values()).map(FactionEnum::getCode).toList();
            // The generator's default form: weighted by set, nothing else.
            FilterForm form = new FilterForm(null, null, List.of(), null, false, false, null, null, null,
                    properties.defaultSetWeights(), true);
            CardFilter filter = poolService.buildFilter(form, null, Set.of());
            for (int round = 0; round < ROUNDS; round++) {
                List<Card> pool = poolDrawService.drawPerFaction(filter, factions, CARDS_PER_FACTION);
                objectMapper.writeValueAsBytes(pool);
                analysisService.analyze(pool);
            }
            log.info("Warm-up done in {} ms", (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            // Card data not available, etc.: the real requests will report it.
            log.warn("Warm-up skipped: {}", e.getMessage());
        }
    }
}
