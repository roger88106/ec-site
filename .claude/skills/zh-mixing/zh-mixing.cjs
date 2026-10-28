// 日本語の文書・コメントに、中国語の字・用語が混ざっていないかを調べる（層 2。中国語で指示する環境だけで有効）
// 使い方（リポジトリのルートで）：
//   node .claude/skills/zh-mixing/zh-mixing.cjs file <パス>...     … 指定したファイルを調べる
//   node .claude/skills/zh-mixing/zh-mixing.cjs staged             … ステージした追加行を調べる（git の pre-commit）
//   node .claude/skills/zh-mixing/zh-mixing.cjs message <ファイル> … コミットメッセージのファイルを調べる（git の commit-msg）
//   node .claude/skills/zh-mixing/zh-mixing.cjs tracked            … Git が追跡しているファイル全体を調べる（アーカイブした spec を除く。較正用）
//   --force を付けると、環境の印がなくても調べる（較正用）
// 環境の印：git config --local ecsite.instructionLanguage の値が zh のときだけ調べる。印がなければ、何も調べず何も出さず、終了コード 0。
// 見つかれば終了コード 1 で、ファイル・行・語を標準エラー出力に出す。使い方の誤りは 2。
// 字・用語の一覧と許す語は zh-mixing.json。Git の外の許す語は .local/agent/zh-allow.txt（「語」か「ファイルのパス<TAB>語」を 1 行ずつ）
// 層 1（scripts/ci/ja-text.json）にある語は、ここに置かない（同じ語を 2 つの層に置かない）
const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')

const LIST_PATH = path.join(__dirname, 'zh-mixing.json')
const CONFIG_KEY = 'ecsite.instructionLanguage'
const ENABLED_VALUE = 'zh'
const LOCAL_ALLOW = '.local/agent/zh-allow.txt'
/** 追跡しているファイルを調べるとき、見ないもの（アーカイブした spec は当時の記録） */
const TRACKED_EXCLUDED = /^docs\/specs\/archive\//
const COMMIT_MSG = 'COMMIT_MSG'

/**
 * GIT_ で始まる環境変数を外した環境を返す。commit の前の hook の中で git を呼ぶと、試す一時リポジトリでなく commit 中のリポジトリを指すため。
 * @param {Record<string, string | undefined>} [env] 元の環境
 * @returns {Record<string, string | undefined>}
 */
function withoutGitEnv(env = process.env) {
  return Object.fromEntries(Object.entries(env).filter(([name]) => !name.startsWith('GIT_')))
}

/**
 * 一覧の形を確認する。許す語には理由が必要だ。
 * @param {{ chars: string | string[], words: string[], allow?: { word: string, file?: string, reason?: string }[] }} raw 読んだ JSON
 * @returns {{ chars: string[], words: string[], allow: { word: string, file?: string, reason: string }[] }} 字を 1 字ずつにした一覧
 */
function checkList(raw) {
  const allow = raw.allow ?? []
  for (const entry of allow) {
    if (!entry.word) throw new Error('許す語に word がない')
    if (!entry.reason) throw new Error(`許す語に理由がない：${entry.word}`)
  }
  return { chars: [...raw.chars], words: [...raw.words], allow }
}

/**
 * 字・用語の一覧を読む。
 * @param {string} [file] 一覧の JSON
 */
function loadList(file = LIST_PATH) {
  return checkList(JSON.parse(fs.readFileSync(file, 'utf8')))
}

/**
 * Git の外の許す語のファイルを読む。
 * @param {string} text 「語」か「ファイルのパス<TAB>語」を 1 行ずつ。# で始まる行と空行は読み飛ばす
 * @returns {{ word: string, file?: string }[]}
 */
function parseLocalAllow(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const tab = line.indexOf('\t')
      return tab < 0 ? { word: line } : { file: line.slice(0, tab).trim(), word: line.slice(tab + 1).trim() }
    })
}

/**
 * 1 行から、中国語の字・用語を見つける（許す語を除く）。
 * @param {string} text 行
 * @param {{ chars: string[], words: string[], allow: { word: string, file?: string }[] }} list 一覧
 * @param {string} [file] 行があるファイル（ファイルを指定した許す語の判定に使う）
 * @param {{ word: string, file?: string }[]} [extraAllow] Git の外の許す語
 * @returns {string[]} 見つけた字・用語（重複なし）
 */
function findWords(text, list, file = '', extraAllow = []) {
  const found = new Set()
  for (const c of text) if (list.chars.includes(c)) found.add(c)
  for (const w of list.words) if (text.includes(w)) found.add(w)
  const allowed = [...list.allow, ...extraAllow]
  return [...found].filter((w) => !allowed.some((a) => a.word === w && (a.file === undefined || a.file === file)))
}

/**
 * 行の並びを調べ、見つかった行を返す。
 * @param {{ file: string, line: number, text: string }[]} lines 調べる行
 * @param {object} list 一覧
 * @param {{ word: string, file?: string }[]} [extraAllow] Git の外の許す語
 * @returns {{ file: string, line: number, text: string, words: string[] }[]}
 */
function scanLines(lines, list, extraAllow = []) {
  const hits = []
  for (const l of lines) {
    const words = findWords(l.text, list, l.file, extraAllow)
    if (words.length > 0) hits.push({ ...l, words })
  }
  return hits
}

/**
 * ファイルの本文を、行の並びにする。
 * @param {string} file ファイルのパス
 * @param {string} body 本文
 */
function fileLines(file, body) {
  return body.split(/\r?\n/).map((text, i) => ({ file, line: i + 1, text }))
}

/**
 * `git diff --cached -U0` の出力から、追加した行（ファイル・行番号・本文）を読む。
 * ハンクの見出し（@@）の後の `+` の行だけを本文とする（本文が `++` で始まる行を見出しと取り違えない）。
 * @param {string} diff diff の出力
 * @returns {{ file: string, line: number, text: string }[]}
 */
function parseAddedLines(diff) {
  const out = []
  let file = ''
  let line = 0
  let inHunk = false
  for (const l of diff.split('\n')) {
    if (l.startsWith('diff --git ')) {
      inHunk = false
      continue
    }
    if (!inHunk && l.startsWith('+++ ')) {
      file = l.slice(4) === '/dev/null' ? '' : l.slice(4).replace(/^b\//, '')
      continue
    }
    const hunk = l.match(/^@@ -\S+ \+(\d+)/)
    if (hunk) {
      line = Number(hunk[1])
      inHunk = true
      continue
    }
    if (inHunk && l.startsWith('+')) {
      out.push({ file, line, text: l.slice(1) })
      line++
    }
  }
  return out
}

/**
 * コミットメッセージを、調べる行にする。# で始まる行と、はさみの行（# ---- >8 ----）以降は除く。
 * @param {string} message メッセージのファイルの本文
 */
function messageLines(message) {
  const out = []
  const lines = message.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    if (/^# -+ >8 -+$/.test(lines[i])) break
    if (lines[i].startsWith('#')) continue
    out.push({ file: COMMIT_MSG, line: i + 1, text: lines[i] })
  }
  return out
}

/**
 * 環境の印があるかを返す。
 * @param {(key: string) => string | undefined} getConfig git の設定を読む関数（テストで差し替える）
 */
function isEnabled(getConfig) {
  return (getConfig(CONFIG_KEY) ?? '').trim() === ENABLED_VALUE
}

/**
 * git の設定（ローカル）を読む。なければ undefined。GIT_ で始まる環境変数を外して呼ぶ。
 * @param {string} key 設定の名前
 * @param {{ cwd?: string, env?: Record<string, string | undefined> }} [options]
 */
function readConfig(key, { cwd = process.cwd(), env = withoutGitEnv() } = {}) {
  try {
    return execFileSync('git', ['config', '--local', '--get', key], { cwd, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch {
    return undefined
  }
}

/**
 * ステージした差分を読む。読むだけで書かない。
 * hook の中では hook の環境（GIT_DIR、GIT_INDEX_FILE）を使う（既定は process.env のまま）。テストは外した環境を渡す。
 * @param {{ cwd?: string, env?: Record<string, string | undefined> }} [options]
 */
function stagedDiff({ cwd = process.cwd(), env = process.env } = {}) {
  return execFileSync(
    'git',
    ['-c', 'core.quotepath=off', 'diff', '--cached', '-U0', '--no-color', '--src-prefix=a/', '--dst-prefix=b/', '--diff-filter=AM'],
    { cwd, env, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 },
  )
}

/** Git が追跡しているファイルの一覧 */
function trackedFiles({ cwd = process.cwd(), env = withoutGitEnv() } = {}) {
  return execFileSync('git', ['ls-files', '-z'], { cwd, env, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).split('\0').filter(Boolean)
}

/**
 * Git の外の許す語のファイルを読む。作業ツリーになければ、主の作業ツリー（共通の .git の親）を探す。なければ空。
 * @returns {{ word: string, file?: string }[]}
 */
function readLocalAllow({ cwd = process.cwd(), env = withoutGitEnv() } = {}) {
  const git = (...args) => execFileSync('git', args, { cwd, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  const candidates = []
  try {
    candidates.push(path.join(git('rev-parse', '--show-toplevel'), LOCAL_ALLOW))
    candidates.push(path.join(path.dirname(path.resolve(cwd, git('rev-parse', '--git-common-dir'))), LOCAL_ALLOW))
  } catch {
    // リポジトリの外では、許す語のファイルを探さない
  }
  const found = candidates.find((file) => fs.existsSync(file))
  return found ? parseLocalAllow(fs.readFileSync(found, 'utf8')) : []
}

/** 本物の入出力の deps */
function defaultDeps() {
  return {
    getConfig: (key) => readConfig(key),
    readFile: (file) => fs.readFileSync(file, 'utf8'),
    listTracked: () => trackedFiles(),
    stagedDiff: () => stagedDiff(),
    localAllow: () => readLocalAllow(),
    write: (stream, text) => (stream === 'err' ? process.stderr : process.stdout).write(text),
  }
}

/**
 * 見つかった行を出力の文にする。
 * @param {{ file: string, line: number, text: string, words: string[] }[]} hits
 */
function formatHits(hits) {
  const rows = hits.map((h) => `${h.file}:${h.line}  [${h.words.join(' ')}]  ${h.text.trim().slice(0, 80)}`)
  return [
    `中国語の字・用語の混入の疑い：${hits.length} 行`,
    ...rows,
    '誤検出なら、.claude/skills/zh-mixing/zh-mixing.json の allow に理由を付けて書く（手元だけの除外は .local/agent/zh-allow.txt）。',
    '',
  ].join('\n')
}

const USAGE = '使い方：node .claude/skills/zh-mixing/zh-mixing.cjs [--force] file <パス>... | staged | message <ファイル> | tracked\n'

/**
 * 入口。
 * @param {string[]} argv 引数
 * @param {ReturnType<typeof defaultDeps>} deps 入出力（テストで差し替える）
 * @returns {number} 終了コード（0：成功、1：見つかった、2：使い方の誤り）
 */
function run(argv, deps) {
  const force = argv.includes('--force')
  const [mode, ...rest] = argv.filter((arg) => arg !== '--force')
  const modes = { file: rest.length > 0, staged: true, message: rest.length === 1, tracked: true }
  if (!modes[mode]) {
    deps.write('err', USAGE)
    return 2
  }
  if (!force && !isEnabled(deps.getConfig)) return 0

  const list = loadList()
  const extraAllow = deps.localAllow()
  let lines
  if (mode === 'file') {
    lines = rest.flatMap((file) => fileLines(file.replace(/\\/g, '/'), deps.readFile(file)))
  } else if (mode === 'staged') {
    lines = parseAddedLines(deps.stagedDiff())
  } else if (mode === 'message') {
    lines = messageLines(deps.readFile(rest[0]))
  } else {
    lines = deps
      .listTracked()
      .filter((file) => !TRACKED_EXCLUDED.test(file))
      .flatMap((file) => {
        let body
        try {
          body = deps.readFile(file)
        } catch {
          return []
        }
        return body.includes('\0') ? [] : fileLines(file, body)
      })
  }
  const hits = scanLines(lines, list, extraAllow)
  if (hits.length === 0) return 0
  deps.write('err', formatHits(hits))
  return 1
}

module.exports = {
  CONFIG_KEY,
  checkList,
  loadList,
  parseLocalAllow,
  findWords,
  scanLines,
  parseAddedLines,
  messageLines,
  isEnabled,
  readConfig,
  stagedDiff,
  run,
  formatHits,
}

if (require.main === module) {
  process.exit(run(process.argv.slice(2), defaultDeps()))
}
