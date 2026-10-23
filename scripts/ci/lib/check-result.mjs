// 検査の成功・失敗の判定。check-all.sh が、検査ごとに終了コードと出力の最後の行を見るために使う
//   使い方：node scripts/ci/lib/check-result.mjs <終了コード> <出力のファイル> <summary|none>
//   失敗なら理由を標準出力に出して終了コード 1 を返す。成功なら何も出さない
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
 * 検査の成功・失敗を判定する。終了コードが 0 でも、要約の行を求めるのに出さなかったら失敗にする。
 * 検査が途中で黙って終わっても、成功にしないため。
 *
 * @param {{ status: number, output: string, needsSummary: boolean }} result 終了コード、出力、要約の行を求めるか
 * @returns {{ ok: true } | { ok: false, reason: string }} 判定。失敗は理由を含む
 */
export function judgeCheck({ status, output, needsSummary }) {
  if (status !== 0) return { ok: false, reason: `終了コード ${status}` }
  if (needsSummary && !hasSummaryLine(output)) return { ok: false, reason: '終了コード 0 だが、最後の行が要約の行（「<何>：<件数>」）でない' }
  return { ok: true }
}

if (isMain(import.meta.url)) {
  const [status, file, mode] = process.argv.slice(2)
  const result = judgeCheck({ status: Number(status), output: readFileSync(file, 'utf8'), needsSummary: mode === 'summary' })
  if (!result.ok) {
    console.log(result.reason)
    process.exit(1)
  }
}
