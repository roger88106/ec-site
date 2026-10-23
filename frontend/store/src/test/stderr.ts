import { vi } from 'vitest'

/** 標準エラー出力に書いた内容の記録 */
export type StderrRecord = {
  /** 書いた行を JSON として読んだ配列（1 回の書き込みが 1 行 1 件の JSON であること） */
  entries: () => Record<string, unknown>[]
  /** 書いた内容をつなげた文字 */
  text: () => string
}

/**
 * 標準エラー出力（process.stderr.write）を差し替えて、書いた内容を記録する。<br />
 * 差し替えは setup.ts の restoreAllMocks で各テストの後に戻る。
 *
 * @returns 書いた内容を読む関数
 */
export function recordStderr(): StderrRecord {
  const write = vi.spyOn(process.stderr, 'write').mockImplementation(() => true)
  const chunks = () => write.mock.calls.map((call) => String(call[0]))
  return {
    entries: () => chunks().map((chunk) => JSON.parse(chunk)),
    text: () => chunks().join(''),
  }
}
