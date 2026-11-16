// 管理画面の IdP の既定値が、全ての置き場所で一致することを確認する（環境変数の文書の管理画面の行）。
// 対象の変数は VITE_OIDC_AUTHORITY と VITE_OIDC_CLIENT_ID。正は管理画面の認証の設定（frontend/admin/src/auth/oidc.ts）で、置き場所は ADMIN_IDP_PLACES にある。
// 値を 1 つのファイルから読まないのは、本番のビルドが、ローカルの値で成功しないようにするため。
// どれかの値が違う、または読めないときは失敗にする。
//
// バックエンドの設定の既定値と文書の照合は、コンパイルの出力を読むので、別の script（backend/config-defaults.mjs）にある。
//
// 失敗したとき：食い違った置き場所の値を認証の設定にそろえる（認証の設定の値を変えるときは、全ての置き場所を同時に修正する）。
// 除外：なし
// 使い方：node scripts/ci/env-defaults.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'

/** 管理画面の IdP の環境変数（認可サーバーの URL とクライアント ID） */
const ADMIN_IDP_VARS = ['VITE_OIDC_AUTHORITY', 'VITE_OIDC_CLIENT_ID']

/**
 * コメントの行（`//`、`#`、`*`、`/*` で始まる行）を除く。
 *
 * @param {string} text ソースか script
 * @returns {string} コメントの行を空にした文
 */
function dropCommentLines(text) {
  return text
    .split('\n')
    .map((line) => (/^\s*(?:\/\/|#|\*|\/\*)/.test(line) ? '' : line))
    .join('\n')
}

/**
 * 管理画面の IdP の既定値を書いた置き場所。最初の 1 つ（管理画面の認証の設定）を正とし、残りは正と同じ値でなければならない。
 *
 * 管理画面の本番のビルドは IdP の値を明示しないと失敗する。そのため、値を 1 つのファイルから読まずに、置き場所ごとに書いて一致を確認する。
 * 各置き場所の read は、名前の既定値（書き方に当たる全て）を返す。
 *
 * @type {{ file: string, read: (text: string, name: string) => string[] }[]}
 */
const ADMIN_IDP_PLACES = [
  {
    file: 'frontend/admin/src/auth/oidc.ts',
    read: (text, name) => [...dropCommentLines(text).matchAll(new RegExp(`import\\.meta\\.env\\.${name}\\s*\\?\\?\\s*(['"])(.*?)\\1`, 'g'))].map((match) => match[2]),
  },
  {
    file: 'knip.ts',
    read: (text, name) => [...dropCommentLines(text).matchAll(new RegExp(`process\\.env\\.${name}\\s*\\?\\?=\\s*(['"])(.*?)\\1`, 'g'))].map((match) => match[2]),
  },
  {
    file: 'scripts/dev/check-frontend.sh',
    read: (text, name) => [...dropCommentLines(text).matchAll(new RegExp(`\\$\\{${name}:-([^}]*)\\}`, 'g'))].map((match) => match[1]),
  },
  {
    file: '.github/workflows/ci.yml',
    read: (text, name) =>
      [...dropCommentLines(text).matchAll(new RegExp(`^\\s*${name}:\\s*(\\S+)\\s*$`, 'gm'))].map((match) => match[1].replace(/^(['"])(.*)\1$/, '$2')),
  },
  {
    file: 'docs/06-手順書/environment.md',
    read: (text, name) =>
      text
        .split(/\r?\n/)
        .map((line) => line.split('|').slice(1, -1).map((cell) => cell.trim()))
        .filter((cells) => cells.length >= 3 && cells[0] === `\`${name}\``)
        .map((cells) => cells.at(-1).replace(/^`(.*)`$/, '$1')),
  },
]

/** 管理画面の IdP の既定値を読む置き場所のファイル（リポジトリのルートからのパス） */
export const ADMIN_IDP_FILES = ADMIN_IDP_PLACES.map((place) => place.file)

/**
 * 管理画面の IdP の既定値が、置き場所の間で一致するかを調べる。管理画面の認証の設定（`oidc.ts`）を正とする。
 *
 * @param {Record<string, string>} texts 置き場所のファイル（ADMIN_IDP_FILES）の中身
 * @returns {string[]} 食い違いと、読めなかった置き場所の説明（環境変数の順、置き場所の順）
 */
export function compareAdminIdp(texts) {
  const [canonicalPlace, ...others] = ADMIN_IDP_PLACES
  const problems = []
  for (const name of ADMIN_IDP_VARS) {
    const [canonical] = canonicalPlace.read(texts[canonicalPlace.file] ?? '', name)
    if (canonical === undefined) {
      problems.push(`${name}：${canonicalPlace.file} の既定値を読めない`)
      continue
    }
    for (const place of others) {
      const values = place.read(texts[place.file] ?? '', name)
      if (values.length === 0) problems.push(`${name}：${place.file} の既定値を読めない`)
      for (const value of values) {
        if (value !== canonical) {
          problems.push(`${name}：${place.file} の既定「${value}」が、${canonicalPlace.file} の既定「${canonical}」と違う`)
        }
      }
    }
  }
  return problems
}

if (isMain(import.meta.url)) {
  const texts = Object.fromEntries(ADMIN_IDP_FILES.map((file) => [file, readFileSync(file, 'utf8')]))
  const problems = compareAdminIdp(texts)
  for (const problem of problems) console.log(problem)
  console.log(`管理画面の IdP の既定値の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
