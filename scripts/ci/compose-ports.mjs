// compose のポートが、既定でこのマシンだけ（127.0.0.1）で待ち受けることを確認する（開発環境の起動 第 2 節）。
// 他のマシンからつなぐときは、すべてのポートを 1 つの変数（LOCAL_BIND_ADDRESS）で替える。
// 使い方：node scripts/ci/compose-ports.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'

/** compose のファイル */
const COMPOSE_FILE = 'docker/compose.yaml'

/** 待ち受けるアドレスの書き方（変数と、その既定値） */
export const BIND_PREFIX = '${LOCAL_BIND_ADDRESS:-127.0.0.1}:'

/**
 * `ports` の項目のうち、待ち受けるアドレスを決まった変数で書いていないものを探す。<br />
 * YAML のライブラリを使わず、`ports:` の下の `- ` で始まる行を読む（compose のファイルはこの形でだけ書く）。
 *
 * @param {string} text compose のファイル
 * @returns {{ ports: { line: number, entry: string }[], problems: { line: number, entry: string }[] }}
 *   すべての項目と、決まった形でない項目（行の順）
 */
export function findPortProblems(text) {
  const ports = []
  let indent = null
  text.split('\n').forEach((raw, index) => {
    const line = raw.replace(/\r$/, '')
    const portsKey = /^(\s*)ports:\s*$/.exec(line)
    if (portsKey) {
      indent = portsKey[1].length
      return
    }
    if (indent === null || line.trim() === '' || line.trim().startsWith('#')) {
      return
    }
    const item = /^(\s*)-\s+(.*)$/.exec(line)
    if (item && item[1].length >= indent) {
      ports.push({ line: index + 1, entry: item[2].trim().replace(/^(["'])(.*)\1$/, '$2') })
    } else {
      indent = null
    }
  })
  return { ports, problems: ports.filter((port) => !port.entry.startsWith(BIND_PREFIX)) }
}

if (isMain(import.meta.url)) {
  const { ports, problems } = findPortProblems(readFileSync(COMPOSE_FILE, 'utf8'))
  for (const problem of problems) {
    console.log(`${COMPOSE_FILE}:${problem.line} ${problem.entry}（${BIND_PREFIX} で始める）`)
  }
  console.log(`compose のポート：${ports.length} 件、決まった形でないもの：${problems.length} 件`)
  process.exit(problems.length || ports.length === 0 ? 1 : 0)
}
