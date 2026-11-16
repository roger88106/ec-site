// agent-guard の判定（拒否と確認）の作り方と、外部の処理の型。git と gh の両方の規則が使う理由の文も置く。

/** 確認の画面で利用者に示す、承認してよいときの説明 */
export const APPROVE_ONLY_IF_ALLOWED = '利用者が会話でこの操作を許可したときだけ承認する。'

/** tag の作成と push で確認を求める理由 */
export const TAG_REASON = `tag の作成と push（gh release create と、Release・tag の参照を変える gh api を含む）は、利用者の許可が必要（マージと後片付けの「マージの許可と後片付け」。docs/05-開発規則/merging.md）。${APPROVE_ONLY_IF_ALLOWED}`

/**
 * @typedef {object} Decision hook の判定
 * @property {'deny' | 'ask'} decision 拒否するか、利用者に確認を求めるか
 * @property {string} reason 理由。拒否はエージェントに返す代わりの手順を、確認は利用者が承認してよいときを含める
 */

/**
 * @typedef {object} CommandResult 外部のコマンドの結果（spawnSync の結果の一部）
 * @property {number | null} status 終了コード。タイムアウトなどで終わらなければ null
 * @property {string} stdout 標準出力
 * @property {string} [stderr] 標準エラー
 * @property {Error & { code?: string }} [error] 起動の失敗やタイムアウト（code が ETIMEDOUT）
 */

/**
 * @typedef {object} Deps 差し替えられる外部の処理
 * @property {(args: string[], cwd: string, env?: Record<string, string>) => CommandResult} runGit git を実行する（env は加える環境変数）
 * @property {(file: string) => string | null} readText ファイルを読む。なければ null
 * @property {(args: string[], options: { cwd: string, timeout: number }) => CommandResult} runGh gh を実行する（timeout はミリ秒）
 */

/**
 * 拒否の判定を作る。
 *
 * @param {string} reason 理由と代わりの手順
 * @returns {Decision} 拒否の判定
 */
export function deny(reason) {
  return { decision: 'deny', reason }
}

/**
 * 確認を求める判定を作る。
 *
 * @param {string} reason 理由と、利用者が承認してよいとき
 * @returns {Decision} 確認を求める判定
 */
export function ask(reason) {
  return { decision: 'ask', reason }
}
