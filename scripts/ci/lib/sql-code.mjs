// SQL のコメントと文字列を読み飛ばす。マイグレーションを読む検査（migrations、design-migrations）が共通で使う

/**
 * SQL のコメントと文字列の中身を、同じ長さの空白（改行は残す）にする。位置と行番号は変わらない。<br />
 * 行コメント（--）、入れ子にできるブロックコメント、文字列（'' の重ね書き、E'...' の \ のエスケープ）、引用符付きの識別子（中は _ にする）を読む。
 * ドル引用（$$...$$、$タグ$...$タグ$）の中は、DO ブロックなどで実際に実行される文を書くので、何も空白にせず、全て字面のままコードとして残す。
 * 中の ' と " を文字列・識別子の始まりとして読まず、-- と /* も読まない（動的な SQL の文字列の中も探すので、誤検出の側に倒れる）。閉じていないドル引用は読めないものとして返す。
 *
 * @param {string} sql マイグレーションの中身
 * @returns {{ code: string, unreadable: { index: number, reason: string }[] }} コメントと文字列を除いた SQL と、閉じていないもの（位置と理由）
 */
export function blankSqlNonCode(sql) {
  const out = sql.split('')
  const unreadable = []
  const blank = (from, to, fill = ' ') => {
    for (let i = from; i < to; i += 1) if (out[i] !== '\n' && out[i] !== '\r') out[i] = fill
  }
  let i = 0
  while (i < sql.length) {
    if (sql.startsWith('--', i)) {
      const end = sql.indexOf('\n', i)
      const to = end === -1 ? sql.length : end
      blank(i, to)
      i = to
    } else if (sql.startsWith('/*', i)) {
      let depth = 1
      let j = i + 2
      while (j < sql.length && depth > 0) {
        if (sql.startsWith('/*', j)) {
          depth += 1
          j += 2
        } else if (sql.startsWith('*/', j)) {
          depth -= 1
          j += 2
        } else {
          j += 1
        }
      }
      if (depth > 0) unreadable.push({ index: i, reason: '閉じていないブロックコメント' })
      blank(i, j)
      i = j
    } else if (sql[i] === '$' && !/[\w$]/.test(sql[i - 1] ?? '') && /^\$(?:[A-Za-z_\u0080-￿][\w\u0080-￿]*)?\$/.test(sql.slice(i, i + 200))) {
      // ドル引用は、中の ' " -- /* を読まずに、字面のままコードとして残す（EXECUTE の動的な SQL も探せる。誤検出の側に倒す）
      const tag = /^\$[^$]*\$/.exec(sql.slice(i, i + 200))[0]
      const close = sql.indexOf(tag, i + tag.length)
      if (close === -1) {
        unreadable.push({ index: i, reason: '閉じていないドル引用' })
        i = sql.length
      } else {
        i = close + tag.length
      }
    } else if (sql[i] === "'" || sql[i] === '"') {
      const quote = sql[i]
      const escapes = quote === "'" && /[eE]/.test(sql[i - 1] ?? '') && !/[\w$]/.test(sql[i - 2] ?? '')
      let j = i + 1
      let closed = false
      while (j < sql.length) {
        if (escapes && sql[j] === '\\') {
          j += 2
        } else if (sql[j] === quote && sql[j + 1] === quote) {
          j += 2
        } else if (sql[j] === quote) {
          closed = true
          j += 1
          break
        } else {
          j += 1
        }
      }
      if (!closed) unreadable.push({ index: i, reason: quote === "'" ? '閉じていない文字列' : '閉じていない引用符付きの識別子' })
      const end = Math.min(j, sql.length)
      blank(i + 1, closed ? end - 1 : end, quote === '"' ? '_' : ' ')
      i = end
    } else {
      i += 1
    }
  }
  return { code: out.join(''), unreadable }
}
