// Git の対象のファイル（追跡しているファイルと、追跡していない無視されないファイル）の一覧を返す。
// 作業ツリーの検査は、commit の前の変更も見るため、追跡していないファイルも含める。
// ファイルを探す検査（doc-refs、doc-terms、frontend-rules、java-rules、message-comments、spec-numbers、test-names、time-waits など）が使う
import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'

/**
 * Git の対象のファイルの一覧を返す。作業ツリーから消えたファイルは含めない。
 *
 * @param {string[]} [pathspecs] 探すパス（ディレクトリなど）。なければリポジトリの全体
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）。名前の順
 */
export function listGitFiles(pathspecs = [], cwd = process.cwd()) {
  const output = execFileSync('git', ['ls-files', '-co', '--exclude-standard', '--', ...pathspecs], { cwd, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  return [...new Set(output.split('\n'))].filter((file) => file && existsSync(path.join(cwd, file))).sort()
}
