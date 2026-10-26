// slot-lock.mjs の型（e2e/scripts/run.ts が TypeScript から読むため）
export type LockOwner = { worktree: string; pid: number; startedAt: string }
export type LockIo = {
  create(content: string): boolean
  read(): string | null
  moveAside(): string | null
  restore(): void
  discard(): void
}

export function acquireLock(input: {
  io: LockIo
  owner: LockOwner
  isRunning: (pid: number) => boolean
  waitMs: number
  now?: () => number
  sleep?: (ms: number) => Promise<void>
  intervalMs?: number
}): Promise<{ acquired: true } | { acquired: false; owner: LockOwner | null }>
export function busyLockMessage(input: { lock: string; owner: LockOwner | null }): string
export function ownsLock(content: string | null, owner: LockOwner): boolean
