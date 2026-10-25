import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findActionDirectories, findUncoveredActions, readActionsDirectories } from './dependabot-actions.mjs'

const dependabot = [
  'updates:',
  '  - package-ecosystem: npm',
  '    directory: /',
  '  - package-ecosystem: github-actions',
  '    # コメント',
  '    directories:',
  '      - /',
  '      - "/.github/actions/setup-frontend" # 注',
  '    schedule:',
  '      interval: weekly',
  '  - package-ecosystem: docker-compose',
  '    directory: /docker',
].join('\n')

describe('Dependabot の github-actions の対象の読み取り', () => {
  test('directories の一覧を読み、他の ecosystem の directory は読まない', () => {
    assert.deepEqual(readActionsDirectories(dependabot), ['/', '/.github/actions/setup-frontend'])
  })

  test('directory（1 つの書き方）も読む', () => {
    assert.deepEqual(readActionsDirectories('updates:\n  - package-ecosystem: github-actions\n    directory: "/"\n'), ['/'])
  })

  test('github-actions がなければ空', () => {
    assert.deepEqual(readActionsDirectories('updates:\n  - package-ecosystem: npm\n    directory: /\n'), [])
  })
})

describe('複合の action のディレクトリ', () => {
  test('action.yml と action.yaml のあるディレクトリを、先頭の / 付きで返す', () => {
    const files = ['.github/actions/a/action.yml', '.github/actions/b/action.yaml', '.github/actions/a/README.md', '.github/workflows/ci.yml', 'action.yml']
    assert.deepEqual(findActionDirectories(files), ['/', '/.github/actions/a', '/.github/actions/b'])
  })
})

describe('対象に入っていない action の検出', () => {
  test('全て入っていれば問題はない', () => {
    assert.deepEqual(findUncoveredActions(['/.github/actions/a'], ['/', '/.github/actions/a']), [])
  })

  test('入っていない action のディレクトリを見つける', () => {
    const problems = findUncoveredActions(['/.github/actions/a', '/.github/actions/new'], ['/', '/.github/actions/a'])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /\/\.github\/actions\/new/)
  })

  test('* を含む対象は、その形に合うディレクトリを含む', () => {
    assert.deepEqual(findUncoveredActions(['/.github/actions/a', '/.github/actions/b'], ['/.github/actions/*']), [])
  })

  test('/ はルートだけで、下のディレクトリは含まない', () => {
    assert.equal(findUncoveredActions(['/.github/actions/a'], ['/']).length, 1)
    assert.deepEqual(findUncoveredActions(['/'], ['/']), [])
  })
})
