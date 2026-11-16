// Git を呼ぶテストのために、環境変数の GIT_ で始まるものを外す。
// commit の前の hook の中では GIT_DIR などが設定され、試す一時のリポジトリでなく commit 中のリポジトリを指すため。
// 外す処理はここだけに置く（git-env.test.mjs が、scripts/ の下で自分で外している所を失敗にする）

/**
 * 環境変数のうち、Git の設定（GIT_ で始まるもの）を外した写しを返す。元の環境は変えない。
 *
 * @param {Record<string, string | undefined>} [env] 元の環境。なければ `process.env`
 * @returns {Record<string, string | undefined>} GIT_ で始まるものを除いた環境
 */
export function withoutGitEnv(env = process.env) {
  return Object.fromEntries(Object.entries(env).filter(([name]) => !name.startsWith('GIT_')))
}

/**
 * `process.env` から GIT_ で始まる変数を消す。環境を渡せない呼び出し（自分で git を呼ぶ関数のテスト）の前に使う。
 */
export function removeGitEnv() {
  const kept = withoutGitEnv()
  for (const name of Object.keys(process.env)) {
    if (!(name in kept)) delete process.env[name]
  }
}
