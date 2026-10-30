// 文書への参照が切れていないことを確認する。
//   - Markdown のリンク：先のファイルと #見出し があるか（見出しの slug は GitHub と同じ作り方）
//   - 見出しの参照（`[開発の進め方](../../docs/05-開発規則/workflow.md)の「起票」`、`開発の進め方の「起票」` など）：リンクか文書の名前の直後の「の「…」」だけを読み、
//     先の文書に、その見出し（番号を除く）が 1 つだけあるか。同じ見出しが 2 つ以上あれば、親の見出しを前に付けた形（`の「段階 1」の「タスク」`）で 1 つに決める。
//     見出しは、リンクを表示の文字に、コードの印を除き、全角と半角を揃え、空白をまとめて比べる。末尾の全角の括弧の補足（`（DS-283-5：DC-283-21〜25）` など）は、
//     除いた形でも全て書いた形でも当たる（除くと 2 つ以上に当たるなら、1 つに決まらないとする）。Markdown のコードブロックの中は見ない
//   - 節の番号の参照（「第」と番号と「節」）：失敗にし、見出しの形に書き換えるよう示す。節の番号は、節を加える・移すたびに変わり、意味の違う節を指したまま通るため。
//     Markdown は、インラインコードとコードブロックの外の全て（名前のないもの、spec・plan の節も）。コードは、文書の名前・リンク・パスの直後（間の「の」は許す）のものだけ。
//     指す先の文書に、その番号の見出しがあれば、失敗のメッセージにその見出しを示す。名前のない番号は、Markdown ではその文書自身を指すとして見出しを求める。
//     ただし、直前が「（」「、」などのときは、前の文書の節かもしれないので見出しを示さず、先の文書を書くよう示す
// 文書の名前は、各文書の 1 行目の見出しから括弧（`（frontend/store）` など）を除いたもの。
// 見出しの参照と節の番号の参照の先は、直前のリンク、文書の名前、パス（`docs/06-手順書/ci.md`、`notification.md` など）で決める。
// 直前の印（`` ` ``、「」」、「の」）と、閉じ括弧（`（docs/06-手順書/ci.md）の`）と、末尾の括弧の補足（`店舗画面の構成（補足）の`）は除いて読む。
// 見出しの参照と節の番号の参照は、Markdown の他にコードと設定のファイルも見る（TEXT_FILE の拡張子。コメントを切り出さず、文字列も含めてファイルの全体を読む）。
// アーカイブした spec（当時の記録で、番号が変わらない）と、適用済みのマイグレーション（直せない）は見ない。
// ただし、アーカイブの下の MVP の完成までの一覧（mvp.md）は spec の記録でなく一覧の文書で、spec を移すたびにリンクが切れるので見る。
// この検査のテストと ja-text のテストも見ない。検査の例として、切れた参照と `第 N 節` をわざと書くため。
// 使い方：node scripts/ci/doc-refs.mjs（リポジトリのルートで）
import { existsSync, readFileSync } from 'node:fs'
import { basename, dirname, join, normalize } from 'node:path/posix'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 見ないファイル（当時の記録、直せないもの、この検査のテストの例） */
const EXCLUDED = [/^docs\/08-spec\/archive\/(?!mvp\/mvp\.md$)/, /\/db\/migration\//, /^scripts\/ci\/(doc-refs|ja-text)\.test\.mjs$/]

/**
 * 見ないファイルか。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 見ないなら true
 */
export function isExcluded(file) {
  return EXCLUDED.some((pattern) => pattern.test(file))
}

/** 参照を探すファイル。Gradle の設定（.kts）と、設定のファイル（xml、json、jsonc、grit、ps1）も、コメントや説明に参照を書くので見る */
const TEXT_FILE = /\.(md|java|kts|ts|tsx|mjs|cjs|js|yml|yaml|sh|ps1|toml|properties|xml|json|jsonc|grit)$/

/**
 * 参照を探すファイルか（拡張子で決める）。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 探すなら true
 */
export function isTargetFile(file) {
  return TEXT_FILE.test(file)
}

/** 節の番号の参照（「第 4.2、5.6 節」「第 4〜6・8 節」など。空白のない「第2節」と、全角の数字の「第２節」も） */
const SECTION_NUMBER = /第 ?([0-9０-９][0-9０-９.．・、〜~ ]*?) ?節/g

/** 見出しの先頭の番号（`1.` か `4.1` の形。`### 1 通だけ送る` の 1 は見出しの一部なので除かない） */
const HEADING_NUMBER = /^(\d+(?:\.\d+)*\.|\d+(?:\.\d+)+)\s+/

/**
 * Markdown のインラインコードを、同じ長さの空白に替える（位置を変えずに、中の文字を読まないため）。
 *
 * @param {string} line 1 行
 * @returns {string} インラインコードを空白にした行
 */
function maskInlineCode(line) {
  return line.replace(/(`+)(.+?)\1/g, (code) => ' '.repeat(code.length))
}

/**
 * 見出しを GitHub と同じ形の slug にする。
 *
 * @param {string} heading 見出しの文字
 * @returns {string} slug
 */
export function slug(heading) {
  return heading
    .replace(/`/g, '')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/ /g, '-')
}

/**
 * 見出しの参照で比べる形にする。リンクは表示の文字に、コードの印は除き、全角と半角を揃え（NFKC）、空白を 1 つにまとめる。
 *
 * @param {string} text 見出しか、参照の「」の中の文字
 * @returns {string} 比べる形
 */
export function headingKey(text) {
  return text
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/`/g, '')
    .normalize('NFKC')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * Markdown の見出しを読み、リンクの先にできる slug と、節の番号と、見出しの参照の先を集める。
 *
 * @param {string} text Markdown
 * @returns {{ title: string | null, anchors: Set<string>, sections: Map<string, string>, headings: { exact: string, keys: Set<string>, ancestors: Set<string>[] }[] }} 1 行目の見出し、slug、節の番号から見出し（番号を除いたもの）への対応、番号を除いた見出し（exact は比べる形、keys はそれと末尾の括弧の補足を除いた形。ancestors は親から順の上の見出しの keys）
 */
export function readHeadings(text) {
  const anchors = new Set()
  const sections = new Map()
  const headings = []
  /** 今の見出しの上の見出し（レベル、比べる形） */
  const stack = []
  const seen = {}
  let title = null
  let inCode = false
  for (const line of text.replace(/^﻿/, '').split('\n')) {
    if (line.startsWith('```')) inCode = !inCode
    const match = !inCode && line.match(/^(#{1,6})\s+(.*)$/)
    if (!match) continue
    if (match[1] === '#' && title === null) title = match[2].trim()
    let key = slug(match[2])
    if (seen[key] !== undefined) {
      seen[key] += 1
      key = `${key}-${seen[key]}`
    } else seen[key] = 0
    anchors.add(key)
    const text = match[2].replace(HEADING_NUMBER, '')
    const number = match[2].match(HEADING_NUMBER)
    if (number) {
      const key = number[1].replace(/\.$/, '')
      if (!sections.has(key)) sections.set(key, text.trim())
    }
    const level = match[1].length
    while (stack.length && stack.at(-1).level >= level) stack.pop()
    // 末尾の全角の括弧の補足（`（DS-283-5：DC-283-21〜25）` など）を除いた形でも当たるようにする
    const exact = headingKey(text)
    const keys = new Set([exact, headingKey(text.replace(/（[^（）]*）\s*$/, ''))])
    headings.push({ exact, keys, ancestors: stack.map((item) => item.keys) })
    stack.push({ level, keys })
  }
  return { title, anchors, sections, headings }
}

/**
 * 見出しの参照に当たる見出しの数を数える。親の見出しは、上の見出しに同じ順で含まれれば（子孫でよい）当たる。
 * 先の見出しは、完全に一致するもの（括弧の補足も同じ）があればそれだけを数え、なければ末尾の括弧の補足を除いた形で数える
 * （兄弟の `設定` と `設定（任意）` では、`「設定」` は `設定` に決まる）。
 *
 * @param {{ exact: string, keys: Set<string>, ancestors: Set<string>[] }[]} headings 先の文書の見出し
 * @param {string[]} path 参照の「」の中の文字（親から順、最後が先の見出し）
 * @returns {number} 当たった見出しの数
 */
export function countHeadingMatches(headings, path) {
  const keys = path.map(headingKey)
  const target = keys.at(-1)
  const parents = keys.slice(0, -1)
  const underParents = (heading) => {
    let index = 0
    for (const ancestor of heading.ancestors) {
      if (index < parents.length && ancestor.has(parents[index])) index += 1
    }
    return index === parents.length
  }
  const exact = headings.filter((heading) => heading.exact === target && underParents(heading)).length
  if (exact) return exact
  return headings.filter((heading) => heading.keys.has(target) && underParents(heading)).length
}

/**
 * 文書の名前を、参照で使う形にする（括弧の中と、コードの印を除く）。
 *
 * @param {string} title 1 行目の見出し
 * @returns {string} 参照で使う名前
 */
export function referenceName(title) {
  return title.replace(/（[^）]*）/g, '').replace(/`/g, '').trim()
}

/**
 * 「第 4〜6・8 節」の番号の部分を、確認する番号に分ける（範囲は両端だけ。全角の数字は半角に揃える）。
 *
 * @param {string} numbers 番号の部分（`4〜6・8` など）
 * @returns {string[]} 節の番号
 */
export function splitSectionNumbers(numbers) {
  return numbers
    .normalize('NFKC')
    .split(/[・、〜~]/)
    .map((number) => number.trim())
    .filter(Boolean)
}

/**
 * 1 つのファイルの中の参照を集める。
 *
 * @param {string} file ファイルのパス（リポジトリのルートから）
 * @param {string} text ファイルの中身
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応
 * @param {Map<string, string>} basenames ファイル名（`admin.md` など）からファイルのパスへの対応（同じ名前が 2 つ以上あるものは除く）
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @returns {{ line: number, target: string | null, anchor?: string, headings?: string[], sections?: string[], unknown?: boolean, text: string }[]} 参照（anchor はリンクの #、headings は見出しの参照の「」の中、sections は節の番号の参照の番号。target は、Markdown の節の番号の参照で先が決まらなければ null。unknown は、直前が「（」などで先の文書が決まらないもの）
 */
export function findReferences(file, text, names, basenames, exists = existsSync) {
  const refs = []
  const isMarkdown = file.endsWith('.md')
  const lines = text.split('\n')
  let inCode = false
  lines.forEach((line, index) => {
    if (isMarkdown && line.startsWith('```')) inCode = !inCode
    const lineNumber = index + 1
    if (isMarkdown && !inCode) {
      for (const [, link] of line.matchAll(/\]\(([^)\s]+)\)/g)) {
        if (/^(https?:|mailto:)/.test(link)) continue
        const [path, anchor] = link.split('#')
        const target = path ? normalize(join(dirname(file), decodeURIComponent(path))) : file
        refs.push({ line: lineNumber, target, anchor: anchor ? decodeURIComponent(anchor) : undefined, text: link })
      }
    }
    if (!(isMarkdown && inCode)) {
      // 「」の後に「の「」が続くものは、親から順の見出しとして 1 つの参照にする
      for (const match of line.matchAll(/(?:\s?の「[^「」\n]*」)+/g)) {
        const target = resolveDocument(file, line.slice(0, match.index), names, basenames, exists, true)
        if (!target) continue
        const path = [...match[0].matchAll(/「([^「」]*)」/g)].map((quote) => quote[1])
        refs.push({ line: lineNumber, target, headings: path, text: match[0].trim() })
      }
    }
    if (isMarkdown && inCode) return
    // Markdown はインラインコードの外の全てを、コードは指す先の文書が決まるものだけを、節の番号の参照にする
    for (const match of (isMarkdown ? maskInlineCode(line) : line).matchAll(SECTION_NUMBER)) {
      const before = line.slice(0, match.index).trimEnd()
      // spec・plan の節（`plan 001`、`spec 003 の`）は、指す先の文書を名前で決められない
      const spec = /(spec|plan|\d{3})(\s*の)?$/.test(before.replace(/[`」]+$/, '').trimEnd())
      const found = spec ? null : resolveDocument(file, before, names, basenames, exists)
      if (!found && !isMarkdown) continue
      // 名前のない番号は、Markdown ではその文書自身を指す。ただし、直前が「（」「、」などなら、前の文書の節かもしれず、先を決めない
      const unknown = !found && !spec && /[（(、,・とや]$/.test(before)
      const target = found ?? (spec || unknown ? null : file)
      refs.push({ line: lineNumber, target, unknown, sections: splitSectionNumbers(match[1]), text: match[0] })
    }
  })
  return refs
}

/**
 * 節の番号で参照したときの、失敗のメッセージを作る。指す先の文書に、その番号の見出しがあれば示す。
 *
 * @param {{ target: string | null, sections: string[], unknown?: boolean }} ref 節の番号の参照
 * @param {(file: string) => ReturnType<typeof readHeadings>} headingsOf 文書の見出しを返す関数
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @param {string} where 参照の場所（ファイル:行 参照の文字）
 * @returns {string} 失敗のメッセージ
 */
function sectionNumberProblem(ref, headingsOf, exists, where) {
  let hint = ''
  if (ref.unknown) {
    hint = '（先の文書が決まらないので、先の文書を書く）'
  } else if (ref.target?.endsWith('.md') && exists(ref.target)) {
    const { sections } = headingsOf(ref.target)
    const found = ref.sections.filter((number) => sections.has(number)).map((number) => `「${sections.get(number)}」`)
    const missing = ref.sections.filter((number) => !sections.has(number))
    const parts = []
    if (found.length) parts.push(`${ref.target} の${found.join('、')}`)
    if (missing.length) parts.push(`${found.length ? '' : `${ref.target} に`}番号 ${missing.join('・')} の節はない`)
    hint = `（${parts.join('、')}）`
  }
  return `節の番号で参照している。見出しの形（<文書の名前>の「<見出し>」）に書き換える${hint}：${where}`
}

/**
 * 参照の前の文字から、指す先の文書を決める。見出しの参照と節の番号の参照で同じ読み方にする。
 * 直前のリンク、constitution、パス（`docs/06-手順書/ci.md`、`notification.md`）、文書の名前の順に当てる。
 * 名前の後の「の」、パスや名前の印（`` ` ``、「」」）、閉じ括弧（`（docs/06-手順書/ci.md）の`）は除いて読む。
 * 当たらず、末尾が括弧の補足（`店舗画面の構成（補足）の`）なら、補足の前で読み直す。
 * 見出しの参照では、パスはファイルがあるときだけ先にする（外のリポジトリのファイルや、コードの中で組み立てるパスを、切れた参照にしないため）。
 *
 * @param {string} file 参照のあるファイル
 * @param {string} before 参照の前の文字
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応（長い名前から順）
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @param {boolean} existingPath パスを、ファイルがあるときだけ先にするか
 * @returns {string | null} 文書のパス。決まらなければ null
 */
export function resolveDocument(file, before, names, basenames, exists = existsSync, existingPath = false) {
  const text = before.trimEnd().replace(/\s*の$/, '').trimEnd()
  const found = resolveEnd(file, text, names, basenames, exists, existingPath)
  if (found !== undefined) return found
  const withoutNote = text.replace(/（[^（）]*）$/, '').trimEnd()
  if (withoutNote === text) return null
  return resolveEnd(file, withoutNote, names, basenames, exists, existingPath) ?? null
}

/**
 * 文字の末尾にあるリンク・パス・文書の名前から、文書を決める。
 *
 * @param {string} file 参照のあるファイル
 * @param {string} text 参照の前の文字（「の」を除いたもの）
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応（長い名前から順）
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @param {boolean} existingPath パスを、ファイルがあるときだけ先にするか
 * @returns {string | null | undefined} 文書のパス。外のリンクと、existingPath でファイルのないパスは null、当たらなければ undefined
 */
function resolveEnd(file, text, names, basenames, exists, existingPath) {
  const marked = text.replace(/[`」]+$/, '').trimEnd()
  for (const candidate of [marked, marked.replace(/[）)]$/, '').replace(/[`」]+$/, '').trimEnd()]) {
    const link = candidate.match(/\]\(([^)\s#]*)(#[^)]*)?\)$/)
    if (link) {
      if (/^(https?:|mailto:)/.test(link[1])) return null
      return link[1] ? normalize(join(dirname(file), decodeURIComponent(link[1]))) : file
    }
    const path = candidate.match(/([^\s`'"（(「」<>[\]]+\.md)$/)
    if (path) {
      const relative = normalize(join(dirname(file), path[1]))
      if (exists(path[1])) return normalize(path[1])
      if (exists(relative)) return relative
      return basenames.get(basename(path[1])) ?? (existingPath ? null : normalize(path[1]))
    }
    if (/constitution$/.test(candidate)) return 'docs/constitution.md'
    for (const [name, target] of names) {
      if (candidate.endsWith(name)) return target
    }
  }
  return undefined
}

/**
 * docs/ の下の文書の、名前とファイル名からパスへの対応を作る。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string | null} titleOf 文書の 1 行目の見出しを返す関数
 * @returns {{ names: Map<string, string>, basenames: Map<string, string> }} 文書の名前からパスへの対応（長い名前から順）と、ファイル名からパスへの対応（同じ名前が 2 つ以上あるものは除く）
 */
export function documentIndex(files, titleOf) {
  const docs = files.filter((file) => file.startsWith('docs/') && file.endsWith('.md'))
  const names = new Map()
  for (const doc of docs) {
    const title = titleOf(doc)
    if (title) names.set(referenceName(title), doc)
  }
  const counts = {}
  for (const doc of docs) counts[basename(doc)] = (counts[basename(doc)] ?? 0) + 1
  return {
    // 長い名前から当てる（「フロントの書き方」より先に「テストの書き方」が誤って当たらないよう、名前の長さの順）
    names: new Map([...names].sort((a, b) => b[0].length - a[0].length)),
    basenames: new Map(docs.filter((doc) => counts[basename(doc)] === 1).map((doc) => [basename(doc), doc])),
  }
}

/**
 * 参照の切れたものを探す。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string} read ファイルを読む関数
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @returns {string[]} 切れた参照の説明
 */
export function checkReferences(files, read, exists) {
  const headings = new Map()
  const headingsOf = (file) => {
    if (!headings.has(file)) headings.set(file, readHeadings(read(file)))
    return headings.get(file)
  }
  const { names: sortedNames, basenames } = documentIndex(files, (file) => headingsOf(file).title)

  const problems = []
  for (const file of files) {
    for (const ref of findReferences(file, read(file), sortedNames, basenames, exists)) {
      const where = `${file}:${ref.line} ${ref.text}`
      if (ref.sections) {
        problems.push(sectionNumberProblem(ref, headingsOf, exists, where))
      } else if (!exists(ref.target)) {
        problems.push(`ファイルがない：${where}`)
      } else if (ref.anchor && ref.target.endsWith('.md') && !headingsOf(ref.target).anchors.has(ref.anchor)) {
        problems.push(`見出しがない：${where}`)
      } else if (ref.headings && ref.target.endsWith('.md')) {
        const count = countHeadingMatches(headingsOf(ref.target).headings, ref.headings)
        const quoted = ref.headings.map((heading) => `「${heading}」`).join('の')
        if (count === 0) problems.push(`見出しがない（${ref.target} の${quoted}）：${where}`)
        if (count > 1) problems.push(`見出しが 1 つに決まらない（${ref.target} の${quoted}）。親の見出しを前に付ける：${where}`)
      }
    }
  }
  return problems
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => isTargetFile(file) && !isExcluded(file))
  exitIfNoFiles(files, '参照を調べる文書')
  const problems = checkReferences(files, (file) => readFileSync(file, 'utf8'), existsSync)
  for (const problem of problems) console.log(problem)
  console.log(`切れた参照：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
