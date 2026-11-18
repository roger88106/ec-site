// 文書の名前と見出しを読み、参照の先の文書を決める。
//
// 文書の名前は、docs/ の下の各文書の 1 行目の見出しから括弧（`（frontend/store）` など）を除いたものと、skill の `skill `<名前>``（.claude/skills/<名前>/SKILL.md を指す）。
// `skill `<名前>` の <ファイル名>`（`skill `review` の prompts.md`）は、その skill のフォルダーのファイルを指す。リポジトリにない skill の参照は見ない。
//
// 参照の先は、直前のリンク、文書の名前、パス（`docs/06-手順書/ci.md`、`notification.md` など）で決める。
// 直前の印（`` ` ``、「」」、「』」、太字の `**` と `__`、「の」）と、名前と番号の間の開き括弧（`開発の進め方（` の後の番号）と、閉じ括弧（`（docs/06-手順書/ci.md）の`）と、末尾の括弧の補足（`店舗画面の構成（補足）の`）は除いて読む。
import { existsSync } from 'node:fs'
import { basename, dirname, join, normalize } from 'node:path/posix'
import { readHeadings } from '../../../shared/docs/doc-structure.mjs'

/** 見出しの先頭の番号（`1.` か `4.1` の形。`### 1 通だけ送る` の 1 は見出しの一部なので除かない）。docs/08-spec/ の見出しを指す参照で除く */
const HEADING_NUMBER = /^(\d+(?:\.\d+)*\.|\d+(?:\.\d+)+)\s+/

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
 * @returns {{ title: string | null, anchors: Set<string>, sections: Map<string, string>, headings: { exact: string, keys: Set<string>, ancestors: Set<string>[] }[] }} 見出しの読み取りの結果
 *   - title：1 行目の見出し
 *   - anchors：slug
 *   - sections：節の番号から、番号を除いた見出しへの対応
 *   - headings：番号を除いた見出し。exact は比べる形、keys はそれと末尾の括弧の補足を除いた形、ancestors は親から順の上の見出しの keys
 */
export function readHeadingIndex(text) {
  const anchors = new Set()
  const sections = new Map()
  const headings = []
  /** 今の見出しの上の見出し（レベル、比べる形） */
  const stack = []
  /** slug ごとの、前に出た回数（2 回目からの slug に `-1`、`-2` を付ける） */
  const seen = new Map()
  let title = null
  // 見出しの読み方（ATX と Setext、コードブロックと HTML のコメントの外）は、spec-structure と同じ doc-structure の readHeadings
  for (const { level, title: name } of readHeadings(text.replace(/^\uFEFF/, ''))) {
    if (level === 1 && title === null) title = name.trim()
    let key = slug(name)
    if (seen.has(key)) {
      const count = seen.get(key) + 1
      seen.set(key, count)
      key = `${key}-${count}`
    } else seen.set(key, 0)
    anchors.add(key)
    const heading = name.replace(HEADING_NUMBER, '')
    const number = name.match(HEADING_NUMBER)
    if (number) {
      const sectionKey = number[1].replace(/\.$/, '')
      if (!sections.has(sectionKey)) sections.set(sectionKey, heading.trim())
    }
    while (stack.length && stack.at(-1).level >= level) stack.pop()
    // 末尾の全角の括弧の補足（`（DS-s-n：DC-s-n〜m）` など）を除いた形でも当たるようにする
    const exact = headingKey(heading)
    const keys = new Set([exact, headingKey(heading.replace(/（[^（）]*）\s*$/, ''))])
    headings.push({ exact, keys, ancestors: stack.map((item) => item.keys) })
    stack.push({ level, keys })
  }
  return { title, anchors, sections, headings }
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
 * 参照の前の文字から、指す先の文書を決める。見出しの参照と節の番号の参照で同じ読み方にする。
 *
 * 直前のリンク、constitution、パス（`docs/06-手順書/ci.md`、`notification.md`）、文書の名前の順に当てる。
 * 名前の後の「の」、パスや名前の印（`` ` ``、「」」）、閉じ括弧（`（docs/06-手順書/ci.md）の`）は除いて読む。
 * 当たらず、末尾が括弧の補足（`店舗画面の構成（補足）の`）なら、補足の前で読み直す。
 * 見出しの参照では、パスはファイルがあるとき（ファイル名だけなら docs/ の下に同じ名前の文書があるとき）だけ先にする（外のリポジトリのファイルや、コードの中で組み立てるパスを、切れた参照にしないため）。パスの読み方は resolvePath。
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
  // 名前と番号の間の開き括弧（`開発の進め方（` の後の番号）と、名前の後の「の」を除く
  const text = before.trimEnd().replace(/[（(]$/, '').trimEnd().replace(/\s*の$/, '').trimEnd()
  const found = resolveEnd(file, text, names, basenames, exists, existingPath)
  if (found !== undefined) return found
  const withoutNote = text.replace(/（[^（）]*）$/, '').trimEnd()
  if (withoutNote === text) return null
  return resolveEnd(file, withoutNote, names, basenames, exists, existingPath) ?? null
}

/** 名前やパスの後ろの閉じる印（コードの印、「」」と「』」、太字の印）。除いてから名前を比べる */
const CLOSING_MARKS = /(?:[`」』]|\*\*|__)+$/

/**
 * 文書の名前・リンク・パスの直後（間の半角・全角の空白と太字の印は許す）かを調べ、先の文書を決める。
 *
 * 「の」のない「」の前の判定に使う。閉じ括弧・「」」・「』」の後と、「の」の後は、直後とみなさない
 * （`（docs/a.md）「語」`、`「docs/a.md」「語」` は、語の引用との見分けがつかないため。リンクの `)` は除く）。
 *
 * @param {string} file 参照のあるファイル
 * @param {string} before 参照の前の文字
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応（長い名前から順）
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @param {boolean} existingPath パスを、ファイルがあるときだけ先にするか
 * @returns {string | null} 文書のパス。直後でなければ、決まらなければ null
 */
export function resolveAdjacent(file, before, names, basenames, exists = existsSync, existingPath = false) {
  const text = before.replace(/[ 　]+$/, '').replace(/(?:\*\*|__)$/, '')
  if (text.endsWith('の')) return null
  if (/[」』）)]$/.test(text) && !/\]\([^)\s]*\)$/.test(text)) return null
  return resolveEnd(file, text, names, basenames, exists, existingPath) ?? null
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
  const marked = text.replace(CLOSING_MARKS, '').trimEnd()
  for (const candidate of [marked, marked.replace(/[）)]$/, '').replace(CLOSING_MARKS, '').trimEnd()]) {
    const link = candidate.match(/\]\(([^)\s#]*)(#[^)]*)?\)$/)
    if (link) {
      if (/^(https?:|mailto:)/.test(link[1])) return null
      return link[1] ? normalize(join(dirname(file), decodeURIComponent(link[1]))) : file
    }
    // skill のフォルダーのファイル。リポジトリにない skill（外の skill）は先にしない
    const skillPath = candidate.match(SKILL_PATH)
    if (skillPath) return names.has(`skill \`${skillPath[1]}\``) ? `.claude/skills/${skillPath[1]}/${skillPath[2]}` : null
    const path = candidate.match(/([^\s`'"（(「」<>[\]]+\.md)$/)
    if (path) return resolvePath(file, path[1], basenames, exists, existingPath)
    if (/constitution$/.test(candidate)) return 'docs/constitution.md'
    for (const [name, target] of names) {
      // skill の名前は末尾がコードの印なので、印を除く前の文字で比べる
      if (candidate.endsWith(name) || text.endsWith(name)) return target
    }
  }
  return undefined
}

/**
 * 参照に書いたパスから、文書のパスを決める。
 *
 *   - Markdown は参照のあるファイルからの相対を先に（リンクと同じ読み方。`README.md` は隣のファイル）、コードはルートからを先に見る（コメントはルートからのパスで書くため）
 *   - どちらにもなく、ディレクトリのないファイル名だけ（`notification.md`）なら、docs/ の下の同じ名前の文書（1 つだけのとき）にする
 *   - ディレクトリまで書いてファイルがないもの（外のリポジトリの `docs/setup.md` など）は、同じ名前の文書に当てない
 *
 * @param {string} file 参照のあるファイル
 * @param {string} path 参照に書いたパス
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @param {boolean} existingPath パスを、ファイルがあるときだけ先にするか
 * @returns {string | null} 文書のパス。existingPath でファイルがなければ null
 */
function resolvePath(file, path, basenames, exists, existingPath) {
  const fromRoot = normalize(path)
  const relative = normalize(join(dirname(file), path))
  for (const candidate of file.endsWith('.md') ? [relative, fromRoot] : [fromRoot, relative]) {
    if (exists(candidate)) return candidate
  }
  if (!path.includes('/') && basenames.has(path)) return basenames.get(path)
  return existingPath ? null : fromRoot
}

/** skill の本体のファイル（名前は skill のフォルダーの名前） */
const SKILL_FILE = /^\.claude\/skills\/([^/]+)\/SKILL\.md$/

/** skill の名前の後のファイル名（`skill `review` の prompts.md`。ファイル名はコードの印で囲んでもよい。末尾のコードの印は除いてある） */
const SKILL_PATH = /skill\s+`([^`\s]+)`\s*の\s*`?([^\s`'"（(「」<>[\]/]+\.md)$/

/**
 * docs/ の下の文書と skill の、名前とファイル名からパスへの対応を作る。
 *
 * docs/ の下の文書の名前は 1 行目の見出し、skill の名前は `skill `<名前>``（`.claude/skills/<名前>/SKILL.md` を指す）。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string | null} titleOf 文書の 1 行目の見出しを返す関数
 * @returns {{ names: Map<string, string>, basenames: Map<string, string> }} 文書の名前からパスへの対応（長い名前から順）と、docs/ の下のファイル名からパスへの対応（同じ名前が 2 つ以上あるものは除く）
 */
export function documentIndex(files, titleOf) {
  const docs = files.filter((file) => file.startsWith('docs/') && file.endsWith('.md'))
  const names = new Map()
  for (const doc of docs) {
    const title = titleOf(doc)
    if (title) names.set(referenceName(title), doc)
  }
  // skill は、1 行目の見出しでなく `skill `<名前>`` の形で参照する
  for (const file of files) {
    const skill = file.match(SKILL_FILE)
    if (skill) names.set(`skill \`${skill[1]}\``, file)
  }
  const counts = {}
  for (const doc of docs) counts[basename(doc)] = (counts[basename(doc)] ?? 0) + 1
  return {
    // 長い名前から当てる（「フロントの書き方」より先に「テストの書き方」が誤って当たらないよう、名前の長さの順）
    names: new Map([...names].sort((a, b) => b[0].length - a[0].length)),
    basenames: new Map(docs.filter((doc) => counts[basename(doc)] === 1).map((doc) => [basename(doc), doc])),
  }
}
