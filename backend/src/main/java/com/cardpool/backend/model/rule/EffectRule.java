package com.cardpool.backend.model.rule;

import java.util.List;

/**
 * A persisted rule describing an effect (or card) that may be dangerous for a
 * pool.
 *
 * @param id          stable identifier, assigned by the server
 * @param label       short human readable name
 * @param reason      why this effect is considered dangerous (shown to the team)
 * @param severity    how dangerous it is
 * @param category    free-form grouping, e.g. "Reserve", "Mana ramp"
 * @param enabled     disabled rules are kept but never flag cards (null = true)
 * @param conditions  tests that must all hold
 * @param factions    faction codes the rule is restricted to (empty = all)
 * @param createdFrom reference of the card the rule was created from, if any
 * @param createdAt   ISO-8601 creation timestamp
 * @param updatedAt   ISO-8601 last update timestamp
 */
public record EffectRule(
        String id,
        String label,
        String reason,
        Severity severity,
        String category,
        Boolean enabled,
        List<RuleCondition> conditions,
        List<String> factions,
        String createdFrom,
        String createdAt,
        String updatedAt) {

    public boolean active() {
        return enabled == null || enabled;
    }

    public EffectRule withMeta(String newId, String newCreatedAt, String newUpdatedAt) {
        return new EffectRule(newId, label, reason, Severity.orDefault(severity), category,
                enabled == null ? Boolean.TRUE : enabled,
                conditions == null ? List.of() : List.copyOf(conditions),
                factions == null ? List.of() : List.copyOf(factions),
                createdFrom, newCreatedAt, newUpdatedAt);
    }
}
