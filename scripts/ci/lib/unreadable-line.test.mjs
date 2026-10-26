import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { stripComments } from './strip-comments.mjs'
import { describeUnreadableLine } from './unreadable-line.mjs'

describe('読めなかった行の文', () => {
  test('ファイル:行 内容（理由）の形で返し、内容の前後の空白は除く', () => {
    assert.equal(
      describeUnreadableLine({ file: 'docker/compose.yaml', line: 12, text: '    - "8080-8090:80"  ', reason: '知らない書き方' }),
      'docker/compose.yaml:12 - "8080-8090:80"（知らない書き方）',
    )
  })
})

/** 検査の script（scripts/ci/ の直下と lib/ の、テストでない .mjs）のパスと、コメントを除いた中身 */
function readCheckSources() {
  const ciDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
  return [ciDir, path.join(ciDir, 'lib')].flatMap((dir) =>
    readdirSync(dir)
      .filter((name) => name.endsWith('.mjs') && !name.endsWith('.test.mjs'))
      .map((name) => ({
        name: path.relative(ciDir, path.join(dir, name)).replaceAll(path.sep, '/'),
        code: stripComments(readFileSync(path.join(dir, name), 'utf8'), { keepRegex: true }),
      })),
  )
}

describe('検査の script が文を直接組み立てない', () => {
  test('読めない行の文（ファイル:行 内容（理由））は、describeUnreadableLine で作る', () => {
    // 行を持たない（ファイル全体や、Java の record の引数）読めない物を自前の文で出す script と、その理由
    const notLineBased = {
      'env-defaults.mjs': 'Java の record の引数の並びは、行でなく括弧の中を読む',
      'frontend-rules.mjs': '共有のパッケージの exports は、package.json の全体を読む',
      'lib/exclusions.mjs': '除外の一覧の JSON は、ファイル全体を読む',
    }
    const unreadableMessage = /(?:throw new Error|\.push|console\.log)\([^\n]*読めない[^\n]*（/
    const offenders = readCheckSources()
      .filter(({ name }) => !(name in notLineBased))
      .filter(({ code }) => unreadableMessage.test(code))
      .map(({ name }) => name)
    assert.deepEqual(offenders, [])
  })

  test('除外の一覧の誤りの文（理由がない、不要になった）は、lib/exclusions.mjs だけが作る', () => {
    const offenders = readCheckSources()
      .filter(({ name }) => name !== 'lib/exclusions.mjs')
      .filter(({ code }) => /の理由がない（|は不要になった除外（/.test(code))
      .map(({ name }) => name)
    assert.deepEqual(offenders, [])
  })
})
