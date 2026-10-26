// 検査が読めなかった行（知らない書き方）を、失敗の文にする。読み飛ばすと検査が誤って通るので、どの検査も同じ形で出す。
// 形：`ファイル:行 内容（理由）`。ファイルと行は、エディタやログから開ける形にする

/**
 * 読めなかった行を、失敗の文にする。
 *
 * @param {{ file: string, line: number, text: string, reason: string }} item 読めなかった行（file は表示するパス、line は 1 始まり）
 * @returns {string} `ファイル:行 内容（理由）`。内容の前後の空白は除く
 */
export function describeUnreadableLine({ file, line, text, reason }) {
  return `${file}:${line} ${text.trim()}（${reason}）`
}
