// 文書への参照が切れていないことを確認する。
//   - Markdown のリンク：先のファイルと #見出し があるか（見出しの slug は GitHub と同じ作り方）
//   - Markdown のリンクテキスト：# の付かない、docs/ の下の文書へのリンクで、リンクテキストが文書の名前か（略した名前を見つけるため）。許可する形は isAllowedLinkLabel
//   - 見出しの参照（`起票と記録の操作の「起票」`、`の節「…」`、`の『…』` など）：先の文書に、その見出しが 1 つだけあるか。
//     同じ見出しが 2 つ以上あれば、親の見出しを前に付けた形（`の「段階 1」の「タスク」`）で 1 つに決める。読む形は scanQuoteRefs、比べ方は countHeadingMatches。
//     見出しの参照の後に `と`・`、`・`・`・`や` か区切りなしで続く「」は、同じ文書の見出しとして確認する。
//     文書の名前・リンクの直後の「」と、パスの後に空白を挟んだ「」は、「の」がなくても、最初の「」が先の文書の見出しに当たるときだけ失敗にし、「の」を入れるよう示す。
//     当たらないものは語の引用（`用語集「写し」`）として通す。空白を挟まないパスの直後の「」は、見出しの参照として読む（なければ失敗。文書の構成の「参照」が許す形）。
//     行が文書の名前・リンク・パス（とその後の「の」）で終わり、次の行（コメントの印を除く）が「」か `の「` で始まるときは、2 行をつないで読む（Markdown は同じ段落の続きだけ）
//   - 節の番号の参照（`第 3 節`、`第三節`、`§3`、`4.2 節` など。形は SECTION_REFERENCE）：失敗にし、見出しの形に書き換えるよう示す。
//     節の番号は、節を加える・移すたびに変わり、意味の違う節を指したまま通るため。指す先に、その番号の見出しがあれば示す。
//     「第」のない `N 節` と `§` は、参照先の文書が直前で決まるとき（文書の名前、パス、リンク、`この文書の`、`本書の`。`N 節` は点を含む番号も）だけ読む（`3 節に分ける`、外の規格の `§3` は通す）
//   - 見出しの番号（`1.`、`1.2` の形。形は NUMBERED_HEADING）：docs/08-spec/ の外の Markdown で失敗にする（文書の構成の「参照」）。
//     誤りを防ぐ検査なので、意図して番号を避けた書き方（`1) ` など）は対象にしない
//   - 存在しないパス：Markdown のコードブロックの外（インラインコードの中を含む）に書いた、`docs/`・`scripts/`・`backend/`・`frontend/`・`e2e/`・`api/openapi/`・`docker/`・`showcase/`・`.claude/`・`.github/` で始まるパスが、
//     Git の一覧（ファイルとフォルダー。大文字と小文字を区別する）にないか。形は findMissingPaths
//
// 参照の先の文書は、直前のリンク、文書の名前（太字も）、パスで決める（読み方は lib/docs/doc-names.mjs）。
// 見出しの参照と節の番号の参照は、Markdown の他に、コードと設定のファイル（TEXT_FILE と、拡張子がなく 1 行目が `#!` のファイル）も、文字列を含めてファイルの全体を読む。
// コードでは、文書の名前・リンク・パスの直後の節の番号だけを読む。Markdown のコードブロックとインラインコードの中は見ない。
// 存在しないパスは Markdown だけを読む（コードとコメントの例のパスが多く、誤検出になるため）。
// 見ないファイル（EXCLUDED）は、アーカイブした spec（当時の記録）、適用済みのマイグレーション（直せない）、切れた参照をわざと書くテスト。
// 存在しないパスの検査だけ、当時のパスを書く記録（docs/07-記録/、skill review の evals）と、まだないファイルを書く進行中の spec（docs/08-spec/）を除く（PATH_CHECK_EXEMPT）。
// 追跡しないが参照してよい場所（`.claude/settings.local.json`、`.claude/worktrees/`、`.local/`、`docker/.env`、`build/` などを含むパス）と、
// プレースホルダー・glob・`…` を含むパスは見ない。
//
// 失敗したとき：リンクか見出しの参照を修正する（skill `docs`）。見出しの参照は文書の名前と見出しの間に「の」を入れる。見出しの番号は外す。`1.5 倍の負荷` のように数字を名前の先頭に置かない。
// 存在しないパスは、綴りと大文字と小文字を直す。まだないファイルの名前なら、パスの形（インラインコード）を避けるか、ファイルを作ってから書く。
//
// 除外：なし
//
// 使い方：node scripts/ci/docs/doc-refs.mjs（リポジトリのルートで）
import { existsSync, readFileSync } from 'node:fs'
import { basename, dirname, join, normalize } from 'node:path/posix'
import { codeBlockLines } from '../../shared/docs/code-block.mjs'
import { maskCodeSpans, readCodeSpans } from '../../shared/source/code-span.mjs'
import { exitIfNoFiles, listGitFiles } from '../../shared/git/git-files.mjs'
import { documentIndex, headingKey, readHeadingIndex, referenceName, resolveAdjacent, resolveDocument } from '../lib/docs/doc-names.mjs'

/**
 * 見ないファイル（当時の記録、直せないもの、この検査と ja-text のテストの例）。
 *
 * アーカイブの下の mvp.md は spec の記録でなく一覧の文書で、spec を移すたびにリンクが切れるので見る
 */
const EXCLUDED = [
  /^docs\/08-spec\/archive\/(?!mvp\/mvp\.md$)/,
  /\/db\/migration\//,
  /^scripts\/ci\/(docs\/doc-refs|ja\/ja-text)\.test\.mjs$/,
]

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

/**
 * 拡張子がなく、1 行目が `#!` で始まるファイルか（`scripts/dev/hooks/pre-commit` など。コードとして読む）。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {(file: string) => string} read ファイルを読む関数
 * @returns {boolean} そうなら true
 */
function isShebangScript(file, read) {
  if (basename(file).includes('.')) return false
  try {
    return read(file).startsWith('#!')
  } catch {
    return false
  }
}

/**
 * 参照を探すファイルを選ぶ。拡張子の一覧（TEXT_FILE）に当たるファイルと、拡張子がなく 1 行目が `#!` のファイルから、見ないファイルを除く。
 *
 * @param {string[]} files Git の対象のファイル
 * @param {(file: string) => string} read ファイルを読む関数
 * @returns {string[]} 参照を探すファイル（入力の順）
 */
export function selectTargetFiles(files, read) {
  return files.filter((file) => !isExcluded(file) && (isTargetFile(file) || isShebangScript(file, read)))
}

/** 節の番号（`4`、`4.2`。全角の数字と点も） */
const SECTION_DIGITS = '[0-9０-９]+(?:[.．][0-9０-９]+)*'

/** 漢数字の節の番号（`三`、`十二`）。「第」のある形だけで読む */
const SECTION_KANJI = '[〇一二三四五六七八九十百]+'

/** 節の番号の間の、範囲と並びの区切り（・、〜 ~ ～ , ， - －、「と」「から」）。前後の半角・全角の空白は許す */
const SECTION_SEPARATOR = '[ 　]*(?:[・、〜~～,，\\-－‐–—−]|と|から)[ 　]*'

/**
 * 節の番号の参照。3 つの形があり、どれに当たったかは名前付きの括弧（dai、plain、sign）で分かる。
 *
 *   - dai：「第 4.2、5.6 節」「第 4〜6・8 節」「第 3 と 4 節」「第三節」「第三・四節」など。空白のない「第2節」、全角の数字と空白の「第　２　節」も。漢数字は「第」のある形だけ
 *   - plain：「第」のない「4.2 節」。番号の直前が英数字か点（`v1.2 節`）のものは除く
 *   - sign：「§3」「§ 3」「§§3-4」「§3〜5」（全角の数字も）
 */
const SECTION_REFERENCE = new RegExp(
  `第[ 　]*(?<dai>(?:${SECTION_DIGITS}|${SECTION_KANJI})(?:${SECTION_SEPARATOR}(?:${SECTION_DIGITS}|${SECTION_KANJI}))*)[ 　]*節` +
    `|(?<![0-9０-９.．\\w])(?<plain>${SECTION_DIGITS}(?:${SECTION_SEPARATOR}${SECTION_DIGITS})*)[ 　]*節` +
    `|§{1,2}[ 　]*(?<sign>${SECTION_DIGITS}(?:${SECTION_SEPARATOR}${SECTION_DIGITS})*)`,
  'gu',
)

/**
 * 名前のない節の番号を、その文書自身の節とする直前の形（行の始め、文の切れ目、表の区切り、リストの印、`この文書の`・`本書の`。その後の開き括弧は許す）。
 *
 * これ以外の語が直前にあれば、他の文書の名前かもしれないので、先の文書を決めない
 */
const SELF_BEFORE = /(?:^|[。．：:|]|^\s*(?:[-*+]|\d+\.)|この文書の?|本書の?)\s*[（(]?$/

/** 「この文書の」「本書の」の直後（名前のない `N 節` と `§` を、その文書自身の節として読む形） */
const THIS_DOCUMENT_BEFORE = /(?:この文書の?|本書の?)\s*[（(]?$/

/** 見出しの参照の「」（「…」か『…』。『』の中に文字列の連結・埋め込み（`"`、`+`、`${`）を含むものは、コードの文字列なので読まない） */
const QUOTE = '(?:「[^「」\\n]*」|『(?:(?!\\$\\{)[^『』\\n"+])*』)'

/** 「の」でつながる「」の並び（`の「段階 1」の「タスク」`。親から順の 1 つの見出しの参照） */
const PARTICLE_CHAIN = `(?:\\s?の${QUOTE})*`

/** 見出しの参照の最初の「」。直前の「の」（`の節` も）は、あれば読む */
const QUOTE_HEAD = `(\\s?の節?)?(${QUOTE})(${PARTICLE_CHAIN})`

/** 見出しの参照の後に続く、同じ文書の見出し（`と`・`、`・`・`・`や` か区切りなし） */
const QUOTE_NEXT = new RegExp(`^(?:と|、|・|や)?(${QUOTE}${PARTICLE_CHAIN})`, 'u')

/**
 * 漢数字（`三`、`十二`、`二十三`、`百`）を、算用数字にする。
 *
 * @param {string} kanji 漢数字
 * @returns {string} 算用数字
 */
function kanjiToNumber(kanji) {
  const digit = (char) => '〇一二三四五六七八九'.indexOf(char)
  if (!/[十百]/.test(kanji)) return [...kanji].map(digit).join('')
  let total = 0
  let current = 0
  for (const char of kanji) {
    if (char === '百') {
      total += (current || 1) * 100
      current = 0
    } else if (char === '十') {
      total += (current || 1) * 10
      current = 0
    } else current = digit(char)
  }
  return String(total + current)
}

/**
 * 見出しの参照に当たる見出しの数を数える。親の見出しは、上の見出しに同じ順で含まれれば（子孫でよい）当たる。
 *
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
 * リンクテキストが、許可する形か。
 *
 * 許可するのは、文書の名前、リンク先のファイル名かパス、constitution、spec の記録へのリンク（リンクテキストを見ない）。
 *
 * @param {string} file リンクのあるファイル
 * @param {{ label: string, target: string, headingRef: boolean }} ref リンク（label はリンクテキスト）
 * @param {string} title リンク先の文書の 1 行目の見出し
 * @returns {boolean} 許可する形なら true
 */
function isAllowedLinkLabel(file, ref, title) {
  // 文書の名前は、両方から全角と半角の括弧の補足を除いて比べる
  const nameKey = (text) => headingKey(referenceName(text).replace(/\([^()]*\)/g, ''))
  if (nameKey(ref.label) === nameKey(title)) return true
  if (SPEC_RECORD.test(ref.target)) return true
  const plain = ref.label.replace(/`/g, '').trim()
  // resolveEnd が constitution の名前として読む（大文字と小文字は区別しない）
  if (ref.target === 'docs/constitution.md' && plain.toLowerCase() === 'constitution') return true
  // ファイル名かパス（相対かルートから）。見出しの参照の形では許可しない（参照は文書の名前で書くため）
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
    .map((number) => (/^[〇一二三四五六七八九十百]+$/.test(number) ? kanjiToNumber(number) : number))
}

/**
 * 行の中の見出しの参照（「の」「の節」の後と、文書の名前・リンク・パスの直後の「」。続く「の「」と、と・、・・や か区切りなしで続く「」も）を集める。
 *
 * 最初の「」に「の」がないとき、直前が文書の名前・リンク・パスなら（空白を挟んでもよい）見出しの参照（noParticle）とする。
 * それ以外は、最初の「」は読まず、続く「の「」から読む。
 *
 * @param {string} file 参照のあるファイル
 * @param {string} line 1 行（2 行をつないだものでもよい）
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @param {number} [minEnd] 参照の終わりがこの位置より後のものだけを返す（2 行をつないだとき、前の行だけで読める参照を重ねないため）
 * @returns {{ target: string, headings: string[], text: string, noParticle: boolean }[]} 参照（headings は「」の中、親から順）
 */
function scanQuoteRefs(file, line, names, basenames, exists, minEnd = 0) {
  const refs = []
  const head = new RegExp(QUOTE_HEAD, 'gu')
  const quotesOf = (text) => [...text.matchAll(/[「『]([^「」『』]*)[」』]/g)].map((quote) => quote[1])
  let match
  while ((match = head.exec(line)) !== null) {
    const [whole, particle, first] = match
    let start = match.index
    let text = whole
    let target = null
    let noParticle = false
    if (particle) {
      target = resolveDocument(file, line.slice(0, start), names, basenames, exists, true)
    } else {
      if (first.startsWith('「')) target = resolveAdjacent(file, line.slice(0, start), names, basenames, exists, true)
      // 空白を挟まないパスの直後の「」は、「の」のない見出しの参照として読む（文書の構成の「参照」が許す形。見出しがなければ失敗）
      // （行をまたいでつないだ形は、行の境目を空白とみなす）
      if (target) noParticle = !(/\.md`?$/.test(line.slice(0, start)) && !(minEnd > 0 && start === minEnd))
      else {
        start += first.length
        text = whole.slice(first.length)
        if (text) target = resolveDocument(file, line.slice(0, start), names, basenames, exists, true)
      }
    }
    let end = match.index + whole.length
    if (!target) continue
    const items = [{ text: text.trim(), end, noParticle }]
    for (let next = QUOTE_NEXT.exec(line.slice(end)); next !== null; next = QUOTE_NEXT.exec(line.slice(end))) {
      end += next[0].length
      items.push({ text: next[1], end, noParticle: false })
    }
    head.lastIndex = end
    for (const item of items) {
      if (item.end > minEnd) refs.push({ target, headings: quotesOf(item.text), text: item.text, noParticle: item.noParticle })
    }
  }
  return refs
}

/**
 * 行と、次の行をつないだ文字を返す。つなぐのは、行が文書の名前・リンク・パス（とその後の「の」）で終わり、次の行が「」か の「 で始まるときだけ。
 *
 * コードでは、次の行のコメントの印（`//`、`#`、`*`、`--`）と前の空白を除く。
 * Markdown は、同じ段落の続きの行だけつなぐ（見出し、表、リストの項目の始め、引用、コードブロックの境目、空行ではつながない）。
 *
 * @param {string} file 参照のあるファイル
 * @param {boolean} isMarkdown Markdown か
 * @param {string[]} lines ファイルの行
 * @param {boolean[]} codeLines 各行がコードブロックの中か
 * @param {number} index 行の番号（0 から）
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @returns {{ text: string, length: number } | null} つないだ文字と、前の行の長さ。つながないなら null
 */
function joinWithNextLine(file, isMarkdown, lines, codeLines, index, names, basenames, exists) {
  if (index + 1 >= lines.length) return null
  const current = lines[index].trimEnd()
  let next = lines[index + 1]
  if (isMarkdown) {
    if (codeLines[index] || codeLines[index + 1] || !current) return null
    if (/^ {0,3}#{1,6}(?:\s|$)/.test(current) || /^\s*\|/.test(current)) return null
    if (/^\s*(?:$|#{1,6}(?:\s|$)|[-*+]\s|\d+[.)]\s|\||>|```|~~~)/.test(next)) return null
    next = next.trim()
  } else {
    next = next.replace(/^\s*(?:\/\/+|#+|\*+|--)\s*/, '')
  }
  if (!/^(?:の「|「)/.test(next)) return null
  if (!resolveAdjacent(file, current.replace(/の$/, ''), names, basenames, exists, true)) return null
  return { text: current + next, length: current.length }
}

/**
 * 1 つのファイルの中の参照を集める。
 *
 * @param {string} file ファイルのパス（リポジトリのルートから）
 * @param {string} text ファイルの中身
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応
 * @param {Map<string, string>} basenames ファイル名（`admin.md` など）からファイルのパスへの対応（同じ名前が 2 つ以上あるものは除く）
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @returns {{ line: number, target: string | null, anchor?: string, headings?: string[], sections?: string[], unknown?: boolean, text: string }[]} 参照
 *   - target：先のファイル。Markdown の節の番号の参照で先が決まらなければ null
 *   - anchor：リンクの #
 *   - headings：見出しの参照の「」の中（親から順）
 *   - sections：節の番号の参照の番号
 *   - unknown：直前が「（」などで、先の文書が決まらない
 */
function findReferences(file, text, names, basenames, exists = existsSync) {
  const refs = []
  const isMarkdown = file.endsWith('.md')
  const lines = text.split(/\r?\n/)
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
      // 「」の後に「の「」が続くものは、親から順の見出しとして 1 つの参照にする（読み方は scanQuoteRefs）
      for (const ref of scanQuoteRefs(file, line, names, basenames, exists)) refs.push({ line: lineNumber, ...ref })
      const joined = joinWithNextLine(file, isMarkdown, lines, codeLines, index, names, basenames, exists)
      if (joined) {
        for (const ref of scanQuoteRefs(file, joined.text, names, basenames, exists, joined.length)) refs.push({ line: lineNumber, ...ref })
      }
    }
    if (isMarkdown && inCode) return
    // Markdown はインラインコードの外の全てを、コードは指す先の文書が決まるものだけを、節の番号の参照にする
    for (const match of (isMarkdown ? maskCodeSpans(line) : line).matchAll(SECTION_REFERENCE)) {
      const before = line.slice(0, match.index).trimEnd()
      const { dai, plain, sign } = match.groups
      // spec・plan の節（`plan` の後に番号が続く形と、`spec` の後に番号と `の` が続く形）は、指す先の文書を名前で決められない
      const spec = /(spec|plan|\d{3})(\s*の)?$/.test(before.replace(/[`」]+$/, '').trimEnd())
      const found = spec ? null : resolveDocument(file, before, names, basenames, exists)
      if (!found && !isMarkdown) continue
      const numbers = dai ?? plain ?? sign
      // 「第」のない `N 節` と `§` は、参照先の文書が直前で決まるとき（名前、パス、リンク、この文書の、本書の）と、点を含む番号の `N 節` だけ読む。
      // 個数（`3 節に分ける`）と外の規格（`RFC 9457 §3.1`）を、節の番号にしないため
      if (dai === undefined && !found && !THIS_DOCUMENT_BEFORE.test(before) && !(plain !== undefined && /[.．]/.test(numbers))) continue
      // 名前のない番号は、Markdown では、直前に名前の候補になる語がないときだけ、その文書自身を指すとする。
      // 語があれば（`環境変数の文書の第 2 節`、`（第 4 節）` の前の語、`、` の後など）、他の文書の節かもしれないので先を決めない
      const self = !found && !spec && SELF_BEFORE.test(before)
      const unknown = !found && !spec && !self
      const target = found ?? (self ? file : null)
      refs.push({ line: lineNumber, target, unknown, sections: splitSectionNumbers(numbers), text: match[0] })
    }
  })
  return refs
}

/** 存在しないパスの検査で、パスの始まりにする語（直前が `/`・英数字でないもの。`api/` は `api/openapi/` だけ） */
const PATH_START = /(?<![\p{L}\p{N}_/])(?:docs|scripts|backend|frontend|e2e|docker|showcase|\.claude|\.github|api\/openapi)\//gu

/** インラインコードの外のパスの終わり（空白、全角の句読点・中黒・括弧・かぎ括弧、半角の括弧・引用符・区切り）。中黒は、`docs/・README・AGENTS.md` のような並べ方があるため */
const PATH_END_OUTSIDE = /^[^\s、。，．：；！？・（）()「」『』【】[\]"'|,;]*/

/** パスの検査で読まない形（プレースホルダー、glob、`…`、`.`が続く省略） */
const PATH_PLACEHOLDER = /[<>*?{}$%[\]…]|\.\.\./

/** 追跡しないが参照してよい場所（パスの始まり。`docker/.env` は、そのファイルだけ） */
const UNTRACKED_PREFIXES = ['.claude/worktrees/', '.local/']

/** 追跡しないが参照してよいファイル */
const UNTRACKED_FILES = ['.claude/settings.local.json', 'docker/.env']

/** 生成物のフォルダー（パスの中にあれば読まない） */
const GENERATED_FOLDERS = ['build/', 'node_modules/', '.next/', 'dist/', 'generated/']

/** 存在しないパスの検査から除く文書（当時のパスを書く記録と、まだないファイルのパスを書く進行中の spec） */
const PATH_CHECK_EXEMPT = [/^docs\/07-記録\//, /^\.claude\/skills\/review\/evals\//, /^docs\/08-spec\//]

/**
 * 文書の 1 つの文字（インラインコードの中か外のどちらか）から、パスの候補を取り出す。
 *
 * @param {string} segment 文字
 * @param {boolean} inCode インラインコードの中か（中は空白か「までをパスにする）
 * @returns {string[]} 確認するパス（行・見出しの指定を除いたもの）
 */
function pathsIn(segment, inCode) {
  const paths = []
  let consumed = 0
  for (const match of segment.matchAll(PATH_START)) {
    if (match.index < consumed) continue
    const raw = segment.slice(match.index).match(inCode ? /^[^\s「]*/ : PATH_END_OUTSIDE)[0]
    consumed = match.index + raw.length
    const path = raw
      .replace(/#.*$/, '')
      .replace(/:\d+(?:-\d+)?$/, '')
      .replace(/(?:\*\*|__)$/, '')
      .replace(/[.,;:!?'"]+$/, '')
    if (PATH_PLACEHOLDER.test(path) || UNTRACKED_FILES.includes(path) || UNTRACKED_PREFIXES.some((prefix) => path.startsWith(prefix))) continue
    const folders = path.endsWith('/') ? path : `${path}/`
    if (GENERATED_FOLDERS.some((folder) => folders.includes(folder))) continue
    paths.push(path)
  }
  return paths
}

/**
 * Markdown の中の、存在しないパスを探す。コードブロックの外を読み、インラインコードの中を含める。リンクの宛先（`](…)`）は、リンクの検査が見るので読まない。
 *
 * @param {string} text Markdown
 * @param {(path: string) => boolean} isKnown Git が追跡するファイルかフォルダーか（大文字と小文字を区別する）
 * @returns {{ line: number, path: string }[]} 存在しないパス
 */
export function findMissingPaths(text, isKnown) {
  const missing = []
  const codeLines = codeBlockLines(text)
  text.split(/\r?\n/).forEach((line, index) => {
    if (codeLines[index]) return
    const masked = line.replace(/\]\([^)]*\)/g, (link) => ' '.repeat(link.length))
    const segments = []
    let last = 0
    for (const { start, end, content } of readCodeSpans(masked).spans) {
      segments.push([masked.slice(last, start), false], [content, true])
      last = end
    }
    segments.push([masked.slice(last), false])
    for (const [segment, inCode] of segments) {
      for (const path of pathsIn(segment, inCode)) {
        if (!isKnown(path)) missing.push({ line: index + 1, path })
      }
    }
  })
  return missing
}

/**
 * 節の番号で参照したときの、失敗のメッセージを作る。指す先の文書に、その番号の見出しがあれば示す。
 *
 * @param {{ target: string | null, sections: string[], unknown?: boolean }} ref 節の番号の参照
 * @param {(file: string) => ReturnType<typeof readHeadingIndex>} headingsOf 文書の見出しを返す関数
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
 * @param {string[]} [tracked] Git が追跡するファイル（存在しないパスの確認に使う。なければ files）
 * @returns {string[]} 切れた参照の説明
 */
export function checkReferences(files, read, exists, tracked = files) {
  const trackedFiles = new Set(tracked)
  const trackedFolders = new Set()
  for (const file of tracked) {
    for (let folder = dirname(file); folder !== '.' && folder !== '/' && !trackedFolders.has(folder); folder = dirname(folder)) trackedFolders.add(folder)
  }
  const isKnown = (path) => (path.endsWith('/') ? trackedFolders.has(path.slice(0, -1)) : trackedFiles.has(path) || trackedFolders.has(path))
  const headings = new Map()
  const headingsOf = (file) => {
    if (!headings.has(file)) headings.set(file, readHeadingIndex(read(file)))
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
        const index = headingsOf(ref.target)
        const quoted = ref.headings.map((heading) => `「${heading}」`).join('の')
        if (ref.noParticle) {
          // 「の」のない「」は、最初の「」が先の文書の見出しに当たるときだけ見出しの参照とする。当たらないものは語の引用
          if (countHeadingMatches(index.headings, ref.headings.slice(0, 1)) === 0) continue
          problems.push(`見出しの参照に「の」がない。<文書の名前>の「<見出し>」の形に書く（${ref.target} の${quoted}）：${where}`)
        }
        const count = countHeadingMatches(index.headings, ref.headings)
        if (count === 0) problems.push(`見出しがない（${ref.target} の${quoted}）：${where}`)
        if (count > 1) problems.push(`見出しが 1 つに決まらない（${ref.target} の${quoted}）。親の見出しを前に付ける：${where}`)
      }
    }
    if (file.endsWith('.md') && !PATH_CHECK_EXEMPT.some((pattern) => pattern.test(file))) {
      for (const { line, path } of findMissingPaths(read(file), isKnown)) {
        problems.push(`存在しないパス（Git が追跡するファイル・フォルダーにない。綴りと大文字小文字を確認する）：${file}:${line} ${path}`)
      }
    }
  }
  return problems
}

/**
 * 番号のある見出しの行（NFKC で揃えた後の形）。`1.5倍` と `1.x` は、点の直後が英数字なので番号としない。
 *
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

if (import.meta.main) {
  const tracked = listGitFiles()
  // docs/ の文書は、見出しを読むときと参照を探すときの 2 回読むので、中身を覚えておく
  const texts = new Map()
  const read = (file) => {
    if (!texts.has(file)) texts.set(file, readFileSync(file, 'utf8'))
    return texts.get(file)
  }
  const files = selectTargetFiles(tracked, read)
  exitIfNoFiles(files, '参照を調べる文書')
  const problems = [...checkReferences(files, read, existsSync, tracked), ...findNumberedHeadings(files, read)]
  for (const problem of problems) console.log(problem)
  console.log(`切れた参照と番号のある見出し：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
