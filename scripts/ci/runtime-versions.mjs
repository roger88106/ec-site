// 実行環境（JDK と Node）の版が、複数の置き場所で一致していることを確認する。
// JDK：`.github/workflows/ci.yml` の `env.JAVA_VERSION` と `backend/ecsite/gradle/libs.versions.toml` の `java` が同じ値。
// Node：`.node-version` の値が、ルートの `package.json` の `engines.node`（`>=N` の形）の範囲を満たす。
// workflow と action の yml は、版を直接書かず、この置き場所を参照する（新しい job の直書きを見つける）。
// 1 か所から読めない版は、置き場所の間の一致を検査で確かめる。ずれると、手元と CI で別の版を動かす。
// 使い方：node scripts/ci/runtime-versions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/**
 * workflow の `JAVA_VERSION` の値を読む。
 *
 * @param {string} text ci.yml の内容
 * @returns {string | undefined} 版。なければ undefined
 */
export function readJavaFromWorkflow(text) {
  return /^\s*JAVA_VERSION:\s*["']?([0-9][0-9.]*)["']?/m.exec(text)?.[1]
}

/**
 * `libs.versions.toml` の `java` の値を読む。
 *
 * @param {string} text libs.versions.toml の内容
 * @returns {string | undefined} 版。なければ undefined
 */
export function readJavaFromToml(text) {
  return /^java\s*=\s*"([^"]+)"/m.exec(text)?.[1]
}

/**
 * `.node-version` の最初の行の版を読む（先頭の `v` は外す）。
 *
 * @param {string} text .node-version の内容
 * @returns {string | undefined} 版。空なら undefined
 */
export function readNodeVersionFile(text) {
  const line = text.split('\n')[0].trim().replace(/^v/, '')
  return line === '' ? undefined : line
}

/**
 * `package.json` の `engines.node` を読む。
 *
 * @param {string} text package.json の内容
 * @returns {string | undefined} 範囲の文字列。なければ undefined
 */
export function readEnginesNode(text) {
  return JSON.parse(text).engines?.node
}

/**
 * 版の置き場所の間の食い違いを探す。
 *
 * @param {{ workflowJava?: string, tomlJava?: string, nodeFile?: string, enginesNode?: string }} versions 読んだ版
 * @returns {string[]} 問題の説明（なければ空）
 */
export function findRuntimeVersionProblems({ workflowJava, tomlJava, nodeFile, enginesNode }) {
  const problems = []
  if (workflowJava === undefined) {
    problems.push('ci.yml の env.JAVA_VERSION を読めない')
  }
  if (tomlJava === undefined) {
    problems.push('libs.versions.toml の java を読めない')
  }
  if (workflowJava !== undefined && tomlJava !== undefined && workflowJava !== tomlJava) {
    problems.push(`JDK の版が違う：ci.yml の JAVA_VERSION は ${workflowJava}、libs.versions.toml の java は ${tomlJava}`)
  }
  if (nodeFile === undefined) {
    problems.push('.node-version の版を読めない')
  }
  if (enginesNode === undefined) {
    problems.push('package.json の engines.node を読めない')
  }
  if (nodeFile !== undefined && !/^\d+(\.\d+){0,2}$/.test(nodeFile)) {
    problems.push(`.node-version は数の版（24 など）だけを扱う：${nodeFile}`)
  } else if (nodeFile !== undefined && enginesNode !== undefined) {
    const minimum = /^>=\s*(\d+)(\.\d+){0,2}$/.exec(enginesNode.trim())?.[1]
    if (minimum === undefined) {
      problems.push(`engines.node は >=N の形だけを扱う：${enginesNode}`)
    } else if (Number.parseInt(nodeFile, 10) < Number(minimum)) {
      problems.push(`Node の版が違う：.node-version は ${nodeFile}、engines.node は ${enginesNode}`)
    }
  }
  return problems
}

/** 版を指定する setup 系の入力の名前と、許す値（決めた 1 か所を参照する形）。undefined は、その入力を使わない。これ以外の値（直書き、別のファイル、行列）は失敗にする */
const ALLOWED_VERSION_INPUTS = {
  'java-version': '${{ env.JAVA_VERSION }}',
  'node-version-file': '.node-version',
  'node-version': undefined,
  'java-version-file': undefined,
}

/**
 * workflow と action の yml の、版の指定が決めた 1 か所を参照しているかを調べる。
 * 値は、前後の引用符と行末のコメントを外して比べる。`node-version` と `java-version-file` は許す値がないので、常に失敗にする。
 *
 * @param {{ path: string, text: string }[]} files yml のパスと内容
 * @returns {string[]} 問題の説明（`パス:行` つき。なければ空）
 */
export function findVersionLiteralProblems(files) {
  const problems = []
  const pattern = /^\s*(?:-\s+)?(java-version|node-version-file|node-version|java-version-file)\s*:\s*(.*)$/
  for (const { path, text } of files) {
    text.split('\n').forEach((line, index) => {
      const match = pattern.exec(line.replace(/\r$/, ''))
      if (match === null) return
      const [, name, rawValue] = match
      const value = rawValue.replace(/\s+#.*$/, '').trim().replace(/^(["'])(.*)\1$/, '$2')
      const expected = ALLOWED_VERSION_INPUTS[name]
      if (value !== expected) {
        const hint = expected === undefined ? 'この入力は使わない（Node は node-version-file: .node-version）' : `${expected} と書く`
        problems.push(`${path}:${index + 1}: ${name} に ${value} を直接書いている。${hint}`)
      }
    })
  }
  return problems
}

if (isMain(import.meta.url)) {
  const read = (path) => readFileSync(path, 'utf8')
  const ymls = listGitFiles(['.github/workflows', '.github/actions']).filter((file) => /\.ya?ml$/.test(file))
  const problems = findRuntimeVersionProblems({
    workflowJava: readJavaFromWorkflow(read('.github/workflows/ci.yml')),
    tomlJava: readJavaFromToml(read('backend/ecsite/gradle/libs.versions.toml')),
    nodeFile: readNodeVersionFile(read('.node-version')),
    enginesNode: readEnginesNode(read('package.json')),
  })
  if (ymls.length === 0) {
    problems.push('workflow と action の yml が 1 件も見つからない（置き場所が変わっていないか確認する）')
  }
  problems.push(...findVersionLiteralProblems(ymls.map((file) => ({ path: file, text: read(file) }))))
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`実行環境の版：問題 ${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
