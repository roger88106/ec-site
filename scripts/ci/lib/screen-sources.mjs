// 画面の本番のソースの定義。画面のソースを読む検査は、ここで決めた対象を読む。
// 対象は、店舗画面・管理画面・共有パッケージの src の .ts と .tsx。
// 除くものは、テスト（.test.ts(x)、src/test/ のテストの補助）、生成物（generated/）、shadcn/ui の部品（components/ui/）、共有の ui（src/ui/）。
// 定義と違う対象を読む検査は、その理由を検査の中に書く。
//   frontend-rules：機能の間の import、ロケール、共有の名前の定義の確認の一部は、生成物・shadcn/ui の部品・共有の ui・テストの補助も読み、テストの確認（CSS のクラス）は画面のテストと E2E を読む
//   spec-numbers：バックエンドも読み、画面の共有の ui と shadcn/ui の部品も読む
//   input-constraints：定義に加えて、src/test/ のテストの補助を読む
//   time-waits：本番のソースでなく、テストを読む
// 定義と同じ対象を読む検査は、message-comments、hardcoded-text、frontend-rules のうち画面の本番のソースに当てる規則（共有の名前の定義、クエリ付きの URL、line-through）

/** 画面のパッケージ（frontend/ の下のディレクトリ） */
export const SCREEN_APPS = ['admin', 'store', 'shared']

/** 画面の src の .ts と .tsx */
const SCREEN_SOURCE = new RegExp(`^frontend/(?:${SCREEN_APPS.join('|')})/src/.*\\.tsx?$`)

/** 本番のソースでないもの（テスト、テストの補助、生成物、shadcn/ui の部品、共有の ui） */
const NOT_PRODUCTION = [/\.test\.tsx?$/, /\/src\/test\//, /\/generated\//, /\/components\/ui\//, /\/src\/ui\//]

/**
 * 画面の本番のソースかを返す。
 *
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} 画面の本番のソースなら true
 */
export function isScreenSource(file) {
  return SCREEN_SOURCE.test(file) && !NOT_PRODUCTION.some((pattern) => pattern.test(file))
}
