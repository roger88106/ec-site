import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../lib/git/git-env.mjs'
import { findDirectPlugins, findPluginProblems, parseCatalogPlugins, readPolicyPluginIds } from './gradle-plugins.mjs'

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
    assert.deepEqual(parseCatalogPlugins(toml), { ids: ['org.springframework.boot', 'nu.studer.jooq'], unknown: [] })
  })

  test('[plugins] がなければ空', () => {
    assert.deepEqual(parseCatalogPlugins('[versions]\na = "1"\n'), { ids: [], unknown: [] })
  })

  test('文字列の形（id:バージョン）の id を読む', () => {
    assert.deepEqual(parseCatalogPlugins('[plugins]\nfoo = "org.foo.bar:1.0"\n'), { ids: ['org.foo.bar'], unknown: [] })
  })

  test('[plugins.名前] の表の id も読む', () => {
    const result = parseCatalogPlugins('[plugins]\na = { id = "a.b" }\n[plugins.baz]\nid = "org.baz"\nversion = "1"\n')
    assert.deepEqual(result, { ids: ['a.b', 'org.baz'], unknown: [] })
  })

  test('id のない表は読めないので、項目の名前を出す', () => {
    assert.deepEqual(parseCatalogPlugins('[plugins]\nfoo = { version = "1" }\n'), { ids: [], unknown: ['foo'] })
  })

  test('TOML として読めないときは例外にする（検査が失敗する）', () => {
    assert.throws(() => parseCatalogPlugins('[plugins]\nbar\n'))
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

  test('Groovy の apply plugin: の行も見つけ、コメントの中は見つけない', () => {
    const groovy = ["apply plugin: 'com.example.applied'", "// apply plugin: 'commented.out'", "apply from: 'other.gradle'"].join('\n')
    assert.deepEqual(findDirectPlugins(groovy).map((item) => item.line), [1])
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

describe('script として実行する', () => {
  const script = fileURLToPath(new URL('./gradle-plugins.mjs', import.meta.url))
  const catalog = 'backend/ecsite/gradle/libs.versions.toml'
  const build = 'backend/ecsite/build.gradle.kts'
  const policy = 'scripts/ci/licenses/policy.json'
  const baseFiles = {
    [catalog]: '[plugins]\nfoo = { id = "org.foo.bar" }\n',
    [build]: 'plugins {\n    alias(libs.plugins.foo)\n}\n',
    [policy]: JSON.stringify({ gradlePlugins: { 'org.foo.bar': 'MIT' } }),
  }

  /**
   * 一時の Git のリポジトリにファイルを置いて、script を実行する。
   *
   * @param {Record<string, string | null>} changes 基本のファイルを置き換える内容（null は置かない）
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function run(changes = {}) {
    const dir = mkdtempSync(path.join(tmpdir(), 'gradle-plugins-'))
    const env = withoutGitEnv()
    try {
      spawnSync('git', ['init', '-q'], { cwd: dir, env })
      for (const [file, text] of Object.entries({ ...baseFiles, ...changes })) {
        if (text === null) continue
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        writeFileSync(path.join(dir, file), text)
      }
      return spawnSync(process.execPath, [script], { cwd: dir, env, encoding: 'utf8' })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('プラグインの過不足がなければ、終了コード 0 で、要約の行を出して終わる', () => {
    const result = run()
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /Gradle のプラグイン：1 件、読んだファイル：1 件、問題：0 件/)
  })

  test('カタログを通さない書き方があれば、ファイルと行を出し、終了コード 1 で終わる', () => {
    const result = run({ [build]: 'plugins {\n    alias(libs.plugins.foo)\n    id("com.example.direct")\n}\n' })
    assert.equal(result.status, 1, result.stdout)
    assert.ok(result.stdout.includes(`${build}:3 id("com.example.direct")`), result.stdout)
  })

  test('外部のプラグインが 1 件も見つからなければ、何も見ずに通らず、終了コード 1 で終わる', () => {
    const result = run({ [catalog]: '[versions]\na = "1"\n', [policy]: JSON.stringify({ gradlePlugins: {} }) })
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /Gradle のプラグインが 1 件も見つからない/)
  })

  test('方針の gradlePlugins がなければ、終了コード 2 で終わる', () => {
    const result = run({ [policy]: '{}' })
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stdout, /gradlePlugins がない/)
    assert.doesNotMatch(result.stdout, /問題：/)
  })

  test('方針かカタログのファイルがなければ、終了コード 2 で終わる', () => {
    assert.equal(run({ [policy]: null }).status, 2)
    const noCatalog = run({ [catalog]: null })
    assert.equal(noCatalog.status, 2, noCatalog.stdout)
    assert.match(noCatalog.stdout, /libs\.versions\.toml を読めない/)
  })
})
