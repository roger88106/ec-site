import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'

/** scripts/ci/ のディレクトリ */
const CI_DIR = path.dirname(fileURLToPath(import.meta.url))

/** check-all.sh と判定の script を写し、替わりの検査を置くリポジトリ */
const root = mkdtempSync(path.join(tmpdir(), 'check-all-'))
after(() => rmSync(root, { recursive: true, force: true }))

/** 替わりの検査（検査のフォルダの直下に置く）。名前 → 中身 */
const CHECKS = {
  pass: "console.log('違反：0')",
  'no-summary': "console.log('終わった')",
  fail: "console.log('違反：1')\nprocess.exit(1)",
  warn: "console.log('警告：気をつける')\nconsole.log('違反：0')",
}

before(() => {
  for (const file of ['check-all.sh', 'lib/output/check-result.mjs']) {
    mkdirSync(path.dirname(path.join(root, 'scripts/ci', file)), { recursive: true })
    copyFileSync(path.join(CI_DIR, file), path.join(root, 'scripts/ci', file))
  }
  mkdirSync(path.join(root, 'scripts/ci/docs'), { recursive: true })
  for (const [name, body] of Object.entries(CHECKS)) writeFileSync(path.join(root, `scripts/ci/docs/${name}.mjs`), `${body}\n`)
  writeFileSync(path.join(root, 'one.test.mjs'), "import { test } from 'node:test'\ntest('通る', () => {})\n")
})

/**
 * 写した check-all.sh を、選んだテストと検査で実行する。
 *
 * @param {{ tests: string, scripts: string }} selection CHECK_TESTS と CHECK_SCRIPTS の値
 * @returns {{ status: number | null, lines: string[] }} 終了コードと、結果の行（OK・FAIL で始まる行と「|」の行。経過の秒は除く）
 */
function runCheckAll({ tests, scripts }) {
  const env = { ...process.env, CHECK_TESTS: tests, CHECK_SCRIPTS: scripts }
  delete env.GITHUB_ACTIONS
  // node --test の中から node --test を呼ぶと、子は NODE_TEST_CONTEXT を見て結果を親に送り、テストの数の行を出さないため
  delete env.NODE_TEST_CONTEXT
  const result = spawnSync('bash', [path.join(root, 'scripts/ci/check-all.sh')], { cwd: root, env, encoding: 'utf8' })
  const lines = result.stdout
    .split('\n')
    .filter((line) => /^(?:OK|FAIL) /.test(line) || line.startsWith('     | 警告'))
    .map((line) => line.replace(/ \(\d+s(?:, )?/, ' ('))
  return { status: result.status, lines }
}

describe('check-all.sh の判定', () => {
  test('要約の行のある検査は OK、終了コードが 0 でない検査と要約の行のない検査は FAIL にし、全てを流してから 1 で終わる', () => {
    const { status, lines } = runCheckAll({ tests: '', scripts: 'fail no-summary pass' })
    assert.equal(status, 1)
    assert.deepEqual(lines, ['FAIL fail (終了コード 1)', 'FAIL no-summary (終了コード 0 だが、最後の行が要約の行（「<何>：<件数>」）でない)', 'OK   pass ()'])
  })

  test('CHECK_SCRIPTS で選んだ検査だけを流し、全て通れば 0 で終わる。通った検査の警告の行は出す', () => {
    const { status, lines } = runCheckAll({ tests: '', scripts: 'pass warn' })
    assert.equal(status, 0)
    assert.deepEqual(lines, ['OK   pass ()', 'OK   warn ()', '     | 警告：気をつける'])
  })

  test('CHECK_TESTS が空なら script のテストを流さず、指定すればそのテストを流す', () => {
    assert.deepEqual(runCheckAll({ tests: '', scripts: 'pass' }).lines, ['OK   pass ()'])
    assert.deepEqual(runCheckAll({ tests: 'one.test.mjs', scripts: 'pass' }).lines, ['OK   script のテスト ()', 'OK   pass ()'])
  })

  test('script のテストの glob が 1 件も一致しない（テストが 0 件）なら、終了コード 0 でも FAIL にする', () => {
    const { status, lines } = runCheckAll({ tests: 'missing/*.test.mjs', scripts: '' })
    assert.equal(status, 1)
    assert.match(lines[0], /^FAIL script のテスト \(終了コード 0 だが、テストが 0 件/)
  })
})
