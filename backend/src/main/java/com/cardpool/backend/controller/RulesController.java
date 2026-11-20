package com.cardpool.backend.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.dto.CardAnalysis;
import com.cardpool.backend.dto.RulePreviewRequest;
import com.cardpool.backend.model.rule.EffectRule;
import com.cardpool.backend.service.AnalysisService;
import com.cardpool.backend.service.RuleService;

@RestController
@RequestMapping("/api/rules")
public class RulesController {

    private final RuleService ruleService;
    private final AnalysisService analysisService;

    public RulesController(RuleService ruleService, AnalysisService analysisService) {
        this.ruleService = ruleService;
        this.analysisService = analysisService;
    }

    @GetMapping
    public List<EffectRule> list() {
        return ruleService.list();
    }

    @GetMapping("/{id}")
    public EffectRule get(@PathVariable String id) {
        return ruleService.get(id).orElseThrow(() -> new NoSuchElementException("Unknown rule " + id));
    }

    @PostMapping
    public EffectRule create(@RequestBody EffectRule rule) {
        return ruleService.create(rule);
    }

    @PutMapping("/{id}")
    public EffectRule update(@PathVariable String id, @RequestBody EffectRule rule) {
        return ruleService.update(id, rule);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return ruleService.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Evaluates a (possibly unsaved) rule over the given cards. */
    @PostMapping("/preview")
    public List<CardAnalysis> preview(@RequestBody RulePreviewRequest request) {
        return analysisService.preview(request.rule(), request.cards());
    }
}
