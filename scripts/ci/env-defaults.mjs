// 環境変数の文書（docs/guides/environment.md）の既定値が、コードの設定の型の既定値と一致することを確認する（バックエンドの書き方 第 6.1 節）。
// - コードの既定値（@ConfigurationProperties の record の @DefaultValue）は、文書に同じ値である
// - 文書に既定値（時間、数、真偽）を書いた設定は、コードも既定値を持つ
// 環境変数の名前は、接頭辞と項目の名前から作る（ecsite.payment の expiryInterval → ECSITE_PAYMENT_EXPIRY_INTERVAL）。
// 文書の省略形（`ECSITE_PAYMENT_DEADLINE`、`_EXPIRY_INTERVAL`）は、前の名前の後ろを置き換えて、コードにある名前に合わせる。
// 時間は、書き方によらず同じ長さなら同じ値とみなす（1m と PT1M）。
// Java のコメントの中の注釈は読まない。設定の値のクラスを 1 つも読めないとき、読めないクラス（record でない、引数やプロパティが 0 件など）があるとき、
// Java のファイルが 0 件のときも失敗にする。
// 使い方：node scripts/ci/env-defaults.mjs（リポジトリのルートで）
import { execFileSync } from 'node:child_process'
import { exitIfNoFiles, splitNulList } from './lib/git-files.mjs'
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'
import { stripJavaComments } from './lib/strip-comments.mjs'

/** 環境変数の文書 */
const ENV_DOC = 'docs/guides/environment.md'

/** 時間の単位の秒数 */
const UNIT_SECONDS = { ms: 0.001, s: 1, m: 60, h: 3600, d: 86400 }

/**
 * camelCase の名前を、環境変数の部分（大文字とアンダースコア）にする。
 *
 * @param {string} name 名前（camelCase か kebab-case）
 * @returns {string} 環境変数の部分
 */
function toEnvPart(name) {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[-.]/g, '_')
    .toUpperCase()
}

/**
 * 括弧と山括弧の外の、カンマで分ける。
 *
 * @param {string} text record の引数の並び
 * @returns {string[]} 引数
 */
function splitTopLevel(text) {
  const parts = []
  let depth = 0
  let current = ''
  for (const char of text) {
    if ('(<'.includes(char)) depth += 1
    if (')>'.includes(char)) depth -= 1
    if (char === ',' && depth === 0) {
      parts.push(current)
      current = ''
    } else {
      current += char
    }
  }
  if (current.trim()) parts.push(current)
  return parts.map((part) => part.trim()).filter(Boolean)
}

/**
 * record の宣言の引数の並びを返す。
 *
 * @param {string} source Java のソース
 * @param {number} start record の名前の後の開き括弧の位置
 * @returns {string} 括弧の中
 */
function paramsFrom(source, start) {
  let depth = 0
  for (let index = start; index < source.length; index += 1) {
    if (source[index] === '(') depth += 1
    if (source[index] === ')') {
      depth -= 1
      if (depth === 0) return source.slice(start + 1, index)
    }
  }
  return ''
}

/** 設定の値のクラスの注釈（接頭辞を文字列リテラルで書いた形）の後ろから、record の名前の開き括弧まで（間は注釈と修飾子だけ） */
const CONFIG_HEAD = /^(?:(?:prefix|value)\s*=\s*)?"([^"]+)"\)\s*(?:@[\w.]+(?:\([^)]*\))?\s*|(?:public|protected|private|static|final|sealed|strictfp)\s+)*record\s+(\w+)\s*\(/

/**
 * 設定の型の record から、環境変数の名前と既定値を作る。読めなかった設定のクラスは problems に理由を入れる（黙って飛ばさない）。
 *
 * @param {string} source Java のソース
 * @returns {{ properties: { env: string, value: string | null }[], problems: string[] }} 環境変数の名前と既定値（既定値がなければ null）と、読めなかった理由
 */
export function readProperties(source) {
  const code = stripJavaComments(source)
  const records = new Map()
  for (const match of code.matchAll(/\brecord\s+(\w+)\s*\(/g)) {
    records.set(match[1], paramsFrom(code, match.index + match[0].length - 1))
  }
  const properties = []
  const problems = []
  const walk = (recordName, envPrefix) => {
    for (const param of splitTopLevel(records.get(recordName) ?? '')) {
      const name = param.match(/(\w+)\s*$/)?.[1]
      if (!name) {
        problems.push(`record ${recordName} の引数「${param}」の名前を読めない`)
        continue
      }
      const type = param.replace(/@\w+(\([^)]*\))?/g, '').trim().split(/\s+/)[0].replace(/<.*$/, '')
      const env = `${envPrefix}_${toEnvPart(name)}`
      if (records.has(type) && type !== recordName) {
        walk(type, env)
        continue
      }
      const value = param.match(/@DefaultValue\("([^"]*)"\)/)
      properties.push({ env, value: value ? value[1] : null })
    }
  }
  for (const match of code.matchAll(/@ConfigurationProperties\(/g)) {
    const head = code.slice(match.index + match[0].length).match(CONFIG_HEAD)
    if (!head) {
      problems.push('@ConfigurationProperties の付いた宣言が、接頭辞を文字列リテラルで書いた record の形でない（record の設定のクラスにする）')
      continue
    }
    const [, prefix, recordName] = head
    if (!records.get(recordName)?.trim()) {
      problems.push(`record ${recordName} の引数を読めない（空か、括弧が閉じていない）`)
      continue
    }
    const before = properties.length
    walk(recordName, toEnvPart(prefix))
    if (properties.length === before) problems.push(`record ${recordName} から、プロパティが 0 件しか読めなかった`)
  }
  return { properties, problems }
}

/**
 * 設定の値のクラスを持つファイルを全て読む。1 つも見つけられないときも、理由にする。
 *
 * @param {{ file: string, source: string }[]} entries 設定の注釈を含むファイル
 * @returns {{ code: { env: string, value: string | null }[], problems: string[] }} 環境変数の名前と既定値、読めなかった理由（ファイルの名前が先頭）
 */
export function readAllProperties(entries) {
  const code = []
  const problems = []
  for (const { file, source } of entries) {
    const result = readProperties(source)
    code.push(...result.properties)
    problems.push(...result.problems.map((problem) => `${file}：${problem}`))
  }
  if (!entries.length) problems.push('設定の値のクラス（@ConfigurationProperties）を 1 つも見つけられない')
  return { code, problems }
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
 * 省略形の名前を、前の名前の後ろを置き換えて補う。
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

if (isMain(import.meta.url)) {
  const javaFiles = splitNulList(execFileSync('git', ['ls-files', '-z', '--', 'backend/ecsite/src/main/java/*.java'], { encoding: 'utf8' }))
  exitIfNoFiles(javaFiles, '設定を読む Java のファイル')
  const entries = javaFiles
    .map((file) => ({ file, source: readFileSync(file, 'utf8') }))
    .filter(({ source }) => stripJavaComments(source).includes('@ConfigurationProperties('))
  const { code, problems: unreadable } = readAllProperties(entries)
  const doc = parseEnvTable(readFileSync(ENV_DOC, 'utf8'), new Set(code.map((row) => row.env)))
  const problems = [...unreadable, ...compareDefaults(code, doc)]
  for (const problem of problems) console.log(problem)
  console.log(`環境変数の既定値の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
