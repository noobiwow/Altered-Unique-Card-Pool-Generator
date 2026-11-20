// Display labels and constants of the analysis tools.

import type { AbilityPart, AbilityPattern, AnalysisStatus, EffectRule, MatchMode, RuleCondition, RuleField, Severity } from './types'

export const SEVERITIES: Severity[] = ['HIGH', 'MEDIUM', 'LOW']
export const SEVERITY_RANK: Record<Severity, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 }

export const FIELD_LABELS: Record<RuleField, string> = {
  NAME: 'Card name',
  MAIN_EFFECT: 'Main effect',
  ECHO_EFFECT: 'Echo effect',
  ANY_EFFECT: 'Main or echo effect',
  KEYWORD: 'Keyword',
  TRIGGER: 'Trigger',
  HAND_COST: 'Hand cost',
  RESERVE_COST: 'Reserve cost',
  CARD_TYPE: 'Card type',
  SUBTYPE: 'Sub type',
  SET: 'Set code',
  ABILITY: 'Ability (trigger + condition + effect)',
}

export const ABILITY_PARTS: AbilityPart[] = ['trigger', 'condition', 'effect']

export const PART_LABELS: Record<AbilityPart, string> = {
  trigger: 'Trigger',
  condition: 'Condition',
  effect: 'Effect',
}

export const MODE_LABELS: Record<MatchMode, string> = {
  CONTAINS: 'contains',
  EXACT: 'is exactly',
  REGEX: 'matches regex',
  ABSENT: 'is empty',
  FRAGMENTS: 'is one of',
}

export const STATUS_LABELS: Record<AnalysisStatus, string> = {
  FLAGGED: 'To review',
  CLEARED: 'Approved',
  REJECTED: 'Rejected',
}

export const FACTIONS = [
  { code: 'AX', name: 'Axiom' },
  { code: 'BR', name: 'Bravos' },
  { code: 'LY', name: 'Lyra' },
  { code: 'MU', name: 'Muna' },
  { code: 'OR', name: 'Ordis' },
  { code: 'YZ', name: 'Yzmir' },
]

/** Display order of the factions. */
export const FACTION_ORDER = FACTIONS.map((f) => f.code)

/** Position of a faction in {@link FACTION_ORDER} (unknown factions last). */
export const factionRank = (code?: string | null) => {
  const index = FACTION_ORDER.indexOf(code ?? '')
  return index === -1 ? FACTION_ORDER.length : index
}

export const factionName = (code?: string | null) =>
  FACTIONS.find((f) => f.code === code)?.name ?? code ?? 'Unknown faction'

export const isCostField = (field: RuleField) => field === 'HAND_COST' || field === 'RESERVE_COST'

const NEGATED_MODE_LABELS: Record<MatchMode, string> = {
  CONTAINS: 'does not contain',
  EXACT: 'is not',
  REGEX: 'does not match regex',
  ABSENT: 'is not empty',
  FRAGMENTS: 'is not one of',
}

/** “A”, “B” or “C” (long lists shortened). */
const describeFragments = (fragments: { text: string }[] = []) => {
  const quoted = fragments.map((f) => `“${f.text}”`)
  if (quoted.length <= 3) return quoted.join(' or ') || '(no fragment)'
  return `${quoted.slice(0, 2).join(', ')} or ${quoted.length - 2} more`
}

/** "trigger is “{J}” + condition is empty + effect contains “draw”" */
export const describeAbilityPattern = (p: AbilityPattern) => {
  const parts = ABILITY_PARTS.flatMap((part) => {
    const m = p[part]
    if (!m?.mode) return []
    if (m.mode === 'FRAGMENTS') return [`${part} is ${describeFragments(m.fragments)}`]
    return [`${part} ${MODE_LABELS[m.mode]}${m.mode === 'ABSENT' ? '' : ` “${m.value ?? ''}”`}`]
  })
  const where = p.scope === 'ECHO' ? 'an echo ability' : p.scope === 'MAIN' ? 'a main ability' : 'an ability'
  return `${where} where ${parts.join(' + ') || 'anything'}`
}

export const describeCondition = (c: RuleCondition) => {
  if (c.field === 'ABILITY') {
    return `${c.negate ? 'No ' : 'Has '}${c.ability ? describeAbilityPattern(c.ability) : 'ability'}`
  }
  return isCostField(c.field)
    ? `${FIELD_LABELS[c.field]} ${c.negate ? 'not ' : ''}${c.value}`
    : `${FIELD_LABELS[c.field]} ${(c.negate ? NEGATED_MODE_LABELS : MODE_LABELS)[c.mode]} “${c.value}”`
}

export const emptyRule = (): EffectRule => ({
  label: '',
  reason: '',
  severity: 'MEDIUM',
  category: '',
  enabled: true,
  conditions: [{ field: 'MAIN_EFFECT', mode: 'CONTAINS', value: '', negate: false }],
  factions: [],
})

