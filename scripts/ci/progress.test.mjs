import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from './lib/git-env.mjs'
import { checkArchiveGroups, checkArchivedTasks, countTasks, readArchivedTasks } from './progress.mjs'

describe('1→10 のアーカイブの進捗の検査', () => {
  test('tasks のチェックボックスを数える', () => {
    assert.deepEqual(countTasks(['- [x] **T018-01-01** a\n- [ ] **T018-01-02** b', '- [x] **T018-02-01** c\n  - [ ] 補足']), {
      total: 3,
      done: 2,
    })
  })

  test('1→10 の spec の tasks に残りがあれば、食い違いとして返す', () => {
    const tasks = new Map([
      ['5-done', { total: 2, done: 2 }],
      ['7-open', { total: 3, done: 1 }],
    ])
    assert.deepEqual(checkArchivedTasks(tasks), ['1→10 の spec 7-open：tasks に残りが 2 ある（アーカイブに移すタスクを tasks の最後にし、その commit で全てのタスクを `[x]` にする。アーカイブの後に追加するタスクは `[x]` の行で追加する。開発の進め方の「規模」）'])
    assert.deepEqual(checkArchivedTasks(new Map([['5-done', { total: 2, done: 2 }]])), [])
  })

  test('アーカイブの 1→10 の下の spec の tasks を読み、直下の進行中の spec と MVP は含めない', () => {
    const specsDir = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      for (const [dir, text] of [
        ['archive/1to10/3-cart/tasks', '- [x] **T3-01-01** a'],
        ['archive/1to10/4-order/tasks', '- [x] **T4-01-01** a\n- [ ] **T4-01-02** b'],
        ['archive/mvp/018-docs/tasks', '- [ ] **T018-01-01** a'],
        ['7-dev-process/tasks', '- [ ] **T7-01-01** a'],
      ]) {
        mkdirSync(path.join(specsDir, dir), { recursive: true })
        writeFileSync(path.join(specsDir, dir, '01.md'), text)
      }
      assert.deepEqual(
        readArchivedTasks(specsDir),
        new Map([
          ['3-cart', { total: 1, done: 1 }],
          ['4-order', { total: 2, done: 1 }],
        ]),
      )
    } finally {
      rmSync(specsDir, { recursive: true, force: true })
    }
  })

  test('1→10 の spec が 0 件なら、食い違いとして返す', () => {
    assert.equal(checkArchivedTasks(new Map()).length, 1)
  })

  test('アーカイブの下の、mvp と 1to10 以外のディレクトリを食い違いとして返す。ファイルは見ない', () => {
    const specsDir = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      for (const dir of ['archive/mvp', 'archive/1to10', 'archive/1to1O', 'archive/done']) mkdirSync(path.join(specsDir, dir), { recursive: true })
      writeFileSync(path.join(specsDir, 'archive', 'README.md'), '説明\n')
      assert.deepEqual(checkArchiveGroups(specsDir).map((line) => line.split('：')[0]), ['archive/1to1O', 'archive/done'])
      assert.deepEqual(checkArchiveGroups(path.join(specsDir, 'none')), [])
    } finally {
      rmSync(specsDir, { recursive: true, force: true })
    }
  })

  test('archive/mvp/ の下の、名前が MVP の形（0 で始まる 3 桁の番号）でない spec のディレクトリを、食い違いとして返す。ファイルは見ない', () => {
    const specsDir = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      for (const dir of ['archive/mvp/001-catalog', 'archive/mvp/019-done', 'archive/mvp/330-x', 'archive/mvp/7-seven', 'archive/mvp/0019-x', 'archive/mvp/012', 'archive/1to10/3-cart']) {
        mkdirSync(path.join(specsDir, dir), { recursive: true })
      }
      writeFileSync(path.join(specsDir, 'archive/mvp/mvp.md'), '説明\n')
      const problems = checkArchiveGroups(specsDir)
      assert.deepEqual(problems.map((line) => line.split('：')[0]), ['archive/mvp/0019-x', 'archive/mvp/012', 'archive/mvp/330-x', 'archive/mvp/7-seven'])
      assert.match(problems[0], /1→10 の spec を誤って mvp\/ に移していないか/)
    } finally {
      rmSync(specsDir, { recursive: true, force: true })
    }
  })

  test('1→10 の spec に tasks が 1 つもなければ、「tasks がない」の食い違いとして返す', () => {
    assert.deepEqual(checkArchivedTasks(new Map([['9-none', { total: 0, done: 0 }]])), ['1→10 の spec 9-none：tasks がない'])
  })

  test('アーカイブの 1→10 の spec で tasks のディレクトリがなければ（規模 M）、spec.md の中のタスクの行を数える', () => {
    const specsDir = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      const medium = path.join(specsDir, 'archive/1to10/8-medium')
      mkdirSync(medium, { recursive: true })
      writeFileSync(
        path.join(medium, 'spec.md'),
        '# spec\n\n## tasks\n\n- [x] **T8-01** a\n- [ ] **T8-02** b\n  - [ ] 補足は数えない\n',
      )
      const noTasks = path.join(specsDir, 'archive/1to10/9-none')
      mkdirSync(noTasks, { recursive: true })
      writeFileSync(path.join(noTasks, 'spec.md'), '# spec\n')
      // spec.md もない spec も、tasks がないものとして含める
      mkdirSync(path.join(specsDir, 'archive/1to10/10-empty'), { recursive: true })
      assert.deepEqual(
        readArchivedTasks(specsDir),
        new Map([
          ['10-empty', { total: 0, done: 0 }],
          ['8-medium', { total: 2, done: 1 }],
          ['9-none', { total: 0, done: 0 }],
        ]),
      )
    } finally {
      rmSync(specsDir, { recursive: true, force: true })
    }
  })

  test('1→10 の spec を、番号の数値の順に並べる（10 は 7 の後）', () => {
    const specsDir = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      for (const name of ['10-ten', '7-seven', '2-two']) {
        mkdirSync(path.join(specsDir, 'archive/1to10', name, 'tasks'), { recursive: true })
        writeFileSync(path.join(specsDir, 'archive/1to10', name, 'tasks', '01.md'), '- [ ] **T1-01-01** a')
      }
      assert.deepEqual([...readArchivedTasks(specsDir).keys()], ['2-two', '7-seven', '10-ten'])
      const open = new Map([
        ['10-ten', { total: 1, done: 0 }],
        ['7-seven', { total: 1, done: 0 }],
      ])
      assert.deepEqual(
        checkArchivedTasks(open).map((line) => line.split('：')[0]),
        ['1→10 の spec 7-seven', '1→10 の spec 10-ten'],
      )
    } finally {
      rmSync(specsDir, { recursive: true, force: true })
    }
  })
})

describe('script として実行する', () => {
  const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'progress.mjs')

  /** 1→10 のアーカイブだけを持つ作業ディレクトリで script を実行する */
  function run(archivedTask, files = {}) {
    const root = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      const tasksDir = path.join(root, 'docs/08-spec/archive/1to10/3-cart/tasks')
      mkdirSync(tasksDir, { recursive: true })
      writeFileSync(path.join(tasksDir, '01.md'), archivedTask)
      for (const [file, text] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(root, file)), { recursive: true })
        writeFileSync(path.join(root, file), text)
      }
      return spawnSync(process.execPath, [script], { cwd: root, encoding: 'utf8', env: withoutGitEnv() })
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  }

  test('1→10 のアーカイブの tasks に未完了があれば、失敗する', () => {
    const result = run('- [x] **T3-01-01** a\n- [ ] **T3-01-02** b')
    assert.equal(result.status, 1)
    assert.match(result.stdout, /1→10 の spec 3-cart：tasks に残りが 1 ある/)
  })

  test('1→10 のアーカイブの下の spec が 0 件なら、何も見ずに通らず失敗する', () => {
    const root = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      mkdirSync(path.join(root, 'docs/08-spec/archive/1to10'), { recursive: true })
      const result = spawnSync(process.execPath, [script], { cwd: root, encoding: 'utf8', env: withoutGitEnv() })
      assert.equal(result.status, 1, result.stdout)
      assert.match(result.stdout, /archive\/1to10\/ の下に spec が 1 件もない/)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })

  test('アーカイブの下に mvp と 1to10 以外のディレクトリがあれば、失敗する', () => {
    for (const group of ['1to1O', 'misc']) {
      const result = run('- [x] **T3-01-01** a', { [`docs/08-spec/archive/${group}/5-x/tasks/01.md`]: '- [ ] **T5-01-01** a\n' })
      assert.equal(result.status, 1, `${group}: ${result.stdout}`)
      assert.match(result.stdout, new RegExp(`archive/${group}`))
    }
  })

  test('1→10 の spec を誤って archive/mvp/ に移したら、tasks が未完了でも見落とさず、失敗する', () => {
    const result = run('- [x] **T3-01-01** a', { 'docs/08-spec/archive/mvp/330-x/tasks/01.md': '- [ ] **T330-01-01** a\n' })
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /archive\/mvp\/330-x/)
  })

  test('MVP の部分を見ない：README の表と mvp.md の有無や食い違いによらず成功する', () => {
    const none = run('- [x] **T3-01-01** a')
    assert.equal(none.status, 0, none.stdout)
    const mismatch = run('- [x] **T3-01-01** a', {
      'README.md': '| [018](docs/08-spec/archive/mvp/018-x) | 整理 | 内容 | 進行中 |\n',
      'docs/08-spec/archive/mvp/mvp.md': '| [018](018-x) | 名前 | 目的 | 理由 | 完了 |\n',
      'docs/08-spec/archive/mvp/018-x/tasks/01.md': '- [x] **T018-01-01** a\n',
    })
    assert.equal(mismatch.status, 0, mismatch.stdout)
  })
})
