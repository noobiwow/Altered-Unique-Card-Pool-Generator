// Every call to the backend goes through this module.

import axios from 'axios'
import { slimCard } from './analysis/insights'
import type {
  AnalyzedCard,
  CardAnalysis,
  Catalog,
  EffectFragment,
  EffectRule,
  PoolCard,
  Replacement,
  Report,
  ReportListItem,
  Verdict,
  VerdictStatus,
} from './analysis/types'

// Same origin everywhere: Vite proxies /api in dev, Spring Boot serves the app in production.
const http = axios.create({ baseURL: '' })

const data = <T>(promise: Promise<{ data: T }>) => promise.then((r) => r.data)
const id = (value: string) => encodeURIComponent(value)

/** The fragment list never changes while the app runs: fetched once. */
let fragmentsRequest: Promise<EffectFragment[]> | null = null

/** Readable message of a failed request ({"error": "..."} body when the backend sent one). */
export const errorMessage = (err: unknown): string => {
  const body = (err as any)?.response?.data
  if (body && typeof body === 'object' && body.error) return body.error
  return (err as any)?.message ?? String(err)
}

/** Downloads a Blob under the given file name. */
export const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

export interface CreateReportBody {
  title: string
  poolName?: string
  notes?: string
  cards: PoolCard[]
  replacements: Replacement[]
  includeCleared: boolean
}

export interface VerdictBody {
  name?: string
  status: VerdictStatus
  note?: string
  reviewedRuleIds: string[]
}

/** What the backend keeps of the current pool: its cards by reference. */
export interface SavedPoolBody {
  name: string
  filter: Record<string, unknown> | null
  replacements: Replacement[]
  generatedAt?: string
  references: string[]
}

export interface SavedPoolWithCards extends Omit<SavedPoolBody, 'references'> {
  updatedAt?: string
  size: number
  cards: PoolCard[]
}

export interface PoolSummary {
  name: string
  size: number
  replacements: number
  generatedAt?: string
  updatedAt?: string
}

export const api = {
  // ---- pools -----------------------------------------------------------------
  catalog: () => data(http.get<Catalog>('/api/form/formValues')),
  generate: (perFaction: number, form: Record<string, unknown>) =>
    data(http.post<PoolCard[]>('/api/pool/generate', form, { params: { size: perFaction } })),
  exportExcel: (cards: PoolCard[]) =>
    data(http.post<Blob>('/api/pool/export', cards, { responseType: 'blob' })),

  replacements: (card: PoolCard, poolReferences: string[], filter: unknown, count = 24) =>
    data(
      http.post<PoolCard[]>('/api/pool/replacements', {
        card: slimCard(card),
        poolReferences,
        filter: filter ?? null,
        count,
      })
    ),

  // ---- current pool (saved by the backend) ---------------------------------
  /** null when there is no current pool (204). */
  currentPool: () => data(http.get<SavedPoolWithCards | ''>('/api/current-pool')).then((p) => p || null),
  currentPoolSummary: () =>
    data(http.get<PoolSummary | ''>('/api/current-pool/summary')).then((p) => p || null),
  saveCurrentPool: (pool: SavedPoolBody) => data(http.put<PoolSummary>('/api/current-pool', pool)),

  // ---- analysis ----------------------------------------------------------------
  analyze: (cards: PoolCard[]) => data(http.post<AnalyzedCard[]>('/api/pool/analyze', cards.map(slimCard))),

  fragments: () => {
    fragmentsRequest ??= data(http.get<EffectFragment[]>('/api/form/fragments')).catch((err) => {
      fragmentsRequest = null
      throw err
    })
    return fragmentsRequest
  },

  listRules: () => data(http.get<EffectRule[]>('/api/rules')),
  createRule: (rule: EffectRule) => data(http.post<EffectRule>('/api/rules', rule)),
  updateRule: (ruleId: string, rule: EffectRule) => data(http.put<EffectRule>(`/api/rules/${id(ruleId)}`, rule)),
  deleteRule: (ruleId: string) => http.delete(`/api/rules/${id(ruleId)}`),
  previewRule: (rule: EffectRule, cards: PoolCard[]) =>
    data(http.post<CardAnalysis[]>('/api/rules/preview', { rule, cards: cards.map(slimCard) })),

  setVerdict: (reference: string, body: VerdictBody) =>
    data(http.put<Verdict>(`/api/verdicts/${id(reference)}`, body)),
  clearVerdict: (reference: string) => http.delete(`/api/verdicts/${id(reference)}`),

  listReports: () => data(http.get<ReportListItem[]>('/api/reports')),
  getReport: (reportId: string) => data(http.get<Report>(`/api/reports/${id(reportId)}`)),
  deleteReport: (reportId: string) => http.delete(`/api/reports/${id(reportId)}`),
  createReport: (body: CreateReportBody) =>
    data(http.post<Report>('/api/reports', { ...body, cards: body.cards.map(slimCard) })),
}
