// 日本語の文を確認する。言い換えの一覧は ja-text.json。
//
// error：使わない語。見つけたら失敗にする。
//   使わない語を一部に含む正しい語は、項目の allow に {許容用語: 理由} で書く（その中の一致は数えない）。
//   理由のない許容用語と、どの文書にも使われなくなった許容用語は失敗にする。
//   files（パスの正規表現）を書いた項目は、当たるファイルにだけ当て、コミットメッセージには当てない（例：規則の文書に書く、AI のサービスの名前）。
//   語の検査（error と warning の語、allow の照合）は、インラインコードの中を見ない。許容用語がインラインコードの中にしか出ないときは、使われていないと見る。
//
// warning：文脈で言い換えるかを決める語（「要求」など）と、文体の型。--warning を付けたときだけ一覧にし、失敗にしない（文書を書くときに見る）。
//   既定の実行は warning を数えない（全体の件数が多く、読まれないため）。
//   文体の型と閾値（ja-text.json の style）は findStyleWarnings、文の取り出し方は proseSentences、読点の数え方は countCommas に書いてある。
//
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ と .claude/agents/ の Markdown と、.github/ の yml・yaml と Markdown。
//
// コードも見る：アーカイブの外のコードの、コメントとテスト名（@DisplayName、describe・it・test の名前）だけを調べる。
//   見るコードは、codeKind が読める種類のファイル（lib/source/comment-text）。
//   コメントの外の文字列とコード（画面の文言など）は調べない。取り出し方は lib/source/comment-text。
//   テンプレートで書いたテスト名（it(`…`)）の外側のバッククォートは文字列の区切りで、インラインコードとして隠さない（埋め込みの ${…} は X の 1 字にする）。
// コミットメッセージは、commit の hook が --message で調べる。
//
// 調べない所：アーカイブした spec、評価の一覧（.claude/skills/review/evals/）、語を例に挙げるファイル（ja-text と comment-text のテスト）。
//
// 失敗したとき：error の語を言い換える。warning は --warning で一覧を出力し（終了コードは 0）、示された文を読んで修正するかを決める。
//
// 除外：部分の一致が誤検出になる語は、項目を word の代わりに pattern（正規表現）で書き、examples の hit（当たる例）と miss（当たらない例）を必ず書く。
//   examples がない、正規表現が読めない、例が合わない項目は失敗にする。allow は上の error の段落。
//
// 使い方：node scripts/ci/ja/ja-text.mjs [--warning [--added] | --changed]（リポジトリのルートで）
//   --added：--warning と合わせて、標準入力の差分（git diff --cached -U0）の追加行だけを一覧にする（commit の前の hook）。
//       文は、どれかの行が追加行なら、最初の追加行で示す（--changed と同じ選び方）。
//   --changed：差分の追加行の文で、字数が style.length 以上か読点が style.comma 以上のものを示す。
//       文の字数の合計が style.cell 以上の表のセルも示す。形は GitHub Actions の注釈（`::warning file=…,line=…::`）。
//       警告があっても終了コードは 0。差分の基準は lib/git/merge-base の resolveDiffBase、追加行の決め方は scripts/shared/git/added-diff。
//   node scripts/ci/ja/ja-text.mjs --message <ファイル>：コミットメッセージのファイルを調べる（# で始まる行は除く）。error があれば失敗にする。
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { ADDED_DIFF_ARGS, parseAddedLines } from '../../shared/git/added-diff.mjs'
import { codeBlockLines } from '../lib/docs/code-block.mjs'
import { maskCodeSpans, replaceCodeSpans } from '../lib/source/code-span.mjs'
import { checkExclusions } from '../lib/output/exclusions.mjs'
import { codeKind, commentText, trailingCommentLines } from '../lib/source/comment-text.mjs'
import { exitIfNoFiles, listGitFiles, listUntrackedFiles } from '../../shared/git/git-files.mjs'
import { isTargetDoc } from '../lib/docs/ja-docs.mjs'
import { resolveDiffBase } from '../lib/git/merge-base.mjs'
import { exitIfShallow } from '../lib/git/shallow-clone.mjs'

/**
 * 調べないコード。
 *
 * - 生成したコード、依存のパッケージ、ビルドの出力（書き手が直せない）
 * - アーカイブした spec の下（当時の記録）
 * - 評価の一覧の置き場所（使わない語を例として書く）
 * - 語を例に挙げるテスト（サンプルの文に、使わない語を書く）
 */
const CODE_EXCLUDED = /(^|\/)(node_modules|generated|build|dist|\.next)\/|^docs\/08-spec\/archive\/|^\.claude\/skills\/review\/evals\/|^scripts\/ci\/(ja\/ja-text\.test\.mjs|lib\/source\/comment-text\.test\.mjs)$/

/**
 * インラインコードを、同じ長さの空白に置き換える（語の一致がコードをまたいで作られないように、字数は保つ）。
 *
 * @param {string} text 文書、または行
 * @returns {string} インラインコードを除いたもの
 */
function withoutInlineCode(text) {
  return text.split('\n').map(maskCodeSpans).join('\n')
}

/**
 * 一覧の項目の名前（word、または pattern）を返す。
 *
 * @param {{ word?: string, pattern?: string }} term 項目
 * @returns {string} 項目の名前
 */
function termName(term) {
  return term.word ?? term.pattern
}

/**
 * pattern の項目の正規表現を作る。u フラグ付きで、全ての一致を見る。
 *
 * @param {{ pattern: string }} term pattern の項目
 * @returns {RegExp} 正規表現
 */
function patternRegExp(term) {
  return new RegExp(term.pattern, 'gu')
}

/** 項目ごとの、作っておいた正規表現と許容用語。行と語の組ごとに再作成しないため（項目の確認の後に作る） */
const compiledTerms = new WeakMap()

/**
 * 項目の正規表現（pattern と files）と許容用語を、1 回だけ作って返す。
 *
 * @param {{ pattern?: string, files?: string, allow?: Record<string, string> }} term 項目
 * @returns {{ regExp: RegExp | null, files: RegExp | null, allows: string[] }} 正規表現（なければ null）と許容用語
 */
function compiled(term) {
  let entry = compiledTerms.get(term)
  if (entry === undefined) {
    entry = {
      regExp: term.pattern === undefined ? null : patternRegExp(term),
      files: term.files === undefined ? null : new RegExp(term.files, 'u'),
      allows: Object.keys(term.allow ?? {}),
    }
    compiledTerms.set(term, entry)
  }
  return entry
}

/**
 * インラインコードを除いた行の中に、使わない語を、許容用語の外で書いているかを返す。
 *
 * 日本語に語の境界はないので、使わない語を一部に含む正しい語は、語ごとの許容用語（allow）で救う。
 * word の項目は部分の一致で、pattern の項目は正規表現の一致で見る（漢字の後を除くなどは pattern で書く）。
 *
 * @param {string} line インラインコードを除いた行（maskCodeSpans の結果）
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string> }} term 使わない語（または正規表現）と許容用語（許容用語と理由）
 * @returns {string | null} 許容用語の外にある最初の一致（当たった文字列）。なければ null
 */
function disallowedMatch(line, term) {
  const { regExp, allows } = compiled(term)
  const matches = []
  if (regExp !== null) {
    for (const match of line.matchAll(regExp)) matches.push({ at: match.index, text: match[0] })
  } else {
    for (let at = line.indexOf(term.word); at !== -1; at = line.indexOf(term.word, at + 1)) {
      matches.push({ at, text: term.word })
    }
  }
  // 大半の行は一致がないので、許容用語の範囲は一致があるときだけ求める
  if (matches.length === 0) return null
  const allowed = []
  for (const allow of allows) {
    for (let at = line.indexOf(allow); at !== -1; at = line.indexOf(allow, at + 1)) {
      allowed.push([at, at + allow.length])
    }
  }
  for (const { at, text } of matches) {
    const end = at + text.length
    if (!allowed.some(([from, to]) => from <= at && end <= to)) return text
  }
  return null
}

/**
 * pattern の項目の誤りを、表示する文にして返す。
 *
 * examples（hit：当たる例、miss：当たらない例。どちらも 1 つ以上）が必須で、正規表現が読めること、hit が全て当たり miss が当たらないことを確認する。
 *
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string>, examples?: { hit?: string[], miss?: string[] } }[]} terms 一覧の項目（word の項目は見ない）
 * @returns {string[]} 誤りの文
 */
export function findPatternProblems(terms) {
  const problems = []
  for (const term of terms) {
    if (term.pattern === undefined) continue
    const name = `ja-text.json の pattern「${term.pattern}」`
    if (term.word !== undefined) {
      problems.push(`${name}：word と pattern を両方書かない`)
      continue
    }
    try {
      patternRegExp(term)
    } catch (e) {
      problems.push(`${name}：正規表現として不正：${e.message}`)
      continue
    }
    const { hit, miss } = term.examples ?? {}
    if (!Array.isArray(hit) || hit.length === 0 || !Array.isArray(miss) || miss.length === 0) {
      problems.push(`${name}：examples の hit と miss（どちらも 1 つ以上の配列）が必要`)
      continue
    }
    for (const example of hit) {
      if (disallowedMatch(maskCodeSpans(example), term) === null) problems.push(`${name}：hit の例「${example}」に当たらない`)
    }
    for (const example of miss) {
      if (disallowedMatch(maskCodeSpans(example), term) !== null) problems.push(`${name}：miss の例「${example}」に当たる`)
    }
  }
  return problems
}

/**
 * 一覧の設定の誤り（pattern の誤り、許容用語の形の誤り、許容用語が使わない語を含まない誤り）を、例外にする。
 *
 * 一覧を読んだ後に 1 回だけ呼ぶ。
 *
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string> }[]} terms 一覧の項目
 */
export function validateTerms(terms) {
  const patternProblems = findPatternProblems(terms)
  if (patternProblems.length) throw new Error(patternProblems.join('\n'))
  for (const term of terms) {
    if (Array.isArray(term.allow)) {
      throw new Error(`「${termName(term)}」の allow は {許容用語: 理由} の形で書く（ja-text.json）`)
    }
    if (term.files !== undefined) {
      try {
        new RegExp(term.files, 'u')
      } catch (e) {
        throw new Error(`ja-text.json の「${termName(term)}」の files「${term.files}」：正規表現として不正：${e.message}`)
      }
    }
    if (term.word === undefined) continue
    for (const allow of Object.keys(term.allow ?? {})) {
      if (!allow.includes(term.word)) {
        throw new Error(`許容用語「${allow}」が使わない語「${term.word}」を含まない（ja-text.json）`)
      }
    }
  }
}

/**
 * ファイルに当てる項目を返す。files（パスの正規表現）を書いた項目は、当たるファイルにだけ当てる。
 *
 * @param {{ files?: string }[]} terms 一覧の項目
 * @param {string | null} file リポジトリのルートからのパス。コミットメッセージは null（files を書いた項目を当てない）
 * @returns {object[]} 当てる項目
 */
export function termsFor(terms, file) {
  return terms.filter((term) => term.files === undefined || (file !== null && compiled(term).files.test(file)))
}

/**
 * 一覧の語を書いた行を探す。一覧の確認は validateTerms で先に行う。
 *
 * @param {string} text 文書
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許容用語（許容用語は {許容用語: 理由}）
 * @param {{ masked?: boolean }} [options] masked は、text のインラインコードを除いてあるか（withoutInlineCode の結果）
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語
 */
export function findTerms(text, terms, { masked = false } = {}) {
  const hits = []
  text.split('\n').forEach((raw, index) => {
    // インラインコードは、行ごとに 1 回だけ除く
    const line = masked ? raw : maskCodeSpans(raw)
    for (const term of terms) {
      const matched = disallowedMatch(line, term)
      if (matched !== null) hits.push({ ...term, line: index + 1, word: matched })
    }
  })
  return hits
}

/**
 * 許容用語の誤り（理由がない、どの文書にも使われなくなった）を、表示する文にして返す。
 *
 * インラインコードの中の語は検査しないので、そこにしか出ない許容用語は、使われていない（不要）と見る。
 *
 * @param {string[]} texts 調べる文書の内容（全て）
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string> }[]} terms 語（または正規表現）と許容用語
 * @param {{ masked?: boolean }} [options] masked は、texts のインラインコードを除いてあるか（withoutInlineCode の結果）
 * @returns {string[]} 誤りの文
 */
export function findAllowProblems(texts, terms, { masked = false } = {}) {
  const plain = masked ? texts : texts.map(withoutInlineCode)
  return terms.flatMap((term) =>
    checkExclusions(`ja-text.json の「${termName(term)}」の allow`, term.allow ?? {}, (allow) => plain.some((text) => text.includes(allow))),
  )
}

/**
 * 調べるコードかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetCode(file) {
  return codeKind(file) !== null && !CODE_EXCLUDED.test(file)
}

/**
 * コードのコメントとテスト名から、使わない語を書いた行を探す。行番号は元のファイルの行である。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {string} text ファイルの内容
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許容用語
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語（調べないファイルは空）
 */
export function findCodeTerms(file, text, terms) {
  return isTargetCode(file) ? findTerms(commentText(text, codeKind(file)), terms) : []
}

/** 文の区間を分ける字（読点・括弧・かぎ括弧・コロン）。1 区間の「の」を数える単位 */
const CLAUSE_SEPARATOR = /[、，,（）()「」：:]/

/** リンクの表示文字列の前後に付ける目印（読点の数で、リンクだけの項目を見分ける。文書に現れない私用領域の字） */
const LINK_START = '\uE000'
const LINK_END = '\uE001'

/**
 * 参照だけの項目。インラインコード（X に置き換え済み）、リンク、skill の名前（`skill X`）か、空白のない文書の名前（`開発の進め方` など）。
 *
 * 後ろに見出しの参照（`の「起票」`。親の見出しを付けた `の「段階 1」の「タスク」` も）が付いてもよい。文書の名前の項目は、見出しの参照が付いたものだけを参照と見る
 */
const REFERENCE_ITEM = new RegExp(
  `^(?:(?:X|${LINK_START}[^${LINK_END}]*${LINK_END}|skill\\s+X)(?:\\s*の「[^「」]*」)*|[^\\s、。「」（）()]+(?:\\s*の「[^「」]*」)+)$`,
)

/** 動詞の連用形の終わりになるかな（い段とえ段。助詞の「に」「へ」と、「て」「で」は除く） */
const STEM_ENDING = 'きぎしじちぢひびぴみりいえけげせぜねめれ'

/** 見出しの行 */
const HEADING = /^\s*#{1,6}(\s|$)/

/** 項目の行（-、*、+、1. で始まる行） */
const ITEM = /^\s*([-*+]|\d+[.)])\s/

/** JSDoc・Javadoc のタグの行（@param など） */
const DOC_TAG = /^@\w/

/** Javadoc の段落と項目の HTML の要素で始まる行（<p>、<li>、<br /> など） */
const HTML_BLOCK = /^<\/?(p|ul|ol|li|br|pre|table|tr|td|th|dl|dt|dd|blockquote|h\d)\b/i

/** 整列のために空白を 2 つ以上続けた行（使い方の一覧など。行の中の空白の連なりで見分ける） */
const ALIGNED = /\S {2,}\S/

/** 一覧を導く行（「：」で終わる行。次の行は一覧の項目なので、つながない） */
const LEAD = /[：:]\s*$/

/** 飾りの罫線の行（===、--- など、記号だけの行） */
const RULE_LINE = /^[=\-*_#~+]{3,}$/

/** 行末の <br />（Javadoc の改行） */
const LINE_BREAK_END = /<br\s*\/?>\s*$/i

/** 罫線の記号で始まるか終わる行（コメントの節の見出し。`--- 見出し ---` など） */
const RULED_HEADING = /^[=\-*_#~+]{3,}\s*\S|\S\s*[=\-*_#~+]{3,}$/

/** コメントの、語の見出しで始まる行（「使い方：」「対象：」など。定義の一覧の項目）。半角の : は型やコードにも使うので見ない */
const LABEL = /^[^\s：:、。（）()]{1,24}：\s*\S/

/** 日本語の字（かな・漢字）。コメントで日本語の字のない行は、コードや型、コマンドの例として、前後の行とつながない */
const JAPANESE = /[ぁ-んァ-ヶ一-龠]/

/** ひらがな。かなを含む文だけを、文体を測る文にする */
const KANA = /[ぁ-ん]/

/** 表の区切りの行（`|---|:--:|` など。`-` を 1 つ以上含む） */
const TABLE_SEPARATOR = /^\s*\|?[\s:|-]+\|?\s*$/

/**
 * 表の区切りの行かを返す。
 *
 * @param {string} line 行
 * @returns {boolean} 区切りの行なら true
 */
function isTableSeparator(line) {
  return TABLE_SEPARATOR.test(line) && line.includes('-')
}

/** テスト名の行（commentText では、名前の文字列が引用符から始まる） */
const TEST_NAME = /^\s*['"`]/

/**
 * 調べるファイルの、文の測り方を返す。
 *
 * - markdown：Markdown の文書。「。」で終わらない行を次の行とつなぐ
 * - lines：.github の yml・yaml。行ごとに測る（キーと値の行をつながない）
 * - code：コードのコメントとテスト名（commentText の結果）。続くコメントの行をつなぐ
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {'markdown' | 'lines' | 'code'} 測り方
 */
export function styleKind(file) {
  if (/\.ya?ml$/.test(file)) return 'lines'
  return isTargetDoc(file) ? 'markdown' : 'code'
}

/**
 * 行のリンク、URL、インラインコードを、数える形に置き換える。
 *
 * @param {string} raw 行
 * @returns {string} 置き換えた行
 */
function plainLine(raw) {
  const linked = raw.replace(/\[([^\]]*)\]\([^)]*\)/g, `${LINK_START}$1${LINK_END}`).replace(/https?:\/\/\S+/g, 'U')
  return replaceCodeSpans(linked, () => 'X')
}

/**
 * コメントの行から、コメントの記号（行の始めの `//`、`/*`、`*`、`#` と、行の終わりの `*\/`）を除く。
 *
 * @param {string} raw commentText の行
 * @returns {string} 記号を除いた行
 */
function commentBody(raw) {
  return raw
    .replace(/\r$/, '')
    .replace(/\s*\*+\/\s*$/, '')
    .replace(/^\s*(\/\*+|\/\/+|#+|\*+)/, '')
}

/**
 * 文の断片をつなぐ。両側が ASCII の字なら空白を挟む（語がつながらないように）。
 *
 * @param {string[]} pieces 行ごとの断片（前後の空白を除いたもの）
 * @returns {string} 文
 */
function joinPieces(pieces) {
  return pieces.reduce((sentence, piece) => (sentence && /[!-~]$/.test(sentence) && /^[!-~]/.test(piece) ? `${sentence} ${piece}` : sentence + piece), '')
}

/**
 * 文書の文を、行番号つきで取り出す。「。」で文に分け、「。」で終わらない行は次の行とつないで 1 文にする。
 *
 * 段落の境目（空行、見出し、表、項目、コードブロックなど）と、「：」で終わる行の後では、行をつながない。
 * 境目の決め方は測り方ごとに違い、処理の中に書く。
 * コードブロック、URL、リンクの宛先は数えず、インラインコードは 1 字に置き換え、かなを含む文だけを返す。
 *
 * @param {string} text 文書、またはコメントとテスト名だけを残した文書（commentText の結果）
 * @param {'markdown' | 'lines' | 'code'} [kind] 測り方（styleKind）
 * @param {Set<number>} [trailing] code で、行末のコメント（コードの後ろのコメント）の行（trailingCommentLines）。前後の行とつながない
 * @returns {{ line: number, lines: number[], sentence: string }[]} 文の最初の行（1 から）、文の全ての行、文。
 *   リンクの表示文字列は LINK_START と LINK_END で囲む（measureSentence が読点の数に使い、plainSentence で除く）
 */
function proseSentences(text, kind = 'markdown', trailing = new Set()) {
  const result = []
  let pending = []
  const flush = () => {
    const joined = joinPieces(pending.map((piece) => piece.text))
    const sentence = kind === 'code' ? withoutTagType(joined) : joined
    if (KANA.test(sentence)) {
      const lines = [...new Set(pending.map((piece) => piece.line))]
      result.push({ line: lines[0], lines, sentence })
    }
    pending = []
  }
  const append = (line, plain) => {
    const parts = plain.split('。')
    parts.forEach((part, index) => {
      if (part.trim()) pending.push({ line, text: part.trim() })
      if (index < parts.length - 1) flush()
    })
  }
  let frontMatter = false
  // 今の項目の記号の字下げ（項目の中でなければ null）
  let itemIndent = null
  // コードはコメントの記号を除いた行で、コードブロックを読む
  const sources = text.split('\n')
  const testNames = sources.map((source) => kind === 'code' && TEST_NAME.test(source))
  const raws = sources.map((source, index) => (kind === 'code' && !testNames[index] ? commentBody(source) : source))
  const inCode = codeBlockLines(raws.join('\n'))
  raws.forEach((raw, index) => {
    const line = index + 1
    const testName = testNames[index]
    if (inCode[index]) {
      if (inCode[index].part !== 'body') {
        flush()
        itemIndent = null
      }
      return
    }
    // 文書の最初の front matter（--- で囲んだ行）は、行ごとに測る
    if (kind === 'markdown' && index === 0 && raw.trim() === '---') {
      frontMatter = true
      return
    }
    if (frontMatter) {
      if (raw.trim() === '---') frontMatter = false
      else append(line, plainLine(raw))
      flush()
      return
    }
    const plain = plainLine(raw)
    const body = plain.trim()
    // 空行（code ではコメントでない行も空になる）、罫線、表の区切りの行は、段落の境目
    if (!body || RULE_LINE.test(body) || isTableSeparator(raw)) {
      flush()
      itemIndent = null
      return
    }
    // 表のセルは、どの測り方でもセルごとの文にする
    if (body.startsWith('|')) {
      flush()
      itemIndent = null
      for (const cell of plain.split('|')) {
        append(line, cell)
        flush()
      }
      return
    }
    // タグの型（{…}）が閉じていなければ、型の続きの行なので、そのままつなぐ（型は withoutTagType で除く）
    if (kind === 'code' && openTagType(pending)) {
      append(line, plain)
      return
    }
    const indent = plain.length - plain.trimStart().length
    // 前後のどちらの行ともつながない行。lines は全ての行。markdown は見出し。
    // code はテスト名・行末のコメント・罫線で囲んだ節の見出し・整列した行（使い方の一覧など）・日本語の字のない行
    const alone =
      kind === 'lines' ||
      testName ||
      (kind === 'markdown' ? HEADING.test(plain) : trailing.has(line) || RULED_HEADING.test(body) || ALIGNED.test(body) || !JAPANESE.test(body))
    // コードの、語の見出しで始まる行（「使い方：」など）は、定義の一覧の項目として扱う
    const item = ITEM.test(plain) || (kind === 'code' && LABEL.test(body))
    // 項目の続きは、項目の記号より深く字下げした行だけ。浅い行は、項目の後の別の段落にする
    const itemEnded = !item && itemIndent !== null && indent <= itemIndent
    // code は、タグの行（@param など）と、Javadoc の段落・項目の要素（<p>、<li> など）で始まる行の前でも切る
    if (alone || item || itemEnded || (kind === 'code' && (DOC_TAG.test(body) || HTML_BLOCK.test(body)))) flush()
    if (item) itemIndent = indent
    else if (alone || itemEnded) itemIndent = null
    // Javadoc の行末の <br /> は改行なので、除いて、次の行とつながない
    const lineBreak = kind === 'code' && LINE_BREAK_END.test(plain)
    append(line, lineBreak ? plain.replace(LINE_BREAK_END, '') : plain)
    if ((alone || lineBreak || LEAD.test(body)) && !(kind === 'code' && openTagType(pending))) flush()
  })
  flush()
  return result
}

/**
 * 取り出し中の文が、型（波括弧）を閉じていないタグの文かを返す。
 *
 * @param {{ text: string }[]} pending 取り出し中の文の断片
 * @returns {boolean} 型が閉じていなければ true
 */
function openTagType(pending) {
  if (pending.length === 0 || !/^@\w+\s*\{/.test(pending[0].text)) return false
  const text = pending.map((piece) => piece.text).join('')
  return (text.match(/\{/g) ?? []).length > (text.match(/\}/g) ?? []).length
}

/**
 * タグの文（@param {型} 名前 説明）から、型（波括弧の組）を除く。型は日本語の文でないので、字数に数えない。
 *
 * @param {string} sentence 文
 * @returns {string} 型を除いた文
 */
function withoutTagType(sentence) {
  const tag = /^@\w+\s*/.exec(sentence)
  if (!tag || sentence[tag[0].length] !== '{') return sentence
  let depth = 0
  for (let at = tag[0].length; at < sentence.length; at += 1) {
    if (sentence[at] === '{') depth += 1
    if (sentence[at] === '}') depth -= 1
    if (depth === 0) return tag[0] + sentence.slice(at + 1).trimStart()
  }
  return sentence
}

/**
 * 表のセルごとに、文の字数の合計を返す。表の行はつながないので、セルの文は proseSentences と同じになる。かなを含む文のないセルは含めない。
 *
 * @param {string} text 文書、またはコメントとテスト名だけを残した文書
 * @returns {{ line: number, total: number }[]} 行番号（1 から）とセルの合計
 */
export function tableCellTotals(text) {
  return proseCells(text)
    .filter(({ table, sentences }) => table && sentences.length > 0)
    .map(({ line, sentences }) => ({ line, total: sentences.reduce((sum, sentence) => sum + plainSentence(sentence).length, 0) }))
}

/**
 * 文書を、表のセルと表でない行に分け、かなを含む文を取り出す。
 *
 * @param {string} text 文書
 * @returns {{ line: number, table: boolean, sentences: string[] }[]} 行番号、表のセルか、文
 */
function proseCells(text) {
  const result = []
  const inCode = codeBlockLines(text)
  text.split('\n').forEach((raw, index) => {
    if (inCode[index] || isTableSeparator(raw)) return
    const plain = plainLine(raw)
    const table = plain.trimStart().startsWith('|')
    const cells = table ? plain.split('|') : [plain]
    for (const cell of cells) {
      const sentences = cell.split('。').filter((sentence) => KANA.test(sentence)).map((sentence) => sentence.trim())
      result.push({ line: index + 1, table, sentences })
    }
  })
  return result
}

/**
 * 1 つの文の、文体を測る値を返す。
 *
 * @param {string} sentence 文
 * @returns {{ no: number, length: number, comma: number }} 1 区間の「の」の最大の数、字数、読点の数
 */
function measureSentence(sentence) {
  const plain = plainSentence(sentence)
  return {
    no: Math.max(...plain.split(CLAUSE_SEPARATOR).map((part) => (part.match(/の/g) ?? []).length)),
    length: plain.length,
    comma: countCommas(sentence),
  }
}

/**
 * 文の読点を数える。参照だけの項目（REFERENCE_ITEM。インラインコード、リンク、skill の名前、文書の名前と、その後の見出しの参照）の間の読点は数えない。
 *
 * 項目は「」の外の読点で分け、最初の項目の「範囲：」のような見出しは、「」の外のコロンまでを除いて見る。「」の中の読点は、項目を分けないが数える。
 * 前後の項目の両方が参照だけのときに、その間の読点を数えない（他の語を含む項目の前後の読点は数える）。
 *
 * @param {string} sentence 文（リンクの表示文字列は LINK_START と LINK_END で囲んである）
 * @returns {number} 数える読点の数
 */
function countCommas(sentence) {
  const parts = splitOutsideQuotes(sentence, '、')
  // 「」の中の読点は、項目を分けないが数える
  const quoted = parts.reduce((sum, part) => sum + (part.match(/、/g)?.length ?? 0), 0)
  const items = parts.map((item, index) => (index === 0 ? splitOutsideQuotes(item, '：:').at(-1) : item).trim())
  return quoted + items.slice(1).filter((item, index) => !(REFERENCE_ITEM.test(items[index]) && REFERENCE_ITEM.test(item))).length
}

/**
 * 文を、かぎ括弧（「」）の外の区切りの字で分ける。見出しの参照の「」の中の読点やコロンで分けないため。
 *
 * @param {string} text 文
 * @param {string} separators 区切りの字（どれか 1 字で分ける）
 * @returns {string[]} 分けた文字（区切りの字は除く）
 */
function splitOutsideQuotes(text, separators) {
  const parts = ['']
  let depth = 0
  for (const char of text) {
    if (char === '「') depth += 1
    if (char === '」') depth = Math.max(depth - 1, 0)
    if (depth === 0 && separators.includes(char)) parts.push('')
    else parts[parts.length - 1] += char
  }
  return parts
}

/**
 * 文からリンクの目印を除く。
 *
 * @param {string} sentence 文
 * @returns {string} 目印のない文
 */
function plainSentence(sentence) {
  return sentence.replaceAll(LINK_START, '').replaceAll(LINK_END, '')
}

/**
 * 文体の warning を探す。閾値を渡した項目だけを見る。warning は失敗にしない。
 *
 * - no：1 区間の「の」の数、length：字数、comma：読点の数（どれも閾値以上を示す）
 * - negation：「〜なく〜ない」の形の文
 * - compound：かな（動詞の連用形の終わり）の直後に、助詞を挟まず漢語が続く形（複合動詞の崩れの疑い。「少し修正」も示す）
 * - cell：表のセルの文の字数の合計（閾値以上を示す。数え方は tableCellTotals）
 * 文は proseSentences で取り出し、複数の行にまたがる文は 1 文として見る。行は文の最初の行にする。
 * 追加行（added）を渡すと、文の行のどれかが追加行の文だけを示し、行は文の行のうち最初の追加行にする（--changed。注釈が差分の中に出るため）。
 *
 * @param {string} text 文書、またはコメントとテスト名だけを残した文書
 * @param {{ no?: number, length?: number, comma?: number, cell?: number, compound?: string[] }} limits 閾値と、崩れの疑いを見る漢語
 * @param {{ kind?: 'markdown' | 'lines' | 'code', added?: Set<number> | null, trailing?: Set<number> }} [options]
 *   測り方（styleKind）、追加行（null か省略なら全ての行）、code の行末のコメントの行（trailingCommentLines）
 * @returns {{ line: number, kind: 'no' | 'length' | 'comma' | 'negation' | 'compound' | 'cell', word: string, use: string }[]} 見つけた行と種類（行の順）
 */
export function findStyleWarnings(text, limits, { kind = 'markdown', added = null, trailing = new Set() } = {}) {
  const hits = []
  if (limits.cell !== undefined) {
    for (const { line, total } of tableCellTotals(text)) {
      if (added !== null && !added.has(line)) continue
      if (total >= limits.cell) hits.push({ line, kind: 'cell', word: '長いセル', use: `セルの文の字数の合計が ${total} 字` })
    }
  }
  const compound = limits.compound?.length ? new RegExp(`[${STEM_ENDING}](?:${limits.compound.join('|')})`, 'g') : null
  for (const { line: first, lines, sentence: marked } of proseSentences(text, kind, trailing)) {
    const line = added === null ? first : lines.find((number) => added.has(number))
    if (line === undefined) continue
    const sentence = plainSentence(marked)
    const excerpt = sentence.length > 30 ? `${sentence.slice(0, 30)}…` : sentence
    const measured = measureSentence(marked)
    const add = (hitKind, word, detail) => hits.push({ line, kind: hitKind, word, use: `${detail}：「${excerpt}」` })
    if (limits.no !== undefined && measured.no >= limits.no) add('no', '「の」の連なり', `1 区間に「の」が ${measured.no} 個`)
    if (limits.length !== undefined && measured.length >= limits.length) add('length', '長い文', `字数が ${measured.length} 字`)
    if (limits.comma !== undefined && measured.comma >= limits.comma) add('comma', '読点が多い文', `読点が ${measured.comma} 個`)
    if (/なく.*ない/.test(sentence)) add('negation', '否定が続く文', '「〜なく〜ない」の形')
    for (const match of compound ? sentence.matchAll(compound) : []) {
      add('compound', match[0], '複合動詞の崩れの疑い（「〜し直す」の後半だけを替えていないか。かなと漢語の間に助詞があるか）')
    }
  }
  return hits.sort((a, b) => a.line - b.line)
}

/** git commit -v がメッセージのファイルに付ける、切り取り線の行。この行から下は差分で、git が捨てる */
const SCISSORS_LINE = /^# -+ >8 -+\r?$/

/**
 * コミットメッセージから、git が付けるコメントの行（# で始まる行）と、切り取り線の行以降（git commit -v の差分）を除く。行の番号は変えない。
 *
 * @param {string} message コミットメッセージ
 * @returns {string} 除く行を空にしたメッセージ
 */
export function stripMessageComments(message) {
  let cut = false
  return message
    .split('\n')
    .map((line) => {
      if (SCISSORS_LINE.test(line)) cut = true
      return cut || line.startsWith('#') ? '' : line
    })
    .join('\n')
}

/**
 * 測るファイルを返す（--warning --added）。追加行の差分があれば、差分にあるファイルだけにする。
 *
 * 差分にないファイルは追加行がないので、測っても示さない（結果は全てのファイルを測るときと同じ）。
 *
 * @param {string[]} files 対象のファイル
 * @param {Map<string, Set<number> | null> | null} addedMap ファイルごとの追加行。null なら全てのファイルを測る
 * @returns {string[]} 測るファイル（files の順）
 */
export function filesToMeasure(files, addedMap) {
  return addedMap === null ? files : files.filter((file) => addedMap.has(file))
}

/**
 * GitHub Actions の注釈（warning）の行を作る。区切りに使う字（%、改行、プロパティの , と :）を逃がす。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {number} line 行（1 から）
 * @param {string} message 内容
 * @returns {string} 注釈の行
 */
export function formatAnnotation(file, line, message) {
  const escapeData = (text) => text.replaceAll('%', '%25').replaceAll('\r', '%0D').replaceAll('\n', '%0A')
  const escapeProperty = (text) => escapeData(text).replaceAll(':', '%3A').replaceAll(',', '%2C')
  return `::warning file=${escapeProperty(file)},line=${line}::${escapeData(message)}`
}

/**
 * 差分の追加行の文体の警告を、GitHub Actions の注釈で出力する（--changed）。
 *
 * 対象のファイルは既定の実行と同じで、削除したファイルは見ない。複数の行にまたがる文は、どれかの行が追加行なら示す。
 * 注釈は 1 件 1 行で出し、最後に件数の行を出す（GitHub は 1 つの step で注釈を 10 件までしか表示しないので、一覧の代わりにしない）。
 *
 * @param {{ style: { length: number, comma: number, cell: number } }} list 一覧
 */
function printChangedWarnings(list) {
  exitIfShallow()
  const git = (args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
  // 基準は origin/develop（なければ develop）との分岐点。分岐点が HEAD のときの決め方は resolveDiffBase
  const { revision, base } = resolveDiffBase()
  if (base === null) {
    console.log('警告：develop（と origin/develop）がない（または履歴が繋がらない）ので、HEAD の 1 つ目の親との差分を見る（初めの commit なら全ての行を追加行として見る）')
  }
  const added = revision
    ? parseAddedLines(git([...ADDED_DIFF_ARGS, revision, '--']))
    : new Map()
  // 追跡していない新しいファイルは、全ての行を追加行として見る（CI にはない。手元で commit の前に見るため）
  const untracked = revision ? listUntrackedFiles() : listGitFiles()
  for (const file of untracked) added.set(file, null)
  // length、comma、cell だけを見る（「の」の連なり、否定、複合動詞の崩れは形だけでは誤りと判定できないので注釈にしない）。
  // 否定の型は閾値を渡さなくても示されるので、種類でも絞る
  const limits = { length: list.style.length, comma: list.style.comma, cell: list.style.cell }
  const kinds = new Set(Object.keys(limits))
  let count = 0
  for (const file of [...added.keys()].sort()) {
    const doc = isTargetDoc(file)
    if ((!doc && !isTargetCode(file)) || !existsSync(file)) continue
    const raw = readFileSync(file, 'utf8')
    // 文の行のどれかが追加行の文だけを示す（既存の行だけでできた文は示さない）
    const options = { kind: styleKind(file), added: added.get(file), trailing: doc ? new Set() : trailingCommentLines(raw, codeKind(file)) }
    for (const hit of findStyleWarnings(doc ? raw : commentText(raw, codeKind(file)), limits, options)) {
      if (!kinds.has(hit.kind)) continue
      console.log(formatAnnotation(file, hit.line, `${hit.word} → ${hit.use}`))
      count += 1
    }
  }
  console.log(`差分の追加行の文体の警告：${count}`)
}

/**
 * コミットメッセージのファイルを調べ、見つけた語を出力する（--message）。
 *
 * @param {string | undefined} file メッセージのファイル
 * @param {object[]} terms 語の一覧（error か warning）
 * @param {boolean} warning warning の一覧を見るか
 * @returns {number} 終了コード（error の語があれば 1。ファイルがなければ 2）
 */
function checkMessage(file, terms, warning) {
  if (!file || file.startsWith('--')) {
    console.error('--message にはコミットメッセージのファイルを指定する')
    return 2
  }
  if (!existsSync(file)) {
    console.error(`--message のファイルがない：${file}`)
    return 2
  }
  const hits = findTerms(stripMessageComments(readFileSync(file, 'utf8')), termsFor(terms, null))
  for (const hit of hits) console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
  console.log(`${warning ? '注意する語' : '使わない語'}：${hits.length}`)
  return !warning && hits.length ? 1 : 0
}

/**
 * 調べる文書とコードを読む。
 *
 * @param {Map<string, Set<number> | null> | null} addedMap ファイルごとの追加行（--warning）。null なら全てのファイルを読む
 * @param {boolean} style 文体を測るか。測るときだけ、コードの行末のコメントの行を求める
 * @returns {{ docs: { file: string, text: string, masked: string }[], code: { file: string, comments: string, masked: string, trailing: Set<number> }[] }} 文書とコード
 *   - text・comments：文書と、コードのコメントとテスト名
 *   - masked：text・comments のインラインコードを除いたもの（語の一致と許容用語の照合で共に使う）
 *   - trailing：コードの行末のコメントの行
 */
function readTargets(addedMap, style) {
  const gitFiles = listGitFiles()
  const allDocs = gitFiles.filter((file) => isTargetDoc(file))
  exitIfNoFiles(allDocs, '語を調べる文書')
  const allCode = gitFiles.filter((file) => isTargetCode(file))
  exitIfNoFiles(allCode, '語を調べるコード')
  const docs = filesToMeasure(allDocs, addedMap).map((file) => {
    const text = readFileSync(file, 'utf8')
    return { file, text, masked: withoutInlineCode(text) }
  })
  const code = filesToMeasure(allCode, addedMap).map((file) => {
    const text = readFileSync(file, 'utf8')
    const comments = commentText(text, codeKind(file))
    return { file, comments, masked: withoutInlineCode(comments), trailing: style ? trailingCommentLines(text, codeKind(file)) : new Set() }
  })
  return { docs, code }
}

/**
 * 文書とコードの語の一致を集める。コードは、コメントとテスト名だけを見る。
 *
 * @param {{ docs: { file: string, masked: string }[], code: { file: string, masked: string }[] }} targets 調べる文書とコード（インラインコードを除いたもの）
 * @param {object[]} terms 語の一覧
 * @returns {{ file: string, line: number, word: string, use: string }[]} 見つけた行と語
 */
function collectTermHits({ docs, code }, terms) {
  return [...docs, ...code].flatMap(({ file, masked }) => findTerms(masked, termsFor(terms, file), { masked: true }).map((hit) => ({ file, ...hit })))
}

/**
 * 文書とコードの文体の warning を集める。
 *
 * @param {{ docs: { file: string, text: string }[], code: { file: string, comments: string, trailing: Set<number> }[] }} targets 調べる文書とコード
 * @param {object} style 閾値（ja-text.json の style）
 * @param {(file: string) => Set<number> | null} addedOf ファイルの追加行（null なら全ての行）
 * @returns {{ file: string, line: number, word: string, use: string }[]} 文体の warning
 */
function collectStyleHits({ docs, code }, style, addedOf) {
  return [
    ...docs.flatMap(({ file, text }) => findStyleWarnings(text, style, { kind: styleKind(file), added: addedOf(file) }).map((hit) => ({ file, ...hit }))),
    ...code.flatMap(({ file, comments, trailing }) => findStyleWarnings(comments, style, { kind: 'code', trailing, added: addedOf(file) }).map((hit) => ({ file, ...hit }))),
  ]
}

/**
 * 文書とコードを調べ、結果を出力する（既定の実行と --warning）。
 *
 * --added のとき、差分にないファイルは追加行がないので測らない。
 *
 * @param {{ error: object[], warning: object[], style: object }} list 一覧
 * @param {{ warning: boolean, added: boolean }} options --warning と --added を付けたか
 * @returns {number} 終了コード（error の語か許容用語の誤りがあれば 1）
 */
function checkFiles(list, { warning, added }) {
  const terms = warning ? list.warning : list.error
  const addedMap = warning && added ? parseAddedLines(readFileSync(0, 'utf8')) : null
  const addedOf = (file) => (addedMap === null ? null : addedMap.has(file) ? addedMap.get(file) : new Set())
  const isAdded = (file, line) => {
    const lines = addedOf(file)
    return lines === null || lines.has(line)
  }
  const targets = readTargets(addedMap, warning)
  const allowProblems = warning ? [] : findAllowProblems([...targets.docs, ...targets.code].map(({ masked }) => masked), terms, { masked: true })
  for (const problem of allowProblems) console.log(problem)
  const termHits = collectTermHits(targets, terms).filter(({ file, line }) => isAdded(file, line))
  for (const hit of termHits) console.log(`${hit.file}:${hit.line} ${hit.word} → ${hit.use}`)
  let count = allowProblems.length + termHits.length
  if (warning) {
    const styleHits = collectStyleHits(targets, list.style, addedOf)
    for (const hit of styleHits) console.log(`${hit.file}:${hit.line} ${hit.word} → ${hit.use}`)
    count += styleHits.length
  }
  console.log(`${warning ? '注意する語と文' : '使わない語'}：${count}`)
  return !warning && count ? 1 : 0
}

/** 引数として受け付ける印。--message の次の引数はファイル */
const OPTIONS = new Set(['--warning', '--added', '--changed', '--message'])

/**
 * 引数の誤り（知らない印、--warning のない --added）を返す。
 *
 * @param {string[]} args コマンドの引数
 * @returns {string | null} 誤りの文。なければ null
 */
export function findArgumentProblem(args) {
  const unknown = args.filter((arg, index) => !OPTIONS.has(arg) && args[index - 1] !== '--message')
  if (unknown.length) return `知らない引数：${unknown.join(' ')}`
  if (args.includes('--added') && !args.includes('--warning')) return '--added は --warning と合わせて使う'
  return null
}

if (import.meta.main) {
  const args = process.argv.slice(2)
  const argumentProblem = findArgumentProblem(args)
  if (argumentProblem) {
    console.error(`${argumentProblem}（使い方は ja-text.mjs の冒頭のコメント）`)
    process.exit(2)
  }
  const warning = args.includes('--warning')
  let list
  // ja-text.json が読めないのは、文書の違反でなく設定の誤りなので、終了コードを 2 にする
  try {
    list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))
    validateTerms(list.error)
    validateTerms(list.warning)
  } catch (e) {
    console.error(`ja-text.json の設定の誤り：${e.message}`)
    process.exit(2)
  }
  if (args.includes('--message')) {
    process.exit(checkMessage(args[args.indexOf('--message') + 1], warning ? list.warning : list.error, warning))
  }
  if (args.includes('--changed')) {
    printChangedWarnings(list)
    process.exit(0)
  }
  process.exit(checkFiles(list, { warning, added: args.includes('--added') }))
}
