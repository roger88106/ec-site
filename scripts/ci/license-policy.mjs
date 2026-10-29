// 許可のライセンスの一覧が、文書と policy.json で一致することを確認する。
// 文書は docs/05-開発規則/licenses.md 第 2 節の表の「許可」の行（2 列目）、policy.json は scripts/ci/licenses/policy.json の allowed。
// 片方だけ直すと、文書の方針と CI が確認する方針が食い違うため、過不足を失敗にする。
// 「許可」の行の 2 列目は、先に「。他に、」から後ろの文（ライセンスの名前でない説明）を除いてから、「、」で分けて読む。
// 行がない、区切りが読めない（空の項目、空白を含む項目）、「許可」の行が複数あるときは、照合の対象から外れて誤って通るため、失敗にする。
// 使い方：node scripts/ci/license-policy.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** ライセンスの方針の文書 */
const DOC_FILE = 'docs/05-開発規則/licenses.md'

/** ライセンスの検査の方針 */
const POLICY_FILE = 'scripts/ci/licenses/policy.json'

/** 読めない項目の理由 */
const UNREADABLE_REASON = 'ライセンスの識別子を「、」で区切った形で読めない'

/** 「許可」の行が複数あるときの理由 */
const DUPLICATE_ROW_REASON = '「許可」の行が複数ある（最初の行だけを照合するので、1 行にまとめる）'

/** ライセンスの識別子として読める形（空白を含まない） */
const LICENSE_ID = /^[\w.+-]+$/

/**
 * 文書の第 2 節の「許可」の行から、ライセンスの一覧を読む。
 *
 * @param {string} text licenses.md の内容
 * @returns {{ licenses: string[] | null, unreadable: { line: number, text: string, reason?: string }[] }} ライセンス（書いた順。最初の行だけ。行がなければ null）と、読めない行（1 始まりの行番号、行の内容、既定と違う理由）
 */
export function readAllowedRow(text) {
  let inSection = false
  let licenses = null
  const unreadable = []
  const lines = text.split('\n')
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index].replace(/\r$/, '')
    if (/^## /.test(line)) {
      inSection = /^## 2\. /.test(line)
      continue
    }
    const cells = line.split('|').map((cell) => cell.trim())
    if (!inSection || cells[1] !== '許可' || cells.length < 4) {
      continue
    }
    if (licenses !== null) {
      // 2 つ目以降の行は照合に使わないので、読み飛ばすと、そこにだけ書いたライセンスが誤って通る
      unreadable.push({ line: index + 1, text: line, reason: DUPLICATE_ROW_REASON })
      continue
    }
    const names = cells[2].replace(/。他に、.*$/, '').split('、').map((name) => name.trim())
    if (names.some((name) => !LICENSE_ID.test(name))) {
      unreadable.push({ line: index + 1, text: line })
    }
    licenses = names.filter((name) => LICENSE_ID.test(name))
  }
  return { licenses, unreadable }
}

/**
 * 文書の一覧と policy.json の一覧を照合する。
 *
 * @param {string[]} documented 文書の「許可」の行のライセンス
 * @param {string[]} listed policy.json の allowed
 * @returns {string[]} 過不足の説明
 */
export function findLicenseProblems(documented, listed) {
  const documentedSet = new Set(documented)
  const listedSet = new Set(listed)
  const problems = []
  for (const name of documentedSet) {
    if (!listedSet.has(name)) {
      problems.push(`${POLICY_FILE} の allowed にない（${DOC_FILE} の「許可」の行にある）：${name}`)
    }
  }
  for (const name of listedSet) {
    if (!documentedSet.has(name)) {
      problems.push(`${DOC_FILE} の「許可」の行にない（${POLICY_FILE} の allowed にある）：${name}`)
    }
  }
  return problems
}

/**
 * 文書と policy.json を照合して、問題の文を集める。読めない行、policy.json の allowed の欠落、「許可」の行の欠落、過不足の順。
 *
 * @param {string} docText licenses.md の内容
 * @param {{ allowed?: unknown } | null} policy policy.json の内容（JSON.parse の結果。オブジェクトでなくてもよい）
 * @returns {{ problems: string[], documentedCount: number }} 問題の文（なければ空）と、文書の「許可」の行のライセンスの件数（行がなければ 0）
 */
export function findPolicyProblems(docText, policy) {
  const row = readAllowedRow(docText)
  const problems = row.unreadable.map((item) => describeUnreadableLine({ file: DOC_FILE, line: item.line, text: item.text, reason: item.reason ?? UNREADABLE_REASON }))
  if (!Array.isArray(policy?.allowed)) {
    problems.push(`${POLICY_FILE} に allowed がない`)
  }
  if (row.licenses === null) {
    problems.push(`${DOC_FILE} 第 2 節の表に「許可」の行がない`)
  } else if (Array.isArray(policy?.allowed)) {
    problems.push(...findLicenseProblems(row.licenses, policy.allowed))
  }
  return { problems, documentedCount: row.licenses?.length ?? 0 }
}

if (isMain(import.meta.url)) {
  const docText = readFileSync(DOC_FILE, 'utf8')
  const policy = JSON.parse(readFileSync(POLICY_FILE, 'utf8'))
  const { problems, documentedCount } = findPolicyProblems(docText, policy)
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`許可のライセンス：${documentedCount} 件、policy.json：${Array.isArray(policy?.allowed) ? policy.allowed.length : 0} 件、問題：${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
