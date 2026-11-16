// 環境変数の文書（docs/06-手順書/environment.md）の既定値が、設定の値の型の既定値と一致することを確認する（文書の書き方の「言い回しと論理」にある「事実」の検査。既定値の決め方は、バックエンドの書き方の「設定の値の型」）。
// - コードの既定値（@ConfigurationProperties の record の @DefaultValue）は、文書に同じ値である
// - 文書に既定値（時間、数、真偽）を書いた設定は、コードも既定値を持つ
//
// コードの既定値は、spring-boot-configuration-processor がコンパイルで書き出す一覧（META-INF/spring-configuration-metadata.json）から読む。
// 一覧はコンパイルの出力なので、check-all.sh（commit の前の hook と docs.yml）では流さず、CI のビルドのジョブがコンパイルの後に流す。
// 環境変数の名前は、プロパティの名前から作る（ecsite.payment.expiry-interval → ECSITE_PAYMENT_EXPIRY_INTERVAL）。
// 時間は、書き方によらず同じ長さなら同じ値とみなす（1m と PT1M）。
//
// 失敗したとき：文書の表の行か値を修正する。一覧がないときは、先に backend/ecsite/ で ./gradlew compileJava を実行する。
//
// 除外：なし
//
// 使い方：node scripts/ci/backend/config-defaults.mjs [設定の一覧の JSON]（リポジトリのルートで。既定は METADATA）
import { existsSync, readFileSync } from 'node:fs'

/** 環境変数の文書 */
const ENV_DOC = 'docs/06-手順書/environment.md'

/** 設定の一覧（compileJava の出力） */
export const METADATA = 'backend/ecsite/build/classes/java/main/META-INF/spring-configuration-metadata.json'

/** 時間の単位の秒数 */
const UNIT_SECONDS = { ms: 0.001, s: 1, m: 60, h: 3600, d: 86400 }

/**
 * 設定の一覧から、環境変数の名前と既定値を作る。
 *
 * @param {string} json spring-configuration-metadata.json の中身
 * @returns {{ env: string, value: string | null }[]} 環境変数の名前と既定値（既定値がなければ null）
 * @throws {Error} プロパティが 0 件のとき
 */
export function parseMetadata(json) {
  const { properties = [] } = JSON.parse(json)
  if (properties.length === 0) throw new Error('設定の一覧に、プロパティが 1 つもない')
  return properties.map((property) => ({
    env: property.name.replace(/[-.]/g, '_').toUpperCase(),
    value: property.defaultValue === undefined ? null : String(property.defaultValue),
  }))
}

/**
 * 環境変数の文書の表を読む。
 *
 * @param {string} markdown 文書
 * @param {Set<string>} known コードにある環境変数の名前（省略形を補うため）
 * @returns {{ env: string, value: string }[]} 環境変数の名前と、表の「既定」の値（括弧の補足を除く）
 */
export function parseEnvTable(markdown, known) {
  const rows = []
  for (const line of markdown.split(/\r?\n/)) {
    const cells = line.split('|').slice(1, -1).map((cell) => cell.trim())
    if (cells.length < 3 || !cells[0].startsWith('`')) continue
    const names = [...cells[0].matchAll(/`([A-Z_][A-Z0-9_]*)`/g)].map((match) => match[1])
    if (!names.length || names[0].startsWith('_')) continue
    const values = cells.at(-1).split('、').map((value) => value.replace(/（.*?）/g, '').trim())
    const first = names[0]
    names.forEach((name, index) => {
      const env = name.startsWith('_') ? expand(first, name, known) : name
      rows.push({ env, value: values.length === names.length ? values[index] : values.join('、') })
    })
  }
  return rows
}

/**
 * 省略形の名前（`_EXPIRY_INTERVAL`）を、前の名前の後ろを置き換えて補う。
 *
 * @param {string} first 省略していない名前
 * @param {string} suffix 省略形（_ で始まる）
 * @param {Set<string>} known コードにある名前
 * @returns {string} 補った名前（コードにある名前に合うものがなければ、最後の部分を置き換えたもの）
 */
function expand(first, suffix, known) {
  const parts = first.split('_')
  for (let keep = parts.length - 1; keep >= 1; keep -= 1) {
    const candidate = parts.slice(0, keep).join('_') + suffix
    if (known.has(candidate)) return candidate
  }
  return parts.slice(0, -1).join('_') + suffix
}

/**
 * 値を比べられる形にする。時間は秒数、ほかはそのまま。
 *
 * @param {string} value 値
 * @returns {string} 比べる形
 */
export function normalizeValue(value) {
  const simple = value.match(/^(\d+)(ms|s|m|h|d)$/)
  if (simple) return `${Number(simple[1]) * UNIT_SECONDS[simple[2]]}s`
  const iso = value.match(/^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/)
  if (iso && value !== 'PT') {
    return `${Number(iso[1] ?? 0) * 3600 + Number(iso[2] ?? 0) * 60 + Number(iso[3] ?? 0)}s`
  }
  return value
}

/**
 * 値が既定値として書いた具体的な値（時間、数、真偽）かを返す。
 *
 * @param {string} value 文書の値
 * @returns {boolean} 具体的な値なら true（「必須」「—」や説明の文は false）
 */
function isConcrete(value) {
  return /^(\d+(ms|s|m|h|d)?|PT[\dHMS]+|true|false)$/.test(value)
}

/**
 * コードと文書の既定値の食い違いを探す。
 *
 * @param {{ env: string, value: string | null }[]} code コードの環境変数と既定値
 * @param {{ env: string, value: string }[]} doc 文書の環境変数と既定値
 * @returns {string[]} 食い違いの説明（環境変数の名前の順）
 */
export function compareDefaults(code, doc) {
  const documented = new Map(doc.map((row) => [row.env, row.value]))
  const problems = []
  for (const { env, value } of code) {
    const docValue = documented.get(env)
    if (value !== null && docValue === undefined) {
      problems.push(`${env}：コードの既定「${value}」が、文書にない`)
    } else if (value !== null && normalizeValue(docValue) !== normalizeValue(value)) {
      problems.push(`${env}：文書の既定「${docValue}」が、コードの既定「${value}」と違う`)
    } else if (value === null && docValue !== undefined && isConcrete(docValue)) {
      problems.push(`${env}：文書の既定「${docValue}」が、コードにない（コードは既定を持たない）`)
    }
  }
  return problems.sort()
}

if (import.meta.main) {
  const metadata = process.argv[2] ?? METADATA
  if (!existsSync(metadata)) {
    console.log(`設定の一覧（${metadata}）がない。先に backend/ecsite/ で ./gradlew compileJava を実行する`)
    process.exit(2)
  }
  const code = parseMetadata(readFileSync(metadata, 'utf8'))
  const doc = parseEnvTable(readFileSync(ENV_DOC, 'utf8'), new Set(code.map((row) => row.env)))
  const problems = compareDefaults(code, doc)
  for (const problem of problems) console.log(problem)
  console.log(`設定の既定値の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
