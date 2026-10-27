import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findRuleViolations, listScanScripts } from './scan-rules.mjs'
import { describeUnreadableLine } from './unreadable-line.mjs'

describe('読めなかった行の文', () => {
  test('ファイル:行 内容（理由）の形で返し、内容の前後の空白は除く', () => {
    assert.equal(
      describeUnreadableLine({ file: 'docker/compose.yaml', line: 12, text: '    - "8080-8090:80"  ', reason: '知らない書き方' }),
      'docker/compose.yaml:12 - "8080-8090:80"（知らない書き方）',
    )
  })
})

describe('検査の script が文を直接組み立てない', () => {
  test('読めない行の文（ファイル:行 内容（理由））は、describeUnreadableLine で作る', () => {
    const rules = [
      {
        guard: 'describeUnreadableLine',
        forbidden: /(?:throw new Error|\.push|console\.log)\([^\n]*読めない[^\n]*（/,
        // 行を持たない（ファイル全体や、Java の record の引数）読めない物を自前の文で出す script と、その理由
        exceptions: {
          'ci/env-defaults.mjs': 'Java の record の引数の並びは、行でなく括弧の中を読む',
          'ci/frontend-rules.mjs': '共有のパッケージの exports は、package.json の全体を読む',
          'ci/lib/exclusions.mjs': '除外の一覧の JSON は、ファイル全体を読む',
        },
      },
    ]
    assert.deepEqual(findRuleViolations(listScanScripts('ci+lib'), rules), [])
  })

  test('除外の一覧の誤りの文（理由がない、不要になった）は、lib/exclusions.mjs だけが作る', () => {
    const rules = [
      {
        guard: 'checkExclusions',
        forbidden: /の理由がない（|は不要になった除外（/,
        exceptions: { 'ci/lib/exclusions.mjs': '誤りの文を作る関数を定義する' },
      },
    ]
    assert.deepEqual(findRuleViolations(listScanScripts('ci+lib'), rules), [])
  })
})
