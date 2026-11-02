import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  checkFunctionalDocs,
  checkFunctionalSpec,
  checkPlan,
  checkSpec,
  checkSpecDir,
  checkSpecDirNames,
  checkSpecFiles,
  checkStage,
  checkTemplates,
  EXAMPLE_NUMBER,
  FEATURE_SECTIONS,
  findTargets,
  fillTemplate,
  FUNCTIONAL_SPECS,
  readSkeleton,
  REFACTOR_SECTIONS,
} from './spec-structure.mjs'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

/** 機能仕様の節（雛形の骨組みの `##` の名前と順） */
const SECTIONS = ['業務規則', '状態の遷移', 'データモデル', '今有効な AC']

/** 機能仕様の本文（節ごとに文を置く） */
const functionalText = (names) => names.map((name) => `## ${name}\n\n文。\n`).join('\n')

/** 既知の spec の番号（進行中とアーカイブのディレクトリの番号と、見本の番号） */
const KNOWN = new Set(['321', '283', '7', '011', '900'])

/** 節の中身（見出しの下に置く文） */
const BODY = {
  スコープ: '| 含める | 含めない |\n|--------|----------|\n| a | b |\n',
  'User stories': '| US | 誰が | 何をできる | なぜ |\n|----|------|------------|------|\n| US-321-1 | 消費者 | できる | 目的 |\n',
  受け入れ条件: '### US-321-1 要約（C / P1）\n\n- **AC-321-1** 条件 1。\n- **AC-321-2** 条件 2。\n',
  業務ルール: '- **BR-321-1** 規則。\n',
  目的と完了条件: '### DS-321-1 目的（D / P1）\n\n- **DC-321-1** 条件 1。\n- **DC-321-2** 条件 2。\n',
  非機能要件: '- **NFR-321-1** 制約。\n',
  依存: '| 対象 | 関係 |\n|------|------|\n| なし | なし |\n',
  'Non-goals': '| 項目 | 理由 |\n|------|------|\n| a | b |\n',
  'Open questions': 'なし。\n',
}

/**
 * spec.md の文を組み立てる。
 *
 * @param {{ kind?: string, size?: string, sections?: string[], lines?: string[] }} [options] 種類、規模、節の名前（なければ種類で決める）、3 行目の後ろに加える行
 * @returns {string} spec.md の内容
 */
function specText({ kind = '整理', size = 'L', sections, lines = [] } = {}) {
  const names = sections ?? [...(kind === '機能' ? FEATURE_SECTIONS : REFACTOR_SECTIONS), ...(size === 'M' ? ['tasks'] : [])]
  const body = names.map((name) => {
    let content = BODY[name] ?? '文。\n'
    if (name === 'tasks') content = `- [ ] **T321-01-01** 内容（${kind === '機能' ? 'AC' : 'DC'}-321-1）。\n  - 依存：なし\n`
    if (name === '非機能要件' && kind === '整理') content = '- **DN-321-1** 制約。\n'
    return `## ${name}\n\n${content}`
  })
  return [`# Spec 321：題`, '', `種類：${kind}　規模：${size}　ステータス：確定`, '', ...lines, ...body].join('\n')
}

/** plan.md の文 */
const PLAN = [
  '# Plan 321：題',
  '',
  'ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)',
  '',
  '前提：文。',
  '',
  '| 本書の節 | 主な担当 |',
  '|----------|----------|',
  '| 決定 | DS-321-1（DC-321-1〜2） |',
  '',
  '## 決定（DS-321-1：DC-321-1〜2）',
  '',
  '| 何を | どこに | 確認方法 |',
  '|------|--------|----------|',
  '| a | b | c |',
  '',
  '## 段階と変えるファイル',
  '',
  '文。',
  '',
  '## 利用者に確認すること',
  '',
  'なし。',
  '',
].join('\n')

/** 段階 1 のファイルの文 */
const STAGE = [
  '# 段階 1：名前',
  '',
  '- 前提：なし。',
  '- 完了条件：DC-321-1〜2 を満たす。',
  '- 参照：[plan](../plan.md)、DS-321-1',
  '',
  '## タスク',
  '',
  '実装は実装役に任せる。',
  '',
  '- [ ] **T321-01-01** [テスト] 内容（DC-321-1）。',
  '  - 依存：なし',
  '- [ ] **T321-01-02** T321-01-01 のテストが通る実装を書く（DC-321-2）。',
  '  - 依存：T321-01-01',
  '',
].join('\n')

/** 整理・規模 L の spec の全ての文書 */
function baseFiles() {
  return { 'spec.md': specText(), 'plan.md': PLAN, 'tasks/01-name.md': STAGE }
}

/** 文書の中の文字列を置き換えた、文書の組を返す。置き換え元がなければテストの誤りなので失敗にする */
function edit(files, file, from, to) {
  assert.ok(files[file].includes(from), `${file} に「${from}」がない`)
  return { ...files, [file]: files[file].replace(from, to) }
}

/** 文書の組を検査する */
function check(files, options) {
  return checkSpecFiles(files, '321', KNOWN, options)
}

/** 問題が 1 件で、その文が期待の正規表現に合うことを確認する */
function assertOne(problems, pattern) {
  assert.equal(problems.length, 1, `問題が 1 件でない：${problems.join(' / ')}`)
  assert.match(problems[0], pattern)
}

describe('構造の検査の土台', () => {
  test('整理・規模 L の完全な文書の組は問題がない', () => {
    assert.deepEqual(check(baseFiles()), [])
  })

  test('機能・規模 L の文書の組は問題がない', () => {
    assert.deepEqual(check({ 'spec.md': specText({ kind: '機能' }) }), [])
  })

  test('規模 M は最後に tasks の節を置き、その中のタスクを定義として読む', () => {
    assert.deepEqual(check({ 'spec.md': specText({ size: 'M' }) }), [])
    assert.deepEqual(check({ 'spec.md': specText({ kind: '機能', size: 'M' }) }), [])
  })

  test('規模 L に tasks の節があれば失敗にする', () => {
    assertOne(check({ 'spec.md': specText({ sections: [...REFACTOR_SECTIONS, 'tasks'] }) }), /決めた節にない見出し「tasks」/)
  })

  test('spec.md がなければ失敗にする', () => {
    assertOne(check({ 'plan.md': PLAN }), /spec\.md：spec\.md がない/)
  })
})

describe('spec の構造（DC-321-19）', () => {
  test('1 行目が「# Spec <番号>：」でなければ失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', '# Spec 321：', '# Spec 322：')), /spec\.md：1 行目が「# Spec 321：…」でない/)
  })

  test('## の順が違えば失敗にする', () => {
    const text = specText().replace('## スコープ', '## 仮の名前').replace('## 背景と目的', '## スコープ').replace('## 仮の名前', '## 背景と目的')
    assertOne(check({ ...baseFiles(), 'spec.md': text }), /spec\.md：.*位置が違う/)
  })

  test('3 行目の区切りが半角の空白なら失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', '種類：整理　規模', '種類：整理 規模')), /spec\.md：3 行目が「種類：/)
  })

  test('3 行目のコロンが半角なら失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', 'ステータス：確定', 'ステータス:確定')), /spec\.md：3 行目が「種類：/)
  })

  test('ステータスの値が草案・確定でなければ失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', 'ステータス：確定', 'ステータス：下書き')), /3 行目が「種類：/)
  })

  test('ステータスが草案の spec は通る', () => {
    assert.deepEqual(check(edit(baseFiles(), 'spec.md', 'ステータス：確定', 'ステータス：草案')), [])
  })

  test('種類が機能・整理以外、規模が L・M 以外なら失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', '種類：整理', '種類：不具合')), /3 行目が「種類：/)
    assertOne(check(edit(baseFiles(), 'spec.md', '規模：L', '規模：S')), /3 行目が「種類：/)
  })

  test('種類の行と ## の組が合わなければ失敗にする（整理の行に機能の節）', () => {
    const problems = check({ ...baseFiles(), 'spec.md': specText({ kind: '整理', sections: FEATURE_SECTIONS }) })
    assert.ok(problems.some((problem) => /決めた節にない見出し「User stories」/.test(problem)))
    assert.ok(problems.some((problem) => /節「目的と完了条件」がない/.test(problem)))
  })

  test('## の節が欠ける、加わる、2 回ある、番号が付く場合は失敗にする', () => {
    const missing = specText().replace('## 変える範囲\n\n文。\n', '')
    assertOne(check({ ...baseFiles(), 'spec.md': missing }), /節「変える範囲」がない/)
    const extra = `${specText()}\n## 余分な節\n`
    assertOne(check({ ...baseFiles(), 'spec.md': extra }), /決めた節にない見出し「余分な節」/)
    const twice = `${specText()}\n## 依存\n`
    assertOne(check({ ...baseFiles(), 'spec.md': twice }), /「依存」が 2 回ある/)
    const numbered = specText().replace('## 背景と目的', '## 1. 背景と目的')
    const problems = check({ ...baseFiles(), 'spec.md': numbered })
    assert.equal(problems.length, 2)
    assert.ok(problems.some((problem) => /決めた節にない見出し「1\. 背景と目的」/.test(problem)))
  })

  test('3 行目の後ろの「危険度：高」の行は問題にしない', () => {
    assert.deepEqual(check({ ...baseFiles(), 'spec.md': specText({ lines: ['危険度：高', ''] }) }), [])
  })

  test('### の見出しは、名前と順を見ない', () => {
    assert.deepEqual(check({ ...baseFiles(), 'spec.md': `${specText()}\n### 好きな名前\n\n### 別の名前\n` }), [])
  })

  test('3 行目でない行にある「種類：」の行は、3 行目の形の代わりにならない', () => {
    const text = specText().replace('種類：整理　規模：L　ステータス：確定', '文。\n\n種類：整理　規模：L　ステータス：確定')
    assertOne(check({ ...baseFiles(), 'spec.md': text }), /3 行目が「種類：/)
  })

  test('コードブロックの中の見出しは節にしない（チルダ、4 つのバッククォート）', () => {
    const text = `${specText()}\n~~~\n## 余分な節 1\n~~~\n\n\`\`\`\`markdown\n\`\`\`\n## 余分な節 2\n\`\`\`\n\`\`\`\`\n`
    assert.deepEqual(check({ ...baseFiles(), 'spec.md': text }), [])
  })

  test('閉じていないコードブロックは失敗にする', () => {
    assertOne(check({ ...baseFiles(), 'spec.md': `${specText()}\n\`\`\`\n## 読めない節\n` }), /閉じていないコードブロック/)
  })

  test('Setext の見出し（段落の直後の ---）は ## の見出しとして読み、決めた節になければ失敗にする', () => {
    assertOne(check({ ...baseFiles(), 'spec.md': `${specText()}\n区切りのつもり\n---\n` }), /決めた節にない見出し「区切りのつもり」/)
  })

  test('閉じの # のある見出し（## 依存 ##）は、名前を「依存」と読む', () => {
    assert.deepEqual(check(edit(baseFiles(), 'spec.md', '## 依存', '## 依存 ##')), [])
  })

  test('HTML のコメントの中の節と定義は数えない', () => {
    const problems = check(edit(baseFiles(), 'spec.md', '## 依存', '<!--\n## 依存\n-->'))
    assertOne(problems, /節「依存」がない/)
    const hidden = check(edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '<!--\n- **DC-321-2** 条件 2。\n-->'))
    assert.ok(hidden.some((problem) => /DC-321-2 が定義されていない/.test(problem)), hidden.join('\n'))
  })

  test('checkSpec は問題の文だけを返す', () => {
    assert.deepEqual(checkSpec(specText(), '321'), [])
    assert.deepEqual(checkSpec(specText(), '322'), ['1 行目が「# Spec 322：…」でない'])
  })
})

describe('plan の構造（DC-321-19）', () => {
  test('1 行目が「# Plan <番号>：」でなければ失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '# Plan 321：', '# Plan 322：')), /plan\.md：1 行目が「# Plan 321：…」でない/)
  })

  test('3 行目のステータスは、草案か承認済み（tasks 作成済み）でなければ失敗にする', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '承認済み（tasks 作成済み）', '草案')), [])
    assertOne(check(edit(baseFiles(), 'plan.md', '承認済み（tasks 作成済み）', '承認済み')), /3 行目が「ステータス：/)
    assertOne(check(edit(baseFiles(), 'plan.md', 'ステータス：承認', 'ステータス:承認')), /3 行目が「ステータス：/)
    assertOne(check(edit(baseFiles(), 'plan.md', '）　対象：', '） 対象：')), /3 行目が「ステータス：/)
  })

  test('最後の節の名前が違えば失敗にする', () => {
    const problems = check(edit(baseFiles(), 'plan.md', '## 利用者に確認すること', '## 確認すること'))
    assert.ok(problems.some((problem) => /節「利用者に確認すること」がない/.test(problem)))
  })

  test('最後の 2 つの節が入れ替われば失敗にする', () => {
    const swapped = PLAN.replace('## 段階と変えるファイル', '## X').replace('## 利用者に確認すること', '## 段階と変えるファイル').replace('## X', '## 利用者に確認すること')
    assertOne(check({ ...baseFiles(), 'plan.md': swapped }), /plan\.md：.*位置が違う/)
  })

  test('最後の節の後ろに節が加われば失敗にする', () => {
    const problems = check({ ...baseFiles(), 'plan.md': `${PLAN}\n## 決定 2（DS-321-1）\n` })
    assertOne(problems, /決定の節は「段階と変えるファイル」の前に置く/)
  })

  test('決定の節の見出しの末尾が（DS-…）か（US-…）でなければ失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '## 決定（DS-321-1：DC-321-1〜2）', '## 決定')), /決めた節にない見出し「決定」/)
    assertOne(check(edit(baseFiles(), 'plan.md', '## 決定（DS-321-1：DC-321-1〜2）', '## 決定（DC-321-1〜2）')), /決めた節にない見出し/)
  })

  test('決定の節の見出しの US・DS が spec に定義されていなければ失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '## 決定（DS-321-1：DC-321-1〜2）', '## 決定（DS-321-3：DC-321-1〜2）')), /plan\.md：.*DS-321-3 が定義されていない/)
  })

  test('決定の節は何個あってもよく、US の見出しも置ける', () => {
    const plan = PLAN.replace('（DS-321-1：DC-321-1〜2）', '（US-321-1：AC-321-1）')
      .replace('DS-321-1（DC-321-1〜2）', 'US-321-1（AC-321-1）')
      .replace('## 段階と変えるファイル', '## 決定 2（US-321-1：AC-321-2）\n\n文。\n\n## 段階と変えるファイル')
    assert.deepEqual(check({ 'spec.md': specText({ kind: '機能' }), 'plan.md': plan }), [])
  })

  test('plan で参照を太字にしても、定義とは読まず、参照として読む', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '| 決定 | DS-321-1（DC-321-1〜2） |', '| 決定 | **DS-321-1**（**DC-321-1**〜2） |')), [])
    assertOne(check(edit(baseFiles(), 'plan.md', '| 決定 | DS-321-1（DC-321-1〜2） |', '| 決定 | **DC-321-9** |')), /plan\.md：9 行：DC-321-9 が定義されていない/)
  })

  test('checkPlan は問題の文だけを返す', () => {
    assert.deepEqual(checkPlan(PLAN, '321'), [])
  })
})

describe('段階のファイルの構造（DC-321-19）', () => {
  test('ファイル名の番号と 1 行目の段階が違えば失敗にする（02 のファイルが「段階 3」）', () => {
    const files = { ...baseFiles(), 'tasks/02-name.md': STAGE.replace('# 段階 1：', '# 段階 3：').replaceAll('T321-01-', 'T321-02-') }
    assertOne(check(files), /tasks\/02-name\.md：1 行目の段階（3）がファイル名の番号（02）と違う/)
  })

  test('1 行目の形の違いは失敗にする（半角のコロン、段階3、## 段階 3）', () => {
    for (const first of ['# 段階 1:名前', '# 段階1：名前', '## 段階 1：名前', '# 段階 01：名前']) {
      assertOne(check(edit(baseFiles(), 'tasks/01-name.md', '# 段階 1：名前', first)), /tasks\/01-name\.md：1 行目が「# 段階 <N>：…」でない/)
    }
  })

  test('ファイル名の番号が 2 桁でなければ失敗にする', () => {
    for (const name of ['tasks/1-name.md', 'tasks/001-name.md', 'tasks/name.md']) {
      const files = { 'spec.md': specText(), 'plan.md': PLAN, [name]: STAGE }
      assert.ok(check(files).some((problem) => /ファイル名が「NN-<名前>\.md」/.test(problem)), name)
    }
  })

  test('前提・完了条件・参照の箇条がなければ失敗にする', () => {
    for (const item of ['前提', '完了条件', '参照']) {
      const files = { ...baseFiles(), 'tasks/01-name.md': STAGE.split('\n').filter((line) => !line.startsWith(`- ${item}：`)).join('\n') }
      assert.ok(check(files).some((problem) => problem.includes(`箇条「${item}：」がない`)), item)
    }
  })

  test('前提・完了条件・参照の箇条は、「タスク」の節より前にあるものだけを見る', () => {
    const text = STAGE.replace('- 前提：なし。\n', '').replace('## タスク\n', '## タスク\n\n- 前提：本文の中\n')
    assert.ok(check({ ...baseFiles(), 'tasks/01-name.md': text }).some((problem) => problem.includes('箇条「前提：」がない')))
  })

  test('## タスク の節がなければ失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'tasks/01-name.md', '## タスク', '## 作業')), /節「タスク」がない/)
  })

  test('タスクの ID の段階が、ファイルの段階と違えば失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'tasks/01-name.md', '**T321-01-02**', '**T321-02-02**')), /tasks\/01-name\.md：.*T321-02-02 の段階（2）がファイルの段階（1）と違う/)
  })

  test('タスクの ID の段階・連番が 2 桁でなければ失敗にする', () => {
    const problems = check(edit(baseFiles(), 'tasks/01-name.md', '**T321-01-02**', '**T321-1-2**'))
    assert.ok(problems.some((problem) => /タスクの段階と連番が 2 桁でない（T321-1-2）/.test(problem)))
  })

  test('段階のファイルの閉じていないコードブロックは失敗にする', () => {
    assertOne(check({ ...baseFiles(), 'tasks/01-name.md': `${STAGE}\n\`\`\`\n## 読めない節\n` }), /tasks\/01-name\.md：.*閉じていないコードブロック/)
  })

  test('checkStage は問題の文だけを返す', () => {
    assert.deepEqual(checkStage(STAGE, 'tasks/01-name.md', '321'), [])
  })
})

describe('ID の定義と参照（DC-321-20）', () => {
  test('定義のない ID を参照すれば失敗にする（DC-321-99）', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-99 を満たす。')), /plan\.md：5 行：DC-321-99 が定義されていない/)
    assertOne(check(edit(baseFiles(), 'tasks/01-name.md', '（DC-321-1）。', '（DC-321-99）。')), /tasks\/01-name\.md：11 行：DC-321-99 が定義されていない/)
    assertOne(check(edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-321-2** 条件 2。DC-321-99 を見る。')), /spec\.md：.*DC-321-99 が定義されていない/)
  })

  test('検査する spec の中の、存在しない spec の番号（AC-312-1）の参照は失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：AC-312-1 を見る。')), /plan\.md：5 行：AC-312-1 の spec（番号 312）がない/)
  })

  test('存在しない spec の番号の失敗の文は、まだ作業ブランチにない spec を「spec <番号>」と書く案内を含む', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：AC-312-1 を見る。')), /まだ作業ブランチにない spec は「spec <番号>」と書く/)
  })

  test('存在しない spec の番号の ID を定義すれば失敗にする', () => {
    const problems = check(edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-312-2** 条件 2。'))
    assert.ok(problems.some((problem) => /DC-312-2 の番号が spec の番号（321）と違う/.test(problem)))
  })

  test('同じ ID を 2 回定義すれば失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-321-2** 条件 2。\n- **DC-321-1** 重複。')), /spec\.md：.*DC-321-1 を 2 回定義している/)
    assertOne(check(edit(baseFiles(), 'tasks/01-name.md', '- [ ] **T321-01-02**', '- [ ] **T321-01-01** 重複。\n- [ ] **T321-01-02**')), /tasks\/01-name\.md：.*T321-01-01 を 2 回定義している/)
  })

  test('別のファイルでの同じ ID の定義も重複にする（spec.md の規模 M のタスクと段階のファイル）', () => {
    const files = { 'spec.md': specText({ size: 'M' }), 'tasks/01-name.md': STAGE }
    assert.ok(check(files).some((problem) => /T321-01-01 を 2 回定義している/.test(problem)))
  })

  test('範囲の端と、列挙の要素に定義のない番号があれば失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-1〜3 を満たす。')), /DC-321-3 が定義されていない/)
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-1、7 を満たす。')), /DC-321-7 が定義されていない/)
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：T321-01-01〜03 を行う。')), /T321-01-03 が定義されていない/)
  })

  test('範囲が逆順なら失敗にし、展開して 999 件を超えるときも失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-2〜1 を満たす。')), /範囲が逆順（DC-321-2〜1）/)
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-1〜1000 を満たす。')), /範囲が 999 件を超える/)
  })

  test('整理の spec が AC・US・BR・NFR を定義すれば失敗にする', () => {
    const problems = check(edit(baseFiles(), 'spec.md', '- **DN-321-1** 制約。', '- **DN-321-1** 制約。\n- **AC-321-9** 条件。\n- **BR-321-9** 規則。\n- **NFR-321-9** 制約。'))
    assert.equal(problems.length, 3)
    assert.match(problems[0], /整理の spec は AC-321-9 を定義しない/)
  })

  test('整理の spec が US を見出しで定義すれば失敗にする', () => {
    assertOne(check(edit(baseFiles(), 'spec.md', '### DS-321-1 目的（D / P1）', '### DS-321-1 目的（D / P1）\n\n### US-321-8 要約')), /整理の spec は US-321-8 を定義しない/)
  })

  test('機能の spec が DS・DC・DN を定義すれば失敗にする', () => {
    const spec = specText({ kind: '機能' }).replace('- **NFR-321-1** 制約。', '- **NFR-321-1** 制約。\n- **DC-321-9** 条件。\n- **DN-321-9** 制約。\n\n### DS-321-9 目的')
    const files = { 'spec.md': spec }
    const problems = check(files)
    assert.equal(problems.length, 3)
    assert.ok(problems.every((problem) => /機能の spec は .* を定義しない/.test(problem)))
  })

  test('ID の連番を 0 で埋めれば失敗にする（定義と参照）', () => {
    assert.ok(check(edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-321-02** 条件 2。')).some((problem) => /DC-321-02/.test(problem) && /0 で埋めている/.test(problem)))
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-01 を満たす。')), /plan\.md：5 行：連番を 0 で埋めている（DC-321-01）/)
  })

  test('他の spec の ID の参照（AC-283-5）は、定義を調べない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：AC-283-5 と T283-02-04 を参照する。')), [])
  })

  test('MVP の ID（AC-82）、MVP の spec を指す文、古い形式のタスクの ID は見ない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：AC-82 と MVP 012、T011-01-01、T301、T3101 を参照する。')), [])
  })

  test('見本の番号 900 の ID の参照は、定義を調べない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：見本の AC-900-3 を見る。')), [])
  })

  test('1 行のコードの中の ID は読まない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：例は `DC-321-99` と `AC-312-1` と `**DC-321-5**`。')), [])
  })

  test('コードブロックの中の見出しと ID は読まない（バッククォート、チルダ、4 つ以上のバッククォート）', () => {
    const block = ['```markdown', '## 節', '- **DC-321-1** 二重の定義', '- [ ] **T321-01-01** 二重のタスク', 'DC-321-99', '```', '~~~', 'AC-312-1', '~~~', '````', 'DC-321-98', '```', 'DC-321-97', '````'].join('\n')
    assert.deepEqual(check({ ...baseFiles(), 'plan.md': `${PLAN}\n${block}\n`, 'spec.md': `${specText()}\n${block}\n` }), [])
  })

  test('表の中の太字の ID と、箇条の途中の太字の ID は定義とみなさず、参照として読む', () => {
    const files = edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-321-2** 条件 2。\n- 見る **DC-321-1**。\n\n| **DC-321-1** | 表 |')
    assert.deepEqual(check(files), [])
    assertOne(check(edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-321-2** 条件 2。\n- 見る **DC-321-77**。')), /DC-321-77 が定義されていない/)
  })

  test('字下げした箇条の太字の ID は定義にしない', () => {
    const files = edit(baseFiles(), 'spec.md', '- **DC-321-2** 条件 2。', '- **DC-321-2** 条件 2。\n  - **DC-321-1** 子の箇条')
    assert.deepEqual(check(files), [])
  })

  test('ID の後の「7 件」「30 秒」「日付」は列挙にせず、定義のない ID の失敗にしない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-1、7 件の確認と、DC-321-2、30 秒待ち、DC-321-2、2026-11-05 に確認。')), [])
  })

  test('他の spec の番号の列挙（DC-321-1、321-2）は読み、定義のない連番は失敗にする', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-1、321-2。')), [])
    assertOne(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-1、321-9。')), /DC-321-9 が定義されていない/)
  })

  test('日付、commit の ID、issue の番号、TDD、語の途中の AC- は読まない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：2026-11-02 の 9faca7a3 は #321 で TDD する。MAC-321-99 は別。')), [])
  })

  test('番号が文字の記号（DC-n、AC-<番号>-1）は読まない', () => {
    assert.deepEqual(check(edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-n と AC-<番号>-1 の形で書く。')), [])
  })

  test('規模 M は、tasks の節の中のタスクを定義として扱う', () => {
    const spec = specText({ size: 'M' }).replace('- [ ] **T321-01-01** 内容（DC-321-1）。', '- [ ] **T321-01-01** 内容（DC-321-1）。\n- [ ] **T321-01-02** T321-01-01 の次（DC-321-2）。')
    assert.deepEqual(check({ 'spec.md': spec }), [])
  })

  test('resolve を false にすると、定義のない ID を許し、形と既知の番号だけを見る（雛形用）', () => {
    const files = edit(baseFiles(), 'plan.md', '前提：文。', '前提：DC-321-99 と AC-312-1。')
    const problems = check(files, { resolve: false })
    assertOne(problems, /AC-312-1 の spec（番号 312）がない/)
  })
})

describe('固定の節に書き換えた機能仕様（DC-321-21）', () => {
  test('業務規則、状態の遷移、データモデル、今有効な AC の順なら問題がない', () => {
    assert.deepEqual(checkFunctionalSpec(`# 注文\n\n${functionalText(SECTIONS)}`, SECTIONS), [])
  })

  test('節の順が違えば失敗にする', () => {
    const swapped = [SECTIONS[1], SECTIONS[0], ...SECTIONS.slice(2)]
    assertOne(checkFunctionalSpec(functionalText(swapped), SECTIONS), /位置が違う/)
  })

  test('節が欠ける、加わる場合は失敗にする', () => {
    assertOne(checkFunctionalSpec(functionalText(SECTIONS.slice(0, 3)), SECTIONS), /節「今有効な AC」がない/)
    assertOne(checkFunctionalSpec(functionalText([...SECTIONS, '余分']), SECTIONS), /決めた節にない見出し「余分」/)
  })

  test('### の見出しとコードブロックの中の見出しは見ない', () => {
    const text = `${functionalText(SECTIONS)}\n### 小節\n\n\`\`\`\n## 余分\n\`\`\`\n`
    assert.deepEqual(checkFunctionalSpec(text, SECTIONS), [])
  })

  test('閉じていないコードブロックは失敗にする', () => {
    assertOne(checkFunctionalSpec(`${functionalText(SECTIONS)}\n\`\`\`\n`, SECTIONS), /閉じていないコードブロック/)
  })

  test('登録している機能仕様は、書き換え済みの ③ だけである', () => {
    assert.deepEqual(FUNCTIONAL_SPECS, [
      'docs/03-機能仕様/order.md',
      'docs/03-機能仕様/tax.md',
      'docs/03-機能仕様/catalog.md',
      'docs/03-機能仕様/inventory.md',
      'docs/03-機能仕様/media.md',
      'docs/03-機能仕様/cart.md',
      'docs/03-機能仕様/member.md',
      'docs/03-機能仕様/region.md',
      'docs/03-機能仕様/storefront.md',
      'docs/03-機能仕様/payment.md',
      'docs/03-機能仕様/shipping.md',
      'docs/03-機能仕様/notification.md',
      'docs/03-機能仕様/reporting.md',
    ])
  })
})

describe('③ の節の名前を雛形の骨組みから読む（DC-326-3、#325）', () => {
  const HEADING = '## 写して使う骨組み'
  const BODY_LINES = ['# <モジュールの論理名>モジュール', '', '## 業務規則', '', '<注>', '', '## 状態の遷移', '', '## データモデル', '', '## 今有効な AC', '']

  /** 雛形を作る。body は骨組みの中の行、open と close は囲み、before と after は骨組みの節の前と後ろ */
  function template({ body = BODY_LINES, open = '````markdown', close = '````', before = '## この雛形の使い方\n\n表。\n\n', after = '' } = {}) {
    return `# 機能仕様の雛形\n\n${before}${HEADING}\n\n${open}\n${body.join('\n')}\n${close}\n\n${after}`
  }

  const sectionsOf = (text) => readSkeleton(text).sections
  const problemsOf = (text) => readSkeleton(text).problems

  test('骨組みの ## を、出る順に読む', () => {
    assert.deepEqual(readSkeleton(template()), { sections: SECTIONS, problems: [] })
  })

  test('リポジトリの雛形から、固定の 4 つの節を読む', () => {
    assert.deepEqual(readSkeleton(readFileSync(path.join(ROOT, 'docs/03-機能仕様/template.md'), 'utf8')), { sections: SECTIONS, problems: [] })
  })

  test('検出する：雛形の ## の順を入れ替えると、古い順の ③ が失敗になる', () => {
    const sections = sectionsOf(template({ body: ['## 状態の遷移', '## 業務規則', '## データモデル', '## 今有効な AC'] }))
    assertOne(checkFunctionalSpec(functionalText(SECTIONS), sections), /位置が違う/)
  })

  test('検出する：雛形に節を加えると、その節のない ③ が失敗になる', () => {
    const sections = sectionsOf(template({ body: [...BODY_LINES, '## 追加', ''] }))
    assertOne(checkFunctionalSpec(functionalText(SECTIONS), sections), /節「追加」がない/)
  })

  test('検出する：雛形の節を消すと、その節が残る ③ が失敗になる', () => {
    const sections = sectionsOf(template({ body: ['## 業務規則', '## 状態の遷移', '## データモデル'] }))
    assertOne(checkFunctionalSpec(functionalText(SECTIONS), sections), /決めた節にない見出し「今有効な AC」/)
  })

  test('検出する：③ に同じ名前の ## が 2 回ある', () => {
    assertOne(checkFunctionalSpec(functionalText([...SECTIONS, '業務規則']), SECTIONS), /「業務規則」が 2 回ある/)
  })

  test('検出する：全角と半角の空白だけ違う名前は、正規化せずに別の名前にする', () => {
    const sections = sectionsOf(template({ body: ['## 業務規則', '## 状態の遷移', '## データモデル', '## 今有効な　AC'] }))
    const problems = checkFunctionalSpec(functionalText(SECTIONS), sections)
    assert.ok(problems.some((problem) => /節「今有効な　AC」がない/.test(problem)), problems.join('\n'))
    assert.ok(problems.some((problem) => /決めた節にない見出し「今有効な AC」/.test(problem)), problems.join('\n'))
  })

  test('検出する：③ に、骨組みの外（雛形の使い方）の ## の名前がある', () => {
    assertOne(checkFunctionalSpec(functionalText([...SECTIONS, 'この雛形の使い方']), SECTIONS), /決めた節にない見出し「この雛形の使い方」/)
  })

  test('検出する：③ で骨組みと同じ名前の節を ### にすると、その節が欠ける', () => {
    assertOne(checkFunctionalSpec('## 業務規則\n## 状態の遷移\n## データモデル\n### 今有効な AC\n', SECTIONS), /節「今有効な AC」がない/)
  })

  test('失敗にする：見出し「写して使う骨組み」がない（名前を変えた、### にした）', () => {
    assertOne(problemsOf(template().replace(HEADING, '## 写して使う骨格')), /「写して使う骨組み」の見出しがない/)
    assertOne(problemsOf(template().replace(HEADING, '### 写して使う骨組み')), /「写して使う骨組み」の見出しがない/)
  })

  test('失敗にする：その見出しが 2 つある', () => {
    assertOne(problemsOf(template({ after: `${HEADING}\n\n文。\n` })), /「写して使う骨組み」の見出しが 2 つある/)
  })

  test('失敗にする：見出しの節にコードブロックがない', () => {
    assertOne(problemsOf(`# 雛形\n\n${HEADING}\n\n## 業務規則\n\n文。\n`), /コードブロックが 0 個/)
  })

  test('失敗にする：同じ節にコードブロックが 2 つ以上ある', () => {
    assertOne(problemsOf(template({ close: '````\n\n```\n例\n```' })), /コードブロックが 2 個/)
  })

  test('失敗にする：骨組みのコードブロックが閉じていない', () => {
    const text = `# 雛形\n\n${HEADING}\n\n\`\`\`\n${BODY_LINES.join('\n')}\n`
    assert.ok(problemsOf(text).some((problem) => /閉じていない/.test(problem)), problemsOf(text).join('\n'))
    assert.deepEqual(sectionsOf(text), [])
  })

  test('失敗にする：骨組みから読んだ ## が 0 件', () => {
    assertOne(problemsOf(template({ body: ['# 題', '', '文。'] })), /## の節が 0 件/)
  })

  test('失敗にする：骨組みの ## に同じ名前が 2 つある', () => {
    assertOne(problemsOf(template({ body: ['## 業務規則', '## 状態の遷移', '## 業務規則'] })), /「業務規則」が 2 回ある/)
  })

  test('失敗にする：雛形のファイルがない、または読めない', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'spec-structure-'))
    try {
      assertOne(checkFunctionalDocs({ templateFile: path.join(dir, 'none.md'), specs: [] }), /雛形を読めない/)
      assertOne(checkFunctionalDocs({ templateFile: dir, specs: [] }), /雛形を読めない/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  for (const [label, open, close] of [
    ['3 個の ` と info string markdown', '```markdown', '```'],
    ['3 個の ` と info string md', '```md', '```'],
    ['3 個の ` と info string なし', '```', '```'],
    ['~~~', '~~~', '~~~'],
    ['1 個の空白で字下げした開き', ' ````markdown', '````'],
    ['3 個の空白で字下げした開き', '   ````markdown', '   ````'],
  ]) {
    test(`検出しない：骨組みを ${label} で囲んでも読む`, () => {
      assert.deepEqual(readSkeleton(template({ open, close })), { sections: SECTIONS, problems: [] })
    })
  }

  test('検出しない：CRLF の雛形でも、名前に CR が残らない', () => {
    assert.deepEqual(readSkeleton(template().replaceAll('\n', '\r\n')), { sections: SECTIONS, problems: [] })
  })

  test('検出しない：骨組みの ## の末尾にある閉じの # と空白', () => {
    const sections = sectionsOf(template({ body: ['## 業務規則 ##', '## 状態の遷移   ', '## データモデル', '## 今有効な AC #'] }))
    assert.deepEqual(sections, SECTIONS)
  })

  test('検出しない：骨組みの中の ### と # の題', () => {
    assert.deepEqual(sectionsOf(template({ body: ['# 題', '## 業務規則', '### 小見出し', '## 状態の遷移', '## データモデル', '## 今有効な AC'] })), SECTIONS)
  })

  test('検出しない：骨組みの中の HTML のコメント、1 行のコード、入れ子のコードブロックの中の ##', () => {
    const body = [
      '## 業務規則', '<!--', '## コメント', '-->', '`## 1 行のコード`', '```', '## 入れ子', '```',
      '## 状態の遷移', '## データモデル', '## 今有効な AC',
    ]
    assert.deepEqual(readSkeleton(template({ body })), { sections: SECTIONS, problems: [] })
  })

  test('検出しない：骨組みの <…> の注が、文の途中で ## に言及している', () => {
    const body = ['## 業務規則', '<注。見出し ## 余分 に言及する。>', '## 状態の遷移', '## データモデル', '## 今有効な AC']
    assert.deepEqual(sectionsOf(template({ body })), SECTIONS)
  })

  test('検出しない：骨組みの外にある、使い方の表や箇条での ## の言及', () => {
    const before = '## この雛形の使い方\n\n| 項目 | 内容 |\n|---|---|\n| 節 | ## 余分 を足す |\n\n- 節は ## 余分 の形\n\n'
    assert.deepEqual(readSkeleton(template({ before })), { sections: SECTIONS, problems: [] })
  })

  test('検出しない：骨組みの後ろに別の ## の節を加えても、読むコードブロックは 1 つだけ', () => {
    assert.deepEqual(readSkeleton(template({ after: '## 補足\n\n```\n例\n```\n' })), { sections: SECTIONS, problems: [] })
  })

  test('検出しない：③ の本文のコードブロックにある ##', () => {
    assert.deepEqual(checkFunctionalSpec(`${functionalText(SECTIONS)}\n\`\`\`\n## 余分\n\`\`\`\n`, SECTIONS), [])
  })

  test('検出しない：骨組みの 1 行目の # の題を変えても、節の名前の検査に影響しない', () => {
    const sections = sectionsOf(template({ body: ['# 別の題', ...BODY_LINES.slice(1)] }))
    assert.deepEqual(checkFunctionalSpec(functionalText(SECTIONS), sections), [])
  })

  test('検出しない：固定の節に書き換えた ③ の一覧にない文書は検査しない', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'spec-structure-'))
    try {
      const templateFile = path.join(dir, 'template.md')
      const listed = path.join(dir, 'listed.md')
      const unlisted = path.join(dir, 'unlisted.md')
      writeFileSync(templateFile, template())
      writeFileSync(listed, functionalText(SECTIONS))
      writeFileSync(unlisted, '## 古い節\n')
      assert.deepEqual(checkFunctionalDocs({ templateFile, specs: [listed] }), [])
      const problems = checkFunctionalDocs({ templateFile, specs: [listed, unlisted] })
      assert.ok(problems.length > 0 && problems.every((problem) => /unlisted\.md：/.test(problem)), problems.join('\n'))
      assert.ok(problems.some((problem) => /「古い節」/.test(problem)), problems.join('\n'))
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })
})

describe('雛形（DC-321-18、DC-321-19）', () => {
  test('<…> を、仮の値に置き換える（番号は 900、選択肢は最初、など は削る、ID の形は残す）', () => {
    const filled = fillTemplate('# Spec <番号>：<題>\n種類：機能　規模：<L または M>　ステータス：<草案 または 確定>\n<承認済み（tasks 作成済み）など> <AC-<番号>-<n>> DC-<番号>-<n>〜<m> T<番号>-<段階 2 桁>-<n+1> # 段階 <N>')
    assert.equal(filled, '# Spec 900：仮\n種類：機能　規模：L　ステータス：草案\n承認済み（tasks 作成済み） AC-900-1 DC-900-1〜2 T900-01-05 # 段階 1')
  })

  test('入れ子の <…> と、「または」で選ぶ見出しの末尾を置き換える', () => {
    assert.equal(fillTemplate('## <名前>（<DS-<番号>-<n>：DC-<番号>-<n>〜<m> または US-<番号>-<n>：AC-<番号>-<n>〜<m>>）'), '## 仮（DS-900-1：DC-900-1〜2）')
  })

  test('HTML の注釈は置き換えない', () => {
    assert.equal(fillTemplate('<!-- 注釈 `x` -->'), '<!-- 注釈 `x` -->')
  })

  test('HTML のタグ（<br/>、<details>、</details>、属性のあるタグ）は置き換えない', () => {
    const tags = '<br/> <br> <details> <summary>要約</summary> </details> <a href="x.md" title="説明">a</a> <img src="a.png" />'
    assert.equal(fillTemplate(tags), tags)
  })

  test('英字で始まる <…> でも、HTML のタグの形でないもの（<plan へのリンク>・<ID の範囲>・<n>・<m>・<N>）は置き換える', () => {
    assert.equal(fillTemplate('<plan へのリンク> <ID の範囲> <n> <m> <N> <spec または作業>'), '仮 仮 1 2 1 spec')
  })

  test('リポジトリの雛形は、置き換えた後の構造の検査を通る', () => {
    assert.deepEqual(checkTemplates(path.join(ROOT, 'docs/08-spec/templates')), [])
  })

  test('雛形の節の名前を変えれば失敗にする', () => {
    const problems = checkTemplates(path.join(ROOT, 'docs/08-spec/templates'), {
      transform: (name, text) => (name === 'spec-feature.md' ? text.replace('## 業務ルール', '## 業務規則') : text),
    })
    assert.ok(problems.some((problem) => /^spec-feature\.md：.*「業務規則」/.test(problem)), problems.join('\n'))
    assert.ok(problems.some((problem) => /^spec-feature\.md：節「業務ルール」がない/.test(problem)), problems.join('\n'))
  })

  test('雛形の ID の連番を 0 で埋めれば失敗にする', () => {
    const problems = checkTemplates(path.join(ROOT, 'docs/08-spec/templates'), {
      transform: (name, text) => (name === 'plan.md' ? text.replace('| 段階と変えるファイル | 全て |', '| 段階と変えるファイル | DC-<番号>-01 |') : text),
    })
    assert.ok(problems.some((problem) => /^plan\.md：.*0 で埋めている/.test(problem)), problems.join('\n'))
  })

  test('リポジトリの見本は、構造の検査を通る', () => {
    const examples = path.join(ROOT, 'docs/08-spec/templates/examples')
    const known = new Set([EXAMPLE_NUMBER])
    assert.deepEqual(checkSpecDir(path.join(examples, 'feature-l'), EXAMPLE_NUMBER, known), [])
    assert.deepEqual(checkSpecDir(path.join(examples, 'refactor-m'), EXAMPLE_NUMBER, known), [])
  })
})

describe('検査の対象（DC-321-19）', () => {
  /** 一時の docs/08-spec を作り、中身を書く */
  function makeSpecs(entries) {
    const dir = mkdtempSync(path.join(tmpdir(), 'spec-structure-'))
    for (const [file, text] of Object.entries(entries)) {
      mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
      writeFileSync(path.join(dir, file), text)
    }
    return dir
  }

  test('進行中の spec は検査の対象にし、アーカイブの spec は対象にしない。既知の番号にはアーカイブも入れる', () => {
    const dir = makeSpecs({
      '321-new/spec.md': specText(),
      '7-old-root/spec.md': '',
      'archive/mvp/011-dev-foundation/spec.md': '# 古い形式',
      'archive/refactor/283-old/spec.md': '# 古い形式',
      'templates/spec-feature.md': '',
    })
    try {
      const { targets, known } = findTargets(dir)
      assert.deepEqual(targets.map((target) => [path.basename(target.dir), target.number]), [['7-old-root', '7'], ['321-new', '321']])
      assert.deepEqual([...known].sort(), ['011', '283', '321', '7', '900'])
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('docs/08-spec/ の直下の、archive と templates 以外で <番号>-<名前> の形でないディレクトリは失敗にする', () => {
    const dir = makeSpecs({
      '321-new/spec.md': specText(),
      'design-templates/spec.md': '',
      '321_design-templates/spec.md': '',
      'archive/mvp/011-dev-foundation/spec.md': '',
      'archive/group/memo.md': '',
      'templates/spec-feature.md': '',
      'mvp.md': '',
    })
    try {
      assert.deepEqual(checkSpecDirNames(dir), [
        '321_design-templates：spec のディレクトリの名前が <番号>-<名前> でない',
        'design-templates：spec のディレクトリの名前が <番号>-<名前> でない',
      ])
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('spec のディレクトリを読み、ファイルのパスを付けて問題を返す', () => {
    const dir = makeSpecs({ '321-new/spec.md': specText().replace('# Spec 321：', '# Spec 1：'), '321-new/plan.md': PLAN, '321-new/tasks/01-name.md': STAGE })
    try {
      const problems = checkSpecDir(path.join(dir, '321-new'), '321', KNOWN)
      assertOne(problems, /321-new\/spec\.md：1 行目が「# Spec 321：…」でない/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('tasks の下の .md 以外のファイルと、plan.md がない規模 M の spec は問題にしない', () => {
    const dir = makeSpecs({ '321-new/spec.md': specText({ size: 'M' }), '321-new/tasks/memo.txt': 'メモ' })
    try {
      assert.deepEqual(checkSpecDir(path.join(dir, '321-new'), '321', KNOWN), [])
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })
})
