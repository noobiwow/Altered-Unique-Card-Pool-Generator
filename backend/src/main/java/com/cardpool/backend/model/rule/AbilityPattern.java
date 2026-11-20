package com.cardpool.backend.model.rule;

/**
 * A combination looked for in a single ability. Every part is optional (null =
 * any value) but at least one must be given; all given parts must match the
 * same ability.
 *
 * @param scope     MAIN or ECHO effect only, or ANY (null = ANY)
 * @param trigger   expected trigger, e.g. contains {@code {J}}
 * @param condition expected condition, e.g. ABSENT for "no condition"
 * @param effect    expected effect, e.g. contains {@code Draw}
 */
public record AbilityPattern(Scope scope, PartMatch trigger, PartMatch condition, PartMatch effect) {

    public enum Scope {
        MAIN, ECHO, ANY
    }

    public Scope scopeOrDefault() {
        return scope == null ? Scope.ANY : scope;
    }
}
