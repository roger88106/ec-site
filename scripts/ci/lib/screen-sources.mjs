// 画面の本番のソースの定義。画面のソースを読む検査は、ここで決めた対象を読む。
// 対象は、店舗画面・管理画面・共有パッケージの src の .ts と .tsx。
// 除くものは、テスト（.test.ts(x)、src/test/ のテストの補助）、生成物（generated/）、shadcn/ui の部品（components/ui/）、共有の ui（src/ui/）。
// 定義と違う対象を読む検査（frontend-rules、spec-numbers、input-constraints）は、その理由を検査の中に書く
// 定義と同じ対象を読む検査は、message-comments と hardcoded-text

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
