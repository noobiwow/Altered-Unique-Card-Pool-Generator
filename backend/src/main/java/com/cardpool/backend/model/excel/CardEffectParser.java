package com.cardpool.backend.model.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw card effect text into a list of {@link ParsedAbility} objects.
 *
 * <h2>Effect text grammar (observed from data)</h2>
 * 
 * <pre>
 * Multiple abilities on a card are separated by two or more whitespace characters.
 *
 * Each ability has the form:
 *   [TRIGGER] [CONDITION —] EFFECT_BODY
 *
 * TRIGGER types:
 *   Activated : {H}, {J}, {R}, {F}  (action costs)
 *   Triggered : "When ...\u00a0\u2014", "At Dusk", "At Night"
 *   Static    : "[]" prefix, or no prefix
 *
 * CONDITION (optional, appears after trigger, separated by \u00a0— or colon):
 *   "If you control a token:", "If I'm in {M}:", etc.
 *
 * KEYWORD extraction:
 *   Named effects  : single brackets  [Sabotage], [Resupply], [Aerolith]
 *   Keyword statuses: double brackets [[Asleep]], [[Fleeting]], [[Anchored]]
 *   Cost/terrain symbols: curly braces {H}, {J}, {R}, {V}, {1} …
 * </pre>
 */
public class CardEffectParser {

    // Abilities on a single card are separated by 2+ whitespace chars (including
    // \u00a0)
    private static final Pattern ABILITY_SPLIT = Pattern.compile("[ \\u00a0]{2,}");

    // Activated triggers: {H}, {J}, {R}, {F} — uppercase action costs
    // Also matches [][][Scout] style triggers (only known action types, not
    // keywords)
    private static final Pattern ACTIVATED_TRIGGER = Pattern.compile(
            "^(\\[\\]\\[\\]\\[Scout\\]|\\{[HJRF]\\})\\s*");

    // Triggered triggers: "When ...\u00a0—" or "At Dusk", "At Night"
    // Also handles "After Rest:", "After Night:" etc. after the dash
    private static final Pattern TRIGGERED_TRIGGER = Pattern.compile(
            "^(When [^\\u2014]+?(?:[\\u00a0\\s]\\u2014|\\u2014)\\s*(?:After\\s+[^:]+:\\s*)?|At (?:Dusk|Dawn|Noon|Night)[\\u00a0\\s]\\u2014\\s*)",
            Pattern.UNICODE_CASE);

    // Static prefix "[]" or "[][]" (zero-cost static ability marker)
    // Matches: [] followed by ( [] optionally + whitespace ) or just whitespace or
    // end
    private static final Pattern STATIC_PREFIX = Pattern.compile("^\\[\\](\\[\\](\\s*)|\\s|\\Z)");

    // Condition: "If ... :" or "You may X. If you do:"
    private static final Pattern CONDITION = Pattern.compile(
            "^((?:If [^:]+(?::|,\\s*(?:otherwise|and))|You\\s+may\\s+[^:]+[.!?:]?\\s*If\\s+you\\s+do:?)\\s*)",
            Pattern.CASE_INSENSITIVE);

    // Keyword extraction
    private static final Pattern NAMED_EFFECT = Pattern.compile("\\[([^\\[\\]]+)\\]");
    private static final Pattern KEYWORD_STATUS = Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");
    private static final Pattern COST_SYMBOL = Pattern.compile("\\{([^}]+)\\}");

    /**
     * Splits {@code rawEffect} into individual abilities and parses each one.
     *
     * @param rawEffect the full effect text from the card (may be null or blank)
     * @return list of parsed abilities; empty list if input is null/blank
     */
    public List<ParsedAbility> parse(String rawEffect) {
        List<ParsedAbility> results = new ArrayList<>();
        if (rawEffect == null || rawEffect.isBlank())
            return results;

        String[] parts = ABILITY_SPLIT.split(rawEffect.strip());
        for (String part : parts) {
            String text = part.strip();
            if (!text.isBlank()) {
                results.add(parseAbility(text));
            }
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ParsedAbility parseAbility(String text) {
        String remaining = text;
        ParsedAbility.AbilityType type;
        String trigger;
        String condition = null;

        Matcher m;

        // 1. Try ACTIVATED trigger
        m = ACTIVATED_TRIGGER.matcher(remaining);
        if (m.find()) {
            type = ParsedAbility.AbilityType.ACTIVATED;
            trigger = m.group(1);
            remaining = remaining.substring(m.end());

            // Optional condition after activated trigger
            m = CONDITION.matcher(remaining);
            if (m.find()) {
                condition = m.group(1).strip();
                remaining = remaining.substring(m.end());
            }

            // 2. Try TRIGGERED trigger ("When ... —" or "At Dusk/Night —")
        } else {
            m = TRIGGERED_TRIGGER.matcher(remaining);
            if (m.find()) {
                type = ParsedAbility.AbilityType.TRIGGERED;
                trigger = m.group(1).replaceAll("[\\u00a0\u2014]+\\s*$", "").replaceAll("\\s+After\\s+[^:]+:\\s*$", "")
                        .strip();
                remaining = remaining.substring(m.end()).strip();

                // Optional condition after the dash ("— If I was [[Boosted]]: ...")
                m = CONDITION.matcher(remaining);
                if (m.find()) {
                    condition = m.group(1).strip();
                    remaining = remaining.substring(m.end());
                }

                // 3. STATIC ability ([] prefix or no trigger at all)
            } else {
                type = ParsedAbility.AbilityType.STATIC;
                trigger = null;

                // Strip static [] marker if present
                m = STATIC_PREFIX.matcher(remaining);
                if (m.find()) {
                    remaining = remaining.substring(m.end());
                }

                // Optional condition
                m = CONDITION.matcher(remaining);
                if (m.find()) {
                    condition = m.group(1).strip();
                    remaining = remaining.substring(m.end());
                }
            }
        }

        String effectBody = remaining.strip();

        // 4. Extract keywords from the FULL raw text (not just effectBody)
        List<String> namedEffects = extractNamedEffects(text);
        List<String> keywordStatuses = extractKeywordStatuses(text);
        List<String> costSymbols = extractCostSymbols(text);

        return new ParsedAbility(text, type, trigger, condition, effectBody,
                namedEffects, keywordStatuses, costSymbols);
    }

    /**
     * Extracts single-bracket named effects like [Sabotage], [Resupply].
     * Double-bracket content [[…]] is excluded (handled separately).
     */
    private List<String> extractNamedEffects(String text) {
        // First remove all [[...]] so we don't re-match them as single brackets
        String cleaned = text.replaceAll("\\[\\[[^\\[\\]]*\\]\\]", "");
        List<String> found = new ArrayList<>();
        Matcher m = NAMED_EFFECT.matcher(cleaned);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    /** Extracts double-bracket keyword statuses like [[Asleep]], [[Fleeting]]. */
    private List<String> extractKeywordStatuses(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = KEYWORD_STATUS.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    /** Extracts curly-brace symbols: {H}, {J}, {R}, {1}, {V} … */
    private List<String> extractCostSymbols(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = COST_SYMBOL.matcher(text);
        while (m.find()) {
            found.add("{" + m.group(1) + "}");
        }
        return found;
    }
}
