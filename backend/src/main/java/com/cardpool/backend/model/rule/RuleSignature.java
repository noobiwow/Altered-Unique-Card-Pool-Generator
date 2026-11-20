package com.cardpool.backend.model.rule;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What a rule looks for, written so that two rules flagging the same thing get
 * the same signature: conditions and factions in any order, text compared the
 * way rules compare it (case, whitespace and keyword brackets ignored). Label,
 * severity, reason, category and enabled state are not part of it.
 * <p>
 * A part matching fragments is described by the fragment texts, so one
 * fragment and "is exactly" that text are the same expectation.
 */
public final class RuleSignature {

    private RuleSignature() {
    }

    public static String of(EffectRule rule) {
        TreeSet<String> conditions = new TreeSet<>();
        if (rule.conditions() != null) {
            rule.conditions().stream().filter(Objects::nonNull).map(RuleSignature::condition).forEach(conditions::add);
        }
        TreeSet<String> factions = new TreeSet<>();
        if (rule.factions() != null) {
            rule.factions().stream()
                    .filter(f -> f != null && !f.isBlank())
                    .map(f -> f.strip().toUpperCase(Locale.ROOT))
                    .forEach(factions::add);
        }
        return String.join(" & ", conditions) + " | factions=" + String.join(",", factions);
    }

    private static String condition(RuleCondition c) {
        String head = (c.negate() ? "NOT " : "") + c.field() + " ";
        if (c.field() == RuleField.ABILITY) {
            AbilityPattern p = c.ability();
            if (p == null) {
                return head + "?";
            }
            return head + p.scopeOrDefault() + "{trigger " + part(p.trigger()) + "; condition " + part(p.condition())
                    + "; effect " + part(p.effect()) + "}";
        }
        if (c.field() != null && c.field().isCost()) {
            return head + (c.value() == null ? "" : c.value().replaceAll("\\s+", ""));
        }
        String value = c.value() == null ? "" : c.value();
        if (c.field() == RuleField.KEYWORD && c.modeOrDefault() != MatchMode.REGEX) {
            value = value.replaceAll("[\\[\\]]", "");
        }
        return head + text(c.modeOrDefault(), value);
    }

    private static String part(PartMatch m) {
        if (m == null || m.mode() == null) {
            return "any";
        }
        return switch (m.mode()) {
            case ABSENT -> "empty";
            case FRAGMENTS -> fragments(m.fragments());
            case EXACT -> "one of [" + RuleMatcher.FragmentMatcher.comparable(Objects.toString(m.value(), "")) + "]";
            default -> text(m.mode(), Objects.toString(m.value(), ""));
        };
    }

    private static String fragments(List<PartMatch.FragmentRef> fragments) {
        if (fragments == null) {
            return "one of []";
        }
        return fragments.stream()
                .map(f -> f.text() == null || f.text().isBlank() ? "#" + f.id()
                        : RuleMatcher.FragmentMatcher.comparable(f.text()))
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().collect(Collectors.joining(" | ", "one of [", "]"));
    }

    private static String text(MatchMode mode, String value) {
        return switch (mode) {
            // A regex is compared as written (only surrounding blanks ignored).
            case REGEX -> "regex " + value.strip();
            case EXACT -> "exact " + RuleMatcher.FragmentMatcher.comparable(value);
            default -> mode.name().toLowerCase(Locale.ROOT) + " " + RuleMatcher.normalize(value);
        };
    }
}
