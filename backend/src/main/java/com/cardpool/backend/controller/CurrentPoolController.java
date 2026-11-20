package com.cardpool.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.pool.SavedPool;
import com.cardpool.backend.model.report.Replacement;
import com.cardpool.backend.service.CurrentPoolService;
import com.cardpool.backend.source.CardSource;
import com.fasterxml.jackson.databind.JsonNode;

/** The pool being worked on, see {@link CurrentPoolService}. */
@RestController
@RequestMapping("/api/current-pool")
public class CurrentPoolController {

    /** The saved pool with its cards resolved. */
    public record CurrentPool(String name, JsonNode filter, List<Replacement> replacements, String generatedAt,
            String updatedAt, int size, List<Card> cards) {
    }

    /** What the home page shows: the pool without its cards. */
    public record CurrentPoolSummary(String name, int size, int replacements, String generatedAt,
            String updatedAt) {

        static CurrentPoolSummary of(SavedPool p) {
            return new CurrentPoolSummary(p.name(), p.references().size(), p.replacements().size(),
                    p.generatedAt(), p.updatedAt());
        }
    }

    private final CurrentPoolService currentPoolService;
    private final CardSource cardSource;

    public CurrentPoolController(CurrentPoolService currentPoolService, CardSource cardSource) {
        this.currentPoolService = currentPoolService;
        this.cardSource = cardSource;
    }

    /** 204 when there is no current pool. */
    @GetMapping
    public ResponseEntity<CurrentPool> get() {
        return currentPoolService.get()
                .map(p -> new CurrentPool(p.name(), p.filter(), p.replacements(), p.generatedAt(), p.updatedAt(),
                        p.references().size(), cardSource.findByReferences(p.references())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 204 when there is no current pool. */
    @GetMapping("/summary")
    public ResponseEntity<CurrentPoolSummary> summary() {
        return currentPoolService.get()
                .map(CurrentPoolSummary::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public CurrentPoolSummary save(@RequestBody SavedPool pool) {
        return CurrentPoolSummary.of(currentPoolService.save(pool));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        currentPoolService.clear();
        return ResponseEntity.noContent().build();
    }
}
