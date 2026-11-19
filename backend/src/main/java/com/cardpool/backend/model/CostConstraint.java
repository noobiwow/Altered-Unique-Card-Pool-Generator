package com.cardpool.backend.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple cost filter parsed from expressions like {@code 4+} (4 or more)
 * or {@code 2-} (2 or less). A plain number ({@code 3}) means exactly 3.
 */
public record CostConstraint(Integer min, Integer max) {

    private static final Pattern EXPRESSION = Pattern.compile("^(\\d+)\\s*([+-])?$");

    public static CostConstraint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Matcher matcher = EXPRESSION.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid cost filter '" + raw + "'. Use a value like 4+ or 2-.");
        }

        int value = Integer.parseInt(matcher.group(1));
        String operator = matcher.group(2);

        if ("+".equals(operator)) {
            return new CostConstraint(value, null);
        }
        if ("-".equals(operator)) {
            return new CostConstraint(null, value);
        }
        return new CostConstraint(value, value);
    }

    public boolean matches(int cost) {
        if (cost < 0) {
            return false;
        }
        if (min != null && cost < min) {
            return false;
        }
        return max == null || cost <= max;
    }
}
