// child-stop.mjs の型（e2e/scripts/run.ts が TypeScript から読むため）
import type { ChildProcess } from 'node:child_process'

export function stopOnSignals(options: { current: () => ChildProcess | null }): { isStopping: () => boolean }
