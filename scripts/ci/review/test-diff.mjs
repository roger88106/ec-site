// 既存のテストの変更が、名前の変更と移動だけであることを確認する。
//
// 元の commit から今の作業ツリーまでの、テストのファイル（バックエンドの src/test・src/integrationTest、画面の *.test.ts(x)、e2e/）の差分を見る。
// 既存のファイルの消した行に名前の対応（旧=新）を当て、どこかのテストのファイルの追加した行と比べる（前後の空白は見ない）。
// 一致しない行があれば、その行を出力して終了コード 1 を返す。import・package の行（複数行の import を含む）は比べない。
// 既存のファイルの追加した行で、照合して残ったものも出す。
// 新しく追加したテストのファイルの、照合して残った行は見ない（既存のテストを変えないことを確認する検査のため）。
//
// 使い方：node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]
//   名前の対応のファイル：1 行に「旧=新」。空行と # の行は飛ばす。名前は語の境目で当てる。
//   元の commit がない、対応のファイルがない、対応のファイルに「旧=新」の形でない行があるときは、理由を出して終了コード 2 を返す。
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'

/**
 * 名前の対応のファイルの中身を、[旧, 新] の並びにする。
 *
 * @param {string} text 名前の対応のファイルの中身
 * @returns {string[][]} [旧, 新] の並び（ファイルの順）
 */
export function parseRenames(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '' && !line.startsWith('#'))
    .map((line) => line.split('=').map((part) => part.trim()))
}

/**
 * 名前の対応のファイルで、「旧=新」の形でない行を探す（= が 1 つでない、旧か新が空）。
 *
 * @param {string} text 名前の対応のファイルの中身
 * @returns {{ line: number, text: string }[]} 形でない行（1 から）と、その行の文字
 */
export function findRenameProblems(text) {
  return text
    .split(/\r?\n/)
    .map((raw, index) => ({ line: index + 1, text: raw.trim() }))
    .filter(({ text: line }) => line !== '' && !line.startsWith('#'))
    .filter(({ text: line }) => {
      const parts = line.split('=').map((part) => part.trim())
      return parts.length !== 2 || parts.some((part) => part === '')
    })
}

/**
 * テストのファイルか。
 *
 * @param {string} path リポジトリのルートからのパス
 * @returns {boolean} テストのファイルなら true
 */
function isTestPath(path) {
  return (
    /\/src\/(test|integrationTest)\//.test(path) ||
    path.startsWith('e2e/') ||
    (path.startsWith('frontend/') && /\.test\.(ts|tsx)$/.test(path))
  )
}

/**
 * import・package の行か。
 *
 * @param {string} line 行
 * @returns {boolean} import・package の行なら true
 */
function isImportLine(line) {
  return /^\s*(import|package)\s/.test(line)
}

/**
 * 名前の対応を、語の境目で当てる正規表現と新しい名前の並びにする（行ごとに正規表現を作らないため）。
 *
 * @param {string[][]} renames [旧, 新] の並び
 * @returns {[RegExp, string][]} [旧の名前の正規表現, 新] の並び
 */
function compileRenames(renames) {
  return renames.map(([from, to]) => [new RegExp(`(?<![\\w.])${RegExp.escape(from)}(?!\\w)`, 'g'), to])
}

/**
 * 行に名前の対応を当てる。
 *
 * @param {string} line 行
 * @param {[RegExp, string][]} renames compileRenames の結果
 * @returns {string} 名前を置き換えた行
 */
function applyRenames(line, renames) {
  return renames.reduce((result, [from, to]) => result.replace(from, to), line)
}

/**
 * 複数行の import の始まりの行か（`import {` や `import type {` で、行の終わりが `{`）。
 *
 * @param {string} line 行
 * @returns {boolean} 始まりの行なら true
 */
function startsImportBlock(line) {
  return /^\s*import\b.*\{\s*$/.test(line)
}

/**
 * 複数行の import の終わりの行か（`} from '…'`）。
 *
 * @param {string} line 行
 * @returns {boolean} 終わりの行なら true
 */
function endsImportBlock(line) {
  return /^\s*\}\s*from\s/.test(line)
}

/**
 * git diff -M の出力を、ファイルごとの消した行と追加した行に分ける。
 *
 * import・package の行（複数行の import の中の行を含む）は除く。複数行の import は、変更の前と後の
 * それぞれで、前後の行（差分の文脈の行を含む）から中にいるかを辿る。
 *
 * @param {string} diffText git diff -M の出力
 * @returns {{ path: string, oldPath: string, newFile: boolean, removed: { text: string, number: number }[], added: { text: string, number: number }[] }[]}
 *   ファイルごとの差分。path は変更の後のパス、oldPath は変更の前のパス。number は、消した行は変更の前の、追加した行は変更の後のファイルの行（1 から）
 */
function parseDiff(diffText) {
  const files = []
  let current = null
  let oldInImport = false
  let newInImport = false
  let oldNumber = 0
  let newNumber = 0
  for (const line of diffText.split(/\r?\n/)) {
    const header = line.match(/^diff --git a\/(.+) b\/(.+)$/)
    const hunk = line.match(/^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/)
    if (header) {
      current = { path: header[2], oldPath: header[1], newFile: false, removed: [], added: [] }
      files.push(current)
      oldInImport = false
      newInImport = false
    } else if (!current) {
      continue
    } else if (line.startsWith('new file mode')) {
      current.newFile = true
    } else if (hunk) {
      oldNumber = Number(hunk[1])
      newNumber = Number(hunk[2])
    } else if (line.startsWith('+++ ') || line.startsWith('--- ') || line.startsWith('@@')) {
      continue
    } else if (line.startsWith('+')) {
      const text = line.slice(1)
      const inImport = newInImport || startsImportBlock(text)
      newInImport = inImport && !endsImportBlock(text)
      if (!inImport && !isImportLine(text)) current.added.push({ text, number: newNumber })
      newNumber++
    } else if (line.startsWith('-')) {
      const text = line.slice(1)
      const inImport = oldInImport || startsImportBlock(text)
      oldInImport = inImport && !endsImportBlock(text)
      if (!inImport && !isImportLine(text)) current.removed.push({ text, number: oldNumber })
      oldNumber++
    } else if (line.startsWith(' ')) {
      const text = line.slice(1)
      const oldIn = oldInImport || startsImportBlock(text)
      const newIn = newInImport || startsImportBlock(text)
      oldInImport = oldIn && !endsImportBlock(text)
      newInImport = newIn && !endsImportBlock(text)
      oldNumber++
      newNumber++
    }
  }
  return files
}

/**
 * テストのファイルの差分のうち、名前の変更と移動で説明できない行を返す。
 *
 * 既存のファイル（消したファイルを含む）の消した行は、すべてのテストのファイル（新しく追加したファイルを含む）の
 * 追加した行と、前後の空白を除いて照合する（テストを中身を変えずに別のファイルへ移すことを認めるため）。
 * 照合して残った追加した行は、既存のファイルなら違反とし、新しく追加したファイルなら見ない（移した先の import と枠の行のため）。
 *
 * @param {string} diffText git diff -M の出力
 * @param {string[][]} renames [旧, 新] の並び
 * @returns {{ file: string, kind: 'removed' | 'added', line: string, location: string }[]} 説明できない行。
 *   location は「ファイル:行」（消した行は変更の前の、追加した行は変更の後のファイルと行）
 */
export function findViolations(diffText, renames) {
  const files = parseDiff(diffText).filter((file) => isTestPath(file.path))
  const added = files.flatMap((file) =>
    file.added.map(({ text, number }) => ({ file, line: text, number, key: text.trim(), used: false })),
  )
  const violations = []
  const compiled = compileRenames(renames)
  for (const file of files.filter((f) => !f.newFile)) {
    for (const { text: line, number } of file.removed) {
      const key = applyRenames(line, compiled).trim()
      // 同じファイルの行を先に当て、なければ他のファイルの行を当てる
      const match =
        added.find((entry) => !entry.used && entry.file === file && entry.key === key) ??
        added.find((entry) => !entry.used && entry.key === key)
      if (match) {
        match.used = true
      } else {
        violations.push({ file: file.path, kind: 'removed', line, location: `${file.oldPath}:${number}` })
      }
    }
  }
  for (const entry of added) {
    if (!entry.used && !entry.file.newFile) {
      violations.push({ file: entry.file.path, kind: 'added', line: entry.line, location: `${entry.file.path}:${entry.number}` })
    }
  }
  return violations
}

/** 元の commit からの差分を調べ、説明できない行があれば失敗する */
function main() {
  const [base, renamesPath] = process.argv.slice(2)
  if (!base) {
    console.error('使い方：node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]')
    process.exit(2)
  }
  let renamesText = ''
  if (renamesPath) {
    if (!existsSync(renamesPath)) {
      console.error(`名前の対応のファイルがない：${renamesPath}`)
      process.exit(2)
    }
    renamesText = readFileSync(renamesPath, 'utf8')
  }
  // 形の崩れた行を読み飛ばすと、名前を置き換えずに違反が増え、原因が対応のファイルだと分からないので、先に止める
  const renameProblems = findRenameProblems(renamesText)
  for (const { line, text } of renameProblems) console.error(`${renamesPath}:${line} 「旧=新」の形でない：${text}`)
  if (renameProblems.length) process.exit(2)
  const renames = parseRenames(renamesText)
  let diffText
  try {
    // 日本語を含むパスを、引用符と 8 進数の形にせずに読む
    diffText = execFileSync('git', ['-c', 'core.quotepath=false', 'diff', '-M', '-l0', '--no-color', base], {
      encoding: 'utf8',
      maxBuffer: 256 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch (e) {
    console.error(`git diff を実行できない（元の commit ${base} があるかを確認する）：${e.stderr?.toString().trim() || e.message}`)
    process.exit(2)
  }
  const violations = findViolations(diffText, renames)
  for (const { kind, line, location } of violations) {
    console.log(`${location} ${kind === 'added' ? '+' : '-'}${line}`)
  }
  console.log(`名前の変更と移動で説明できない行：${violations.length}`)
  process.exit(violations.length === 0 ? 0 : 1)
}

if (import.meta.main) {
  main()
}
