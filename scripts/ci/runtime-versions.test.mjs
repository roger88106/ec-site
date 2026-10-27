import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  findJavaVersionDefinitionProblems,
  findReactSpecifierProblems,
  findRuntimeVersionProblems,
  findVersionLiteralProblems,
  readEnginesNode,
  readJavaFromToml,
  readJavaFromWorkflow,
  readNodeVersionFile,
} from './runtime-versions.mjs'

describe('バージョンの読み取り', () => {
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

describe('バージョンの一致の検査', () => {
  const ok = { workflowJava: '21', tomlJava: '21', nodeFile: '24', enginesNode: '>=24' }

  test('一致していれば問題はない', () => {
    assert.deepEqual(findRuntimeVersionProblems(ok), [])
    assert.deepEqual(findRuntimeVersionProblems({ ...ok, nodeFile: '25.2.0' }), [])
  })

  test('JDK のバージョンが違えば失敗にする', () => {
    const problems = findRuntimeVersionProblems({ ...ok, tomlJava: '17' })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /JDK/)
  })

  test('.node-version が engines の範囲を満たさなければ失敗にする', () => {
    const problems = findRuntimeVersionProblems({ ...ok, nodeFile: '22' })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /Node/)
  })

  test('.node-version が数で始まらない（lts/*、node）なら理由を出して失敗にする', () => {
    for (const nodeFile of ['lts/*', 'node']) {
      const problems = findRuntimeVersionProblems({ ...ok, nodeFile })
      assert.equal(problems.length, 1)
      assert.match(problems[0], /数/)
    }
  })

  test('engines が >=N の形でなければ理由を出して失敗にする', () => {
    const problems = findRuntimeVersionProblems({ ...ok, enginesNode: '^24' })
    assert.equal(problems.length, 1)
    assert.match(problems[0], />=/)
  })

  test('読めないバージョンは、ファイル:行 内容（理由）の形で出す', () => {
    assert.deepEqual(findRuntimeVersionProblems({ ...ok, nodeFile: 'lts/*' }), ['.node-version:1 lts/*（数字のバージョン（24 など）だけを扱う）'])
    assert.deepEqual(findRuntimeVersionProblems({ ...ok, enginesNode: '^24', enginesLine: 7 }), ['package.json:7 ^24（>=N の形だけを扱う）'])
  })

  test('読めない値があれば失敗にする', () => {
    assert.equal(findRuntimeVersionProblems({ ...ok, workflowJava: undefined }).length, 1)
    assert.equal(findRuntimeVersionProblems({ ...ok, nodeFile: undefined }).length, 1)
    assert.equal(findRuntimeVersionProblems({ ...ok, enginesNode: undefined }).length, 1)
  })
})

describe('workflow と action のバージョンの参照の検査', () => {
  const ok = [
    '      - uses: actions/setup-java@v6',
    '        with:',
    '          distribution: temurin',
    '          java-version: ${{ env.JAVA_VERSION }}',
    '      - uses: actions/setup-node@v7',
    '        with:',
    '          node-version-file: .node-version',
  ].join('\n')

  test('決めた 1 か所を参照する形は通る', () => {
    assert.deepEqual(findVersionLiteralProblems([{ path: '.github/workflows/ci.yml', text: ok }]), [])
  })

  test('新しい job に java-version や node-version を直接書けば、ファイルと行を出して失敗にする', () => {
    const text = ['jobs:', '  new-job:', '    steps:', '      - uses: actions/setup-java@v6', '        with:', '          java-version: 21', '      - uses: actions/setup-node@v7', '        with:', "          node-version: '24'"].join('\n')
    const problems = findVersionLiteralProblems([{ path: '.github/workflows/new.yml', text }])
    assert.equal(problems.length, 2)
    assert.match(problems[0], /new\.yml:6/)
    assert.match(problems[1], /new\.yml:9/)
  })

  test('参照の形でない値（別のファイル、行列、引用符つきの直書き）も失敗にする', () => {
    const lines = [
      'java-version: ${{ matrix.java }}',
      'node-version-file: package.json',
      'node-version: ${{ env.NODE_VERSION }}',
      'java-version-file: .java-version',
      "java-version: '21'",
      'java-version: [17, 21]',
    ]
    for (const line of lines) {
      assert.equal(findVersionLiteralProblems([{ path: 'a.yml', text: `          ${line}` }]).length, 1, line)
    }
  })

  test('リストの項目の先頭（- ）にあっても検出し、コメントの中は見ない', () => {
    assert.equal(findVersionLiteralProblems([{ path: 'a.yml', text: '      - java-version: 21' }]).length, 1)
    const text = '# java-version: 21\n          java-version: ${{ env.JAVA_VERSION }} # 21'
    assert.deepEqual(findVersionLiteralProblems([{ path: 'a.yml', text }]), [])
  })
})

describe('フロー形式のバージョンの指定の検査', () => {
  test('with: { ... } の中に直接書いたバージョンを、ファイルと行を出して失敗にする', () => {
    const lines = [
      '        with: { node-version: 20 }',
      '        with: { distribution: temurin, java-version: 21 }',
      "        with: {distribution: temurin,java-version: '21'}",
      '      - { uses: actions/setup-node@v7, with: { node-version-file: package.json } }',
    ]
    for (const line of lines) {
      const problems = findVersionLiteralProblems([{ path: 'a.yml', text: `jobs:\n${line}` }])
      assert.equal(problems.length, 1, line)
      assert.match(problems[0], /a\.yml:2/, line)
    }
  })

  test('with: { ... } の中でも、決めた 1 か所を参照する形は通る', () => {
    const text = [
      "        with: { distribution: temurin, java-version: '${{ env.JAVA_VERSION }}' }",
      '        with: { java-version: ${{ env.JAVA_VERSION }}, cache: gradle }',
      '        with: { node-version-file: .node-version }',
    ].join('\n')
    assert.deepEqual(findVersionLiteralProblems([{ path: 'a.yml', text }]), [])
  })
})

describe('JAVA_VERSION の定義の場所の検査', () => {
  const ci = "name: CI\nenv:\n  NODE_OPTIONS: x\n  JAVA_VERSION: '21'\njobs:\n  a:\n    steps: []\n"

  test('ci.yml の最上位の env の 1 か所だけに書いたものは通る', () => {
    assert.deepEqual(findJavaVersionDefinitionProblems([{ path: '.github/workflows/ci.yml', text: ci }]), [])
  })

  test('job の env（ブロック形式とフロー形式）で上書きすれば、ファイルと行を出して失敗にする', () => {
    const text = `${ci}  b:\n    env:\n      JAVA_VERSION: '17'\n  c:\n    env: { JAVA_VERSION: 17 }\n`
    const problems = findJavaVersionDefinitionProblems([{ path: '.github/workflows/ci.yml', text }])
    assert.equal(problems.length, 2)
    assert.match(problems[0], /ci\.yml:10/)
    assert.match(problems[1], /ci\.yml:12/)
  })

  test('別の workflow・action のファイルに書いたものと、最上位の env に 2 度書いたものも失敗にする', () => {
    const other = findJavaVersionDefinitionProblems([
      { path: '.github/workflows/ci.yml', text: ci },
      { path: '.github/workflows/other.yml', text: "env:\n  JAVA_VERSION: '17'\n" },
    ])
    assert.equal(other.length, 1)
    assert.match(other[0], /other\.yml:2/)
    const twice = findJavaVersionDefinitionProblems([{ path: '.github/workflows/ci.yml', text: ci.replace('jobs:', "  JAVA_VERSION: '17'\njobs:") }])
    assert.equal(twice.length, 1)
    assert.match(twice[0], /ci\.yml:5/)
  })

  test('引用符で囲んだ最上位のキー（"jobs":）の下の JAVA_VERSION を、最上位の env の中と誤って許さない', () => {
    const path = '.github/workflows/ci.yml'
    for (const key of ['"jobs":', "'jobs':", '? jobs\n:']) {
      const problems = findJavaVersionDefinitionProblems([{ path, text: `env:\n  A: 1\n${key}\n  JAVA_VERSION: '17'\n` }])
      assert.equal(problems.length, 1, key)
    }
  })

  test('参照（${{ env.JAVA_VERSION }}）とコメントは定義と読まない', () => {
    const text = `${ci}    # JAVA_VERSION: 17\n    with:\n      java-version: \${{ env.JAVA_VERSION }}\n`
    assert.deepEqual(findJavaVersionDefinitionProblems([{ path: '.github/workflows/ci.yml', text }]), [])
  })
})

describe('React の指定の書き方の検査', () => {
  const pkg = (path, dependencies, devDependencies = {}, peerDependencies = {}) => ({
    path,
    text: JSON.stringify({ dependencies, devDependencies, peerDependencies }),
  })

  test('react・react-dom・@types の指定が全て ^ の範囲なら問題がない', () => {
    const files = [
      pkg('frontend/admin/package.json', { react: '^19.3.1', 'react-dom': '^19.3.1' }, { '@types/react': '^19.2.18', '@types/react-dom': '^19.2.7' }),
      pkg('frontend/shared/package.json', {}, { react: '^19.3.1' }),
    ]
    assert.deepEqual(findReactSpecifierProblems(files), [])
  })

  test('固定のバージョンの指定を失敗にする（ファイルと名前を出す）', () => {
    const problems = findReactSpecifierProblems([pkg('frontend/store/package.json', { react: '19.3.1', 'react-dom': '^19.3.1' })])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /frontend\/store\/package\.json.*react.*19\.3\.1/)
  })

  test('devDependencies の @types の固定も失敗にする', () => {
    const problems = findReactSpecifierProblems([pkg('a/package.json', {}, { '@types/react': '19.2.18' })])
    assert.equal(problems.length, 1)
  })

  test('peerDependencies は範囲で持つので対象にしない', () => {
    assert.deepEqual(findReactSpecifierProblems([pkg('a/package.json', { react: '^19.3.1' }, {}, { react: '>=19' })]), [])
  })

  test('React の指定が 1 件もなければ失敗にする', () => {
    assert.equal(findReactSpecifierProblems([pkg('a/package.json', { lodash: '1.0.0' })]).length, 1)
  })
})
