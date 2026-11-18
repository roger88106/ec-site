import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findActionPinProblems, isActionPinTarget, runActionPins } from './action-pins.mjs'

const SHA = '3d3c42e5aac5ba805825da76410c181273ba90b1'
const WORKFLOW = '.github/workflows/new.yml'
const ACTION = '.github/actions/a/action.yml'

/** step の uses を 1 つ持つ workflow の内容 */
const workflowWithStep = (uses) => `jobs:\n  a:\n    steps:\n      - uses: ${uses}\n`

/** workflow を検査して、問題の件数を返す */
const countProblems = (text, path = WORKFLOW) => findActionPinProblems([{ path, text }]).length

describe('action の固定の検査・検出すべき例', () => {
  test('タグ、ブランチ、短い SHA の指定は失敗にする', () => {
    for (const uses of ['actions/checkout@v4', 'actions/checkout@main', 'actions/checkout@3d3c42e']) {
      assert.equal(countProblems(workflowWithStep(uses)), 1, uses)
    }
  })

  test('大文字を含む 40 桁、39 桁、41 桁の 16 進は失敗にする', () => {
    for (const sha of [SHA.toUpperCase(), `${SHA.slice(0, 39)}A`, SHA.slice(1), `${SHA}0`]) {
      assert.equal(countProblems(workflowWithStep(`actions/checkout@${sha}`)), 1, sha)
    }
  })

  test('@ のない指定と owner のない指定は失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('actions/checkout')), 1)
    assert.equal(countProblems(workflowWithStep(`checkout@${SHA}`)), 1)
  })

  test('パスの付いた指定のタグは失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('github/codeql-action/init@v3')), 1)
  })

  test('job の uses（再利用の workflow）のタグは失敗にする', () => {
    const text = 'jobs:\n  a:\n    uses: org/repo/.github/workflows/a.yml@v1\n'
    assert.equal(countProblems(text), 1)
  })

  test('複合の action の runs.steps にあるタグの指定は失敗にする', () => {
    const text = "runs:\n  using: composite\n  steps:\n    - uses: actions/checkout@v4\n      shell: bash\n"
    assert.equal(countProblems(text, ACTION), 1)
  })

  test('docker:// の指定は失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('docker://alpine:3.20')), 1)
    assert.equal(countProblems(workflowWithStep(`docker://alpine@sha256:${'a'.repeat(64)}`)), 1)
  })

  test('./ で始まらない相対のパスは失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('../other/action')), 1)
    assert.equal(countProblems(workflowWithStep('.github/actions/x')), 1)
  })

  test('.github/actions/ と .github/workflows/ の外のローカルの指定と、.. を含む指定は失敗にする', () => {
    for (const uses of ['./tools/setup', './.github/actions/../x', './.github/workflows/../../x', './.github/other/x']) {
      assert.equal(countProblems(workflowWithStep(uses)), 1, uses)
    }
    assert.equal(countProblems('jobs:\n  a:\n    uses: ./tools/build.yml\n'), 1)
  })

  test('改行を含むローカルの指定は失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('"./.github/actions/x\\nfoo"')), 1)
  })

  test('失敗の出力の修正の方法に、複合の action を .github/actions/ の下に置くことを示す', () => {
    const [problem] = findActionPinProblems([{ path: WORKFLOW, text: workflowWithStep('./tools/setup') }])
    assert.ok(problem.includes('.github/actions/'), problem)
  })

  test('マージキー（<<）が step・job・runs・文書の直下にあれば、判定できないとして失敗にする', () => {
    const cases = [
      [WORKFLOW, `x: &x\n  uses: actions/checkout@${SHA}\njobs:\n  a:\n    steps:\n      - <<: *x\n`],
      [WORKFLOW, 'x: &x\n  runs-on: a\njobs:\n  a:\n    <<: *x\n    steps: []\n'],
      [ACTION, 'x: &x\n  using: composite\nruns:\n  <<: *x\n  steps: []\n'],
      [WORKFLOW, 'x: &x\n  jobs: {}\n<<: *x\n'],
    ]
    for (const [path, text] of cases) {
      const problems = findActionPinProblems([{ path, text }])
      assert.equal(problems.length, 1, text)
      assert.ok(problems[0].includes(path) && problems[0].includes('マージキー'), problems[0])
    }
  })

  test('uses の値が数値、空、null なら失敗にする', () => {
    for (const value of ['123', "''", 'null', '']) {
      assert.equal(countProblems(workflowWithStep(value)), 1, `[${value}]`)
    }
  })

  test('引用符の中で SHA の後に空白や文字が続けば失敗にする', () => {
    assert.equal(countProblems(workflowWithStep(`"actions/checkout@${SHA} "`)), 1)
    assert.equal(countProblems(workflowWithStep(`'actions/checkout@${SHA}x'`)), 1)
    assert.equal(countProblems(workflowWithStep(`"actions/checkout@${SHA}\\n"`)), 1)
  })

  test('YAML のアンカーとエイリアスで入れたタグの指定は、展開後の値で判定して失敗にする', () => {
    const text = 'jobs:\n  a:\n    steps:\n      - &s\n        uses: actions/checkout@v4\n      - *s\n'
    assert.equal(countProblems(text), 2)
  })

  test('拡張子 .yaml の workflow と action.yaml の中のタグは失敗にする', () => {
    const text = workflowWithStep('actions/checkout@v4')
    assert.equal(countProblems(text, '.github/workflows/a.yaml'), 1)
    assert.equal(countProblems('runs:\n  steps:\n    - uses: actions/checkout@v4\n', '.github/actions/a/action.yaml'), 1)
  })

  test('読めない YAML は、ファイルを示して失敗にする', () => {
    const problems = findActionPinProblems([{ path: WORKFLOW, text: 'jobs: [\n  a: {' }])
    assert.equal(problems.length, 1)
    assert.ok(problems[0].startsWith(`${WORKFLOW} `), problems[0])
    assert.ok(problems[0].includes('YAML を読めない'), problems[0])
  })

  test('式を含む指定は、step と job のどちらも失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('actions/checkout@${{ matrix.ref }}')), 1)
    assert.equal(countProblems('jobs:\n  a:\n    uses: org/repo/.github/workflows/a.yml@${{ matrix.ref }}\n'), 1)
  })

  test('.github/actions/ の下の 2 階層下のフォルダーの action.yml も対象にして失敗にする', () => {
    assert.equal(isActionPinTarget('.github/actions/a/b/action.yml'), true)
    assert.equal(countProblems('runs:\n  steps:\n    - uses: actions/checkout@v4\n', '.github/actions/a/b/action.yml'), 1)
  })

  test('対象のファイルが 0 件なら、終了コード 2 で理由を出す', () => {
    const lines = []
    const code = runActionPins([], () => '', (line) => lines.push(line))
    assert.equal(code, 2)
    assert.match(lines.join('\n'), /1 件も見つからない/)
  })

  test('改行が CRLF の workflow の中のタグの指定は失敗にする', () => {
    assert.equal(countProblems(workflowWithStep('actions/checkout@v4').replaceAll('\n', '\r\n')), 1)
  })

  test('1 つのファイルに複数の文書があるときと、1 つの step に uses が 2 つあるときは、読めない YAML として失敗にする', () => {
    const multiple = `---\njobs: {}\n---\njobs:\n  a:\n    steps:\n      - uses: actions/checkout@${SHA}\n`
    const duplicate = `jobs:\n  a:\n    steps:\n      - uses: actions/checkout@${SHA}\n        uses: actions/checkout@v4\n`
    for (const text of [multiple, duplicate]) {
      const problems = findActionPinProblems([{ path: WORKFLOW, text }])
      assert.equal(problems.length, 1, text)
      assert.ok(problems[0].includes('YAML を読めない'), problems[0])
    }
  })
})

describe('action の固定の検査・検出してはいけない例', () => {
  test('40 桁の小文字の SHA の固定は、後ろのコメントを見ずに通る', () => {
    assert.equal(countProblems(workflowWithStep(`actions/checkout@${SHA} # v7.0.1`)), 0)
  })

  test('ローカルの action と、ローカルの再利用の workflow は通る', () => {
    assert.equal(countProblems(workflowWithStep('./.github/actions/setup-frontend')), 0)
    assert.equal(countProblems('jobs:\n  a:\n    uses: ./.github/workflows/build.yml\n'), 0)
  })

  test('./.github/actions/ の下の深いローカルの指定は通る', () => {
    assert.equal(countProblems(workflowWithStep('./.github/actions/a/b')), 0)
  })

  test('パスの付いた固定と、job の uses の固定は通る', () => {
    assert.equal(countProblems(workflowWithStep(`github/codeql-action/init@${SHA}`)), 0)
    assert.equal(countProblems(`jobs:\n  a:\n    uses: org/repo/.github/workflows/a.yml@${SHA}\n`), 0)
  })

  test('uses のない step は通る', () => {
    assert.equal(countProblems('jobs:\n  a:\n    steps:\n      - run: echo hi\n'), 0)
  })

  test('run の文字列やコメントの中の uses は見ない', () => {
    const text = [
      'jobs:',
      '  a:',
      '    steps:',
      '      - run: |',
      '          echo "uses: actions/checkout@v4"',
      '        # uses: actions/checkout@v4',
      '',
    ].join('\n')
    assert.equal(countProblems(text), 0)
  })

  test('with の入力の値は見ない', () => {
    const text = `jobs:\n  a:\n    steps:\n      - uses: actions/checkout@${SHA}\n        with:\n          ref: v1\n          uses: v1\n`
    assert.equal(countProblems(text), 0)
  })

  test('.github/ の外の YAML、dependabot.yml、workflows/ のサブフォルダーの yml は対象にしない', () => {
    for (const path of ['docs/a.yml', 'compose.yaml', '.github/dependabot.yml', '.github/workflows/sub/a.yml', '.github/actions/a/other.yml']) {
      assert.equal(isActionPinTarget(path), false, path)
    }
    for (const path of ['.github/workflows/ci.yml', '.github/workflows/ci.yaml', '.github/actions/a/action.yml', '.github/actions/a/b/c/action.yaml']) {
      assert.equal(isActionPinTarget(path), true, path)
    }
  })

  test('フロースタイルの step も展開後の値で判定して通る', () => {
    const text = `jobs:\n  a:\n    steps:\n      - { uses: a/b@${SHA} }\n`
    assert.equal(countProblems(text), 0)
    assert.equal(countProblems('jobs:\n  a:\n    steps:\n      - { uses: a/b@v1 }\n'), 1)
  })

  test('owner と repo に大文字、-、_、. を含む固定は通る', () => {
    assert.equal(countProblems(workflowWithStep(`Azure/login@${SHA}`)), 0)
    assert.equal(countProblems(workflowWithStep(`my-org_1/Repo.name-x@${SHA}`)), 0)
  })

  test('改行が CRLF の、許可する形の workflow は通る', () => {
    assert.equal(countProblems(workflowWithStep(`actions/checkout@${SHA} # v7.0.1`).replaceAll('\n', '\r\n')), 0)
  })

  test('引用符で囲んだ許可する形は通る', () => {
    assert.equal(countProblems(workflowWithStep(`"actions/checkout@${SHA}"`)), 0)
    assert.equal(countProblems(workflowWithStep("'./.github/actions/setup-frontend'")), 0)
  })
})

describe('action の固定の検査・失敗の出力', () => {
  test('ファイル、場所（job と step）、値、固定の形への修正の方法を出す', () => {
    const text = 'jobs:\n  build:\n    steps:\n      - run: echo\n      - uses: actions/checkout@v4\n'
    const [problem, ...rest] = findActionPinProblems([{ path: WORKFLOW, text }])
    assert.deepEqual(rest, [])
    assert.ok(problem.includes(WORKFLOW), problem)
    assert.ok(problem.includes('jobs.build.steps[1].uses'), problem)
    assert.ok(problem.includes('actions/checkout@v4'), problem)
    assert.ok(problem.includes('<owner>/<repo>@<40 桁の小文字の 16 進>'), problem)
  })

  test('job の uses と複合の action の場所も示す', () => {
    const [job] = findActionPinProblems([{ path: WORKFLOW, text: 'jobs:\n  r:\n    uses: o/r/.github/workflows/a.yml@v1\n' }])
    assert.ok(job.includes('jobs.r.uses'), job)
    const [composite] = findActionPinProblems([{ path: ACTION, text: 'runs:\n  steps:\n    - uses: a/b@v1\n' }])
    assert.ok(composite.includes('runs.steps[0].uses'), composite)
  })

  test('文字列でない値は、値の型が分かる形で示す', () => {
    const [problem] = findActionPinProblems([{ path: WORKFLOW, text: workflowWithStep('123') }])
    assert.ok(problem.includes('123'), problem)
    assert.ok(problem.includes('（文字列でない）'), problem)
  })

  test('問題があれば終了コード 1、なければ 0 で、最後の行が要約の行になる', () => {
    const files = { [WORKFLOW]: workflowWithStep('actions/checkout@v4'), '.github/workflows/ok.yml': workflowWithStep(`actions/checkout@${SHA}`) }
    const run = (paths) => {
      const lines = []
      const code = runActionPins(paths, (file) => files[file], (line) => lines.push(line))
      return { code, last: lines.at(-1), lines }
    }
    const failed = run([WORKFLOW, '.github/workflows/ok.yml'])
    assert.equal(failed.code, 1)
    assert.match(failed.last, /^[^：]+：.*2 件.*1 件/)
    assert.ok(failed.lines.some((line) => line.includes(WORKFLOW)))
    const passed = run(['.github/workflows/ok.yml'])
    assert.equal(passed.code, 0)
    assert.match(passed.last, /^[^：]+：.*1 件.*0 件/)
  })
})
