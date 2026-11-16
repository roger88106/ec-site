// frontend-rules が規則を当てるファイルの種類と、種類ごとの振り分け。
// spec-numbers も、テストのファイルの種類を読む（frontend-rules.mjs が公開する）

/** 画面のソース（テスト、生成物、shadcn/ui の部品、共有の ui、テストの補助を含む） */
export const SOURCE = /^frontend\/(?:store|admin|shared)\/src\/.*\.tsx?$/

/** 場所に関わらない、テストのファイル（.test.ts・.test.tsx）。公開する名前などから、どの場所のテストも除くときに使う */
export const ANY_TEST_FILE = /\.test\.tsx?$/

/** 画面の単体・結合テストのファイル */
export const FRONTEND_TEST = /^frontend\/(?:store|admin|shared)\/src\/.*\.test\.tsx?$/

/** 画面のテストの補助のファイル（src の下の test/ ディレクトリの下） */
export const FRONTEND_TEST_SUPPORT = /^frontend\/(?:store|admin|shared)\/src\/(?:.*\/)?test\/.*\.tsx?$/

/** E2E のファイル */
export const E2E_FILE = /^e2e\/.*\.tsx?$/

/**
 * ファイルを、規則を当てるまとまりに振り分ける。ファイルの順は保つ。どのまとまりにも入らないファイルは捨てる。
 * まとまりは重なる：sources はテストの補助を含み、tests と testsWithSupport は画面のテストを含む。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {{ sources: string[], frontendTests: string[], testSupport: string[], e2e: string[], tests: string[], testsWithSupport: string[], testsWithSupportAndE2e: string[] }}
 *   sources は画面のソース（テストを除く。テストの補助を含む）、frontendTests は画面の単体・結合テスト、testSupport はテストの補助、e2e は E2E、
 *   tests は画面のテストと E2E、testsWithSupport は画面のテストとテストの補助（E2E を除く）、
 *   testsWithSupportAndE2e は画面のテストとテストの補助と E2E（src/test/ の下の .test.ts は 1 回だけ入る。ファイルごとに 1 回報告する検査用）
 */
export function groupTargets(files) {
  const pick = (matches) => files.filter(matches)
  const isFrontendTest = (file) => FRONTEND_TEST.test(file)
  const isTestSupport = (file) => FRONTEND_TEST_SUPPORT.test(file)
  const isE2e = (file) => E2E_FILE.test(file)
  return {
    sources: pick((file) => SOURCE.test(file) && !isFrontendTest(file)),
    frontendTests: pick(isFrontendTest),
    testSupport: pick(isTestSupport),
    e2e: pick(isE2e),
    tests: pick((file) => isFrontendTest(file) || isE2e(file)),
    testsWithSupport: pick((file) => isFrontendTest(file) || isTestSupport(file)),
    testsWithSupportAndE2e: pick((file) => isFrontendTest(file) || isTestSupport(file) || isE2e(file)),
  }
}
