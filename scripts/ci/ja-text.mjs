// 日本語の文を確認する。言い換えの一覧は ja-text.json。
//   error：使わない語。見つけたら失敗にする。使わない語を一部に含む正しい語は、項目の allow に {許す語: 理由} で書く（その中の一致は数えない）
//       理由のない許す語と、どの文書にも使われなくなった許す語は失敗にする
//   warning：文脈で言い換えるかを決める語（「要求」など）と、文体の型。--warning（--caution も同じ）を付けたときだけ一覧にし、失敗にしない（文書を書くときに見る）
//       文体の型は、1 区間の「の」・字数・読点の数が閾値以上の文、「〜なく〜ない」の形の文、複合動詞の崩れの疑い（かなの直後に助詞を挟まず漢語が続く形）。閾値は ja-text.json の style
//       コードブロック、表の区切り行、URL、インラインコードは数えない。既定の実行は warning の件数を「警告：」の行で示す
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ の Markdown と、.github/ の yml・yaml と Markdown。
// コードも見る：アーカイブの外の .java、.kts、.ts、.tsx、.mjs、.cjs、.sh の、コメントとテスト名（@DisplayName、describe・it・test の名前）だけを調べる。
//   コメントの外の文字列とコード（画面の文言など）は調べない。取り出し方は lib/comment-text
// 使い方：node scripts/ci/ja-text.mjs [--warning | --stats]（リポジトリのルートで）
//   --stats：閾値ごとの文の数を出力する（閾値を決めるときに見る）
//   node scripts/ci/ja-text.mjs --message <ファイル>：コミットメッセージのファイルを調べる（# で始まる行は除く）。error があれば失敗にする
import { readFileSync } from 'node:fs'
import { checkExclusions } from './lib/exclusions.mjs'
import { codeKind, commentText } from './lib/comment-text.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 調べる文書 */
const DOCS = /^((docs\/.*|README|AGENTS|\.claude\/skills\/.*)\.md|\.github\/.*\.(md|ya?ml))$/

/** 見ない文書（アーカイブした spec（当時の記録）と、言い換える前の語を例に挙げる言い回しの規則） */
const EXCLUDED = /^(docs\/specs\/archive\/|\.claude\/skills\/docs\/tech-writing\.md$)/

/**
 * 調べないコード。
 * - 生成したコード、依存のパッケージ、ビルドの出力（書き手が直せない）
 * - アーカイブした spec の下（当時の記録）
 * - 語を例に挙げるテスト（サンプルの文に、使わない語を書く）
 */
const CODE_EXCLUDED = /(^|\/)(node_modules|generated|build|dist|\.next)\/|^docs\/specs\/archive\/|^scripts\/ci\/(ja-text\.test\.mjs|lib\/comment-text\.test\.mjs)$/

/**
 * 行の中に、使わない語を、許す語の外で書いているかを返す。
 * 日本語に語の境界はないので、使わない語を一部に含む正しい語は、語ごとの許す語（allow）で救う。
 *
 * @param {string} line 行
 * @param {{ word: string, allow?: Record<string, string> }} term 使わない語と許す語（許す語と理由）
 * @returns {boolean} 許す語の外に一致があれば true
 */
function hasDisallowedMatch(line, term) {
  const allowed = []
  for (const allow of Object.keys(term.allow ?? {})) {
    for (let at = line.indexOf(allow); at !== -1; at = line.indexOf(allow, at + 1)) {
      allowed.push([at, at + allow.length])
    }
  }
  for (let at = line.indexOf(term.word); at !== -1; at = line.indexOf(term.word, at + 1)) {
    const end = at + term.word.length
    if (!allowed.some(([from, to]) => from <= at && end <= to)) return true
  }
  return false
}

/**
 * 一覧の語を書いた行を探す。許す語が使わない語を含まないときは、設定の誤りとして例外にする。
 *
 * @param {string} text 文書
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許す語（許す語は {許す語: 理由}）
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語
 */
export function findTerms(text, terms) {
  for (const term of terms) {
    if (Array.isArray(term.allow)) {
      throw new Error(`「${term.word}」の allow は {許す語: 理由} の形で書く（ja-text.json）`)
    }
    for (const allow of Object.keys(term.allow ?? {})) {
      if (!allow.includes(term.word)) {
        throw new Error(`許す語「${allow}」が使わない語「${term.word}」を含まない（ja-text.json）`)
      }
    }
  }
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const term of terms) {
      if (hasDisallowedMatch(line, term)) hits.push({ line: index + 1, ...term })
    }
  })
  return hits
}

/**
 * 許す語の誤り（理由がない、どの文書にも使われなくなった）を、表示する文にして返す。
 *
 * @param {string[]} texts 調べる文書の内容（全て）
 * @param {{ word: string, allow?: Record<string, string> }[]} terms 語と許す語
 * @returns {string[]} 誤りの文
 */
export function findAllowProblems(texts, terms) {
  return terms.flatMap((term) =>
    checkExclusions(`ja-text.json の「${term.word}」の allow`, term.allow ?? {}, (allow) => texts.some((text) => text.includes(allow))),
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

/** 動詞の連用形の終わりになるかな（い段とえ段。助詞の「に」「へ」と、「て」「で」は除く） */
const STEM_ENDING = 'きぎしじちぢひびぴみりいえけげせぜねめれ'

/**
 * 文書の文を、行番号つきで取り出す。表のセルと「。」で文に分ける。
 * コードブロック、表の区切り行、URL、リンクの宛先は数えず、インラインコードは 1 字に置き換える（かなを含む文だけを返す）。
 *
 * @param {string} text 文書、またはコメントとテスト名だけを残した文書（commentText の結果）
 * @returns {{ line: number, sentence: string }[]} 行番号（1 から）と文
 */
export function proseSentences(text) {
  const sentences = []
  let fenced = false
  text.split('\n').forEach((raw, index) => {
    if (/^\s*(```|~~~)/.test(raw)) {
      fenced = !fenced
      return
    }
    if (fenced || (/^\s*\|?[\s:|-]+\|?\s*$/.test(raw) && raw.includes('-'))) return
    const plain = raw
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/https?:\/\/\S+/g, 'U')
      .replace(/`[^`]*`/g, 'X')
    const cells = plain.trimStart().startsWith('|') ? plain.split('|') : [plain]
    for (const cell of cells) {
      for (const sentence of cell.split('。')) {
        if (/[ぁ-ん]/.test(sentence)) sentences.push({ line: index + 1, sentence: sentence.trim() })
      }
    }
  })
  return sentences
}

/**
 * 1 つの文の、文体を測る値を返す。
 *
 * @param {string} sentence 文
 * @returns {{ no: number, length: number, comma: number }} 1 区間の「の」の最大の数、字数、読点の数
 */
export function measureSentence(sentence) {
  return {
    no: Math.max(...sentence.split(CLAUSE_SEPARATOR).map((part) => (part.match(/の/g) ?? []).length)),
    length: sentence.length,
    comma: (sentence.match(/、/g) ?? []).length,
  }
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
  for (const { line, sentence } of proseSentences(text)) {
    const excerpt = sentence.length > 30 ? `${sentence.slice(0, 30)}…` : sentence
    const measured = measureSentence(sentence)
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
    console.log(`  「〜なく〜ない」：${negation}、複合動詞の崩れの疑い：${compound}、「要求」を書いた行：${request}`)
  }
}

/**
 * コミットメッセージから、git が付けるコメントの行（# で始まる行）を除く。行の番号は変えない。
 *
 * @param {string} message コミットメッセージ
 * @returns {string} # で始まる行を空にしたメッセージ
 */
export function stripMessageComments(message) {
  return message
    .split('\n')
    .map((line) => (line.startsWith('#') ? '' : line))
    .join('\n')
}

if (isMain(import.meta.url)) {
  const args = process.argv.slice(2)
  const warning = args.includes('--warning') || args.includes('--caution')
  const list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))
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
      return sum + docs + codeFiles.reduce((n, file, index) => n + findCodeTerms(file, codeTexts[index], [term]).length, 0)
    }, styleHits.length)
    console.log(`警告：warning が ${warningCount} 件ある（一覧：node scripts/ci/ja-text.mjs --warning）`)
  }
  console.log(`${warning ? '注意する語と文' : '使わない語'}：${count}`)
  process.exit(!warning && count ? 1 : 0)
}

