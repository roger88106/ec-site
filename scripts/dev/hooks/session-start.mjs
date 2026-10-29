// SessionStart の hook（.claude/settings.local.json）：セッションの始めと文脈の圧縮の後に、
// マシンと利用者に固有の環境（environment.md）を文脈に入れる。圧縮で返答の言語などの習慣が抜けるのを防ぐため。
// environment.md は Git に入らないので、追加の作業ツリーにはない。hook の入力の cwd から
// メインの作業ツリー（git の共通のディレクトリの親）を求め、そこの /.local/agent/environment.md を読む。
// 読めないときは何も出さずに終了コード 0 で終わる。
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { withoutGitEnv } from '../../ci/lib/git-env.mjs'

try {
  const cwd = JSON.parse(readFileSync(0, 'utf8'))?.cwd
  if (typeof cwd !== 'string' || cwd === '') process.exit(0)

  // GIT_DIR などが設定された環境（git の hook の中など）でも cwd のリポジトリを求めるため、GIT_ で始まる変数を外して実行する
  const git = spawnSync('git', ['rev-parse', '--git-common-dir'], { cwd, encoding: 'utf8', env: withoutGitEnv() })
  if (git.status !== 0) process.exit(0)

  // cwd がメインの作業ツリーの直下だと、相対パス（.git）が返る
  const commonDir = path.resolve(cwd, git.stdout.trim())
  const file = path.join(path.dirname(commonDir), '.local', 'agent', 'environment.md')
  const text = readFileSync(file, 'utf8')
  process.stdout.write(`以下は /.local/agent/environment.md（マシンと利用者に固有の環境）。従うこと。\n\n${text}`)
} catch {
  // 入力かファイルが読めなければ何も入れない
}
