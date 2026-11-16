// フロントの書き方の規則のうち、Biome の plugin（frontend/biome-plugins/、e2e/biome-plugins/）で書けないものを確認する。
// 複数のファイルを比べる規則と、変数の範囲を追う規則が残る。規則は、ファイルの種類ごとのモジュール（lib/frontend/）にあり、対象と見る形は関数のコメントにある。
//
// - 店舗画面と管理画面：同じ相対パスで同じ内容のファイルを置かない。共有のパッケージが公開する名前と同じ名前を定義しない（フロントの書き方の「入れるもの・入れないもの」）
// - 画面のテスト：複数の結果を並びのインデックスで選ばない（フロントのテストの書き方の「画面に見えるもので探す」）
//
// 失敗したとき：報告の括弧の中の書き方に直す。同じファイルは共有のパッケージに移す。
//
// 除外：frontend-rules-exclusions.json に {キー: 理由}。キーは src からの相対パス（同じ内容のファイル）か <画面>:<名前>（共有と同じ名前の定義。例：store:translate）。不要になった除外は失敗にする。
//
// 使い方：node scripts/ci/frontend/frontend-rules.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from '../lib/git/git-files.mjs'
import { sharedPublicNames } from '../lib/frontend/shared-names.mjs'
import { findViolations } from '../lib/frontend/violations.mjs'
import { exclusionsPath, readExclusions } from '../lib/output/exclusions.mjs'

if (import.meta.main) {
  const files = listGitFiles(['frontend'])
  exitIfNoFiles(files, 'フロントのファイル')
  const exclusions = readExclusions(exclusionsPath('frontend/frontend-rules-exclusions.json'))
  const read = (file) => readFileSync(file, 'utf8')
  let sharedNames
  try {
    sharedNames = sharedPublicNames(JSON.parse(read('frontend/shared/package.json')).exports, files, read)
  } catch (e) {
    console.log(e.message)
    process.exit(1)
  }
  const messages = findViolations(files, read, sharedNames, exclusions)
  for (const message of messages) console.log(message)
  console.log(`フロントの書き方の違反：${messages.length}`)
  process.exit(messages.length ? 1 : 0)
}
