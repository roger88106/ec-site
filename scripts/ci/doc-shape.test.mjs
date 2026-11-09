import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findShapeWarnings, isShapeTarget, KIND } from './doc-shape.mjs'

const DESIGN = 'docs/02-設計/payment.md'
const SPEC = 'docs/03-機能仕様/payment.md'
const OTHER = 'docs/05-開発規則/writing.md'

/** 指定の種類の warning の行番号の配列 */
function lines(file, text, kind) {
  return findShapeWarnings(file, text)
    .filter((warning) => warning.kind === kind)
    .map((warning) => warning.line)
}

/** バイト数を指定したセルの表 */
function tableWithCell(bytes, indent = '') {
  return [`${indent}| 見出し | 内容 |`, `${indent}|--------|------|`, `${indent}| a | ${'a'.repeat(bytes)} |`].join('\n')
}

/** sequenceDiagram の mermaid 図 */
function diagram(...body) {
  return ['```mermaid', 'sequenceDiagram', ...body, '```'].join('\n')
}

describe('表のセル', () => {
  test('301 バイトのセルを検出する', () => {
    assert.deepEqual(lines(OTHER, tableWithCell(301), KIND.cell), [3])
  })

  test('全角 101 字（303 バイト）のセルを検出する', () => {
    assert.deepEqual(lines(OTHER, `| a |\n|---|\n| ${'あ'.repeat(101)} |`, KIND.cell), [3])
  })

  test('箇条書きの中に字下げした表の 301 バイトのセルを検出する', () => {
    assert.deepEqual(lines(OTHER, `- 項目\n\n${tableWithCell(301, '  ')}`, KIND.cell), [5])
  })

  test('300 バイトのセルと全角 100 字のセルは検出しない', () => {
    assert.deepEqual(lines(OTHER, tableWithCell(300), KIND.cell), [])
    assert.deepEqual(lines(OTHER, `| a |\n|---|\n| ${'あ'.repeat(100)} |`, KIND.cell), [])
  })

  test('前後の空白を含めると 301 バイトで、中身が 299 バイトのセルは検出しない', () => {
    assert.deepEqual(lines(OTHER, `| a |\n|---|\n| ${'a'.repeat(299)} |`, KIND.cell), [])
  })

  test('コードブロック（バッククォート 3 個、4 個以上、チルダ）の中の表の行は検出しない', () => {
    for (const fence of ['```', '````', '~~~']) {
      assert.deepEqual(lines(OTHER, `${fence}\n${tableWithCell(301)}\n${fence}`, KIND.cell), [], fence)
    }
  })

  test('区切りの行と、| で書いた縦線は検出しない', () => {
    assert.deepEqual(lines(OTHER, `| a |\n|${'-'.repeat(400)}|`, KIND.cell), [])
    assert.deepEqual(lines(OTHER, '| a | b |\n|---|---|\n| x \\| y | z |', KIND.cell), [])
    // \| は区切りでなく、1 つのセルの中身（セルは分かれない）
    assert.deepEqual(lines(OTHER, `| a |\n|---|\n| ${'a'.repeat(150)}\\|${'a'.repeat(150)} |`, KIND.cell), [3])
  })

  test('generated の組の中の表は検出しない', () => {
    const text = `<!-- generated:x -->\n${tableWithCell(301)}\n<!-- /generated:x -->\n${tableWithCell(301)}`
    assert.deepEqual(lines(OTHER, text, KIND.cell), [8])
  })
})

describe('観測の語', () => {
  test('設計の「sandbox で確認した」を検出する', () => {
    assert.deepEqual(lines(DESIGN, '（売上確定済みは sandbox で確認した）', KIND.observation), [1])
    assert.deepEqual(lines(DESIGN, 'sandbox で確認した応答', KIND.observation), [1])
  })

  test('設計の「sandbox で動かして確認した」を検出する', () => {
    assert.deepEqual(lines(DESIGN, 'sandbox で動かして確認した', KIND.observation), [1])
  })

  test('機能仕様の「実測では」「試したところ」を検出する', () => {
    assert.deepEqual(lines(SPEC, '実測では 3 秒かかった', KIND.observation), [1])
    assert.deepEqual(lines(SPEC, '試したところ、409 が返った', KIND.observation), [1])
  })

  test('機能仕様の「検証したところ」「確認したところ、」「計測では」「動かしたところ」を検出する', () => {
    for (const word of ['検証したところ', '確認したところ、', '計測では', '動かしたところ']) {
      assert.deepEqual(lines(SPEC, `a\n${word}`, KIND.observation), [2], word)
    }
  })

  test('通常の「確認」の文は検出しない', () => {
    assert.deepEqual(lines(SPEC, '消費者が確認の画面で確認した注文\n確認していない注文', KIND.observation), [])
    assert.deepEqual(lines(SPEC, '署名を確認し、確認できないものは処理しない', KIND.observation), [])
  })

  test('Stripe の sandbox で動かす設定は検出しない', () => {
    assert.deepEqual(lines(SPEC, 'Stripe の sandbox で動かす設定', KIND.observation), [])
    assert.deepEqual(lines(SPEC, 'Stripe の sandbox の設定ファイルが入らない', KIND.observation), [])
  })

  test('図の Note over の「確認の画面へ」は検出しない', () => {
    assert.deepEqual(lines(DESIGN, diagram('Note over U: 確認の画面へ'), KIND.observation), [])
  })

  test('設計と機能仕様の外では検出しない', () => {
    assert.deepEqual(lines(OTHER, 'sandbox で確認した', KIND.observation), [])
    assert.deepEqual(lines('docs/08-spec/454-doc-skill/spec.md', '実測では 3 秒', KIND.observation), [])
  })

  test('template.md の見本は検出しない', () => {
    assert.deepEqual(lines('docs/02-設計/template.md', '観測の結果（「sandbox で確認した」など）', KIND.observation), [])
    assert.deepEqual(lines('docs/03-機能仕様/template.md', '観測の結果（「sandbox で確認した」など）', KIND.observation), [])
  })
})

describe('Stripe の語', () => {
  test('機能仕様の PaymentIntent、Checkout Session、capture_before を検出する', () => {
    for (const word of ['`PaymentIntent`', '`Checkout Session`', '`capture_before`']) {
      assert.deepEqual(lines(SPEC, `決済は ${word} で行う`, KIND.stripe), [1], word)
    }
  })

  test('機能仕様の payment_intent 系の語を検出する', () => {
    assert.deepEqual(lines(SPEC, '`payment_intent`', KIND.stripe), [1])
    assert.deepEqual(lines(SPEC, '`payment_intent_unexpected_state`', KIND.stripe), [1])
  })

  test('機能仕様の checkout.session の事象の名前を検出する', () => {
    assert.deepEqual(lines(SPEC, '`checkout.session.completed`', KIND.stripe), [1])
    assert.deepEqual(lines(SPEC, '`checkout.session.expired`', KIND.stripe), [1])
  })

  test('機能仕様の Refund と Refunds API を検出する', () => {
    assert.deepEqual(lines(SPEC, '`Refund` を作る', KIND.stripe), [1])
    assert.deepEqual(lines(SPEC, 'Refunds API を呼ぶ', KIND.stripe), [1])
  })

  test('サービスと機能の名前（Stripe、Stripe Checkout）は検出しない', () => {
    assert.deepEqual(lines(SPEC, 'Stripe と Stripe Checkout を使う', KIND.stripe), [])
  })

  test('プロジェクトの識別子（RefundRequested、PaymentGateway、PaymentPage）は検出しない', () => {
    assert.deepEqual(lines(SPEC, '`RefundRequested`、`PaymentGateway`、`PaymentPage`', KIND.stripe), [])
  })

  test('小文字の refund は検出しない', () => {
    assert.deepEqual(lines(SPEC, '`refund` を作る', KIND.stripe), [])
  })

  test('リンクの先のパスにある語は検出しない', () => {
    assert.deepEqual(lines(SPEC, '[決済](../02-設計/PaymentIntent.md)', KIND.stripe), [])
  })

  test('template.md の見本と、設計の PaymentIntent は検出しない', () => {
    assert.deepEqual(lines('docs/03-機能仕様/template.md', '`PaymentIntent`', KIND.stripe), [])
    assert.deepEqual(lines(DESIGN, '`PaymentIntent`', KIND.stripe), [])
  })
})

describe('Mermaid の参加者', () => {
  test('participant、actor、create participant の宣言が合わせて 6 の図を検出する', () => {
    const text = diagram('participant A as 名前', 'actor B', 'create participant C', 'participant D', 'participant E', 'participant F')
    assert.deepEqual(lines(OTHER, text, KIND.participants), [2])
  })

  test('宣言 5 と、矢印にだけ出る参加者 1 の図を検出する', () => {
    const text = diagram('participant A', 'participant B', 'participant C', 'participant D', 'participant E', 'A->>F: ok')
    assert.deepEqual(lines(OTHER, text, KIND.participants), [2])
  })

  test('参加者が 5 の図と、同じ ID を 2 回宣言して 5 の図は検出しない', () => {
    assert.deepEqual(lines(OTHER, diagram('participant A', 'participant B', 'participant C', 'participant D', 'participant E'), KIND.participants), [])
    assert.deepEqual(lines(OTHER, diagram('participant A', 'participant B', 'participant C', 'participant D', 'participant E', 'participant A'), KIND.participants), [])
  })

  test('Note over にだけ出る ID は数えない', () => {
    const text = diagram('participant A', 'participant B', 'participant C', 'participant D', 'participant E', 'Note over F: メモ')
    assert.deepEqual(lines(OTHER, text, KIND.participants), [])
  })

  test('参加者が 3 の図を 2 つ置いた文書は検出しない', () => {
    const one = diagram('participant A', 'participant B', 'participant C')
    assert.deepEqual(lines(OTHER, `${one}\n\n${one}`, KIND.participants), [])
  })

  test('ノードが 6 以上の flowchart と、text の囲みの中の sequenceDiagram は検出しない', () => {
    const flow = ['```mermaid', 'flowchart LR', 'A-->B', 'B-->C', 'C-->D', 'D-->E', 'E-->F', 'F-->G', '```'].join('\n')
    assert.deepEqual(findShapeWarnings(OTHER, flow), [])
    const asText = ['```text', 'sequenceDiagram', 'participant A', 'participant B', 'participant C', 'participant D', 'participant E', 'participant F', '```'].join('\n')
    assert.deepEqual(findShapeWarnings(OTHER, asText), [])
  })
})

describe('自分への矢印', () => {
  const label = (count) => 'あ'.repeat(count)

  test('ラベルが 21 字（全角の記号を 1 字と数える）を検出し、20 字は検出しない', () => {
    assert.deepEqual(lines(OTHER, diagram(`P->>P: ${label(21)}`), KIND.selfArrow), [3])
    assert.deepEqual(lines(OTHER, diagram(`P->>P: ${label(18)}、（）`), KIND.selfArrow), [3])
    assert.deepEqual(lines(OTHER, diagram(`P->>P: ${label(17)}、（）`), KIND.selfArrow), [])
  })

  test('活性化の印の付いた P->>+P: の 21 字を検出する', () => {
    assert.deepEqual(lines(OTHER, diagram(`P->>+P: ${label(21)}`), KIND.selfArrow), [3])
  })

  test('ラベルが 20 字は検出しない', () => {
    assert.deepEqual(lines(OTHER, diagram(`P->>P: ${label(20)}`), KIND.selfArrow), [])
  })

  test('<br> で分けた各行が 20 字以下なら、全体が 21 字以上でも検出しない', () => {
    for (const br of ['<br>', '<br/>', '<br />']) {
      assert.deepEqual(lines(OTHER, diagram(`P->>P: ${label(15)}${br}${label(15)}`), KIND.selfArrow), [], br)
    }
    assert.deepEqual(lines(OTHER, diagram(`P->>P: ${label(15)}<br>${label(21)}`), KIND.selfArrow), [3])
  })

  test('ID の違う矢印の 21 字は、自分への矢印として検出しない', () => {
    assert.deepEqual(lines(OTHER, diagram(`P->>Q: ${label(21)}`), KIND.selfArrow), [])
  })
})

describe('他の矢印', () => {
  const label = (count) => 'あ'.repeat(count)

  test('ラベルが 26 字を、全ての矢印の形で検出する', () => {
    for (const arrow of ['->', '-->', '->>', '-->>', '-x', '--x', '-)', '--)', '<<->>', '<<-->>']) {
      assert.deepEqual(lines(OTHER, diagram(`P${arrow}Q: ${label(26)}`), KIND.otherArrow), [3], arrow)
    }
  })

  test('+・- の印が付いた矢印の 26 字を検出する', () => {
    assert.deepEqual(lines(OTHER, diagram(`P->>+Q: ${label(26)}`, `Q-->>-P: ${label(26)}`), KIND.otherArrow), [3, 4])
  })

  test('ラベルが 25 字は検出しない', () => {
    assert.deepEqual(lines(OTHER, diagram(`P->>Q: ${label(25)}`), KIND.otherArrow), [])
  })

  test('Note の文は検出しない', () => {
    const text = diagram(`Note over P,Q: ${label(30)}`, `Note right of P: ${label(30)}`, `Note left of P: ${label(30)}`)
    assert.deepEqual(findShapeWarnings(OTHER, text), [])
  })

  test('alt、else、loop、opt の見出しは検出しない', () => {
    const text = diagram(`alt ${label(30)}`, `else ${label(30)}`, `loop ${label(30)}`, `opt ${label(30)}`, 'end')
    assert.deepEqual(findShapeWarnings(OTHER, text), [])
  })

  test('ラベルが空の矢印は検出せず、例外で止まらない', () => {
    assert.deepEqual(findShapeWarnings(OTHER, diagram('P->>Q:', 'P->>Q: ')), [])
  })
})

describe('判定の決まり', () => {
  test('改行（CRLF と LF）と BOM で、結果は変わらない', () => {
    const text = `${tableWithCell(301)}\n\n${diagram('P->>P: ' + 'あ'.repeat(21))}`
    const expected = findShapeWarnings(OTHER, text)
    assert.equal(expected.length, 2)
    assert.deepEqual(findShapeWarnings(OTHER, text.replaceAll('\n', '\r\n')), expected)
    assert.deepEqual(findShapeWarnings(OTHER, `﻿${text}`), expected)
  })

  test('1 行が 2 つの種類に当たるときは、種類ごとに 1 件ずつ出す', () => {
    const text = `| a |\n|---|\n| 実測では ${'a'.repeat(300)} \`PaymentIntent\` |`
    const kinds = findShapeWarnings(SPEC, text)
      .map((warning) => warning.kind)
      .sort()
    assert.deepEqual(kinds, [KIND.cell, KIND.observation, KIND.stripe].sort())
  })

  test('warning は行、種類、説明を持つ', () => {
    const [warning] = findShapeWarnings(OTHER, tableWithCell(301))
    assert.equal(warning.line, 3)
    assert.equal(warning.kind, KIND.cell)
    assert.equal(typeof warning.message, 'string')
  })

  test('対象は Markdown で、archive、evals、worktrees の下は除く', () => {
    assert.equal(isShapeTarget('docs/02-設計/payment.md'), true)
    assert.equal(isShapeTarget('docs/08-spec/archive/1-x/spec.md'), false)
    assert.equal(isShapeTarget('.claude/skills/review/evals/a.md'), false)
    assert.equal(isShapeTarget('.claude/worktrees/x/docs/a.md'), false)
    assert.equal(isShapeTarget('scripts/ci/doc-shape.mjs'), false)
    assert.deepEqual(findShapeWarnings('docs/08-spec/archive/1-x/spec.md', tableWithCell(301)), [])
  })
})
