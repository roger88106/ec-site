import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { compare, percent2 } from './coverage-baseline.mjs'

const SCRIPT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'coverage-baseline.mjs')
/** リポジトリのルート（scripts/ci/report/ の 3 つ上） */
const ROOT = path.resolve(path.dirname(SCRIPT), '../../..')

/** Vitest の json-summary の小さな例（行 80.00%、分岐 50.00%） */
const REPORT = JSON.stringify({
  total: { lines: { covered: 80, total: 100 }, branches: { covered: 1, total: 2 } },
})

/** 基準のファイルの内容を作る（行 80、分岐 50、許容の幅は引数） */
const baselineOf = (tolerance = 0, line = 80, branch = 50) =>
  JSON.stringify({
    targets: {
      store: { line: { value: line, tolerance }, branch: { value: branch, tolerance } },
    },
  })

/** JaCoCo の XML の小さな例（行 80.00%、分岐 50.00%）。全体の counter は最後の package の後に並ぶ */
const JACOCO = [
  '<report name="ecsite">',
  '<package name="com/ecsite/a">',
  '<sourcefile name="A.java"><counter type="LINE" missed="20" covered="80"/></sourcefile>',
  '<counter type="LINE" missed="20" covered="80"/>',
  '</package>',
  '<counter type="LINE" missed="20" covered="80"/><counter type="BRANCH" missed="1" covered="1"/>',
  '</report>',
].join('\n')

/** 行の全体が 0 の json-summary */
const NO_LINES = JSON.stringify({ total: { lines: { covered: 0, total: 0 }, branches: { covered: 0, total: 0 } } })

let dir
before(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'coverage-baseline-'))
  writeFileSync(path.join(dir, 'report.json'), REPORT)
  writeFileSync(path.join(dir, 'report.xml'), JACOCO)
  writeFileSync(path.join(dir, 'empty.json'), NO_LINES)
  writeFileSync(path.join(dir, 'broken.xml'), '<report name="ecsite"></report>')
})
after(() => rmSync(dir, { recursive: true, force: true }))

/** script を流す。基準のファイルは、渡されたときだけ dir の中に書く。summary は標準出力に出る */
function run(baseline, args = ['store', 'report.json'], extraEnv = {}) {
  if (baseline !== undefined) writeFileSync(path.join(dir, 'baseline.json'), baseline)
  const env = { ...process.env }
  delete env.GITHUB_STEP_SUMMARY
  Object.assign(env, extraEnv)
  const result = spawnSync(process.execPath, [SCRIPT, ...args, '--baseline', 'baseline.json'], {
    cwd: dir,
    env,
    encoding: 'utf8',
  })
  return { status: result.status, out: `${result.stdout}${result.stderr}` }
}

describe('割合の丸め', () => {
  test('小数第 2 位にし、全体が 0 のときは 100 にする', () => {
    assert.equal(percent2(5876, 5991), 98.08)
    assert.equal(percent2(1, 3), 33.33)
    assert.equal(percent2(0, 0), 100)
  })
})

describe('基準との比較', () => {
  const entry = (tolerance) => ({ line: { value: 80, tolerance }, branch: { value: 50, tolerance } })
  const actual = (line, branch) => ({ line, branch })

  test('許容の幅の中なら成功、幅より下なら失敗にする', () => {
    assert.equal(compare(entry(1), actual(79, 50)).failures.length, 0)
    assert.equal(compare(entry(1), actual(78.99, 50)).failures.length, 1)
  })

  test('失敗の内容に、種類、基準、今回、差を持つ', () => {
    const [failure] = compare(entry(0), actual(79.5, 50)).failures
    assert.deepEqual(failure, { metric: 'line', baseline: 80, actual: 79.5, diff: -0.5 })
  })
})

describe('基準の検査（CLI）', () => {
  test('基準を下回ると失敗にして、対象・基準・今回・差・直し方を出す', () => {
    const { status, out } = run(baselineOf(0, 90, 50))
    assert.equal(status, 1)
    assert.match(out, /store/)
    assert.match(out, /90/)
    assert.match(out, /80/)
    assert.match(out, /-10/)
    assert.match(out, /--write/)
  })

  test('基準と同じなら成功にする', () => {
    const { status, out } = run(baselineOf())
    assert.equal(status, 0)
    assert.doesNotMatch(out, /警告：/)
  })

  test('基準を上回ると成功にして、警告を出す', () => {
    const { status, out } = run(baselineOf(0, 70, 50))
    assert.equal(status, 0)
    assert.match(out, /^警告：.*--write/m)
  })

  test('許容の幅より低いと失敗にし、幅の中なら成功にする', () => {
    assert.equal(run(baselineOf(5, 84, 50)).status, 0)
    assert.equal(run(baselineOf(5, 86, 50)).status, 1)
  })

  test('報告がないと失敗にする', () => {
    const { status, out } = run(baselineOf(), ['store', 'missing.json'])
    assert.equal(status, 1)
    assert.match(out, /missing\.json/)
  })

  test('基準のファイルに対象がないと失敗にする', () => {
    const { status, out } = run(baselineOf(), ['admin', 'report.json'])
    assert.equal(status, 1)
    assert.match(out, /admin/)
  })

  test('基準のファイルの値の形が違うと失敗にする', () => {
    const entryOf = (line) => JSON.stringify({ targets: { store: { line, branch: { value: 50, tolerance: 0 } } } })
    assert.equal(run(entryOf({ value: '80', tolerance: 0 })).status, 1)
    assert.equal(run(entryOf({ value: 80, tolerance: -1 })).status, 1)
    assert.equal(run(entryOf({ value: 80 })).status, 1)
    assert.equal(run('{ not json').status, 1)
  })

  test('分岐だけが基準を下回っても失敗にする', () => {
    const { status, out } = run(baselineOf(0, 80, 60))
    assert.equal(status, 1)
    assert.match(out, /分岐：基準 60/)
    assert.doesNotMatch(out, /行：基準/)
  })

  test('JaCoCo の XML の報告も読んで比べる', () => {
    assert.equal(run(baselineOf(), ['store', 'report.xml']).status, 0)
    assert.equal(run(baselineOf(0, 90, 50), ['store', 'report.xml']).status, 1)
  })

  test('行の全体が 0 の報告は、成功にせず失敗にする', () => {
    const { status, out } = run(baselineOf(0, 0, 0), ['store', 'empty.json'])
    assert.equal(status, 1)
    assert.match(out, /行がない/)
  })

  test('分岐の全体が 0 の報告は、分岐を 100 とみなす', () => {
    const noBranches = JSON.stringify({ total: { lines: { covered: 80, total: 100 }, branches: { covered: 0, total: 0 } } })
    writeFileSync(path.join(dir, 'no-branches.json'), noBranches)
    assert.equal(run(baselineOf(0, 80, 100), ['store', 'no-branches.json']).status, 0)
  })

  test('package も全体の counter もない XML は失敗にする', () => {
    const { status, out } = run(baselineOf(0, 0, 0), ['store', 'broken.xml'])
    assert.equal(status, 1)
    assert.match(out, /行がない/)
  })

  test('基準のファイルに targets がない、branch の値が違う、ファイルがないと失敗にする', () => {
    assert.equal(run(JSON.stringify({ other: {} })).status, 1)
    const branchBroken = JSON.stringify({
      targets: { store: { line: { value: 80, tolerance: 0 }, branch: { value: 'x', tolerance: 0 } } },
    })
    assert.equal(run(branchBroken).status, 1)
    rmSync(path.join(dir, 'baseline.json'))
    const { status, out } = run(undefined)
    assert.equal(status, 1)
    assert.match(out, /基準のファイルがありません/)
  })

  test('失敗したときは、summary にも対象・基準・今回・差・直し方を書く', () => {
    const summary = path.join(dir, 'summary.md')
    writeFileSync(summary, '')
    run(baselineOf(0, 90, 50), ['store', 'report.json'], { GITHUB_STEP_SUMMARY: summary })
    const text = readFileSync(summary, 'utf8')
    assert.match(text, /store/)
    assert.match(text, /90\.00%/)
    assert.match(text, /80\.00%/)
    assert.match(text, /-10\.00/)
    assert.match(text, /--write/)
  })

  test('上回ったときは、summary に警告を書く', () => {
    const summary = path.join(dir, 'summary.md')
    writeFileSync(summary, '')
    run(baselineOf(0, 70, 50), ['store', 'report.json'], { GITHUB_STEP_SUMMARY: summary })
    assert.match(readFileSync(summary, 'utf8'), /警告：/)
  })

  test('直し方の --write のコマンドは、script と報告をリポジトリのルートからのパスで示し、そのまま解決できる', () => {
    const { out } = run(baselineOf(0, 90, 50))
    const [, scriptPath, reportPath] = out.match(/node (\S+coverage-baseline\.mjs) store (\S+) --write/)
    assert.equal(path.resolve(ROOT, scriptPath), SCRIPT)
    assert.equal(path.resolve(ROOT, reportPath), path.join(dir, 'report.json'))
    assert.ok(existsSync(path.resolve(ROOT, scriptPath)))
  })

  test('引数が足りないと、使い方を出して終了コード 2 にする', () => {
    assert.equal(run(baselineOf(), ['store']).status, 2)
  })
})

describe('--write', () => {
  const written = () => JSON.parse(readFileSync(path.join(dir, 'baseline.json'), 'utf8')).targets

  test('報告の値で基準を書き換える（上げるとき）', () => {
    const { status } = run(baselineOf(0, 70, 40), ['store', 'report.json', '--write'])
    assert.equal(status, 0)
    assert.deepEqual(written().store, { line: { value: 80, tolerance: 0 }, branch: { value: 50, tolerance: 0 } })
  })

  test('報告の値で基準を書き換える（下げるとき）。許容の幅と、ほかの対象は変えない', () => {
    const base = JSON.stringify({
      targets: {
        store: { line: { value: 95, tolerance: 2 }, branch: { value: 90, tolerance: 0 } },
        admin: { line: { value: 70, tolerance: 0 }, branch: { value: 60, tolerance: 0 } },
      },
    })
    const { status } = run(base, ['store', 'report.json', '--write'])
    assert.equal(status, 0)
    assert.deepEqual(written().store, { line: { value: 80, tolerance: 2 }, branch: { value: 50, tolerance: 0 } })
    assert.deepEqual(written().admin, { line: { value: 70, tolerance: 0 }, branch: { value: 60, tolerance: 0 } })
  })

  test('書き換えた後は、同じ報告で成功し、警告も出ない', () => {
    run(baselineOf(0, 99, 99), ['store', 'report.json', '--write'])
    const { status, out } = run(undefined)
    assert.equal(status, 0)
    assert.doesNotMatch(out, /警告：/)
  })

  test('行の全体が 0 の報告では、書き換えずに失敗にする', () => {
    const original = baselineOf(0, 99, 99)
    const { status } = run(original, ['store', 'empty.json', '--write'])
    assert.equal(status, 1)
    assert.equal(readFileSync(path.join(dir, 'baseline.json'), 'utf8'), original)
  })

  test('報告がないと、書き換えずに失敗にする', () => {
    const original = baselineOf(0, 99, 99)
    const { status } = run(original, ['store', 'missing.json', '--write'])
    assert.equal(status, 1)
    assert.equal(readFileSync(path.join(dir, 'baseline.json'), 'utf8'), original)
  })
})

describe('mutation score（PIT の XML）', () => {
  /** 実際の mutations.xml から切り出した 7 件（検出 4、生き残り 3：Order 2、Product 1）。57.14% */
  const PIT = path.join(path.dirname(SCRIPT), 'testdata', 'pit-mutations.xml')
  const TARGET = 'backend-domain'
  const mutationBaseline = (value = 57.14, tolerance = 0) =>
    JSON.stringify({ targets: { [TARGET]: { mutation: { value, tolerance } } } })
  const runPit = (baseline, args = [TARGET, PIT], extraEnv = {}) => run(baseline, args, extraEnv)

  test('基準を下回ると失敗にして、対象・基準・今回・差・直し方と、生き残ったクラスを出す', () => {
    const { status, out } = runPit(mutationBaseline(60))
    assert.equal(status, 1)
    assert.match(out, /backend-domain/)
    assert.match(out, /mutation score/)
    assert.match(out, /60\.00%/)
    assert.match(out, /57\.14%/)
    assert.match(out, /-2\.86/)
    assert.match(out, /--write/)
    assert.match(out, /domain\.order\.Order/)
  })

  test('基準と同じなら成功にし、許容の幅の中なら成功にする', () => {
    const same = runPit(mutationBaseline())
    assert.equal(same.status, 0)
    assert.doesNotMatch(same.out, /警告：/)
    assert.equal(runPit(mutationBaseline(60, 3)).status, 0)
    assert.equal(runPit(mutationBaseline(60, 2)).status, 1)
  })

  test('基準を上回ると成功にして、警告を出す', () => {
    const { status, out } = runPit(mutationBaseline(50))
    assert.equal(status, 0)
    assert.match(out, /^警告：.*mutation score.*--write/m)
  })

  test('変異が 0 の報告は、成功にせず失敗にする', () => {
    writeFileSync(path.join(dir, 'pit-empty.xml'), '<mutations partial="true">\n</mutations>\n')
    const { status, out } = runPit(mutationBaseline(0), [TARGET, 'pit-empty.xml'])
    assert.equal(status, 1)
    assert.match(out, /変異がない/)
  })

  test('報告の種類と対象の基準が合わないと失敗にする（JaCoCo と PIT を取り違えない）', () => {
    const pitForCoverage = run(baselineOf(), ['store', PIT])
    assert.equal(pitForCoverage.status, 1)
    assert.match(pitForCoverage.out, /mutation score/)
    const jacocoForMutation = runPit(mutationBaseline(), [TARGET, 'report.xml'])
    assert.equal(jacocoForMutation.status, 1)
    assert.match(jacocoForMutation.out, /JaCoCo/)
  })

  test('基準のファイルの mutation の値の形が違うと失敗にする', () => {
    const of = (mutation) => JSON.stringify({ targets: { [TARGET]: { mutation } } })
    assert.equal(runPit(of({ value: '57', tolerance: 0 })).status, 1)
    assert.equal(runPit(of({ value: 101, tolerance: 0 })).status, 1)
    assert.equal(runPit(of({ value: 57, tolerance: -1 })).status, 1)
    assert.equal(runPit(JSON.stringify({ targets: { [TARGET]: {} } })).status, 1)
  })

  test('summary に、mutation score と、生き残った変異が多いクラスの表を書く', () => {
    const summary = path.join(dir, 'summary.md')
    writeFileSync(summary, '')
    runPit(mutationBaseline(), [TARGET, PIT], { GITHUB_STEP_SUMMARY: summary })
    const text = readFileSync(summary, 'utf8')
    assert.match(text, /mutation score/)
    assert.match(text, /57\.14%/)
    assert.match(text, /生き残った変異/)
    assert.match(text, /\| `com\.ecsite\.module\.order\.domain\.order\.Order` \| 2 \| 3 \|/)
  })

  test('--write で、報告の値に書き換える（許容の幅と、ほかの対象は変えない）', () => {
    const base = JSON.stringify({
      targets: {
        [TARGET]: { mutation: { value: 90, tolerance: 1.5 } },
        store: { line: { value: 70, tolerance: 0 }, branch: { value: 60, tolerance: 0 } },
      },
    })
    const { status } = runPit(base, [TARGET, PIT, '--write'])
    assert.equal(status, 0)
    const written = JSON.parse(readFileSync(path.join(dir, 'baseline.json'), 'utf8')).targets
    assert.deepEqual(written[TARGET], { mutation: { value: 57.14, tolerance: 1.5 } })
    assert.deepEqual(written.store, { line: { value: 70, tolerance: 0 }, branch: { value: 60, tolerance: 0 } })
  })

  test('--write は、変異が 0 の報告では書き換えずに失敗にする', () => {
    writeFileSync(path.join(dir, 'pit-empty.xml'), '<mutations partial="true"></mutations>')
    const original = mutationBaseline(90)
    const { status } = runPit(original, [TARGET, 'pit-empty.xml', '--write'])
    assert.equal(status, 1)
    assert.equal(readFileSync(path.join(dir, 'baseline.json'), 'utf8'), original)
  })

  test('閉じの </mutations> がない（途中で切れた）XML は、失敗にする', () => {
    writeFileSync(path.join(dir, 'pit-truncated.xml'), `<mutations partial="true"><mutation detected='true' status='KILLED'>`)
    const { status, out } = runPit(mutationBaseline(0), [TARGET, 'pit-truncated.xml'])
    assert.equal(status, 1)
    assert.match(out, /途中で切れ/)
  })
})
