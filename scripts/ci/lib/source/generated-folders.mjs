// 生成物と依存のフォルダー（書き手が直せないもの）の判定。参照の検査、日本語の文の検査、用語集の識別子の検査が、同じ一覧を使う。
//
// 判定はパスの区切りの単位で行う（`build/` は先頭か `/build/` の形。`rebuild/` は当てない）。

/** 生成物と依存のフォルダーの名前（ビルドの出力、依存のパッケージ、生成したコード） */
const GENERATED_FOLDER_NAMES = ['build', 'node_modules', '.next', 'dist', 'generated']

const GENERATED_FOLDER = new RegExp(`(?:^|/)(?:${GENERATED_FOLDER_NAMES.map((name) => name.replace('.', '\\.')).join('|')})/`)

/**
 * パスが、生成物のフォルダーの中か（パス自体がフォルダーの場合を含む）。
 *
 * @param {string} path リポジトリのルートからのパス（/ 区切り。フォルダーは末尾の / があってもなくてもよい）
 * @returns {boolean} 生成物のフォルダーの中なら true
 */
export function isInGeneratedFolder(path) {
  return GENERATED_FOLDER.test(path.endsWith('/') ? path : `${path}/`)
}
