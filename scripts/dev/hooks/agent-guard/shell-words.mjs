// bash のコマンドの全文を、コマンドごとの語に分ける（引用符、`$(…)`、バッククォート、ヒアドキュメント、リダイレクトを読む）。

/**
 * ヒアドキュメントの本文を読み飛ばす。
 *
 * @param {string} command コマンドの全文
 * @param {number} start 本文の始まり（`<<` のある行の次の行の先頭）
 * @param {{ delimiter: string, stripTabs: boolean }[]} heredocs その行で始まったヒアドキュメント
 * @returns {number} 最後の本文の、終わりの行の次の位置
 */
function skipHeredocs(command, start, heredocs) {
  let position = start
  for (const { delimiter, stripTabs } of heredocs) {
    while (position < command.length) {
      const newline = command.indexOf('\n', position)
      const end = newline < 0 ? command.length : newline
      let line = command.slice(position, end).replace(/\r$/, '')
      if (stripTabs) line = line.replace(/^\t+/, '')
      position = newline < 0 ? command.length : newline + 1
      if (line === delimiter) break
    }
  }
  return position
}

/**
 * コマンドの文字列を、1 つずつのコマンドの語の並びに分ける（bash の書き方の、判定に必要な範囲だけを読む）。
 * `&&`・`||`・`;`・`|`・`&`・改行・括弧で区切り、引用符の中は区切らない。
 * コメント、リダイレクトとその先のファイル、ヒアドキュメントの本文は、語に含めない。
 * `$(…)` とバッククォートの中は、二重引用符の中でも、別のコマンドとして同じ読み方で読み、外のコマンドより前に並べる
 * （外の語には `$(…)`・`` `…` `` と書く）。
 *
 * @param {string} command コマンドの全文
 * @returns {string[][]} コマンドごとの語（引用符と \ を外したもの）
 * @throws {Error} 引用符、`$(…)`、バッククォートが閉じていないとき
 */
export function splitCommands(command) {
  const commands = []
  tokenize(command, 0, null, commands)
  return commands
}

/**
 * コマンドの文字列を、`start` から、`closer` で閉じるまで（なければ終わりまで）読み、コマンドを `commands` に加える。
 *
 * @param {string} command コマンドの全文
 * @param {number} start 読み始める位置
 * @param {')' | '`' | null} closer 閉じる文字。`$(` の中は `)`、バッククォートの中は `` ` ``、一番外は null
 * @param {string[][]} commands 読んだコマンドを加える先
 * @returns {number} 閉じる文字の位置。一番外では文字列の長さ
 * @throws {Error} 引用符、`$(…)`、バッククォートが閉じていないとき
 */
function tokenize(command, start, closer, commands) {
  let words = []
  let word = ''
  let inWord = false
  let dropNextWord = false
  let heredocs = []
  // 括弧（サブシェル）の深さ。$( の中では、深さが 0 の ) で閉じる
  let depth = 0
  let i = start
  const n = command.length
  const unclosed = (what) => new Error(`閉じていない${what}がある`)
  /** `$(` か `` ` `` の中を別のコマンドとして読み、外の語には印を残す。閉じる文字の次の位置を返す */
  const substitute = (at, inner) => {
    const end = tokenize(command, at, inner, commands)
    word += inner === ')' ? '$(…)' : '`…`'
    inWord = true
    return end + 1
  }
  const endWord = () => {
    if (!inWord) return
    if (dropNextWord) dropNextWord = false
    else words.push(word)
    word = ''
    inWord = false
  }
  const endCommand = () => {
    endWord()
    dropNextWord = false
    if (words.length > 0) commands.push(words)
    words = []
  }
  // 1 文字ずつ、引用符・区切り・リダイレクト・ヒアドキュメントを見分けて語を作る
  while (i < n) {
    const c = command[i]
    if ((closer === '`' && c === '`') || (closer === ')' && c === ')' && depth === 0)) {
      endCommand()
      return i
    }
    if (c === "'") {
      const end = command.indexOf("'", i + 1)
      if (end < 0) throw unclosed("'")
      word += command.slice(i + 1, end)
      inWord = true
      i = end + 1
    } else if (c === '"') {
      inWord = true
      i++
      while (command[i] !== '"') {
        if (i >= n) throw unclosed('"')
        // 二重引用符の中の \ は、" \ $ ` 改行の前でだけエスケープとして扱われる
        if (command[i] === '\\' && i + 1 < n && '"\\$`\n'.includes(command[i + 1])) {
          if (command[i + 1] !== '\n') word += command[i + 1]
          i += 2
        } else if (command.startsWith('$(', i) && command[i + 2] !== '(') {
          i = substitute(i + 2, ')')
        } else if (command[i] === '`') {
          i = substitute(i + 1, '`')
        } else {
          word += command[i]
          i++
        }
      }
      i++
    } else if (command.startsWith('$(', i) && command[i + 2] !== '(') {
      i = substitute(i + 2, ')')
    } else if (c === '`') {
      i = substitute(i + 1, '`')
    } else if (c === '\\') {
      if (i + 1 < n && command[i + 1] !== '\n') {
        word += command[i + 1]
        inWord = true
      }
      i += 2
    } else if (c === '#' && !inWord) {
      while (i < n && command[i] !== '\n') i++
    } else if (c === ' ' || c === '\t' || c === '\r') {
      endWord()
      i++
    } else if (c === '\n') {
      endCommand()
      i = skipHeredocs(command, i + 1, heredocs)
      heredocs = []
    } else if (command.startsWith('<<', i) && !command.startsWith('<<<', i)) {
      endWord()
      i += 2
      const stripTabs = command[i] === '-'
      if (stripTabs) i++
      while (command[i] === ' ' || command[i] === '\t') i++
      let delimiter = ''
      while (i < n && !/[\s;&|()<>]/.test(command[i])) {
        if (!`"'\\`.includes(command[i])) delimiter += command[i]
        i++
      }
      heredocs.push({ delimiter, stripTabs })
    } else if (c === '<' || c === '>' || (c === '&' && command[i + 1] === '>')) {
      // 2>&1 の 2 のような、リダイレクトの前の数字は語でない
      if (inWord && /^\d+$/.test(word)) {
        word = ''
        inWord = false
      } else {
        endWord()
      }
      i += c === '&' ? 2 : 1
      while (command[i] === '>' || command[i] === '<' || command[i] === '|') i++
      if (command[i] === '&') {
        i++
        while (i < n && /[\d-]/.test(command[i])) i++
      } else {
        dropNextWord = true
      }
    } else if (';&|()'.includes(c)) {
      if (c === '(') depth++
      else if (c === ')' && depth > 0) depth--
      endCommand()
      i++
    } else {
      word += c
      inWord = true
      i++
    }
  }
  if (closer !== null) throw unclosed(closer === ')' ? ' $(' : ' `')
  endCommand()
  return n
}
