// compare-targets.mjs のテスト。一時の git のリポジトリを作り、script を実行して出力を確認する
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../../scripts/shared/git/git-env.mjs'

const SCRIPT = fileURLToPath(new URL('./compare-targets.mjs', import.meta.url))
const env = withoutGitEnv()

const SERVICE = 'backend/ecsite/src/main/java/com/ecsite/module/payment/application/PaymentCommandService.java'
const PAGE = 'frontend/admin/src/features/product/pages/ProductEditPage.tsx'

/**
 * 一時のリポジトリを作り、ファイルを置いて commit する。
 *
 * @param {Record<string, string>[]} commits commit ごとの、パスと中身
 * @returns {string} リポジトリのパス
 */
function makeRepo(commits) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'compare-targets-'))
  const git = (...args) => execFileSync('git', args, { cwd: dir, env, encoding: 'utf8' })
  git('init', '-q')
  git('config', 'user.email', 't@example.com')
  git('config', 'user.name', 't')
  git('config', 'core.autocrlf', 'false')
  commits.forEach((files, index) => {
    for (const [file, text] of Object.entries(files)) {
      mkdirSync(path.join(dir, path.dirname(file)), { recursive: true })
      writeFileSync(path.join(dir, file), text)
    }
    git('add', '-A')
    git('commit', '-q', '-m', `c${index}`)
  })
  return dir
}

/**
 * script を実行する。
 *
 * @param {string} dir 実行する場所
 * @param {string[]} args 引数
 * @returns {{ status: number | null, stdout: string, stderr: string }} 結果
 */
function run(dir, args) {
  const result = spawnSync(process.execPath, [SCRIPT, ...args], { cwd: dir, env, encoding: 'utf8' })
  return { status: result.status, stdout: result.stdout, stderr: result.stderr }
}

/**
 * 出力から、見出しの後の箇条を、次の見出しまで読む。
 *
 * @param {string} out 出力
 * @param {string} target 対象の節の見出し（## の後）
 * @param {string} section 節の見出し（### の後）
 * @returns {string[]} 箇条の中身（「- 」を除く）
 */
function items(out, target, section) {
  const lines = out.split('\n')
  const start = lines.indexOf(`## ${target}`)
  assert.notEqual(start, -1, `## ${target} がない：\n${out}`)
  const head = lines.indexOf(`### ${section}`, start)
  assert.notEqual(head, -1, `### ${section} がない：\n${out}`)
  const result = []
  for (const line of lines.slice(head + 1)) {
    if (line.startsWith('#')) break
    if (line.startsWith('- ')) result.push(line.slice(2))
  }
  return result
}

test('同じ種類のファイル：末尾の語が同じものを、同じ層を優先して最大 3 つ、パスの順に出す', () => {
  const app = 'backend/ecsite/src/main/java/com/ecsite/module'
  const dir = makeRepo([{
    [SERVICE]: 'class PaymentCommandService {}\n',
    [`${app}/order/application/OrderService.java`]: 'x\n',
    [`${app}/cart/application/CartService.java`]: 'x\n',
    [`${app}/stock/domain/StockService.java`]: 'x\n',
    [`${app}/tax/application/TaxService.java`]: 'x\n',
    [`${app}/user/application/UserService.java`]: 'x\n',
    [`${app}/order/application/OrderRepository.java`]: 'x\n',
    [`${app}/order/application/OrderServiceTest.java`]: 'x\n',
  }])
  try {
    const out = run(dir, [SERVICE]).stdout
    assert.deepEqual(items(out, SERVICE, '同じ種類のファイル'), [
      `${app}/cart/application/CartService.java`,
      `${app}/order/application/OrderService.java`,
      `${app}/tax/application/TaxService.java`,
    ])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('同じ種類のファイル：同じ層が足りなければ、別の層のものを続ける', () => {
  const dir = makeRepo([{
    'src/a/application/AService.java': 'x\n',
    'src/b/domain/BService.java': 'x\n',
    'src/c/application/CService.java': 'x\n',
  }])
  try {
    const out = run(dir, ['src/a/application/AService.java']).stdout
    assert.deepEqual(items(out, 'src/a/application/AService.java', '同じ種類のファイル'), [
      'src/c/application/CService.java',
      'src/b/domain/BService.java',
    ])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('同じ種類のファイル：テストのファイルは、テストのファイルどうしで探す', () => {
  const dir = makeRepo([{
    'src/a/AServiceTest.java': 'x\n',
    'src/a/BServiceTest.java': 'x\n',
    'src/a/BService.java': 'x\n',
    'web/pages/APage.test.tsx': 'x\n',
    'web/pages/BPage.test.tsx': 'x\n',
    'web/pages/BPage.tsx': 'x\n',
  }])
  try {
    const out = run(dir, ['src/a/AServiceTest.java', 'web/pages/APage.test.tsx']).stdout
    assert.deepEqual(items(out, 'src/a/AServiceTest.java', '同じ種類のファイル'), ['src/a/BServiceTest.java'])
    assert.deepEqual(items(out, 'web/pages/APage.test.tsx', '同じ種類のファイル'), ['web/pages/BPage.test.tsx'])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('同じ種類のファイル：小文字の名前は、同じ拡張子のファイルを、近いフォルダーのものから出す', () => {
  const dir = makeRepo([{
    '.claude/skills/x/a.mjs': 'x\n',
    'scripts/ci/ja/ja-text.mjs': 'x\n',
    'scripts/ci/docs/doc-refs.mjs': 'x\n',
    'scripts/ci/lib/b.mjs': 'x\n',
    'scripts/shared/c.mjs': 'x\n',
    'scripts/ci/ja/ja-text.test.mjs': 'x\n',
  }])
  try {
    const out = run(dir, ['scripts/ci/ja/ja-text.mjs']).stdout
    assert.deepEqual(items(out, 'scripts/ci/ja/ja-text.mjs', '同じ種類のファイル'), [
      'scripts/ci/docs/doc-refs.mjs',
      'scripts/ci/lib/b.mjs',
      'scripts/shared/c.mjs',
    ])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('同じ種類のファイル：小文字の名前は、同じ親フォルダーの同じ拡張子のファイルを先にする', () => {
  const dir = makeRepo([{
    'scripts/ja/ja-text.mjs': 'x\n',
    'scripts/ja/ja-words.mjs': 'x\n',
    'scripts/ja/ja-text.test.mjs': 'x\n',
    'scripts/ja/readme.md': 'x\n',
    'scripts/other/other.mjs': 'x\n',
    'docs/payment.md': 'x\n',
    'docs/order.md': 'x\n',
    'docs/sub/stock.md': 'x\n',
  }])
  try {
    const out = run(dir, ['scripts/ja/ja-text.mjs', 'docs/payment.md']).stdout
    assert.deepEqual(items(out, 'scripts/ja/ja-text.mjs', '同じ種類のファイル'), ['scripts/ja/ja-words.mjs', 'scripts/other/other.mjs'])
    assert.deepEqual(items(out, 'docs/payment.md', '同じ種類のファイル'), ['docs/order.md', 'docs/sub/stock.md', 'scripts/ja/readme.md'])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('参照元：名前を単語として含むファイルを、自分を除いて出す', () => {
  const dir = makeRepo([{
    'src/Foo.java': 'class Foo {}\n',
    'src/UseFoo.java': 'new Foo()\n',
    'src/FooBar.java': 'class FooBar {}\n',
    'docs/foo.md': 'Foo を使う\n',
  }])
  try {
    const out = run(dir, ['src/Foo.java']).stdout
    assert.deepEqual(items(out, 'src/Foo.java', '参照元'), ['docs/foo.md', 'src/UseFoo.java'])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('参照元：アーカイブした spec は出さない', () => {
  const dir = makeRepo([{
    'src/Foo.java': 'class Foo {}\n',
    'docs/08-spec/archive/1to10/1-a/spec.md': 'Foo\n',
    'docs/08-spec/2-b/spec.md': 'Foo\n',
  }])
  try {
    assert.deepEqual(items(run(dir, ['src/Foo.java']).stdout, 'src/Foo.java', '参照元'), ['docs/08-spec/2-b/spec.md'])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('参照元：最大 10 で、11 個目は出さない', () => {
  const files = { 'src/Foo.java': 'class Foo {}\n' }
  for (let i = 10; i < 21; i++) files[`src/U${i}.java`] = 'Foo\n'
  const dir = makeRepo([files])
  try {
    const refs = items(run(dir, ['src/Foo.java']).stdout, 'src/Foo.java', '参照元')
    assert.equal(refs.length, 10)
    assert.equal(refs[9], 'src/U19.java')
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('参照元がなければ「なし」と出す', () => {
  const dir = makeRepo([{ 'src/Foo.java': 'class Foo {}\n', 'src/Bar.java': 'x\n' }])
  try {
    const out = run(dir, ['src/Foo.java']).stdout
    assert.deepEqual(items(out, 'src/Foo.java', '参照元'), [])
    assert.match(out, /### 参照元\n\nなし\n/)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('一緒に変わるファイル：2 回以上のものを、多い順に回数とともに出す', () => {
  const dir = makeRepo([
    { 'src/A.java': '1\n', 'src/B.java': '1\n', 'src/C.java': '1\n', 'src/D.java': '1\n' },
    { 'src/A.java': '2\n', 'src/B.java': '2\n', 'src/C.java': '2\n' },
    { 'src/A.java': '3\n', 'src/C.java': '3\n' },
    { 'src/B.java': '4\n', 'src/D.java': '4\n' },
  ])
  try {
    const out = run(dir, ['src/A.java']).stdout
    assert.deepEqual(items(out, 'src/A.java', '一緒に変わるファイル'), ['src/C.java（3 回）', 'src/B.java（2 回）'])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('一緒に変わるファイル：最大 5 で、回数が同じならパスの順', () => {
  const together = {}
  for (const name of ['B', 'C', 'D', 'E', 'F', 'G']) together[`src/${name}.java`] = '1\n'
  const second = Object.fromEntries(Object.keys(together).map((file) => [file, '2\n']))
  const dir = makeRepo([{ 'src/A.java': '1\n', ...together }, { 'src/A.java': '2\n', ...second }])
  try {
    const out = run(dir, ['src/A.java']).stdout
    assert.deepEqual(items(out, 'src/A.java', '一緒に変わるファイル'), ['B', 'C', 'D', 'E', 'F'].map((name) => `src/${name}.java（2 回）`))
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('共有の部品：フロントとバックエンドの対象があれば、それぞれの一覧を 1 回だけ出す', () => {
  const common = 'backend/ecsite/src/main/java/com/ecsite/common'
  const dir = makeRepo([{
    [SERVICE]: 'x\n',
    [PAGE]: 'x\n',
    'frontend/admin/src/features/product/pages/ProductListPage.tsx': 'x\n',
    'frontend/shared/src/ui/Button.tsx': 'x\n',
    'frontend/shared/src/lib/money.ts': 'x\n',
    'frontend/shared/src/lib/money.test.ts': 'x\n',
    'frontend/shared/package.json': '{}\n',
    [`${common}/web/ApiError.java`]: 'x\n',
    [`${common}/time/Clock.java`]: 'x\n',
  }])
  try {
    const out = run(dir, [SERVICE, PAGE, 'frontend/admin/src/features/product/pages/ProductListPage.tsx']).stdout
    assert.equal(out.match(/^## 共有の部品$/gm)?.length, 1)
    assert.deepEqual(items(out, '共有の部品', 'フロント（frontend/shared/）'), [
      'frontend/shared/src/lib/money.ts',
      'frontend/shared/src/ui/Button.tsx',
    ])
    assert.deepEqual(items(out, '共有の部品', `バックエンド（${common}/）`), [`${common}/time/Clock.java`, `${common}/web/ApiError.java`])
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('共有の部品：バックエンドだけの対象なら、フロントの一覧は出さない', () => {
  const dir = makeRepo([{ [SERVICE]: 'x\n', 'frontend/shared/src/ui/Button.tsx': 'x\n' }])
  try {
    const out = run(dir, [SERVICE]).stdout
    assert.doesNotMatch(out, /フロント（frontend\/shared\/）/)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('Windows の区切りと ./ で始まるパスも受け付ける', () => {
  const dir = makeRepo([{ 'src/A.java': 'x\n' }])
  try {
    const result = run(dir, ['.\\src\\A.java'])
    assert.equal(result.status, 0)
    assert.match(result.stdout, /^## src\/A\.java$/m)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('成功すると終了コード 0', () => {
  const dir = makeRepo([{ 'src/A.java': 'x\n' }])
  try {
    assert.equal(run(dir, ['src/A.java']).status, 0)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('引数がなければ、使い方を出して終了コード 2', () => {
  const dir = makeRepo([{ 'src/A.java': 'x\n' }])
  try {
    const result = run(dir, [])
    assert.equal(result.status, 2)
    assert.match(result.stderr, /使い方/)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})

test('追跡していないファイルは、そのパスを出して終了コード 2', () => {
  const dir = makeRepo([{ 'src/A.java': 'x\n' }])
  try {
    writeFileSync(path.join(dir, 'src/New.java'), 'x\n')
    const result = run(dir, ['src/A.java', 'src/New.java'])
    assert.equal(result.status, 2)
    assert.match(result.stderr, /src\/New\.java/)
    assert.equal(result.stdout, '')
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})
