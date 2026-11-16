// workflow と action の yml が、実行環境（JDK と Node）のバージョンを直接書かず、バージョンのファイルを参照していることを確認する。
// バージョンは、JDK が .java-version（Gradle の toolchain も読む）、Node が .node-version の 1 か所にある。
// yml に直接書くと、手元と CI、または job ごとに別のバージョンを動かすため。
// setup の入力は、java-version-file: .java-version と node-version-file: .node-version だけを許す。java-version と node-version は失敗にする。
//
// .node-version と engines.node（ルートの package.json）のずれは、pnpm の engineStrict がインストールで失敗にする。
//
// 失敗したとき：yml の setup の入力を、バージョンのファイルを参照する形にする。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/runtime-versions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { load } from 'js-yaml'
import { exitIfNoFiles, listGitFiles } from '../lib/git/git-files.mjs'

/** バージョンを指定する setup 系の入力の名前と、許す値（バージョンのファイル）。undefined は、その入力を使わない。これ以外の値（直書き、別のファイル、行列）は失敗にする */
const ALLOWED_VERSION_INPUTS = {
  'java-version-file': '.java-version',
  'node-version-file': '.node-version',
  'java-version': undefined,
  'node-version': undefined,
}

/**
 * YAML を読んだ値の全ての対応表のキーを、場所（`jobs.a.steps[0].with` の形）とともにたどる。
 *
 * @param {unknown} node 読んだ値
 * @param {(key: string, value: unknown, where: string) => void} visit キーごとに呼ぶ処理。where はキーを持つ対応表の場所（最上位は空）
 * @param {string} [where] node の場所
 */
function walkKeys(node, visit, where = '') {
  if (Array.isArray(node)) {
    node.forEach((item, index) => walkKeys(item, visit, `${where}[${index}]`))
  } else if (node !== null && typeof node === 'object') {
    for (const [key, value] of Object.entries(node)) {
      visit(key, value, where)
      walkKeys(value, visit, where === '' ? key : `${where}.${key}`)
    }
  }
}

/**
 * workflow と action の yml の、バージョンの指定がバージョンのファイルを参照しているかを調べる。
 *
 * `java-version` と `node-version` は許す値がないので、常に失敗にする。
 *
 * @param {{ path: string, text: string }[]} files yml のパスと内容
 * @returns {string[]} 問題の説明（ファイルと場所つき。なければ空）
 */
export function findVersionLiteralProblems(files) {
  const problems = []
  for (const { path, text } of files) {
    walkKeys(load(text), (key, value, where) => {
      if (!Object.hasOwn(ALLOWED_VERSION_INPUTS, key)) return
      const expected = ALLOWED_VERSION_INPUTS[key]
      const written = typeof value === 'string' ? value : JSON.stringify(value)
      if (written !== expected) {
        const hint = expected === undefined ? 'この入力は使わない（java-version-file: .java-version、node-version-file: .node-version と書く）' : `${expected} と書く`
        problems.push(`${path} の ${where}：${key} に ${written} を直接書いている。${hint}`)
      }
    })
  }
  return problems
}

if (import.meta.main) {
  const ymls = listGitFiles(['.github/workflows', '.github/actions']).filter((file) => /\.ya?ml$/.test(file))
  exitIfNoFiles(ymls, 'workflow と action の yml のファイル')
  const problems = findVersionLiteralProblems(ymls.map((file) => ({ path: file, text: readFileSync(file, 'utf8') })))
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`実行環境のバージョン：問題 ${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
