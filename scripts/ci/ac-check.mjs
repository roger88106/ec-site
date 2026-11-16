// AC 一覧（docs/03-機能仕様/acceptance-criteria.md）を、③ の表、進行中の spec、テスト名と照合する。
// AC 一覧は AC の正本で、③ の表は「今有効な AC」の節の表。テスト名の規則は、テストの書き方の「AC 番号を入れる」。
// 目的：AC の今の内容を AC 一覧の 1 か所に置き、③ の表とテストとの対応を、AC 一覧と食い違わせない。
//   AC の内容を得るために、アーカイブの spec は読まない。
// 読むもの：
//   - AC 一覧：生成のマーカー（<!-- generated:ac-list --> とその閉じ）の範囲の表。改行コードは LF にそろえて読む
//   - AC 一覧の列：AC・モジュール・状態・元の spec・変えた spec・今の内容。
//     --write は、進行中の spec の AC について、列 AC・状態（有効）・元の spec・今の内容を転記し、変えた spec を「—」にする。
//     手動で書くのは、モジュールと、前の spec の AC を変えた・廃止したときの状態・変えた spec・今の内容。
//     転記した行の今の内容も、spec の中だけで通じる参照を書き換えるときと、転記の後に spec の AC の文を直したときは手動で修正する
//     （手順は docs/05-開発規則/issues.md「AC 一覧の操作」）
//   - 元の spec・変えた spec の値：MVP の spec は MVP-<3 桁の番号>、それ以外は番号だけ（227）。
//     変えた spec が 2 つ以上なら読点で区切る。
//     spec のディレクトリは lib/spec-dirs.mjs で探す。MVP-<番号> は docs/08-spec/archive/mvp/ の下と照合する。
//     番号だけの値は、docs/08-spec/ の直下と、MVP 以外のまとまりの下と照合する。
//     番号は、ディレクトリの名前の先頭の <番号>- と文字列として比べる（007 と 7 は別の番号）
//   - AC：MVP の AC-n と、1→10 の AC-s-n（s は spec の番号、n は連番）。2 つの形は別の AC として扱う。
//     AC-s-n を AC-s と読まない。番号は、文書の記号と番号の「spec の番号」
//   - ③ のモジュールの名前：次の 2 つが両方ある名前と、common（共通。③ の表に生成しない）。
//     backend/ecsite/src/main/java/com/ecsite/module/<名前>/ と docs/03-機能仕様/<名前>.md
//   - ③ の表：構造の検査の対象の一覧（lib/functional-specs.mjs の FUNCTIONAL_SPECS）にある ③ の、マーカーの範囲。
//     マーカーは「今有効な AC」の節の <!-- generated:module-ac --> とその閉じ
//   - ③ の表の中身：AC 一覧から作る。列は AC、今の内容、元の spec。0 件なら「なし。」の 1 行。
//     状態が有効か変更で、列「モジュール」がその ③ の名前の AC を、AC の順に置く
//   - ③ の表の比べ方：範囲の先頭と末尾の空行を除き、改行コードを LF にそろえて、完全一致で比べる。
//     全角と半角などは正規化しない
//   - 進行中の spec：docs/08-spec/ の直下の spec.md の、定義の行（- **AC-…**）。spec.md のないディレクトリは飛ばす。
//     コードブロック・HTML のコメント・1 行のコードの中は読まない
//   - テスト名：Java のメソッドの @DisplayName（src/test、src/integrationTest）。
//     Vitest・Playwright の test・it の第 1 引数（*.test.ts(x)、*.spec.ts）。Git の対象のファイルだけを読む
//   - 読まないテスト名：まとまりの名前（describe、クラス）、skip・fixme のテスト、skip・fixme のまとまりの中のテスト。
//     コメント・文字列の中の呼び出しも読まない
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
//   - AC の番号：AC が MVP の形（AC-n）の行（状態によらない）で、n が上限（312）を超える。MVP は終わっていて、AC-n は増えないため
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
//     定義の行が崩れている（箇条の先頭の太字の AC が、- **AC-<番号>** <文> の形でない）。
//     崩れた形には、* や + の箇条、字下げ、番号付きの箇条を含む。
//     太字のない箇条・箇条のない太字・チェックボックス・見出し・表の行（先頭のセルが番号だけ）の定義も、崩れた行として報告する。
//     表の行を除く 4 つの形は、番号に続く文が一覧の助詞か記号で始まるなら、文の中で番号に触れただけの行として問題にしない（例：- AC-<番号> の補足）。
//     開きのかぎかっこ（「 『）、[ で始まるリンク、* の強調、1 行のコードは、文の先頭として扱う（記号に含めない）。
//     助詞の一覧は語の先頭で比べるので、同じかなで始まる文も問題にしない。
//     太字のない箇条は、番号だけの行も問題にしない（番号の一覧の箇条のため）。
//     spec から消した AC が、AC 一覧に有効・変更で残っている
//   - テスト名の AC：AC 一覧にない AC 番号がある、廃止の AC を参照する
//   - テスト名の書き方：範囲（AC-n〜m）、新しい形の後の数字だけの番号（AC-s-n、m）、空白の区切り（AC-n AC-m）
//   - テストのファイル：テスト名を読めない test・it の呼び出しがある、ファイルが 0 件。
//     読めない呼び出しは、名前が文字列でない、each の括弧が閉じない、知らない修飾が付いている、のどれか
//   - テスト名に出ない有効・変更の AC：CI（環境変数 CI=true）でだけ失敗にし、手元では警告にする。
//     spec を書いてからテストを書くまでの間も commit できるようにし、テストの漏れは CI で防ぐため。
//     手元で CI と同じに確認するとき：CI=true node scripts/ci/ac-check.mjs
// 失敗したとき：
//   - AC 一覧の行を修正する。進行中の spec の AC がないときは --write で転記し、モジュールの列を書く。
//     転記した行はモジュールが空なので、書くまで失敗する
//   - ③ の表が古いときは、--write で書き換えて差分を確認する。
//     マーカーと節の誤りは手動で修正する（そのとき --write は何も書かずに失敗する）
//   - テストを追加するか、テスト名に AC を書く。AC は読点で 1 つずつ書く（AC-n、AC-m）。
//     読めない呼び出しは ファイル:行 を示すので、テスト名を文字列で書く
// 除外：ac-exclusions.json に {キー: 理由}。キーは AC 一覧の有効・変更の AC（AC-n）。
//   テストがある AC、AC 一覧にない AC、廃止の AC の除外は失敗にする
// 使い方（リポジトリのルートで）：
//   - node scripts/ci/ac-check.mjs：照合する
//   - node scripts/ci/ac-check.mjs --write：AC 一覧にない進行中の spec の AC を転記し、③ のマーカーの範囲を書き換える。
//     範囲の外と、一覧にない ③ は変えない
//   - node scripts/ci/ac-check.mjs --tests：AC とテストの対応を表示する（対応は Git に置かない）
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describeEmptyReason, describeStaleExclusion, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { FUNCTIONAL_SPECS } from './lib/functional-specs.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { listSpecDirs } from './lib/spec-dirs.mjs'
import { AC_LIST, BACKEND_MODULES_DIR, MODULE_DOCS_DIR, SPECS_DIR, addAcsToList, listModuleNames, readAcList, readInProgressSpecs } from './ac/ac-list.mjs'
import { check, checkAcRows, checkInProgress, checkModuleDoc, checkModuleDocNames, renderTestMap } from './ac/match.mjs'
import { readTests } from './ac/test-names.mjs'

/** 除外の一覧のファイルの名前 */
const EXCLUSIONS_NAME = 'ac-exclusions.json'

/**
 * テスト名に出ない AC を失敗にするかを、環境から決める。CI では失敗にし、手元では警告にする。<br />
 * 理由：spec を書いてからテストを書くまでの間も、手元で commit できるようにするため。
 * テストの漏れは、CI（push と pull request）で止める。
 *
 * @param env 環境変数
 * @returns CI なら true
 */
export function isStrict(env) {
  return env.CI === 'true'
}

/** 照合した結果を、標準出力に書く */
function report(result, root, strict) {
  const where = (test) => `${path.relative(root, test.file).replaceAll('\\', '/')}：${test.name}`
  if (result.missing.length > 0) {
    // 手元では警告。check-all.sh は「警告：」で始まる行を、通ったときも出す
    const prefix = strict ? '' : '警告：'
    console.log(`${prefix}テスト名に出ない AC：${result.missing.map((ac) => `AC-${ac}`).join('、')}`)
    console.log('  テストを追加するか、テストで確認できない理由を scripts/ci/ac-exclusions.json に書く。')
  }
  for (const test of result.ranges) {
    console.log(`範囲の表記（AC 番号を 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.ambiguous) {
    console.log(`曖昧な AC の並べ方（新しい形は AC-7-1、AC-7-2 と書く）：${where(test)}`)
  }
  for (const test of result.spaced) {
    console.log(`空白で区切った AC の並び（AC-1、AC-2 と読点で 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.unknown) {
    console.log(`AC 一覧にない AC-${test.ac}：${where(test)}`)
  }
  for (const test of result.deprecatedRefs) {
    console.log(`廃止した AC-${test.ac} を参照するテスト（AC 番号を消すか、AC 一覧の状態を直す）：${where(test)}`)
  }
  for (const ac of result.staleExclusions) {
    console.log(describeStaleExclusion(EXCLUSIONS_NAME, `AC-${ac}`))
  }
  for (const key of result.emptyReasons) {
    console.log(describeEmptyReason(EXCLUSIONS_NAME, key))
  }
  if (result.invalidExclusions.length > 0) {
    console.log(`${EXCLUSIONS_NAME}：AC 一覧にない AC か、廃止の AC の除外：${result.invalidExclusions.join('、')}`)
  }
}

/**
 * ③ の置き場所の文書（AC 一覧を除く）を読む。
 *
 * @param {string} root リポジトリのルート
 * @returns {{ file: string, module: string, text: string, required: boolean }[]} リポジトリのルートからのパス、モジュールの名前（ファイル名から .md を除く）、内容、構造の検査の対象の一覧にあるか
 */
function readModuleDocs(root) {
  if (!existsSync(path.join(root, MODULE_DOCS_DIR))) return []
  return readdirSync(path.join(root, MODULE_DOCS_DIR))
    .filter((name) => name.endsWith('.md') && `${MODULE_DOCS_DIR}/${name}` !== AC_LIST)
    .sort()
    .map((name) => {
      const file = `${MODULE_DOCS_DIR}/${name}`
      return { file, module: name.slice(0, -3), text: readFileSync(path.join(root, file), 'utf8'), required: FUNCTIONAL_SPECS.includes(file) }
    })
}

/** AC 一覧、③、進行中の spec、テストを読んで照合する。--write は転記と ③ の表の書き換え、--tests は対応の表示 */
function main() {
  // 他の検査と同じく、リポジトリのルートで実行する（今のディレクトリを、読む対象のルートにする）
  const root = process.cwd()
  const writing = process.argv.includes('--write')
  // 対象のファイルが 0 件なら、何も見ずに通らないよう、先に失敗にする
  const gitFiles = listGitFiles([], root)
  exitIfNoFiles(gitFiles, 'Git のファイル')
  const { tests, unreadable, testFiles } = writing ? { tests: [], unreadable: [], testFiles: new Map() } : readTests(root, gitFiles)
  const listFile = path.join(root, AC_LIST)
  const document = existsSync(listFile) ? readFileSync(listFile, 'utf8') : ''
  let list = readAcList(document)
  const exclusions = readExclusions(exclusionsPath(EXCLUSIONS_NAME))

  if (process.argv.includes('--tests')) {
    const { lines, ok } = renderTestMap({ list, testFiles, exclusions })
    for (const line of lines) console.log(line)
    process.exit(ok ? 0 : 1)
  }

  const inProgress = readInProgressSpecs(path.join(root, SPECS_DIR))
  const docs = readModuleDocs(root)
  const backendDir = path.join(root, BACKEND_MODULES_DIR)
  const backendDirs = existsSync(backendDir) ? readdirSync(backendDir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name) : []
  const modules = listModuleNames({ backendDirs, docFiles: docs.map((doc) => path.basename(doc.file)) })
  const listProblems = [...list.problems, ...inProgress.problems]

  if (writing) {
    if (listProblems.length > 0) {
      for (const problem of listProblems) console.log(`${AC_LIST}：${problem}`)
      console.log('NG：AC 一覧か進行中の spec を読めないので、書き換えない')
      process.exit(1)
    }
    const written = addAcsToList(document, inProgress.specs.flatMap((spec) => spec.definitions))
    if (written !== document) list = readAcList(written)
    const results = docs.map((doc) => ({ doc, result: checkModuleDoc({ text: doc.text, module: doc.module, rows: list.rows, required: doc.required }) }))
    const blocking = results.flatMap(({ doc, result }) => (result.stale ? [] : result.problems.map((problem) => `${doc.file}：${problem}`)))
    if (blocking.length > 0) {
      for (const problem of blocking) console.log(problem)
      console.log('NG：③ の生成の印か節を修正してから、もう一度実行する（何も書き換えていない）')
      process.exit(1)
    }
    if (written !== document) {
      writeFileSync(listFile, written)
      console.log(`転記した：${AC_LIST}`)
    }
    for (const { doc, result } of results.filter(({ result }) => result.stale)) {
      writeFileSync(path.join(root, doc.file), result.updated)
      console.log(`書き換えた：${doc.file}`)
    }
    console.log('OK：書き換えた後に、モジュールの列と差分を確認する')
    process.exit(0)
  }

  const specDirs = listSpecDirs(path.join(root, SPECS_DIR))
  listProblems.push(...checkAcRows({ rows: list.rows, modules, specDirs }), ...checkInProgress({ rows: list.rows, specs: inProgress.specs }))
  const docProblems = checkModuleDocNames({ docs, modules })
  for (const doc of docs) {
    docProblems.push(...checkModuleDoc({ text: doc.text, module: doc.module, rows: list.rows, required: doc.required }).problems.map((problem) => `${doc.file}：${problem}`))
  }

  const specAcs = new Set([...list.rows.map((row) => row.ac), ...inProgress.specs.flatMap((spec) => spec.definitions.map((definition) => definition.ac))])
  const deprecated = new Set(list.rows.filter((row) => row.status === '廃止').map((row) => row.ac))
  const strict = isStrict(process.env)
  const result = check({ specAcs, tests, exclusions, deprecated, strict })
  report(result, root, strict)
  for (const problem of listProblems) console.log(`${AC_LIST}：${problem}`)
  for (const problem of docProblems) console.log(problem)
  for (const problem of unreadable) console.log(problem)
  const ok = result.ok && listProblems.length === 0 && docProblems.length === 0 && unreadable.length === 0
  const generated = docs.filter((doc) => doc.required).length
  console.log(`${ok ? 'OK' : 'NG'}：AC ${list.rows.length} 件（廃止 ${deprecated.size} 件）、③ の生成の表 ${generated} 件、テスト名 ${tests.length} 件、除外 ${Object.keys(exclusions).length} 件`)
  process.exit(ok ? 0 : 1)
}

if (isMain(import.meta.url)) {
  main()
}
