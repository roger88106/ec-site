// frontend-rules が規則を当てるファイルの種類と、種類ごとの振り分け。
// 画面のテストのファイルの場所は、spec-numbers と共有する lib/screen-sources.mjs にある
import { FRONTEND_TEST } from '../lib/screen-sources.mjs'

/** 画面のソース（テスト、生成物、shadcn/ui のコンポーネント、共有の ui、テストの補助を含む） */
const SOURCE = /^frontend\/(?:store|admin|shared)\/src\/.*\.tsx?$/

/** 場所に関わらない、テストのファイル（.test.ts・.test.tsx）。公開する名前などから、どの場所のテストも除くときに使う */
export const ANY_TEST_FILE = /\.test\.tsx?$/

/**
 * ファイルを、規則を当てるまとまりに振り分ける。ファイルの順は保つ。どのまとまりにも入らないファイルは捨てる。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {{ sources: string[], frontendTests: string[] }}
 *   sources は画面のソース（テストを除く。テストの補助を含む）、frontendTests は画面の単体・結合テスト
 */
export function groupTargets(files) {
  const isFrontendTest = (file) => FRONTEND_TEST.test(file)
  return {
    sources: files.filter((file) => SOURCE.test(file) && !isFrontendTest(file)),
    frontendTests: files.filter(isFrontendTest),
  }
}
