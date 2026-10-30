// ============================================================================
// フロントの依存のライセンスを、方針（policy.json。依存とライセンスの「依存のライセンスの方針」）と照合する
//
//   使い方：node scripts/ci/licenses/check-frontend.mjs
//
// リポジトリのルートで pnpm licenses list --json --recursive を流し、workspace のすべての部品
// （開発用を含む）を照合する。方針に合わない部品があれば、一覧を出して失敗する。
// ============================================================================
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { isMain } from '../lib/is-main.mjs'

/**
 * 部品の名前が、方針の部品の名前の形（* は任意の文字列）に合うか。
 *
 * @param pattern 部品の名前の形（npm: を除いたもの）
 * @param name    部品の名前
 */
function matches(pattern, name) {
  const escaped = pattern.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replaceAll('*', '.*')
  return new RegExp(`^${escaped}$`).test(name)
}

/**
 * 1 つのライセンス（OR・AND を含まない）を判定する。
 *
 * @param license ライセンスの識別子
 * @param name    部品の名前
 * @param policy  方針
 * @returns 通るか、通らない理由
 */
function evaluateOne(license, name, policy) {
  const id = policy.normalize?.[license] ?? license
  if (policy.allowed.includes(id)) {
    return { ok: true }
  }
  if (policy.forbidden.includes(id) || /^unknown$/i.test(id) || id === '') {
    return { ok: false, reason: `禁止か分からないライセンス（${id || '空'}）` }
  }
  if (policy.conditional.licenses.includes(id)) {
    const listed = Object.keys(policy.conditional.components)
      .filter((key) => key.startsWith('npm:'))
      .some((key) => matches(key.slice('npm:'.length), name))
    return listed ? { ok: true } : { ok: false, reason: `条件付きのライセンス（${id}）で、一覧にない部品` }
  }
  return { ok: false, reason: `方針にないライセンス（${id}）` }
}

/**
 * ライセンスの式を、丸括弧・AND・OR・識別子の並びに分ける。WITH などの演算子でない語は識別子の一部になる。
 *
 * @param expression ライセンスの式
 * @returns 並び（括弧と演算子は文字列、識別子は { id }）
 */
function tokenize(expression) {
  const tokens = []
  let words = []
  const flush = () => {
    if (words.length > 0) {
      tokens.push({ id: words.join(' ') })
      words = []
    }
  }
  for (const word of expression.replace(/([()])/g, ' $1 ').split(/\s+/).filter((w) => w !== '')) {
    if (word === '(' || word === ')' || word === 'AND' || word === 'OR') {
      flush()
      tokens.push(word)
    } else {
      words.push(word)
    }
  }
  flush()
  return tokens
}

/**
 * ライセンスの式（SPDX の OR・AND と括弧）を判定する。AND は OR より強く結ぶ。
 * OR はどれか 1 つ、AND はすべてが通れば通る。解析できない式は通らない。
 *
 * @param expression ライセンスの式
 * @param name       部品の名前
 * @param policy     方針
 * @returns 通るか、通らない理由
 */
export function evaluate(expression, name, policy) {
  const tokens = tokenize(expression)
  let position = 0
  const syntaxError = (message) => {
    throw new SyntaxError(message)
  }

  const parseOr = () => {
    const results = [parseAnd()]
    while (tokens[position] === 'OR') {
      position++
      results.push(parseAnd())
    }
    return results.find((result) => result.ok) ?? results[0]
  }

  const parseAnd = () => {
    const results = [parsePrimary()]
    while (tokens[position] === 'AND') {
      position++
      results.push(parsePrimary())
    }
    return results.find((result) => !result.ok) ?? { ok: true }
  }

  const parsePrimary = () => {
    const token = tokens[position]
    if (token === undefined) {
      return syntaxError('式が途中で終わっている')
    }
    if (token === '(') {
      position++
      const result = parseOr()
      if (tokens[position] !== ')') {
        return syntaxError('括弧が閉じていない')
      }
      position++
      return result
    }
    if (typeof token === 'string') {
      return syntaxError(`${token} の位置が正しくない`)
    }
    position++
    return evaluateOne(token.id, name, policy)
  }

  try {
    if (tokens.length === 0) {
      return syntaxError('式が空')
    }
    const result = parseOr()
    if (position < tokens.length) {
      return syntaxError(`${tokens[position]?.id ?? tokens[position]} の位置が正しくない`)
    }
    return result
  } catch (error) {
    if (error instanceof SyntaxError) {
      return { ok: false, reason: `解析できないライセンスの式（${expression.trim() || '空'}）：${error.message}` }
    }
    throw error
  }
}

/**
 * pnpm licenses list --json の結果を照合する。
 *
 * @param report ライセンス → 部品（name、versions）の一覧
 * @param policy 方針
 * @returns 方針に合わない部品
 */
export function check(report, policy) {
  const failures = []
  for (const [license, packages] of Object.entries(report)) {
    for (const pkg of packages) {
      const result = evaluate(license, pkg.name, policy)
      if (!result.ok) {
        failures.push({ name: pkg.name, versions: pkg.versions, license, reason: result.reason })
      }
    }
  }
  return failures
}

/** workspace の依存を照合し、合わない部品があれば失敗する */
function main() {
  const here = path.dirname(fileURLToPath(import.meta.url))
  const root = path.resolve(here, '../../..')
  const policy = JSON.parse(readFileSync(path.join(here, 'policy.json'), 'utf8'))
  const output = execFileSync('pnpm', ['licenses', 'list', '--json', '--recursive'], {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    shell: process.platform === 'win32',
  })
  const report = JSON.parse(output)
  const failures = check(report, policy)
  for (const failure of failures) {
    console.log(`${failure.name}@${failure.versions.join(',')}：${failure.license}。${failure.reason}`)
  }
  const count = Object.values(report).reduce((sum, packages) => sum + packages.length, 0)
  console.log(`${failures.length === 0 ? 'OK' : 'NG'}：部品 ${count} 件、方針に合わない部品 ${failures.length} 件`)
  if (failures.length > 0) {
    console.log('  方針に合う部品に替えるか、条件付きなら理由を scripts/ci/licenses/policy.json に追加する（依存とライセンス）。')
  }
  process.exit(failures.length === 0 ? 0 : 1)
}

if (isMain(import.meta.url)) {
  main()
}
