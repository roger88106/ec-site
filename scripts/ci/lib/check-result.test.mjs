import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { hasSummaryLine, judgeCheck } from './check-result.mjs'

describe('要約の行の判定', () => {
  test('最後の行が「<何>：<件数>」の形なら、要約の行がある', () => {
    assert.equal(hasSummaryLine('違反の行\n切れた参照：0\n'), true)
    assert.equal(hasSummaryLine('イメージ：8 件（compose）、食い違い：0 件'), true)
    assert.equal(hasSummaryLine('OK：AC 312 件（廃止 0 件）'), true)
  })

  test('最後の空行は無視して、その前の行を見る', () => {
    assert.equal(hasSummaryLine('切れた参照：0\n\n\n'), true)
  })

  test('要約の行が最後の行でなければ、要約の行はない', () => {
    assert.equal(hasSummaryLine('切れた参照：0\nそのあとの行\n'), false)
  })

  test('「：」か数字がない行や、空の出力は、要約の行ではない', () => {
    assert.equal(hasSummaryLine('表示の文言を直接書いた所はない'), false)
    assert.equal(hasSummaryLine('警告：数字のない行'), false)
    assert.equal(hasSummaryLine('  src/a.ts:3  保存：1'), false)
    assert.equal(hasSummaryLine(''), false)
  })
})

describe('検査の成功・失敗の判定', () => {
  test('終了コード 0 で要約の行があれば成功', () => {
    assert.deepEqual(judgeCheck({ status: 0, output: '切れた参照：0\n', needsSummary: true }), { ok: true })
  })

  test('終了コードが 0 でなければ、要約の行があっても失敗', () => {
    const result = judgeCheck({ status: 1, output: '切れた参照：2\n', needsSummary: true })
    assert.equal(result.ok, false)
  })

  test('終了コード 0 でも、要約の行がなければ失敗にして、理由を返す', () => {
    const result = judgeCheck({ status: 0, output: '問題はない\n', needsSummary: true })
    assert.equal(result.ok, false)
    assert.match(result.reason, /要約の行/)
  })

  test('要約の行を求めないもの（script のテスト）は、終了コードだけで判定する', () => {
    assert.deepEqual(judgeCheck({ status: 0, output: '# pass 10\n', needsSummary: false }), { ok: true })
    assert.equal(judgeCheck({ status: 1, output: '', needsSummary: false }).ok, false)
  })
})

describe('check-all.sh から呼ぶ形（コマンドライン）', () => {
  /** ログのファイルを作って判定の script を実行する */
  function run(output, status, mode) {
    const dir = mkdtempSync(path.join(tmpdir(), 'check-result-'))
    try {
      const log = path.join(dir, 'log.txt')
      writeFileSync(log, output)
      return spawnSync(process.execPath, ['scripts/ci/lib/check-result.mjs', String(status), log, mode], { encoding: 'utf8' })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('成功は終了コード 0 で、何も出さない', () => {
    const result = run('切れた参照：0\n', 0, 'summary')
    assert.equal(result.status, 0)
    assert.equal(result.stdout, '')
  })

  test('要約の行がなければ終了コード 1 で、理由を出す', () => {
    const result = run('問題はない\n', 0, 'summary')
    assert.equal(result.status, 1)
    assert.match(result.stdout, /要約の行/)
  })

  test('モードが none なら、要約の行がなくても成功', () => {
    assert.equal(run('# pass 10\n', 0, 'none').status, 0)
  })
})
