// 依存とライセンスの文書の「依存のライセンスの方針」の表が、policy.json から作った表と同じことを確認する。
// 表は、文書の <!-- generated:license-policy --> とその閉じの間に置く。
// 区分ごとのライセンスは、policy.json の allowed・conditional.licenses・forbidden から作る。識別子にない補足と扱いは、この script の ROWS に置く。
// 片方だけを変えると、文書の方針と CI が確認する方針が食い違うため。
// 失敗したとき：policy.json を修正してから、--write で表を再生成し、差分を確認する。補足と扱いを変えるときは ROWS を修正する。
// 除外：なし
// 使い方：node scripts/ci/license-policy.mjs [--write]（リポジトリのルートで。--write で文書の表を再生成する）
import { readFileSync, writeFileSync } from 'node:fs'

/** ライセンスの方針の文書 */
const DOC_FILE = 'docs/05-開発規則/licenses.md'

/** ライセンスの検査の方針 */
const POLICY_FILE = 'scripts/ci/licenses/policy.json'

/** 生成の範囲の印 */
const START = '<!-- generated:license-policy -->'
const END = '<!-- /generated:license-policy -->'

/** 表の行。licenses は policy.json のライセンスの一覧を返す関数、note は一覧の後に続ける補足（識別子で書けないもの） */
const ROWS = [
  {
    label: '許可',
    licenses: (policy) => policy.allowed,
    note: '改変を禁じるが無料で商用に使えるもの',
    handling: 'そのまま使える。改変を禁じるものは改変しない',
  },
  {
    label: '条件付き',
    licenses: (policy) => policy.conditional.licenses,
    note: '規模（売上、利用者数など）が一定以内なら無料のもの',
    handling: '改変せずに依存として使う場合だけ。規模で無料のものは、その規模以内で使う。ライブラリごとに理由を書いて一覧に載せる',
  },
  {
    label: '禁止',
    licenses: (policy) => policy.forbidden,
    note: '非商用限定のもの、有料のもの',
    handling: '使わない',
  },
]

/**
 * policy.json から、方針の表を作る。
 *
 * @param {{ allowed: string[], conditional: { licenses: string[] }, forbidden: string[] }} policy policy.json の内容
 * @returns {string} 表（末尾に改行）
 */
export function renderTable(policy) {
  const rows = ROWS.map(({ label, licenses, note, handling }) => `| ${label} | ${licenses(policy).join('、')}。他に、${note} | ${handling} |`)
  return `${['| 区分 | ライセンス | 扱い |', '|------|------------|------|', ...rows].join('\n')}\n`
}

/**
 * 文書の生成の範囲を読む。
 *
 * @param {string} document 文書（改行は LF でも CRLF でもよい）
 * @returns {{ from: number, to: number, body: string } | null} from は範囲の始め、to は閉じの印の位置、body は中身（前後の空行を除き、末尾に改行）。範囲がなければ null
 */
function readGenerated(document) {
  const from = document.indexOf(START)
  const to = document.indexOf(END)
  if (from < 0 || to < from || document.indexOf(START, from + 1) >= 0) return null
  return { from: from + START.length, to, body: `${document.slice(from + START.length, to).replaceAll('\r\n', '\n').trim()}\n` }
}

/**
 * 文書の生成の範囲と、policy.json から作った表を比べる。
 *
 * @param {string} document 文書
 * @param {string} table policy.json から作った表
 * @returns {string[]} 食い違い（なければ空）
 */
export function checkDocument(document, table) {
  const generated = readGenerated(document)
  if (generated === null) return [`${DOC_FILE} に ${START} と ${END} の範囲が 1 組だけない`]
  if (generated.body === table) return []
  return [`${DOC_FILE} の方針の表が、${POLICY_FILE} から作った表と違う。node scripts/ci/license-policy.mjs --write で再生成し、差分を確認する`]
}

/**
 * 文書の生成の範囲を、作った表に置き換える。
 *
 * @param {string} document 文書
 * @param {string} table policy.json から作った表
 * @returns {string} 置き換えた文書
 */
export function replaceGenerated(document, table) {
  const generated = readGenerated(document)
  if (generated === null) throw new Error(`${DOC_FILE} に ${START} と ${END} の範囲が 1 組だけない`)
  return `${document.slice(0, generated.from)}\n${table}${document.slice(generated.to)}`
}

if (import.meta.main) {
  const document = readFileSync(DOC_FILE, 'utf8')
  const table = renderTable(JSON.parse(readFileSync(POLICY_FILE, 'utf8')))
  if (process.argv.includes('--write')) {
    writeFileSync(DOC_FILE, replaceGenerated(document, table))
    console.log(`再生成した：${DOC_FILE}`)
    process.exit(0)
  }
  const problems = checkDocument(document, table)
  for (const problem of problems) console.log(problem)
  console.log(`方針の表の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
