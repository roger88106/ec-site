// ============================================================================
// フロントの依存のライセンスを、方針（policy.json。依存とライセンス 第 2 節）と照らす
//
//   使い方：node scripts/ci/licenses/check-frontend.mjs
//
// リポジトリのルートで pnpm licenses list --json --recursive を流し、workspace のすべての部品
// （開発用を含む）を照らす。方針に合わない部品があれば、一覧を出して失敗する。
// ============================================================================
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

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
 * ライセンスの式（SPDX の OR・AND と括弧）を判定する。OR はどれか 1 つ、AND はすべてが通れば通る。
 *
 * @param expression ライセンスの式
 * @param name       部品の名前
 * @param policy     方針
 * @returns 通るか、通らない理由
 */
export function evaluate(expression, name, policy) {
  const text = expression.trim().replace(/^\((.*)\)$/, '$1').trim()
  const alternatives = text.split(/\s+OR\s+/)
  if (alternatives.length > 1) {
    const results = alternatives.map((alternative) => evaluate(alternative, name, policy))
    return results.find((result) => result.ok) ?? results[0]
  }
  const parts = text.split(/\s+AND\s+/)
  if (parts.length > 1) {
    return parts.map((part) => evaluate(part, name, policy)).find((result) => !result.ok) ?? { ok: true }
  }
  return evaluateOne(text, name, policy)
}

/**
 * pnpm licenses list --json の結果を照らす。
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

/** workspace の依存を照らし、合わない部品があれば失敗する */
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
    console.log('  方針に合う部品に替えるか、条件付きなら理由を scripts/ci/licenses/policy.json に足す（依存とライセンス）。')
  }
  process.exit(failures.length === 0 ? 0 : 1)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
