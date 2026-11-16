import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { describeUnreadableLine } from './unreadable-line.mjs'

describe('読めなかった行の文', () => {
  test('ファイル:行 内容（理由）の形で返し、内容の前後の空白は除く', () => {
    assert.equal(
      describeUnreadableLine({ file: 'docker/compose.yaml', line: 12, text: '    - "8080-8090:80"  ', reason: '知らない書き方' }),
      'docker/compose.yaml:12 - "8080-8090:80"（知らない書き方）',
    )
  })
})
