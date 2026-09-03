let currentPool: any[] | null = null
let refsPool: string[] | null = null

export function setPool(pool: any[]) {
  currentPool = pool
  refsPool = null
}

export function getPool(): any[] | null {
  return currentPool
}

export function setRefsPool(refs: string[]) {
  refsPool = refs
  currentPool = null
}

export function getRefsPool(): string[] | null {
  return refsPool
}

export function clearPool() {
  currentPool = null
  refsPool = null
}
