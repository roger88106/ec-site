// コードをわざと壊してテストが失敗するかを確認する（「壊しての確認」）。
// sed は合わない式でも黙って何もしないので、壊したつもりで「通った」と読み違える。
// 置き換えは正規表現でなく文字列で行い、当たらなければ止める。テストの後は必ず元に戻す（Ctrl+C などの停止のシグナルを受けたときも）。
// コマンドは Git Bash で実行する。Windows の cmd.exe では `./gradlew` のような `./` で始まるコマンドを起動できない。
//
// 使い方：node scripts/dev/break-check.mjs <ファイル> <置き換える文字列> <置き換え後> <ディレクトリ> <テストのコマンド>
//   例：node scripts/dev/break-check.mjs frontend/store/src/x.tsx "{!ok && <p" "{false && <p" frontend/store "npx vitest run src/x.test.tsx"
// 出力：失敗したテストの名前（×、FAILED）と件数の行だけ。終了コードは、テストが失敗すれば 0（壊すと失敗する＝壊すと検出できる）、
// 通れば 1、置き換えが 1 か所に当たらない・ファイルが読めない・bash を起動できない・コマンドが見つからない（127）か実行できない（126）ときは 2。
// 停止のシグナルを受けたときは、元に戻してから 130（SIGINT）か 143（SIGTERM）で終わる。
// 127 と 126 は、コマンドの誤りを「壊すと検出できる」と読み違えないよう、テストの失敗と区別する。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { gitBash } from './lib/git-bash.mjs'

const [file, from, to, cwd, command] = process.argv.slice(2)
if (!command) {
  process.stderr.write('使い方：break-check.mjs <ファイル> <置き換える文字列> <置き換え後> <ディレクトリ> <テストのコマンド>\n')
  process.exit(2)
}
let original
try {
  original = readFileSync(file, 'utf8')
} catch (error) {
  process.stderr.write(`対象のファイルが読めない：${file}（${error.message}）\n`)
  process.exit(2)
}
const count = original.split(from).length - 1
if (count !== 1) {
  process.stderr.write(`置き換える文字列が ${count} か所に当たった（1 か所だけにする）：${from}\n`)
  process.exit(2)
}

/** bash が「コマンドが使えない」ときに返す終了コードと、その理由 */
const COMMAND_PROBLEMS = new Map([
  [126, 'テストのコマンドを実行できない'],
  [127, 'テストのコマンドが見つからない'],
])

let child = null
let restored = false

/** 壊す前の内容に戻す。何度呼んでも 1 回だけ書く */
function restore() {
  if (restored) return
  restored = true
  writeFileSync(file, original)
}

// 終了のしかたによらず元に戻す。process.exit も、シグナルの処理も、この 'exit' を通る。
// テストの実行中も、非同期の spawn で event loop を空けておくので、シグナルを受け取れる
process.on('exit', restore)
for (const [signal, code] of [
  ['SIGINT', 130],
  ['SIGTERM', 143],
]) {
  process.on(signal, () => {
    child?.kill()
    process.exit(code)
  })
}

// 置き換え後の文字列の `$` を特別扱いしないよう、関数で置き換える
writeFileSync(file, original.replace(from, () => to))

let stdout = ''
let stderr = ''
let finished = false

/** テストのコマンドが終わった後の、元に戻す・報告・終了 */
function finish(status, signal, startError) {
  if (finished) return
  finished = true
  restore()

  if (startError || status === null) {
    process.stderr.write(`bash を起動できない：${startError ?? `bash が終了コードなしで終わった（シグナル：${signal}）`}\n`)
    process.exit(2)
  }
  if (COMMAND_PROBLEMS.has(status)) {
    process.stderr.write(`${COMMAND_PROBLEMS.get(status)}（終了コード ${status}）。テストの失敗ではないので、コマンドを直す：${command}\n`)
    process.exit(2)
  }

  const failed = status !== 0
  const summary = `${stdout}${stderr}`
    .replace(/\x1b\[[0-9;]*m/g, '')
    .split('\n')
    .filter((line) => /^\s+×|FAILED|Tests\s+\d|tests completed|BUILD/.test(line))
  process.stdout.write(`${summary.join('\n')}\n${failed ? '→ 失敗した（壊すと検出できる）' : '→ 通った（壊しても検出できない）'}\n`)
  process.exit(failed ? 0 : 1)
}

child = spawn(gitBash(), ['-c', command], { cwd, stdio: ['ignore', 'pipe', 'pipe'] })
child.stdout.setEncoding('utf8')
child.stderr.setEncoding('utf8')
child.stdout.on('data', (chunk) => {
  stdout += chunk
})
child.stderr.on('data', (chunk) => {
  stderr += chunk
})
child.on('error', (error) => finish(null, null, String(error)))
child.on('close', (status, signal) => finish(status, signal))
