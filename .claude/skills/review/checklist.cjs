// 書き方の規則の各文書の「確認方法：」の行から、確認方法に「レビュー」を含むものを抜き出し、レビューのチェックリストにする
// 使い方：node .claude/skills/review/checklist.cjs（リポジトリのルートで。結果を標準出力に Markdown で出す）
// 対象は、docs/05-開発規則/ の文書。確認方法の行は、直前の見出し（## から ######）の節のものとして扱う。
// コードブロック（CommonMark の囲み。``` か ~~~ を 3 つ以上。同じ文字で同じ長さ以上の行だけで閉じる）の中は読まない。
// 形 1：「確認方法：<方法>」の 1 行（節の規則が 1 つ、または確認方法が 1 つ）。
// 形 2：「確認方法：」だけの行の後に、空行を挟んで「- <規則の短い名前>：<方法>」の箇条を並べる（確認方法の違う規則が複数ある節）。
// 決まった形でない行は、黙って読み飛ばさず、ファイルと行の番号と理由を標準エラーに出し、終了コード 1 で終える。
// 異常の行：形 1・形 2 として読まなかった「確認方法」で始まる行（行頭の空白、半角のコロン、表のセルを含む）、
// 形 1 の後に続く箇条、形 2 の箇条の中の形の違う行、箇条のない形 2、閉じないコードブロック。
const fs = require('fs'), { execSync } = require('child_process')

const TARGET_DIR = 'docs/05-開発規則/'

/**
 * チェックリストの対象の文書か。
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} docs/05-開発規則/ の文書なら true
 */
function isTargetFile(file) {
  return file.startsWith(TARGET_DIR) && file.endsWith('.md')
}

/**
 * 確認方法の行の形を外れた行か（行の始めの空白の後、または表のセルの始めが「確認方法」）。
 * @param {string} line 行
 * @returns {boolean} 形 1・形 2 として読まなかったときに異常にする行なら true
 */
function looksLikeCheckMethod(line) {
  if (/^\s*確認方法/.test(line)) return true
  return /^\s*\|/.test(line) && line.split('|').some((cell) => cell.trim().startsWith('確認方法'))
}

/**
 * 文書の「確認方法：」の行を読む。
 * 箇条は、「確認方法：」だけの行の後の、最初の空行の後に続く「- 」の行（最初の「：」で名前と方法に分ける）。
 * 箇条は、空行か見出しで終わる。
 * @param {string} text 文書の全文
 * @param {string} file 題名がないときに使う文書のパス
 * @returns {{title: string, entries: {section: string, name: string, check: string}[], errors: {line: number, message: string}[]} | null}
 *   確認方法の行も異常もなければ null。形 1 の name は空文字。line は 1 始まり
 */
function readCheckMethods(text, file) {
  const lines = text.split(/\r?\n/)
  let title = null
  let section = ''
  let fence = null // { char, len, line }
  // none：箇条を待たない、after1：形 1 の行の後、head：形 2 の行の直後、start：空行の後で最初の箇条を待つ、bullets：箇条の中
  let state = 'none'
  let bulletCount = 0
  let headLine = 0
  const entries = []
  const errors = []
  const fail = (line, message) => errors.push({ line, message })
  const endBullets = () => {
    if ((state === 'head' || state === 'start') && bulletCount === 0) {
      fail(headLine, '「確認方法：」の後に「- <名前>：<方法>」の箇条が 1 つもない')
    }
    state = 'none'
  }
  lines.forEach((raw, index) => {
    const no = index + 1
    const line = raw.trimEnd()
    if (fence) {
      const close = /^ {0,3}(`{3,}|~{3,})\s*$/.exec(line)
      if (close && close[1][0] === fence.char && close[1].length >= fence.len) fence = null
      return
    }
    const open = /^ {0,3}(`{3,}|~{3,})(.*)$/.exec(line)
    if (open && !(open[1][0] === '`' && open[2].includes('`'))) {
      endBullets()
      fence = { char: open[1][0], len: open[1].length, line: no }
      return
    }
    if (title === null && line.startsWith('# ')) title = line.replace(/^# /, '')
    const heading = /^ {0,3}#{2,6}\s+(.*)$/.exec(line)
    if (heading) {
      endBullets()
      section = heading[1].trim()
      return
    }
    const method = /^確認方法：(.*)$/.exec(line)
    if (method) {
      endBullets()
      const check = method[1].trim()
      bulletCount = 0
      headLine = no
      if (check) {
        entries.push({ section, name: '', check })
        state = 'after1'
      } else {
        state = 'head'
      }
      return
    }
    const blank = line.trim() === ''
    const isBullet = /^[-*] /.test(line)
    if (state === 'after1') {
      if (blank) return
      state = 'none'
      if (isBullet) fail(no, '形 1 の「確認方法：<方法>」の後に箇条が続いている（形 1 と形 2 を混ぜている）')
    } else if (state === 'head') {
      if (blank) {
        state = 'start'
        return
      }
      fail(no, '形 2 の「確認方法：」の後に空行がない')
      state = 'start'
    }
    if (state === 'start') {
      if (blank) return
      if (!isBullet) {
        endBullets()
      } else {
        state = 'bullets'
      }
    }
    if (state === 'bullets') {
      if (blank) {
        endBullets()
        return
      }
      const bullet = /^- (.*?)：(.*)$/.exec(line)
      if (bullet && bullet[2].trim() !== '') {
        bulletCount++
        entries.push({ section, name: bullet[1].trim(), check: bullet[2].trim() })
      } else {
        fail(no, `形 2 の箇条が「- <名前>：<方法>」（全角のコロン）の形でない：${line.trim()}`)
      }
      return
    }
    if (looksLikeCheckMethod(line)) {
      fail(no, `「確認方法」の行が、形 1・形 2 の形（行頭から「確認方法：」）でない：${line.trim()}`)
    }
  })
  endBullets()
  if (fence) fail(fence.line, 'コードブロックが文書の終わりまで閉じない')
  if (!entries.length && !errors.length) return null
  return { title: title || file, entries, errors }
}

/**
 * 確認方法に「レビュー」を含むものを、チェックリストの項目（Markdown）にする。
 * @param {{section: string, name: string, check: string}[]} entries 確認方法の行
 * @returns {string[]} チェック項目の行
 */
function reviewItems(entries) {
  return entries
    .filter((e) => e.check.includes('レビュー'))
    .map((e) => `- [ ] 「${e.section}」${e.name}：${e.check}`)
}

/** git で管理している docs/05-開発規則/ の文書からチェックリストを作って標準出力に出す。項目がないか、決まった形でない行があれば異常終了する。 */
function main() {
  // 日本語を含むパスを、引用符と 8 進数の形にせずに読む
  const files = execSync('git -c core.quotepath=false ls-files "docs/*.md"', { encoding: 'utf8' })
    .split('\n').filter(Boolean).filter(isTargetFile)
  const out = []
  let failed = false
  for (const file of files) {
    const doc = readCheckMethods(fs.readFileSync(file, 'utf8'), file)
    if (!doc) continue
    for (const e of doc.errors) {
      console.error(`${file}:${e.line}: ${e.message}`)
      failed = true
    }
    const items = reviewItems(doc.entries)
    if (items.length) out.push(`## ${doc.title}（${file}）`, '', ...items, '')
  }
  if (failed) process.exit(1)
  if (!out.length) {
    console.error('docs/05-開発規則/ に確認方法が「レビュー」の行がない')
    process.exit(1)
  }
  console.log(out.join('\n'))
}

module.exports = { isTargetFile, readCheckMethods, reviewItems }

if (require.main === module) main()
