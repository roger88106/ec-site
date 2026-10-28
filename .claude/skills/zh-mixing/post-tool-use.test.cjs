// Claude Code の書き込みの後の hook の入口（post-tool-use.cjs）のテスト
// 中国語の字は、ファイルに書かず、コードポイントから作る。環境の印は注入した値で試し、利用者の git の設定は読まない。
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { execFileSync, spawnSync } = require('child_process')
const hook = require('./post-tool-use.cjs')

const SIMPLIFIED = String.fromCodePoint(0x8fd9)
const BAD = `この${SIMPLIFIED}は混入した行。`
const GOOD = 'この関数は、注文の合計を返す。'
const SILENT = { code: 0, stderr: '' }

function cleanEnv() {
  return Object.fromEntries(Object.entries(process.env).filter(([name]) => !name.startsWith('GIT_')))
}

/** 一時のディレクトリを作り、リポジトリのルートと home に見立てて fn を実行する */
function withDirs(fn) {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), 'zh-hook-'))
  try {
    const root = path.join(base, 'repo')
    const home = path.join(base, 'home')
    fs.mkdirSync(root)
    fs.mkdirSync(home)
    return fn({ root, home, base })
  } finally {
    fs.rmSync(base, { recursive: true, force: true })
  }
}

function put(file, body) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, body)
  return file
}

function makeDeps({ root, home, enabled = true }) {
  return {
    getConfig: () => (enabled ? 'zh' : undefined),
    readFile: (file) => fs.readFileSync(file, 'utf8'),
    repoRoot: () => root,
    homeDir: () => home,
  }
}

function input(file, cwd) {
  return { hook_event_name: 'PostToolUse', tool_name: 'Write', tool_input: { file_path: file }, cwd }
}

test('リポジトリのファイルに字があれば、終了コード 2 で、ファイル・行・語を返す', () => {
  withDirs((d) => {
    const file = put(path.join(d.root, 'docs', 'a.md'), `${GOOD}\n${BAD}\n`)
    const r = hook.handle(input(file, d.root), makeDeps(d))
    assert.equal(r.code, 2)
    assert.match(r.stderr, /a\.md:2/)
    assert.ok(r.stderr.includes(SIMPLIFIED))
    assert.match(r.stderr, /日本語に直/)
  })
})

test('字がないファイルは何も返さない', () => {
  withDirs((d) => {
    const file = put(path.join(d.root, 'a.md'), `${GOOD}\n`)
    assert.deepEqual(hook.handle(input(file, d.root), makeDeps(d)), SILENT)
  })
})

test('環境の印がなければ、字があっても何も返さない', () => {
  withDirs((d) => {
    const file = put(path.join(d.root, 'a.md'), BAD)
    assert.deepEqual(hook.handle(input(file, d.root), makeDeps({ ...d, enabled: false })), SILENT)
  })
})

test('memory のファイルは調べる', () => {
  withDirs((d) => {
    const file = put(path.join(d.home, '.claude', 'projects', 'proj', 'memory', 'm.md'), BAD)
    const r = hook.handle(input(file, d.root), makeDeps(d))
    assert.equal(r.code, 2)
    assert.match(r.stderr, /m\.md:1/)
  })
})

test('/.local/ の下、リポジトリの外の他のファイル、memory 以外の .claude の下は調べない', () => {
  withDirs((d) => {
    const files = [
      path.join(d.root, '.local', 'agent', 'x.md'),
      path.join(d.base, 'other', 'x.md'),
      path.join(d.home, '.claude', 'projects', 'proj', 'x.jsonl'),
      path.join(d.home, 'memory', 'x.md'),
    ]
    for (const f of files) {
      put(f, BAD)
      assert.deepEqual(hook.handle(input(f, d.root), makeDeps(d)), SILENT, f)
    }
  })
})

test('ファイルのパスがない入力と、読めないファイルは何も返さない', () => {
  withDirs((d) => {
    const deps = makeDeps(d)
    assert.deepEqual(hook.handle({ tool_name: 'Bash', tool_input: { command: 'ls' }, cwd: d.root }, deps), SILENT)
    assert.deepEqual(hook.handle({}, deps), SILENT)
    assert.deepEqual(hook.handle(input(path.join(d.root, 'none.md'), d.root), deps), SILENT)
  })
})

test('相対のパスは cwd から解く', () => {
  withDirs((d) => {
    put(path.join(d.root, 'a.md'), BAD)
    assert.equal(hook.handle(input('a.md', d.root), makeDeps(d)).code, 2)
  })
})

test('入口の script：標準入力の JSON を読み、一時のリポジトリの印で動く（終了コードと標準エラー）', () => {
  withDirs((d) => {
    execFileSync('git', ['init', '-q'], { cwd: d.root, env: cleanEnv() })
    const bad = put(path.join(d.root, 'bad.md'), BAD)
    const good = put(path.join(d.root, 'good.md'), GOOD)
    const run = (file) =>
      spawnSync('node', [path.join(__dirname, 'post-tool-use.cjs')], {
        cwd: d.root,
        env: { ...cleanEnv(), HOME: d.home, USERPROFILE: d.home },
        input: JSON.stringify(input(file, d.root)),
        encoding: 'utf8',
      })
    assert.equal(run(bad).status, 0, '印がなければ何も返さない')
    execFileSync('git', ['config', '--local', 'ecsite.instructionLanguage', 'zh'], { cwd: d.root, env: cleanEnv() })
    const r = run(bad)
    assert.equal(r.status, 2)
    assert.match(r.stderr, /bad\.md:1/)
    const g = run(good)
    assert.deepEqual([g.status, g.stderr], [0, ''])
  })
})

test('リポジトリの外で呼ばれても、入口は失敗せず、何も出さない（利用者の設定に置いたとき）', () => {
  withDirs((d) => {
    const outside = path.join(d.base, 'outside')
    fs.mkdirSync(outside)
    const file = put(path.join(outside, 'x.md'), BAD)
    const env = { ...cleanEnv(), HOME: d.home, USERPROFILE: d.home, GIT_CEILING_DIRECTORIES: d.base }
    const entry = spawnSync('node', [path.join(__dirname, 'post-tool-use.cjs')], { cwd: outside, env, input: JSON.stringify(input(file, outside)), encoding: 'utf8' })
    assert.deepEqual([entry.status, entry.stdout, entry.stderr], [0, '', ''])
    for (const args of [['staged'], ['file', file], ['tracked']]) {
      const r = spawnSync('node', [path.join(__dirname, 'zh-mixing.cjs'), ...args], { cwd: outside, env, encoding: 'utf8' })
      assert.deepEqual([r.status, r.stdout, r.stderr], [0, '', ''], args.join(' '))
    }
  })
})
