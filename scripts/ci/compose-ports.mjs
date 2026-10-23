// compose のポートが、既定でこのマシンだけ（127.0.0.1）で待ち受けることを確認する（開発環境の起動 第 2 節）。
// 他のマシンからつなぐときは、すべてのポートを 1 つの変数（LOCAL_BIND_ADDRESS）で替える。
// `ports:` の節の中に、知っている書き方（`ports:` の行と、字下げのそろった `- ` の項目）に当たらない行があるとき、
// ホスト側のポートを読めない項目（既定値のない変数、範囲）があるとき、ポートが 0 件のときも失敗にする。
// 使い方：node scripts/ci/compose-ports.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'

/** compose のファイル */
const COMPOSE_FILE = 'docker/compose.yaml'

/** 待ち受けるアドレスの書き方（変数と、その既定値） */
export const BIND_PREFIX = '${LOCAL_BIND_ADDRESS:-127.0.0.1}:'

/**
 * `ports` の項目から、ホスト側のポートを読む。待ち受けるアドレス（`${LOCAL_BIND_ADDRESS:-127.0.0.1}:`）の後ろ
 * （アドレスのない項目は先頭）が、数字か `${変数:-数字}` で、その後に `:` とコンテナの側が続くものだけを読む。
 *
 * @param {string} entry 項目（引用符を外したもの）
 * @returns {number | null} ホスト側のポート。それ以外の書き方（既定値のない変数、範囲、コンテナの側だけ）は null
 */
export function readHostPort(entry) {
  const rest = entry.startsWith(BIND_PREFIX) ? entry.slice(BIND_PREFIX.length) : entry
  const match = /^(?:\$\{[A-Z_]+:-(\d+)\}|(\d+)):[^:]+$/.exec(rest)
  return match ? Number(match[1] ?? match[2]) : null
}

/**
 * `ports` の項目のうち、待ち受けるアドレスを決まった変数で書いていないものと、ホスト側のポートを読めないものを探す。<br />
 * YAML のライブラリを使わず、`ports:` の下の `- ` で始まる行を読む（compose のファイルはこの形でだけ書く）。<br />
 * 読む節（`ports:` の行と、その下の項目）の中で、知っている書き方に当たらない行（フロー形式の `ports: [...]`、
 * 項目の字下げがそろわない行、項目でない行）は、読み飛ばさずに `unknown` に入れる。空行とコメントは除く。
 * 決まった変数で始まる項目のうち、ホスト側のポートを読めないもの（既定値のない変数、範囲）は `unreadable` に入れる
 * （local-ports がポートの番号を照合できず、一覧にないポートを見逃すため）。
 *
 * @param {string} text compose のファイル
 * @returns {{ ports: { line: number, entry: string }[], problems: { line: number, entry: string }[],
 *   unreadable: { line: number, entry: string }[], unknown: { line: number, text: string }[] }}
 *   すべての項目と、決まった形でない項目と、ホスト側のポートを読めない項目と、知らない書き方の行（どれも行の順）
 */
export function findPortProblems(text) {
  const ports = []
  const unknown = []
  let indent = null
  let itemIndent = null
  text.split('\n').forEach((raw, index) => {
    const line = raw.replace(/\r$/, '')
    const trimmed = line.trim()
    if (trimmed === '' || trimmed.startsWith('#')) {
      return
    }
    const lineIndent = /^\s*/.exec(line)[0].length
    if (/^\s*ports:/.test(line)) {
      if (/^\s*ports:\s*(#.*)?$/.test(line)) {
        indent = lineIndent
        itemIndent = null
      } else {
        indent = null
        unknown.push({ line: index + 1, text: line })
      }
      return
    }
    if (indent === null) {
      return
    }
    const item = /^(\s*)-\s+(.*)$/.exec(line)
    if (item && item[1].length >= indent && (itemIndent === null || item[1].length === itemIndent)) {
      itemIndent = item[1].length
      ports.push({ line: index + 1, entry: item[2].trim().replace(/^(["'])(.*)\1$/, '$2') })
    } else if (lineIndent > indent) {
      unknown.push({ line: index + 1, text: line })
    } else {
      indent = null
    }
  })
  const problems = ports.filter((port) => !port.entry.startsWith(BIND_PREFIX))
  const unreadable = ports.filter((port) => port.entry.startsWith(BIND_PREFIX) && readHostPort(port.entry) === null)
  return { ports, problems, unreadable, unknown }
}

if (isMain(import.meta.url)) {
  const { ports, problems, unreadable, unknown } = findPortProblems(readFileSync(COMPOSE_FILE, 'utf8'))
  for (const problem of problems) {
    console.log(`${COMPOSE_FILE}:${problem.line} ${problem.entry}（${BIND_PREFIX} で始める）`)
  }
  for (const port of unreadable) {
    console.log(`${COMPOSE_FILE}:${port.line} ${port.entry}（ホスト側のポートを読めない。数字か \${変数:-数字} で 1 つのポートを書く）`)
  }
  for (const entry of unknown) {
    console.log(`${COMPOSE_FILE}:${entry.line} ${entry.text.trim()}（知らない書き方。検査の読み方を追加するか、項目を 1 行ずつ書く）`)
  }
  console.log(
    `compose のポート：${ports.length} 件、決まった形でないもの：${problems.length} 件、ホスト側のポートを読めないもの：${unreadable.length} 件、知らない書き方の行：${unknown.length} 件`,
  )
  process.exit(problems.length || unreadable.length || unknown.length || ports.length === 0 ? 1 : 0)
}
