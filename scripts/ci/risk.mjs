// 変更の危険度（高か低）を、develop との差分のファイルと、差分に含まれる spec.md の宣言から判定する。
// - 経路：差分のファイルが、高の経路の一覧（risk.json）のどれかの下にあれば高。テストのファイルも経路で判定する。
//   経路とファイルは小文字にしてから比べる（Windows と macOS では、大文字の違うパスが同じ所に入るため）
// - spec の宣言：差分に含まれる spec.md（アーカイブへの移動を含む）の冒頭（最初の節の見出しより前）に「危険度：高」の行があれば高。
//   差分の後のバージョンと基準（分岐点）のバージョンの両方を読み、どちらかにあれば高（宣言の行を消す変更を低にしないため。改名は元のパスの基準のバージョンを読む）。
//   差分の後のバージョンの冒頭で「危険度」を含む行が、正確な「危険度：高」でないときは失敗にする（書き損じを黙って宣言なしにしないため）
// 判定と、当たった経路（まとまりの名前とファイル）と、宣言した spec を、pull request の本文の「## 危険度」の節に貼れる数行で出す。
// --body-file を渡すと、本文の「## 危険度」の節の「判定：」の値と比べ、本文が低で判定が高のとき、値が空・節がないときに失敗にする。
// 本文が高で判定が低のときは成功にする（規模 S の issue で高とした変更は、本文で高にするため）。
// Dependabot の pull request（--author が dependabot[bot]。ブランチ名は誰でも付けられるので見分けに使わない）は、本文と比べない。
// その判定は、--title の題から読む更新の種類（major か）と、経路の判定の高い方にする。
// 差分の基準は、HEAD と基準の ref（既定は origin/develop）の分岐点。手元では、commit していない変更と、追跡していない無視されないファイルも差分に含める。
// CI（.github/workflows/risk.yml）は、基準の側の risk.mjs と risk.json でも同じ引数で実行し、どちらかが失敗すれば失敗にする
// （経路の一覧や script を変える変更が、変えた後の一覧で自分を低と判定するのを防ぐため）。基準の側には --skip-path-check を付ける
// （一覧の経路に当たるファイルがあるかは変えた後のバージョンで確認すればよく、基準の一覧の経路を消した変更で失敗にしないため）。
// 使い方：リポジトリのルートで node scripts/ci/risk.mjs [--base <ref>] [--body-file <ファイル。- は標準入力>] [--author <作成者>] [--title <題>] [--skip-path-check]
//   引数なし（check-all.sh から）は、本文と比べずに判定だけを出す。
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { parseArgs } from 'node:util'
import { listGitFiles, splitNulList } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { isShallowRepository, SHALLOW_CLONE_MESSAGE } from './lib/shallow-clone.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** 高の経路の一覧 */
const CONFIG_FILE = new URL('./risk.json', import.meta.url)

/** 差分の基準の既定の ref */
const DEFAULT_BASE = 'origin/develop'

/** 1 つのまとまりについて、出力に名前を出すファイルの数（残りは件数だけ） */
const SHOWN_FILES = 5

/** spec の宣言の行 */
const DECLARATION = '危険度：高'

/** 宣言を書いたつもりの行の語（冒頭でこの語を含む行は、宣言の形でなければ失敗にする） */
const DECLARATION_WORD = '危険度'

/** spec.md（作業中の spec と、アーカイブのまとまりの下の spec）。大文字の違うパスも同じ所に入るので、大小を区別しない */
const SPEC_FILE = /^docs\/specs\/(?:archive\/(?:[^/]+\/)+)?\d+-[^/]+\/spec\.md$/i

/** 本文の危険度の節の見出し */
const BODY_HEADING = /^##\s+危険度\s*$/

/** 本文の危険度の値の行（箇条書きの記号があってもよい） */
const BODY_VALUE = /^\s*(?:[-*]\s+)?判定：\s*(\S*)\s*$/

/** Dependabot の作成者（[bot] の付く名前は利用者が取れない） */
const DEPENDABOT_AUTHOR = 'dependabot[bot]'

/** Dependabot の 1 つの依存の更新の題（「bump <名前> from <バージョン> to <バージョン>」） */
const UPDATE_VERSIONS = /\bfrom\s+(\S+)\s+to\s+(\S+)/i

/** Dependabot の group の更新の題（「bump the <group> group ...」） */
const UPDATE_GROUP = /\bthe\s+(\S+)\s+group\b/i

/** major を含まない group の名前の終わり（.github/dependabot.yml の minor と patch の group） */
const MINOR_PATCH_GROUP = '-minor-patch'

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
 * 大文字と小文字は区別しない（Windows と macOS では、大文字の違うパスが既存のディレクトリに入るため）。
 *
 * @param {string} pattern 一覧の経路
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} 当たれば true
 */
function matchesPath(pattern, file) {
  const directory = pattern.endsWith('/')
  const patternParts = pattern.toLowerCase().replace(/\/$/, '').split('/')
  const fileParts = file.toLowerCase().split('/')
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
 * spec.md の冒頭（最初の節の見出しより前）の行を返す。
 *
 * @param {string} text spec.md の中身
 * @returns {string[]} 冒頭の行（行末の CR を除く）
 */
function headLines(text) {
  const lines = text.split('\n').map((line) => line.replace(/\r$/, ''))
  const end = lines.findIndex((line) => line.startsWith('## '))
  return lines.slice(0, end === -1 ? lines.length : end)
}

/**
 * 差分に含まれる spec.md のうち、冒頭（最初の節の見出しより前）に「危険度：高」の行があるものを返す。
 * 差分の後のバージョンと基準のバージョンのどちらかにあれば、宣言とみなす（宣言の行を消す変更を低にしないため）。
 * 差分の後のバージョンの冒頭で「危険度」を含む行（題の `# ` の行を除く）が正確な「危険度：高」でなければ、読めない行として返す。
 * 基準のバージョンの書き方は見ない（基準は照合を通った後のバージョンで、ここで失敗にすると直せないため）。
 *
 * @param {string[]} files 差分のファイル
 * @param {(file: string) => string | null} readFile 差分の後の中身を返す。ないファイル（削除）は null
 * @param {(file: string) => string | null} [readBaseFile] 基準のバージョンの中身を返す。基準にないファイル（追加）は null
 * @returns {{ specs: string[], unreadable: { file: string, line: number, text: string, reason: string }[] }}
 *   宣言した spec.md（差分の順）と、宣言の形でない行
 */
export function findDeclaredSpecs(files, readFile, readBaseFile = () => null) {
  const declared = (lines) => lines.some((line) => line.trim() === DECLARATION)
  const specs = []
  const unreadable = []
  for (const file of files.filter((item) => SPEC_FILE.test(item))) {
    const text = readFile(file)
    const baseText = readBaseFile(file)
    const lines = text === null ? [] : headLines(text)
    lines.forEach((line, index) => {
      if (line.trim() === DECLARATION || !line.includes(DECLARATION_WORD) || line.startsWith('# ')) return
      unreadable.push({ file, line: index + 1, text: line, reason: `冒頭の「${DECLARATION_WORD}」の行は「${DECLARATION}」だけを書く` })
    })
    if (declared(lines) || (baseText !== null && declared(headLines(baseText)))) specs.push(file)
  }
  return { specs, unreadable }
}

/**
 * 差分のファイルから危険度を判定する。経路の判定と spec の宣言の、高い方にする。
 *
 * @param {string[]} files 差分のファイル（リポジトリのルートからのパス）
 * @param {{ groups: { name: string, paths: string[] }[] }} config 高の経路の一覧
 * @param {(file: string) => string | null} readFile 差分の後の中身を返す。ないファイルは null
 * @param {(file: string) => string | null} [readBaseFile] 基準のバージョンの中身を返す。基準にないファイルは null
 * @returns {{ level: '高' | '低', hits: { name: string, files: string[] }[], specs: string[],
 *   unreadable: { file: string, line: number, text: string, reason: string }[], total: number }}
 *   判定、当たったまとまり（一覧の順。当たったファイルがあるものだけ）、宣言した spec、宣言の形でない行、差分のファイルの数
 */
export function judgeRisk(files, config, readFile, readBaseFile = () => null) {
  const hits = config.groups
    .map((group) => ({ name: group.name, files: files.filter((file) => group.paths.some((pattern) => matchesPath(pattern, file))) }))
    .filter((hit) => hit.files.length > 0)
  const { specs, unreadable } = findDeclaredSpecs(files, readFile, readBaseFile)
  return { level: hits.length > 0 || specs.length > 0 ? '高' : '低', hits, specs, unreadable, total: files.length }
}

/**
 * バージョンの major と minor を読む（先頭の v と、後ろの -alpine などは読まない）。
 *
 * @param {string} version バージョン
 * @returns {{ major: number, minor: number | null } | null} 読めなければ null
 */
function parseVersion(version) {
  const match = /^v?(\d+)(?:\.(\d+))?/.exec(version)
  return match ? { major: Number(match[1]), minor: match[2] === undefined ? null : Number(match[2]) } : null
}

/**
 * Dependabot の pull request の題から、更新の種類で高にするかを決める。
 * - 「from <バージョン> to <バージョン>」の形で、major が違うか、major が 0 で minor が違えば高（major の更新）
 * - group の更新で、group の名前が -minor-patch で終われば、経路の判定のまま（major を含まない group）
 * - それ以外（読めない題、major を含む github-actions-all などの group）は高
 *
 * @param {string | undefined} title pull request の題
 * @returns {{ level: '高' | null, reason: string }} 高にするなら '高'、経路の判定のままなら null と、その理由
 */
export function judgeDependabotTitle(title) {
  const text = title ?? ''
  const versions = UPDATE_VERSIONS.exec(text)
  const group = UPDATE_GROUP.exec(text)
  if (versions) {
    const [from, to] = [parseVersion(versions[1]), parseVersion(versions[2])]
    const change = `${versions[1]} → ${versions[2]}`
    if (!from || !to) return { level: '高', reason: `題のバージョンを読めない（${change}）` }
    if (from.major !== to.major) return { level: '高', reason: `major の更新（${change}）` }
    if (from.major === 0 && from.minor !== to.minor) return { level: '高', reason: `major が 0 で minor の更新（${change}）` }
    if (!group) return { level: null, reason: `major でない更新（${change}）。経路の判定のまま` }
  }
  if (group) {
    if (group[1].endsWith(MINOR_PATCH_GROUP)) return { level: null, reason: `group「${group[1]}」（minor と patch だけ）。経路の判定のまま` }
    return { level: '高', reason: `group「${group[1]}」は major を含みうる` }
  }
  return { level: '高', reason: `題から更新の種類を読めない（${text === '' ? '題が空' : text}）` }
}

/**
 * Dependabot の pull request の判定に、題から読んだ更新の種類を入れる。経路と宣言の判定と、高い方にする。
 *
 * @template {{ level: '高' | '低' }} T
 * @param {T} result 経路と宣言の判定
 * @param {string | undefined} title pull request の題
 * @returns {T & { dependabot: string }} 判定（他の項目はそのまま）と、更新の種類の理由
 */
export function applyDependabotTitle(result, title) {
  const update = judgeDependabotTitle(title)
  return { ...result, level: update.level ?? result.level, dependabot: update.reason }
}

/**
 * 判定を、pull request の本文に貼れる数行にする。最後の行は check-all.sh が求める要約の行。
 *
 * @param {{ level: string, hits: { name: string, files: string[] }[], specs: string[], dependabot?: string, total: number }} result 判定
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
    ...(result.dependabot ? [`- Dependabot の更新：${result.dependabot}`] : []),
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
 * Dependabot の pull request か。作成者だけで見分ける（ブランチ名は誰でも付けられるので使わない）。
 *
 * @param {{ author?: string }} pullRequest 作成者（分からなければ省く）
 * @returns {boolean} Dependabot なら true
 */
export function isDependabot({ author }) {
  return author === DEPENDABOT_AUTHOR
}

/**
 * pull request の本文と判定を比べるかを決め、比べる。Dependabot は比べない。本文を渡さなければ（check-all.sh）比べない。
 *
 * @param {{ level: '高' | '低', body?: string, author?: string }} pullRequest 判定、本文、作成者
 * @returns {{ ok: boolean, message: string | null }} 成功か、と出力する 1 行（比べないときは Dependabot の行か null）
 */
export function checkPullRequest({ level, body, author }) {
  if (isDependabot({ author })) return { ok: true, message: 'Dependabot の pull request なので、本文と比べない' }
  if (body === undefined) return { ok: true, message: null }
  return compareBody(level, body)
}

/** git を実行し、失敗したら null を返す */
function tryGit(args, cwd) {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] })
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
 * @param {string} [cwd] 実行する場所（リポジトリのルート）。なければ今のディレクトリ
 * @returns {{ files: string[], base: string } | { error: string }} 差分のファイル（名前の順）と分岐点の commit か、求められない理由
 */
export function listChangedFiles(ref, gitFiles, cwd = process.cwd()) {
  const base = tryGit(['merge-base', 'HEAD', ref], cwd)?.trim()
  if (!base) {
    if (isShallowRepository(cwd)) return { error: SHALLOW_CLONE_MESSAGE }
    return { error: `HEAD と ${ref} の分岐点を求められない（ref があるか、git fetch したかを確認する）` }
  }
  const changed = tryGit(['diff', '--name-only', '-z', '--no-renames', base], cwd)
  const committed = tryGit(['ls-tree', '-r', '-z', '--name-only', 'HEAD'], cwd)
  if (changed === null || committed === null) return { error: `${ref} との差分を読めない` }
  // HEAD にないファイルは、git add したもの（diff に入る）か、追跡していないもの
  const inHead = new Set(splitNulList(committed))
  const added = gitFiles.filter((file) => !inHead.has(file))
  return { files: [...new Set([...splitNulList(changed), ...added])].sort(), base }
}

/**
 * 基準のバージョンのファイルの中身を返す関数を作る。
 *
 * @param {string} base 基準の commit
 * @param {string} [cwd] 実行する場所（リポジトリのルート）。なければ今のディレクトリ
 * @returns {(file: string) => string | null} 中身を返す関数（基準にないファイルは null）
 */
export function baseFileReader(base, cwd = process.cwd()) {
  return (file) => tryGit(['show', `${base}:${file}`], cwd)
}

if (isMain(import.meta.url)) {
  const { values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      base: { type: 'string', default: DEFAULT_BASE },
      'body-file': { type: 'string' },
      author: { type: 'string' },
      title: { type: 'string' },
      'skip-path-check': { type: 'boolean', default: false },
    },
  })
  const config = parseRiskConfig(readFileSync(CONFIG_FILE, 'utf8'))
  const gitFiles = listGitFiles()
  const missing = values['skip-path-check'] ? [] : findMissingPaths(config, gitFiles)
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
  const judged = judgeRisk(changed.files, config, (file) => (existsSync(file) ? readFileSync(file, 'utf8') : null), baseFileReader(changed.base))
  const result = isDependabot(values) ? applyDependabotTitle(judged, values.title) : judged
  const lines = formatJudgement(result)
  for (const line of lines.slice(0, -1)) console.log(line)
  for (const item of result.unreadable) console.log(describeUnreadableLine(item))
  // - は標準入力（CI は本文を環境変数から標準入力に渡す）
  const bodyFile = values['body-file']
  const body = bodyFile === undefined ? undefined : readFileSync(bodyFile === '-' ? 0 : bodyFile, 'utf8')
  const checked = checkPullRequest({ level: result.level, body, author: values.author })
  if (checked.message !== null) console.log(checked.message)
  console.log(lines.at(-1))
  process.exit(checked.ok && result.unreadable.length === 0 ? 0 : 1)
}
