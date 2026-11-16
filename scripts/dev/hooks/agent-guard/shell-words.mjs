// bash のコマンドの全文を、コマンドごとの語に分ける（引用符、`$(…)`、バッククォート、ヒアドキュメント、リダイレクトを読む）。
// シェルが展開する部分を含む語には、EXPANDED_WORDS で印を付ける（gh の判定が、値を読めない語として扱うため）。

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
 * splitCommands が返したコマンドの語の並びごとの、シェルが展開する部分（変数、`$(…)`、バッククォート）を含む語。
 * 引用符を外した語では、単一引用符の中の `$` と区別できないので、語を読むときに記録する。
 *
 * @type {WeakMap<string[], Set<string>>}
 */
export const EXPANDED_WORDS = new WeakMap()

/** 引用符の外と二重引用符の中で、`$` の次にあれば変数の展開（算術式の `$((` を含む）になる文字 */
const EXPANSION_START = /^[A-Za-z_0-9{@*#?$!(-]/

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
  // 今の語が、シェルが展開する部分を含むか
  let expanded = false
  let expandedWords = new Set()
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
    expanded = true
    return end + 1
  }
  /** `$` を語に加え、変数の展開なら記録する */
  const addDollar = (at) => {
    if (EXPANSION_START.test(command[at + 1] ?? '')) expanded = true
    word += '$'
  }
  const endWord = () => {
    if (!inWord) return
    if (dropNextWord) {
      dropNextWord = false
    } else {
      words.push(word)
      if (expanded) expandedWords.add(word)
    }
    word = ''
    inWord = false
    expanded = false
  }
  const endCommand = () => {
    endWord()
    dropNextWord = false
    if (words.length > 0) {
      if (expandedWords.size > 0) EXPANDED_WORDS.set(words, expandedWords)
      commands.push(words)
    }
    words = []
    expandedWords = new Set()
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
        } else if (command[i] === '$') {
          addDollar(i)
          i++
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
        expanded = false
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
    } else if (c === '$') {
      addDollar(i)
      inWord = true
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
