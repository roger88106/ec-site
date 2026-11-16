// フロントの書き方の規則のうち、Biome の plugin（frontend/biome-plugins/、e2e/biome-plugins/）で書けないものを確認する。
// 複数のファイルを比べる規則と、変数の範囲を追う規則が残る。規則は、ファイルの種類ごとのモジュール（frontend-rules/）にあり、対象と見る形は関数のコメントにある。
//
// - 店舗画面と管理画面：同じ相対パスで同じ内容のファイルを置かない。共有のパッケージが公開する名前と同じ名前を定義しない（フロントの書き方の「入れるもの・入れないもの」）
// - 画面のテスト：複数の結果を並びのインデックスで選ばない（フロントのテストの書き方の「画面に見えるもので探す」）
//
// 失敗したとき：報告の括弧の中の書き方に直す。同じファイルは共有のパッケージに移す。
// 除外：frontend-rules-exclusions.json に {キー: 理由}。キーは src からの相対パス（同じ内容のファイル）か <画面>:<名前>（共有と同じ名前の定義。例：store:translate）。不要になった除外は失敗にする。
// 使い方：node scripts/ci/frontend/frontend-rules.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { checkExclusions, exclusionsPath, readExclusions } from '../lib/output/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from '../lib/git/git-files.mjs'
import { isScreenSource } from '../lib/docs/screen-sources.mjs'
import { groupTargets } from '../lib/frontend/files.mjs'
import { findIndexedMultiples } from '../lib/frontend/screen-tests.mjs'
import { duplicatedFiles, findDefinitions, sharedPublicNames } from '../lib/frontend/shared-names.mjs'

const shadowKey = (file, name) => `${file.split('/')[1]}:${name}`

if (import.meta.main) {
  const files = listGitFiles(['frontend'])
  exitIfNoFiles(files, 'フロントのファイル')
  const exclusions = readExclusions(exclusionsPath('frontend/frontend-rules-exclusions.json'))
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
    for (const hit of findIndexedMultiples(text)) {
      report(`${file}:${hit.line} 複数の結果を並びのインデックスで選ぶ（役割と名前で 1 つに絞る。フロントのテストの書き方の「画面に見えるもので探す」）：${hit.text}`)
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
