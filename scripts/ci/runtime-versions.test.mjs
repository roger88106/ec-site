import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  findRuntimeVersionProblems,
  readEnginesNode,
  readJavaFromToml,
  readJavaFromWorkflow,
  readNodeVersionFile,
} from './runtime-versions.mjs'

describe('版の読み取り', () => {
  test('workflow の env の JAVA_VERSION を読む', () => {
    assert.equal(readJavaFromWorkflow("env:\n  NODE_VERSION: '24'\n  JAVA_VERSION: '21'\n"), '21')
    assert.equal(readJavaFromWorkflow('env:\n  JAVA_VERSION: 21 # 注\n'), '21')
    assert.equal(readJavaFromWorkflow('env:\n  OTHER: 1\n'), undefined)
  })

  test('libs.versions.toml の java を読む', () => {
    assert.equal(readJavaFromToml('[versions]\njava = "21"\njavax = "9"\n'), '21')
    assert.equal(readJavaFromToml('[versions]\njavax = "9"\n'), undefined)
  })

  test('.node-version の最初の行を読む（先頭の v は外す）', () => {
    assert.equal(readNodeVersionFile('24\n'), '24')
    assert.equal(readNodeVersionFile('v24.1.0\n'), '24.1.0')
    assert.equal(readNodeVersionFile('\n'), undefined)
  })

  test('package.json の engines.node を読む', () => {
    assert.equal(readEnginesNode('{"engines":{"node":">=24"}}'), '>=24')
    assert.equal(readEnginesNode('{}'), undefined)
  })
})

describe('版の一致の検査', () => {
  const ok = { workflowJava: '21', tomlJava: '21', nodeFile: '24', enginesNode: '>=24' }

  test('一致していれば問題はない', () => {
    assert.deepEqual(findRuntimeVersionProblems(ok), [])
    assert.deepEqual(findRuntimeVersionProblems({ ...ok, nodeFile: '25.2.0' }), [])
  })

  test('JDK の版が違えば失敗にする', () => {
    const problems = findRuntimeVersionProblems({ ...ok, tomlJava: '17' })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /JDK/)
  })

  test('.node-version が engines の範囲を満たさなければ失敗にする', () => {
    const problems = findRuntimeVersionProblems({ ...ok, nodeFile: '22' })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /Node/)
  })

  test('engines が >=N の形でなければ理由を出して失敗にする', () => {
    const problems = findRuntimeVersionProblems({ ...ok, enginesNode: '^24' })
    assert.equal(problems.length, 1)
    assert.match(problems[0], />=/)
  })

  test('読めない値があれば失敗にする', () => {
    assert.equal(findRuntimeVersionProblems({ ...ok, workflowJava: undefined }).length, 1)
    assert.equal(findRuntimeVersionProblems({ ...ok, nodeFile: undefined }).length, 1)
    assert.equal(findRuntimeVersionProblems({ ...ok, enginesNode: undefined }).length, 1)
  })
})
