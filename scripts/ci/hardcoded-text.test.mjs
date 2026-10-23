import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { findHardcodedText, scanSources } from './hardcoded-text.mjs'

describe('表示の文言を直接書いた所の検査', () => {
  test('JSX の文字、属性、コードの文字列の日本語を見つける', () => {
    const text = [
      '<Button>保存</Button>',
      '<Input aria-label="キーワード" />',
      "toast.success('保存しました')",
      '<Button>{t("save")}</Button>',
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [1, 2, 3],
    )
  })

  test('コメントの日本語は見ない。文字列の中の // はコメントにしない', () => {
    const text = [
      '/** 保存のボタン */',
      '// 保存する',
      '{/* 確認してから送る */}',
      "const url = 'https://example.com' // 外のサイト",
      "const label = 'https://example.com/保存'",
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [5],
    )
  })

  test('複数行のコメントの後も、行番号は元のファイルの行と同じ', () => {
    const text = ['/*', ' * 説明', ' */', '{/* 注', '  続き */}', "const label = '保存'"].join('\n')
    assert.deepEqual(findHardcodedText(text), [{ line: 6, text: "const label = '保存'" }])
  })

  test('ログとプログラムの誤りの例外の文は見ない', () => {
    const text = [
      "await log.warn('カートを移せませんでした')",
      "throw new Error('環境変数がありません')",
      "throw new ImageUploadError('画像を送れませんでした')",
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [3],
    )
  })

  test('正規表現のリテラルの中の引用符は文字列の始まりにしない', () => {
    const text = [
      "const QUOTES = /[\"'「]/g // 引用符と鍵括弧",
      'const SLASHES = /\\/\\/[a-z/]+/ // 二重の斜線',
      "if (/^'/.test(value)) return // 引用符で始まる",
      "const label = '保存'",
      '{t.rich("at", { date: () => <DateTime value={at} /> }) /* 日時 */}',
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [4],
    )
  })

  test('割り算は正規表現にしない', () => {
    const text = [
      'const half = total / 2 // 半分にする',
      "const ratio = a / b; const label = '保存'",
      "const per = (sum) / count / 2; const unit = '個'",
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [2, 3],
    )
  })

  test('ファイルごとに、文言を直接書いた行を集める（見つからないファイルは含めない）', () => {
    const files = { 'a.tsx': 'const a = "日本語"', 'b.tsx': 'const b = 1' }
    const hits = scanSources(Object.keys(files), (file) => files[file])
    assert.deepEqual([...hits.keys()], ['a.tsx'])
    assert.equal(hits.get('a.tsx')[0].line, 1)
  })
})

/** Git の環境変数を外した環境（テストの中の git が、元のリポジトリを壊さないため） */
function cleanEnv() {
  return Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('GIT_')))
}

/** 画面のソースを 1 つ置いた一時の Git リポジトリで、検査を子プロセスとして実行する */
function runCheck(source, args = []) {
  const dir = mkdtempSync(path.join(tmpdir(), 'hardcoded-text-'))
  try {
    const env = cleanEnv()
    spawnSync('git', ['init', '-q'], { cwd: dir, env })
    const file = path.join(dir, 'frontend/store/src/Sample.tsx')
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, source)
    const script = fileURLToPath(new URL('./hardcoded-text.mjs', import.meta.url))
    return spawnSync(process.execPath, [script, ...args], { cwd: dir, env, encoding: 'utf8' })
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

describe('表示の文言の検査の実行（コマンドライン）', () => {
  test('違反があれば、違反の行と最後の要約の行を標準出力に出し、終了コード 1 にする', () => {
    const result = runCheck("const a = '保存'\n")
    assert.equal(result.status, 1)
    assert.equal(result.stderr, '')
    const lines = result.stdout.trim().split('\n')
    assert.match(lines[0], /^frontend\/store\/src\/Sample\.tsx:1 {2}const a = /)
    assert.match(lines.at(-1), /^表示の文言を直接書いた所：1、除外の誤り：0$/)
  })

  test('違反がなければ、最後の要約の行だけを出して終了コード 0 にする', () => {
    const result = runCheck('const a = 1\n')
    assert.equal(result.status, 0)
    assert.equal(result.stdout.trim(), '表示の文言を直接書いた所：0、除外の誤り：0')
  })

  test('--count は、ファイルごとの数と合計を出し、違反があっても終了コード 0 にする', () => {
    const result = runCheck("const a = '保存'\nconst b = '削除'\n", ['--count'])
    assert.equal(result.status, 0)
    const lines = result.stdout.trim().split('\n')
    assert.equal(lines[0], '2\tfrontend/store/src/Sample.tsx')
    assert.equal(lines.at(-1), '合計 2 行、1 ファイル')
  })
})
