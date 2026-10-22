// lock-io.mjs の型（e2e/scripts/run.ts が TypeScript から読むため）
import type { LockIo } from './slot-lock.mjs'

export function createLockIo(lock: string): LockIo
