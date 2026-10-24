#!/usr/bin/env node
// ============================================================================
// 既存のテストの変更が、名前の変更と移動だけであることを確認する
//
//   使い方：node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]
//
// 元の commit から今の作業ツリーまでの、テストのファイル（バックエンドの src/test・
// src/integrationTest、画面の *.test.ts(x)、e2e/）の差分を見る。
// import・package の行（複数行の import を含む）を除き、既存のファイルの消した行に名前の対応（旧=新）を
// 当てた結果が、どこかのテストのファイルの追加した行と一致しなければ（前後の空白は見ない）、その行を出して
// 終了コード 1 を返す。既存のファイルの追加した行で、照合して残ったものも出す。
// 新しく追加したテストのファイルの、照合して残った行は見ない（既存のテストを変えないことを確認する検査のため）。
//
// 名前の対応のファイル：1 行に「旧=新」。空行と # の行は飛ばす。名前は語の境目で当てる。
// ============================================================================
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { isMain } from '../lib/is-main.mjs'

/** 名前の対応のファイルの中身を、[旧, 新] の並びにする */
export function parseRenames(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '' && !line.startsWith('#'))
    .map((line) => line.split('=').map((part) => part.trim()))
}

/** テストのファイルか */
function isTestPath(path) {
  return (
    /\/src\/(test|integrationTest)\//.test(path) ||
    path.startsWith('e2e/') ||
    (path.startsWith('frontend/') && /\.test\.(ts|tsx)$/.test(path))
  )
}

/** import・package の行か */
function isImportLine(line) {
  return /^\s*(import|package)\s/.test(line)
}

/** 正規表現の特別な文字を逃がす */
function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 行に名前の対応を当てる */
function applyRenames(line, renames) {
  return renames.reduce(
    (result, [from, to]) => result.replace(new RegExp(`(?<![\\w.])${escapeRegExp(from)}(?!\\w)`, 'g'), to),
    line,
  )
}

/** 複数行の import の始まりの行か（`import {` や `import type {` で、行の終わりが `{`） */
function startsImportBlock(line) {
  return /^\s*import\b.*\{\s*$/.test(line)
}

/** 複数行の import の終わりの行か（`} from '…'`） */
function endsImportBlock(line) {
  return /^\s*\}\s*from\s/.test(line)
}

/**
 * git diff -M の出力を、ファイルごとの消した行と追加した行に分ける。<br />
 * import・package の行（複数行の import の中の行を含む）は除く。複数行の import は、変更の前と後の
 * それぞれで、前後の行（差分の文脈の行を含む）から中にいるかを辿る。
 */
function parseDiff(diffText) {
  const files = []
  let current = null
  let oldInImport = false
  let newInImport = false
  for (const line of diffText.split(/\r?\n/)) {
    const header = line.match(/^diff --git a\/(.+) b\/(.+)$/)
    if (header) {
      current = { path: header[2], newFile: false, removed: [], added: [] }
      files.push(current)
      oldInImport = false
      newInImport = false
    } else if (!current) {
      continue
    } else if (line.startsWith('new file mode')) {
      current.newFile = true
    } else if (line.startsWith('+++ ') || line.startsWith('--- ') || line.startsWith('@@')) {
      continue
    } else if (line.startsWith('+')) {
      const text = line.slice(1)
      const inImport = newInImport || startsImportBlock(text)
      newInImport = inImport && !endsImportBlock(text)
      if (!inImport && !isImportLine(text)) current.added.push(text)
    } else if (line.startsWith('-')) {
      const text = line.slice(1)
      const inImport = oldInImport || startsImportBlock(text)
      oldInImport = inImport && !endsImportBlock(text)
      if (!inImport && !isImportLine(text)) current.removed.push(text)
    } else if (line.startsWith(' ')) {
      const text = line.slice(1)
      const oldIn = oldInImport || startsImportBlock(text)
      const newIn = newInImport || startsImportBlock(text)
      oldInImport = oldIn && !endsImportBlock(text)
      newInImport = newIn && !endsImportBlock(text)
    }
  }
  return files
}

/**
 * テストのファイルの差分のうち、名前の変更と移動で説明できない行を返す。<br />
 * 既存のファイル（消したファイルを含む）の消した行は、すべてのテストのファイル（新しく追加したファイルを含む）の
 * 追加した行と、前後の空白を除いて照合する（テストを中身を変えずに別のファイルへ移すことを認めるため）。
 * 照合して残った追加した行は、既存のファイルなら違反とし、新しく追加したファイルなら見ない（移した先の import と枠の行のため）。
 */
export function findViolations(diffText, renames) {
  const files = parseDiff(diffText).filter((file) => isTestPath(file.path))
  const added = files.flatMap((file) =>
    file.added.map((line) => ({ file, line, key: line.trim(), used: false })),
  )
  const violations = []
  for (const file of files.filter((f) => !f.newFile)) {
    for (const line of file.removed) {
      const key = applyRenames(line, renames).trim()
      // 同じファイルの行を先に当て、なければ他のファイルの行を当てる
      const match =
        added.find((entry) => !entry.used && entry.file === file && entry.key === key) ??
        added.find((entry) => !entry.used && entry.key === key)
      if (match) {
        match.used = true
      } else {
        violations.push({ file: file.path, kind: 'removed', line })
      }
    }
  }
  for (const entry of added) {
    if (!entry.used && !entry.file.newFile) {
      violations.push({ file: entry.file.path, kind: 'added', line: entry.line })
    }
  }
  return violations
}

function main() {
  const [base, renamesPath] = process.argv.slice(2)
  if (!base) {
    console.error('使い方：node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]')
    process.exit(2)
  }
  const renames = renamesPath ? parseRenames(readFileSync(renamesPath, 'utf8')) : []
  const diffText = execFileSync('git', ['diff', '-M', '-l0', '--no-color', base], {
    encoding: 'utf8',
    maxBuffer: 256 * 1024 * 1024,
  })
  const violations = findViolations(diffText, renames)
  for (const { file, kind, line } of violations) {
    console.log(`${file}: ${kind === 'added' ? '+' : '-'}${line}`)
  }
  console.log(`名前の変更と移動で説明できない行：${violations.length}`)
  process.exit(violations.length === 0 ? 0 : 1)
}

if (isMain(import.meta.url)) {
  main()
}
