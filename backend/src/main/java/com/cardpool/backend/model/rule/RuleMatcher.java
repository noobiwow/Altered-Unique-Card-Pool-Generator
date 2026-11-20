package com.cardpool.backend.model.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.cardpool.backend.dto.RuleMatch;
import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.AbilitySlot;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CostConstraint;
import com.cardpool.backend.model.rule.CardContext.AbilityView;

/**
 * A compiled, validated {@link EffectRule} that can be evaluated against cards.
 * Construction throws {@link IllegalArgumentException} for invalid rules (no
 * conditions, bad regex, bad cost expression, empty ability pattern...).
 */
public final class RuleMatcher {

    private final EffectRule rule;
    private final List<Condition> conditions;
    private final Set<String> factions;

    public RuleMatcher(EffectRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule is required");
        }
        if (rule.conditions() == null || rule.conditions().isEmpty()) {
            throw new IllegalArgumentException("A rule needs at least one condition");
        }
        this.rule = rule;
        List<Condition> compiled = new ArrayList<>();
        for (int i = 0; i < rule.conditions().size(); i++) {
            compiled.add(Condition.compile(rule.conditions().get(i), "Condition " + (i + 1)));
        }
        this.conditions = List.copyOf(compiled);
        this.factions = rule.factions() == null ? Set.of()
                : rule.factions().stream()
                        .filter(f -> f != null && !f.isBlank())
                        .map(f -> f.strip().toUpperCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    public EffectRule rule() {
        return rule;
    }

    public Optional<RuleMatch> match(Card card) {
        return match(new CardContext(card));
    }

    public Optional<RuleMatch> match(CardContext ctx) {
        if (!inFactionScope(ctx)) {
            return Optional.empty();
        }
        return matchIgnoringFactions(ctx);
    }

    /** True when the rule applies to the card's faction (no restriction, or one of its factions). */
    public boolean appliesTo(CardContext ctx) {
        return inFactionScope(ctx);
    }

    /** The match the rule would report, whatever the card's faction. */
    public Optional<RuleMatch> matchIgnoringFactions(CardContext ctx) {
        Hit first = null;
        for (Condition condition : conditions) {
            Hit hit = condition.evaluate(ctx);
            if (hit == null) {
                return Optional.empty();
            }
            if (first == null && hit.text() != null) {
                first = hit;
            }
        }
        return Optional.of(new RuleMatch(rule.id(), rule.label(), Severity.orDefault(rule.severity()),
                rule.category(), rule.reason(),
                first == null ? null : first.text(),
                first == null ? "CARD" : first.source(),
                false,
                first == null ? null : first.ability()));
    }

    private boolean inFactionScope(CardContext ctx) {
        if (factions.isEmpty()) {
            return true;
        }
        String code = ctx.factionCode();
        String name = ctx.card().getMainFaction() == null ? null : ctx.card().getMainFaction().getName();
        return (code != null && factions.contains(code.toUpperCase(Locale.ROOT)))
                || (name != null && factions.contains(name.toUpperCase(Locale.ROOT)));
    }

    // ------------------------------------------------------------------ text

    /**
     * Text as compared by rules: case-insensitive, whitespace collapsed and
     * keyword brackets unified ({@code [[Anchored]]} and {@code [Anchored]} are
     * the same), so rules work whatever the card source writes.
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return unifyBrackets(text.replace(' ', ' ')).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** {@code [[X]]} -> {@code [X]}; the static marker {@code []} is kept. */
    static String unifyBrackets(String text) {
        return text.replace("[[", "[").replace("]]", "]");
    }

    private static String trimTrailingPunctuation(String text) {
        return text.replaceAll("[\\s.!;:—\\-]+$", "");
    }

    /** One compiled text test: contains, exact, regex, or (for ability parts) absent. */
    static final class TextMatcher {
        private final MatchMode mode;
        private final String normalizedValue;
        private final Pattern pattern;

        TextMatcher(MatchMode mode, String value, boolean keyword, boolean allowAbsent, String where) {
            this.mode = mode == null ? MatchMode.CONTAINS : mode;
            if (this.mode == MatchMode.FRAGMENTS) {
                throw new IllegalArgumentException(where + ": fragments are only available for ability parts");
            }
            if (this.mode == MatchMode.ABSENT) {
                if (!allowAbsent) {
                    throw new IllegalArgumentException(where + ": \"is empty\" is only available for ability parts");
                }
                this.normalizedValue = null;
                this.pattern = null;
                return;
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(where + ": a value is required");
            }
            String effective = keyword && this.mode != MatchMode.REGEX ? value.replaceAll("[\\[\\]]", "") : value;
            String normalized = normalize(effective);
            this.normalizedValue = this.mode == MatchMode.EXACT ? trimTrailingPunctuation(normalized) : normalized;
            if (this.mode == MatchMode.REGEX) {
                try {
                    this.pattern = Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException(
                            where + ": invalid regular expression (" + e.getDescription() + ")", e);
                }
            } else {
                this.pattern = null;
            }
        }

        MatchMode mode() {
            return mode;
        }

        boolean matches(String text) {
            if (mode == MatchMode.ABSENT) {
                return text == null || text.isBlank();
            }
            if (text == null || text.isBlank()) {
                return false;
            }
            switch (mode) {
                case REGEX: {
                    String plain = text.replace(' ', ' ');
                    return pattern.matcher(plain).find() || pattern.matcher(unifyBrackets(plain)).find();
                }
                case EXACT:
                    return trimTrailingPunctuation(normalize(text)).equals(normalizedValue);
                default:
                    return normalize(text).contains(normalizedValue);
            }
        }
    }

    // ------------------------------------------------------------------ conditions

    /** Result of a condition that holds; any field may be null. */
    private record Hit(String text, String source, Ability ability) {
        static final Hit EMPTY = new Hit(null, null, null);

        static Hit of(AbilityView view) {
            return new Hit(view.text(), view.slot().name(), null);
        }
    }

    /** A compiled {@link RuleCondition}. */
    private interface Condition {

        /** @return a Hit when the condition holds, null otherwise. */
        Hit test(CardContext ctx);

        default Hit evaluate(CardContext ctx) {
            return test(ctx);
        }

        static Condition compile(RuleCondition c, String where) {
            if (c == null || c.field() == null) {
                throw new IllegalArgumentException(where + ": a field is required");
            }
            Condition compiled = switch (c.field()) {
                case HAND_COST, RESERVE_COST -> costCondition(c, where);
                case ABILITY -> new AbilityCondition(c.ability(), where);
                default -> textCondition(c, where);
            };
            return c.negate() ? ctx -> compiled.test(ctx) == null ? Hit.EMPTY : null : compiled;
        }

        private static Condition costCondition(RuleCondition c, String where) {
            CostConstraint cost;
            try {
                cost = CostConstraint.parse(c.value());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
            }
            if (cost == null) {
                throw new IllegalArgumentException(where + ": a cost such as 3- or 4+ is required");
            }
            Function<Card, Integer> value = c.field() == RuleField.HAND_COST ? Card::getMainCost : Card::getRecallCost;
            return ctx -> cost.matches(value.apply(ctx.card())) ? Hit.EMPTY : null;
        }

        private static Condition textCondition(RuleCondition c, String where) {
            TextMatcher m = new TextMatcher(c.modeOrDefault(), c.value(), c.field() == RuleField.KEYWORD, false, where);
            return switch (c.field()) {
                case NAME -> ctx -> m.matches(ctx.card().getName()) ? new Hit(ctx.card().getName(), "NAME", null) : null;
                case MAIN_EFFECT -> ctx -> effect(m, ctx, ctx.card().getMainEffect(), AbilitySlot.MAIN);
                case ECHO_EFFECT -> ctx -> effect(m, ctx, ctx.card().getEchoEffect(), AbilitySlot.ECHO);
                case ANY_EFFECT -> ctx -> firstNonNull(effect(m, ctx, ctx.card().getMainEffect(), AbilitySlot.MAIN),
                        effect(m, ctx, ctx.card().getEchoEffect(), AbilitySlot.ECHO));
                case KEYWORD -> ctx -> ctx.abilities().stream()
                        .filter(a -> a.keywords().stream().anyMatch(m::matches))
                        .findFirst().map(Hit::of).orElse(null);
                case TRIGGER -> ctx -> ctx.abilities().stream()
                        .filter(a -> m.matches(a.trigger()))
                        .findFirst().map(Hit::of).orElse(null);
                case CARD_TYPE -> ctx -> {
                    Card card = ctx.card();
                    boolean has = card.getCardType() != null && card.getCardType().getName() != null;
                    return has && m.matches(card.getCardTypeName()) ? Hit.EMPTY : null;
                };
                case SUBTYPE -> ctx -> ctx.card().getCardSubTypes() != null && ctx.card().getCardSubTypes().stream()
                        .anyMatch(st -> st != null && st.getName() != null && m.matches(st.getName().getEn_label()))
                                ? Hit.EMPTY
                                : null;
                case SET -> ctx -> m.matches(ctx.card().getSetCode()) || m.matches(ctx.card().getSetName())
                        ? Hit.EMPTY
                        : null;
                default -> throw new IllegalArgumentException(where + ": unsupported field " + c.field());
            };
        }

        /** An ability (or, failing that, the whole effect) of the slot matches. */
        private static Hit effect(TextMatcher m, CardContext ctx, String effectText, AbilitySlot slot) {
            for (AbilityView a : ctx.abilities(slot)) {
                if (m.matches(a.text()) || m.matches(a.effect())) {
                    return Hit.of(a);
                }
            }
            // A text spanning several abilities can still match the whole effect.
            if (m.mode() != MatchMode.EXACT && effectText != null && !effectText.isBlank() && m.matches(effectText)) {
                return new Hit(effectText.strip(), slot.name(), null);
            }
            return null;
        }

        private static Hit firstNonNull(Hit a, Hit b) {
            return a != null ? a : b;
        }
    }

    /** One part of an ability: its text and, for CardsData cards, its fragment id. */
    private interface PartTest {
        boolean matches(String text, Integer id, boolean hasIds);
    }

    /**
     * The part is one of the given fragments: by id when the card has fragment
     * ids, otherwise by text (same text as the fragment, static marker and
     * trailing punctuation ignored).
     */
    static final class FragmentMatcher implements PartTest {
        private final Set<Integer> ids;
        private final Set<String> texts;

        FragmentMatcher(List<PartMatch.FragmentRef> fragments, String where) {
            if (fragments == null || fragments.isEmpty()) {
                throw new IllegalArgumentException(where + ": choose at least one fragment");
            }
            this.ids = fragments.stream().map(PartMatch.FragmentRef::id).collect(Collectors.toUnmodifiableSet());
            this.texts = fragments.stream()
                    .map(PartMatch.FragmentRef::text)
                    .filter(t -> t != null && !t.isBlank())
                    .map(FragmentMatcher::comparable)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public boolean matches(String text, Integer id, boolean hasIds) {
            if (hasIds) {
                return id != null && ids.contains(id);
            }
            return text != null && !text.isBlank() && texts.contains(comparable(text));
        }

        static String comparable(String text) {
            String t = normalize(text);
            while (t.startsWith(Ability.STATIC_MARKER)) {
                t = t.substring(Ability.STATIC_MARKER.length()).strip();
            }
            return trimTrailingPunctuation(t);
        }
    }

    /** All given parts of an {@link AbilityPattern} match one ability. */
    private static final class AbilityCondition implements Condition {
        private final AbilityPattern.Scope scope;
        private final PartTest trigger, condition, effect;

        AbilityCondition(AbilityPattern pattern, String where) {
            if (pattern == null) {
                throw new IllegalArgumentException(where + ": describe the trigger, condition and/or effect to look for");
            }
            this.scope = pattern.scopeOrDefault();
            this.trigger = part(pattern.trigger(), where + " (trigger)");
            this.condition = part(pattern.condition(), where + " (condition)");
            this.effect = part(pattern.effect(), where + " (effect)");
            if (trigger == null && condition == null && effect == null) {
                throw new IllegalArgumentException(where + ": set at least one of trigger, condition or effect");
            }
        }

        private static PartTest part(PartMatch part, String where) {
            if (part == null || part.mode() == null) {
                return null;
            }
            if (part.mode() == MatchMode.FRAGMENTS) {
                return new FragmentMatcher(part.fragments(), where);
            }
            TextMatcher text = new TextMatcher(part.mode(), part.value(), false, true, where);
            return (value, id, hasIds) -> text.matches(value);
        }

        @Override
        public Hit test(CardContext ctx) {
            for (AbilityView a : ctx.abilities()) {
                boolean ids = a.hasFragmentIds();
                if (inScope(a) && matches(trigger, a.trigger(), a.triggerId(), ids)
                        && matches(condition, a.condition(), a.conditionId(), ids)
                        && matches(effect, a.effect(), a.effectId(), ids)) {
                    return new Hit(a.text(), a.slot().name(), a.toAbility());
                }
            }
            return null;
        }

        private boolean inScope(AbilityView a) {
            return scope == AbilityPattern.Scope.ANY || scope.name().equals(a.slot().name());
        }

        private static boolean matches(PartTest test, String part, Integer id, boolean hasIds) {
            return test == null || test.matches(part, id, hasIds);
        }
    }
}
