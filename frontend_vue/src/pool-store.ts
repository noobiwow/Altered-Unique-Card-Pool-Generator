// The pool being worked on. The backend keeps it (data/current-pool.json), so
// it survives page changes, reloads and restarts; a copy is kept in memory so
// coming back to the pool page is instant.

import { ref } from 'vue'
import { api, errorMessage, type PoolSummary } from './api'
import type { PoolCard, Replacement } from './analysis'

/** What is known about a pool, kept alongside it. */
export interface PoolContext {
  /** Display name, e.g. "Generated pool 19/11/2026". */
  name: string
  /** Filters the pool was generated with (reused for replacements). */
  filter: Record<string, unknown> | null
  /** Cards swapped from the analysis view. */
  replacements: Replacement[]
  /** ISO date the pool was generated. */
  generatedAt?: string
}

export interface StoredPool {
  cards: PoolCard[]
  context: PoolContext
}

/** Keys of the localStorage copy used before the backend kept the pool. */
const LEGACY_KEYS = { cards: 'cardpool_generated_pool', context: 'cardpool_pool_context' }

/** undefined: not loaded from the backend yet. */
let current: StoredPool | null | undefined
let loading: Promise<StoredPool | null> | null = null
/** Saves are sent one after the other so the last change always wins. */
let saving: Promise<unknown> = Promise.resolve()

/** Message of the last failed save, '' once a save succeeds. */
export const saveError = ref('')

function readLegacy(): StoredPool | null {
  try {
    const cards = JSON.parse(localStorage.getItem(LEGACY_KEYS.cards) ?? 'null')
    if (!Array.isArray(cards) || !cards.length) return null
    const raw = JSON.parse(localStorage.getItem(LEGACY_KEYS.context) ?? 'null') ?? {}
    return {
      cards,
      context: {
        name: raw.name ?? 'Generated pool',
        filter: raw.filter ?? null,
        replacements: Array.isArray(raw.replacements) ? raw.replacements : [],
        generatedAt: typeof raw.generatedAt === 'string' ? raw.generatedAt : undefined,
      },
    }
  } catch {
    return null
  }
}

function clearLegacy() {
  try {
    localStorage.removeItem(LEGACY_KEYS.cards)
    localStorage.removeItem(LEGACY_KEYS.context)
  } catch {}
}

async function fetchCurrent(): Promise<StoredPool | null> {
  const saved = await api.currentPool()
  if (saved) {
    clearLegacy()
    const { cards, name, filter, replacements, generatedAt } = saved
    return { cards, context: { name, filter: filter ?? null, replacements: replacements ?? [], generatedAt } }
  }
  // A pool kept by the browser before the backend did: hand it over once.
  const legacy = readLegacy()
  if (legacy) {
    await saveCurrentPool(legacy.cards, legacy.context)
    if (!saveError.value) clearLegacy()
  }
  return legacy
}

/** The current pool with its cards, or null when none was generated yet. */
export function loadCurrentPool(): Promise<StoredPool | null> {
  if (current !== undefined) return Promise.resolve(current)
  loading ??= fetchCurrent()
    .then((pool) => (current = pool))
    .finally(() => (loading = null))
  return loading
}

/** What the home page shows, without loading the cards when they are not in memory. */
export async function currentPoolSummary(): Promise<PoolSummary | null> {
  if (current === undefined) {
    const summary = await api.currentPoolSummary()
    if (summary || !readLegacy()) return summary
    await loadCurrentPool()
  }
  if (!current) return null
  return {
    name: current.context.name,
    size: current.cards.length,
    replacements: current.context.replacements.length,
    generatedAt: current.context.generatedAt,
  }
}

/**
 * Makes this pool the current one (memory first, so the pages see it at once)
 * and saves it. Never throws: a failure is reported in {@link saveError}.
 */
export function saveCurrentPool(cards: PoolCard[], context: PoolContext): Promise<void> {
  current = { cards, context }
  const body = { ...context, references: cards.map((card) => card.reference) }
  const request = saving.then(() => api.saveCurrentPool(body))
  saving = request.then(
    () => (saveError.value = ''),
    (err) => (saveError.value = `The pool could not be saved: ${errorMessage(err)}`)
  )
  return saving.then(() => undefined)
}
