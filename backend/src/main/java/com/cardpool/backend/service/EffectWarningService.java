package com.cardpool.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.cardpool.backend.dto.CardWarning;
import com.cardpool.backend.dto.EffectWarning;
import com.cardpool.backend.dto.WarningRule;
import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.excel.CardEffectParser;
import com.cardpool.backend.model.excel.ParsedAbility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Detects "warning" cards in a generated pool based on a configurable list of
 * card-name, effect-body regex and exact effect-line rules (see
 * {@code effect-warnings.json}).
 * <p>
 * Each rule may be restricted with:
 * <ul>
 * <li>{@code faction} - only flag cards of that faction (matches the faction
 * code e.g. "BR" or name e.g. "Bravos")</li>
 * <li>{@code ignoreEcho} - only flag the rule when it appears in the main
 * effect, not the echo effect</li>
 * </ul>
 */
@Service
public class EffectWarningService {

    private static final String TYPE_PATTERN = "PATTERN";
    private static final String TYPE_CARD_NAME = "CARD_NAME";
    private static final String TYPE_EFFECT_LINE = "EFFECT_LINE";

    private record CardNameRule(String name, String faction) {
        CardNameRule {
            faction = normalizeFaction(faction);
        }
    }

    private record EffectPatternRule(String pattern, String faction, boolean ignoreEcho) {
        EffectPatternRule {
            faction = normalizeFaction(faction);
        }
    }

    private record EffectLineRule(@JsonProperty("line") String raw, String faction, boolean ignoreEcho) {
        EffectLineRule {
            faction = normalizeFaction(faction);
        }

        String normalized() {
            return EffectWarningService.normalize(raw);
        }
    }

    private record CompiledPatternRule(Pattern pattern, String faction, boolean ignoreEcho) {
    }

    private record Config(List<CardNameRule> cardNames, List<EffectPatternRule> effectPatterns,
            List<EffectLineRule> effectLines) {
    }

    private final CardEffectParser parser = new CardEffectParser();
    private final List<CardNameRule> cardNameRules;
    private final List<CompiledPatternRule> effectPatternRules;
    private final List<EffectLineRule> effectLineRules;

    public EffectWarningService() {
        Config config = loadConfig();
        this.cardNameRules = config.cardNames() == null ? List.of() : List.copyOf(config.cardNames());
        List<CompiledPatternRule> compiled = new ArrayList<>();
        if (config.effectPatterns() != null) {
            for (EffectPatternRule rule : config.effectPatterns()) {
                compiled.add(new CompiledPatternRule(
                        Pattern.compile(rule.pattern(), Pattern.CASE_INSENSITIVE),
                        rule.faction(), rule.ignoreEcho()));
            }
        }
        this.effectPatternRules = List.copyOf(compiled);
        this.effectLineRules = config.effectLines() == null ? List.of() : List.copyOf(config.effectLines());
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("[\\s\\u00a0]+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeFaction(String faction) {
        return faction == null ? null : faction.strip().toUpperCase(Locale.ROOT);
    }

    private Config loadConfig() {
        try (InputStream in = new ClassPathResource("effect-warnings.json").getInputStream()) {
            return new ObjectMapper().readValue(in, Config.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load effect-warnings.json", e);
        }
    }

    /**
     * Analyzes each card of the pool and returns warnings only for cards that
     * contain at least one warned effect.
     */
    public List<CardWarning> analyze(List<Card> cards) {
        List<CardWarning> result = new ArrayList<>();
        if (cards == null) {
            return result;
        }
        for (Card card : cards) {
            if (card == null) {
                continue;
            }
            List<EffectWarning> warnings = analyzeCard(card);
            if (!warnings.isEmpty()) {
                result.add(new CardWarning(card.getReference(), card.getName(), warnings));
            }
        }
        return result;
    }

    /**
     * Returns the configured warning rules as they are defined in
     * {@code effect-warnings.json}, for building filter dropdowns.
     */
    public List<WarningRule> getRules() {
        List<WarningRule> rules = new ArrayList<>();
        for (CardNameRule rule : cardNameRules) {
            String label = rule.faction() == null ? rule.name()
                    : rule.name() + " (" + factionLabel(rule.faction()) + ")";
            rules.add(new WarningRule(TYPE_CARD_NAME, rule.name(), label));
        }
        for (CompiledPatternRule rule : effectPatternRules) {
            rules.add(new WarningRule(TYPE_PATTERN, rule.pattern().pattern(), rule.pattern().pattern()));
        }
        for (EffectLineRule rule : effectLineRules) {
            rules.add(new WarningRule(TYPE_EFFECT_LINE, rule.raw(), rule.raw()));
        }
        return rules;
    }

    private static String factionLabel(String code) {
        for (FactionEnum faction : FactionEnum.values()) {
            if (faction.getCode().equalsIgnoreCase(code)) {
                return faction.getName();
            }
        }
        return code;
    }

    private List<EffectWarning> analyzeCard(Card card) {
        List<EffectWarning> warnings = new ArrayList<>();

        String name = card.getName();
        for (CardNameRule rule : cardNameRules) {
            if (rule.name() != null && name != null && name.strip().equalsIgnoreCase(rule.name())
                    && matchesFaction(card, rule.faction())) {
                warnings.add(new EffectWarning(TYPE_CARD_NAME, "Card name '" + name + "' is in the watch list",
                        rule.name()));
            }
        }

        checkEffect(card.getMainEffect(), false, card, warnings);
        checkEffect(card.getEchoEffect(), true, card, warnings);
        return warnings;
    }

    private void checkEffect(String effect, boolean isEcho, Card card, List<EffectWarning> warnings) {
        if (effect == null) {
            return;
        }
        for (ParsedAbility ability : parser.parse(effect)) {
            String body = ability.getEffectBody() == null ? "" : ability.getEffectBody();
            String raw = ability.getRawText() == null ? "" : ability.getRawText();

            String normalizedBody = normalize(body);
            String normalizedRaw = normalize(raw);

            for (CompiledPatternRule rule : effectPatternRules) {
                if (rule.ignoreEcho() && isEcho) {
                    continue;
                }
                if (!matchesFaction(card, rule.faction())) {
                    continue;
                }
                Matcher matcher = rule.pattern().matcher(body);
                if (matcher.find()) {
                    warnings.add(new EffectWarning(TYPE_PATTERN,
                            "Effect matches pattern '" + rule.pattern().pattern() + "'",
                            rule.pattern().pattern()));
                }
            }

            for (EffectLineRule rule : effectLineRules) {
                if (rule.ignoreEcho() && isEcho) {
                    continue;
                }
                if (!matchesFaction(card, rule.faction())) {
                    continue;
                }
                if (normalizedRaw.contains(rule.normalized()) || normalizedBody.contains(rule.normalized())) {
                    warnings.add(new EffectWarning(TYPE_EFFECT_LINE, "Effect line '" + rule.raw() + "'", rule.raw()));
                }
            }
        }
    }

    private boolean matchesFaction(Card card, String ruleFaction) {
        if (ruleFaction == null) {
            return true;
        }
        if (card.getMainFaction() == null) {
            return false;
        }
        String code = card.getMainFaction().getCode();
        if (code != null && ruleFaction.equals(code.toUpperCase(Locale.ROOT))) {
            return true;
        }
        String factionName = card.getMainFaction().getName();
        return factionName != null && ruleFaction.equals(factionName.toUpperCase(Locale.ROOT));
    }
}
