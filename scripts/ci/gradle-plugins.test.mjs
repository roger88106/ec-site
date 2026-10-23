import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findPluginProblems, readBuildPluginIds, readCatalogPluginIds, readPolicyPluginIds } from './gradle-plugins.mjs'

const toml = [
  '[libraries]',
  'lombok = { module = "org.projectlombok:lombok", id = "not.a.plugin" }',
  '',
  '[plugins]',
  '# コメントの id = "ignored.plugin"',
  'spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }',
  'jooq = { id = "nu.studer.jooq", version.ref = "jooqPlugin" }',
  '',
  '[bundles]',
  'x = ["a"]',
].join('\n')

describe('プラグインの読み取り', () => {
  test('libs.versions.toml の [plugins] の id だけを読む', () => {
    assert.deepEqual(readCatalogPluginIds(toml), ['org.springframework.boot', 'nu.studer.jooq'])
  })

  test('[plugins] がなければ空', () => {
    assert.deepEqual(readCatalogPluginIds('[versions]\na = "1"\n'), [])
  })

  test('plugins {} の中の id("…") を読み、alias・組み込み・コメントは読まない', () => {
    const kts = [
      'plugins {',
      '    java',
      '    alias(libs.plugins.spring.boot)',
      '    id("com.example.direct") version "1.0"',
      '    // id("commented.out.plugin")',
      '    jacoco',
      '}',
      'dependencies { id("outside.block") }',
    ].join('\n')
    assert.deepEqual(readBuildPluginIds(kts), ['com.example.direct'])
  })

  test('plugins {} の中の括弧の後の id も読む', () => {
    const kts = 'plugins {\n    id("a.b") version System.getProperty("x")\n    id("c.d")\n}\n'
    assert.deepEqual(readBuildPluginIds(kts), ['a.b', 'c.d'])
  })

  test('policy.json の gradlePlugins のキー（$comment を除く）を読む', () => {
    const policy = { gradlePlugins: { $comment: 'x', 'a.b': 'Apache-2.0', 'c.d': 'MIT' } }
    assert.deepEqual(readPolicyPluginIds(policy), ['a.b', 'c.d'])
  })

  test('gradlePlugins がなければ失敗にする', () => {
    assert.throws(() => readPolicyPluginIds({}), /gradlePlugins/)
  })
})

describe('一覧の照合', () => {
  test('一致していれば問題はない', () => {
    assert.deepEqual(findPluginProblems(['a.b', 'c.d'], ['c.d', 'a.b']), [])
  })

  test('ライセンスの一覧にないプラグインを見つける', () => {
    const problems = findPluginProblems(['a.b', 'c.d'], ['a.b'])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /c\.d/)
  })

  test('使っていないプラグインがライセンスの一覧にあれば見つける', () => {
    const problems = findPluginProblems(['a.b'], ['a.b', 'x.y'])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /x\.y/)
  })

  test('. のない id（Gradle の組み込み）は、どちらにあっても対象にしない', () => {
    assert.deepEqual(findPluginProblems(['java', 'a.b'], ['a.b', 'jacoco']), [])
  })

  test('同じ id が重なっても 1 つとして数える', () => {
    assert.deepEqual(findPluginProblems(['a.b', 'a.b'], ['a.b']), [])
  })
})
