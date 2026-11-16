// フロントの書き方の規則のうち、Biome の plugin（frontend/biome-plugins/、e2e/biome-plugins/）で書けないものを確認する。
// 複数のファイルを比べる規則と、変数の範囲や式の終わりを追う規則が残る。規則は、ファイルの種類ごとのモジュール（frontend-rules/）にあり、対象と見る形は関数のコメントにある。
//
// - 店舗画面と管理画面：同じ相対パスで同じ内容のファイルを置かない。共有のパッケージが公開する名前と同じ名前を定義しない（フロントの書き方の「入れるもの・入れないもの」）
// - 画面のテスト：要素のプロパティ・属性を toBe・toEqual で比べない（フロントのテストの書き方の「要素のプロパティ・属性・状態を jest-dom で確認する」）
// - 画面のテストと E2E：複数の結果を並びのインデックスで選ばない（フロントのテストの書き方の「画面に見えるもので探す」）
// - E2E：ボタンの名前を完全一致で探す（同上）。タイムアウトを数字のリテラルで書かない（テストの書き方の「待ち方」）
// - 画面のテストと補助と E2E：テストの間に空行を置く（フロントのテストの書き方の「テストとテストの間に空行を置く」）。
//   組み込みの名前と同じ名前の const・let を宣言しない（フロントのテストの書き方の「組み込みの名前をシャドーイングしない」）。
//   名前の一覧 globals/lib-globals.json は、lib-globals.mjs --write が TypeScript の lib から作る
//
// 失敗したとき：報告の括弧の中の書き方に直す。同じファイルは共有のパッケージに移す。TypeScript を上げて lib の名前が変わったときは、lib-globals.mjs --write で一覧を書き換える。
// 除外：frontend-rules-exclusions.json に {キー: 理由}。キーは src からの相対パス（同じ内容のファイル）か <画面>:<名前>（共有と同じ名前の定義。例：store:translate）。不要になった除外は失敗にする。
// 使い方：node scripts/ci/frontend-rules.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'
import { findButtonNameChecks, findTimeoutLiterals, timeoutTargets } from './frontend-rules/e2e.mjs'
import { groupTargets } from './frontend-rules/files.mjs'
import { findIndexedMultiples, findPropertyComparisons } from './frontend-rules/screen-tests.mjs'
import { duplicatedFiles, findDefinitions, sharedPublicNames } from './frontend-rules/shared-names.mjs'
import { BUILTIN_NAMES_FILE, findBuiltinShadowing, findMissingTestSpacing } from './frontend-rules/test-style.mjs'

export { E2E_FILE, FRONTEND_TEST, FRONTEND_TEST_SUPPORT } from './frontend-rules/files.mjs'

const shadowKey = (file, name) => `${file.split('/')[1]}:${name}`

if (isMain(import.meta.url)) {
  // E2E（e2e/）には、並びのインデックス、E2E の規則、テストの間の空行と組み込みの名前のシャドーイングだけを当てる
  const files = listGitFiles(['frontend', 'e2e'])
  exitIfNoFiles(files, 'フロントのファイル')
  const exclusions = readExclusions(exclusionsPath('frontend-rules-exclusions.json'))
  let count = 0
  const report = (message) => {
    console.log(message)
    count += 1
  }
  const sharedExports = JSON.parse(readFileSync('frontend/shared/package.json', 'utf8')).exports
  let sharedNames
  try {
    sharedNames = sharedPublicNames(sharedExports, files, (file) => readFileSync(file, 'utf8'))
  } catch (error) {
    console.log(error.message)
    process.exit(1)
  }
  const targets = groupTargets(files)
  const shadowing = new Set()
  for (const file of targets.sources) {
    const text = readFileSync(file, 'utf8')
    if (isScreenSource(file) && !file.startsWith('frontend/shared/')) {
      for (const hit of findDefinitions(text, sharedNames)) {
        const key = shadowKey(file, hit.name)
        shadowing.add(key)
        if (!(key in exclusions)) {
          report(`${file}:${hit.line} 共有のパッケージが公開する ${hit.name} と同じ名前の定義（共有のパッケージの ${hit.name} を使う。フロントの書き方の「入れるもの・入れないもの」。別の定義が正しいときは除外の一覧にキー ${key} と理由を書く）：${hit.text}`)
        }
      }
    }
  }
  for (const file of targets.frontendTests) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findPropertyComparisons(text)) {
      report(`${file}:${hit.line} 要素のプロパティ・属性を toBe・toEqual で比べる（toBeDisabled、toHaveValue、toHaveAttribute などを使う。フロントのテストの書き方の「要素のプロパティ・属性・状態を jest-dom で確認する」）：${hit.text}`)
    }
  }
  for (const file of targets.tests) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findIndexedMultiples(text)) {
      report(`${file}:${hit.line} 複数の結果を並びのインデックスで選ぶ（役割と名前で 1 つに絞る。フロントのテストの書き方の「画面に見えるもので探す」）：${hit.text}`)
    }
  }
  const timeoutFiles = new Set(timeoutTargets(targets.e2e))
  for (const file of targets.e2e) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findButtonNameChecks(text)) {
      report(`${file}:${hit.line} ボタンの名前が完全一致でない（文字列は exact: true を付け、正規表現は先頭の ^ と末尾の $ を固定する。フロントのテストの書き方の「画面に見えるもので探す」）：${hit.text}`)
    }
    if (!timeoutFiles.has(file)) continue
    for (const hit of findTimeoutLiterals(text)) {
      report(`${file}:${hit.line} タイムアウトを数字のリテラルで書く（e2e/src/waits.ts の定数を使う。テストの書き方の「待ち方」）：${hit.text}`)
    }
  }
  for (const file of targets.testsWithSupportAndE2e) {
    for (const hit of findMissingTestSpacing(readFileSync(file, 'utf8'))) {
      report(`${file}:${hit.line} テストの間に空行がない（}) の直後に test・it・describe を続けず、空行を置く。フロントのテストの書き方の「テストとテストの間に空行を置く」）：${hit.text}`)
    }
  }
  const builtinNames = new Set(JSON.parse(readFileSync(BUILTIN_NAMES_FILE, 'utf8')))
  for (const file of targets.testsWithSupportAndE2e) {
    for (const hit of findBuiltinShadowing(readFileSync(file, 'utf8'), builtinNames)) {
      report(`${file}:${hit.line} 組み込みの名前 ${hit.name} と同じ名前の const・let（意味の分かる名前にする。フロントのテストの書き方の「組み込みの名前をシャドーイングしない」）：${hit.text}`)
    }
  }
  const sourceFiles = files.filter((file) => /^frontend\/(?:store|admin)\/src\//.test(file))
  // 同じ内容のファイルの一覧は 1 回だけ求め、違反の報告（除外を除く）と除外の要否の判定の両方に使う
  const duplicated = new Set(duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), []))
  for (const relative of [...duplicated].filter((relative) => !Object.hasOwn(exclusions, relative))) {
    report(`frontend/{store,admin}/src/${relative} 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）`)
  }
  // 除外が必要なのは、2 つの画面に同じ内容のファイルがあるもの（キーは src からの相対パス）と、共有と同じ名前の定義があるもの（キーは <画面>:<名前>）だけ
  // （片方が消えた、内容が変わった、定義がなくなったものは不要）
  for (const message of checkExclusions('frontend-rules-exclusions.json', exclusions, (key) => duplicated.has(key) || shadowing.has(key))) {
    report(message)
  }
  console.log(`フロントの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
