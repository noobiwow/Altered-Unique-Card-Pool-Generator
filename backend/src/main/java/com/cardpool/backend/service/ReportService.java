package com.cardpool.backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.cardpool.backend.dto.AnalysisStatus;
import com.cardpool.backend.dto.CardAnalysis;
import com.cardpool.backend.dto.CreateReportRequest;
import com.cardpool.backend.dto.RuleMatch;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.report.Report;
import com.cardpool.backend.model.report.ReportCard;
import com.cardpool.backend.model.report.ReportListItem;
import com.cardpool.backend.model.report.ReportSummary;
import com.cardpool.backend.model.rule.Severity;

/** Creates and stores analysis report snapshots under {@code reports/}. */
@Service
public class ReportService {

    static final String DIR = "reports";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]{1,80}");
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JsonFileStore store;
    private final AnalysisService analysisService;

    public ReportService(JsonFileStore store, AnalysisService analysisService) {
        this.store = store;
        this.analysisService = analysisService;
    }

    public Report create(CreateReportRequest request) {
        if (request == null || request.cards() == null || request.cards().isEmpty()) {
            throw new IllegalArgumentException("The pool to report on is empty");
        }
        Map<String, Card> byRef = new HashMap<>();
        request.cards().forEach(c -> {
            if (c != null && c.getReference() != null) {
                byRef.put(c.getReference(), c);
            }
        });

        List<ReportCard> cards = new ArrayList<>();
        for (CardAnalysis analysis : analysisService.analyze(request.cards())) {
            if (analysis.status() == AnalysisStatus.CLEARED && !request.includeCleared()) {
                continue;
            }
            cards.add(toReportCard(byRef.get(analysis.reference()), analysis));
        }
        cards.sort(Comparator
                .comparing((ReportCard c) -> c.status().ordinal())
                .thenComparing(c -> -highest(c.warnings()).ordinal())
                .thenComparing(c -> c.factionCode() == null ? "~" : c.factionCode())
                .thenComparing(c -> c.name() == null ? "" : c.name()));

        String id = LocalDateTime.now().format(ID_DATE) + "-" + UUID.randomUUID().toString().substring(0, 4);
        String title = request.title() == null || request.title().isBlank()
                ? "Pool analysis " + LocalDateTime.now().toLocalDate()
                : request.title().strip();
        Report report = new Report(id, title, request.poolName(), request.notes(), Instant.now().toString(),
                byRef.size(), summarize(cards), cards,
                request.replacements() == null ? List.of() : List.copyOf(request.replacements()));
        store.write(path(id), report);
        return report;
    }

    public Optional<Report> get(String id) {
        if (!ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return store.read(path(id), Report.class);
    }

    public List<ReportListItem> list() {
        List<ReportListItem> items = new ArrayList<>();
        for (String file : store.list(DIR, ".json")) {
            store.read(DIR + "/" + file, Report.class).map(ReportListItem::of).ifPresent(items::add);
        }
        items.sort(Comparator.comparing(ReportListItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    public boolean delete(String id) {
        return ID.matcher(id).matches() && store.delete(path(id));
    }

    private static String path(String id) {
        return DIR + "/" + id + ".json";
    }

    private static Severity highest(List<RuleMatch> warnings) {
        return warnings.stream().map(RuleMatch::severity).map(Severity::orDefault)
                .max(Comparator.naturalOrder()).orElse(Severity.LOW);
    }

    private static ReportSummary summarize(List<ReportCard> cards) {
        int flagged = 0, cleared = 0, rejected = 0;
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (Severity s : new Severity[] { Severity.HIGH, Severity.MEDIUM, Severity.LOW }) {
            bySeverity.put(s.name(), 0);
        }
        Map<String, Integer> byFaction = new LinkedHashMap<>();
        for (ReportCard card : cards) {
            switch (card.status()) {
                case FLAGGED -> {
                    flagged++;
                    bySeverity.merge(highest(card.warnings()).name(), 1, Integer::sum);
                }
                case CLEARED -> cleared++;
                case REJECTED -> rejected++;
            }
            byFaction.merge(card.factionCode() == null ? "?" : card.factionCode(), 1, Integer::sum);
        }
        return new ReportSummary(flagged, cleared, rejected, bySeverity, byFaction);
    }

    private static ReportCard toReportCard(Card card, CardAnalysis analysis) {
        if (card == null) {
            return new ReportCard(analysis.reference(), analysis.name(), analysis.factionCode(), null, null, null,
                    null, null, null, null, null, analysis.status(), analysis.warnings(), analysis.verdict());
        }
        return new ReportCard(card.getReference(), card.getName(), analysis.factionCode(), card.getFactionName(),
                card.getSetName(),
                card.getCardType() != null && card.getCardType().getName() != null ? card.getCardTypeName() : null,
                card.getElementValue("MAIN_COST"), card.getElementValue("RECALL_COST"),
                card.getMainEffect(), card.getEchoEffect(), card.getAbilities(),
                analysis.status(), analysis.warnings(), analysis.verdict());
    }
}
