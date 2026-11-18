// 差分（git diff -U0）を取る引数と、その出力から追加行を読む関数。
// 1 か所に置き、CI の注釈（ja-text --changed）と commit の前の hook で追加行の判定が食い違わないようにする。
//
// 利用者の git の設定（diff.renames、diff.mnemonicPrefix など）によらないよう、名前の変更の検出（-M）と接頭辞を固定する。
// 名前を変えたファイルの変えていない行と、改行コードだけの違いは、追加行にしない。

/** `git diff` の後ろに、比べる対象（revision や --cached）と `--` を追加して使う。 */
export const ADDED_DIFF_ARGS = [
  '-c',
  'core.quotePath=false',
  'diff',
  '-M',
  '--ignore-cr-at-eol',
  '--no-color',
  '--no-ext-diff',
  '--no-textconv',
  '-U0',
  '--src-prefix=a/',
  '--dst-prefix=b/',
  '--diff-filter=AMR',
]

/** git が引用符で囲んだパスの、逃がした字（\ooo は 1 バイト） */
const QUOTED_ESCAPES = { a: 7, b: 8, t: 9, n: 10, v: 11, f: 12, r: 13, '"': 34, '\\': 92 }

/**
 * git の差分のパス（"…" で囲み、字を逃がしたもの）を戻す。囲んでいなければそのまま返す。
 *
 * @param {string} name 差分の +++ の行のパス
 * @returns {string} パス
 */
function unquotePath(name) {
  if (!name.startsWith('"') || !name.endsWith('"')) return name
  const bytes = []
  const body = name.slice(1, -1)
  for (let at = 0; at < body.length; at += 1) {
    if (body[at] !== '\\') {
      bytes.push(...Buffer.from(body[at], 'utf8'))
    } else if (/[0-7]{3}/.test(body.slice(at + 1, at + 4))) {
      bytes.push(Number.parseInt(body.slice(at + 1, at + 4), 8))
      at += 3
    } else {
      at += 1
      bytes.push(QUOTED_ESCAPES[body[at]] ?? body.charCodeAt(at))
    }
  }
  return Buffer.from(bytes).toString('utf8')
}

/**
 * git diff -U0 の出力から、ファイルごとの追加行の番号（新しい方の行）を読む。
 *
 * 本文の行は hunk の見出しの行数だけ読み飛ばすので、「+++」で始まる追加行を見出しと取り違えない。
 *
 * @param {string} output 差分（ADDED_DIFF_ARGS で取ったもの）
 * @returns {Map<string, Set<number>>} パス → 追加行の番号（1 から）
 */
export function parseAddedLines(output) {
  const added = new Map()
  let current = null
  let remaining = 0
  for (const line of output.split('\n')) {
    if (remaining > 0) {
      if (!line.startsWith('\\')) remaining -= 1
      continue
    }
    if (line.startsWith('+++ ')) {
      // パスに空白があると、git は後ろにタブを 1 つ付ける
      const name = unquotePath(line.slice(4).replace(/\t$/, ''))
      current = name.startsWith('b/') ? name.slice(2) : null
      if (current !== null && !added.has(current)) added.set(current, new Set())
      continue
    }
    const hunk = /^@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/.exec(line)
    if (!hunk) continue
    const removedCount = Number(hunk[1] ?? 1)
    const start = Number(hunk[2])
    const count = Number(hunk[3] ?? 1)
    remaining = removedCount + count
    for (let number = start; number < start + count; number += 1) added.get(current)?.add(number)
  }
  return added
}
