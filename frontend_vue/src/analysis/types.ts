// Types mirrored from the backend API.

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH'
/** ABSENT (the part is empty) and FRAGMENTS (one of some CardsData fragments) are only valid for ability parts. */
export type MatchMode = 'CONTAINS' | 'EXACT' | 'REGEX' | 'ABSENT' | 'FRAGMENTS'
export type RuleField =
  | 'NAME'
  | 'MAIN_EFFECT'
  | 'ECHO_EFFECT'
  | 'ANY_EFFECT'
  | 'KEYWORD'
  | 'TRIGGER'
  | 'HAND_COST'
  | 'RESERVE_COST'
  | 'CARD_TYPE'
  | 'SUBTYPE'
  | 'SET'
  | 'ABILITY'
export type AnalysisStatus = 'FLAGGED' | 'CLEARED' | 'REJECTED'
export type VerdictStatus = 'APPROVED' | 'REJECTED'

export type AbilitySlot = 'MAIN' | 'ECHO'
export type AbilityPart = 'trigger' | 'condition' | 'effect'

/** One ability of a card, split into its parts (any part may be empty). */
export interface Ability {
  slot: AbilitySlot
  trigger?: string | null
  condition?: string | null
  effect?: string | null
  /** CardsData fragment ids of the parts (absent for text-only sources). */
  triggerId?: number | null
  conditionId?: number | null
  effectId?: number | null
}

/** A CardsData effect fragment chosen in a rule; its text is kept for display and text-only cards. */
export interface FragmentRef {
  id: number
  text: string
}

/** A fragment offered by the rule editor (GET /api/form/fragments). */
export interface EffectFragment {
  id: number
  part: 'TRIGGER' | 'CONDITION' | 'EFFECT'
  text: string
  /** Number of unique card abilities using it in that part. */
  uses: number
}

/** How one part of an ability must look; null/undefined = any value. */
export interface PartMatch {
  mode: MatchMode
  value?: string
  /** For FRAGMENTS: any of these fragments. */
  fragments?: FragmentRef[]
}

/** A combination looked for in a single ability (for ABILITY conditions). */
export interface AbilityPattern {
  scope: AbilitySlot | 'ANY'
  trigger?: PartMatch | null
  condition?: PartMatch | null
  effect?: PartMatch | null
}

export interface RuleCondition {
  field: RuleField
  mode: MatchMode
  value: string
  negate: boolean
  /** For ABILITY conditions only. */
  ability?: AbilityPattern | null
}

export interface EffectRule {
  id?: string
  label: string
  reason?: string | null
  severity: Severity
  category?: string | null
  enabled: boolean
  conditions: RuleCondition[]
  factions: string[]
  createdFrom?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface RuleMatch {
  ruleId: string
  label: string
  severity: Severity
  category?: string | null
  reason?: string | null
  matchedText?: string | null
  source: 'NAME' | 'MAIN' | 'ECHO' | 'CARD'
  reviewed: boolean
  /** The ability matched by an ABILITY condition, split into its parts. */
  ability?: Ability | null
}

export interface Verdict {
  reference: string
  name?: string
  status: VerdictStatus
  note?: string
  reviewedRuleIds: string[]
  decidedAt: string
}

export interface CardAnalysis {
  reference: string
  name?: string
  factionCode?: string
  status: AnalysisStatus
  warnings: RuleMatch[]
  verdict?: Verdict | null
}

/** A rule that would flag a card but does not apply to it (information only). */
export interface DormantMatch {
  ruleId: string
  label: string
  severity: Severity
  matchedText?: string | null
  source?: string | null
  ability?: Ability | null
  /** The rule is switched off. */
  disabled: boolean
  /** The factions the rule is limited to, when the card is not one of them. */
  factions: string[]
}

/** As sent by POST /api/pool/analyze: status is null when the card only has dormant matches. */
export interface AnalyzedCard extends Omit<CardAnalysis, 'status'> {
  status: AnalysisStatus | null
  dormant?: DormantMatch[]
}

export interface Replacement {
  fromReference: string
  fromName?: string
  toReference: string
  toName?: string
  replacedAt: string
}

export interface ReportSummary {
  flagged: number
  cleared: number
  rejected: number
  bySeverity: Record<string, number>
  byFaction: Record<string, number>
}

export interface ReportCard {
  reference: string
  name?: string
  factionCode?: string
  factionName?: string
  setName?: string
  cardType?: string
  handCost?: string
  reserveCost?: string
  mainEffect?: string
  echoEffect?: string
  abilities?: Ability[] | null
  status: AnalysisStatus
  warnings: RuleMatch[]
  verdict?: Verdict | null
}

export interface Report {
  id: string
  title: string
  poolName?: string
  notes?: string
  createdAt: string
  poolSize: number
  summary: ReportSummary
  cards: ReportCard[]
  replacements: Replacement[]
}

export type ReportListItem = Omit<Report, 'cards' | 'replacements' | 'notes'>

/** Loose card shape as returned by the backend. */
export interface PoolCard {
  reference: string
  name?: string
  mainFaction?: { code?: string; name?: string }
  cardSet?: { code?: string; reference?: string; name?: string }
  cardType?: { name?: { en?: string } }
  mainEffect?: string
  echoEffect?: string
  elements?: Record<string, string>
  /** Abilities split into trigger / condition / effect (CardsData). */
  abilities?: Ability[] | null
  [key: string]: any
}

export interface FactionOption {
  id: string
  code: string
  name: string
}

export interface SetOption {
  reference: string
  name: string
  /** Default share of the set in a generated pool (0..1). */
  defaultWeight: number
}

/** Values offered by the generator form (GET /api/form/formValues). */
export interface Catalog {
  factions: FactionOption[]
  sets: SetOption[]
  subtypes: { name: string }[]
}
