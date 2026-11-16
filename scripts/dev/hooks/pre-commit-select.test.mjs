// pre-commit-select.mjs のテスト（node --test scripts/dev/hooks/pre-commit-select.test.mjs）
import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { CHECK_GROUPS, CHECK_INPUTS, fileReader, importClosure, listChecksAndTests, selectChecks } from './pre-commit-select.mjs'

/** ファイルの内容の表から、読む関数を作る */
const reader = (files) => (file) => files[file] ?? null

/**
 * 小さなリポジトリ：compose-ports は compose を読み、java-rules は lib を import する（検査はフォルダの下に置く）。
 *
 * ja-text は常に実行する検査で、scripts/ の外のファイルを import する。
 */
const FILES = {
  'scripts/ci/config/compose-ports.mjs': "import { lineNumber } from '../lib/source/line-number.mjs'\nconst COMPOSE_FILE = 'docker/compose.yaml'",
  'scripts/ci/java/java-rules.mjs': "import { listGitFiles } from '../lib/git/git-files.mjs'\nimport { RULES } from '../../../config/rules.mjs'",
  'scripts/ci/ja/ja-text.mjs': "import { WORDS } from '../../../config/words.mjs'",
  'config/rules.mjs': "import { x } from './shared.mjs'",
  'config/shared.mjs': '',
  'config/words.mjs': '',
  'scripts/ci/lib/source/line-number.mjs': '',
  'scripts/ci/lib/git/git-files.mjs': "import { x } from './git-env.mjs'",
  'scripts/ci/lib/git/git-env.mjs': '',
  'scripts/ci/frontend/new-check.mjs': '',
  'scripts/ci/config/compose-ports.test.mjs': "import { findPortProblems } from './compose-ports.mjs'",
  'scripts/ci/ja/ja-text.test.mjs': "import { checkText } from './ja-text.mjs'",
  'scripts/ci/lib/git/git-files.test.mjs': "import { listGitFiles } from './git-files.mjs'",
  '.claude/skills/review/checklist.test.cjs': "const x = require('./checklist.cjs')",
}

/** FILES の検査（名前と、ファイルの場所）。new-check は表にない検査 */
const FILE_OF = {
  'compose-ports': 'scripts/ci/config/compose-ports.mjs',
  'ja-text': 'scripts/ci/ja/ja-text.mjs',
  'java-rules': 'scripts/ci/java/java-rules.mjs',
  'new-check': 'scripts/ci/frontend/new-check.mjs',
}

/** FILES の検査とテストで選ぶ */
const select = (staged) =>
  selectChecks({
    staged,
    checks: ['compose-ports', 'ja-text', 'java-rules', 'new-check'],
    files: FILE_OF,
    tests: ['.claude/skills/review/checklist.test.cjs', 'scripts/ci/config/compose-ports.test.mjs', 'scripts/ci/ja/ja-text.test.mjs', 'scripts/ci/lib/git/git-files.test.mjs'],
    read: reader(FILES),
  })

describe('commit の前の hook が実行する検査の選び方', () => {
  test('scripts/ の外の、import でたどれるファイル（入れ子を含む）を変えると、その検査を実行する', () => {
    const result = select(['config/shared.mjs'])
    assert.equal(result.all, false)
    assert.deepEqual(result.checks, ['ja-text', 'java-rules', 'new-check'])
  })

  test('検査が名前を書いたファイルを変えると、その検査と同じ名前のテストを実行する', () => {
    const result = select(['docker/compose.yaml'])
    assert.deepEqual(result.checks, ['compose-ports', 'ja-text', 'new-check'])
    assert.deepEqual(result.tests, ['scripts/ci/config/compose-ports.test.mjs'])
  })

  test('検査の読むファイルの形に当たるファイルを変えると、その検査を実行する', () => {
    assert.deepEqual(select(['backend/ecsite/src/main/java/A.java']).checks, ['ja-text', 'java-rules', 'new-check'])
  })

  test('表にない検査と、表で常に実行するとした検査は、どのファイルを変えても実行する', () => {
    assert.deepEqual(select(['README.md']).checks, ['ja-text', 'new-check'])
  })

  test('常に実行する検査のテストは、検査が import でたどれるファイルを変えたときだけ実行する', () => {
    assert.deepEqual(select(['README.md']).tests, [])
    assert.deepEqual(select(['config/words.mjs']).tests, ['scripts/ci/ja/ja-text.test.mjs'])
  })

  test('require でたどれるファイルと、テストの読むファイルの形に当たるファイルを変えると、そのテストを実行する', () => {
    assert.deepEqual(select(['.claude/skills/review/checklist.cjs']).tests, ['.claude/skills/review/checklist.test.cjs'])
    assert.deepEqual(select(['docs/05-開発規則/review.md']).tests, ['.claude/skills/review/checklist.test.cjs'])
  })

  test('scripts/ の下のファイルか依存を変えると、全てを実行する', () => {
    for (const file of ['scripts/ci/check-all.sh', 'scripts/ci/lib/git/git-env.mjs', 'scripts/dev/slot.mjs', 'package.json', 'pnpm-lock.yaml']) {
      assert.equal(select([file]).all, true, file)
    }
  })

  test('import の先は、相対パスを正規化して読む', () => {
    const closure = importClosure('scripts/ci/java/java-rules.mjs', reader(FILES))
    assert.deepEqual([...closure.keys()].sort(), ['config/rules.mjs', 'config/shared.mjs', 'scripts/ci/java/java-rules.mjs', 'scripts/ci/lib/git/git-env.mjs', 'scripts/ci/lib/git/git-files.mjs'])
  })
})

describe('リポジトリの検査とテスト', () => {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
  const { checks, files, tests } = listChecksAndTests(root)

  test('読むファイルの形の表に、今ない検査を書いていない', () => {
    assert.deepEqual(
      Object.keys(CHECK_INPUTS).filter((name) => !checks.includes(name)),
      [],
    )
  })

  test('検査は、検査のフォルダの直下の .mjs（テストを除く）で、名前と場所を対にする', () => {
    const pattern = new RegExp(`^scripts/ci/(?:${CHECK_GROUPS.join('|')})/[^/]+\\.mjs$`)
    for (const name of checks) assert.match(files[name], pattern, name)
    assert.equal(files['compose-ports'], 'scripts/ci/config/compose-ports.mjs')
    assert.ok(!checks.some((name) => name.endsWith('.test')), 'テストを検査にしない')
  })

  test('compose を変えると、compose を読む検査と、常に実行する検査だけを実行する', () => {
    const result = selectChecks({ staged: ['docker/compose.yaml'], checks, files, tests, read: fileReader(root) })
    assert.equal(result.all, false)
    for (const name of ['compose-ports', 'local-ports']) assert.ok(result.checks.includes(name), name)
    // 常に実行する検査（CI の検査の script の「commit の前の hook」の表）
    for (const name of ['doc-refs', 'ja-text', 'risk', 'spec-numbers']) assert.ok(result.checks.includes(name), name)
    for (const name of ['java-rules', 'frontend-rules', 'migrations']) assert.ok(!result.checks.includes(name), name)
  })
})
