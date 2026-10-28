// 変更の危険度（高か低）を、develop との差分のファイルと、差分に含まれる spec.md の宣言から判定する。
// - 経路：差分のファイルが、高の経路の一覧（risk.json）のどれかの下にあれば高。テストのファイルも経路で判定する
// - spec の宣言：差分に含まれる spec.md（アーカイブへの移動を含む）の冒頭（最初の節の見出しより前）に「危険度：高」の行があれば高
// 判定と、当たった経路（まとまりの名前とファイル）と、宣言した spec を、pull request の本文の「## 危険度」の節に貼れる数行で出す。
// --body-file を渡すと、本文の「## 危険度」の節の「判定：」の値と比べ、本文が低で判定が高のとき、値が空・節がないときに失敗にする。
// 本文が高で判定が低のときは成功にする（規模 S の issue で高とした変更は、本文で高にするため）。
// Dependabot の pull request（--author か --branch で見分ける）は、本文と比べずに判定だけを出す。
// 差分の基準は、HEAD と基準の ref（既定は origin/develop）の分岐点。手元では、commit していない変更と、追跡していない無視されないファイルも差分に含める。
// 使い方：リポジトリのルートで node scripts/ci/risk.mjs [--base <ref>] [--body-file <ファイル。- は標準入力>] [--author <作成者>] [--branch <ブランチ名>]
//   引数なし（check-all.sh から）は、本文と比べずに判定だけを出す。
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { parseArgs } from 'node:util'
import { listGitFiles, splitNulList } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { isShallowRepository, SHALLOW_CLONE_MESSAGE } from './lib/shallow-clone.mjs'

/** 高の経路の一覧 */
const CONFIG_FILE = new URL('./risk.json', import.meta.url)

/** 差分の基準の既定の ref */
const DEFAULT_BASE = 'origin/develop'

/** 1 つのまとまりについて、出力に名前を出すファイルの数（残りは件数だけ） */
const SHOWN_FILES = 5

/** spec の宣言の行 */
const DECLARATION = '危険度：高'

/** spec.md（作業中の spec と、アーカイブのまとまりの下の spec） */
const SPEC_FILE = /^docs\/specs\/(?:archive\/(?:[^/]+\/)+)?\d+-[^/]+\/spec\.md$/

/** 本文の危険度の節の見出し */
const BODY_HEADING = /^##\s+危険度\s*$/

/** 本文の危険度の値の行（箇条書きの記号があってもよい） */
const BODY_VALUE = /^\s*(?:[-*]\s+)?判定：\s*(\S*)\s*$/

/**
 * 高の経路の一覧（risk.json の中身）を読む。形が違えば例外を投げる（読めない一覧で、全てを低と判定しないため）。
 *
 * @param {string} text risk.json の中身
 * @returns {{ groups: { name: string, paths: string[] }[] }} まとまり（一覧の順）
 */
export function parseRiskConfig(text) {
  const config = JSON.parse(text)
  if (!Array.isArray(config.groups) || config.groups.length === 0) throw new Error('risk.json に groups（まとまりの配列）がない')
  for (const group of config.groups) {
    if (typeof group.name !== 'string' || group.name === '') throw new Error('risk.json のまとまりに name がない')
    const validPaths = Array.isArray(group.paths) && group.paths.length > 0 && group.paths.every((item) => typeof item === 'string' && item !== '' && !item.startsWith('/'))
    if (!validPaths) throw new Error(`risk.json のまとまり「${group.name}」の paths が空か、空の経路・/ で始まる経路を含む`)
  }
  return { groups: config.groups.map(({ name, paths }) => ({ name, paths })) }
}

/**
 * ファイルが経路に当たるか。/ で終わる経路はディレクトリの下の全て、終わらない経路はそのファイル。* は 1 つの階層に当たる。
 *
 * @param {string} pattern 一覧の経路
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} 当たれば true
 */
function matchesPath(pattern, file) {
  const directory = pattern.endsWith('/')
  const patternParts = pattern.replace(/\/$/, '').split('/')
  const fileParts = file.split('/')
  // ディレクトリはその下のファイルだけ（同じ名前で始まる別の名前は当たらない）
  if (directory ? fileParts.length <= patternParts.length : fileParts.length !== patternParts.length) return false
  return patternParts.every((part, index) => part === '*' || part === fileParts[index])
}

/**
 * 一覧の経路のうち、どのファイルにも当たらないもの（経路の書き間違い、置き場所の移動）を返す。
 *
 * @param {{ groups: { name: string, paths: string[] }[] }} config 高の経路の一覧
 * @param {string[]} files リポジトリの全てのファイル
 * @returns {string[]} 当たるファイルのない経路（一覧の順）
 */
export function findMissingPaths(config, files) {
  return config.groups.flatMap((group) => group.paths).filter((pattern) => !files.some((file) => matchesPath(pattern, file)))
}

/**
 * 差分に含まれる spec.md のうち、冒頭（最初の節の見出しより前）に「危険度：高」の行があるものを返す。
 *
 * @param {string[]} files 差分のファイル
 * @param {(file: string) => string | null} readFile ファイルの中身を返す。ないファイル（削除）は null
 * @returns {string[]} 宣言した spec.md（差分の順）
 */
export function findDeclaredSpecs(files, readFile) {
  return files.filter((file) => {
    if (!SPEC_FILE.test(file)) return false
    const text = readFile(file)
    if (text === null) return false
    const lines = text.split('\n').map((line) => line.replace(/\r$/, ''))
    const end = lines.findIndex((line) => line.startsWith('## '))
    return lines.slice(0, end === -1 ? lines.length : end).some((line) => line.trim() === DECLARATION)
  })
}

/**
 * 差分のファイルから危険度を判定する。経路の判定と spec の宣言の、高い方にする。
 *
 * @param {string[]} files 差分のファイル（リポジトリのルートからのパス）
 * @param {{ groups: { name: string, paths: string[] }[] }} config 高の経路の一覧
 * @param {(file: string) => string | null} readFile ファイルの中身を返す。ないファイルは null
 * @returns {{ level: '高' | '低', hits: { name: string, files: string[] }[], specs: string[], total: number }}
 *   判定、当たったまとまり（一覧の順。当たったファイルがあるものだけ）、宣言した spec、差分のファイルの数
 */
export function judgeRisk(files, config, readFile) {
  const hits = config.groups
    .map((group) => ({ name: group.name, files: files.filter((file) => group.paths.some((pattern) => matchesPath(pattern, file))) }))
    .filter((hit) => hit.files.length > 0)
  const specs = findDeclaredSpecs(files, readFile)
  return { level: hits.length > 0 || specs.length > 0 ? '高' : '低', hits, specs, total: files.length }
}

/**
 * 判定を、pull request の本文に貼れる数行にする。最後の行は check-all.sh が求める要約の行。
 *
 * @param {{ level: string, hits: { name: string, files: string[] }[], specs: string[], total: number }} result 判定
 * @returns {string[]} 1 行目が「判定：高」か「判定：低」、最後が要約の行
 */
export function formatJudgement(result) {
  const shorten = (files) => {
    const shown = files.slice(0, SHOWN_FILES).join('、')
    return files.length > SHOWN_FILES ? `${shown}、ほか ${files.length - SHOWN_FILES} 件` : shown
  }
  const hitCount = new Set(result.hits.flatMap((hit) => hit.files)).size
  return [
    `判定：${result.level}`,
    ...result.hits.map((hit) => `- ${hit.name}：${shorten(hit.files)}`),
    ...(result.specs.length > 0 ? [`- spec の宣言：${shorten(result.specs)}`] : []),
    `当たったファイル：${hitCount}、宣言した spec：${result.specs.length}（差分のファイル ${result.total} 件中）`,
  ]
}

/**
 * pull request の本文の「## 危険度」の節から、「判定：」の行の値を読む。HTML のコメントの中は読まない。
 *
 * @param {string} body pull request の本文
 * @returns {{ value: '高' | '低' } | { error: string }} 値か、読めない理由
 */
export function readBodyRisk(body) {
  const lines = body.replace(/<!--[\s\S]*?-->/g, '').split('\n').map((line) => line.replace(/\r$/, ''))
  const start = lines.findIndex((line) => BODY_HEADING.test(line))
  if (start === -1) return { error: '「## 危険度」の節がない' }
  const rest = lines.slice(start + 1)
  const end = rest.findIndex((line) => /^#{1,2}\s/.test(line))
  const values = rest
    .slice(0, end === -1 ? rest.length : end)
    .map((line) => BODY_VALUE.exec(line))
    .filter(Boolean)
    .map((match) => match[1])
  if (values.length === 0) return { error: '「## 危険度」の節に「判定：」の行がない' }
  const unique = [...new Set(values)]
  if (unique.some((value) => value !== '高' && value !== '低')) return { error: '「## 危険度」の節の「判定：」の値が、高か低でない（空を含む）' }
  if (unique.length > 1) return { error: '「## 危険度」の節に、高と低の両方の「判定：」の行がある' }
  return { value: unique[0] }
}

/**
 * 判定と、pull request の本文の危険度を比べる。
 *
 * @param {'高' | '低'} level 判定
 * @param {string} body pull request の本文
 * @returns {{ ok: boolean, message: string }} 成功か、と出力する 1 行
 */
export function compareBody(level, body) {
  const read = readBodyRisk(body)
  if ('error' in read) return { ok: false, message: `本文の危険度を読めない：${read.error}（この script の出力を「## 危険度」の節に貼る）` }
  if (read.value === level) return { ok: true, message: `本文の危険度は ${read.value} で、判定と一致する` }
  if (read.value === '高') return { ok: true, message: '本文の危険度は高で、判定は低（本文で高にした変更として扱う）' }
  return { ok: false, message: '本文の危険度は低だが、判定は高（本文の「判定：」を高に直す）' }
}

/**
 * Dependabot の pull request か（作成者か、ブランチ名で見分ける）。
 *
 * @param {{ author?: string, branch?: string }} pullRequest 作成者とブランチ名（分からなければ省く）
 * @returns {boolean} Dependabot なら true
 */
export function isDependabot({ author, branch }) {
  return author === 'dependabot[bot]' || (branch ?? '').startsWith('dependabot/')
}

/** git を実行し、失敗したら null を返す */
function tryGit(args) {
  try {
    return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] })
  } catch {
    return null
  }
}

/**
 * 差分のファイルを返す。基準は HEAD と ref の分岐点。改名は、元と先の両方のパスにする（高の経路から出す改名も当てるため）。
 * 手元で commit の前に確かめられるよう、作業ツリーの変更と、追跡していない無視されないファイルも含める。
 *
 * @param {string} ref 基準の ref
 * @param {string[]} gitFiles Git の対象のファイル（追跡していない無視されないファイルを含む）
 * @returns {{ files: string[] } | { error: string }} 差分のファイル（名前の順）か、求められない理由
 */
function listChangedFiles(ref, gitFiles) {
  const base = tryGit(['merge-base', 'HEAD', ref])?.trim()
  if (!base) {
    if (isShallowRepository()) return { error: SHALLOW_CLONE_MESSAGE }
    return { error: `HEAD と ${ref} の分岐点を求められない（ref があるか、git fetch したかを確認する）` }
  }
  const changed = tryGit(['diff', '--name-only', '-z', '--no-renames', base])
  const committed = tryGit(['ls-tree', '-r', '-z', '--name-only', 'HEAD'])
  if (changed === null || committed === null) return { error: `${ref} との差分を読めない` }
  // HEAD にないファイルは、git add したもの（diff に入る）か、追跡していないもの
  const inHead = new Set(splitNulList(committed))
  const added = gitFiles.filter((file) => !inHead.has(file))
  return { files: [...new Set([...splitNulList(changed), ...added])].sort() }
}

if (isMain(import.meta.url)) {
  const { values } = parseArgs({
    args: process.argv.slice(2),
    options: { base: { type: 'string', default: DEFAULT_BASE }, 'body-file': { type: 'string' }, author: { type: 'string' }, branch: { type: 'string' } },
  })
  const config = parseRiskConfig(readFileSync(CONFIG_FILE, 'utf8'))
  const gitFiles = listGitFiles()
  const missing = findMissingPaths(config, gitFiles)
  if (missing.length > 0) {
    for (const pattern of missing) console.log(`risk.json の経路に当たるファイルがない（書き間違いか、置き場所が変わった）：${pattern}`)
    console.log(`当たるファイルのない経路：${missing.length}`)
    process.exit(1)
  }
  const changed = listChangedFiles(values.base, gitFiles)
  if ('error' in changed) {
    console.log(changed.error)
    process.exit(1)
  }
  const result = judgeRisk(changed.files, config, (file) => (existsSync(file) ? readFileSync(file, 'utf8') : null))
  const lines = formatJudgement(result)
  for (const line of lines.slice(0, -1)) console.log(line)
  let ok = true
  if (isDependabot(values)) {
    console.log('Dependabot の pull request なので、本文と比べない')
  } else if (values['body-file'] !== undefined) {
    // - は標準入力（CI は本文を環境変数から標準入力に渡す）
    const body = readFileSync(values['body-file'] === '-' ? 0 : values['body-file'], 'utf8')
    const compared = compareBody(result.level, body)
    console.log(compared.message)
    ok = compared.ok
  }
  console.log(lines.at(-1))
  process.exit(ok ? 0 : 1)
}
