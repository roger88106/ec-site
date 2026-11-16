// 語に分けたコマンドから、前に付けたコマンド（timeout、env など）と環境変数を外し、cd・pushd・git -C などで実行するディレクトリを決める。
import { homedir } from 'node:os'
import path from 'node:path'
import { EXPANDED_WORDS, splitCommands } from './shell-words.mjs'

/**
 * 前に付けて、後ろの語を別のコマンドとして実行させるコマンドと、そのオプション。
 * valueShort・valueLong は値を次の語（短いオプションは同じ語の続きも）に取るもの、optionalShort は値を同じ語の続きにだけ取るもの、
 * operands はオプションの後に読む値の数（timeout の時間）。
 */
const PREFIX_COMMANDS = {
  builtin: { valueShort: '', valueLong: [] },
  command: { valueShort: '', valueLong: [] },
  exec: { valueShort: 'a', valueLong: [] },
  nohup: { valueShort: '', valueLong: [] },
  time: { valueShort: 'fo', valueLong: ['format', 'output'] },
  env: { valueShort: 'uCS', valueLong: ['unset', 'chdir', 'split-string'] },
  timeout: { valueShort: 'sk', valueLong: ['signal', 'kill-after'], operands: 1 },
  nice: { valueShort: 'n', valueLong: ['adjustment'] },
  stdbuf: { valueShort: 'ioe', valueLong: ['input', 'output', 'error'] },
  xargs: {
    valueShort: 'ILnPsdEa',
    optionalShort: 'ile',
    valueLong: ['arg-file', 'delimiter', 'eof', 'max-args', 'max-chars', 'max-lines', 'max-procs', 'process-slot-var'],
  },
}

/**
 * コマンドの前に来て、後ろの語をコマンドとして実行させるシェルの予約語（if・for・while の本体や条件、{ } の中、! の否定）。
 * `;` で区切った後の `do git push -f` のような形で、git・gh を語の先頭から見分けるために外す。
 */
const SHELL_KEYWORDS = ['if', 'then', 'else', 'elif', 'do', 'while', 'until', '!', '{']

/** 前に付けた環境変数のうち、対象のリポジトリを替えるもの（pre-commit hook を調べる rev-parse に渡す） */
const REPOSITORY_ENV = ['GIT_DIR', 'GIT_WORK_TREE']

/** 前に付けた環境変数のうち、gh の対象のリポジトリを替えるもの */
const GH_REPOSITORY_ENV = 'GH_REPO'

/**
 * @typedef {object} PathOptions パスの解き方
 * @property {string} [platform] OS（`process.platform` の値）。なければ実行している OS
 * @property {string} [home] ホームのディレクトリ。なければ実行している利用者のもの
 */

/**
 * cd の先などのパスを、ディレクトリの絶対パスにする。
 * Windows では、Git Bash の `/c/…` の形をドライブのパスにする。`~` はホームにする。
 *
 * @param {string} base 起点のディレクトリ
 * @param {string} target パス
 * @param {PathOptions} [options] パスの解き方
 * @returns {string} 絶対パス
 */
export function resolveDir(base, target, { platform = process.platform, home = homedir() } = {}) {
  const lib = platform === 'win32' ? path.win32 : path.posix
  let value = target
  if (value === '~' || value.startsWith('~/')) value = lib.join(home, value.slice(2))
  if (platform === 'win32' && /^\/[a-zA-Z](\/|$)/.test(value)) value = `${value[1].toUpperCase()}:\\${value.slice(2)}`
  return lib.resolve(base, value)
}

/**
 * 前に付けたコマンドのオプションを読み飛ばす。
 *
 * @param {string[]} words コマンドの語
 * @param {number} start オプションの始まりの位置
 * @param {{ valueShort: string, optionalShort?: string, valueLong: string[], operands?: number }} spec オプション
 * @returns {{ index: number, values: { option: string, value: string }[] }} 後ろのコマンドの位置と、値を取ったオプション
 */
function skipPrefixOptions(words, start, { valueShort, optionalShort = '', valueLong, operands = 0 }) {
  const values = []
  let i = start
  // オプションを、値を取るものは値と一緒に、位置引数の手前まで読む
  while (i < words.length) {
    const word = words[i]
    if (word === '--') {
      i++
      break
    }
    if (word === '-') {
      i++
      continue
    }
    if (!word.startsWith('-')) break
    if (word.startsWith('--')) {
      const [name, ...value] = word.slice(2).split('=')
      if (value.length > 0) {
        values.push({ option: name, value: value.join('=') })
      } else if (valueLong.includes(name)) {
        values.push({ option: name, value: words[i + 1] ?? '' })
        i++
      }
    } else {
      for (let j = 1; j < word.length; j++) {
        const option = word[j]
        if (valueShort.includes(option)) {
          const rest = word.slice(j + 1)
          values.push({ option, value: rest !== '' ? rest : (words[i + 1] ?? '') })
          if (rest === '') i++
          break
        }
        if (optionalShort.includes(option)) break
      }
    }
    i++
  }
  return { index: Math.min(i + operands, words.length), values }
}

/**
 * コマンドの前に付けた環境変数の設定、シェルの予約語（do、then など）、後ろの語を実行させるコマンド（env、timeout など）を外す。
 * env -C はディレクトリを、env -S は語を替える。
 *
 * @param {string[]} raw コマンドの語
 * @param {string} dir 実行するディレクトリ
 * @param {PathOptions} options パスの解き方
 * @returns {{ words: string[], dir: string, env: Record<string, string>, ghRepo: string | null }} 実行するコマンドの語、ディレクトリ、
 *   対象のリポジトリを替える環境変数、gh の対象のリポジトリ（前に付けた GH_REPO。なければ null）
 */
function stripPrefixes(raw, dir, options) {
  let words = raw
  let current = dir
  const assigned = {}
  // 環境変数の設定、予約語、前に付けるコマンドがなくなるまで外す
  while (words.length > 0) {
    const assignment = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/s.exec(words[0])
    if (assignment) {
      assigned[assignment[1]] = assignment[2]
      words = words.slice(1)
      continue
    }
    if (SHELL_KEYWORDS.includes(words[0])) {
      words = words.slice(1)
      continue
    }
    // function f { … } は、{ の後ろを本体のコマンドとして読む。{ がなければ（function f() の形）定義の名前だけなので、語を残さない
    // （function f() { … } の本体は、括弧で区切られた後の { が予約語として外れる）
    if (words[0] === 'function') {
      const brace = words.indexOf('{')
      words = brace < 0 ? [] : words.slice(brace + 1)
      continue
    }
    const spec = PREFIX_COMMANDS[words[0]]
    if (!spec) break
    const { index, values } = skipPrefixOptions(words, 1, spec)
    let split = []
    for (const { option, value } of values) {
      if (words[0] === 'env' && (option === 'C' || option === 'chdir')) current = resolveDir(current, value, options)
      if (words[0] === 'env' && (option === 'S' || option === 'split-string')) split = splitCommands(value)[0] ?? []
    }
    words = [...split, ...words.slice(index)]
  }
  const env = {}
  for (const name of REPOSITORY_ENV) {
    if (assigned[name] !== undefined && assigned[name] !== '') env[name] = resolveDir(current, assigned[name], options)
  }
  const ghRepo = assigned[GH_REPOSITORY_ENV] ? assigned[GH_REPOSITORY_ENV] : null
  return { words, dir: current, env, ghRepo }
}

/**
 * cd・pushd・popd を実行した後のディレクトリを返す。それ以外のコマンドでは、今のディレクトリを返す。
 * pushd は前のディレクトリを `stack` に積み（先がなければ、積んだ最後のディレクトリと入れ替え）、popd は積んだ最後のディレクトリに戻す。
 *
 * @param {string[]} words コマンドの語
 * @param {string} dir 今のディレクトリ
 * @param {string[]} stack pushd で積んだディレクトリ（変える）
 * @param {PathOptions} options パスの解き方
 * @returns {string} 後のディレクトリ
 */
function changeDir(words, dir, stack, options) {
  const [name, ...args] = words
  // popd +1 などの引数のある形は、どのディレクトリに移るかを追わないので、今のままにする
  if (name === 'popd') return args.length === 0 && stack.length > 0 ? stack.pop() : dir
  if (name !== 'cd' && name !== 'pushd') return dir
  const target = args.find((w) => !/^-[LPe@]+$/.test(w))
  if (name === 'pushd' && target === undefined) {
    if (stack.length === 0) return dir
    const top = stack.pop()
    stack.push(dir)
    return top
  }
  if (name === 'pushd') stack.push(dir)
  // cd - は前のディレクトリに戻るが、前のディレクトリは追わないので、今のままにする
  if (target === '-') return dir
  // 先が変数かコマンドの置き換え（$D、$(…)、`…`）を含むときは、値を読めないので、今のままにする。
  // 文字どおりのパスにすると、存在しないディレクトリで判定し、リポジトリの中の通常の git commit まで拒否するため
  if (target !== undefined && /[$`]/.test(target)) return dir
  return resolveDir(dir, target ?? '~', options)
}

/**
 * コマンドの全文を、コマンドごとの語と、実行するディレクトリに分ける。
 * ディレクトリは入力の cwd から始め、cd・pushd・popd の後のコマンドでは、移った先にする。
 *
 * @param {string} command コマンドの全文
 * @param {string} cwd 入力の cwd
 * @param {PathOptions} [options] パスの解き方
 * @returns {{ words: string[], dir: string, env: Record<string, string>, ghRepo: string | null, expandedWords: Set<string> }[]} コマンドごとの語
 *   （前に付けた環境変数の設定などを除いたもの）、ディレクトリ、対象のリポジトリを替える環境変数、gh の対象のリポジトリ（前に付けた GH_REPO）、
 *   シェルが展開する部分を含む語
 */
export function parseCommands(command, cwd, options = {}) {
  let dir = resolveDir(process.cwd(), cwd, options)
  const stack = []
  const commands = []
  for (const raw of splitCommands(command)) {
    const { words, dir: commandDir, env, ghRepo } = stripPrefixes(raw, dir, options)
    if (words.length === 0) continue
    commands.push({ words, dir: commandDir, env, ghRepo, expandedWords: EXPANDED_WORDS.get(raw) ?? new Set() })
    dir = changeDir(words, dir, stack, options)
  }
  return commands
}
