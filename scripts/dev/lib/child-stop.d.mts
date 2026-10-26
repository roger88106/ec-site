// child-stop.mjs の型（e2e/scripts/run.ts が TypeScript から読むため）
import type { ChildProcess } from 'node:child_process'

export const STOP_SIGNALS: readonly NodeJS.Signals[]
export function stopChild(child: ChildProcess, signal: NodeJS.Signals): void
export function stopOnSignals(options: { current: () => ChildProcess | null }): { isStopping: () => boolean }
