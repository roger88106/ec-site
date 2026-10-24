// 検査の成功・失敗の判定。check-all.sh が、検査ごとに終了コードと出力の最後の行を見るために使う
//   使い方：node scripts/ci/lib/check-result.mjs <終了コード> <出力のファイル> <summary|tests>
//   成功なら OK の 1 行を標準出力に出して終了コード 0 を返す。失敗なら理由を標準出力に出して終了コード 1 を返す
//   呼ぶ側は、OK の行が出たときだけ成功にする（この script が動かなかったときに成功にしないため）
import { readFileSync } from 'node:fs'
import { isMain } from './is-main.mjs'

/** 要約の行：「<何>：<件数>」。「：」の前が空でなく、「：」の後に数字がある */
const SUMMARY_LINE = /^[^\s：][^：]*：.*\d/

/**
 * 出力の最後の行が要約の行（「<何>：<件数>」）かを返す。最後の空行は無視する。
 *
 * @param {string} output 検査の出力
 * @returns {boolean} 要約の行があれば true
 */
export function hasSummaryLine(output) {
  const lines = output.split('\n').map((line) => line.trimEnd())
  const last = lines.findLast((line) => line !== '')
  return last !== undefined && SUMMARY_LINE.test(last)
}

/**
 * `node --test` の出力から、テストの数（spec の `ℹ tests N`、tap の `# tests N`）を読む。
 *
 * @param {string} output `node --test` の出力
 * @returns {number | null} テストの数。行がなければ null
 */
export function countTests(output) {
  const match = /^(?:ℹ|#) tests (\d+)\s*$/m.exec(output)
  return match ? Number(match[1]) : null
}

/**
 * 検査の成功・失敗を判定する。終了コードが 0 でも、次のときは失敗にする。検査が途中で黙って終わっても、成功にしないため。
 * - summary（検査）：最後の行が要約の行でない
 * - tests（script のテスト）：テストの数の行がないか、0 件（glob が 1 件も一致しないと、node --test は終了コード 0 で終わる）
 *
 * @param {{ status: number, output: string, mode: 'summary' | 'tests' }} result 終了コード、出力、判定の種類
 * @returns {{ ok: true } | { ok: false, reason: string }} 判定。失敗は理由を含む
 */
export function judgeCheck({ status, output, mode }) {
  if (status !== 0) return { ok: false, reason: `終了コード ${status}` }
  if (mode === 'summary') {
    if (!hasSummaryLine(output)) return { ok: false, reason: '終了コード 0 だが、最後の行が要約の行（「<何>：<件数>」）でない' }
    return { ok: true }
  }
  if (mode === 'tests') {
    const count = countTests(output)
    if (count === null) return { ok: false, reason: '終了コード 0 だが、node --test の出力にテストの数の行（tests N）がない' }
    if (count === 0) return { ok: false, reason: '終了コード 0 だが、テストが 0 件（テストのファイルの glob が 1 件も一致しない）' }
    return { ok: true }
  }
  return { ok: false, reason: `知らない判定の種類：${mode}（summary か tests）` }
}

if (isMain(import.meta.url)) {
  const [status, file, mode] = process.argv.slice(2)
  const result = judgeCheck({ status: Number(status), output: readFileSync(file, 'utf8'), mode })
  if (!result.ok) {
    console.log(result.reason)
    process.exit(1)
  }
  console.log('OK')
}
