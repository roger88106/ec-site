#!/usr/bin/env node
// ============================================================================
// 既存のテストの変更が、名前の変更と移動だけであることを確かめる（spec 013 の DN-15）
//
//   使い方：node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]
//
// 元の commit から今の作業ツリーまでの、テストのファイル（バックエンドの src/test・
// src/integrationTest、画面の *.test.ts(x)、e2e/）の差分を見る。
// import・package の行を除き、消した行に名前の対応（旧=新）を当てた結果と、足した行が
// 一致しなければ、その行を出して終了コード 1 を返す。
// 新しく足したテストのファイルは見ない（既存のテストを変えないことを確かめる検査のため）。
//
// 名前の対応のファイル：1 行に「旧=新」。空行と # の行は飛ばす。名前は語の境目で当てる。
// ============================================================================
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

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

/** git diff -M の出力を、ファイルごとの消した行と足した行に分ける */
function parseDiff(diffText) {
  const files = []
  let current = null
  for (const line of diffText.split(/\r?\n/)) {
    const header = line.match(/^diff --git a\/(.+) b\/(.+)$/)
    if (header) {
      current = { path: header[2], newFile: false, removed: [], added: [] }
      files.push(current)
    } else if (!current) {
      continue
    } else if (line.startsWith('new file mode')) {
      current.newFile = true
    } else if (line.startsWith('+++ ') || line.startsWith('--- ')) {
      continue
    } else if (line.startsWith('+')) {
      current.added.push(line.slice(1))
    } else if (line.startsWith('-')) {
      current.removed.push(line.slice(1))
    }
  }
  return files
}

/** テストのファイルの差分のうち、名前の変更と移動で説明できない行を返す */
export function findViolations(diffText, renames) {
  const violations = []
  for (const file of parseDiff(diffText)) {
    if (file.newFile || !isTestPath(file.path)) continue
    const unmatched = file.added.filter((line) => !isImportLine(line))
    for (const line of file.removed.filter((l) => !isImportLine(l))) {
      const index = unmatched.indexOf(applyRenames(line, renames))
      if (index >= 0) {
        unmatched.splice(index, 1)
      } else {
        violations.push({ file: file.path, kind: 'removed', line })
      }
    }
    for (const line of unmatched) {
      violations.push({ file: file.path, kind: 'added', line })
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

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main()
}
