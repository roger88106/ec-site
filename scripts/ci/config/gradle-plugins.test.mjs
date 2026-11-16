import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findDirectPlugins, findPluginProblems, readCatalogPlugins, readPolicyPluginIds } from './gradle-plugins.mjs'

const toml = [
  '[libraries]',
  'lombok = { module = "org.projectlombok:lombok", id = "not.a.plugin" }',
  '',
  '[plugins]',
  '# コメントの id = "ignored.plugin"',
  'spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }',
  'jooq = { id = "nu.studer.jooq", version.ref = "jooqPlugin" } # 行末のコメント',
  '',
  '[bundles]',
  'x = ["a"]',
].join('\n')

describe('カタログのプラグインの読み取り', () => {
  test('libs.versions.toml の [plugins] の id だけを読む', () => {
    assert.deepEqual(readCatalogPlugins(toml), { ids: ['org.springframework.boot', 'nu.studer.jooq'], unknown: [] })
  })

  test('[plugins] がなければ空', () => {
    assert.deepEqual(readCatalogPlugins('[versions]\na = "1"\n'), { ids: [], unknown: [] })
  })

  test('文字列の形（id:バージョン）の id を読む', () => {
    assert.deepEqual(readCatalogPlugins('[plugins]\nfoo = "org.foo.bar:1.0"\n'), { ids: ['org.foo.bar'], unknown: [] })
  })

  test('[plugins.名前] の表の id も読む', () => {
    const result = readCatalogPlugins('[plugins]\na = { id = "a.b" }\n[plugins.baz]\nid = "org.baz"\nversion = "1"\n')
    assert.deepEqual(result, { ids: ['a.b', 'org.baz'], unknown: [] })
  })

  test('id のない表は読めないので、項目の名前を出す', () => {
    assert.deepEqual(readCatalogPlugins('[plugins]\nfoo = { version = "1" }\n'), { ids: [], unknown: ['foo'] })
  })

  test('TOML として読めないときは例外にする（検査が失敗する）', () => {
    assert.throws(() => readCatalogPlugins('[plugins]\nbar\n'))
  })
})

describe('カタログを通さないプラグインの書き方', () => {
  test('id("…")、kotlin("…")、apply(plugin = "…") の行を見つけ、alias・組み込み・コメントは見つけない', () => {
    const kts = [
      'plugins {',
      '    java',
      '    `java-test-fixtures`',
      '    alias(libs.plugins.spring.boot)',
      '    id("com.example.direct") version "1.0"',
      '    // id("commented.out.plugin")',
      '    kotlin("jvm") version "2.0"',
      '}',
      'apply(plugin = "com.example.applied")',
    ].join('\n')
    assert.deepEqual(findDirectPlugins(kts).map((item) => item.line), [5, 7, 9])
  })
})

describe('policy の読み取り', () => {
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
