// 複合の action（action.yml）を置いたディレクトリが、Dependabot の github-actions の対象（.github/dependabot.yml）に入っていることを確認する。
// Dependabot の `/` は .github/workflows とルートの action.yml だけを見るので、`.github/actions/<名前>/` の中の `uses:` は、
// そのディレクトリを `directories` に加えなければ更新されない。加え忘れると、action のバージョンが古いまま残る。
// `directory`・`directories` の節に、知っている書き方に当たらない行があるときも失敗にする（読み飛ばすと、対象を見落として通るため）。
// 使い方：node scripts/ci/dependabot-actions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** Dependabot の設定 */
const DEPENDABOT_FILE = '.github/dependabot.yml'

/** 値の前後の引用符と、行末のコメントを外す */
const cleanValue = (text) =>
  text
    .replace(/\s+#.*$/, '')
    .trim()
    .replace(/^["']|["']$/g, '')

/**
 * dependabot.yml の github-actions の節（`- package-ecosystem: github-actions` から次の `- package-ecosystem:` まで）を読む。<br />
 * YAML のライブラリを使わず、`directory: 値` と、`directories:` の下の字下げのそろった `- 値` の項目だけを読む。
 * 読む節（`directory`、`directories` の行と、その下の行）の中で、知っている書き方に当たらない行
 * （フロー形式の `directories: [...]`、値のない `directory:`、項目でない行、字下げのそろわない項目）は、読み飛ばさずに `unknown` に入れる。
 * 空行とコメントは除く。
 *
 * @param {string} text dependabot.yml の内容
 * @returns {{ directories: string[], unknown: { line: number, text: string }[] }} 対象のディレクトリ（書いた順）と、知らない書き方の行
 */
function parseActionsBlock(text) {
  const directories = []
  const unknown = []
  let inBlock = false
  let listIndent = null
  let itemIndent = null
  text.split('\n').forEach((raw, index) => {
    const line = raw.replace(/\r$/, '')
    if (line.trim() === '' || /^\s*#/.test(line)) {
      return
    }
    const ecosystem = /^\s*-\s+package-ecosystem:\s*(.+)$/.exec(line)
    if (ecosystem) {
      inBlock = cleanValue(ecosystem[1]) === 'github-actions'
      listIndent = null
      return
    }
    if (!inBlock) {
      return
    }
    const lineIndent = /^\s*/.exec(line)[0].length
    if (listIndent !== null) {
      const item = /^(\s*)-\s+(.+)$/.exec(line)
      if (item && item[1].length >= listIndent && (itemIndent === null || item[1].length === itemIndent)) {
        itemIndent = item[1].length
        directories.push(cleanValue(item[2]))
        return
      }
      if (lineIndent > listIndent || (item && item[1].length >= listIndent)) {
        unknown.push({ line: index + 1, text: line })
        return
      }
      listIndent = null
    }
    const single = /^\s+directory:\s*(.*)$/.exec(line)
    if (single) {
      const value = cleanValue(single[1])
      if (value === '') {
        unknown.push({ line: index + 1, text: line })
      } else {
        directories.push(value)
      }
      return
    }
    const list = /^(\s+)directories:(.*)$/.exec(line)
    if (list) {
      if (cleanValue(list[2]) === '') {
        listIndent = list[1].length
        itemIndent = null
      } else {
        unknown.push({ line: index + 1, text: line })
      }
    }
  })
  return { directories, unknown }
}

/**
 * dependabot.yml の github-actions の `directory` と `directories` を読む。
 *
 * @param {string} text dependabot.yml の内容
 * @returns {string[]} 対象のディレクトリ（書いた順）
 */
export function readActionsDirectories(text) {
  return parseActionsBlock(text).directories
}

/**
 * dependabot.yml の github-actions の節で、知っている書き方に当たらない行を探す。
 *
 * @param {string} text dependabot.yml の内容
 * @returns {{ line: number, text: string }[]} 知らない書き方の行（行の順）
 */
export function findUnknownActionLines(text) {
  return parseActionsBlock(text).unknown
}

/**
 * 知らない書き方の行を、失敗の文にする。
 *
 * @param {string} file 表示する dependabot.yml のパス
 * @param {{ line: number, text: string }} entry `findUnknownActionLines` が見つけた行
 * @returns {string} `ファイル:行 内容（理由）`
 */
export function describeUnknownActionLine(file, entry) {
  return describeUnreadableLine({ file, line: entry.line, text: entry.text, reason: '知らない書き方。検査の読み方を追加するか、directories を 1 行ずつの項目で書く' })
}

/**
 * Git の対象のファイルから、action.yml（または action.yaml）を置いたディレクトリを返す。
 *
 * @param {string[]} files リポジトリのルートからの相対パス（/ 区切り）
 * @returns {string[]} 先頭に `/` を付けたディレクトリ（ルートは `/`）。名前の順
 */
export function findActionDirectories(files) {
  const directories = new Set()
  for (const file of files) {
    const match = /^(?:(.*)\/)?action\.ya?ml$/.exec(file)
    if (match) {
      directories.add(`/${match[1] ?? ''}`)
    }
  }
  return [...directories].sort()
}

/**
 * Dependabot の対象が、ディレクトリを含むか。`*` は 1 つの階層の中の任意の文字として扱う。`/` はルートだけで、下の階層を含まない。
 *
 * @param {string} target 対象の書き方
 * @param {string} directory ディレクトリ
 * @returns {boolean} 含めば true
 */
function covers(target, directory) {
  const pattern = target.split('*').map((part) => part.replace(/[.+?^${}()|[\]\\]/g, '\\$&'))
  return new RegExp(`^${pattern.join('[^/]*')}$`).test(directory)
}

/**
 * Dependabot の対象に入っていない action のディレクトリを探す。
 *
 * @param {string[]} actionDirectories action.yml を置いたディレクトリ
 * @param {string[]} targets Dependabot の github-actions の対象
 * @returns {string[]} 入っていないディレクトリの説明
 */
export function findUncoveredActions(actionDirectories, targets) {
  return actionDirectories
    .filter((directory) => !targets.some((target) => covers(target, directory)))
    .map((directory) => `${DEPENDABOT_FILE} の github-actions の directories にない（加える）：${directory}`)
}

if (isMain(import.meta.url)) {
  const files = listGitFiles()
  exitIfNoFiles(files, 'Git のファイル')
  exitIfNoFiles(files.filter((file) => file === DEPENDABOT_FILE), 'Dependabot の設定')
  const actionDirectories = findActionDirectories(files)
  const dependabot = readFileSync(DEPENDABOT_FILE, 'utf8')
  const targets = readActionsDirectories(dependabot)
  const problems = [
    ...findUnknownActionLines(dependabot).map((entry) => describeUnknownActionLine(DEPENDABOT_FILE, entry)),
    ...findUncoveredActions(actionDirectories, targets),
  ]
  if (targets.length === 0) {
    problems.push(`${DEPENDABOT_FILE} に github-actions の対象がない`)
  }
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`複合の action：${actionDirectories.length} 件、Dependabot の対象：${targets.length} 件、問題：${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
