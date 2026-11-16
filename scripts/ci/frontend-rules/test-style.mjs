// 画面のテスト・テストの補助・E2E の規則：テストの間の空行
import { createLineLocator } from '../lib/line-number.mjs'
import { stripComments } from '../lib/strip-comments.mjs'

/** テストの呼び出しを閉じる行（`})`、`}, 30_000)`、`});`）。コメントと文字列を除いた行に当てる */
const CALL_CLOSE_LINE = /^\}.*\)[;,]?$/

/**
 * テスト・グループの呼び出しの始まりの行（test、it、describe と、test.each(…)、describe.skip(…)、test.describe(… など）。
 * `.` の後はテスト・グループの形に限る。test.beforeEach・test.use・test.step・test.info・test.setTimeout などは含めない（await 付きの test.step も含めない）
 */
const TEST_CALL_START = /^(?:test|it|describe)(?:\.(?:each|skip|only|fixme|fail|todo|concurrent|describe|serial|parallel))*\s*[(`]/

/** 1 行で閉じるテストの呼び出しの終わり（`)`、`);`） */
const CALL_ONE_LINE_END = /\)[;,]?$/

/**
 * 1 行で閉じる、テスト・グループの呼び出しかを返す（test('a', () => {}) のように、始まりの行で括弧が閉じるもの）。
 *
 * @param {string} line コメントと文字列を除いた行（前後の空白を除く）
 * @returns {boolean} 1 行で閉じていれば true
 */
function isOneLineTestCall(line) {
  if (!TEST_CALL_START.test(line) || !CALL_ONE_LINE_END.test(line)) return false
  let depth = 0
  for (const char of line) {
    if (char === '(') depth += 1
    else if (char === ')') depth -= 1
  }
  return depth === 0
}

/**
 * テストの間に空行がない箇所を探す（フロントのテストの書き方の「テストとテストの間に空行を置く」）。
 * テストを閉じる行（`})` と、1 行で閉じるテストの行）の直後に、空行なしで test・it・describe が続く箇所。間にコメントの行があっても、空行がなければ見つける。
 * test.beforeEach・test.use・test.step などはテスト・グループでないので、続く側として見ない。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（続く側の test・it・describe の行）
 */
export function findMissingTestSpacing(text) {
  const original = text.split('\n')
  const code = stripComments(text, { blankStrings: true }).split('\n')
  const hits = []
  code.forEach((line, index) => {
    if (!CALL_CLOSE_LINE.test(line.trim()) && !isOneLineTestCall(line.trim())) return
    let next = index + 1
    while (next < code.length && code[next].trim() === '' && original[next].trim() !== '') next += 1
    if (next < code.length && TEST_CALL_START.test(code[next].trim())) hits.push({ line: next + 1, text: original[next].trim() })
  })
  return hits
}
