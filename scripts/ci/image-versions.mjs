// コンテナのイメージの版が、compose、Gradle、統合テストで一致することと、compose の版が patch まで固定されていることを確認する。
// compose の `image:` の `名前:版` を正とし、同じ名前のイメージを書いた Gradle と統合テストのコードの版が違えば失敗にする。
// 版がずれると、手元（compose）と CI（Testcontainers）で別のイメージを動かすことになり、片方だけで起きる不具合を見逃す。
// 使い方：node scripts/ci/image-versions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** compose のファイル */
const COMPOSE_FILE = 'docker/compose.yaml'

/** イメージの名前と版を探すソースの置き場所（Git の対象のファイルだけを読む） */
const SOURCE_PATHS = ['backend/ecsite/build.gradle.kts', 'backend/ecsite/src/integrationTest']

/**
 * patch を持たない版の付け方をするイメージと、許す版の形。
 * - postgres：公式のイメージは `18-alpine` のように major で使うのが通例で、major の中の更新は
 *   セキュリティと不具合の修正だけで互換が保たれるため、major だけを許す。
 * - chrislusf/seaweedfs：版が `4.48` の major.minor だけで、patch の版が存在しないため、major.minor を許す。
 */
const PATCHLESS_ALLOWED = new Map([
  ['postgres', /^\d+(-[A-Za-z]+)?$/],
  ['chrislusf/seaweedfs', /^\d+\.\d+$/],
])

/** 版に使える文字 */
const VERSION = '[A-Za-z0-9._-]+'

/** patch まで固定した版（先頭の v は付けてよい。後ろの `-alpine` などの種類の名前も付けてよい） */
const PINNED = /^v?\d+\.\d+\.\d+/

/** 読めない `image:` の行の理由 */
const UNREADABLE_IMAGE_REASON = '変数の差し込みを読めない。既定値 ${NAME:-名前:版} の形だけを扱う'

/**
 * compose の `image:` から、イメージの名前ごとのバージョンの集合と、読めない行を読む。<br />
 * YAML のライブラリを使わず、`image:` で始まる行を読む（コメントの行は読まない）。
 * 変数の差し込みは既定値（`${NAME:-既定値}`）を読み、既定値のない書き方は版を決められないので、読めない行に集める。
 *
 * @param {string} text compose のファイル
 * @returns {{ images: Map<string, Set<string | undefined>>, unreadable: { line: number, text: string, reason: string }[] }}
 *   イメージの名前から版の集合（版のない書き方は undefined）と、読めない行（1 始まりの行番号と内容、行の順）
 */
export function parseComposeImages(text) {
  const images = new Map()
  const unreadable = []
  for (const [index, raw] of text.split('\n').entries()) {
    const match = /^\s*image:\s*["']?([^"'\s#]+)/.exec(raw)
    if (!match) {
      continue
    }
    let reference = match[1]
    if (reference.includes('$')) {
      const fallback = /^\$\{[A-Za-z_]\w*:?-([^${}]+)\}$/.exec(reference)?.[1]
      if (fallback === undefined) {
        unreadable.push({ line: index + 1, text: raw, reason: UNREADABLE_IMAGE_REASON })
        continue
      }
      reference = fallback
    }
    const colon = reference.lastIndexOf(':')
    const hasVersion = colon > reference.lastIndexOf('/')
    const name = hasVersion ? reference.slice(0, colon) : reference
    const version = hasVersion ? reference.slice(colon + 1) : undefined
    if (!images.has(name)) {
      images.set(name, new Set())
    }
    images.get(name).add(version)
  }
  return { images, unreadable }
}

/**
 * 読めない `image:` の行を、失敗の文にする。
 *
 * @param {string} file compose のファイルのパス
 * @param {{ line: number, text: string, reason: string }[]} unreadable parseComposeImages が返した、読めない行
 * @returns {string[]} `ファイル:行 内容（理由）`（行の順）
 */
export function describeUnreadableImages(file, unreadable) {
  return unreadable.map((item) => describeUnreadableLine({ file, ...item }))
}

/** 正規表現の特別な文字を、そのままの文字として書く */
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\/-]/g, '\\$&')

/**
 * ソースから、イメージの名前と版を読む。<br />
 * compose にある名前は、名前の一覧で探す（名前の前後が続く別の名前は読まない）。
 * compose にない名前は、`DockerImageName.parse("名前:版")` の文字列だけを読む。
 *
 * @param {string} text ソースのファイル
 * @param {Map<string, Set<string | undefined>>} images compose のイメージ
 * @returns {{ known: { line: number, name: string, version: string }[], unknown: { line: number, name: string, version: string }[] }}
 *   compose にある名前の記述と、compose にない名前の記述
 */
export function readSourceImages(text, images) {
  const known = []
  const unknown = []
  const names = [...images.keys()]
  const knownPattern = names.length
    ? new RegExp(`(?<![\\w./-])(${names.map(escapeRegExp).join('|')}):(${VERSION})`, 'g')
    : null
  const parsePattern = new RegExp(`DockerImageName\\.parse\\("([^":]+):(${VERSION})"\\)`, 'g')
  text.split('\n').forEach((line, index) => {
    if (knownPattern) {
      for (const match of line.matchAll(knownPattern)) {
        known.push({ line: index + 1, name: match[1], version: match[2] })
      }
    }
    for (const match of line.matchAll(parsePattern)) {
      if (!images.has(match[1])) {
        unknown.push({ line: index + 1, name: match[1], version: match[2] })
      }
    }
  })
  return { known, unknown }
}

/**
 * ソースのイメージの版を compose と照合する。<br />
 * compose にないイメージを統合テストだけが使うことはある（compose で動かす必要がないもの）ので、失敗にせず報告だけにする。
 * 版を比べる相手がなく、食い違いを見つけられないだけで、手元と CI の差の原因にはならないため。
 *
 * @param {Map<string, Set<string | undefined>>} images compose のイメージ
 * @param {{ path: string, text: string }[]} files 読むソース
 * @returns {{ mismatches: string[], unknown: string[] }} compose と版が違う記述と、compose にないイメージの記述
 */
export function findImageProblems(images, files) {
  const mismatches = []
  const unknown = []
  for (const file of files) {
    const found = readSourceImages(file.text, images)
    for (const item of found.known) {
      const versions = images.get(item.name)
      if (!versions.has(item.version)) {
        mismatches.push(`${file.path}:${item.line} ${item.name}:${item.version}（compose は ${[...versions].join('、')}）`)
      }
    }
    for (const item of found.unknown) {
      unknown.push(`${file.path}:${item.line} ${item.name}:${item.version}`)
    }
  }
  return { mismatches, unknown }
}

/**
 * compose のイメージのうち、版が patch まで固定されていないものを探す。<br />
 * 版のない書き方、`latest`、`3` や `3.6` のように patch のない版が対象。
 * patch のない版は、{@link PATCHLESS_ALLOWED} のイメージだけ許す。
 *
 * @param {Map<string, Set<string | undefined>>} images compose のイメージ
 * @returns {string[]} 固定されていないイメージの書き方
 */
export function findUnpinned(images) {
  const problems = []
  for (const [name, versions] of images) {
    for (const version of versions) {
      if (version === undefined) {
        problems.push(`${name}（版がない）`)
      } else if (!PINNED.test(version) && !PATCHLESS_ALLOWED.get(name)?.test(version)) {
        problems.push(`${name}:${version}`)
      }
    }
  }
  return problems
}

/** Git の対象のファイルのうち、`SOURCE_PATHS` の下のものを読む */
function readSourceFiles() {
  const paths = listGitFiles(SOURCE_PATHS).filter((path) => /\.(java|kts)$/.test(path))
  exitIfNoFiles(paths, 'イメージのバージョンを書くファイル')
  return paths.map((path) => ({ path, text: readFileSync(path, 'utf8') }))
}

if (isMain(import.meta.url)) {
  const files = readSourceFiles()
  const { images, unreadable } = parseComposeImages(readFileSync(COMPOSE_FILE, 'utf8'))
  const { mismatches, unknown } = findImageProblems(images, files)
  const unpinned = findUnpinned(images)
  const unreadableLines = describeUnreadableImages(COMPOSE_FILE, unreadable)
  for (const item of unreadableLines) {
    console.log(item)
  }
  for (const item of mismatches) {
    console.log(`版の食い違い：${item}`)
  }
  for (const item of unpinned) {
    console.log(`${COMPOSE_FILE} の版が patch まで固定されていない：${item}`)
  }
  for (const item of unknown) {
    console.log(`警告：compose にないイメージ（照合しない）：${item}`)
  }
  const problems = mismatches.length + unpinned.length + unreadableLines.length
  console.log(
    `イメージ：${images.size} 件（compose）、読んだファイル：${files.length} 件、` +
      `食い違い：${mismatches.length} 件、固定されていない：${unpinned.length} 件、読めない行：${unreadableLines.length} 件`,
  )
  process.exit(problems || images.size === 0 || files.length === 0 ? 1 : 0)
}
