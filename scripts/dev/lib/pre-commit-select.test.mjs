import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { CHECK_INPUTS, fileReader, importClosure, listChecksAndTests, selectChecks } from './pre-commit-select.mjs'

/** ファイルの内容の表から、読む関数を作る */
const reader = (files) => (file) => files[file] ?? null

/** 小さなリポジトリ：compose-ports は compose を読み、java-rules は共有の部品を import する */
const FILES = {
  'scripts/ci/compose-ports.mjs': "import { isMain } from './lib/is-main.mjs'\nconst COMPOSE_FILE = 'docker/compose.yaml'",
  'scripts/ci/java-rules.mjs': "import { listGitFiles } from './lib/git-files.mjs'",
  'scripts/ci/lib/is-main.mjs': '',
  'scripts/ci/lib/git-files.mjs': "import { x } from './git-env.mjs'",
  'scripts/ci/lib/git-env.mjs': '',
  'scripts/ci/new-check.mjs': '',
  'scripts/ci/compose-ports.test.mjs': "import { findPortProblems } from './compose-ports.mjs'",
  'scripts/ci/lib/git-files.test.mjs': "import { listGitFiles } from './git-files.mjs'",
  '.claude/skills/review/checklist.test.cjs': "const x = require('./checklist.cjs')",
}

/** FILES の検査とテストで選ぶ */
const select = (staged) =>
  selectChecks({
    staged,
    checks: ['compose-ports', 'java-rules', 'new-check'],
    tests: ['.claude/skills/review/checklist.test.cjs', 'scripts/ci/compose-ports.test.mjs', 'scripts/ci/lib/git-files.test.mjs'],
    read: reader(FILES),
  })

describe('commit の前の hook が実行する検査の選び方', () => {
  test('import でたどれるファイル（入れ子を含む）を変えると、その検査とテストを実行する', () => {
    const result = select(['scripts/ci/lib/git-env.mjs'])
    assert.deepEqual(result.checks, ['java-rules', 'new-check'])
    assert.deepEqual(result.tests, ['scripts/ci/lib/git-files.test.mjs'])
  })

  test('検査が名前を書いたファイルを変えると、その検査と同じ名前のテストを実行する', () => {
    const result = select(['docker/compose.yaml'])
    assert.deepEqual(result.checks, ['compose-ports', 'new-check'])
    assert.deepEqual(result.tests, ['scripts/ci/compose-ports.test.mjs'])
  })

  test('検査の読むファイルの形に当たるファイルを変えると、その検査を実行する', () => {
    assert.deepEqual(select(['backend/ecsite/src/main/java/A.java']).checks, ['java-rules', 'new-check'])
  })

  test('表にない検査は、どのファイルを変えても実行する', () => {
    assert.deepEqual(select(['README.md']).checks, ['new-check'])
  })

  test('require でたどれるファイルと、テストの読むファイルの形に当たるファイルを変えると、そのテストを実行する', () => {
    assert.deepEqual(select(['.claude/skills/review/checklist.cjs']).tests, ['.claude/skills/review/checklist.test.cjs'])
    assert.deepEqual(select(['docs/05-開発規則/review.md']).tests, ['.claude/skills/review/checklist.test.cjs'])
  })

  test('まとめて実行する仕組みか依存を変えると、全てを実行する', () => {
    for (const file of ['scripts/ci/check-all.sh', 'package.json', 'pnpm-lock.yaml', 'scripts/dev/lib/pre-commit-select.mjs']) {
      assert.equal(select([file]).all, true, file)
    }
  })

  test('import の先は、相対パスを正規化して読む', () => {
    const closure = importClosure('scripts/ci/java-rules.mjs', reader(FILES))
    assert.deepEqual([...closure.keys()].sort(), ['scripts/ci/java-rules.mjs', 'scripts/ci/lib/git-env.mjs', 'scripts/ci/lib/git-files.mjs'])
  })
})

describe('リポジトリの検査とテスト', () => {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
  const { checks, tests } = listChecksAndTests(root)

  test('読むファイルの形の表に、今ない検査を書いていない', () => {
    assert.deepEqual(
      Object.keys(CHECK_INPUTS).filter((name) => !checks.includes(name)),
      [],
    )
  })

  test('compose を変えると、compose を読む検査と、常に実行する検査だけを実行する', () => {
    const result = selectChecks({ staged: ['docker/compose.yaml'], checks, tests, read: fileReader(root) })
    assert.equal(result.all, false)
    for (const name of ['compose-ports', 'local-ports']) assert.ok(result.checks.includes(name), name)
    for (const name of ['java-rules', 'frontend-rules', 'migrations']) assert.ok(!result.checks.includes(name), name)
  })
})
