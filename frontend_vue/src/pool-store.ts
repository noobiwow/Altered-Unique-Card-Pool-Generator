let currentPool: any[] | null = null

export function setPool(pool: any[]) {
  currentPool = pool
}

export function getPool(): any[] | null {
  return currentPool
}

export function clearPool() {
  currentPool = null
}
