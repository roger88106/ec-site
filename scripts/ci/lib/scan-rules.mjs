// 検査の script を走査して、共通の関数の使用を確認するテストの補助。
// 共通の関数（lib/ の関数）を、検査ごとに自前で書き直さないことを、禁じる書き方の表で確認する。
//
// 使い方：
// - テストは、走査する範囲を listScanScripts で求め、表を findRuleViolations に渡し、返りが空であることを確認する
// - 表の 1 行は { guard, forbidden, target, exceptions }
//   - guard：守る関数の名前（違反の報告に添える）
//   - forbidden：禁じる書き方の正規表現（g・y のフラグは使えない。lastIndex が持ち越されて script を見逃すので、例外にする）
//   - target：'code'（コメントを除いたコード。既定）か 'raw'（原文）
//   - exceptions：{ script の名前: 例外にする理由 }。守る関数を定義する script など
// - 新しい共通の関数を加えたときは、そのテストの表に行を追加する（表にない関数は、守られているか確認されない）
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { stripComments } from './strip-comments.mjs'

/** scripts/ のパス（走査する範囲の基準） */
const defaultRoot = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

/** 走査する範囲の名前と、その範囲の判定 */
const SCOPES = {
  // scripts/ci/ の直下の、テストでない .mjs
  ci: { dirs: ['ci'], recursive: false, withTests: false },
  // scripts/ci/ の直下と scripts/ci/lib/ の、テストでない .mjs
  'ci+lib': { dirs: ['ci', 'ci/lib'], recursive: false, withTests: false },
  // scripts/ の下の全ての .mjs（テストも含む。node_modules は除く）
  all: { dirs: ['.'], recursive: true, withTests: true },
}

/**
 * 走査する script の一覧を、名前の順に返す。
 *
 * @param {'ci' | 'ci+lib' | 'all'} scope 走査する範囲
 * @param {string} [root] scripts/ のパス（テストが別の場所に見立てるときに渡す）
 * @returns {{ name: string, raw: string, code: string }[]} script の名前（root からの相対パス、/ 区切り）、原文、コメントを除いたコード
 */
export function listScanScripts(scope, root = defaultRoot) {
  const option = SCOPES[scope]
  if (!option) throw new Error(`知らない走査の範囲：${scope}`)
  const files = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const file = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        if (option.recursive && entry.name !== 'node_modules') walk(file)
      } else if (entry.name.endsWith('.mjs') && (option.withTests || !entry.name.endsWith('.test.mjs'))) {
        files.push(file)
      }
    }
  }
  for (const dir of option.dirs) walk(path.join(root, dir))
  return files
    .map((file) => path.relative(root, file).replaceAll(path.sep, '/'))
    .sort()
    .map((name) => {
      const raw = readFileSync(path.join(root, name), 'utf8')
      return { name, raw, code: stripComments(raw, { keepRegex: true }) }
    })
}

/**
 * 表の禁じる書き方を含む script を返す。例外の script は返さない。
 *
 * @param {{ name: string, raw: string, code: string }[]} sources listScanScripts の返り
 * @param {{ guard: string, forbidden: RegExp, target?: 'code' | 'raw', exceptions: Record<string, string> }[]} rules 守る関数ごとの表
 * @returns {{ guard: string, name: string }[]} 違反した script（表の行の順、script の名前の順）
 * @throws 走査する script が 0 件のとき、または、どの script にも当たらない例外があるとき
 */
export function findRuleViolations(sources, rules) {
  if (sources.length === 0) throw new Error('走査する script が 0 件（走査の範囲が空）')
  const known = new Set(sources.map(({ name }) => name))
  const violations = []
  for (const { guard, forbidden, target = 'code', exceptions } of rules) {
    if (target !== 'code' && target !== 'raw') throw new Error(`${guard} の target が 'code'・'raw' でない：${String(target)}`)
    if (!(forbidden instanceof RegExp)) throw new Error(`${guard} の forbidden が正規表現でない`)
    if (forbidden.global || forbidden.sticky) throw new Error(`${guard} の forbidden に g・y のフラグがある（lastIndex が持ち越されて、script を見逃す）`)
    for (const name of Object.keys(exceptions)) {
      if (!known.has(name)) throw new Error(`${guard} の例外 ${name} が、走査する script にない`)
    }
    for (const source of sources) {
      if (source.name in exceptions) continue
      if (forbidden.test(source[target])) violations.push({ guard, name: source.name })
    }
  }
  return violations
}
