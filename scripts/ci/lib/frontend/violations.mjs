// frontend-rules の違反と、除外の一覧の誤りを、表示する文にして集める（規則は frontend-rules の冒頭のコメント）。
import { isScreenSource } from '../docs/screen-sources.mjs'
import { checkExclusions } from '../output/exclusions.mjs'
import { groupTargets } from './files.mjs'
import { findIndexedMultiples } from './screen-tests.mjs'
import { duplicatedFiles, findDefinitions } from './shared-names.mjs'

/**
 * 共有と同じ名前の定義の、除外のキー（<画面>:<名前>）を返す。
 *
 * @param {string} file リポジトリのルートからのパス（frontend/<画面>/…）
 * @param {string} name 定義の名前
 * @returns {string} 除外のキー
 */
function shadowKey(file, name) {
  return `${file.split('/')[1]}:${name}`
}

/**
 * フロントの書き方の違反と、除外の一覧の誤りを探す。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @param {(file: string) => string} read ファイルの中身を読む関数
 * @param {Set<string>} sharedNames 共有のパッケージが公開する名前
 * @param {Record<string, unknown>} exclusions 除外の一覧（frontend-rules-exclusions.json）
 * @returns {string[]} 表示する文。共有と同じ名前の定義、並びのインデックス、同じ内容のファイル、除外の一覧の誤りの順
 */
export function findViolations(files, read, sharedNames, exclusions) {
  const messages = []
  const targets = groupTargets(files)
  const shadowing = new Set()
  for (const file of targets.sources) {
    if (!isScreenSource(file) || file.startsWith('frontend/shared/')) continue
    for (const hit of findDefinitions(read(file), sharedNames)) {
      const key = shadowKey(file, hit.name)
      shadowing.add(key)
      if (Object.hasOwn(exclusions, key)) continue
      messages.push(`${file}:${hit.line} 共有のパッケージが公開する ${hit.name} と同じ名前の定義（共有のパッケージの ${hit.name} を使う。フロントの書き方の「入れるもの・入れないもの」。別の定義が正しいときは除外の一覧にキー ${key} と理由を書く）：${hit.text}`)
    }
  }
  for (const file of targets.frontendTests) {
    for (const hit of findIndexedMultiples(read(file))) {
      messages.push(`${file}:${hit.line} 複数の結果を並びのインデックスで選ぶ（役割と名前で 1 つに絞る。フロントのテストの書き方の「画面に見えるもので探す」）：${hit.text}`)
    }
  }
  // 同じ内容のファイルの一覧は 1 回だけ求め、違反の報告（除外を除く）と除外の要否の判定の両方に使う
  const duplicated = new Set(duplicatedFiles(files.filter((file) => /^frontend\/(?:store|admin)\/src\//.test(file)), read))
  for (const relative of [...duplicated].filter((relative) => !Object.hasOwn(exclusions, relative))) {
    messages.push(`frontend/{store,admin}/src/${relative} 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）`)
  }
  // 除外が必要なのは、違反になる同じ内容のファイルと共有と同じ名前の定義だけ（片方が消えた、内容が変わった、定義がなくなったものは不要）
  messages.push(...checkExclusions('frontend-rules-exclusions.json', exclusions, (key) => duplicated.has(key) || shadowing.has(key)))
  return messages
}
