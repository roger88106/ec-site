// レビューの対象のファイルと比べる候補（同じ種類のファイル、参照元、一緒に変わるファイル、共有の部品）を、git の記録から集める。
// 使い方：node .claude/skills/review/compare-targets.mjs <リポジトリのルートからのパス>...（リポジトリのルートで。結果を標準出力に Markdown で出す）
//
// レビュー役は対象のファイルの中だけで判断しがちで、共通にすべき処理、処理を置く層の誤り、共通の部品ができた後の古い書き方の残りを見落とす。
// 比べる候補を先に機械で集めて渡し、他のファイルと比べる手がかりにする。候補は手がかりで、全てを読む必要はない。
//
// 同じ種類：名前の末尾の大文字で始まる語と拡張子（Service.java、Page.tsx）が同じファイル。末尾に大文字で始まる語がない名前は、同じ拡張子のファイル。
// テストのファイルは、テストのファイルどうしで探す。
// 並びは、親のフォルダーの名前が同じもの（同じ層）、共通の上位のフォルダーが深いもの、パスの順。
//
// 参照元から、アーカイブした spec を除く。
//
// 終了コード：0 成功、2 使い方の誤り（引数なし、追跡していないファイル）。
import { execFileSync } from 'node:child_process'
import path from 'node:path'

/** 同じ種類のファイルの上限 */
const MAX_SAME_KIND = 3
/** 参照元の上限 */
const MAX_REFERRERS = 10
/** 参照元から除く場所。終わった spec は比べ先にならず、残すと他の参照元が上限から外れるため */
const REFERRER_EXCLUDED = ':(exclude)docs/08-spec/archive/'
/** 一緒に変わるファイルを数える commit の数 */
const MAX_COMMITS = 50
/** 一緒に変わるファイルとして出す最小の回数 */
const MIN_CO_CHANGES = 2
/** 一緒に変わるファイルの上限 */
const MAX_CO_CHANGED = 5
/** 共有の部品の一覧の上限 */
const MAX_SHARED = 50

/** テストのファイルの名前。1 つ目の組は、テストを除いた名前を作る置き換え */
const TEST_FILE = /(?:Test(\.java)|\.test(\.[cm]?[jt]sx?))$/

/** 共有の部品。対象の置き場所、見出し、部品の置き場所、部品のファイル */
const SHARED = [
  { target: 'frontend/', title: 'フロント（frontend/shared/）', dir: 'frontend/shared/src/', file: /\.tsx?$/ },
  {
    target: 'backend/',
    title: 'バックエンド（backend/ecsite/src/main/java/com/ecsite/common/）',
    dir: 'backend/ecsite/src/main/java/com/ecsite/common/',
    file: /\.java$/,
  },
]

/**
 * git を実行して標準出力を返す。
 *
 * @param {string[]} args 引数
 * @returns {string} 標準出力
 */
function git(args) {
  return execFileSync('git', ['-c', 'core.quotepath=false', ...args], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
}

/**
 * 引数のパスを、リポジトリのルートからの / 区切りのパスにする。
 *
 * @param {string} arg 引数
 * @returns {string} パス
 */
function normalizePath(arg) {
  return arg.replaceAll('\\', '/').replace(/^(?:\.\/)+/, '')
}

/**
 * ファイルの種類を返す。種類が同じファイルを、同じ種類のファイルとする。
 *
 * @param {string} file パス
 * @returns {string} 種類（テストの印、末尾の語、拡張子をつないだもの）
 */
function kindOf(file) {
  const isTest = TEST_FILE.test(file)
  const base = isTest ? file.replace(TEST_FILE, '$1$2') : file
  const ext = path.posix.extname(base)
  const stem = path.posix.basename(base, ext)
  const word = /[A-Z][a-z0-9]*$/.exec(stem)?.[0]
  const key = word && word !== stem ? `${word}${ext}` : `*${ext}`
  return `${isTest ? 'test:' : ''}${key}`
}

/**
 * 2 つのパスで、先頭から同じフォルダーが続く数を返す。
 *
 * @param {string} a パス
 * @param {string} b パス
 * @returns {number} 先頭から一致するフォルダーの数
 */
function commonDepth(a, b) {
  const dirsA = a.split('/').slice(0, -1)
  const dirsB = b.split('/').slice(0, -1)
  let depth = 0
  while (depth < dirsA.length && dirsA[depth] === dirsB[depth]) depth++
  return depth
}

/**
 * パスの親のフォルダーの名前を返す。
 *
 * @param {string} file パス
 * @returns {string} 名前
 */
function parentName(file) {
  return path.posix.basename(path.posix.dirname(file))
}

/**
 * 同じ種類のファイルを、同じ層のもの、近いフォルダーのものを先にして返す。
 *
 * @param {string} file 対象
 * @param {Map<string, string[]>} byKind 種類ごとのファイル（パスの順）
 * @returns {string[]} 同じ種類のファイル
 */
function findSameKind(file, byKind) {
  const parent = parentName(file)
  return (byKind.get(kindOf(file)) ?? [])
    .filter((other) => other !== file)
    .map((other) => ({ other, sameLayer: parentName(other) === parent ? 1 : 0, depth: commonDepth(file, other) }))
    .sort((a, b) => b.sameLayer - a.sameLayer || b.depth - a.depth || (a.other < b.other ? -1 : 1))
    .slice(0, MAX_SAME_KIND)
    .map((entry) => entry.other)
}

/**
 * ファイルの名前（拡張子を除く）を単語として含む追跡ファイルを返す。
 *
 * @param {string} file 対象
 * @returns {string[]} 参照元（パスの順）
 */
function findReferrers(file) {
  const name = path.posix.basename(file, path.posix.extname(file))
  let output
  try {
    output = git(['grep', '-l', '-z', '-w', '-F', '-e', name, '--', '.', REFERRER_EXCLUDED])
  } catch (e) {
    // 1 件もないとき git grep は終了コード 1 を返す
    if (e.status === 1) return []
    throw e
  }
  return output.split('\0').filter(Boolean).filter((other) => other !== file).sort().slice(0, MAX_REFERRERS)
}

/**
 * 対象を変えた直近の commit で、一緒に変わったファイルと回数を返す。
 *
 * @param {string} file 対象
 * @param {Set<string>} tracked 追跡しているファイル（消えたファイルを除くため）
 * @returns {{ file: string, count: number }[]} 回数の多い順、同じならパスの順
 */
function findCoChanged(file, tracked) {
  // --full-diff がないと、--name-only は対象のパスしか出さない
  const output = git(['log', `-n${MAX_COMMITS}`, '--full-diff', '--format=%x00', '--name-only', '--', file])
  const counts = new Map()
  for (const commit of output.split('\0')) {
    const files = new Set(commit.split(/\r?\n/).filter(Boolean))
    for (const other of files) {
      if (other !== file && tracked.has(other)) counts.set(other, (counts.get(other) ?? 0) + 1)
    }
  }
  return [...counts]
    .filter(([, count]) => count >= MIN_CO_CHANGES)
    .map(([other, count]) => ({ file: other, count }))
    .sort((a, b) => b.count - a.count || (a.file < b.file ? -1 : 1))
    .slice(0, MAX_CO_CHANGED)
}

/**
 * 見出しと箇条を Markdown の行にする。箇条がなければ「なし」とする。
 *
 * @param {string} title 見出し（### の後）
 * @param {string[]} entries 箇条の中身
 * @returns {string[]} 行
 */
function section(title, entries) {
  return [`### ${title}`, '', ...(entries.length ? entries.map((entry) => `- ${entry}`) : ['なし']), '']
}

/** 引数の対象ごとに比べる候補を集め、標準出力に出す。 */
function main() {
  const targets = process.argv.slice(2).map(normalizePath)
  if (!targets.length) {
    console.error('使い方：node .claude/skills/review/compare-targets.mjs <リポジトリのルートからのパス>...')
    process.exit(2)
  }
  const files = git(['ls-files', '-z']).split('\0').filter(Boolean).sort()
  const tracked = new Set(files)
  const untracked = targets.filter((file) => !tracked.has(file))
  if (untracked.length) {
    console.error(`追跡していないファイル：${untracked.join('、')}`)
    process.exit(2)
  }
  const byKind = new Map()
  for (const file of files) {
    const kind = kindOf(file)
    if (!byKind.has(kind)) byKind.set(kind, [])
    byKind.get(kind).push(file)
  }

  const out = ['# 比べる対象', '']
  for (const file of targets) {
    out.push(`## ${file}`, '')
    out.push(...section('同じ種類のファイル', findSameKind(file, byKind)))
    out.push(...section('参照元', findReferrers(file)))
    out.push(...section('一緒に変わるファイル', findCoChanged(file, tracked).map((entry) => `${entry.file}（${entry.count} 回）`)))
  }

  const shared = SHARED.filter((area) => targets.some((file) => file.startsWith(area.target)))
  if (shared.length) {
    out.push('## 共有の部品', '')
    for (const area of shared) {
      const parts = files.filter((file) => file.startsWith(area.dir) && area.file.test(file) && !TEST_FILE.test(file))
      const shown = parts.slice(0, MAX_SHARED)
      if (parts.length > shown.length) shown.push(`ほか ${parts.length - shown.length} 件`)
      out.push(...section(area.title, shown))
    }
  }
  process.stdout.write(`${out.join('\n').trimEnd()}\n`)
}

main()
