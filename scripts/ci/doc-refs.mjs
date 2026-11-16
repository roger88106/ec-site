// 文書への参照が切れていないことを確認する。
//   - Markdown のリンク：先のファイルと #見出し があるか（見出しの slug は GitHub と同じ作り方）
//   - Markdown のリンクテキスト：# の付かない、docs/ の下の文書へのリンクで、リンクテキストが文書の名前か（略した名前を見つけるため）。許可する形は isAllowedLinkLabel
//   - 見出しの参照（`[起票と記録の操作](../../docs/05-開発規則/issues.md)の「起票」`、`起票と記録の操作の「起票」` など）：リンクか文書の名前の直後の「の「…」」と、
//     「の」を挟まずにパスかリンクの直後に書いた「…」（`docs/03-機能仕様/order.md「支払」`。文書の名前の直後と、空白や閉じ括弧を挟んだものは読まない）だけを読み、
//     先の文書に、その見出し（番号を除く）が 1 つだけあるか。同じ見出しが 2 つ以上あれば、親の見出しを前に付けた形（`の「段階 1」の「タスク」`）で 1 つに決める。
//     見出しは、リンクを表示の文字に、コードの印を除き、全角と半角を揃え、空白をまとめて比べる。末尾の全角の括弧の補足（`（DS-s-n：DC-s-n〜m）` など）は、
//     除いた形でも全て書いた形でも当たる（除くと 2 つ以上に当たるなら、1 つに決まらないとする）。Markdown のコードブロックの中は見ない
//   - 節の番号の参照（番号と「節」。前の「第」はあってもなくてもよい）：失敗にし、見出しの形に書き換えるよう示す。節の番号は、節を加える・移すたびに変わり、意味の違う節を指したまま通るため。
//     番号の間の区切りは「・」「、」「〜」「～」「~」「,」「，」「-」「－」「と」「から」で、空白は半角・全角のどちらでもよい（`第　3　節`、`第 4～6 節`、`第 3 と 4 節`）。
//     Markdown は、インラインコードとコードブロックの外の全て（名前のないもの、spec・plan の節も）。コードは、文書の名前・リンク・パスの直後（間の「の」は許す）のものだけ。
//     指す先の文書に、その番号の見出しがあれば、失敗のメッセージにその見出しを示す。名前のない番号は、Markdown では、直前が行の始め・文の切れ目・表の区切り・
//     リストの印・`この文書の` のときだけ、その文書自身を指すとして見出しを求める。それ以外の語が直前にあれば、他の文書の節かもしれないので見出しを示さず、先の文書を書くよう示す
//   - 見出しの番号（`1.`、`1.2`、`1.2.` の形。全角の数字と点、点の後に空白のない `1.概要`、行の始めの 3 個までの空白も）：docs/08-spec/ の外の Markdown で失敗にする（文書の構成の「参照」）。
//     コードブロックの中は見ない。誤りを防ぐ検査なので、意図して番号を避けた書き方（`1) ` など）は対象にしない。`1.5 倍の負荷` は形で番号と区別できず失敗になるので、数字を名前の先頭に置かない
//
// 参照の先の文書は、直前のリンク、文書の名前、パスで決める（読み方は lib/doc-names.mjs）。
// 見出しの参照と節の番号の参照は、Markdown の他に、次のコードと設定のファイルも見る。
//   - JVM：.java、.kts、.properties、.xml
//   - TypeScript と JavaScript：.ts、.tsx、.mjs、.cjs、.js
//   - 設定：.yml、.yaml、.toml、.json、.jsonc
//   - script：.sh、.ps1
//   - その他：.grit
// コメントを切り出さず、文字列も含めてファイルの全体を読む。
// アーカイブした spec（当時の記録で、番号が変わらない）と、適用済みのマイグレーション（直せない）は見ない。
// ただし、アーカイブの下の MVP の完成までの一覧（mvp.md）は spec の記録でなく一覧の文書で、spec を移すたびにリンクが切れるので見る。
// この検査のテストと ja-text のテストも見ない。検査の例として、切れた参照と `第 N 節` をわざと書くため。
//
// 失敗したとき：リンクか見出しの参照を修正する（skill `docs`）。見出しの番号は外す。
// 除外：なし
// 使い方：node scripts/ci/doc-refs.mjs（リポジトリのルートで）
import { existsSync, readFileSync } from 'node:fs'
import { basename, dirname, join, normalize } from 'node:path/posix'
import { codeBlockLines } from './lib/code-block.mjs'
import { maskCodeSpans } from './lib/code-span.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { documentIndex, headingKey, readHeadings, referenceName, resolveDocument } from './lib/doc-names.mjs'
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

/** 節の番号（`4`、`4.2`。全角の数字と点も） */
const SECTION_DIGITS = '[0-9０-９]+(?:[.．][0-9０-９]+)*'

/** 節の番号の間の、範囲と並びの区切り（・、〜 ~ ～ , ， - －、「と」「から」）。前後の半角・全角の空白は許す */
const SECTION_SEPARATOR = '[ 　]*(?:[・、〜~～,，\\-－‐–—−]|と|から)[ 　]*'

/**
 * 節の番号の参照（「第 4.2、5.6 節」「第 4〜6・8 節」「第 3 と 4 節」など）。空白のない「第2節」、全角の数字と空白の「第　２　節」、
 * 「第」のない「4.2 節」も。番号の直前が英数字か点（`v1.2 節`）のものは除く
 */
const SECTION_NUMBER = new RegExp(`(?:第[ 　]*)?(?<![0-9０-９.．\\w])(${SECTION_DIGITS}(?:${SECTION_SEPARATOR}${SECTION_DIGITS})*)[ 　]*節`, 'gu')

/**
 * 名前のない節の番号を、その文書自身の節とする直前の形（行の始め、文の切れ目、表の区切り、リストの印、`この文書の`・`本書の`。その後の開き括弧は許す）。
 * これ以外の語が直前にあれば、他の文書の名前かもしれないので、先の文書を決めない
 */
const SELF_BEFORE = /(?:^|[。．：:|]|^\s*(?:[-*+]|\d+\.)|この文書の?|本書の?)\s*[（(]?$/

/**
 * 「の」のない見出しの参照（`order.md「支払」`）の直前の形。パス（`.md` の後のバッククォートは許す）か、Markdown のリンクの直後だけ。
 * 文書の名前の直後（`用語集「写し」`）、空白・閉じ括弧・「」」を挟んだ形は、語の引用と見分けられないので読まない
 */
const NO_PARTICLE_BEFORE = /(?:\.md`?|\]\([^)\s]+\))$/

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

/** spec の記録（spec・plan・tasks の文書）。番号や役割（`002`、`spec <番号>`、`レビューの結果`）でリンクするので、リンクテキストを見ない */
const SPEC_RECORD = /^docs\/08-spec\/(?:.+\/)?(?:(?:spec|plan)\.md|tasks\/[^/]+\.md)$/

/**
 * リンクテキストが、許可する形か。許可する形は次のどれか。
 *   - 文書の名前（リンクテキストと 1 行目の見出しの両方から、全角と半角の括弧の補足を除き、headingKey の形で比べる）
 *   - リンク先のファイル名かパス（バッククォートで囲んでもよい。パスは、リンクのあるファイルからの相対か、ルートからで、リンク先と同じファイルを指すもの）。
 *     見出しの参照（直後の「の「…」」）の形では許可しない（参照の形は文書の名前で書くため）
 *   - リンク先が constitution で、リンクテキストが `constitution`（resolveEnd が constitution の名前として読む。大文字と小文字は区別しない）
 *   - リンク先が spec の記録（リンクテキストは見ない。番号の取り違えは見つけない）
 *
 * @param {string} file リンクのあるファイル
 * @param {{ label: string, target: string, headingRef: boolean }} ref リンク（label はリンクテキスト）
 * @param {string} title リンク先の文書の 1 行目の見出し
 * @returns {boolean} 許可する形なら true
 */
export function isAllowedLinkLabel(file, ref, title) {
  const nameKey = (text) => headingKey(referenceName(text).replace(/\([^()]*\)/g, ''))
  if (nameKey(ref.label) === nameKey(title)) return true
  if (SPEC_RECORD.test(ref.target)) return true
  const plain = ref.label.replace(/`/g, '').trim()
  if (ref.target === 'docs/constitution.md' && plain.toLowerCase() === 'constitution') return true
  if (ref.headingRef || !plain.endsWith('.md')) return false
  return plain === basename(ref.target) || [normalize(join(dirname(file), plain)), normalize(plain)].includes(ref.target)
}

/**
 * 「第 4〜6・8 節」の番号の部分を、確認する番号に分ける（範囲は両端だけ。全角の数字・空白・記号は半角に揃える）。
 *
 * @param {string} numbers 番号の部分（`4〜6・8`、`3 と 4` など。区切りは SECTION_SEPARATOR）
 * @returns {string[]} 節の番号
 */
export function splitSectionNumbers(numbers) {
  return numbers
    .normalize('NFKC')
    .split(/[・、〜~,\-‐–—−]|と|から/)
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
  const codeLines = isMarkdown ? codeBlockLines(text) : []
  lines.forEach((line, index) => {
    const inCode = Boolean(codeLines[index])
    const lineNumber = index + 1
    if (isMarkdown && !inCode) {
      // 宛先の後の title（`[x](a.md "説明")`）も読む。参照式のリンク（`[x][ref]` と `[ref]: a.md`）は読まないので、文書では使わない
      for (const match of line.matchAll(/(?:\[([^[\]]*))?\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
        const [whole, label, link] = match
        if (/^(https?:|mailto:)/.test(link)) continue
        const [path, anchor] = link.split('#')
        const target = path ? normalize(join(dirname(file), decodeURIComponent(path))) : file
        const ref = { line: lineNumber, target, anchor: anchor ? decodeURIComponent(anchor) : undefined, text: link }
        // 画像（`![…](…)`）の文字は、リンクテキストとして見ない
        if (label !== undefined && line[match.index - 1] !== '!') {
          // 見出しの参照の読み方（resolveDocument）と同じく、「の「」の前には間の空白と、閉じる印（`` ` ``、「」」、閉じ括弧）を挟んでもよい。「の」のない「」は直後だけ
          Object.assign(ref, { label, headingRef: /^(?:[`」）)]*\s*の「|「)/.test(line.slice(match.index + whole.length)) })
        }
        refs.push(ref)
      }
    }
    if (!(isMarkdown && inCode)) {
      // 「」の後に「の「」が続くものは、親から順の見出しとして 1 つの参照にする。
      // 最初の「」は、「の」を挟むか、パスかリンクの直後（NO_PARTICLE_BEFORE）に書いたものだけを読む。読まないときは、続く「の「」から読む
      for (const match of line.matchAll(/(\s?の)?(「[^「」\n]*」)(?:\s?の「[^「」\n]*」)*/g)) {
        let start = match.index
        let text = match[0]
        if (!match[1] && !NO_PARTICLE_BEFORE.test(line.slice(0, start))) {
          const first = match[2]
          start += first.length
          text = text.slice(first.length)
          if (!text) continue
        }
        const target = resolveDocument(file, line.slice(0, start), names, basenames, exists, true)
        if (!target) continue
        const path = [...text.matchAll(/「([^「」]*)」/g)].map((quote) => quote[1])
        refs.push({ line: lineNumber, target, headings: path, text: text.trim() })
      }
    }
    if (isMarkdown && inCode) return
    // Markdown はインラインコードの外の全てを、コードは指す先の文書が決まるものだけを、節の番号の参照にする
    for (const match of (isMarkdown ? maskCodeSpans(line) : line).matchAll(SECTION_NUMBER)) {
      const before = line.slice(0, match.index).trimEnd()
      // spec・plan の節（`plan` の後に番号が続く形と、`spec` の後に番号と `の` が続く形）は、指す先の文書を名前で決められない
      const spec = /(spec|plan|\d{3})(\s*の)?$/.test(before.replace(/[`」]+$/, '').trimEnd())
      const found = spec ? null : resolveDocument(file, before, names, basenames, exists)
      if (!found && !isMarkdown) continue
      // 名前のない番号は、Markdown では、直前に名前の候補になる語がないときだけ、その文書自身を指すとする。
      // 語があれば（`環境変数の文書の第 2 節`、`（第 4 節）` の前の語、`、` の後など）、他の文書の節かもしれないので先を決めない
      const self = !found && !spec && SELF_BEFORE.test(before)
      const unknown = !found && !spec && !self
      const target = found ?? (self ? file : null)
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
      } else if (ref.label !== undefined && !ref.anchor) {
        // # の付くリンクは見出しを指すので見ない。docs/ の下の文書（1 行目に見出しがあるもの）へのリンクだけ、リンクテキストを見る
        const title = ref.target.startsWith('docs/') && ref.target.endsWith('.md') ? headingsOf(ref.target).title : null
        if (title && !isAllowedLinkLabel(file, ref, title)) {
          problems.push(`リンクテキスト「${ref.label}」が文書の名前でない（${ref.target} の名前は「${referenceName(title)}」）：${where}`)
        }
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

/**
 * 番号のある見出しの行（NFKC で揃えた後の形）。`1.5倍` と `1.x` は、点の直後が英数字なので番号としない。
 * 参照の読み方（HEADING_NUMBER）より広い。参照は見出しの名前を正しく取り出す必要があり、この検査は、誤って付けた番号を広く見つけるため
 */
const NUMBERED_HEADING = /^ {0,3}#{1,6}[ \t]+(?:\d+(?:\.\d+)*\.(?![\da-zA-Z])|\d+(?:\.\d+)+(?:\s|$))/

/**
 * docs/08-spec/ の外の Markdown で、番号のある見出しを探す。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string} read ファイルを読む関数
 * @returns {string[]} 番号のある見出しの説明
 */
export function findNumberedHeadings(files, read) {
  const problems = []
  for (const file of files) {
    if (!file.endsWith('.md') || file.startsWith('docs/08-spec/')) continue
    const text = read(file)
    const inCode = codeBlockLines(text)
    text
      .split(/\r?\n/)
      .forEach((line, index) => {
        if (!inCode[index] && NUMBERED_HEADING.test(line.normalize('NFKC'))) problems.push(`見出しに番号がある。番号を外す：${file}:${index + 1} ${line.trimEnd()}`)
      })
  }
  return problems
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => isTargetFile(file) && !isExcluded(file))
  exitIfNoFiles(files, '参照を調べる文書')
  const read = (file) => readFileSync(file, 'utf8')
  const problems = [...checkReferences(files, read, existsSync), ...findNumberedHeadings(files, read)]
  for (const problem of problems) console.log(problem)
  console.log(`切れた参照と番号のある見出し：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
