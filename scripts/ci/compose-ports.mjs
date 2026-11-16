// compose の全ての ports が、待ち受けるアドレスを ${LOCAL_BIND_ADDRESS:-127.0.0.1}: で始めることを確認する。
// 既定ではこのマシンだけ（127.0.0.1）で待ち受けるようにするため（開発環境の起動の「起動」）。
//
// ホスト側のポートを読めない項目（既定値のない変数、範囲、文字列でない項目）と、ポートが 0 件のときも失敗にする。
// local-ports が、読めたポートを一覧と照合するため。
//
// 失敗したとき：ポートの前に ${LOCAL_BIND_ADDRESS:-127.0.0.1}: を書き、ホスト側のポートは数字か ${変数:-数字} で 1 つずつ書く。
// 除外：なし
// 使い方：node scripts/ci/compose-ports.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { load } from 'js-yaml'

/** compose のファイル */
const COMPOSE_FILE = 'docker/compose.yaml'

/** 待ち受けるアドレスの書き方（変数と、その既定値） */
export const BIND_PREFIX = '${LOCAL_BIND_ADDRESS:-127.0.0.1}:'

/**
 * `ports` の項目から、ホスト側のポートを読む。<br />
 * 待ち受けるアドレスの後ろ（アドレスのない項目は先頭）が、数字か `${変数:-数字}` で、その後に `:` とコンテナの側が続くものだけを読む。
 *
 * @param {string} entry 項目
 * @returns {number | null} ホスト側のポート。それ以外の書き方（既定値のない変数、範囲、コンテナの側だけ）は null
 */
export function readHostPort(entry) {
  const rest = entry.startsWith(BIND_PREFIX) ? entry.slice(BIND_PREFIX.length) : entry
  const match = /^(?:\$\{[A-Z_]+:-(\d+)\}|(\d+)):[^:]+$/.exec(rest)
  return match ? Number(match[1] ?? match[2]) : null
}

/**
 * compose の各サービスに書いた `ports` の項目を読む。<br />
 * 文字列でない項目（長い書き方の対応表など）は、`entry` を JSON の文字列にする。待ち受けるアドレスで始まらないので、問題の項目になる。
 *
 * @param {string} text compose のファイル
 * @returns {{ service: string, entry: string }[]} サービスの名前と項目（書いた順）
 */
export function readComposePorts(text) {
  const services = load(text)?.services ?? {}
  return Object.entries(services).flatMap(([service, definition]) =>
    (definition?.ports ?? []).map((entry) => ({ service, entry: typeof entry === 'string' ? entry : JSON.stringify(entry) })),
  )
}

/**
 * `ports` の項目のうち、待ち受けるアドレスを決まった変数で書いていないものと、ホスト側のポートを読めないものを探す。
 *
 * @param {string} text compose のファイル
 * @returns {{ ports: { service: string, entry: string }[], problems: { service: string, entry: string }[], unreadable: { service: string, entry: string }[] }}
 *   全ての項目と、決まった形でない項目と、決まった形だがホスト側のポートを読めない項目
 */
export function findPortProblems(text) {
  const ports = readComposePorts(text)
  const problems = ports.filter((port) => !port.entry.startsWith(BIND_PREFIX))
  const unreadable = ports.filter((port) => port.entry.startsWith(BIND_PREFIX) && readHostPort(port.entry) === null)
  return { ports, problems, unreadable }
}

/**
 * ホスト側のポートを読めない項目を、失敗の文にする（local-ports も使う）。
 *
 * @param {string} file 表示する compose のパス
 * @param {{ service: string, entry: string }} port 読めなかった項目
 * @returns {string} `ファイル サービス：項目（理由）`
 */
export function describeUnreadableHostPort(file, port) {
  return `${file} ${port.service}：${port.entry}（ホスト側のポートを読めない。数字か \${変数:-数字} で 1 つのポートを書く）`
}

if (import.meta.main) {
  const { ports, problems, unreadable } = findPortProblems(readFileSync(COMPOSE_FILE, 'utf8'))
  for (const problem of problems) {
    console.log(`${COMPOSE_FILE} ${problem.service}：${problem.entry}（${BIND_PREFIX} で始める）`)
  }
  for (const port of unreadable) {
    console.log(describeUnreadableHostPort(COMPOSE_FILE, port))
  }
  console.log(`compose のポート：${ports.length} 件、決まった形でないもの：${problems.length} 件、ホスト側のポートを読めないもの：${unreadable.length} 件`)
  process.exit(problems.length || unreadable.length || ports.length === 0 ? 1 : 0)
}
