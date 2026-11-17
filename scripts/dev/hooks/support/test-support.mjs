// pre-commit と commit-msg の、実際の hook を動かすテストが共有する補助。テストのファイルではない
import { copyFileSync, mkdirSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** リポジトリ（作業ツリー）の根 */
export const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..')

/**
 * 日本語の文の検査と、その検査が読む補助（scripts/ci/lib と scripts/shared のテスト以外の .mjs）。
 *
 * 一時のリポジトリに、実際のリポジトリと同じ置き場所でコピーする。
 */
export const JA_TEXT_FILES = [
  'scripts/ci/ja/ja-text.mjs',
  ...['scripts/ci/lib', 'scripts/shared'].flatMap((dir) =>
    readdirSync(path.join(REPO_ROOT, dir), { recursive: true })
      .map((name) => `${dir}/${String(name).split(path.sep).join('/')}`)
      .filter((file) => file.endsWith('.mjs') && !file.endsWith('.test.mjs')),
  ),
]

/** commit-msg の hook と、その hook が呼ぶ script */
export const COMMIT_MSG_FILES = ['scripts/dev/hooks/commit-msg', 'scripts/dev/hooks/commit-msg.mjs', 'scripts/dev/hooks/commit-msg-main.mjs']

/**
 * 実際の hook のテストが使う、日本語の文の検査の一覧。
 *
 * 本物の一覧は、許容用語が使われていないと失敗にする（小さな一時のリポジトリでは使われない）ので、試す語だけの一覧にする。
 */
export const MINI_LIST = JSON.stringify({ error: [{ word: '道具', use: 'ツール' }], warning: [{ word: '要求', use: 'リクエスト' }], style: { no: 4, length: 100, comma: 5, cell: 80 } })

/**
 * 実際のリポジトリのファイルを、一時のディレクトリの同じ置き場所にコピーする。
 *
 * @param {string} dir 一時のディレクトリ
 * @param {string[]} files リポジトリのルートからの相対パス
 */
export function copyFromRepo(dir, files) {
  for (const file of files) {
    mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
    copyFileSync(path.join(REPO_ROOT, file), path.join(dir, file))
  }
}
