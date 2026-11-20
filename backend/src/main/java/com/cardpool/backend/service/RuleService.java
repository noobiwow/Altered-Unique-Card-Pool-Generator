package com.cardpool.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.cardpool.backend.model.rule.EffectRule;
import com.cardpool.backend.model.rule.MatchMode;
import com.cardpool.backend.model.rule.RuleCondition;
import com.cardpool.backend.model.rule.RuleField;
import com.cardpool.backend.model.rule.RuleMatcher;
import com.cardpool.backend.model.rule.RuleSignature;
import com.cardpool.backend.model.rule.Severity;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Persists the dangerous-effect rules in {@code rules.json} of the data
 * directory. On first start the file is seeded from the legacy
 * {@code effect-warnings.json} bundled in the application.
 */
@Slf4j
@Service
public class RuleService {

    static final String FILE = "rules.json";
    static final String LEGACY_RESOURCE = "effect-warnings.json";
    static final String LEGACY_CATEGORY = "Imported";

    /** On-disk layout. */
    public record RuleFile(int version, List<EffectRule> rules) {
    }

    private final JsonFileStore store;
    private List<EffectRule> rules;
    private List<RuleMatcher> activeMatchers;
    private List<RuleMatcher> allMatchers;

    public RuleService(JsonFileStore store) {
        this.store = store;
    }

    public synchronized List<EffectRule> list() {
        return List.copyOf(rules());
    }

    public synchronized Optional<EffectRule> get(String id) {
        return rules().stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** Compiled matchers of the enabled rules. */
    public synchronized List<RuleMatcher> activeMatchers() {
        if (activeMatchers == null) {
            activeMatchers = allMatchers().stream().filter(m -> m.rule().active()).toList();
        }
        return activeMatchers;
    }

    /** Compiled matchers of every valid rule, enabled or not. */
    public synchronized List<RuleMatcher> allMatchers() {
        if (allMatchers == null) {
            List<RuleMatcher> compiled = new ArrayList<>();
            for (EffectRule rule : rules()) {
                try {
                    compiled.add(new RuleMatcher(rule));
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping invalid rule {} ({}): {}", rule.id(), rule.label(), e.getMessage());
                }
            }
            allMatchers = List.copyOf(compiled);
        }
        return allMatchers;
    }

    public synchronized EffectRule create(EffectRule draft) {
        String now = Instant.now().toString();
        EffectRule rule = normalize(draft).withMeta(newId(), now, now);
        new RuleMatcher(rule); // validates
        ensureNotAlreadyFlagged(rule, null);
        rules().add(rule);
        persist();
        return rule;
    }

    public synchronized EffectRule update(String id, EffectRule draft) {
        List<EffectRule> all = rules();
        for (int i = 0; i < all.size(); i++) {
            EffectRule existing = all.get(i);
            if (existing.id().equals(id)) {
                EffectRule rule = normalize(draft).withMeta(id, existing.createdAt(), Instant.now().toString());
                new RuleMatcher(rule); // validates
                ensureNotAlreadyFlagged(rule, id);
                all.set(i, rule);
                persist();
                return rule;
            }
        }
        throw new java.util.NoSuchElementException("Unknown rule " + id);
    }

    public synchronized boolean delete(String id) {
        boolean removed = rules().removeIf(r -> r.id().equals(id));
        if (removed) {
            persist();
        }
        return removed;
    }

    // ------------------------------------------------------------------

    /**
     * Refuses a rule looking for exactly what another rule (enabled or not)
     * already looks for, see {@link RuleSignature}.
     *
     * @param self id of the rule being updated, null for a new rule
     */
    private void ensureNotAlreadyFlagged(EffectRule rule, String self) {
        String signature = RuleSignature.of(rule);
        for (EffectRule other : rules()) {
            if (!other.id().equals(self) && RuleSignature.of(other).equals(signature)) {
                throw new IllegalArgumentException("Already flagged by the rule “" + other.label() + "”"
                        + (other.active() ? "" : " (disabled: enable it instead)")
                        + ": edit that rule rather than creating a duplicate.");
            }
        }
    }

    private EffectRule normalize(EffectRule draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Rule is required");
        }
        String label = draft.label() == null || draft.label().isBlank()
                ? defaultLabel(draft.conditions())
                : draft.label().strip();
        return new EffectRule(null, label, trim(draft.reason()), Severity.orDefault(draft.severity()),
                trim(draft.category()), draft.enabled(), draft.conditions(), draft.factions(),
                draft.createdFrom(), null, null);
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }

    private static String defaultLabel(List<RuleCondition> conditions) {
        if (conditions == null || conditions.isEmpty() || conditions.get(0).value() == null) {
            return "Unnamed rule";
        }
        String value = conditions.get(0).value().strip();
        return value.length() > 60 ? value.substring(0, 57) + "..." : value;
    }

    private static String newId() {
        return "r-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<EffectRule> rules() {
        if (rules == null) {
            Optional<RuleFile> file = store.read(FILE, RuleFile.class);
            if (file.isPresent() && file.get().rules() != null) {
                rules = new ArrayList<>(file.get().rules());
            } else {
                rules = new ArrayList<>(importLegacyRules());
                persist();
                log.info("Seeded {} with {} rules from {}", FILE, rules.size(), LEGACY_RESOURCE);
            }
        }
        return rules;
    }

    private void persist() {
        activeMatchers = null;
        allMatchers = null;
        store.write(FILE, new RuleFile(1, rules));
    }

    /** Converts the legacy {@code effect-warnings.json} format into rules. */
    List<EffectRule> importLegacyRules() {
        ClassPathResource resource = new ClassPathResource(LEGACY_RESOURCE);
        if (!resource.exists()) {
            return List.of();
        }
        JsonNode root;
        try (InputStream in = resource.getInputStream()) {
            root = store.mapper().readTree(in);
        } catch (IOException e) {
            log.warn("Could not read {}: {}", LEGACY_RESOURCE, e.getMessage());
            return List.of();
        }
        String now = Instant.now().toString();
        List<EffectRule> imported = new ArrayList<>();
        for (JsonNode node : root.path("effectPatterns")) {
            String pattern = node.path("pattern").asText(null);
            if (pattern != null) {
                imported.add(legacy(pattern, effectField(node), MatchMode.REGEX, pattern, node, now));
            }
        }
        for (JsonNode node : root.path("cardNames")) {
            String name = node.path("name").asText(null);
            if (name != null) {
                imported.add(legacy(name, RuleField.NAME, MatchMode.EXACT, name, node, now));
            }
        }
        for (JsonNode node : root.path("effectLines")) {
            String line = node.path("line").asText(null);
            if (line != null) {
                imported.add(legacy(line, effectField(node), MatchMode.CONTAINS, line, node, now));
            }
        }
        return imported;
    }

    private static RuleField effectField(JsonNode node) {
        return node.path("ignoreEcho").asBoolean(false) ? RuleField.MAIN_EFFECT : RuleField.ANY_EFFECT;
    }

    private static EffectRule legacy(String label, RuleField field, MatchMode mode, String value, JsonNode node,
            String now) {
        String faction = node.path("faction").asText(null);
        return new EffectRule(newId(), label, null, Severity.MEDIUM, LEGACY_CATEGORY, true,
                List.of(new RuleCondition(field, mode, value, false)),
                faction == null || faction.isBlank() ? List.of() : List.of(faction.strip().toUpperCase()),
                null, now, now);
    }
}
