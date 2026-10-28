// 中国語の混入の検査（zh-mixing.cjs）のテスト
// 中国語の字は、ファイルに書かず、コードポイントや一覧から作る（commit の前の検査が、中国語の字を含む追加行を止めるため）。
// 環境の印は、テストの中で注入した値で試す。利用者の環境の git の設定は読まない。
// git を呼ぶテストは、GIT_ で始まる環境変数を外した環境で、mkdtemp の一時のリポジトリだけで行う。
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { execFileSync } = require('child_process')
const zh = require('./zh-mixing.cjs')

const list = zh.loadList()
/** 許す語を空にした一覧。文書に字・語がないことは、許す語によらず調べる（DC-249-28、30） */
const strict = { ...list, allow: [] }
const ja = require('../../../scripts/ci/ja-text.json')

/** 簡体字の U+8FD9。日本語では使わない字 */
const SIMPLIFIED = String.fromCodePoint(0x8fd9)
/** 一覧の最初の用語 */
const FIRST_WORD = list.words[0]
/** 日本語の普通の文 */
const JAPANESE = 'この関数は、注文の合計を計算して返す。'

/** GIT_ で始まる環境変数を外した環境 */
function cleanEnv() {
  return Object.fromEntries(Object.entries(process.env).filter(([name]) => !name.startsWith('GIT_')))
}

/** 一時のリポジトリを作って、そこで fn を実行し、後で消す */
function withTempRepo(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'zh-mixing-'))
  try {
    execFileSync('git', ['init', '-q'], { cwd: dir, env: cleanEnv() })
    return fn(dir)
  } finally {
    fs.rmSync(dir, { recursive: true, force: true })
  }
}

/** 出力を集める deps を作る。読み書きを呼ぶと失敗する既定を持つ */
function makeDeps(overrides = {}) {
  const out = []
  const err = []
  return {
    out,
    err,
    deps: {
      getConfig: () => 'zh',
      readFile: () => { throw new Error('readFile を呼んではならない') },
      listTracked: () => { throw new Error('listTracked を呼んではならない') },
      stagedDiff: () => { throw new Error('stagedDiff を呼んではならない') },
      localAllow: () => [],
      write: (stream, text) => (stream === 'err' ? err : out).push(text),
      ...overrides,
    },
  }
}

// --- 字・用語の判定 ---

test('日本語の文は見つからない', () => {
  assert.deepEqual(zh.findWords(JAPANESE, list), [])
})

test('日本語では使わない字を見つける', () => {
  assert.deepEqual(zh.findWords(`a${SIMPLIFIED}b`, list), [SIMPLIFIED])
})

test('一覧の字は、全て見つかる', () => {
  for (const c of list.chars) assert.deepEqual(zh.findWords(`a${c}b`, list), [c], c.codePointAt(0).toString(16))
})

test('一覧の用語は、全て見つかる', () => {
  for (const w of list.words) assert.ok(zh.findWords(`a${w}b`, list).includes(w), w.codePointAt(0).toString(16))
})

test('同じ行の字と用語を、まとめて見つける', () => {
  const found = zh.findWords(`${FIRST_WORD}${SIMPLIFIED}`, list)
  assert.ok(found.includes(FIRST_WORD))
  assert.ok(found.includes(SIMPLIFIED))
})

// --- 許す語 ---

test('一覧の許す語は、見つけても数えない', () => {
  const custom = { ...list, allow: [{ word: SIMPLIFIED, reason: 'テスト' }] }
  assert.deepEqual(zh.findWords(`a${SIMPLIFIED}b`, custom, 'docs/a.md'), [])
})

test('ファイルを指定した許す語は、そのファイルだけで効く', () => {
  const custom = { ...list, allow: [{ word: SIMPLIFIED, file: 'docs/a.md', reason: 'テスト' }] }
  assert.deepEqual(zh.findWords(`a${SIMPLIFIED}b`, custom, 'docs/a.md'), [])
  assert.deepEqual(zh.findWords(`a${SIMPLIFIED}b`, custom, 'docs/b.md'), [SIMPLIFIED])
})

test('Git の外の許す語は、語だけでもファイルと語でも書ける', () => {
  const extra = zh.parseLocalAllow(['# 注釈', '', SIMPLIFIED, `docs/a.md\t${FIRST_WORD}`].join('\n'))
  assert.deepEqual(extra, [{ word: SIMPLIFIED }, { file: 'docs/a.md', word: FIRST_WORD }])
  assert.deepEqual(zh.findWords(`${SIMPLIFIED}`, list, 'x.md', extra), [])
  assert.deepEqual(zh.findWords(FIRST_WORD, list, 'docs/a.md', extra), [])
  assert.deepEqual(zh.findWords(FIRST_WORD, list, 'docs/b.md', extra), [FIRST_WORD])
})

test('許す語に理由がなければ、一覧の読み込みが失敗する', () => {
  assert.throws(() => zh.checkList({ chars: '', words: [], allow: [{ word: 'x' }] }), /理由/)
})

// --- 対象：行を集める ---

test('行ごとに、ファイル・行番号・語を返す', () => {
  const hits = zh.scanLines(
    [{ file: 'a.md', line: 1, text: JAPANESE }, { file: 'a.md', line: 2, text: `x${SIMPLIFIED}` }],
    list,
  )
  assert.deepEqual(hits.map((h) => [h.file, h.line, h.words]), [['a.md', 2, [SIMPLIFIED]]])
})

test('ステージした差分から、追加した行とその行番号を読む', () => {
  const diff = [
    'diff --git a/a.md b/a.md',
    'index 1..2 100644',
    '--- a/a.md',
    '+++ b/a.md',
    '@@ -1,0 +2,2 @@',
    '+一行目',
    '+++ 二行目',
    '@@ -9 +12 @@',
    '-古い',
    '+新しい',
    'diff --git a/gone.md b/gone.md',
    'deleted file mode 100644',
    '--- a/gone.md',
    '+++ /dev/null',
    '@@ -1 +0,0 @@',
    '-消えた',
  ].join('\n')
  assert.deepEqual(zh.parseAddedLines(diff), [
    { file: 'a.md', line: 2, text: '一行目' },
    { file: 'a.md', line: 3, text: '++ 二行目' },
    { file: 'a.md', line: 12, text: '新しい' },
  ])
})

test('コミットメッセージは、# で始まる行と、切り取り線の行以降を除く', () => {
  const message = ['件名', '# 注釈', '本文', '# ------------------------ >8 ------------------------', '差分の行', ''].join('\n')
  assert.deepEqual(zh.messageLines(message), [
    { file: 'COMMIT_MSG', line: 1, text: '件名' },
    { file: 'COMMIT_MSG', line: 3, text: '本文' },
  ])
})

// --- 環境の印 ---

test('値が zh のときだけ、調べる', () => {
  assert.equal(zh.isEnabled(() => 'zh'), true)
  assert.equal(zh.isEnabled(() => 'ja'), false)
  assert.equal(zh.isEnabled(() => ''), false)
  assert.equal(zh.isEnabled(() => undefined), false)
})

test('環境の印がなければ、何も調べず、何も出さず、成功する', () => {
  const { deps, out, err } = makeDeps({ getConfig: () => undefined })
  for (const argv of [['file', 'a.md'], ['staged'], ['message', 'm.txt'], ['tracked']]) {
    assert.equal(zh.run(argv, deps), 0, argv.join(' '))
  }
  assert.deepEqual([out, err], [[], []])
})

test('環境の印が zh でない値のときも、成功する', () => {
  const { deps } = makeDeps({ getConfig: () => 'ja' })
  assert.equal(zh.run(['file', 'a.md'], deps), 0)
})

test('--force は、環境の印がなくても調べる', () => {
  const { deps, err } = makeDeps({ getConfig: () => undefined, readFile: () => SIMPLIFIED })
  assert.equal(zh.run(['--force', 'file', 'a.md'], deps), 1)
  assert.ok(err.join('').includes('a.md:1'))
})

// --- 3 つの入口 ---

test('ファイルを指定して調べ、見つかれば 1、ファイル・行・語を出す', () => {
  const files = { 'a.md': `${JAPANESE}\n前${SIMPLIFIED}後\n`, 'b.md': JAPANESE }
  const { deps, err } = makeDeps({ readFile: (f) => files[f] })
  assert.equal(zh.run(['file', 'a.md', 'b.md'], deps), 1)
  const text = err.join('')
  assert.ok(text.includes(`a.md:2`))
  assert.ok(text.includes(SIMPLIFIED))
  assert.ok(!text.includes('b.md'))
})

test('ファイルを指定して調べ、見つからなければ 0', () => {
  const { deps, err } = makeDeps({ readFile: () => JAPANESE })
  assert.equal(zh.run(['file', 'a.md'], deps), 0)
  assert.deepEqual(err, [])
})

test('ステージした追加行を調べる', () => {
  const diff = ['diff --git a/a.md b/a.md', '--- a/a.md', '+++ b/a.md', '@@ -0,0 +1 @@', `+x${SIMPLIFIED}`].join('\n')
  const { deps, err } = makeDeps({ stagedDiff: () => diff })
  assert.equal(zh.run(['staged'], deps), 1)
  assert.ok(err.join('').includes('a.md:1'))
})

test('ステージした追加行に見つからなければ 0', () => {
  const diff = ['diff --git a/a.md b/a.md', '--- a/a.md', '+++ b/a.md', '@@ -0,0 +1 @@', `+${JAPANESE}`].join('\n')
  const { deps } = makeDeps({ stagedDiff: () => diff })
  assert.equal(zh.run(['staged'], deps), 0)
})

test('コミットメッセージのファイルを調べる', () => {
  const { deps, err } = makeDeps({ readFile: () => `件名\n# ${SIMPLIFIED}\n本文${SIMPLIFIED}\n` })
  assert.equal(zh.run(['message', 'COMMIT_EDITMSG'], deps), 1)
  assert.ok(err.join('').includes('COMMIT_MSG:3'))
  assert.ok(!err.join('').includes('COMMIT_MSG:2'))
})

test('Git の外の許す語は、入口の判定にも効く', () => {
  const { deps } = makeDeps({ readFile: () => SIMPLIFIED, localAllow: () => [{ word: SIMPLIFIED }] })
  assert.equal(zh.run(['file', 'a.md'], deps), 0)
})

test('追跡しているファイルを調べるとき、アーカイブした spec は除く', () => {
  const files = { 'docs/specs/archive/x/spec.md': SIMPLIFIED, 'docs/a.md': JAPANESE }
  const { deps } = makeDeps({ listTracked: () => Object.keys(files), readFile: (f) => files[f] })
  assert.equal(zh.run(['tracked'], deps), 0)
})

test('入口の指定がなければ、使い方を出して 2', () => {
  const { deps, err } = makeDeps()
  assert.equal(zh.run([], deps), 2)
  assert.ok(err.join('').includes('zh-mixing.cjs'))
})

// --- 一時のリポジトリで本物の git を呼ぶ ---

test('git の設定の環境の印を読む（一時のリポジトリ）', () => {
  withTempRepo((dir) => {
    assert.equal(zh.readConfig(zh.CONFIG_KEY, { cwd: dir, env: cleanEnv() }), undefined)
    execFileSync('git', ['config', '--local', zh.CONFIG_KEY, 'zh'], { cwd: dir, env: cleanEnv() })
    assert.equal(zh.readConfig(zh.CONFIG_KEY, { cwd: dir, env: cleanEnv() }), 'zh')
  })
})

test('本物の git で、ステージした追加行だけを調べる（一時のリポジトリ）', () => {
  withTempRepo((dir) => {
    fs.writeFileSync(path.join(dir, 'ok.md'), `${JAPANESE}\n`)
    fs.writeFileSync(path.join(dir, 'ng.md'), `${JAPANESE}\nx${SIMPLIFIED}\n`)
    fs.writeFileSync(path.join(dir, 'unstaged.md'), SIMPLIFIED)
    execFileSync('git', ['add', 'ok.md', 'ng.md'], { cwd: dir, env: cleanEnv() })
    const diff = zh.stagedDiff({ cwd: dir, env: cleanEnv() })
    const hits = zh.scanLines(zh.parseAddedLines(diff), list)
    assert.deepEqual(hits.map((h) => [h.file, h.line]), [['ng.md', 2]])
  })
})

test('許す語を通す一覧では見逃す行を、許す語を空にした一覧は見つける', () => {
  const lenient = { ...list, allow: [{ word: SIMPLIFIED, reason: 'テスト' }] }
  const lines = [{ file: 'SKILL.md', line: 1, text: `x${SIMPLIFIED}` }]
  assert.deepEqual(zh.scanLines(lines, lenient), [])
  assert.equal(zh.scanLines(lines, strict).length, 1)
})

test('名前を変えて追記したファイルの追加行を調べる（一時のリポジトリ）', () => {
  withTempRepo((dir) => {
    const env = cleanEnv()
    const git = (...args) => execFileSync('git', ['-c', 'user.name=t', '-c', 'user.email=t@example.com', ...args], { cwd: dir, env })
    const base = Array.from({ length: 10 }, (_, i) => `${JAPANESE}${i}`).join('\n')
    fs.writeFileSync(path.join(dir, 'a.md'), `${base}\n`)
    git('add', 'a.md')
    git('commit', '-q', '-m', 'init')
    git('mv', 'a.md', 'b.md')
    fs.appendFileSync(path.join(dir, 'b.md'), `x${SIMPLIFIED}\n`)
    git('add', 'b.md')
    const hits = zh.scanLines(zh.parseAddedLines(zh.stagedDiff({ cwd: dir, env })), list)
    assert.deepEqual(hits.map((h) => [h.file, h.line]), [['b.md', 11]])
  })
})

// --- 一覧 ---

test('層 1 の一覧（error と warning）と層 2 の一覧に、同じ字・語がない（DC-249-5）', () => {
  const items = [...ja.error, ...ja.warning]
  const layer1 = items.map((t) => t.word).filter((word) => word !== undefined)
  const patterns = items.map((t) => t.pattern).filter((pattern) => pattern !== undefined)
  const layer2 = [...list.chars, ...list.words]
  const hex = (text) => [...text].map((c) => c.codePointAt(0).toString(16))
  for (const a of layer1) {
    for (const b of layer2) {
      assert.ok(!a.includes(b) && !b.includes(a), `層 1 の ${hex(a)} と層 2 の ${hex(b)} が重なる`)
    }
  }
  // pattern の項目も、層 2 の各字・語に当たらない
  for (const pattern of patterns) {
    const re = new RegExp(pattern, 'u')
    for (const b of layer2) {
      assert.ok(!re.test(b), `層 1 の pattern ${pattern} が層 2 の ${hex(b)} に当たる`)
    }
  }
})

test('層 2 の字は 1 字ずつで、重複がない', () => {
  const chars = [...list.chars]
  assert.equal(new Set(chars).size, chars.length)
  assert.ok(chars.length > 0 && list.words.length > 0)
})

test('一覧と script は、中国語の字を書かず、エスケープだけで書く', () => {
  for (const name of ['zh-mixing.json', 'zh-mixing.cjs', 'zh-mixing.test.cjs']) {
    const body = fs.readFileSync(path.join(__dirname, name), 'utf8')
    const lines = body.split('\n').filter((line) => zh.findWords(line, strict).length > 0)
    assert.deepEqual(lines, [], name)
  }
})

test('SKILL.md に、層 2 の字・用語がない（環境の印によらない。DC-249-28）', () => {
  const body = fs.readFileSync(path.join(__dirname, 'SKILL.md'), 'utf8')
  const hits = zh.scanLines(body.split(/\r?\n/).map((text, i) => ({ file: 'SKILL.md', line: i + 1, text })), strict)
  assert.deepEqual(hits.map((h) => h.line), [])
})

test('校閲役の定義に、層 2 の字・用語がない。文脈を絞る設定がある（環境の印によらない。DC-249-29、30）', () => {
  const body = fs.readFileSync(path.join(__dirname, '..', '..', 'agents', 'ja-proofreader.md'), 'utf8')
  const hits = zh.scanLines(body.split(/\r?\n/).map((text, i) => ({ file: 'ja-proofreader.md', line: i + 1, text })), strict)
  assert.deepEqual(hits.map((h) => h.line), [])
  assert.match(body, /^omitClaudeMd: true$/m)
  assert.match(body, /^tools: Read, Grep, Glob$/m)
})
