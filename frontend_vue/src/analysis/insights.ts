// Pure helpers deriving what to show from analysis results.

import { factionRank, SEVERITY_RANK } from './labels'
import type { Ability, AbilityPattern, CardAnalysis, PartMatch, PoolCard, ReportCard, RuleMatch, Severity } from './types'

/** Strongest severity of a list (null when empty). */
export const maxSeverity = (severities: Severity[]): Severity | null =>
  severities.reduce<Severity | null>((best, s) => (!best || SEVERITY_RANK[s] > SEVERITY_RANK[best] ? s : best), null)

export const highestSeverity = (warnings: RuleMatch[]): Severity | null => maxSeverity(warnings.map((w) => w.severity))

/**
 * Warnings that still need a decision. For an approved (cleared) card every
 * warning is kept, so it still shows why it was flagged.
 */
export const openWarnings = (a: Pick<CardAnalysis | ReportCard, 'status' | 'warnings'>): RuleMatch[] =>
  a.status === 'CLEARED' ? a.warnings : a.warnings.filter((w) => !w.reviewed)

/** Severity shown for a card: the strongest of its open warnings. */
export const topSeverity = (a?: Pick<CardAnalysis | ReportCard, 'status' | 'warnings'> | null): Severity | null =>
  a ? highestSeverity(openWarnings(a)) : null

/** Higher = needs attention first: to review, then rejected, then approved; by severity within each. */
export const urgency = (a?: CardAnalysis | null): number => {
  if (!a) return 0
  const status = a.status === 'FLAGGED' ? 30 : a.status === 'REJECTED' ? 20 : 10
  const severity = topSeverity(a)
  return status + (severity ? SEVERITY_RANK[severity] : 0)
}

/** Sorts cards most urgent first, then by faction. */
export const byUrgencyThenFaction =
  (analysisOf: (card: PoolCard) => CardAnalysis | undefined) => (a: PoolCard, b: PoolCard) =>
    urgency(analysisOf(b)) - urgency(analysisOf(a)) || factionRank(a.mainFaction?.code) - factionRank(b.mainFaction?.code)

/** Sorts cards by faction, then most urgent first. */
export const byFactionThenUrgency =
  (analysisOf: (card: PoolCard) => CardAnalysis | undefined) => (a: PoolCard, b: PoolCard) =>
    factionRank(a.mainFaction?.code) - factionRank(b.mainFaction?.code) || urgency(analysisOf(b)) - urgency(analysisOf(a))

/** An ability as printed: parts separated by a space, none after the static marker []. Same as the backend. */
export const abilityText = (a: Ability): string =>
  [a.trigger, a.condition, a.effect]
    .map((part) => part?.trim())
    .filter((part): part is string => !!part)
    .reduce((text, part) => (!text ? part : text.endsWith('[]') ? text + part : `${text} ${part}`), '')

/** Comparable form of an ability text (whitespace and case insensitive). */
export const abilityKey = (text: string) => text.replace(/[\s\u00a0]+/g, ' ').trim().toLowerCase()

/** The card's abilities (from CardsData), in display order. */
export const cardAbilities = (card: PoolCard): Ability[] => card.abilities ?? []

/** True when the ability comes from CardsData fragments (rules can match it by fragment id). */
export const hasFragmentIds = (a: Ability) => a.triggerId != null || a.conditionId != null || a.effectId != null

/** Only the fields the analysis needs: keeps request payloads small. */
export const slimCard = (card: PoolCard): PoolCard => ({
  reference: card.reference,
  name: card.name,
  mainFaction: card.mainFaction,
  cardSet: card.cardSet,
  cardType: card.cardType,
  cardSubTypes: card.cardSubTypes,
  mainEffect: card.mainEffect,
  echoEffect: card.echoEffect,
  abilities: card.abilities,
  elements: card.elements
    ? { MAIN_COST: card.elements.MAIN_COST, RECALL_COST: card.elements.RECALL_COST }
    : undefined,
})

/** Removes the static marker "[]" in front of a text. */
export const withoutStaticMarker = (text: string) => text.replace(/^(\[\])+\s*/, '').trim()

/**
 * A combination pattern describing one ability exactly: its trigger, its
 * condition (or none) and its effect. Meant as a starting point to loosen.
 */
export const patternFromAbility = (a: Ability): AbilityPattern => {
  if (hasFragmentIds(a)) {
    // CardsData card: the exact fragments, to widen with variants in the editor.
    const fragment = (id?: number | null, text?: string | null): PartMatch | null =>
      id != null && text?.trim() ? { mode: 'FRAGMENTS', fragments: [{ id, text: text.trim() }] } : null
    return {
      scope: a.slot,
      trigger: fragment(a.triggerId, a.trigger),
      condition: a.conditionId != null ? fragment(a.conditionId, a.condition) : { mode: 'ABSENT' },
      effect: fragment(a.effectId, a.effect ? withoutStaticMarker(a.effect) : null),
    }
  }
  const part = (value?: string | null, mode: PartMatch['mode'] = 'CONTAINS'): PartMatch | null =>
    value?.trim() ? { mode, value: value.trim() } : null
  return {
    scope: a.slot,
    trigger: part(a.trigger, 'EXACT'),
    condition: a.condition?.trim() ? part(a.condition) : { mode: 'ABSENT' },
    effect: part(a.effect ? withoutStaticMarker(a.effect) : null),
  }
}

/** True when the pattern looks for something and every given part has a value. */
export const isCompletePattern = (p?: AbilityPattern | null) => {
  if (!p) return false
  const parts = [p.trigger, p.condition, p.effect].filter((m): m is PartMatch => !!m?.mode)
  return (
    parts.length > 0 &&
    parts.every((m) => m.mode === 'ABSENT' || (m.mode === 'FRAGMENTS' ? !!m.fragments?.length : !!m.value?.trim()))
  )
}
