// 実行環境（JDK と Node）の版が、複数の置き場所で一致していることを確認する。
// JDK：`.github/workflows/ci.yml` の `env.JAVA_VERSION` と `backend/ecsite/gradle/libs.versions.toml` の `java` が同じ値。
// Node：`.node-version` の値が、ルートの `package.json` の `engines.node`（`>=N` の形）の範囲を満たす。
// workflow と action の yml は、バージョンを直接書かず、この置き場所を参照する（新しい job の直書きを見つける。フロー形式の `with: { ... }` も読む）。
// `JAVA_VERSION` は `ci.yml` の最上位の `env` の 1 か所だけで定義する（job の `env` や別のファイルでの上書きを失敗にする）。
// 3 つの package.json の React の指定（`react`、`react-dom`、`@types/react`、`@types/react-dom`）は、全て `^` の範囲の書き方にそろえる。
// 1 か所から読めない版は、置き場所の間の一致を検査で確かめる。ずれると、手元と CI で別の版を動かす。
// 使い方：node scripts/ci/runtime-versions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
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

/** バージョンを指定する入力の名前 */
const VERSION_INPUT_NAME = '(java-version|node-version-file|node-version|java-version-file)'

/** ブロック形式の行（`java-version: 21`、`- java-version: 21`）。値は行の終わりまで */
const BLOCK_VERSION_INPUT = new RegExp(`^\\s*(?:-\\s+)?${VERSION_INPUT_NAME}\\s*:\\s*(.*)$`)

/** フロー形式の中のキー（`with: { node-version: 20 }`）。値は次の , か } まで（引用符と `${{ ... }}` の中は区切りにしない） */
const FLOW_VERSION_INPUT = new RegExp(`[{,]\\s*${VERSION_INPUT_NAME}\\s*:\\s*((?:'[^']*'|"[^"]*"|\\$\\{\\{[^}]*\\}\\}|[^,}])*)`, 'g')

/**
 * workflow と action の yml の、版の指定が決めた 1 か所を参照しているかを調べる。
 * ブロック形式の行と、フロー形式（`with: { ... }`）の中のキーを読む。
 * 値は、前後の引用符と行末のコメントを外して比べる。`node-version` と `java-version-file` は許す値がないので、常に失敗にする。
 *
 * @param {{ path: string, text: string }[]} files yml のパスと内容
 * @returns {string[]} 問題の説明（`パス:行` つき。なければ空）
 */
export function findVersionLiteralProblems(files) {
  const problems = []
  for (const { path, text } of files) {
    text.split('\n').forEach((raw, index) => {
      const line = raw.replace(/\r$/, '')
      if (/^\s*#/.test(line)) return
      const block = BLOCK_VERSION_INPUT.exec(line)
      const found = block ? [block] : [...line.replace(/\s+#.*$/, '').matchAll(FLOW_VERSION_INPUT)]
      for (const [, name, rawValue] of found) {
        const value = rawValue.replace(/\s+#.*$/, '').trim().replace(/^(["'])(.*)\1$/, '$2')
        const expected = ALLOWED_VERSION_INPUTS[name]
        if (value !== expected) {
          const hint = expected === undefined ? 'この入力は使わない（Node は node-version-file: .node-version）' : `${expected} と書く`
          problems.push(`${path}:${index + 1}: ${name} に ${value} を直接書いている。${hint}`)
        }
      }
    })
  }
  return problems
}

/** `JAVA_VERSION` を定義してよい、ただ 1 つのファイル（その最上位の `env`） */
const JAVA_VERSION_HOME = '.github/workflows/ci.yml'

/**
 * `JAVA_VERSION` の定義が、`ci.yml` の最上位の `env` の 1 か所だけにあるかを調べる。
 * job や step の `env`（ブロック形式とフロー形式）や、別のファイルで定義すると、そこで別のバージョンを使えてしまうので失敗にする。
 * 参照（`${{ env.JAVA_VERSION }}`）とコメントは定義と読まない。
 *
 * @param {{ path: string, text: string }[]} files yml のパスと内容
 * @returns {string[]} 問題の説明（`パス:行` つき。なければ空）
 */
export function findJavaVersionDefinitionProblems(files) {
  const problems = []
  let allowed = 0
  for (const { path, text } of files) {
    let topKey = null
    text.split('\n').forEach((raw, index) => {
      const line = raw.replace(/\r$/, '')
      if (/^\s*(#|$)/.test(line)) return
      // 字下げのない行は最上位のキー（引用符で囲んだものも）。キーと読めない行（? jobs など）は null にして、前の env を引きずらない
      if (/^\S/.test(line)) topKey = /^(?:"([^"]*)"|'([^']*)'|([\w-]+))\s*:/.exec(line)?.slice(1).find(Boolean) ?? null
      const code = line.replace(/\s+#.*$/, '')
      if (!/(?:^\s*(?:-\s+)?|[{,]\s*)JAVA_VERSION\s*:/.test(code)) return
      const inTopEnv = path === JAVA_VERSION_HOME && topKey === 'env' && /^\s+JAVA_VERSION\s*:/.test(code)
      if (inTopEnv && allowed === 0) {
        allowed += 1
        return
      }
      problems.push(`${path}:${index + 1}: JAVA_VERSION を定義している。${JAVA_VERSION_HOME} の最上位の env の 1 か所だけに書き、他は \${{ env.JAVA_VERSION }} で参照する`)
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

if (isMain(import.meta.url)) {
  const read = (path) => readFileSync(path, 'utf8')
  const ymls = listGitFiles(['.github/workflows', '.github/actions']).filter((file) => /\.ya?ml$/.test(file))
  exitIfNoFiles(ymls, 'workflow と action の yml のファイル')
  const problems = findRuntimeVersionProblems({
    workflowJava: readJavaFromWorkflow(read(JAVA_VERSION_HOME)),
    tomlJava: readJavaFromToml(read('backend/ecsite/gradle/libs.versions.toml')),
    nodeFile: readNodeVersionFile(read('.node-version')),
    enginesNode: readEnginesNode(read('package.json')),
  })
  const workflows = ymls.map((file) => ({ path: file, text: read(file) }))
  problems.push(...findVersionLiteralProblems(workflows))
  problems.push(...findJavaVersionDefinitionProblems(workflows))
  problems.push(...findReactSpecifierProblems(PACKAGE_JSON_PATHS.map((file) => ({ path: file, text: read(file) }))))
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`実行環境の版：問題 ${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
