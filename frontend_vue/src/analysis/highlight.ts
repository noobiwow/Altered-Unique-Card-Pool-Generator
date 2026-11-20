// Highlighting of what triggered a rule in the card text.

import { abilityKey, abilityText, withoutStaticMarker } from './insights'
import { ABILITY_PARTS, SEVERITY_RANK } from './labels'
import type { Ability, AbilityPart, EffectRule, PartMatch, RuleField, RuleMatch, Severity } from './types'

export interface TextSegment {
  text: string
  /** Severity of the strongest rule matching this part, if any. */
  severity?: Severity
  reviewed?: boolean
  /** Ability part the text belongs to (ability rendering only). */
  part?: AbilityPart
}

/** Where a highlighter applies. */
type Target =
  | { kind: 'text'; scope: 'MAIN' | 'ECHO' | 'ANY' | 'NAME' }
  /** One part of one specific ability (the one an ABILITY condition matched). */
  | { kind: 'part'; part: AbilityPart; abilityKey: string }

export interface Highlighter {
  pattern: RegExp
  severity: Severity
  reviewed: boolean
  target: Target
}

const escapeRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Literal text as a tolerant regex: any whitespace, [x] or [[x]] brackets. */
const literalPattern = (value: string) =>
  escapeRegex(value.trim())
    .replace(/\s+/g, '[\\s\\u00a0]+')
    .replace(/\\\[(\\\[)?/g, '\\[{1,2}')
    .replace(/\\\](\\\])?/g, '\\]{1,2}')

const textScope = (field: RuleField): 'MAIN' | 'ECHO' | 'ANY' | 'NAME' | null => {
  switch (field) {
    case 'MAIN_EFFECT':
      return 'MAIN'
    case 'ECHO_EFFECT':
      return 'ECHO'
    case 'ANY_EFFECT':
    case 'KEYWORD':
    case 'TRIGGER':
      return 'ANY'
    case 'NAME':
      return 'NAME'
    default:
      return null
  }
}

const regexOf = (source: string) => {
  try {
    return new RegExp(source, 'gi')
  } catch {
    return null // invalid in the browser's regex flavour: no highlight
  }
}

/** Regex of an ability part expectation (an exact part is highlighted whole). */
const partRegex = (m: PartMatch | null | undefined) => {
  if (m?.mode === 'FRAGMENTS') {
    const texts = (m.fragments ?? []).map((f) => withoutStaticMarker(f.text)).filter(Boolean)
    return texts.length ? regexOf(texts.map(literalPattern).join('|')) : null
  }
  if (!m?.mode || m.mode === 'ABSENT' || !m.value?.trim()) return null
  return regexOf(m.mode === 'REGEX' ? m.value : literalPattern(m.value))
}

/** Patterns highlighting what each warning's rule looks for. */
export const highlightersFor = (warnings: RuleMatch[], rulesById: Record<string, EffectRule>): Highlighter[] => {
  const result: Highlighter[] = []
  for (const w of warnings) {
    const rule = rulesById[w.ruleId]
    if (!rule) continue
    for (const c of rule.conditions) {
      if (c.negate) continue
      if (c.field === 'ABILITY') {
        // Only the ability the combination matched is highlighted, part by part.
        if (!c.ability || !w.ability) continue
        const key = abilityKey(abilityText(w.ability))
        for (const part of ABILITY_PARTS) {
          const pattern = partRegex(c.ability[part])
          if (pattern) {
            result.push({ pattern, severity: w.severity, reviewed: w.reviewed, target: { kind: 'part', part, abilityKey: key } })
          }
        }
        continue
      }
      const scope = textScope(c.field)
      if (!scope || !c.value?.trim()) continue
      const source =
        c.field === 'KEYWORD' && c.mode !== 'REGEX'
          ? `\\[{1,2}${literalPattern(c.value.replace(/[[\]]/g, ''))}\\]{1,2}`
          : c.mode === 'REGEX'
            ? c.value
            : literalPattern(c.value)
      const pattern = regexOf(source)
      if (pattern) result.push({ pattern, severity: w.severity, reviewed: w.reviewed, target: { kind: 'text', scope } })
    }
  }
  return result
}

/** Abilities matched by an ABILITY condition, by ability key, with their strongest severity. */
export const matchedCombinations = (warnings: RuleMatch[]): Map<string, { severity: Severity; reviewed: boolean }> => {
  const result = new Map<string, { severity: Severity; reviewed: boolean }>()
  for (const w of warnings) {
    if (!w.ability) continue
    const key = abilityKey(abilityText(w.ability))
    const current = result.get(key)
    if (!current || stronger({ severity: w.severity, reviewed: w.reviewed }, current)) {
      result.set(key, { severity: w.severity, reviewed: w.reviewed })
    }
  }
  return result
}

type Mark = Pick<Highlighter, 'severity' | 'reviewed'>

/** Unreviewed beats reviewed; then the higher severity wins. */
const stronger = (candidate: Mark, current: Mark | null) =>
  !current ||
  (current.reviewed && !candidate.reviewed) ||
  (current.reviewed === candidate.reviewed && SEVERITY_RANK[candidate.severity] > SEVERITY_RANK[current.severity])

/** Marks every match of {@code pattern} in text[from, to). */
const markMatches = (text: string, from: number, to: number, h: Mark & { pattern: RegExp }, marks: (Mark | null)[]) => {
  const slice = text.slice(from, to)
  h.pattern.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = h.pattern.exec(slice)) !== null) {
    if (m[0].length === 0) {
      h.pattern.lastIndex++
      continue
    }
    for (let i = from + m.index; i < from + m.index + m[0].length; i++) {
      if (stronger(h, marks[i])) marks[i] = h
    }
  }
}

/** Groups consecutive characters with the same mark (and part) into segments. */
const toSegments = (text: string, marks: (Mark | null)[], parts?: (AbilityPart | undefined)[]): TextSegment[] => {
  const segments: TextSegment[] = []
  for (let i = 0; i < text.length; ) {
    let j = i + 1
    while (j < text.length && marks[j] === marks[i] && parts?.[j] === parts?.[i]) j++
    segments.push({ text: text.slice(i, j), severity: marks[i]?.severity, reviewed: marks[i]?.reviewed, part: parts?.[i] })
    i = j
  }
  return segments
}

const appliesToText = (h: Highlighter, scope: 'MAIN' | 'ECHO' | 'NAME') =>
  h.target.kind === 'text' && (h.target.scope === scope || (h.target.scope === 'ANY' && scope !== 'NAME'))

/** Splits a plain text (card name, effect line) into plain and highlighted parts. */
export const highlight = (text: string, highlighters: Highlighter[], scope: 'MAIN' | 'ECHO' | 'NAME'): TextSegment[] => {
  const marks: (Mark | null)[] = new Array(text.length).fill(null)
  for (const h of highlighters) {
    if (appliesToText(h, scope)) markMatches(text, 0, text.length, h, marks)
  }
  return toSegments(text, marks)
}

/**
 * Splits an ability into segments tagged with their part (trigger, condition,
 * effect) and highlighted by text rules on the whole line and by ability
 * rules on their own part of the matched ability.
 */
export const highlightAbility = (ability: Ability, highlighters: Highlighter[]): TextSegment[] => {
  // Rebuild the printed text while recording which part each character belongs to.
  let text = ''
  const parts: (AbilityPart | undefined)[] = []
  const ranges: Partial<Record<AbilityPart, [number, number]>> = {}
  for (const part of ABILITY_PARTS) {
    const value = ability[part]?.trim()
    if (!value) continue
    if (text && !text.endsWith('[]')) {
      text += ' '
      parts.push(undefined)
    }
    ranges[part] = [text.length, text.length + value.length]
    text += value
    for (let i = 0; i < value.length; i++) parts.push(part)
  }

  const marks: (Mark | null)[] = new Array(text.length).fill(null)
  const key = abilityKey(text)
  for (const h of highlighters) {
    if (h.target.kind === 'text') {
      if (appliesToText(h, ability.slot)) markMatches(text, 0, text.length, h, marks)
    } else if (h.target.abilityKey === key) {
      const range = ranges[h.target.part]
      if (range) markMatches(text, range[0], range[1], h, marks)
    }
  }
  return toSegments(text, marks, parts)
}
