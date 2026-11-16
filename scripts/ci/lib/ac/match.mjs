// AC 一覧を、③ の表、進行中の spec、テスト名と照合する（ac-check の照合）。
//
// 次のときに失敗する：
//   - AC 一覧の範囲：マーカーの範囲がない、2 組ある、閉じが前にある
//   - AC 一覧の形：範囲に表の見出しの行がない（空、空行だけ）・列が違う・行のセルの数が違う・表の行でない行がある（読み飛ばさない）・AC の番号の形が違う。
//     表の中に空行がある（GFM の表はそこで切れる。表より前と、表より後の空行は許す）
//   - AC 一覧の行：同じ AC の行が 2 つある、状態が有効・変更・廃止でない
//   - モジュール：有効・変更の AC で、③ の名前か common の 1 つでない。廃止の AC は空でもよい
//   - 変えた spec：有効の AC で「—」以外、変更・廃止の AC で空か「—」。
//     変更・廃止の AC の各番号が、番号の形でない、その番号の spec のディレクトリがない、2 つある。
//     MVP の spec を番号だけで書いたもの（001）と、MVP にない番号を MVP-<番号> で書いたものは、ディレクトリがないものとして失敗にする
//   - 元の spec：変えた spec の各番号と同じ。
//     AC が AC-s-n の形の行（状態によらない）で、元の spec が s と文字列として一致しない（0 埋めの違いと、MVP- を付けた値も一致しない）。
//     AC が MVP の形（AC-n）の行（状態によらない）で、元の spec が MVP-<番号> でない。番号と n は比べない
//   - AC の番号：AC が MVP の形（AC-n）の行（状態によらない）で、n が上限（MVP_AC_LIMIT）を超える。MVP は終わっていて、AC-n は増えないため
//   - 今の内容：有効・変更の AC で、空か仮の値（—、なし、<…>）。<br /> がある
//   - 今の内容の参照：spec の中だけで通じる参照（※1、事実 2、US の番号、spec の「…」）がある
//   - 今の内容の根拠：変更と common の AC で、根拠（リンク [<文書の名前>](<パス>) と、その直後の「の「<見出し>」」）がない。
//     リンクのない「…の「…」」は根拠と読まない（doc-refs が見出しの実在を確認できないため）
//   - ③ のマーカー：一覧にある ③ のマーカーの組がない、1 組でない、閉じが前にある、「今有効な AC」の節の外にある、節をまたぐ
//   - ③ の節と表：一覧にある ③ に「今有効な AC」の節がない、表が古い、節の中でマーカーの範囲の外に AC の表の行（| AC- で始まる行）がある
//   - ③ の名前：一覧にある ③ の名前に、バックエンドのモジュールのディレクトリがない。
//     バックエンドのモジュールのディレクトリと ③ の文書の両方がある名前の ③ が、一覧にない（追加し忘れ）
//   - その他のマーカー：一覧にない ③ にマーカーがある。既知のマーカー（generated:ac-list、generated:module-ac）でない generated: のコメントがある
//   - 進行中の spec：AC が AC 一覧にない、AC 一覧の元の spec がその spec でない（番号の重なり）。
//     定義の行が崩れている（- **AC-<番号>** <文> の形でない。見分け方は ac-list.mjs の DEFINITION_ATTEMPTS）。
//     spec から消した AC が、AC 一覧に有効・変更で残っている
//   - テスト名の AC：AC 一覧にない AC 番号がある、廃止の AC を参照する
//   - テスト名の書き方：範囲（AC-n〜m）、新しい形の後の数字だけの番号（AC-s-n、m）、空白の区切り（AC-n AC-m）
//   - テストのファイル：テスト名を読めない test・it の呼び出しがある、ファイルが 0 件。
//     読めない呼び出しは、名前が文字列でない、each の括弧が閉じない、知らない修飾が付いている、のどれか
import { findEmptyReasons } from '../output/exclusions.mjs'
import { readDocument, readHeadings } from '../docs/doc-structure.mjs'
import { AC_NUMBER_ONLY, compareAc, findAmbiguousAcLists, findSpacedAcLists, parseAcRefs, toAcKey } from './test-names.mjs'
import { BASIS, COMMON, LIST_MARK, MODULE_AC_SECTION, MODULE_COLUMNS, MODULE_MARK, MVP_AC_LIMIT, MVP_ARCHIVE_PREFIX, PLACEHOLDER, SPEC_NUMBER, SPEC_NUMBER_SEPARATOR, SPEC_ONLY_REFS, STATUSES, findMarks, readPair, renderModuleTable, specNumberOf, splitKeepingEol, toLf } from './ac-list.mjs'
import { SPECS_DIR } from '../docs/spec-dirs.mjs'

/**
 * AC とテスト名を照合する。
 *
 * @param input.specAcs    AC の番号（AC 一覧と進行中の spec の AC）
 * @param input.tests      テスト（ファイルと名前）
 * @param input.exclusions 除外の一覧（"AC-n" → 理由）
 * @param input.deprecated 廃止の AC の番号。テストが要らず、テストが参照すると失敗。除外の一覧のキーにもできない
 * @param input.strict     テスト名に出ない AC を失敗にするか。false なら警告にする（missing には出す）
 * @returns 照合した結果。ok が false なら失敗
 */
export function check({ specAcs, tests, exclusions, deprecated = new Set(), strict = true }) {
  const covered = new Set()
  const ranges = []
  const ambiguous = []
  const spaced = []
  const unknown = []
  const deprecatedRefs = []
  for (const test of tests) {
    const refs = parseAcRefs(test.name)
    if (refs.ranges.length > 0) {
      ranges.push(test)
    }
    if (findAmbiguousAcLists(test.name).length > 0) {
      ambiguous.push(test)
    }
    if (findSpacedAcLists(test.name).length > 0) {
      spaced.push(test)
    }
    for (const ac of refs.acs) {
      if (!specAcs.has(ac)) {
        unknown.push({ ...test, ac })
      } else if (deprecated.has(ac)) {
        deprecatedRefs.push({ ...test, ac })
      } else {
        covered.add(ac)
      }
    }
  }

  const excluded = new Set()
  const invalidExclusions = []
  const emptyReasons = findEmptyReasons(exclusions)
  for (const key of Object.keys(exclusions)) {
    const match = key.startsWith('AC-') ? AC_NUMBER_ONLY.exec(key.slice(3)) : null
    if (!match || !specAcs.has(toAcKey(match[1])) || deprecated.has(toAcKey(match[1]))) {
      invalidExclusions.push(key)
    } else if (!emptyReasons.includes(key)) {
      excluded.add(toAcKey(match[1]))
    }
  }

  const sorted = (values) => [...values].sort(compareAc)
  const missing = sorted([...specAcs].filter((ac) => !covered.has(ac) && !excluded.has(ac) && !deprecated.has(ac)))
  const staleExclusions = sorted([...excluded].filter((ac) => covered.has(ac)))
  const ok =
    (!strict || missing.length === 0) &&
    ranges.length === 0 &&
    ambiguous.length === 0 &&
    spaced.length === 0 &&
    unknown.length === 0 &&
    deprecatedRefs.length === 0 &&
    staleExclusions.length === 0 &&
    invalidExclusions.length === 0 &&
    emptyReasons.length === 0
  return { ok, missing, ranges, ambiguous, spaced, unknown, deprecatedRefs, staleExclusions, invalidExclusions, emptyReasons }
}

/**
 * 元の spec・変えた spec の 1 つの番号を、spec のディレクトリと照合する。
 *
 * MVP-<番号> は MVP の spec のディレクトリと、番号だけは MVP の外のディレクトリと照合する。
 *
 * @param {string} value 列の値（変えた spec は読点で区切った 1 つ）
 * @param {{ name: string, rel: string }[]} specDirs spec のディレクトリ
 * @param {string} column 列の名前
 * @returns {string | null} 問題。なければ null
 */
function checkSpecNumber(value, specDirs, column) {
  const match = SPEC_NUMBER.exec(value)
  if (!match) return `${column}が spec の番号（MVP-001、227）の形でない（${value}）`
  const { mvp, plain } = match.groups
  const number = mvp ?? plain
  const inMvp = (dir) => dir.rel.startsWith(MVP_ARCHIVE_PREFIX)
  const sameNumber = specDirs.filter((dir) => specNumberOf(dir.name) === number)
  const found = sameNumber.filter((dir) => inMvp(dir) === (mvp !== undefined))
  if (found.length === 0) {
    if (mvp !== undefined) return `${column}の ${value} の spec のディレクトリがない（${SPECS_DIR}/${MVP_ARCHIVE_PREFIX} の下を探した）`
    const hint = sameNumber.length > 0 ? `。MVP の spec は MVP-${number} と書く` : ''
    return `${column}の番号 ${value} の spec のディレクトリがない（${SPECS_DIR}/${MVP_ARCHIVE_PREFIX} の外を探した${hint}）`
  }
  if (found.length > 1) return `${column}の番号 ${value} の spec のディレクトリが ${found.length} つある（${found.map((dir) => dir.rel).join('、')}。番号から 1 つに決められない）`
  return null
}

/**
 * AC 一覧の行の、重複・状態・モジュール・変えた spec・元の spec・今の内容を調べる。
 *
 * @param {object} input
 * @param {ReturnType<typeof readAcList>['rows']} input.rows AC 一覧の行
 * @param {Set<string>} input.modules ③ のモジュールの名前
 * @param {{ name: string, rel: string }[]} input.specDirs spec のディレクトリ（listSpecDirs の結果）
 * @returns {string[]} 問題（行を付ける）
 */
export function checkAcRows({ rows, modules, specDirs }) {
  const problems = []
  const seen = new Map()
  for (const row of rows) {
    const where = `${row.line} 行：${row.label}：`
    const add = (problem) => problems.push(`${where}${problem}`)
    const key = String(row.ac)
    if (seen.has(key)) add(`同じ AC の行が ${seen.get(key)} 行にもある`)
    else seen.set(key, row.line)
    const knownModule = modules.has(row.module) || row.module === COMMON
    const originProblem = checkSpecNumber(row.origin, specDirs, '元の spec')
    if (originProblem) add(originProblem)
    // AC-s-n の行は、元の spec が s と同じ（文字列として完全一致）
    if (typeof row.ac === 'string') {
      const acSpec = row.ac.split('-')[0]
      if (row.origin !== acSpec) add(`元の spec（${row.origin}）が AC の番号に含まれる spec（${acSpec}）と違う。誤っている方を直す`)
    } else if (!originProblem && !row.origin.startsWith('MVP-')) {
      // AC-n の行は、元の spec が MVP の spec。形の誤りと、ディレクトリがない値は、originProblem で報告済み
      add(`元の spec（${row.origin}）が番号だけで、AC が MVP の形（AC-n）である。MVP の AC なら元の spec を MVP-<番号> に、1→10 の AC なら AC を AC-<spec の番号>-<連番> に直す`)
    }
    if (typeof row.ac === 'number' && row.ac > MVP_AC_LIMIT) add(`AC-n の番号が上限（${MVP_AC_LIMIT}）を超えている。MVP は終わっていて、AC-n は増えない。1→10 の AC は AC-<spec の番号>-<連番> にする`)
    // 変更・廃止の AC の変えた spec の各番号（空と「—」は、状態ごとの検査が見る）
    const checkChangedBy = () => {
      for (const number of row.changedBy.split(SPEC_NUMBER_SEPARATOR)) {
        const problem = checkSpecNumber(number, specDirs, '変えた spec')
        if (problem) add(problem)
      }
    }
    if (row.content !== '') {
      for (const pattern of SPEC_ONLY_REFS) {
        if (pattern.test(row.content)) add(`今の内容に、spec の中だけで通じる参照（${pattern.exec(row.content)[0]}）がある。意味を補って書き換えるか、削る`)
      }
      if (/<br\s*\/?>/i.test(row.content)) add('今の内容を複数行にしない（<br /> を使わない）')
    }
    if (!STATUSES.includes(row.status)) {
      add(`状態が有効・変更・廃止のどれでもない（${row.status}）`)
      continue
    }
    if (row.status === '廃止') {
      if (row.module !== '' && !knownModule) add(`廃止の AC のモジュールが、空でも ③ の名前でも ${COMMON} でもない（${row.module}）`)
      if (row.changedBy === '' || row.changedBy === '—') add('廃止の AC の変えた spec がない')
      else checkChangedBy()
      continue
    }
    if (!knownModule) add(`モジュールが、③ の名前か ${COMMON} の 1 つでない（「${row.module}」）。名前は ${[...modules].sort().join('、')}`)
    if (row.status === '有効' && row.changedBy !== '—') add(`有効の AC の変えた spec は「—」にする（${row.changedBy}）`)
    if (row.status === '変更') {
      if (row.changedBy === '' || row.changedBy === '—') add('変更の AC の変えた spec がない')
      else checkChangedBy()
    }
    if (['', '—', '-', 'なし'].includes(row.content) || PLACEHOLDER.test(row.content)) add(`今の内容が空か仮の値（${row.content}）`)
    else if ((row.status === '変更' || row.module === COMMON) && !BASIS.test(row.content)) {
      add('今の内容に、根拠を、リンク（[<文書の名前>](<パス>)）と、その直後の「の「<見出し>」」の形で添える')
    }
  }
  return problems
}

/**
 * 進行中の spec の AC と、AC 一覧を照合する。AC 一覧の元の spec は、spec のディレクトリの名前の先頭の番号と文字列として比べる。
 *
 * AC の本文は比べない（転記の後は AC 一覧を手動で修正する）。
 *
 * @param {object} input
 * @param {ReturnType<typeof readAcList>['rows']} input.rows AC 一覧の行
 * @param {{ rel: string, definitions: { ac: number | string }[] }[]} input.specs 進行中の spec（docs/08-spec からのパスと AC の定義）
 * @returns {string[]} 問題
 */
export function checkInProgress({ rows, specs }) {
  const problems = []
  const byKey = new Map(rows.map((row) => [String(row.ac), row]))
  for (const spec of specs) {
    const number = specNumberOf(spec.rel)
    const defined = new Set(spec.definitions.map((definition) => String(definition.ac)))
    for (const key of defined) {
      const row = byKey.get(key)
      if (!row) problems.push(`進行中の spec（${spec.rel}）の AC-${key} が AC 一覧にない。node scripts/ci/spec/ac-check.mjs --write で転記し、モジュールを書く`)
      else if (row.origin !== number) problems.push(`AC 一覧の AC-${key} の元の spec が、その AC を定義する進行中の spec（${spec.rel}）でない（番号が重なっていないかも確かめる）`)
    }
    for (const row of rows) {
      if (row.origin === number && row.status !== '廃止' && !defined.has(String(row.ac))) {
        problems.push(`${row.line} 行：${row.label} は、元の spec（${spec.rel}）にない。AC 一覧の行を消すか、spec に戻す`)
      }
    }
  }
  return problems
}

/**
 * ③ の文書の名前を、バックエンドのモジュールのディレクトリと、構造の検査の対象の一覧（FUNCTIONAL_SPECS）に照合する。
 *
 * 一覧にある ③ にディレクトリがない名前と、ディレクトリがあるモジュールの ③ が一覧にない名前を、問題にする。
 * 後者を許すと、追加し忘れた ③ が、節の構造の検査と「今有効な AC」の表の照合から外れる。
 *
 * @param {object} input
 * @param {{ file: string, module: string, required: boolean }[]} input.docs ③ の置き場所の文書（required は構造の検査の対象の一覧にあるか）
 * @param {Set<string>} input.modules ③ のモジュールの名前（listModuleNames の結果）
 * @returns {string[]} 問題（ファイルのパスを付ける）
 */
export function checkModuleDocNames({ docs, modules }) {
  const problems = []
  for (const doc of docs) {
    if (doc.required && !modules.has(doc.module)) problems.push(`${doc.file}：構造の検査の対象の ③ の名前（${doc.module}）に、バックエンドのモジュールのディレクトリがない`)
    if (!doc.required && modules.has(doc.module)) problems.push(`${doc.file}：モジュールの ③ が構造の検査の対象の一覧（scripts/ci/lib/docs/functional-specs.mjs の FUNCTIONAL_SPECS）にない。固定の節に書いて一覧に追加する`)
  }
  return problems
}

/**
 * ③ の文書の、生成のマーカーと「今有効な AC」の表を調べる。
 *
 * 構造の検査の対象の一覧にある ③ は、「今有効な AC」の節の中にマーカーの組が必須で、範囲の中が生成した表と同じでなければならない。
 * 一覧にない ③ は、マーカーを置かない。
 *
 * @param {object} input
 * @param {string} input.text ③ の文書（改行は LF でも CRLF でもよい）
 * @param {string} input.module モジュールの名前
 * @param {ReturnType<typeof readAcList>['rows']} input.rows AC 一覧の行
 * @param {boolean} input.required 構造の検査の対象の一覧にあるか
 * @returns {{ problems: string[], stale: boolean, updated: string | null }}
 *   problems：問題（行を付ける）。stale：表が古いだけか（--write で修正できる）。updated：範囲の中を書き換えた文書（書き換えられないときは null。古くなければ元のまま）
 */
export function checkModuleDoc({ text, module, rows, required }) {
  const lf = toLf(text)
  const { marks, problems } = findMarks(lf)
  if (marks.some((mark) => mark.name === LIST_MARK)) problems.push(`③ に AC 一覧の生成の印（generated:${LIST_MARK}）がある`)
  const own = marks.filter((mark) => mark.name === MODULE_MARK)
  if (!required) {
    if (own.length > 0) problems.push(`${own[0].line} 行：構造の検査の対象の一覧（scripts/ci/lib/docs/functional-specs.mjs の FUNCTIONAL_SPECS）にない ③ に、生成の印がある。固定の節に書き換えて一覧に加えてから置く`)
    return { problems, stale: false, updated: null }
  }
  const { pair, problems: pairProblems } = readPair(marks, MODULE_MARK)
  problems.push(...pairProblems)
  if (!pair && pairProblems.length === 0) problems.push(`「${MODULE_AC_SECTION}」の節に <!-- generated:${MODULE_MARK} --> と <!-- /generated:${MODULE_MARK} --> の組がない`)
  const h2 = readHeadings(lf).filter((heading) => heading.level <= 2)
  const sections = h2.filter((heading) => heading.level === 2 && heading.title === MODULE_AC_SECTION)
  if (sections.length !== 1) problems.push(`「${MODULE_AC_SECTION}」の節が ${sections.length} 個ある（1 つにする）`)
  else if (pair) {
    const next = h2.find((heading) => heading.line > sections[0].line)?.line ?? Infinity
    if (pair.open < sections[0].line || pair.close > next) problems.push(`生成の印（${pair.open} 行、${pair.close} 行）が「${MODULE_AC_SECTION}」の節の外にあるか、節をまたぐ`)
    else {
      // 節の中で、範囲の外の AC の表の行。手動で書いた行が、AC 一覧にない AC が ③ に残ったまま検査を通らないようにする
      const { plain } = readDocument(lf)
      for (let number = sections[0].line + 1; number < Math.min(next, plain.length + 1); number++) {
        if (number >= pair.open && number <= pair.close) continue
        if (plain[number - 1].trimStart().startsWith('| AC-')) problems.push(`${number} 行：「${MODULE_AC_SECTION}」の節の、生成の印の範囲の外に、AC の表の行がある。AC 一覧に書き、--write で範囲の中に生成する`)
      }
    }
  }
  if (problems.length > 0) return { problems, stale: false, updated: null }

  const expected = renderModuleTable(rows, module)
  const inner = lf.split('\n').slice(pair.open, pair.close - 1)
  while (inner.length > 0 && inner[0].trim() === '') inner.shift()
  while (inner.length > 0 && inner.at(-1).trim() === '') inner.pop()
  if (inner.join('\n') === expected) return { problems: [], stale: false, updated: text }
  const lines = splitKeepingEol(text)
  const eol = lines[pair.open - 1].endsWith('\r\n') ? '\r\n' : '\n'
  const updated = [...lines.slice(0, pair.open), ...expected.split('\n').map((line) => `${line}${eol}`), ...lines.slice(pair.close - 1)].join('')
  return {
    problems: [`${pair.open} 行：「${MODULE_AC_SECTION}」の表が AC 一覧と違う（列は ${MODULE_COLUMNS.join('、')}）。node scripts/ci/spec/ac-check.mjs --write で書き換え、差分を確認する`],
    stale: true,
    updated,
  }
}

/**
 * AC とテストの対応を、表示する行にする（--tests）。
 *
 * @param {object} input
 * @param {ReturnType<typeof readAcList>} input.list AC 一覧を読んだ結果
 * @param {Map<number | string, string[]>} input.testFiles AC → その AC をテスト名に持つテストのファイル（リポジトリのルートからのパス）
 * @param {Record<string, unknown>} input.exclusions 除外の一覧（"AC-n" → 理由）
 * @returns {{ lines: string[], ok: boolean }} 表示する行（最後は要約の行）。AC 一覧を読めなければ ok が false で、行は問題
 */
export function renderTestMap({ list, testFiles, exclusions }) {
  if (list.problems.length > 0) return { lines: [...list.problems, 'NG：AC 一覧を読めない'], ok: false }
  const lines = list.rows.map((row) => {
    const files = [...new Set(testFiles.get(row.ac) ?? [])].sort()
    if (files.length > 0) return `${row.label}：${files.join('、')}`
    if (exclusions[row.label]) return `${row.label}：除外（${exclusions[row.label]}）`
    return `${row.label}：なし${row.status === '廃止' ? '（廃止）' : ''}`
  })
  return { lines: [...lines, `AC とテストの対応：AC ${list.rows.length} 件`], ok: true }
}
