// 文書の形（文の形から推定するもの）を確認する。warning だけを出し、CI を止めない。
//   - 表のセル：前後の空白を除いた中身が CELL_BYTES（300）バイトを超える。区切りの行、コードブロック、<!-- generated:… --> の組の中は見ない。
//   - 観測の語：docs/02-設計/ と docs/03-機能仕様/ に、観測の結果の言い回し（「sandbox で確認した」「実測では」など。語は OBSERVATION_WORDS）がある。
//   - Stripe の語：docs/03-機能仕様/ に、Stripe の API の名前（PaymentIntent、Refund など。語は STRIPE_WORDS）がある。リンクの先のパスは見ない。
//   - Mermaid の sequenceDiagram：参加者が PARTICIPANTS（6）以上、自分への矢印のラベルが SELF_LABEL（21）字以上、他の矢印のラベルが OTHER_LABEL（26）字以上。
//       参加者は宣言（participant、actor、create）と矢印の両端を、図ごとに重複なく数える。ラベルは `<br>` で分けた各行の最大の字数（コードポイント）で見る。
// 対象：Git が管理する Markdown から、docs/08-spec/archive/、.claude/skills/review/evals/、`.claude/worktrees/` を除いたもの。
//   観測の語と Stripe の語は template.md も除く（見本として語を書くため）。語の検査はコードブロック（Mermaid の図を含む）の中を見ない。表のセルもコードブロックの中を見ない。Mermaid の図の検査だけが mermaid の囲みの中を見る。
// 1 行が 2 つの種類に当たるときは、種類ごとに 1 件ずつ出す。改行（CRLF と LF）と BOM で結果は変わらない。
// 失敗したとき：warning では失敗にしない（終了コードは 0）。対象の Markdown が 0 件のときだけ、他の検査と同じく失敗にする。--list で一覧を出し、示された所を、文書を書く規則（writing.md、各 template.md）に沿って直すかを決める。
// 除外：なし。閾値と語は、この script の先頭の定数で変える。
// 使い方：node scripts/ci/doc-shape.mjs [--list]（リポジトリのルートで）
//   既定：種類ごとの件数を「警告：…」の行で示し、最後に合計を示す。
//   --list：1 件 1 行（`パス:行 種類 → 説明`）で一覧にする。
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 表のセルの上限（バイト）。これを超えたら warning */
const CELL_BYTES = 300

/** Mermaid の参加者の数の下限。これ以上で warning */
const PARTICIPANTS = 6

/** 自分への矢印のラベルの字数の下限。これ以上で warning */
const SELF_LABEL = 21

/** 他の矢印のラベルの字数の下限。これ以上で warning */
const OTHER_LABEL = 26

/** 観測の語（設計・機能仕様に書かない観測の結果の言い回し） */
const OBSERVATION_WORDS = /sandbox で(?:動かして)?(?:確認し|確かめ|試し)た|sandbox では[^。]*確認した|ことを確認した|確認した結果|実測では|試したところ|検証したところ|確認したところ|計測では|動かしたところ/

/** Stripe の語（機能仕様に書かない API の名前）。大文字小文字を区別し、プロジェクトの識別子（RefundRequested など）に当てない */
const STRIPE_WORDS = /PaymentIntent|Checkout Session|capture_before|payment_intent|checkout\.session\.[a-z_*]+|(?<![A-Za-z0-9_])charge\.[a-z_*]|charge_already_refunded|capture_method|Stripe-Signature|Refunds API|(?<![A-Za-z0-9_])Refund(?![A-Za-z0-9_])/

/** warning の文に示すラベルの目安。12 字と 20 字だけが docs/02-設計/template.md（骨組みの処理の流れ）の目安と同じ値。上の warning の閾値は、この目安より緩い */
const GUIDE_SELF_LABEL = 12
const GUIDE_OTHER_LABEL = 20

/** 検査の対象にしない Markdown */
const EXCLUDED = /^docs\/08-spec\/archive\/|^\.claude\/skills\/review\/evals\/|^\.claude\/worktrees\//

/** warning の種類 */
export const KIND = {
  cell: '表のセル',
  observation: '観測の語',
  stripe: 'Stripe の語',
  participants: 'Mermaid の参加者',
  selfArrow: 'Mermaid の自分への矢印',
  otherArrow: 'Mermaid の他の矢印',
}

/** コードブロックの開き・閉じの行 */
const FENCE = /^\s*(`{3,}|~{3,})(.*)$/

/** 参加者の宣言 */
const DECLARATION = /^\s*(?:create\s+)?(?:participant|actor)\s+(\S+)/

/** 矢印の文（送り元、矢印、活性化の印、送り先、ラベル）。Note、alt などは先頭の語の後に矢印が続かないので当たらない */
const ARROW = /^\s*([^\s:+\-<>()]+)\s*(?:<<-->>|<<->>|-->>|->>|-->|->|--x|-x|--\)|-\))\s*[+-]?\s*([^\s:+\-<>()]+)\s*:(.*)$/

/**
 * 文書のパスが検査の対象かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} Markdown で、除外の場所の外なら true
 */
export function isShapeTarget(file) {
  return file.endsWith('.md') && !EXCLUDED.test(file)
}

/**
 * 表の行を、セルの中身（前後の空白を除く）に分ける。`\|` は区切りにしない。
 *
 * @param {string} row 表の行
 * @returns {string[]} セルの中身
 */
function splitCells(row) {
  const body = row.trim().replace(/^\|/, '').replace(/(?<!\\)\|$/, '')
  return body.split(/(?<!\\)\|/).map((cell) => cell.trim())
}

/**
 * 文書を、コードブロックの内と外に分けた行にする。
 *
 * @param {string} text 文書
 * @returns {{ line: number, text: string, fence: string | null, block: number }[]} 行。fence はコードブロックの中なら情報文字列（なければ空文字）、外なら null。block はコードブロックごとの番号（外は 0）。開きと閉じの行は含めない
 */
function readLines(text) {
  const rows = []
  let open = null
  let blocks = 0
  text
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .forEach((raw, index) => {
      const match = raw.match(FENCE)
      if (open) {
        if (match && match[1][0] === open.char && match[1].length >= open.length && match[2].trim() === '') open = null
        else rows.push({ line: index + 1, text: raw, fence: open.info, block: blocks })
        return
      }
      if (match && !(match[1][0] === '`' && match[2].includes('`'))) {
        blocks += 1
        open = { char: match[1][0], length: match[1].length, info: match[2].trim().split(/\s+/)[0] }
        return
      }
      rows.push({ line: index + 1, text: raw, fence: null, block: 0 })
    })
  return rows
}

/**
 * 表のセルの warning を探す。
 *
 * @param {{ line: number, text: string, fence: string | null, block: number }[]} rows 行
 * @returns {{ line: number, kind: string, message: string }[]} warning
 */
function findCells(rows) {
  const found = []
  let generated = 0
  for (const row of rows) {
    if (row.fence !== null) continue
    if (/^\s*<!--\s*generated:/.test(row.text)) generated += 1
    if (/^\s*<!--\s*\/generated:/.test(row.text)) generated = Math.max(0, generated - 1)
    if (generated > 0 || !/^\s*\|/.test(row.text)) continue
    const cells = splitCells(row.text)
    if (cells.every((cell) => /^:?-+:?$/.test(cell))) continue
    const longest = Math.max(...cells.map((cell) => Buffer.byteLength(cell)))
    if (longest > CELL_BYTES) found.push({ line: row.line, kind: KIND.cell, message: `表のセルが ${longest} バイト（${CELL_BYTES} バイトまで）。短くするか、表の外に出す` })
  }
  return found
}

/**
 * 語の warning を探す（1 行に種類ごとに 1 件）。
 *
 * @param {{ line: number, text: string, fence: string | null, block: number }[]} rows 行
 * @param {string} kind 種類
 * @param {RegExp} pattern 語
 * @param {string} advice 説明の後半
 * @returns {{ line: number, kind: string, message: string }[]} warning
 */
function findWords(rows, kind, pattern, advice) {
  const found = []
  for (const row of rows) {
    if (row.fence !== null) continue
    // リンクの先のパスは見ない
    const match = row.text.replace(/\]\([^)]*\)/g, '](…)').match(pattern)
    if (match) found.push({ line: row.line, kind, message: `「${match[0]}」${advice}` })
  }
  return found
}

/**
 * ラベルの字数（`<br>` で分けた各行の最大。コードポイント）を返す。
 *
 * @param {string} label ラベル
 * @returns {number} 字数
 */
function labelLength(label) {
  return Math.max(...label.split(/<br\s*\/?>/i).map((part) => Array.from(part.trim()).length))
}

/**
 * Mermaid の sequenceDiagram の warning を探す。
 *
 * @param {{ line: number, text: string, fence: string | null, block: number }[]} rows 行
 * @returns {{ line: number, kind: string, message: string }[]} warning
 */
function findDiagrams(rows) {
  const found = []
  let diagram = null
  const finish = () => {
    if (diagram && diagram.ids.size >= PARTICIPANTS) {
      found.push({ line: diagram.line, kind: KIND.participants, message: `参加者が ${diagram.ids.size}。図を分けるか、参加者を減らす` })
    }
    diagram = null
  }
  let previous = null
  for (const row of rows) {
    if (row.fence !== 'mermaid') {
      if (previous !== null) finish()
      previous = null
      continue
    }
    if (previous !== row.block) finish()
    previous = row.block
    const body = row.text.trim()
    if (body === 'sequenceDiagram') {
      finish()
      diagram = { line: row.line, ids: new Set() }
      continue
    }
    if (!diagram || body.startsWith('%%')) continue
    const declaration = row.text.match(DECLARATION)
    if (declaration) {
      diagram.ids.add(declaration[1])
      continue
    }
    const arrow = row.text.match(ARROW)
    if (!arrow) continue
    const [, from, to, label] = arrow
    diagram.ids.add(from)
    diagram.ids.add(to)
    if (label.trim() === '') continue
    const length = labelLength(label)
    if (from === to && length >= SELF_LABEL) {
      found.push({ line: row.line, kind: KIND.selfArrow, message: `自分への矢印のラベルが ${length} 字（目安は ${GUIDE_SELF_LABEL} 字）。図を分けるか、細部を図の下の文に移す` })
    } else if (from !== to && length >= OTHER_LABEL) {
      found.push({ line: row.line, kind: KIND.otherArrow, message: `矢印のラベルが ${length} 字（目安は ${GUIDE_OTHER_LABEL} 字）。図を分けるか、細部を図の下の文に移す` })
    }
  }
  finish()
  return found
}

/**
 * 1 つの文書の形の warning を探す。
 *
 * @param {string} file 文書のパス（リポジトリのルートから。対象の種類を決める）
 * @param {string} text 文書の本文
 * @returns {{ line: number, kind: string, message: string }[]} warning。行の順
 */
export function findShapeWarnings(file, text) {
  if (!isShapeTarget(file)) return []
  const rows = readLines(text)
  const found = [...findCells(rows), ...findDiagrams(rows)]
  if (!file.endsWith('/template.md')) {
    if (/^docs\/(02-設計|03-機能仕様)\//.test(file)) found.push(...findWords(rows, KIND.observation, OBSERVATION_WORDS, 'は観測の結果の言い回し。設計と機能仕様に書かない（記録に置く）'))
    if (file.startsWith('docs/03-機能仕様/')) found.push(...findWords(rows, KIND.stripe, STRIPE_WORDS, 'は Stripe の API の名前。機能仕様に書かない（設計に置く）'))
  }
  return found.sort((a, b) => a.line - b.line)
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => isShapeTarget(file))
  exitIfNoFiles(files, '文書の形を調べる Markdown')
  const all = files.flatMap((file) => findShapeWarnings(file, readFileSync(file, 'utf8')).map((warning) => ({ file, ...warning })))
  if (process.argv.includes('--list')) {
    for (const warning of all) console.log(`${warning.file}:${warning.line} ${warning.kind} → ${warning.message}`)
  } else {
    for (const kind of Object.values(KIND)) {
      const count = all.filter((warning) => warning.kind === kind).length
      if (count > 0) console.log(`警告：${kind}が ${count} 件ある`)
    }
    if (all.length > 0) console.log('警告：一覧は node scripts/ci/doc-shape.mjs --list')
  }
  console.log(`文書の形の警告：${all.length}`)
}
