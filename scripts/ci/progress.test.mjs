import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { checkProgress, countTasks, parseStatusTable, readTasks } from './progress.mjs'

const README = [
  '| spec | 種類 | 内容 | 状態 |',
  '|------|------|------|------|',
  '| [010](docs/specs/010-sales-report) | 機能 | 売上 | 完了。[段階 1〜6](docs/specs/010-sales-report/tasks/) を終えた |',
  '| [017](docs/specs/017-stability) | 機能 | 安定性 | 完了。直さずに残したことは [レビューの結果](x.md) |',
  '| [018](docs/specs/018-docs-and-tools) | 整理 | 文書とツールの整理 | 進行中 |',
].join('\n')

const MVP = [
  '| spec | 名前 | 目的 | この順の理由 | 状態 |',
  '|------|------|------|--------------|------|',
  '| [017](017-stability) | 安定性 | 片付け | 後に置く | 完了 |',
  '| [018](018-docs-and-tools) | 文書とツールの整理 | 一覧 | 最後 | 進行中 |',
].join('\n')

describe('進捗の一致の検査', () => {
  test('表の行から、spec の番号と状態（最初の句点まで）を読む', () => {
    assert.deepEqual(parseStatusTable(README), new Map([['010', '完了'], ['017', '完了'], ['018', '進行中']]))
    assert.deepEqual(parseStatusTable(MVP), new Map([['017', '完了'], ['018', '進行中']]))
  })

  test('tasks のチェックボックスを数える', () => {
    assert.deepEqual(countTasks(['- [x] **T018-01-01** a\n- [ ] **T018-01-02** b', '- [x] **T018-02-01** c\n  - [ ] 補足']), {
      total: 3,
      done: 2,
    })
  })

  test('一致していれば、何も返さない', () => {
    const tasks = new Map([['017', { total: 2, done: 2 }], ['018', { total: 3, done: 1 }]])
    assert.deepEqual(checkProgress(parseStatusTable(README), parseStatusTable(MVP), tasks, 11), [])
  })

  test('tasks がすべて済んだのに完了でない、完了なのに残ったタスクがある、2 つの表が食い違う、を見つける', () => {
    const readme = new Map([['017', '進行中'], ['018', '完了'], ['019', '未着手']])
    const mvp = new Map([['017', '進行中'], ['018', '完了'], ['019', '完了']])
    const tasks = new Map([['017', { total: 2, done: 2 }], ['018', { total: 3, done: 1 }]])
    assert.deepEqual(checkProgress(readme, mvp, tasks, 11), [
      'spec 017：tasks はすべて済んだが、README の状態が「進行中」',
      'spec 017：tasks はすべて済んだが、MVP の完成までの一覧の状態が「進行中」',
      'spec 018：README の状態が「完了」だが、tasks に残りが 2 ある',
      'spec 018：MVP の完成までの一覧の状態が「完了」だが、tasks に残りが 2 ある',
      'spec 019：README の状態「未着手」と、MVP の完成までの一覧の状態「完了」が食い違う',
      'spec 019：MVP の完成までの一覧の状態が「完了」だが、tasks がない',
    ])
  })

  test('対象より前の spec は見ない', () => {
    const readme = new Map([['010', '完了']])
    assert.deepEqual(checkProgress(readme, new Map(), new Map([['010', { total: 2, done: 1 }]]), 11), [])
  })

  test('直下とアーカイブの spec の tasks を読む', () => {
    const specsDir = mkdtempSync(path.join(tmpdir(), 'progress-'))
    try {
      for (const [dir, text] of [
        ['archive/mvp/018-docs/tasks', '- [x] **T018-01-01** a'],
        ['019-mvp/tasks', '- [x] **T019-01-01** a\n- [ ] **T019-01-02** b'],
      ]) {
        mkdirSync(path.join(specsDir, dir), { recursive: true })
        writeFileSync(path.join(specsDir, dir, '01.md'), text)
      }
      assert.deepEqual(
        readTasks(specsDir),
        new Map([
          ['018', { total: 1, done: 1 }],
          ['019', { total: 2, done: 1 }],
        ]),
      )
    } finally {
      rmSync(specsDir, { recursive: true, force: true })
    }
  })
})
