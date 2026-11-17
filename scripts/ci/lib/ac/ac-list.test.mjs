// lib/ac/ac-list.mjs のテスト（node --test scripts/ci/lib/ac/ac-list.test.mjs）
import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { parseSkeleton } from '../../spec/spec-structure.mjs'
import { parseSpecAcDefinitions } from '../../../shared/docs/ac-definitions.mjs'
import { addAcsToList, findMarks, listModuleNames, MODULE_AC_SECTION, parseAcList, readInProgressSpecs, renderModuleTable } from './ac-list.mjs'

/** リポジトリのルート */
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../../')

/** AC 一覧の、元の spec の列の値（MVP の spec の番号） */
const ORIGIN = 'MVP-001'

/** AC 一覧の表の見出しと区切りの行 */
const LIST_HEADER = ['| AC | モジュール | 状態 | 元の spec | 変えた spec | 今の内容 |', '|----|------------|------|-----------|-------------|----------|']

/** AC 一覧の行を作る */
function listRow({ ac = 'AC-1', module = 'order', status = '有効', origin = ORIGIN, changedBy = '—', content = '注文できる。' } = {}) {
  return `| ${ac} | ${module} | ${status} | ${origin} | ${changedBy} | ${content} |`
}

/** AC 一覧の文書を作る */
function listDoc(...rows) {
  return ['# AC 一覧', '', '説明。', '', '<!-- generated:ac-list -->', ...LIST_HEADER, ...rows, '<!-- /generated:ac-list -->', ''].join('\n')
}

/** AC 一覧の行を読む（問題がないことも確認する） */
function rowsOf(...rows) {
  const read = parseAcList(listDoc(...rows))
  assert.deepEqual(read.problems, [])
  return read.rows
}

/** ③ に生成する表の、見出しと区切りの行 */
const MODULE_HEADER = ['| AC | 今の内容 | 元の spec |', '|----|----------|-----------|']

/** ③ の生成の表の行を作る */
function moduleRow(ac, content, origin = ORIGIN) {
  return `| ${ac} | ${content} | ${origin} |`
}

describe('AC 一覧の読み取り', () => {
  test('6 列の行を読む。改行コードが CRLF でも、今の内容に CR が残らない', () => {
    const rows = parseAcList(listDoc(listRow({ content: '注文できる。' })).replaceAll('\n', '\r\n')).rows
    assert.deepEqual(rows.map(({ ac, module, status, origin, changedBy, content }) => ({ ac, module, status, origin, changedBy, content })), [
      { ac: 1, module: 'order', status: '有効', origin: ORIGIN, changedBy: '—', content: '注文できる。' },
    ])
  })

  test('先頭のセルが AC- の行のセルが足りない・多いと、読み飛ばさず問題にする', () => {
    const short = '| AC-2 | order | 有効 | — | 注文できる。 |'
    const long = `${listRow({ ac: 'AC-3' })} 余り |`
    const { rows, problems } = parseAcList(listDoc(listRow(), short, long))
    assert.deepEqual(rows.map((row) => row.ac), [1])
    assert.equal(problems.length, 2)
    assert.match(problems[0], /AC-2/)
    assert.match(problems[1], /AC-3/)
  })

  test('マーカーの範囲の中の、表の行でない行（セルの中の改行の続き）を問題にする', () => {
    const { problems } = parseAcList(listDoc('| AC-1 | order | 有効 | ' + ORIGIN + ' | — | 一行目', '二行目 |'))
    assert.ok(problems.length > 0)
    assert.match(problems[0], /表の行でない行がある/)
  })

  test('AC の番号を読めない行（0 埋め、小文字、空白の区切り）を問題にする', () => {
    for (const ac of ['AC-07-1', 'ac-7-1', 'AC 7-1']) {
      const { problems } = parseAcList(listDoc(listRow({ ac })))
      assert.equal(problems.length, 1, ac)
      assert.match(problems[0], /AC の番号の形が違う/, ac)
    }
  })

  test('表の見出しが AC・モジュール・状態・元の spec・変えた spec・今の内容でなければ問題にする', () => {
    const document = listDoc(listRow()).replace('| AC | モジュール | 状態 |', '| AC | 状態 | モジュール |')
    const { problems } = parseAcList(document)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC 一覧の列が .* でない/)
  })

  test('AC 一覧のマーカーの範囲がない、重複している、閉じが前にあるときは失敗にする', () => {
    const body = [...LIST_HEADER, listRow()]
    const cases = [
      [['# AC 一覧', ...body].join('\n'), /範囲がない/],
      [['<!-- generated:ac-list -->', ...body, '<!-- /generated:ac-list -->', '<!-- generated:ac-list -->', '<!-- /generated:ac-list -->'].join('\n'), /開きが 2 個、閉じが 2 個/],
      [['<!-- /generated:ac-list -->', ...body, '<!-- generated:ac-list -->'].join('\n'), /より前にある/],
      [['<!-- generated:ac-list -->', ...body].join('\n'), /開きが 1 個、閉じが 0 個/],
    ]
    for (const [document, reason] of cases) {
      const { problems } = parseAcList(document)
      assert.ok(problems.length > 0, document)
      assert.match(problems[0], reason, document)
    }
  })

  test('既知のマーカーのどれとも一致しない generated: のコメント（綴りの誤り）を失敗にする', () => {
    const document = listDoc(listRow()).replace('# AC 一覧', '# AC 一覧\n\n<!-- generated:modul-ac -->')
    const { problems } = parseAcList(document)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /知らない生成の印/)
  })

  test('表の途中（AC の行の間）に空行があれば、表がそこで切れるので失敗にする', () => {
    const { problems } = parseAcList(listDoc(listRow({ ac: 'AC-1' }), '', listRow({ ac: 'AC-2' })))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^9 行：AC 一覧の表の途中に空行がある/)
  })

  test('表の見出しと区切りの行の間に空行があれば、失敗にする', () => {
    const document = listDoc(listRow()).replace(`${LIST_HEADER[0]}\n`, `${LIST_HEADER[0]}\n\n`)
    const { problems } = parseAcList(document)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC 一覧の表の途中に空行がある/)
  })

  test('マーカーの範囲で、表より前と表より後にある空行は問題にしない', () => {
    const document = listDoc(listRow()).replace('<!-- generated:ac-list -->\n', '<!-- generated:ac-list -->\n\n').replace('\n<!-- /generated:ac-list -->', '\n\n\n<!-- /generated:ac-list -->')
    assert.deepEqual(parseAcList(document).problems, [])
  })

  test('生成の印が、行に単独で書かれていない（前後に文字がある）と問題にし、前後の空白だけなら通す', () => {
    for (const line of ['説明 <!-- generated:ac-list -->', '<!-- /generated:ac-list --> 説明']) {
      const { marks, problems } = findMarks(line)
      assert.deepEqual(marks, [], line)
      assert.equal(problems.length, 1, line)
      assert.match(problems[0], /^1 行：生成の印は、1 行に単独で書く/, line)
    }
    const { marks, problems } = findMarks('  <!-- generated:ac-list -->  ')
    assert.deepEqual(problems, [])
    assert.deepEqual(marks, [{ name: 'ac-list', close: false, line: 1 }])
  })

  test('AC 一覧に ③ の生成の印（module-ac）があると問題にし、コードの中に引用しただけなら通す', () => {
    const { problems } = parseAcList(listDoc(listRow()).replace('# AC 一覧', '# AC 一覧\n\n<!-- generated:module-ac -->'))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC 一覧に ③ の生成の印（generated:module-ac）がある/)
    const quoted = listDoc(listRow()).replace('# AC 一覧', '# AC 一覧\n\n印は `<!-- generated:module-ac -->` の形。')
    assert.deepEqual(parseAcList(quoted).problems, [])
  })

  test('区切りの行は、列が AC 一覧より多くても少なくても、区切りの形でなくても問題にし、揃えの記号と短い線は通す', () => {
    const documentWith = (separator) => ['<!-- generated:ac-list -->', LIST_HEADER[0], separator, listRow(), '<!-- /generated:ac-list -->'].join('\n')
    const fewer = '|----|------------|------|-----------|-------------|'
    const more = `${LIST_HEADER[1]}----|`
    const notSeparator = '| a | b | c | d | e | f |'
    for (const separator of [fewer, more, notSeparator]) {
      const { problems } = parseAcList(documentWith(separator))
      assert.equal(problems.length, 1, separator)
      assert.match(problems[0], /^3 行：AC 一覧の表の区切りの行が 6 列でない/, separator)
    }
    for (const separator of ['|:---|---:|:---:|-|--|---|']) {
      assert.deepEqual(parseAcList(documentWith(separator)).problems, [], separator)
    }
  })
})

describe('AC 一覧の表の見出しの行', () => {
  const document = (...inner) => ['# AC 一覧', '', '<!-- generated:ac-list -->', ...inner, '<!-- /generated:ac-list -->', ''].join('\n')

  test('マーカーの範囲が空、または空行だけで、表の見出しの行がなければ問題にする', () => {
    for (const inner of [[], [''], ['', '']]) {
      const { problems } = parseAcList(document(...inner))
      assert.equal(problems.length, 1, JSON.stringify(inner))
      assert.match(problems[0], /表の見出しの行がない/)
    }
  })

  test('見出しと区切りの行だけで AC の行が 0 件の範囲は、問題にしない', () => {
    assert.deepEqual(parseAcList(document(...LIST_HEADER)).problems, [])
  })
})

describe('③ のモジュールの名前', () => {
  test('バックエンドのモジュールのディレクトリと、③ の文書の両方がある名前だけ', () => {
    const names = listModuleNames({
      backendDirs: ['order', 'cart', 'newmodule'],
      docFiles: ['order.md', 'cart.md', 'template.md', 'acceptance-criteria.md', 'glossary.md', 'draft.md'],
    })
    assert.deepEqual([...names].sort(), ['cart', 'order'])
  })
})

describe('③ の「今有効な AC」の表の生成', () => {
  test('有効・変更の、そのモジュールの AC を、AC の順（MVP、次に spec の番号と連番）に置く', () => {
    const rows = rowsOf(
      listRow({ ac: 'AC-7-10', content: 'c。' }),
      listRow({ ac: 'AC-10', content: 'b。' }),
      listRow({ ac: 'AC-7-2', content: 'd。' }),
      listRow({ ac: 'AC-9', content: 'a。' }),
      listRow({ ac: 'AC-11', module: 'cart' }),
      listRow({ ac: 'AC-12', module: '', status: '廃止', changedBy: 'MVP-019', content: '' }),
      listRow({ ac: 'AC-13', module: 'common', content: '根拠は[共通コード・ログ・可観測性](../../../../docs/02-設計/common.md)の「メトリクスと死活監視」。' }),
    )
    assert.equal(
      renderModuleTable(rows, 'order'),
      [...MODULE_HEADER, moduleRow('AC-9', 'a。'), moduleRow('AC-10', 'b。'), moduleRow('AC-7-2', 'd。'), moduleRow('AC-7-10', 'c。')].join('\n'),
    )
  })

  test('列「元の spec」は、AC 一覧の元の spec の番号だけで書く（リンクにしない）', () => {
    const rows = rowsOf(listRow({ ac: 'AC-1', content: '一つ目。' }), listRow({ ac: 'AC-7-1', origin: '7', content: '二つ目。' }))
    assert.equal(renderModuleTable(rows, 'order'), [...MODULE_HEADER, '| AC-1 | 一つ目。 | MVP-001 |', '| AC-7-1 | 二つ目。 | 7 |'].join('\n'))
  })

  test('対応する AC が 0 件なら「なし。」の 1 行', () => {
    assert.equal(renderModuleTable(rowsOf(listRow({ module: 'cart' })), 'order'), 'なし。')
  })

  test('common の AC は、どの ③ の表にも入れない', () => {
    const rows = rowsOf(listRow({ module: 'common', content: '根拠は[共通コード・ログ・可観測性](../../../../docs/02-設計/common.md)の「メトリクスと死活監視」。' }))
    assert.equal(renderModuleTable(rows, 'common'), 'なし。')
  })
})

describe('③ の「今有効な AC」の節の名前', () => {
  test('生成の表を置く節の名前が、リポジトリの雛形の骨組みの ## にある（節の名前の正本は雛形）', () => {
    const { sections, problems } = parseSkeleton(readFileSync(path.join(ROOT, 'docs/03-機能仕様/template.md'), 'utf8'))
    assert.deepEqual(problems, [])
    assert.ok(sections.includes(MODULE_AC_SECTION), sections.join('、'))
  })
})

describe('AC 一覧への転記', () => {
  const definition = (ac) => ({ ac, specRel: '5-x', content: '転記する。' })
  const added = (ac) => `| AC-${ac} |  | 有効 | 5 | — | 転記する。 |`

  test('既存の行の全てより後ろの AC は、範囲の末尾に空行があっても、最後の AC の行の直後に入れる', () => {
    const base = listDoc(listRow({ ac: 'AC-1' })).replace('\n<!-- /generated:ac-list -->', '\n\n\n<!-- /generated:ac-list -->')
    const expected = ['# AC 一覧', '', '説明。', '', '<!-- generated:ac-list -->', ...LIST_HEADER, listRow({ ac: 'AC-1' }), added(5), '', '', '<!-- /generated:ac-list -->', ''].join('\n')
    assert.equal(addAcsToList(base, [definition(5)]), expected)
  })

  test('既存の行が 0 件なら、区切りの行の直後に入れる', () => {
    const base = listDoc().replace('\n<!-- /generated:ac-list -->', '\n\n<!-- /generated:ac-list -->')
    const expected = ['# AC 一覧', '', '説明。', '', '<!-- generated:ac-list -->', ...LIST_HEADER, added(5), added(6), '', '<!-- /generated:ac-list -->', ''].join('\n')
    assert.equal(addAcsToList(base, [definition(6), definition(5)]), expected)
  })

  test('既存の行の間の AC は、その順の位置に入れる', () => {
    const base = listDoc(listRow({ ac: 'AC-1' }), listRow({ ac: 'AC-9' }))
    const expected = listDoc(listRow({ ac: 'AC-1' }), added(5), listRow({ ac: 'AC-9' }), added(12))
    assert.equal(addAcsToList(base, [definition(12), definition(5)]), expected)
  })

  test('--write の転記は、読めない AC 一覧（マーカーの閉じがない）では例外を投げる', () => {
    const broken = listDoc(listRow()).replace('<!-- /generated:ac-list -->', '')
    const { definitions } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。', '5-x')
    assert.throws(() => addAcsToList(broken, definitions), /AC 一覧を読めないので転記しない/)
  })

  test('--write は、範囲の外と既存の行の改行コード（CRLF）を変えず、転記する AC がなければ文書を変えない', () => {
    const document = listDoc(listRow({ ac: 'AC-1' })).replaceAll('\n', '\r\n')
    assert.equal(addAcsToList(document, []), document)
    const { definitions } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。', '5-x')
    assert.equal(addAcsToList(document, definitions), listDoc(listRow({ ac: 'AC-1' }), '| AC-5-1 |  | 有効 | 5 | — | 一つ目。 |').replaceAll('\n', '\r\n'))
  })

  test('AC 一覧への転記：MVP の形の行の後に、新しい形（AC-<spec の番号>-<連番>）の行を、spec の番号と連番の順に置き、元の spec は 0 埋めのない番号だけで書く。読み戻せる', () => {
    const { definitions } = parseSpecAcDefinitions('- **AC-12** 一つ目。\n- **AC-7-2** 三つ目。\n- **AC-7-1** 二つ目。', '7-dev-process')
    const empty = ['<!-- generated:ac-list -->', ...LIST_HEADER, '<!-- /generated:ac-list -->'].join('\n')
    const markdown = addAcsToList(empty, definitions)
    assert.match(markdown, /^\| AC-7-1 \| {2}\| 有効 \| 7 \| — \| 二つ目。 \|$/m)
    assert.ok(markdown.indexOf('| AC-12 |') < markdown.indexOf('| AC-7-1 |'))
    assert.ok(markdown.indexOf('| AC-7-1 |') < markdown.indexOf('| AC-7-2 |'))
    const { rows, problems } = parseAcList(markdown)
    assert.deepEqual(problems, [])
    assert.deepEqual(rows.map((row) => row.ac), [12, '7-1', '7-2'])
  })
})

describe('進行中の spec を読む範囲', () => {
  test('docs/08-spec/ の直下の spec.md だけを読み、アーカイブと spec.md のないディレクトリは読まない', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      mkdirSync(path.join(dir, '5-x'))
      writeFileSync(path.join(dir, '5-x', 'spec.md'), '- **AC-5-1** 一つ目。\n')
      mkdirSync(path.join(dir, '6-plan-only'))
      writeFileSync(path.join(dir, '6-plan-only', 'plan.md'), '- **AC-6-1** plan の中。\n')
      mkdirSync(path.join(dir, 'archive', 'mvp', '001-old'), { recursive: true })
      writeFileSync(path.join(dir, 'archive', 'mvp', '001-old', 'spec.md'), '- **AC-1\n壊れた spec。\n')
      const { specs, problems } = readInProgressSpecs(dir)
      assert.deepEqual(problems, [])
      assert.deepEqual(specs.map((spec) => [spec.rel, spec.definitions.map((definition) => definition.ac)]), [['5-x', ['5-1']]])
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('進行中の spec の崩れた定義の行を、spec のパスを付けて問題にする', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      mkdirSync(path.join(dir, '5-x'))
      writeFileSync(path.join(dir, '5-x', 'spec.md'), '- **AC-5-1**\n')
      const { problems } = readInProgressSpecs(dir)
      assert.equal(problems.length, 1)
      assert.match(problems[0], /5-x\/spec\.md/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('進行中の spec の、定義の形でない書き方を、spec のパスを付けて問題にする', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      mkdirSync(path.join(dir, '5-x'))
      writeFileSync(path.join(dir, '5-x', 'spec.md'), '- AC-5-1 文。\n')
      const { specs, problems } = readInProgressSpecs(dir)
      assert.deepEqual(specs.map((spec) => spec.definitions), [[]])
      assert.equal(problems.length, 1)
      assert.match(problems[0], /5-x\/spec\.md：1 行：AC の定義の行が崩れている/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })
})
