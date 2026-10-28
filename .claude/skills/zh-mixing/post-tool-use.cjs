// Claude Code の PostToolUse の hook の入口。Write・Edit などで書いたファイルを調べ、中国語の字・用語があればエージェントに返す（層 2）。
// 入力：標準入力の JSON（tool_input.file_path、cwd）。
// 出力：見つかれば終了コード 2 と標準エラー（Claude Code がエージェントに返す）。何もなければ終了コード 0 で何も出さない。
// 調べる対象：リポジトリのファイルと、memory のファイル（~/.claude/projects/<プロジェクト>/memory/ の下）。
// 調べないもの：/.local/ の下、リポジトリの外の他のファイル、ファイルのパスのない入力。環境の印がなければ何もしない。
// 設定の手順は SKILL.md。
const fs = require('fs')
const os = require('os')
const path = require('path')
const { execFileSync } = require('child_process')
const zh = require('./zh-mixing.cjs')

/** 親の下にあるか（親と同じパスは含まない） */
function isInside(parent, child) {
  const rel = path.relative(parent, child)
  return rel !== '' && !rel.startsWith('..') && !path.isAbsolute(rel)
}

/** 調べる対象のファイルか */
function isTarget(file, repoRoot, home) {
  if (repoRoot && isInside(repoRoot, file)) {
    const rel = path.relative(repoRoot, file).split(path.sep)
    return rel[0] !== '.local'
  }
  const projects = path.join(home, '.claude', 'projects')
  if (!isInside(projects, file)) return false
  const rel = path.relative(projects, file).split(path.sep)
  return rel.length >= 3 && rel[1] === 'memory'
}

/**
 * PostToolUse の入力を調べる。
 * @param {{ tool_input?: { file_path?: unknown }, cwd?: string }} input 標準入力の JSON
 * @param {{ getConfig: (cwd: string, key: string) => string | undefined, readFile: (file: string) => string, repoRoot: (cwd: string) => string | undefined, homeDir: () => string }} deps 入出力（テストで差し替える）
 * @returns {{ code: number, stderr: string }}
 */
function handle(input, deps) {
  const silent = { code: 0, stderr: '' }
  const given = input?.tool_input?.file_path
  if (typeof given !== 'string' || given === '') return silent
  const cwd = typeof input.cwd === 'string' && input.cwd !== '' ? input.cwd : process.cwd()
  if (!zh.isEnabled((key) => deps.getConfig(cwd, key))) return silent

  const file = path.resolve(cwd, given)
  const root = deps.repoRoot(cwd)
  if (!isTarget(file, root && path.resolve(root), deps.homeDir())) return silent
  let body
  try {
    body = deps.readFile(file)
  } catch {
    return silent
  }
  if (body.includes('\0')) return silent

  const shown = root && isInside(path.resolve(root), file) ? path.relative(path.resolve(root), file).split(path.sep).join('/') : file.split(path.sep).join('/')
  const errors = []
  const code = zh.run(['--force', 'file', shown], {
    getConfig: () => 'zh',
    readFile: () => body,
    localAllow: () => deps.localAllow?.(cwd) ?? [],
    write: (_stream, text) => errors.push(text),
  })
  if (code === 0) return silent
  return { code: 2, stderr: `${errors.join('')}書いたファイルの該当の行を、日本語に直す（コードの識別子は変えない）。\n` }
}

/** 本物の入出力の deps */
function defaultDeps() {
  const env = zh.withoutGitEnv()
  return {
    getConfig: (cwd, key) => zh.readConfig(key, { cwd, env }),
    readFile: (file) => fs.readFileSync(file, 'utf8'),
    repoRoot: (cwd) => {
      try {
        return execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
      } catch {
        return undefined
      }
    },
    localAllow: (cwd) => zh.readLocalAllow({ cwd, env }),
    homeDir: () => os.homedir(),
  }
}

module.exports = { handle, isTarget }

if (require.main === module) {
  let text = ''
  try {
    text = fs.readFileSync(0, 'utf8')
  } catch {
    // 標準入力がなければ何もしない
  }
  let input = {}
  try {
    input = JSON.parse(text)
  } catch {
    // JSON でなければ何もしない
  }
  const result = handle(input, defaultDeps())
  if (result.stderr) process.stderr.write(result.stderr)
  process.exit(result.code)
}
