// バックエンドのエラーの文に、数字を直接書いていないことを確認する（バックエンドの書き方 第 3.2 節）。
// 上限・桁数などの値を文に書くと、規則の値が変わったときに文が取り残される。値はドメインの定数から {名前} で差し込む。
// 差し込み（{max} など）を除いた文に数字があれば失敗にする。数え方の言葉（「1 つだけ」など）で規則の値でないものは、
// 理由を添えて message-numbers-exclusions.json に書く。メールの文（ecsite.notification.*）はエラーの文でないので見ない。
// 使い方：node scripts/ci/message-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** エラーの文のファイル */
const MESSAGES = 'backend/ecsite/src/main/resources/messages.properties'

/** 例外の一覧（キー → 理由） */
const EXCLUSIONS = 'scripts/ci/message-numbers-exclusions.json'

/** 見ないキーの接頭辞（エラーの文でないもの） */
const IGNORED_PREFIXES = ['ecsite.notification.']

/** 差し込み（{max} など） */
const PLACEHOLDER = /\{\w+\}/g

/** 数字（半角と全角） */
const DIGIT = /[0-9０-９]/

/**
 * properties の文を、キーと文の組にする。注釈（# か !）と空行は飛ばし、行末の \ で続く行はつなぐ。
 *
 * @param {string} text properties のファイルの中身
 * @returns {{ key: string, value: string, line: number }[]} キー、文、始まりの行
 */
export function parseProperties(text) {
  const entries = []
  const lines = text.split(/\r?\n/)
  for (let i = 0; i < lines.length; i += 1) {
    const start = i
    let line = lines[i]
    if (/^\s*(?:[#!]|$)/.test(line)) continue
    while (line.endsWith('\\') && i + 1 < lines.length) {
      i += 1
      line = line.slice(0, -1) + lines[i].trimStart()
    }
    const separator = line.search(/[=:]/)
    if (separator < 0) continue
    entries.push({ key: line.slice(0, separator).trim(), value: line.slice(separator + 1).trim(), line: start + 1 })
  }
  return entries
}

/**
 * 差し込みを除いた文に数字のあるエラーの文を探す。
 *
 * @param {string} text       properties のファイルの中身
 * @param {Record<string, string>} exclusions 例外の一覧（キー → 理由）
 * @returns {{ key: string, value: string, line: number }[]} 数字のある文
 */
export function findNumbers(text, exclusions) {
  return parseProperties(text).filter(
    ({ key, value }) =>
      !IGNORED_PREFIXES.some((prefix) => key.startsWith(prefix)) &&
      !(key in exclusions) &&
      DIGIT.test(value.replace(PLACEHOLDER, '')),
  )
}

/**
 * 例外の一覧のうち、ファイルにないキーと、数字のない文のキーを探す（例外が取り残されないように）。
 *
 * @param {string} text       properties のファイルの中身
 * @param {Record<string, string>} exclusions 例外の一覧（キー → 理由）
 * @returns {string[]} 不要な例外のキー
 */
export function findStaleExclusions(text, exclusions) {
  const values = new Map(parseProperties(text).map(({ key, value }) => [key, value]))
  return Object.keys(exclusions).filter((key) => !values.has(key) || !DIGIT.test(values.get(key).replace(PLACEHOLDER, '')))
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const text = readFileSync(MESSAGES, 'utf8')
  const exclusions = JSON.parse(readFileSync(EXCLUSIONS, 'utf8'))
  const hits = findNumbers(text, exclusions)
  for (const hit of hits) {
    console.log(`${MESSAGES}:${hit.line} ${hit.key}=${hit.value}`)
  }
  const stale = findStaleExclusions(text, exclusions)
  for (const key of stale) {
    console.log(`${EXCLUSIONS}：不要な例外 ${key}`)
  }
  console.log(`数字を書いたエラーの文：${hits.length}、不要な例外：${stale.length}`)
  process.exit(hits.length || stale.length ? 1 : 0)
}
