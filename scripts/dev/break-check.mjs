// コードをわざと壊してテストが失敗するかを確認する（「壊しての確認」）。
// sed は合わない式でも黙って何もしないので、壊したつもりで「通った」と読み違える。
// 置き換えは正規表現でなく文字列で行い、当たらなければ止める。テストの後は必ず元に戻す。
// コマンドは Git Bash で実行する。Windows の cmd.exe では `./gradlew` のような `./` で始まるコマンドを起動できない。
//
// 使い方：node scripts/dev/break-check.mjs <ファイル> <置き換える文字列> <置き換え後> <ディレクトリ> <テストのコマンド>
//   例：node scripts/dev/break-check.mjs frontend/store/src/x.tsx "{!ok && <p" "{false && <p" frontend/store "npx vitest run src/x.test.tsx"
// 出力：失敗したテストの名前（×、FAILED）と件数の行だけ。終了コードは、テストが失敗すれば 0（壊すと失敗する＝確認が効く）、
// 通れば 1、置き換えが 1 か所に当たらなければ 2。
import { spawnSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { gitBash } from './lib/git-bash.mjs'

const [file, from, to, cwd, command] = process.argv.slice(2)
if (!command) {
  process.stderr.write('使い方：break-check.mjs <ファイル> <置き換える文字列> <置き換え後> <ディレクトリ> <テストのコマンド>\n')
  process.exit(2)
}
const original = readFileSync(file, 'utf8')
const count = original.split(from).length - 1
if (count !== 1) {
  process.stderr.write(`置き換える文字列が ${count} か所に当たった（1 か所だけにする）：${from}\n`)
  process.exit(2)
}

let output = ''
let failed = false
// 置き換え後の文字列の `$` を特別扱いしないよう、関数で置き換える
writeFileSync(file, original.replace(from, () => to))
try {
  const result = spawnSync(gitBash(), ['-c', command], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 })
  failed = result.status !== 0
  output = `${result.stdout ?? ''}${result.stderr ?? ''}${result.error ? String(result.error) : ''}`
} finally {
  writeFileSync(file, original)
}

const summary = output
  .replace(/\x1b\[[0-9;]*m/g, '')
  .split('\n')
  .filter((line) => /^\s+×|FAILED|Tests\s+\d|tests completed|BUILD/.test(line))
process.stdout.write(`${summary.join('\n')}\n${failed ? '→ 失敗した（壊すと気づく）' : '→ 通った（壊しても気づかない）'}\n`)
process.exit(failed ? 0 : 1)
