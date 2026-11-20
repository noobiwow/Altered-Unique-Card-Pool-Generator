package com.cardpool.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.dto.VerdictRequest;
import com.cardpool.backend.model.verdict.Verdict;
import com.cardpool.backend.service.VerdictService;

@RestController
@RequestMapping("/api/verdicts")
public class VerdictsController {

    private final VerdictService verdictService;

    public VerdictsController(VerdictService verdictService) {
        this.verdictService = verdictService;
    }

    @GetMapping
    public List<Verdict> list() {
        return verdictService.all();
    }

    @PutMapping("/{reference}")
    public Verdict set(@PathVariable String reference, @RequestBody VerdictRequest request) {
        return verdictService.set(reference, request);
    }

    /** Puts the card back to pending. */
    @DeleteMapping("/{reference}")
    public ResponseEntity<Void> clear(@PathVariable String reference) {
        verdictService.clear(reference);
        return ResponseEntity.noContent().build();
    }
}
