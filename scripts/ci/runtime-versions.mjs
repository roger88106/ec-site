// 実行環境（JDK と Node）のバージョンが、複数の置き場所で一致していることを確認する。
// 1 か所から読めないバージョンがずれると、手元と CI で別のバージョンを動かすため。
//
// JDK は、ci.yml の env.JAVA_VERSION と libs.versions.toml の java が同じ値。JAVA_VERSION は ci.yml の最上位の env の 1 か所だけで定義する。
// Node は、.node-version の値が、ルートの package.json の engines.node（`>=N` の形だけを扱う）の範囲を満たす。
// workflow と action の yml は、バージョンを直接書かない。java-version は ${{ env.JAVA_VERSION }}、Node は node-version-file: .node-version と書く。
//
// 3 つの package.json（管理画面、店舗画面、共有）の dependencies と devDependencies の React の指定は、全て `^1.2.3` の範囲の書き方にそろえる。
// peerDependencies は使う側に許す範囲を書くので対象にしない。
//
// 失敗したとき：置き場所のバージョンをそろえる。Node のバージョンを上げるときは .node-version と engines.node を合わせる。
// 除外：なし
// 使い方：node scripts/ci/runtime-versions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { load } from 'js-yaml'
import { parse } from 'smol-toml'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** Node のバージョンを書いたファイル */
const NODE_VERSION_FILE = '.node-version'

/** ルートの package.json（engines.node） */
const PACKAGE_JSON_FILE = 'package.json'

/**
 * workflow の `JAVA_VERSION` の値を読む。
 *
 * @param {string} text ci.yml の内容
 * @returns {string | undefined} バージョン。なければ undefined
 */
export function readJavaFromWorkflow(text) {
  const value = load(text)?.env?.JAVA_VERSION
  return value === undefined ? undefined : String(value)
}

/**
 * `libs.versions.toml` の `java` の値を読む。
 *
 * @param {string} text libs.versions.toml の内容
 * @returns {string | undefined} バージョン。なければ undefined
 */
export function readJavaFromToml(text) {
  const value = parse(text).versions?.java
  return typeof value === 'string' ? value : undefined
}

/**
 * `.node-version` の最初の行のバージョンを読む（先頭の `v` は外す）。
 *
 * @param {string} text .node-version の内容
 * @returns {string | undefined} バージョン。空なら undefined
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
 * バージョンの置き場所の間の食い違いを探す。
 *
 * @param {{ workflowJava?: string, tomlJava?: string, nodeFile?: string, enginesNode?: string, enginesLine?: number }} versions
 *   読んだバージョン。enginesLine は package.json の engines.node の行（1 始まり。読めない書き方の文に添える。既定は 1）
 * @returns {string[]} 問題の説明（読めないバージョンは `ファイル:行 内容（理由）`。なければ空）
 */
export function findRuntimeVersionProblems({ workflowJava, tomlJava, nodeFile, enginesNode, enginesLine = 1 }) {
  const problems = []
  if (workflowJava === undefined) {
    problems.push('ci.yml の env.JAVA_VERSION を読めない')
  }
  if (tomlJava === undefined) {
    problems.push('libs.versions.toml の java を読めない')
  }
  if (workflowJava !== undefined && tomlJava !== undefined && workflowJava !== tomlJava) {
    problems.push(`JDK のバージョンが違う：ci.yml の JAVA_VERSION は ${workflowJava}、libs.versions.toml の java は ${tomlJava}`)
  }
  if (nodeFile === undefined) {
    problems.push('.node-version のバージョンを読めない')
  }
  if (enginesNode === undefined) {
    problems.push('package.json の engines.node を読めない')
  }
  if (nodeFile !== undefined && !/^\d+(\.\d+){0,2}$/.test(nodeFile)) {
    problems.push(describeUnreadableLine({ file: NODE_VERSION_FILE, line: 1, text: nodeFile, reason: '数字のバージョン（24 など）だけを扱う' }))
  } else if (nodeFile !== undefined && enginesNode !== undefined) {
    const minimum = /^>=\s*(\d+)(\.\d+){0,2}$/.exec(enginesNode.trim())?.[1]
    if (minimum === undefined) {
      problems.push(describeUnreadableLine({ file: PACKAGE_JSON_FILE, line: enginesLine, text: enginesNode, reason: '>=N の形だけを扱う' }))
    } else if (Number.parseInt(nodeFile, 10) < Number(minimum)) {
      problems.push(`Node のバージョンが違う：.node-version は ${nodeFile}、engines.node は ${enginesNode}`)
    }
  }
  return problems
}

/** バージョンを指定する setup 系の入力の名前と、許す値（決めた 1 か所を参照する形）。undefined は、その入力を使わない。これ以外の値（直書き、別のファイル、行列）は失敗にする */
const ALLOWED_VERSION_INPUTS = {
  'java-version': '${{ env.JAVA_VERSION }}',
  'node-version-file': '.node-version',
  'node-version': undefined,
  'java-version-file': undefined,
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
 * workflow と action の yml の、バージョンの指定が決めた 1 か所を参照しているかを調べる。<br />
 * `node-version` と `java-version-file` は許す値がないので、常に失敗にする。
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
        const hint = expected === undefined ? 'この入力は使わない（Node は node-version-file: .node-version）' : `${expected} と書く`
        problems.push(`${path} の ${where}：${key} に ${written} を直接書いている。${hint}`)
      }
    })
  }
  return problems
}

/** `JAVA_VERSION` を定義してよい、ただ 1 つのファイル（その最上位の `env`） */
const JAVA_VERSION_HOME = '.github/workflows/ci.yml'

/**
 * `JAVA_VERSION` の定義が、`ci.yml` の最上位の `env` の 1 か所だけにあるかを調べる。<br />
 * job や step の `env` や、別のファイルで定義すると、そこで別のバージョンを使えてしまうので失敗にする。
 *
 * @param {{ path: string, text: string }[]} files yml のパスと内容
 * @returns {string[]} 問題の説明（ファイルと場所つき。なければ空）
 */
export function findJavaVersionDefinitionProblems(files) {
  const problems = []
  for (const { path, text } of files) {
    walkKeys(load(text), (key, _value, where) => {
      if (key !== 'JAVA_VERSION' || (path === JAVA_VERSION_HOME && where === 'env')) return
      problems.push(`${path} の ${where}：JAVA_VERSION を定義している。${JAVA_VERSION_HOME} の最上位の env の 1 か所だけに書き、他は \${{ env.JAVA_VERSION }} で参照する`)
    })
  }
  return problems
}

/** React の package（バージョンの書き方をそろえる対象） */
const REACT_PACKAGES = ['react', 'react-dom', '@types/react', '@types/react-dom']

/** package.json の React の指定を読む場所の一覧（`frontend/` の下の画面と共有のパッケージ） */
const PACKAGE_JSON_PATHS = ['frontend/admin/package.json', 'frontend/store/package.json', 'frontend/shared/package.json']

/**
 * 複数の package.json の、React の指定（`dependencies` と `devDependencies` の `react`、`react-dom`、`@types/react`、`@types/react-dom`）が、
 * 全て `^1.2.3` の範囲の書き方か調べる。固定のバージョン（`19.3.1`）が混ざると、更新の仕方がパッケージごとに変わるため。<br />
 * `peerDependencies` は、使う側に許す範囲（`>=19` など）を書くものなので、対象にしない。
 *
 * @param {{ path: string, text: string }[]} files package.json のパスと内容
 * @returns {string[]} 問題の説明（なければ空）
 */
export function findReactSpecifierProblems(files) {
  const problems = []
  let found = 0
  for (const { path, text } of files) {
    const json = JSON.parse(text)
    for (const section of ['dependencies', 'devDependencies']) {
      for (const name of REACT_PACKAGES) {
        const specifier = json[section]?.[name]
        if (specifier === undefined) continue
        found++
        if (!/^\^\d+\.\d+\.\d+$/.test(specifier)) {
          problems.push(`${path}: ${section} の ${name} の指定が ^ の範囲でない：${specifier}（^ を付ける）`)
        }
      }
    }
  }
  if (found === 0) {
    problems.push('package.json に React の指定が 1 件も見つからない（読む場所が変わっていないか確認する）')
  }
  return problems
}

if (import.meta.main) {
  const read = (path) => readFileSync(path, 'utf8')
  const ymls = listGitFiles(['.github/workflows', '.github/actions']).filter((file) => /\.ya?ml$/.test(file))
  exitIfNoFiles(ymls, 'workflow と action の yml のファイル')
  const packageJson = read(PACKAGE_JSON_FILE)
  const enginesIndex = packageJson.split('\n').findIndex((line) => /"node"\s*:/.test(line))
  const problems = findRuntimeVersionProblems({
    workflowJava: readJavaFromWorkflow(read(JAVA_VERSION_HOME)),
    tomlJava: readJavaFromToml(read('backend/ecsite/gradle/libs.versions.toml')),
    nodeFile: readNodeVersionFile(read(NODE_VERSION_FILE)),
    enginesNode: readEnginesNode(packageJson),
    enginesLine: enginesIndex + 1 || 1,
  })
  const workflows = ymls.map((file) => ({ path: file, text: read(file) }))
  problems.push(...findVersionLiteralProblems(workflows))
  problems.push(...findJavaVersionDefinitionProblems(workflows))
  problems.push(...findReactSpecifierProblems(PACKAGE_JSON_PATHS.map((file) => ({ path: file, text: read(file) }))))
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`実行環境のバージョン：問題 ${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
