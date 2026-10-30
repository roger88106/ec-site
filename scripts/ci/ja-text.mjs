// 日本語の文を確認する。言い換えの一覧は ja-text.json。
//   error：使わない語。見つけたら失敗にする。使わない語を一部に含む正しい語は、項目の allow に {許す語: 理由} で書く（その中の一致は数えない）
//       理由のない許す語と、どの文書にも使われなくなった許す語は失敗にする
//       語の検査（error と warning の語、allow の照合）は、インラインコード（バッククォートで囲んだ部分）の中を見ない。許す語がインラインコードの中にしか出ないときは、使われていないと見る
//   warning：文脈で言い換えるかを決める語（「要求」など）と、文体の型。--warning を付けたときだけ一覧にし、失敗にしない（文書を書くときに見る）
//       文体の型は、1 区間の「の」・字数・読点の数が閾値以上の文、「〜なく〜ない」の形の文、複合動詞の崩れの疑い（かなの直後に助詞を挟まず漢語が続く形）。閾値は ja-text.json の style
//       コードブロック、表の区切り行、URL、インラインコードは数えない。既定の実行は warning の件数を「警告：」の行で示す
//       読点の数は、インラインコードかリンクだけの項目（「第 N 節」が付いてもよい）を区切る読点を数えない（範囲・参照の行の並び）。他の語を含む項目の前後の読点は数える
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ と .claude/agents/ の Markdown と、.github/ の yml・yaml と Markdown。
// コードも見る：アーカイブの外の .java、.kts、.ts、.tsx、.mjs、.cjs、.sh の、コメントとテスト名（@DisplayName、describe・it・test の名前）だけを調べる。
//   コメントの外の文字列とコード（画面の文言など）は調べない。取り出し方は lib/comment-text
// 使い方：node scripts/ci/ja-text.mjs [--warning | --stats]（リポジトリのルートで）
//   --stats：閾値ごとの文の数を出力する。表のセルは、セルごとに文の字数を合計して数える（閾値を決めるときに見る）
//   node scripts/ci/ja-text.mjs --message <ファイル>：コミットメッセージのファイルを調べる（# で始まる行は除く）。error があれば失敗にする
import { readFileSync } from 'node:fs'
import { checkExclusions } from './lib/exclusions.mjs'
import { codeKind, commentText } from './lib/comment-text.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 調べる文書 */
const DOCS = /^((docs\/.*|README|AGENTS|\.claude\/(skills|agents)\/.*)\.md|\.github\/.*\.(md|ya?ml))$/

/** 見ない文書（アーカイブした spec（当時の記録）と、言い換える前の語を例に挙げる言い回しの規則） */
const EXCLUDED = /^(docs\/08-spec\/archive\/|\.claude\/skills\/docs\/tech-writing\.md$)/

/**
 * 調べないコード。
 * - 生成したコード、依存のパッケージ、ビルドの出力（書き手が直せない）
 * - アーカイブした spec の下（当時の記録）
 * - 語を例に挙げるテスト（サンプルの文に、使わない語を書く）
 */
const CODE_EXCLUDED = /(^|\/)(node_modules|generated|build|dist|\.next)\/|^docs\/08-spec\/archive\/|^scripts\/ci\/(ja-text\.test\.mjs|lib\/comment-text\.test\.mjs)$/

/** インラインコード（バッククォートで囲んだ、1 行の中の部分） */
const INLINE_CODE = /`[^`\n]+`/g

/**
 * インラインコードを、同じ長さの空白に置き換える（語の一致がコードをまたいで作られないように、字数は保つ）。
 *
 * @param {string} text 文書、または行
 * @returns {string} インラインコードを除いたもの
 */
function withoutInlineCode(text) {
  return text.replace(INLINE_CODE, (code) => ' '.repeat(code.length))
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

/**
 * 行の中に、使わない語を、許す語の外で書いているかを返す。
 * 日本語に語の境界はないので、使わない語を一部に含む正しい語は、語ごとの許す語（allow）で救う。
 * word の項目は部分の一致で、pattern の項目は正規表現の一致で見る（漢字の後を除くなどは pattern で書く）。
 *
 * インラインコードの中は見ない。
 *
 * @param {string} rawLine 行
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string> }} term 使わない語（または正規表現）と許す語（許す語と理由）
 * @returns {string | null} 許す語の外にある最初の一致（当たった文字列）。なければ null
 */
function disallowedMatch(rawLine, term) {
  const line = withoutInlineCode(rawLine)
  const allowed = []
  for (const allow of Object.keys(term.allow ?? {})) {
    for (let at = line.indexOf(allow); at !== -1; at = line.indexOf(allow, at + 1)) {
      allowed.push([at, at + allow.length])
    }
  }
  const matches = []
  if (term.pattern !== undefined) {
    for (const match of line.matchAll(patternRegExp(term))) matches.push({ at: match.index, text: match[0] })
  } else {
    for (let at = line.indexOf(term.word); at !== -1; at = line.indexOf(term.word, at + 1)) {
      matches.push({ at, text: term.word })
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
    } catch (error) {
      problems.push(`${name}：正規表現として不正：${error.message}`)
      continue
    }
    const { hit, miss } = term.examples ?? {}
    if (!Array.isArray(hit) || hit.length === 0 || !Array.isArray(miss) || miss.length === 0) {
      problems.push(`${name}：examples の hit と miss（どちらも 1 つ以上の配列）が必要`)
      continue
    }
    for (const example of hit) {
      if (disallowedMatch(example, term) === null) problems.push(`${name}：hit の例「${example}」に当たらない`)
    }
    for (const example of miss) {
      if (disallowedMatch(example, term) !== null) problems.push(`${name}：miss の例「${example}」に当たる`)
    }
  }
  return problems
}

/**
 * 一覧の設定の誤り（pattern の誤り、許す語の形の誤り、許す語が使わない語を含まない誤り）を、例外にする。
 * 一覧を読んだ後に 1 回だけ呼ぶ。
 *
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string> }[]} terms 一覧の項目
 */
export function validateTerms(terms) {
  const patternProblems = findPatternProblems(terms)
  if (patternProblems.length) throw new Error(patternProblems.join('\n'))
  for (const term of terms) {
    if (Array.isArray(term.allow)) {
      throw new Error(`「${termName(term)}」の allow は {許す語: 理由} の形で書く（ja-text.json）`)
    }
    if (term.word === undefined) continue
    for (const allow of Object.keys(term.allow ?? {})) {
      if (!allow.includes(term.word)) {
        throw new Error(`許す語「${allow}」が使わない語「${term.word}」を含まない（ja-text.json）`)
      }
    }
  }
}

/**
 * 一覧の語を書いた行を探す。一覧の確認は validateTerms で先に行う。
 *
 * @param {string} text 文書
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許す語（許す語は {許す語: 理由}）
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語
 */
export function findTerms(text, terms) {
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const term of terms) {
      const matched = disallowedMatch(line, term)
      if (matched !== null) hits.push({ ...term, line: index + 1, word: matched })
    }
  })
  return hits
}

/**
 * 許す語の誤り（理由がない、どの文書にも使われなくなった）を、表示する文にして返す。
 * インラインコードの中の語は検査しないので、そこにしか出ない許す語は、使われていない（不要）と見る。
 *
 * @param {string[]} texts 調べる文書の内容（全て）
 * @param {{ word?: string, pattern?: string, allow?: Record<string, string> }[]} terms 語（または正規表現）と許す語
 * @returns {string[]} 誤りの文
 */
export function findAllowProblems(texts, terms) {
  return terms.flatMap((term) =>
    checkExclusions(`ja-text.json の「${termName(term)}」の allow`, term.allow ?? {}, (allow) => texts.some((text) => withoutInlineCode(text).includes(allow))),
  )
}

/**
 * 調べる文書かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetDoc(file) {
  return DOCS.test(file) && !EXCLUDED.test(file)
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
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許す語
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語（調べないファイルは空）
 */
export function findCodeTerms(file, text, terms) {
  return isTargetCode(file) ? findTerms(commentText(text, codeKind(file)), terms) : []
}

/** 文の区間を分ける字（読点・括弧・かぎ括弧・コロン）。1 区間の「の」を数える単位 */
const CLAUSE_SEPARATOR = /[、，,（）()「」：:]/

/** リンクの表示文字列の前後に付ける目印（読点の数で、リンクだけの項目を見分ける。文書に現れない私用領域の字） */
const LINK_START = ''
const LINK_END = ''

/** インラインコード（X に置き換え済み）かリンクだけの項目。後ろに「第 N 節」が付いてもよい */
const REFERENCE_ITEM = new RegExp(`^(X|${LINK_START}[^${LINK_END}]*${LINK_END})(\\s*第\\s*\\d+(\\.\\d+)*\\s*節)?$`)

/** 動詞の連用形の終わりになるかな（い段とえ段。助詞の「に」「へ」と、「て」「で」は除く） */
const STEM_ENDING = 'きぎしじちぢひびぴみりいえけげせぜねめれ'

/**
 * 文書の文を、行番号つきで取り出す。表のセルと「。」で文に分ける。
 * コードブロック、表の区切り行、URL、リンクの宛先は数えず、インラインコードは 1 字に置き換える（かなを含む文だけを返す）。
 * リンクの表示文字列は LINK_START と LINK_END で囲んで返す（measureSentence が読点の数に使う。plainSentence で除く）。
 *
 * @param {string} text 文書、またはコメントとテスト名だけを残した文書（commentText の結果）
 * @returns {{ line: number, sentence: string }[]} 行番号（1 から）と文
 */
export function proseSentences(text) {
  return proseCells(text).flatMap(({ line, sentences }) => sentences.map((sentence) => ({ line, sentence })))
}

/**
 * 表のセルごとに、文の字数の合計を返す（--stats）。文の取り出し方は proseSentences と同じ。かなを含む文のないセルは含めない。
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
  let fenced = false
  text.split('\n').forEach((raw, index) => {
    if (/^\s*(```|~~~)/.test(raw)) {
      fenced = !fenced
      return
    }
    if (fenced || (/^\s*\|?[\s:|-]+\|?\s*$/.test(raw) && raw.includes('-'))) return
    const plain = raw
      .replace(/\[([^\]]*)\]\([^)]*\)/g, `${LINK_START}$1${LINK_END}`)
      .replace(/https?:\/\/\S+/g, 'U')
      .replace(/`[^`]*`/g, 'X')
    const table = plain.trimStart().startsWith('|')
    const cells = table ? plain.split('|') : [plain]
    for (const cell of cells) {
      const sentences = cell.split('。').filter((sentence) => /[ぁ-ん]/.test(sentence)).map((sentence) => sentence.trim())
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
export function measureSentence(sentence) {
  const plain = plainSentence(sentence)
  return {
    no: Math.max(...plain.split(CLAUSE_SEPARATOR).map((part) => (part.match(/の/g) ?? []).length)),
    length: plain.length,
    comma: countCommas(sentence),
  }
}

/**
 * 文の読点を数える。インラインコードかリンクだけの項目（「第 N 節」が付いてもよい）の間の読点は数えない。
 * 項目は読点で分け、最初の項目の「範囲：」のような見出しは、コロンまでを除いて見る。
 * 前後の項目の両方が参照だけのときに、その間の読点を数えない（他の語を含む項目の前後の読点は数える）。
 *
 * @param {string} sentence 文（リンクの表示文字列は LINK_START と LINK_END で囲んである）
 * @returns {number} 数える読点の数
 */
function countCommas(sentence) {
  const items = sentence.split('、').map((item) => item.slice(Math.max(item.lastIndexOf('：'), item.lastIndexOf(':')) + 1).trim())
  return items.slice(1).filter((item, index) => !(REFERENCE_ITEM.test(items[index]) && REFERENCE_ITEM.test(item))).length
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
 * - no：1 区間の「の」の数、length：字数、comma：読点の数（どれも閾値以上を示す）
 * - negation：「〜なく〜ない」の形の文
 * - compound：かな（動詞の連用形の終わり）の直後に、助詞を挟まず漢語が続く形（複合動詞の崩れの疑い。「少し修正」も示す）
 *
 * @param {string} text 文書、またはコメントとテスト名だけを残した文書
 * @param {{ no?: number, length?: number, comma?: number, compound?: string[] }} limits 閾値と、崩れの疑いを見る漢語
 * @returns {{ line: number, kind: 'no' | 'length' | 'comma' | 'negation' | 'compound', word: string, use: string }[]} 見つけた行と種類
 */
export function findStyleWarnings(text, limits) {
  const hits = []
  const compound = limits.compound?.length ? new RegExp(`[${STEM_ENDING}](?:${limits.compound.join('|')})`, 'g') : null
  for (const { line, sentence: marked } of proseSentences(text)) {
    const sentence = plainSentence(marked)
    const excerpt = sentence.length > 30 ? `${sentence.slice(0, 30)}…` : sentence
    const measured = measureSentence(marked)
    const add = (kind, word, detail) => hits.push({ line, kind, word, use: `${detail}：「${excerpt}」` })
    if (limits.no !== undefined && measured.no >= limits.no) add('no', '「の」の連なり', `1 区間に「の」が ${measured.no} 個`)
    if (limits.length !== undefined && measured.length >= limits.length) add('length', '長い文', `字数が ${measured.length} 字`)
    if (limits.comma !== undefined && measured.comma >= limits.comma) add('comma', '読点が多い文', `読点が ${measured.comma} 個`)
    if (/なく.*ない/.test(sentence)) add('negation', '否定が続く文', '「〜なく〜ない」の形')
    for (const match of compound ? sentence.matchAll(compound) : []) {
      add('compound', match[0], '複合動詞の崩れの疑い（「〜し直す」の後半だけを替えていないか。かなと漢語の間に助詞があるか）')
    }
  }
  return hits
}

/**
 * 閾値ごとの文の数を出力する（閾値を決めるときに使う。--stats）。
 *
 * @param {string[]} docTexts 調べる文書の内容
 * @param {string[]} codeComments コメントとテスト名だけを残した文書
 * @param {{ warning: { word: string }[], style: { compound: string[] } }} list 一覧
 */
function printStats(docTexts, codeComments, list) {
  for (const [label, texts] of [['文書', docTexts], ['コメントとテスト名', codeComments]]) {
    const measured = texts.flatMap((text) => proseSentences(text).map(({ sentence }) => measureSentence(sentence)))
    const countAtLeast = (key, value) => measured.filter((item) => item[key] >= value).length
    const negation = texts.reduce((n, text) => n + findStyleWarnings(text, {}).filter((hit) => hit.kind === 'negation').length, 0)
    const compound = texts.reduce((n, text) => n + findStyleWarnings(text, { compound: list.style.compound }).filter((hit) => hit.kind === 'compound').length, 0)
    const request = texts.reduce((n, text) => n + findTerms(text, list.warning.filter((term) => term.word === '要求')).length, 0)
    console.log(`${label}：${measured.length} 文`)
    console.log(`  「の」（1 区間）：${[3, 4, 5, 6].map((v) => `${v}以上 ${countAtLeast('no', v)}`).join('、')}`)
    console.log(`  字数：${[80, 100, 120].map((v) => `${v}以上 ${countAtLeast('length', v)}`).join('、')}`)
    console.log(`  読点：${[4, 5, 6].map((v) => `${v}以上 ${countAtLeast('comma', v)}`).join('、')}`)
    const cells = texts.flatMap((text) => tableCellTotals(text))
    console.log(`  表のセルの文の字数の合計：${[40, 60, 80].map((v) => `${v}以上 ${cells.filter((cell) => cell.total >= v).length}`).join('、')}（セル ${cells.length}）`)
    console.log(`  「〜なく〜ない」：${negation}、複合動詞の崩れの疑い：${compound}、「要求」を書いた行：${request}`)
  }
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

if (isMain(import.meta.url)) {
  const args = process.argv.slice(2)
  const warning = args.includes('--warning')
  const list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))
  validateTerms(list.error)
  validateTerms(list.warning)
  const terms = warning ? list.warning : list.error
  const messageIndex = args.indexOf('--message')
  if (messageIndex !== -1) {
    const file = args[messageIndex + 1]
    if (!file) {
      console.error('--message にはコミットメッセージのファイルを指定する')
      process.exit(2)
    }
    const hits = findTerms(stripMessageComments(readFileSync(file, 'utf8')), terms)
    for (const hit of hits) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
    }
    console.log(`${warning ? '注意する語' : '使わない語'}：${hits.length}`)
    process.exit(!warning && hits.length ? 1 : 0)
  }
  const gitFiles = listGitFiles()
  const files = gitFiles.filter((file) => isTargetDoc(file))
  exitIfNoFiles(files, '語を調べる文書')
  const codeFiles = gitFiles.filter((file) => isTargetCode(file))
  exitIfNoFiles(codeFiles, '語を調べるコード')
  const texts = files.map((file) => readFileSync(file, 'utf8'))
  const codeTexts = codeFiles.map((file) => readFileSync(file, 'utf8'))
  const codeComments = codeFiles.map((file, index) => commentText(codeTexts[index], codeKind(file)))
  if (args.includes('--stats')) {
    printStats(texts, codeComments, list)
    process.exit(0)
  }
  const allowTexts = [...texts, ...codeComments]
  const allowProblems = warning ? [] : findAllowProblems(allowTexts, terms)
  for (const problem of allowProblems) {
    console.log(problem)
  }
  let count = allowProblems.length
  for (const [index, file] of files.entries()) {
    for (const hit of findTerms(texts[index], terms)) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
      count += 1
    }
  }
  for (const [index, file] of codeFiles.entries()) {
    for (const hit of findCodeTerms(file, codeTexts[index], terms)) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
      count += 1
    }
  }
  // 文体の warning（--warning のときだけ一覧にする。既定の実行は件数だけを示す）
  const styleHits = []
  for (const [index, file] of files.entries()) {
    for (const hit of findStyleWarnings(texts[index], list.style)) styleHits.push({ file, ...hit })
  }
  for (const [index, file] of codeFiles.entries()) {
    for (const hit of findStyleWarnings(codeComments[index], list.style)) styleHits.push({ file, ...hit })
  }
  if (warning) {
    for (const hit of styleHits) {
      console.log(`${hit.file}:${hit.line} ${hit.word} → ${hit.use}`)
    }
    count += styleHits.length
  } else {
    // 「警告：」で始まる行は、check-all.sh が通った検査でも出す
    const warningCount = list.warning.reduce((sum, term) => {
      const docs = texts.reduce((n, text) => n + findTerms(text, [term]).length, 0)
      return sum + docs + codeFiles.reduce((n, file, index) => n + (isTargetCode(file) ? findTerms(codeComments[index], [term]).length : 0), 0)
    }, styleHits.length)
    console.log(`警告：warning が ${warningCount} 件ある（一覧：node scripts/ci/ja-text.mjs --warning）`)
  }
  console.log(`${warning ? '注意する語と文' : '使わない語'}：${count}`)
  process.exit(!warning && count ? 1 : 0)
}

