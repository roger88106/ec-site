import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findPluginProblems, readBuildPlugins, readCatalogPlugins, readPolicyPluginIds } from './gradle-plugins.mjs'

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

  test('[plugins.名前] の表は読めないので、行を出す', () => {
    const result = readCatalogPlugins('[plugins]\na = { id = "a.b" }\n[plugins.baz]\nid = "org.baz"\nversion = "1"\n')
    assert.deepEqual(result.ids, ['a.b'])
    assert.equal(result.unknown[0].line, 3)
  })

  test('複数行の表は読めないので、行を出す', () => {
    const result = readCatalogPlugins('[plugins]\nfoo = {\n  id = "a.b",\n  version = "1"\n}\n')
    assert.equal(result.unknown[0].line, 2)
    assert.ok(result.unknown.length >= 1)
  })

  test('id のない表と、知らない行は読めないので、行を出す', () => {
    const result = readCatalogPlugins('[plugins]\nfoo = { version = "1" }\nbar\n')
    assert.deepEqual(result.unknown.map((item) => item.line), [2, 3])
  })

  test('[plugins] の外の知らない行は読まない', () => {
    assert.deepEqual(readCatalogPlugins('[versions]\nfoo = {\n[bundles]\nx\n'), { ids: [], unknown: [] })
  })
})

describe('build.gradle.kts のプラグインの読み取り', () => {
  test('plugins {} の中の id("…") を読み、alias・組み込み・コメントは読まない', () => {
    const kts = [
      'plugins {',
      '    java',
      '    `java-library`',
      '    alias(libs.plugins.spring.boot)',
      '    id("com.example.direct") version "1.0"',
      '    // id("commented.out.plugin")',
      '    jacoco',
      '}',
      'dependencies { id("outside.block") }',
    ].join('\n')
    assert.deepEqual(readBuildPlugins(kts), { ids: ['com.example.direct'], unknown: [] })
  })

  test('plugins {} の中の括弧の後の id も読む', () => {
    const kts = 'plugins {\n    id("a.b") version System.getProperty("x")\n    id("c.d") version "1" apply false\n}\n'
    assert.deepEqual(readBuildPlugins(kts).ids, ['a.b', 'c.d'])
    assert.deepEqual(readBuildPlugins(kts).unknown, [])
  })

  test('plugins { と同じ行の id も読む', () => {
    assert.deepEqual(readBuildPlugins('plugins { id("a.b") version "1" }\n').ids, ['a.b'])
    assert.deepEqual(readBuildPlugins('plugins { id("a.b"); id("c.d") }\n').ids, ['a.b', 'c.d'])
    assert.deepEqual(readBuildPlugins('plugins { id("a.b") }\nid("outside.block")\n').ids, ['a.b'])
  })

  test('kotlin("名前") は org.jetbrains.kotlin.名前 として読む', () => {
    assert.deepEqual(readBuildPlugins('plugins {\n    kotlin("jvm") version "2.0"\n}\n'), { ids: ['org.jetbrains.kotlin.jvm'], unknown: [] })
  })

  test('plugins {} の中の読めない行は、行を出す', () => {
    const result = readBuildPlugins('plugins {\n    id("a.b")\n    maven-publish(1)\n    foo("x")\n}\n')
    assert.deepEqual(result.ids, ['a.b'])
    assert.deepEqual(result.unknown.map((item) => item.line), [3, 4])
  })

  test('apply(plugin = "…") は読めないので、行を出す', () => {
    const result = readBuildPlugins('plugins {\n    java\n}\napply(plugin = "com.example.applied")\n')
    assert.deepEqual(result.ids, [])
    assert.deepEqual(result.unknown.map((item) => item.line), [4])
  })

  test('コメントの中の apply は読まない', () => {
    assert.deepEqual(readBuildPlugins('// apply(plugin = "x.y")\n'), { ids: [], unknown: [] })
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
