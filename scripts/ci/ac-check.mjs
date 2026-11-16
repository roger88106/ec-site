// AC 一覧（docs/03-機能仕様/acceptance-criteria.md）を、③ の表、進行中の spec、テスト名と照合する。
// AC 一覧は AC の正本で、③ の表は「今有効な AC」の節の表。テスト名の規則は、テストの書き方の「AC 番号を入れる」。
// 目的：AC の今の内容を AC 一覧の 1 か所に置き、③ の表とテストとの対応を、AC 一覧と食い違わせない。
//   AC の内容を得るために、アーカイブの spec は読まない。
//
// 読み方と失敗の条件は、モジュールごとの先頭のコメントにある。
//   - ac/ac-list.mjs：AC 一覧、③ の表、進行中の spec の読み書き
//   - ac/match.mjs：照合（失敗にする条件）
//   - ac/test-names.mjs：テスト名と AC の番号の読み方
// テスト名に出ない有効・変更の AC は、CI（環境変数 CI=true）でだけ失敗にし、手元では警告にする。
//   spec を書いてからテストを書くまでの間も commit できるようにし、テストの漏れは CI で防ぐため。
//   手元で CI と同じに確認するとき：CI=true node scripts/ci/ac-check.mjs
//
// 失敗したとき：
//   - AC 一覧の行を修正する。進行中の spec の AC がないときは --write で転記し、モジュールの列を書く。
//     転記した行はモジュールが空なので、書くまで失敗する
//   - ③ の表が古いときは、--write で書き換えて差分を確認する。
//     マーカーと節の誤りは手動で修正する（そのとき --write は何も書かずに失敗する）
//   - テストを追加するか、テスト名に AC を書く。AC は読点で 1 つずつ書く（AC-n、AC-m）。
//     読めない呼び出しは ファイル:行 を示すので、テスト名を文字列で書く
// 除外：ac-exclusions.json に {キー: 理由}。キーは AC 一覧の有効・変更の AC（AC-n）。
//   テストがある AC、AC 一覧にない AC、廃止の AC の除外は失敗にする
// 使い方（リポジトリのルートで）：
//   - node scripts/ci/ac-check.mjs：照合する
//   - node scripts/ci/ac-check.mjs --write：AC 一覧にない進行中の spec の AC を転記し、③ のマーカーの範囲を書き換える。
//     範囲の外と、一覧にない ③ は変えない
//   - node scripts/ci/ac-check.mjs --tests：AC とテストの対応を表示する（対応は Git に置かない）
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describeEmptyReason, describeStaleExclusion, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { FUNCTIONAL_SPECS } from './lib/functional-specs.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { listSpecDirs } from './lib/spec-dirs.mjs'
import { AC_LIST, BACKEND_MODULES_DIR, MODULE_DOCS_DIR, SPECS_DIR, addAcsToList, listModuleNames, readAcList, readInProgressSpecs } from './ac/ac-list.mjs'
import { check, checkAcRows, checkInProgress, checkModuleDoc, checkModuleDocNames, renderTestMap } from './ac/match.mjs'
import { readTests } from './ac/test-names.mjs'

/** 除外の一覧のファイルの名前 */
const EXCLUSIONS_NAME = 'ac-exclusions.json'

/**
 * テスト名に出ない AC を失敗にするかを、環境から決める。CI では失敗にし、手元では警告にする。<br />
 * 理由：spec を書いてからテストを書くまでの間も、手元で commit できるようにするため。
 * テストの漏れは、CI（push と pull request）で止める。
 *
 * @param env 環境変数
 * @returns CI なら true
 */
export function isStrict(env) {
  return env.CI === 'true'
}

/** 照合した結果を、標準出力に書く */
function report(result, root, strict) {
  const where = (test) => `${path.relative(root, test.file).replaceAll('\\', '/')}：${test.name}`
  if (result.missing.length > 0) {
    // 手元では警告。check-all.sh は「警告：」で始まる行を、通ったときも出す
    const prefix = strict ? '' : '警告：'
    console.log(`${prefix}テスト名に出ない AC：${result.missing.map((ac) => `AC-${ac}`).join('、')}`)
    console.log('  テストを追加するか、テストで確認できない理由を scripts/ci/ac-exclusions.json に書く。')
  }
  for (const test of result.ranges) {
    console.log(`範囲の表記（AC 番号を 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.ambiguous) {
    console.log(`曖昧な AC の並べ方（新しい形は AC-7-1、AC-7-2 と書く）：${where(test)}`)
  }
  for (const test of result.spaced) {
    console.log(`空白で区切った AC の並び（AC-1、AC-2 と読点で 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.unknown) {
    console.log(`AC 一覧にない AC-${test.ac}：${where(test)}`)
  }
  for (const test of result.deprecatedRefs) {
    console.log(`廃止した AC-${test.ac} を参照するテスト（AC 番号を消すか、AC 一覧の状態を直す）：${where(test)}`)
  }
  for (const ac of result.staleExclusions) {
    console.log(describeStaleExclusion(EXCLUSIONS_NAME, `AC-${ac}`))
  }
  for (const key of result.emptyReasons) {
    console.log(describeEmptyReason(EXCLUSIONS_NAME, key))
  }
  if (result.invalidExclusions.length > 0) {
    console.log(`${EXCLUSIONS_NAME}：AC 一覧にない AC か、廃止の AC の除外：${result.invalidExclusions.join('、')}`)
  }
}

/**
 * ③ の置き場所の文書（AC 一覧を除く）を読む。
 *
 * @param {string} root リポジトリのルート
 * @returns {{ file: string, module: string, text: string, required: boolean }[]} リポジトリのルートからのパス、モジュールの名前（ファイル名から .md を除く）、内容、構造の検査の対象の一覧にあるか
 */
function readModuleDocs(root) {
  if (!existsSync(path.join(root, MODULE_DOCS_DIR))) return []
  return readdirSync(path.join(root, MODULE_DOCS_DIR))
    .filter((name) => name.endsWith('.md') && `${MODULE_DOCS_DIR}/${name}` !== AC_LIST)
    .sort()
    .map((name) => {
      const file = `${MODULE_DOCS_DIR}/${name}`
      return { file, module: name.slice(0, -3), text: readFileSync(path.join(root, file), 'utf8'), required: FUNCTIONAL_SPECS.includes(file) }
    })
}

/** AC 一覧、③、進行中の spec、テストを読んで照合する。--write は転記と ③ の表の書き換え、--tests は対応の表示 */
function main() {
  // 他の検査と同じく、リポジトリのルートで実行する（今のディレクトリを、読む対象のルートにする）
  const root = process.cwd()
  const writing = process.argv.includes('--write')
  // 対象のファイルが 0 件なら、何も見ずに通らないよう、先に失敗にする
  const gitFiles = listGitFiles([], root)
  exitIfNoFiles(gitFiles, 'Git のファイル')
  const { tests, unreadable, testFiles } = writing ? { tests: [], unreadable: [], testFiles: new Map() } : readTests(root, gitFiles)
  const listFile = path.join(root, AC_LIST)
  const document = existsSync(listFile) ? readFileSync(listFile, 'utf8') : ''
  let list = readAcList(document)
  const exclusions = readExclusions(exclusionsPath(EXCLUSIONS_NAME))

  if (process.argv.includes('--tests')) {
    const { lines, ok } = renderTestMap({ list, testFiles, exclusions })
    for (const line of lines) console.log(line)
    process.exit(ok ? 0 : 1)
  }

  const inProgress = readInProgressSpecs(path.join(root, SPECS_DIR))
  const docs = readModuleDocs(root)
  const backendDir = path.join(root, BACKEND_MODULES_DIR)
  const backendDirs = existsSync(backendDir) ? readdirSync(backendDir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name) : []
  const modules = listModuleNames({ backendDirs, docFiles: docs.map((doc) => path.basename(doc.file)) })
  const listProblems = [...list.problems, ...inProgress.problems]

  if (writing) {
    if (listProblems.length > 0) {
      for (const problem of listProblems) console.log(`${AC_LIST}：${problem}`)
      console.log('NG：AC 一覧か進行中の spec を読めないので、書き換えない')
      process.exit(1)
    }
    const written = addAcsToList(document, inProgress.specs.flatMap((spec) => spec.definitions))
    if (written !== document) list = readAcList(written)
    const results = docs.map((doc) => ({ doc, result: checkModuleDoc({ text: doc.text, module: doc.module, rows: list.rows, required: doc.required }) }))
    const blocking = results.flatMap(({ doc, result }) => (result.stale ? [] : result.problems.map((problem) => `${doc.file}：${problem}`)))
    if (blocking.length > 0) {
      for (const problem of blocking) console.log(problem)
      console.log('NG：③ の生成の印か節を修正してから、もう一度実行する（何も書き換えていない）')
      process.exit(1)
    }
    if (written !== document) {
      writeFileSync(listFile, written)
      console.log(`転記した：${AC_LIST}`)
    }
    for (const { doc, result } of results.filter(({ result }) => result.stale)) {
      writeFileSync(path.join(root, doc.file), result.updated)
      console.log(`書き換えた：${doc.file}`)
    }
    console.log('OK：書き換えた後に、モジュールの列と差分を確認する')
    process.exit(0)
  }

  const specDirs = listSpecDirs(path.join(root, SPECS_DIR))
  listProblems.push(...checkAcRows({ rows: list.rows, modules, specDirs }), ...checkInProgress({ rows: list.rows, specs: inProgress.specs }))
  const docProblems = checkModuleDocNames({ docs, modules })
  for (const doc of docs) {
    docProblems.push(...checkModuleDoc({ text: doc.text, module: doc.module, rows: list.rows, required: doc.required }).problems.map((problem) => `${doc.file}：${problem}`))
  }

  const specAcs = new Set([...list.rows.map((row) => row.ac), ...inProgress.specs.flatMap((spec) => spec.definitions.map((definition) => definition.ac))])
  const deprecated = new Set(list.rows.filter((row) => row.status === '廃止').map((row) => row.ac))
  const strict = isStrict(process.env)
  const result = check({ specAcs, tests, exclusions, deprecated, strict })
  report(result, root, strict)
  for (const problem of listProblems) console.log(`${AC_LIST}：${problem}`)
  for (const problem of docProblems) console.log(problem)
  for (const problem of unreadable) console.log(problem)
  const ok = result.ok && listProblems.length === 0 && docProblems.length === 0 && unreadable.length === 0
  const generated = docs.filter((doc) => doc.required).length
  console.log(`${ok ? 'OK' : 'NG'}：AC ${list.rows.length} 件（廃止 ${deprecated.size} 件）、③ の生成の表 ${generated} 件、テスト名 ${tests.length} 件、除外 ${Object.keys(exclusions).length} 件`)
  process.exit(ok ? 0 : 1)
}

if (isMain(import.meta.url)) {
  main()
}
