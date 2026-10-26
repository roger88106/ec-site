import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findShardCountProblems, readCiShardCount, readGradleShardCount } from './integration-shards.mjs'

const CI = [
  'jobs:',
  '  backend-integration:',
  '    strategy:',
  '      fail-fast: false',
  '      matrix:',
  '        shard: [1, 2]',
  '    steps:',
  '      - run: ./gradlew integrationTest -PintegrationTestShard=${{ matrix.shard }}/${{ strategy.job-total }}',
].join('\n')

const GRADLE = [
  'tasks.register("integrationTestShards") {',
  '    val shardCount = providers.gradleProperty("integrationTestShardCount").map { it.toInt() }.orElse(2)',
  '}',
].join('\n')

describe('統合テストを分ける数の読み取り', () => {
  test('ci.yml の matrix の shard の一覧の数を読む', () => {
    assert.equal(readCiShardCount(CI).count, 2)
    assert.equal(readCiShardCount(CI.replace('[1, 2]', '[1, 2, 3]')).count, 3)
    assert.equal(readCiShardCount(CI).line, 6)
  })

  test('ブロック形式の shard の一覧も読む', () => {
    const block = CI.replace('shard: [1, 2]', 'shard:\n          - 1\n          - 2\n          - 3')
    assert.equal(readCiShardCount(block).count, 3)
  })

  test('shard の一覧がない、または読めない書き方なら理由を返す', () => {
    assert.match(readCiShardCount('jobs: {}').reason, /shard/)
    assert.match(readCiShardCount(CI.replace('[1, 2]', '${{ fromJSON(vars.SHARDS) }}')).reason, /読めない/)
    assert.match(readCiShardCount(CI.replace('[1, 2]', '[]')).reason, /読めない/)
  })

  test('shard の一覧が複数あると読めない', () => {
    const two = `${CI}\n        shard: [1]`
    assert.match(readCiShardCount(two).reason, /複数/)
  })

  test('build.gradle.kts の既定の数を読む', () => {
    assert.equal(readGradleShardCount(GRADLE).count, 2)
    assert.equal(readGradleShardCount(GRADLE.replace('orElse(2)', 'orElse(4)')).count, 4)
  })

  test('既定の数がない、または数でないなら理由を返す', () => {
    assert.match(readGradleShardCount('val a = 1').reason, /integrationTestShardCount/)
    assert.match(readGradleShardCount(GRADLE.replace('orElse(2)', 'orElse(n)')).reason, /読めない/)
  })
})

describe('統合テストを分ける数の食い違い', () => {
  test('一致していれば問題はない', () => {
    assert.deepEqual(findShardCountProblems(CI, GRADLE), [])
  })

  test('食い違うと、両方の数を示す', () => {
    const problems = findShardCountProblems(CI.replace('[1, 2]', '[1, 2, 3]'), GRADLE)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci\.yml/)
    assert.match(problems[0], /build\.gradle\.kts/)
    assert.match(problems[0], /3/)
    assert.match(problems[0], /2/)
  })

  test('読めない書き方は、ファイル:行 内容（理由）の形で失敗にする', () => {
    const problems = findShardCountProblems(CI.replace('[1, 2]', '${{ fromJSON(vars.SHARDS) }}'), GRADLE)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^\.github\/workflows\/ci\.yml:6 shard: \$\{\{ fromJSON\(vars\.SHARDS\) \}\}（/)
  })

  test('どちらかが見つからないときも失敗にする', () => {
    assert.equal(findShardCountProblems('jobs: {}', GRADLE).length, 1)
    assert.equal(findShardCountProblems(CI, 'val a = 1').length, 1)
  })
})
