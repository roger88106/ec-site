// ignored-tracked.mjs のテスト（node --test scripts/ci/config/ignored-tracked.test.mjs）
// 一時のディレクトリに Git のリポジトリを作り、除外の対象になる形とならない形を確認する。
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { removeGitEnv, withoutGitEnv } from '../../shared/git/git-env.mjs'
import { findIgnoredTracked, runIgnoredTracked } from './ignored-tracked.mjs'

/** 一時のリポジトリを作った場所 */
const roots = []

/** hook の中でも一時のリポジトリを指すように、GIT_ で始まる環境変数を外す */
const env = withoutGitEnv()

/** 検査の実行時の環境も、hook の GIT_DIR などを外す（検査が git を呼び出すときに、この環境を引き継ぐため） */
removeGitEnv()

/** 一時のリポジトリで git を実行する */
function git(dir, ...args) {
  return execFileSync('git', args, { cwd: dir, env, encoding: 'utf8' })
}

/** ファイルを書く（親のフォルダーも作る） */
function write(dir, file, content) {
  const full = path.join(dir, file)
  mkdirSync(path.dirname(full), { recursive: true })
  writeFileSync(full, content)
}

/**
 * 一時のリポジトリを作る。files のファイルを書き、`git add -f` で追跡する（除外の設定に関係なく追跡できる）。
 *
 * @param {Record<string, string>} files パスと内容
 * @param {string[]} [untracked] 追跡しないファイルのパス
 * @returns {string} リポジトリの場所
 */
function makeRepo(files, untracked = []) {
  const dir = mkdtempSync(path.join(tmpdir(), 'ignored-tracked-'))
  roots.push(dir)
  git(dir, 'init', '-q')
  for (const [file, content] of Object.entries(files)) {
    write(dir, file, content)
    git(dir, 'add', '-f', '--', file)
  }
  for (const file of untracked) write(dir, file, 'x\n')
  return dir
}

/**
 * commit の前の hook と同じ状態で検査する。ステージした内容を別のディレクトリに取り出し、
 * GIT_DIR を元のリポジトリ、GIT_WORK_TREE を取り出した場所にして、取り出した場所で実行する。
 *
 * @param {string} dir 元のリポジトリ
 * @returns {string[]} 除外された追跡しているファイル
 */
function findInHookForm(dir) {
  const staged = mkdtempSync(path.join(tmpdir(), 'ignored-tracked-staged-'))
  roots.push(staged)
  git(dir, 'checkout-index', '-a', `--prefix=${staged.replaceAll('\\', '/')}/`)
  process.env.GIT_DIR = path.join(dir, '.git')
  process.env.GIT_WORK_TREE = staged
  try {
    return findIgnoredTracked(staged)
  } finally {
    removeGitEnv()
  }
}

describe('除外された追跡しているファイルの検出', () => {
  after(() => {
    for (const dir of roots) rmSync(dir, { recursive: true, force: true })
  })

  describe('検出すべき例', () => {
    test('例 1：ルートの .gitignore が、追跡しているファイルを除外する', () => {
      const dir = makeRepo({ 'a.txt': 'a', 'b.txt': 'b', '.gitignore': 'a.txt\n' })
      assert.deepEqual(findIgnoredTracked(dir), ['a.txt'])
    })

    test('例 2：子のフォルダーの .gitignore が、追跡しているファイルを除外する', () => {
      const dir = makeRepo({ 'sub/a.txt': 'a', 'sub/.gitignore': 'a.txt\n', 'b.txt': 'b' })
      assert.deepEqual(findIgnoredTracked(dir), ['sub/a.txt'])
    })

    test('例 3：許可の行を消すと、戻していたファイルが除外になる', () => {
      const dir = makeRepo({ LICENSE: 'l', '.gitignore': '/*\n!/.gitignore\n!/LICENSE\n' })
      assert.deepEqual(findIgnoredTracked(dir), [])
      write(dir, '.gitignore', '/*\n!/.gitignore\n')
      assert.deepEqual(findIgnoredTracked(dir), ['LICENSE'])
    })

    test('例 4：追跡しているディレクトリを丸ごと除外すると、下の全てが対象になる', () => {
      const dir = makeRepo({ 'docs/a.md': 'a', 'docs/deep/b.md': 'b', 'c.md': 'c', '.gitignore': '/docs/\n' })
      assert.deepEqual(findIgnoredTracked(dir).sort(), ['docs/a.md', 'docs/deep/b.md'])
    })

    test('例 5：git add -f で加えた除外のファイルが対象になる', () => {
      const dir = makeRepo({ '.gitignore': '*.log\n' })
      write(dir, 'x.log', 'x')
      git(dir, 'add', '-f', 'x.log')
      assert.deepEqual(findIgnoredTracked(dir), ['x.log'])
    })

    test('例 6：名前に空白・日本語を含む追跡しているファイルが対象になる', () => {
      const dir = makeRepo({ '.gitignore': '/文書 1.md\n', '文書 1.md': 'x' })
      assert.deepEqual(findIgnoredTracked(dir), ['文書 1.md'])
    })

    test('例 7：親のディレクトリを除外した後に、下のファイルを ! で戻しても対象になる', () => {
      const dir = makeRepo({ 'backend/ecsite/build.gradle.kts': 'x', '.gitignore': '/backend/\n!/backend/ecsite/build.gradle.kts\n' })
      assert.deepEqual(findIgnoredTracked(dir), ['backend/ecsite/build.gradle.kts'])
    })

    test('例 8：許可の行を除外の行より前に置くと、最後に一致する行が除外する', () => {
      const dir = makeRepo({ LICENSE: 'l', '.gitignore': '!/LICENSE\n/*\n' })
      assert.deepEqual(findIgnoredTracked(dir).sort(), ['.gitignore', 'LICENSE'])
    })

    test('例 9：/ のない行が、深いフォルダーの追跡しているファイルに一致する', () => {
      const dir = makeRepo({ 'scripts/bin/run.sh': 'x', '.gitignore': 'bin/\n' })
      assert.deepEqual(findIgnoredTracked(dir), ['scripts/bin/run.sh'])
    })

    test('例 10：大文字と小文字だけが違う許可の行は、core.ignorecase が true でも一致させない', () => {
      const dir = makeRepo({ 'README.md': 'r', '.gitignore': '/*\n!/.gitignore\n!/readme.md\n' })
      git(dir, 'config', 'core.ignorecase', 'true')
      assert.deepEqual(findIgnoredTracked(dir), ['README.md'])
    })

    test('例 11：ステージした .gitignore が除外するとき、作業ツリーの .gitignore が元のままでも対象になる（commit の前の hook と同じ状態）', () => {
      const dir = makeRepo({ 'a.txt': 'a', '.gitignore': 'b.log\n' })
      write(dir, '.gitignore', 'a.txt\n')
      git(dir, 'add', '-f', '.gitignore')
      write(dir, '.gitignore', 'b.log\n')
      assert.deepEqual(findInHookForm(dir), ['a.txt'])
    })
  })

  describe('検出してはいけない例', () => {
    test('例 1：追跡していない除外のファイルは対象にしない', () => {
      const dir = makeRepo({ 'a.txt': 'a', '.gitignore': '/.local/\nbuild/\n' }, ['.local/note.md', 'build/out.jar'])
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 2：.git/info/exclude と core.excludesFile だけが除外するファイルは対象にしない', () => {
      const dir = makeRepo({ 'a.txt': 'a', 'b.txt': 'b', 'c.txt': 'c' })
      writeFileSync(path.join(dir, '.git', 'info', 'exclude'), 'a.txt\n')
      const global = path.join(dir, '.git', 'global-ignore')
      writeFileSync(global, 'b.txt\n')
      git(dir, 'config', 'core.excludesFile', global)
      // 前提：標準の読み方なら、この 2 つは除外として見える
      assert.deepEqual(git(dir, 'ls-files', '-i', '-c', '--exclude-standard').split('\n').filter(Boolean).sort(), ['a.txt', 'b.txt'])
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 3：除外の後に ! で戻した追跡しているファイルは対象にしない', () => {
      const dir = makeRepo({ 'docs/a.md': 'a', '.gitignore': '*.md\n!docs/a.md\n' })
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 4：追跡していない、除外されないファイルは対象にしない', () => {
      const dir = makeRepo({ 'a.txt': 'a', '.gitignore': '*.log\n' }, ['new.txt'])
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 5：/* で除外し、!/docs/ で戻したディレクトリの下の追跡しているファイルは対象にしない', () => {
      const dir = makeRepo({ 'docs/a.md': 'a', 'docs/deep/b.md': 'b', '.gitignore': '/*\n!/.gitignore\n!/docs/\n' })
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 6：許可の行の末尾の空白は無視される', () => {
      const dir = makeRepo({ LICENSE: 'l', '.gitignore': '/*\n!/.gitignore\n!/LICENSE   \n' })
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 7：git rm --cached で追跡をやめた除外のファイルは対象にしない', () => {
      const dir = makeRepo({ 'a.log': 'a', '.gitignore': '*.log\n' })
      assert.deepEqual(findIgnoredTracked(dir), ['a.log'])
      git(dir, 'rm', '-q', '--cached', 'a.log')
      assert.deepEqual(findIgnoredTracked(dir), [])
    })

    test('例 8：作業ツリーでだけ .gitignore を変えてステージしていないとき、ステージした内容で判定する（commit の前の hook と同じ状態）', () => {
      const dir = makeRepo({ 'a.txt': 'a', '.gitignore': 'b.log\n' })
      write(dir, '.gitignore', 'a.txt\n')
      assert.deepEqual(findInHookForm(dir), [])
    })
  })

  describe('出力と終了コード', () => {
    /** runIgnoredTracked を実行して、終了コードと出力の行を返す */
    function run(dir) {
      const lines = []
      const code = runIgnoredTracked(dir, (line) => lines.push(line))
      return { code, lines }
    }

    test('失敗の出力に、ファイルの一覧と、一致した行を調べるコマンドがある', () => {
      const dir = makeRepo({ 'a.txt': 'a', 'sub/b.txt': 'b', '.gitignore': '*.txt\n' })
      const { code, lines } = run(dir)
      const text = lines.join('\n')
      assert.equal(code, 1)
      assert.match(text, /a\.txt/)
      assert.match(text, /sub\/b\.txt/)
      assert.match(text, /git check-ignore -v --no-index <ファイル>/)
    })

    test('通るときは終了コード 0 で、最後の行が要約の行になる', () => {
      const dir = makeRepo({ 'a.txt': 'a', '.gitignore': '*.log\n' })
      const { code, lines } = run(dir)
      assert.equal(code, 0)
      assert.match(lines.at(-1), /^[^：]+：\d+ 件/)
    })

    test('失敗のときも、最後の行が要約の行になる', () => {
      const dir = makeRepo({ 'a.txt': 'a', '.gitignore': '*.txt\n' })
      assert.match(run(dir).lines.at(-1), /^[^：]+：\d+ 件/)
    })

    test('リポジトリの外など、git が失敗するときは、終了コード 2 で理由と要約の行を出す', () => {
      const outside = mkdtempSync(path.join(tmpdir(), 'ignored-tracked-outside-'))
      roots.push(outside)
      const saved = process.env.GIT_CEILING_DIRECTORIES
      process.env.GIT_CEILING_DIRECTORIES = path.dirname(outside)
      try {
        const { code, lines } = run(outside)
        assert.equal(code, 2)
        assert.ok(lines.length >= 2, lines.join('\n'))
        assert.match(lines.at(-1), /^[^：]+：\d+ 件/)
      } finally {
        if (saved === undefined) delete process.env.GIT_CEILING_DIRECTORIES
        else process.env.GIT_CEILING_DIRECTORIES = saved
      }
    })

    test('追跡しているファイルの一覧を取得できて、除外の判定の git が失敗したときは、終了コード 2 で、取得した件数を要約の行に出す', () => {
      const dir = makeRepo({ 'a.txt': 'a', 'b.txt': 'b' })
      const lines = []
      const code = runIgnoredTracked(dir, (line) => lines.push(line), {
        findIgnored: () => {
          throw new Error('git が失敗した')
        },
      })
      assert.equal(code, 2)
      assert.equal(lines.at(-1), '追跡しているファイル：2 件、除外されたもの：0 件')
    })

    test('追跡しているファイルが 0 件なら、終了コード 2 で理由を出す', () => {
      const dir = makeRepo({})
      const { code, lines } = run(dir)
      assert.equal(code, 2)
      assert.match(lines.join('\n'), /1 件も見つからない/)
    })
  })
})
