import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { describeUnknownActionLine, findActionDirectories, findUncoveredActions, findUnknownActionLines, readActionsDirectories } from './dependabot-actions.mjs'

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

describe('Dependabot の github-actions の知らない書き方', () => {
  const wrap = (lines) => ['updates:', '  - package-ecosystem: github-actions', ...lines].join('\n')

  test('知っている書き方だけなら 0 件', () => {
    assert.deepEqual(findUnknownActionLines(dependabot), [])
    assert.deepEqual(findUnknownActionLines(wrap(['    directory: /'])), [])
  })

  test('フロー形式の directories を知らない書き方にする', () => {
    const unknown = findUnknownActionLines(wrap(['    directories: [/, /a]']))
    assert.deepEqual(unknown.map((entry) => entry.line), [3])
  })

  test('値のない directory を知らない書き方にする', () => {
    assert.equal(findUnknownActionLines(wrap(['    directory:'])).length, 1)
  })

  test('directories の下の、項目でない行と字下げのそろわない項目を知らない書き方にする', () => {
    const unknown = findUnknownActionLines(wrap(['    directories:', '      - /', '        - /deep', '      - /ok', '    - /other', '      key: value']))
    assert.deepEqual(unknown.map((entry) => entry.line), [5, 7, 8])
  })

  test('directories の外の他の節と、他の ecosystem の行は見ない', () => {
    const text = ['updates:', '  - package-ecosystem: npm', '    directories: [/a]', '  - package-ecosystem: github-actions', '    directories:', '      - /', '    labels:', '      - x'].join('\n')
    assert.deepEqual(findUnknownActionLines(text), [])
  })

  test('知らない書き方の行は、読めない行の形（ファイル:行 内容（理由））で表示する', () => {
    const [entry] = findUnknownActionLines(wrap(['    directories: [/, /a]']))
    assert.match(describeUnknownActionLine('.github/dependabot.yml', entry), /^\.github\/dependabot\.yml:3 directories: \[\/, \/a\]（知らない書き方/)
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
