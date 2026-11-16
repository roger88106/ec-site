import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findVersionLiteralProblems } from './runtime-versions.mjs'

describe('workflow と action のバージョンの参照の検査', () => {
  /** 1 つの step の with を、workflow の形にする */
  const withOf = (lines) => ['jobs:', '  a:', '    steps:', '      - uses: actions/setup@v1', '        with:', ...lines.map((line) => `          ${line}`)].join('\n')

  test('バージョンのファイルを参照する形は通る', () => {
    const text = withOf(['distribution: temurin', 'java-version-file: .java-version', 'node-version-file: .node-version'])
    assert.deepEqual(findVersionLiteralProblems([{ path: '.github/workflows/ci.yml', text }]), [])
  })

  test('新しい job に java-version や node-version を直接書けば、ファイルと場所を出して失敗にする', () => {
    const text = ['jobs:', '  new-job:', '    steps:', '      - uses: actions/setup-java@v6', '        with:', '          java-version: 21', '      - uses: actions/setup-node@v7', '        with:', "          node-version: '24'"].join('\n')
    assert.deepEqual(findVersionLiteralProblems([{ path: '.github/workflows/new.yml', text }]), [
      '.github/workflows/new.yml の jobs.new-job.steps[0].with：java-version に 21 を直接書いている。この入力は使わない（java-version-file: .java-version、node-version-file: .node-version と書く）',
      '.github/workflows/new.yml の jobs.new-job.steps[1].with：node-version に 24 を直接書いている。この入力は使わない（java-version-file: .java-version、node-version-file: .node-version と書く）',
    ])
  })

  test('バージョンのファイルでない値（別のファイル、行列、変数）も失敗にする', () => {
    const lines = [
      'java-version: ${{ matrix.java }}',
      'node-version-file: package.json',
      'node-version: ${{ env.NODE_VERSION }}',
      'java-version-file: .tool-versions',
      'java-version: [17, 21]',
    ]
    for (const line of lines) {
      assert.equal(findVersionLiteralProblems([{ path: 'a.yml', text: withOf([line]) }]).length, 1, line)
    }
  })

  test('フロー形式（with: { ... }）の中も読み、コメントの中は見ない', () => {
    const flow = ['jobs:', '  a:', '    steps:', '      - { uses: actions/setup-node@v7, with: { node-version-file: package.json } }'].join('\n')
    assert.equal(findVersionLiteralProblems([{ path: 'a.yml', text: flow }]).length, 1)
    const reference = 'jobs:\n  a:\n    steps:\n      - with: { distribution: temurin, java-version-file: .java-version }'
    assert.deepEqual(findVersionLiteralProblems([{ path: 'a.yml', text: reference }]), [])
    assert.deepEqual(findVersionLiteralProblems([{ path: 'a.yml', text: withOf(['# java-version: 21', 'node-version-file: .node-version # 24']) }]), [])
  })
})
