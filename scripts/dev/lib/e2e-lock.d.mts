// e2e-lock.mjs の型（e2e/scripts/run.ts が TypeScript から読むため）
export type LockOwner = { worktree: string; pid: number; startedAt: string }
export type LockDecision = { action: 'acquire'; stale?: LockOwner | null } | { action: 'busy'; owner: LockOwner }

export function formatLock(owner: LockOwner): string
export function parseLock(content: string): LockOwner | null
export function decideTakeover(input: { judged: string; moved: string }): 'take' | 'restore'
export function decideLock(input: { content: string | null; isRunning: (pid: number) => boolean }): LockDecision
export function isE2eRunningIn(input: { content: string | null; worktree: string; isRunning: (pid: number) => boolean }): boolean
