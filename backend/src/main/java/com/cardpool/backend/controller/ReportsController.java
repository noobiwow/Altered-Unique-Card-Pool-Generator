package com.cardpool.backend.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.dto.CreateReportRequest;
import com.cardpool.backend.model.report.Report;
import com.cardpool.backend.model.report.ReportListItem;
import com.cardpool.backend.service.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<ReportListItem> list() {
        return reportService.list();
    }

    @GetMapping("/{id}")
    public Report get(@PathVariable String id) {
        return reportService.get(id).orElseThrow(() -> new NoSuchElementException("Unknown report " + id));
    }

    @PostMapping
    public Report create(@RequestBody CreateReportRequest request) {
        return reportService.create(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return reportService.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
