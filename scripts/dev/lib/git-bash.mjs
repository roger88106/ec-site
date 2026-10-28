// テストが、git の hook や Claude Code の hook のコマンドを実行するときの bash を求める補助。
// Windows の素の PATH では、名前の bash が WSL の bash.exe を指すことがあり、Git Bash で動くことを確認するテストにならない。
// bash を求める処理はここだけに置き、hook を実行するテストはこれを使う
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { withoutGitEnv } from '../../ci/lib/git-env.mjs'

/**
 * Git の bash（git が hook を実行するときと同じ bash）の絶対パスを返す。
 * Windows では git の exec-path から Git for Windows の `bin/bash.exe` を求め、それ以外では PATH の bash の絶対パスを返す。
 * PATH を差し替えても同じ bash を使えるよう、絶対パスで返す。
 *
 * @param {Record<string, string | undefined>} [env] git を呼ぶ環境。なければ GIT_ で始まる変数を外した `process.env`
 * @returns {string} bash のパス（見つからなければ `bash`）
 */
export function gitBash(env = withoutGitEnv()) {
  if (process.platform !== 'win32') return spawnSync('bash', ['-c', 'command -v bash'], { env, encoding: 'utf8' }).stdout.trim() || 'bash'
  const execPath = spawnSync('git', ['--exec-path'], { env, encoding: 'utf8' }).stdout.trim()
  const candidate = path.resolve(execPath, '../../../bin/bash.exe')
  return existsSync(candidate) ? candidate : 'bash'
}
