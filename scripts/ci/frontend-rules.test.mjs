import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  duplicatedFiles,
  featureOf,
  findFireEvents,
  findIdentifierDescribes,
  findIndexedMultiples,
  findMarkupTextChecks,
  findPropertyComparisons,
  findTypeCasts,
  findWeakExistenceChecks,
  findCrossFeatureImports,
  findBuiltinShadowing,
  findButtonNameChecks,
  findCssClassChecks,
  findInternalSelectors,
  findLineThrough,
  findLocaleLiterals,
  findMissingTestSpacing,
  findNetworkIdleWaits,
  findPositionSelectors,
  findQueryUrlStrings,
  findTimeoutLiterals,
  findDefinitions,
  findExportedNames,
  groupTargets,
  isCommonFile,
  sharedPublicNames,
  timeoutTargets,
} from './frontend-rules.mjs'

describe('検査の対象のファイルの振り分け', () => {
  const store = 'frontend/store/src/features/cart/components/CartLine.tsx'
  const storeTest = 'frontend/store/src/features/cart/components/CartLine.test.tsx'
  const admin = 'frontend/admin/src/features/order/pages/OrderListPage.tsx'
  const adminTest = 'frontend/admin/src/features/order/pages/OrderListPage.test.tsx'
  const shared = 'frontend/shared/src/lib/format.ts'
  const sharedTest = 'frontend/shared/src/lib/format.test.ts'
  const support = 'frontend/store/src/test/render.tsx'
  const nestedSupport = 'frontend/admin/src/features/order/test/factory.ts'
  const e2e = 'e2e/src/cart.spec.ts'
  const supportTest = 'frontend/store/src/test/auth.test.ts'
  const others = ['frontend/store/package.json', 'frontend/store/src/styles.css', 'e2e/playwright.config.js', 'backend/ecsite/build.gradle']
  const all = [store, storeTest, admin, adminTest, shared, sharedTest, support, nestedSupport, e2e, ...others]

  test('本番のソースには、店舗画面・管理画面・共有のテストでないソースとテストの補助が入る', () => {
    assert.deepEqual(groupTargets(all).sources, [store, admin, shared, support, nestedSupport])
  })

  test('画面のテストには、店舗画面・管理画面・共有の単体・結合テストだけが入る', () => {
    assert.deepEqual(groupTargets(all).frontendTests, [storeTest, adminTest, sharedTest])
  })

  test('テストの補助には、src の下の test/ ディレクトリ（src/test/ と機能の中の test/）の下のファイルだけが入る', () => {
    assert.deepEqual(groupTargets(all).testSupport, [support, nestedSupport])
  })

  test('E2E には、e2e/ の下の TypeScript のファイルだけが入る', () => {
    assert.deepEqual(groupTargets(all).e2e, [e2e])
  })

  test('テストの種類のファイルには、画面のテストと E2E が入り、テストの補助は入らない', () => {
    assert.deepEqual(groupTargets(all).tests, [storeTest, adminTest, sharedTest, e2e])
  })

  test('テストと補助のファイルには、画面のテストとテストの補助が入り、E2E は入らない', () => {
    assert.deepEqual(groupTargets(all).testsWithSupport, [storeTest, adminTest, sharedTest, support, nestedSupport])
  })

  test('テストと補助と E2E のファイルには、重なるファイル（src/test/ の下の .test.ts）が 1 回だけ入る', () => {
    const grouped = groupTargets([...all, supportTest])
    assert.deepEqual(grouped.testsWithSupportAndE2e, [storeTest, adminTest, sharedTest, support, nestedSupport, e2e, supportTest])
    assert.equal(grouped.frontendTests.includes(supportTest) && grouped.testSupport.includes(supportTest), true)
  })

  test('ファイルの順を保ち、どのまとまりにも入らないファイルは捨てる', () => {
    assert.deepEqual(groupTargets([e2e, storeTest, store]).tests, [e2e, storeTest])
    assert.deepEqual(groupTargets(others), { sources: [], frontendTests: [], testSupport: [], e2e: [], tests: [], testsWithSupport: [], testsWithSupportAndE2e: [] })
  })
})

describe('フロントの書き方の検査', () => {
  test('機能のファイルの、ほかの機能の import を見つける', () => {
    const text = [
      "import { CartLine } from '@/features/cart/components/CartLine'",
      "import { formatPrice } from '@/lib/format'",
      "import { OrderSummary } from '@/features/order/components/OrderSummary'",
      "import { checkoutQuery } from '../checkoutQuery'",
    ].join('\n')
    assert.deepEqual(findCrossFeatureImports(text, 'cart').map((hit) => hit.line), [3])
    const commented = ["// moved from '@/features/orders'", "/* import { x } from '@/features/orders' */", "import { y } from '@/features/orders'"].join('\n')
    assert.deepEqual(findCrossFeatureImports(commented, 'cart').map((hit) => hit.line), [3])
  })

  test('相対パス、export from、import() の書き方でも、ほかの機能の import を見つける', () => {
    const file = 'frontend/store/src/features/cart/components/CartLine.tsx'
    const text = [
      "import { OrderSummary } from '../../order/components/OrderSummary'",
      "export { OrderSummary } from '../../order/components/OrderSummary'",
      "const Lazy = lazy(() => import('../../order/pages/OrderPage'))",
      "import '../../order/styles'",
      "import { checkoutQuery } from '../checkoutQuery'",
      "import { CartTotal } from '../../cart/components/CartTotal'",
      "import { formatPrice } from '../../../lib/format'",
      "export * from '@/features/order/index'",
      'import {',
      "  Foo,",
      "} from '../../order/foo'",
    ].join('\n')
    assert.deepEqual(findCrossFeatureImports(text, 'cart', file).map((hit) => hit.line), [1, 2, 3, 4, 8, 11])
  })

  test('機能のファイルかどうかと、その機能の名前を返す', () => {
    assert.deepEqual(featureOf('frontend/store/src/features/cart/components/CartLine.tsx'), { app: 'store', feature: 'cart' })
    assert.deepEqual(featureOf('frontend/admin/src/features/order/pages/OrderListPage.tsx'), { app: 'admin', feature: 'order' })
    assert.equal(featureOf('frontend/admin/src/lib/format.ts'), null)
  })

  test('Intl と toLocaleString に直接書いたロケールと、ロケール・タイムゾーンの文字を見つける', () => {
    const text = [
      "const yen = new Intl.NumberFormat('ja-JP')",
      'const yen2 = new Intl.NumberFormat(LOCALE)',
      "value.toLocaleString('ja-JP')",
      "const zone = 'Asia/Tokyo'",
      "const lang = \"ja\"",
      "const label = 'japan'",
    ].join('\n')
    assert.deepEqual(findLocaleLiterals(text).map((hit) => hit.line), [1, 3, 4, 5])
  })

  test('店舗画面と管理画面で、同じ相対パスに同じ内容のファイルを見つける。除外の一覧のパスは見ない', () => {
    const files = {
      'frontend/store/src/lib/utils.ts': 'export const cn = shared',
      'frontend/admin/src/lib/utils.ts': 'export const cn = shared',
      'frontend/store/src/lib/price.ts': 'export const price = 1',
      'frontend/admin/src/lib/price.ts': 'export const price = 1',
      'frontend/store/src/lib/date.ts': 'store',
      'frontend/admin/src/lib/date.ts': 'admin',
      'frontend/store/src/lib/only-store.ts': 'x',
    }
    const read = (file) => files[file]
    assert.deepEqual(duplicatedFiles(Object.keys(files), read, ['lib/utils.ts']), ['lib/price.ts'])
  })

  test('除外を外した同じ内容のファイルの一覧に、除外がまだ要るファイルが出る', () => {
    const files = {
      'frontend/store/src/lib/utils.ts': 'same',
      'frontend/admin/src/lib/utils.ts': 'same',
      'frontend/store/src/lib/price.ts': 'store',
      'frontend/admin/src/lib/price.ts': 'admin',
    }
    assert.deepEqual(duplicatedFiles(Object.keys(files), (file) => files[file], []), ['lib/utils.ts'])
  })

  test('両方の画面のファイルの一覧は、検査の実行で 1 回だけ求める（定義のほかに呼び出しが 1 つ）', () => {
    const source = readFileSync(fileURLToPath(new URL('./frontend-rules.mjs', import.meta.url)), 'utf8')
    const code = source.replace(/^\s*\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '')
    const calls = [...code.matchAll(/\bduplicatedFiles\(/g)].length - [...code.matchAll(/function duplicatedFiles\(/g)].length
    assert.equal(calls, 1)
  })
})

describe('ほかの機能の import の境界', () => {
  test('括弧の後ろの改行、from と指定子の間の改行を挟んだ import も、始まりの行で見つける', () => {
    const text = [
      'const a = await import(',
      "  '@/features/cart/x')",
      'import {',
      '  X,',
      '} from',
      "  '@/features/order/y'",
      "import { Z } from '@/features/order/z'",
    ].join('\n')
    assert.deepEqual(findCrossFeatureImports(text, 'order').map((hit) => hit.line), [1])
    assert.deepEqual(findCrossFeatureImports(text, 'cart').map((hit) => hit.line), [5, 7])
  })

  test('テンプレート文字列の動的 import は、${ を含まない固定の文字列だけを見る', () => {
    const text = ['import(`@/features/cart/x`)', 'import(`@/features/${name}/x`)'].join('\n')
    assert.deepEqual(findCrossFeatureImports(text, 'order').map((hit) => hit.line), [1])
  })
})

describe('店舗画面の書き方の規則', () => {
  test('クエリ付きの URL を文字列で組み立てる行を見つける', () => {
    const text = [
      "return page > 1 ? `/orders?page=${page}` : '/orders'",
      "const a = '/orders?page=' + page",
      "const b = '/orders' + '&page=' + page",
      'const c = `/products?sort=newest&page=${page}`',
      "const d = '/orders?page=1'",
      'const e = new URLSearchParams({ page: String(page) })',
      'const f = `/orders/${id}`',
    ].join('\n')
    assert.deepEqual(findQueryUrlStrings(text).map((hit) => hit.line), [1, 2, 3, 4, 5])
  })

  test('固定の文字列、連結、${} の名前のテンプレートでも、クエリ付きの URL を見つける', () => {
    const text = [
      "const a = '/x?a=1'",
      "const b = '/x?' + 'page=' + p",
      "const c = '/x?page=' + p",
      'const d = `${b}?${k}=${v}`',
      "const e = '/x&' + 'page=' + p",
      "const f = cond ? a : b",
      "const g = new URL('/x', base)",
      "g.searchParams.set('page', String(p))",
      "const h = a && b === c",
      "const i = '/x?'",
    ].join('\n')
    assert.deepEqual(findQueryUrlStrings(text).map((hit) => hit.line), [1, 2, 3, 4, 5])
  })

  test('コメントの中のクエリ付きの URL、line-through、CSS のクラスは違反にしない', () => {
    const text = [
      "// 例：'/orders?page=' + page",
      '/* const a = `/x?page=${p}`',
      " * line-through で .className を読む */",
      "const ok = 1 // expect(el).toHaveClass('a') と line-through",
    ].join('\n')
    assert.deepEqual(findQueryUrlStrings(text), [])
    assert.deepEqual(findLineThrough(text), [])
    assert.deepEqual(findCssClassChecks(text), [])
  })

  test('CSS のクラスの確認の書き方（属性、描画した HTML の class=、クラスのセレクター）を広く見つける', () => {
    const text = [
      "expect(el).toHaveAttribute('class', 'a')",
      "expect(html).toContain('<p class=\"line-through\">')",
      'expect(html).toMatch(/<p class="[^"]*line-through">/)',
      "await page.locator('.cart-line').click()",
      "document.querySelector('.a')",
      "document.querySelectorAll('div.a')",
      "await page.$('.a')",
      "await page.$$('.a')",
      "await page.locator('button.primary').click()",
      "await page.locator('[data-testid=\"x\"]').click()",
      "await page.locator('a[href=\"/x.html\"]').click()",
      "await page.locator('text=a.b').click()",
      "expect(el).toHaveAttribute('aria-pressed', 'true')",
      '<div className="a" />',
    ].join('\n')
    assert.deepEqual(findCssClassChecks(text).map((hit) => hit.line), [1, 2, 3, 4, 5, 6, 7, 8, 9])
  })

  test('CSS のクラスを確認する行を見つける', () => {
    const text = [
      "expect(button).toHaveClass('active')",
      "expect(el.className).toContain('a')",
      "el.classList.contains('a')",
      "el.getAttribute('class')",
      "expect(button).toHaveAttribute('aria-pressed', 'true')",
    ].join('\n')
    assert.deepEqual(findCssClassChecks(text).map((hit) => hit.line), [1, 2, 3, 4])
  })

  test('コンポーネントの内部の属性（data-slot）と querySelector で探す行を見つける', () => {
    const text = [
      `const card = container.querySelector('[data-slot="card"]')`,
      `container.querySelectorAll('li')`,
      `screen.getByText('a').closest('[data-slot="field"]')`,
      `expect(el).toHaveAttribute('data-slot', 'card')`,
      `document.querySelector('#a')`,
      `const el = screen.getByRole('button', { name: '保存' })`,
      `expect(el).toHaveAttribute('aria-pressed', 'true')`,
      `// container.querySelector('[data-slot]')`,
    ].join('\n')
    assert.deepEqual(findInternalSelectors(text).map((hit) => hit.line), [1, 2, 3, 4, 5])
  })

  test('line-through の直書きの行を見つける', () => {
    const text = ['<span className="line-through">x</span>', '<s>x</s>'].join('\n')
    assert.deepEqual(findLineThrough(text).map((hit) => hit.line), [1])
  })

  test('actions・lib・api のファイルから features への import を、相対パスも含めて見つける', () => {
    const file = 'frontend/store/src/actions/cart.ts'
    const text = [
      "import { x } from '@/features/cart/x'",
      "import { y } from '../features/order/y'",
      "import { z } from '@/lib/z'",
    ].join('\n')
    assert.deepEqual(findCrossFeatureImports(text, null, file).map((hit) => hit.line), [1, 2])
  })

  test('店舗画面と管理画面の actions・lib・api のファイルだけを共通の置き場所とする', () => {
    assert.equal(isCommonFile('frontend/store/src/actions/cart.ts'), true)
    assert.equal(isCommonFile('frontend/admin/src/lib/format.ts'), true)
    assert.equal(isCommonFile('frontend/admin/src/api/client.ts'), true)
    assert.equal(isCommonFile('frontend/shared/src/lib/locale.ts'), false)
    assert.equal(isCommonFile('frontend/store/src/features/cart/lib/x.ts'), false)
  })
})

describe('共有のパッケージと同じ名前の定義', () => {
  test('export の書き方から、公開する名前を読む（型は除く）', () => {
    const text = [
      'export function formatYen(amount: number): string {',
      'export async function load() {',
      "export const LOCALE = 'ja-JP'",
      'export class Foo {}',
      "export { cn } from 'cn'",
      'export { Button, buttonVariants }',
      'export { Inner as Outer, type Skip }',
      'export type Node = { id: string }',
      'export default function Page() {}',
      'const hidden = 1',
    ].join('\n')
    assert.deepEqual(findExportedNames(text), ['formatYen', 'load', 'LOCALE', 'Foo', 'cn', 'Button', 'buttonVariants', 'Outer'])
  })

  test('package.json の exports の先のファイルだけから、公開する名前を集める', () => {
    const exportsMap = { './lib/*': './src/lib/*.ts', './ui/*': './src/ui/*.tsx', './theme.css': './src/theme.css' }
    const files = [
      'frontend/shared/src/lib/format.ts',
      'frontend/shared/src/lib/format.test.ts',
      'frontend/shared/src/ui/button.tsx',
      'frontend/shared/src/internal/secret.ts',
      'frontend/shared/src/theme.css',
    ]
    const sources = {
      'frontend/shared/src/lib/format.ts': 'export function formatYen() {}',
      'frontend/shared/src/lib/format.test.ts': 'export const fromTest = 1',
      'frontend/shared/src/ui/button.tsx': 'export { Button }',
      'frontend/shared/src/internal/secret.ts': 'export const secret = 1',
    }
    assert.deepEqual([...sharedPublicNames(exportsMap, files, (file) => sources[file])].sort(), ['Button', 'formatYen'])
  })

  test('src の外の .test.ts も、公開する名前に入れない', () => {
    const exportsMap = { './lib/*': './lib/*.ts' }
    const files = ['frontend/shared/lib/format.ts', 'frontend/shared/lib/format.test.ts']
    const sources = {
      'frontend/shared/lib/format.ts': 'export function formatYen() {}',
      'frontend/shared/lib/format.test.ts': 'export const fromTest = 1',
    }
    assert.deepEqual([...sharedPublicNames(exportsMap, files, (file) => sources[file])], ['formatYen'])
  })

  test('公開する名前が 0 件のときは、通さずに失敗にする', () => {
    const files = ['frontend/shared/src/lib/format.ts']
    const read = () => 'const hidden = 1'
    assert.throws(() => sharedPublicNames({ './lib/*': './src/lib/*.ts' }, files, read), /公開する名前/)
    assert.throws(() => sharedPublicNames({}, files, read), /公開する名前/)
  })

  test('同じ名前の関数・定数・クラスの定義を、export の有無によらず見つける', () => {
    const text = [
      'export const translate = createTranslator()',
      'function formatYen(amount) {}',
      'async function load() {}',
      'class Button {}',
      'const other = 1',
      '  const translate = 2',
      'const translated = 3',
      "import { translate } from 'x'",
    ].join('\n')
    const names = new Set(['translate', 'formatYen', 'load', 'Button'])
    assert.deepEqual(findDefinitions(text, names).map((hit) => [hit.line, hit.name]), [
      [1, 'translate'],
      [2, 'formatYen'],
      [3, 'load'],
      [4, 'Button'],
    ])
  })
})

describe('テストの書き方の検査', () => {
  const lines = (hits) => hits.map((hit) => hit.line)

  test('理由のコメントのない型を回避する変換を見つける（同じ行か直前の行のコメントは理由とみなす）', () => {
    const text = [
      'const a = value as never',
      'const b = value as unknown as Foo // ライブラリの型が合わない',
      '// ライブラリの型が合わない',
      'const c = value as unknown as Foo',
      'const d = value as never /* 理由 */',
      'const e = value as unknown as Foo',
      "const f = 'as never'",
      '// value as never',
      'const g = value as Foo',
    ].join('\n')
    assert.deepEqual(lines(findTypeCasts(text)), [1, 6])
  })

  test('改行をまたぐ型の変換も見つける（開始の行の直前のコメントか、またぐ行のコメントは理由）', () => {
    const text = [
      'const a = value as',
      '  never',
      'const b = value as unknown',
      '  as Foo',
      '// 理由',
      'const c = value as unknown',
      '  as Foo',
      'const d = value as',
      '  never // 理由',
    ].join('\n')
    assert.deepEqual(lines(findTypeCasts(text)), [1, 3])
  })

  test('同じ行に正規表現のリテラルがあっても、理由のコメントがなければ型の回避を見つける', () => {
    const text = [
      'foo(/x/, y as never)',
      'foo(/x/, y as never) // 理由',
      '// 理由',
      'foo(/x/, y as unknown as Z)',
      'const w = /a/.test(s) ? (v as never) : 1',
    ].join('\n')
    assert.deepEqual(lines(findTypeCasts(text)), [1, 5])
  })

  test('toBeTruthy と toBeDefined は、名前と括弧の間に空白があっても見つける', () => {
    const text = ['expect(el).toBeTruthy ()', 'expect(el).toBeDefined  ()', 'expect(el).toBeInTheDocument ()'].join('\n')
    assert.deepEqual(lines(findWeakExistenceChecks(text)), [1, 2])
  })

  test('toBeTruthy と toBeDefined の呼び出しを見つける（コメントと文字列の中は数えない）', () => {
    const text = [
      'expect(el).toBeTruthy()',
      'expect.poll(() => x)',
      '  .toBeDefined()',
      "test('toBeTruthy() で確かめない', () => {})",
      '// expect(el).toBeDefined()',
      'expect(el).toBeInTheDocument()',
    ].join('\n')
    assert.deepEqual(lines(findWeakExistenceChecks(text)), [1, 3])
  })

  test('getAllBy・findAllBy・queryAllBy の結果を、並びのインデックスで選ぶ所を見つける', () => {
    const text = [
      "screen.getAllByRole('link')[0]",
      "(await screen.findAllByText('a')).at(1)",
      "within(x).queryAllByRole('row')[2]",
      "screen.getAllByRole('link').at(-1)",
      "expect(screen.getAllByRole('link')).toHaveLength(2)",
      "screen.getAllByRole('row').slice(1)",
      "const items = screen.getAllByRole('listitem')",
      'expect(items[0]).toHaveTextContent(/a/)',
      "const [first] = screen.getAllByRole('link')",
      "const rows = screen.getAllByRole('row').map((row) => row)",
      'rows[0]',
      "// screen.getAllByRole('link')[0]",
    ].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(text)), [1, 2, 3, 4, 8, 9])
  })

  test('変数に入れる形（within、型の変換、改行、スプレッド、Array.from、型の注釈、あとの代入）も見つける', () => {
    const cases = [
      ["const rows = within(table).getAllByRole('row')", 'rows[0]'],
      ["const rows = screen.getAllByRole('row') as HTMLElement[]", 'rows[0]'],
      ["const rows = (await screen.findAllByRole('row')) as unknown as HTMLElement[]", 'rows.at(1)'],
      ['const rows =', "  screen.getAllByRole('row')", 'rows[1]'],
      ["const rows = [...screen.getAllByRole('row')]", 'rows[0]'],
      ["const rows = Array.from(screen.getAllByRole('row'))", 'rows[0]'],
      ["const rows: HTMLElement[] = screen.getAllByRole('row')", 'rows[0]'],
      ['let rows', "rows = screen.getAllByRole('row')", 'rows[0]'],
    ]
    for (const parts of cases) {
      assert.deepEqual(lines(findIndexedMultiples(parts.join('\n'))), [parts.length], parts.join(' / '))
    }
    assert.deepEqual(lines(findIndexedMultiples(["const rows = screen.getAllByRole('row')", 'rows[0]', ''].join('\r\n'))), [2])
  })

  test('配列の分割代入は、スプレッドと Array.from、改行を含む形でも見つける', () => {
    const cases = ["const [a] = [...screen.getAllByRole('row')]", "const [a] = Array.from(screen.getAllByRole('row'))", "const [a] = (await screen.findAllByRole('row')) as HTMLElement[]"]
    for (const text of cases) assert.deepEqual(lines(findIndexedMultiples(text)), [1], text)
    assert.deepEqual(lines(findIndexedMultiples("const [a, b] =\n  within(x).getAllByRole('row')")), [2])
  })

  test('変数に入れる前に変換するもの、関数の引数に渡すものは見つけない', () => {
    const text = [
      "const names = screen.getAllByRole('row').map((row) => row)",
      'names[0]',
      "const sorted = sortRows(screen.getAllByRole('row'))",
      'sorted[0]',
      "const first = [...screen.getAllByRole('row')].filter(Boolean)",
      'first[0]',
    ].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(text)), [])
  })

  test('同じ名前の別の変数は、別のテストや別の宣言の範囲では見つけない', () => {
    const text = [
      "test('a', () => {",
      "  const items = screen.getAllByRole('row')",
      '  expect(items).toHaveLength(2)',
      '})',
      "test('b', () => {",
      '  const items = [1, 2]',
      '  expect(items[0]).toBe(1)',
      '})',
      'const items = [3]',
      'items[0]',
    ].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(text)), [])
    const shadowed = ["const rows = screen.getAllByRole('row')", 'rows[0]', 'function f(rows) { return rows[1] }', 'const g = (rows: string[]) => rows[2]'].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(shadowed)), [2])
    const arrow = ["const rows = screen.getAllByRole('row')", 'rows[0]', 'const g = (rows: string[]) => rows[2]'].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(arrow)), [2])
    const afterBlock = ['{', "  const rows = screen.getAllByRole('row')", '  rows[0]', '}', 'rows[1]'].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(afterBlock)), [3])
  })

  test('描画した HTML の文字を、要素や属性の並びで確かめる所を見つける（空の描画の確認は除く）', () => {
    const text = [
      "expect(html).toContain('<a href=\"/x\">')",
      'expect(html).toMatch(/<ul[^>]*>/)',
      "expect(html).toContain('href=\"/cart\"')",
      "expect(html).toMatch(/aria-label=\"x\"/)",
      "expect(renderMarkup(<Foo />)).toBe('')",
      "expect(text).toContain('カートに入れる')",
      "expect(header).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/)",
      "expect(query).toContain('a < b')",
      "expect(html).toBe('<p>x</p>')",
      "// expect(html).toContain('<a>')",
    ].join('\n')
    assert.deepEqual(lines(findMarkupTextChecks(text)), [1, 2, 3, 4, 9])
  })

  test('型引数の < は HTML のタグと読まない（期待値の文字列・テンプレート・正規表現の中だけ見る）', () => {
    const text = [
      'expect(new Map<string, number>()).toEqual(expected)',
      'expect(list).toEqual(Array<string>())',
      'expect(value).toBe(fn<A>(1))',
      "expect(html).toBe(`<p>${x}</p>`)",
      'expect(items).toStrictEqual<Foo[]>([])',
    ].join('\n')
    assert.deepEqual(lines(findMarkupTextChecks(text)), [4])
  })

  test('識別子だけの describe の名前を見つける（修飾と each も読む）', () => {
    const text = [
      "describe('ProductCard', () => {})",
      "describe('商品カード', () => {})",
      "describe('formatYen()', () => {})",
      "describe('<OrderForm />', () => {})",
      "describe.each(cases)('%s', () => {})",
      "describe.each([['a', 1]])('lib/format', () => {})",
      'describe.skip(`useCart`, () => {})',
      "describe('日本時間の整形', () => {})",
      "describe('shows the total', () => {})",
      "// describe('Commented', () => {})",
      "test('describe(\"InString\")', () => {})",
    ].join('\n')
    assert.deepEqual(lines(findIdentifierDescribes(text)), [1, 3, 4, 6, 7])
  })

  test('describe.each の置き換えの記号（$名前、%s など）を含む名前は、識別子だけの名前にしない', () => {
    const text = [
      "describe.each(cases)('$name', () => {})",
      "describe.each(cases)('%s', () => {})",
      "describe.each(cases)('%d', () => {})",
      "describe.each(cases)('$a.b', () => {})",
      "describe.each(cases)('plain', () => {})",
      "describe('$store', () => {})",
    ].join('\n')
    assert.deepEqual(lines(findIdentifierDescribes(text)), [5, 6])
  })

  test('.disabled・.checked・.value・getAttribute を toBe・toEqual で比べる所を見つける', () => {
    const text = [
      "const button = screen.getByRole('button')",
      "const input = await screen.findByRole('textbox')",
      "const link = within(nav).getByRole('link')",
      "const box = screen.queryByRole('checkbox')",
      'expect(button.disabled).toBe(true)',
      'expect((input as HTMLInputElement).value).toEqual(\'a\')',
      "expect(link.getAttribute('href')).toBe('/x')",
      'expect(box.checked).not.toBe(true)',
      'expect(button).toBeDisabled()',
      'expect(input).toHaveValue(\'a\')',
      'expect(parts.map((part) => part.value)).toEqual([1])',
      "expect(link.getAttribute('href')).toBeNull()",
      'expect(button.textContent).toBe(\'a\')',
      'expect(\n  input.value,\n).toBe(\'a\')',
      '// expect(a.disabled).toBe(true)',
    ].join('\n')
    assert.deepEqual(lines(findPropertyComparisons(text)), [5, 6, 7, 8, 14])
  })

  test('受け手が要素のものだけを見つける（screen・within・document・container からたどる形、要素の型の変数）', () => {
    const hits = [
      "expect(screen.getByRole('button').disabled).toBe(true)",
      "expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('a')",
      "expect(within(row).getByRole('checkbox').checked).toBe(true)",
      "expect(document.getElementById('a').value).toBe('a')",
      "expect(container.querySelector('input').value).toBe('a')",
      "function check(input: HTMLInputElement) {\n  expect(input.value).toBe('a')\n}",
      "const rows = screen.getAllByRole('row')\nexpect(rows[0].getAttribute('x')).toBe('a')",
      "const field = screen.getByLabelText('a') as HTMLInputElement\nexpect(field.value).toBe('a')",
    ]
    for (const text of hits) assert.equal(findPropertyComparisons(text).length, 1, text)
    const misses = [
      'const { result } = renderHook(() => useX())\nexpect(result.current.value).toBe(1)',
      'const counter = createCounter()\nexpect(counter.value).toBe(1)',
      'expect(item.value).toBe(1)',
      "const input = screen.getByRole('textbox')\nconst input2 = { value: 1 }\nexpect(input2.value).toBe(1)",
      "const state = useStore()\nexpect(state.disabled).toEqual(false)",
    ]
    for (const text of misses) assert.deepEqual(findPropertyComparisons(text), [], text)
  })

  test('理由のコメントのない fireEvent の使用を見つける（import とコメントと文字列の中は除く。同じ行か直前のコメントだけの行は理由）', () => {
    const text = [
      "import { fireEvent } from '@testing-library/react'",
      'import {',
      '  fireEvent,',
      "} from '@testing-library/react'",
      '',
      '// fireEvent は使わない',
      'export function typeDateTime(input: HTMLElement, value: string): void {',
      '  fireEvent.change(input, { target: { value } })',
      '}',
      '',
      'export const returnToTab = (): void => {',
      '  // 対応する userEvent の操作がない',
      "  fireEvent(window, new Event('visibilitychange'))",
      '}',
      "test('fireEvent で起こさない', () => {})",
      'fireEvent.click(x) // 理由',
      '',
      'fireEvent.click(y)',
    ].join('\n')
    assert.deepEqual(findFireEvents(text).map((hit) => hit.line), [8, 18])
  })

  test('同じ行に正規表現のリテラルがあっても、理由のコメントがなければ fireEvent を見つける', () => {
    const text = [
      "fireEvent.click(screen.getByRole('button', { name: /送信/ }))",
      "fireEvent.click(screen.getByRole('button', { name: /送信/ })) // 理由",
      '// 理由',
      'fireEvent.change(screen.getByLabelText(/名前/), { target: { value: 1 } })',
      'if (/a/.test(s)) fireEvent.click(b)',
    ].join('\n')
    assert.deepEqual(findFireEvents(text).map((hit) => hit.line), [1, 5])
  })

  test('fireEvent を別名で import した呼び出しと、名前空間の import の呼び出しも見つける', () => {
    const alias = ["import { fireEvent as fe, render } from '@testing-library/react'", 'fe.click(x)', 'fe(window, new Event("a"))', 'const fee = 1'].join('\n')
    assert.deepEqual(findFireEvents(alias).map((hit) => hit.line), [2, 3])
    const namespace = ["import * as rtl from '@testing-library/react'", 'rtl.fireEvent.click(x)', 'rtl.render(y)'].join('\n')
    assert.deepEqual(findFireEvents(namespace).map((hit) => hit.line), [2])
    const crlf = "import { fireEvent as fe } from 'x'\r\n// 理由\r\nfe.click(a)\r\nfe.click(b)\r\n"
    assert.deepEqual(findFireEvents(crlf).map((hit) => hit.line), [4])
  })
})

describe('E2E の書き方の検査', () => {
  const lines = (hits) => hits.map((hit) => hit.line)

  test('位置で選ぶメソッド（.first()・.nth()・.last()）を見つける（?. と空白と改行を含む。コメントと文字列の中は除く）', () => {
    const text = [
      "await page.getByRole('row').first().click()",
      "await page.getByRole('row').nth(2).click()",
      "await page.getByRole('row').last().click()",
      "await page.getByRole('row')?.first ( ).click()",
      "await page.getByRole('row')",
      '  .nth(',
      '    0).click()',
      "// page.getByRole('row').first()",
      "const message = '.first() は使わない'",
      "await page.getByRole('row', { name: 'a', exact: true }).click()",
      'const firstName = first',
    ].join('\n')
    assert.deepEqual(lines(findPositionSelectors(text)), [1, 2, 3, 4, 6])
  })

  test('locator の文字列のセレクターの中の位置（nth=、:nth-match(、:nth-child(、:first-child、:last-child）を見つける', () => {
    const text = [
      "await page.locator('tr >> nth=0').click()",
      'await page.locator("li:nth-match(:visible, 2)").click()',
      "await page.locator('ul > li:nth-child(2)').click()",
      "await page.locator('li:first-child').click()",
      "await page.locator('li:last-child').click()",
      "await page.getByRole('row').locator('td:first-child').click()",
      "await page.locator('table', { has: page.locator('li:nth-child(3)') }).click()",
      "await page.locator('li').click()",
      "test('li:first-child は使わない', () => {})",
      "// page.locator('li:first-child')",
      "const message = 'li:last-child'",
    ].join('\n')
    assert.deepEqual(lines(findPositionSelectors(text)), [1, 2, 3, 4, 5, 6, 7])
  })

  test('networkidle で待つ箇所を見つける（コメントの中は除く）', () => {
    const text = [
      "await page.goto(url, { waitUntil: 'networkidle' })",
      'await page.waitForLoadState("networkidle")',
      '// networkidle では待たない',
      "await page.goto(url, { waitUntil: 'load' })",
    ].join('\n')
    assert.deepEqual(lines(findNetworkIdleWaits(text)), [1, 2])
  })

  test('ボタンを文字列の名前で探すとき、exact: true がない箇所を見つける（連鎖の途中、複数の行、変数、テンプレート文字列を含む）', () => {
    const text = [
      "await page.getByRole('button', { name: '保存' }).click()",
      "await row.getByRole('button', { name: status }).click()",
      'await page.getByRole("button", {',
      '  name: `出荷 ${id}`,',
      '}).click()',
      "await page.getByRole('button', { name: '保存', exact: false }).click()",
      "await page.getByRole('button', { name }).click()",
      "await page.getByRole('button', { exact: true, name: '保存' }).click()",
      "await page.getByRole('button', { name: '保存', exact: true }).click()",
      "await page.getByRole('button', { name: `出荷 ${id}`, exact: true }).click()",
      "await page.getByRole('button', { name: status, exact: true }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 3, 6, 7])
  })

  test('ボタンを正規表現の名前で探すとき、先頭の ^ と末尾の $ がなければ見つける（フラグ、カンマや括弧を含む正規表現でも読む）', () => {
    const text = [
      "await page.getByRole('button', { name: /注文する/ }).click()",
      "await page.getByRole('button', { name: /^注文する/ }).click()",
      "await page.getByRole('button', { name: /注文する$/ }).click()",
      "await page.getByRole('button', { name: /^注文する（[\\d,]+ 円）$/ }).click()",
      "await page.getByRole('button', { name: /^保存$/i }).click()",
      "await page.getByRole('button', { name: /^a, b$/, exact: true }).click()",
      "await page.getByRole('button', { name: /a, b/, exact: true }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 3, 7])
  })

  test('name のキーを引用符や [...] で書いても、name として読む', () => {
    const text = [
      "await page.getByRole('button', { 'name': '保存' }).click()",
      'await page.getByRole("button", { "name": 保存 }).click()',
      "await page.getByRole('button', { ['name']: '保存' }).click()",
      "await page.getByRole('button', { 'name': '保存', exact: true }).click()",
      "await page.getByRole('button', { 'name': /^保存$/ }).click()",
      "await page.getByRole('button', { ['name']: /保存/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 3, 6])
  })

  test('正規表現の最上位に | があれば固定していないとみなす。グループ・文字クラス・エスケープの中の | は固定', () => {
    const text = [
      "await page.getByRole('button', { name: /^a|b$/ }).click()",
      "await page.getByRole('button', { name: /^(?:a|b)$/ }).click()",
      "await page.getByRole('button', { name: /^[a|b]$/ }).click()",
      "await page.getByRole('button', { name: /^a\\|b$/ }).click()",
      "await page.getByRole('button', { name: /^(a|b)c|d$/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 5])
  })

  test('末尾の $ がエスケープされていれば固定していないとみなす。\\ の後の $ は固定', () => {
    const text = [
      "await page.getByRole('button', { name: /^合計\\$/ }).click()",
      "await page.getByRole('button', { name: /^a\\\\$/ }).click()",
      "await page.getByRole('button', { name: /^a\\\\\\$/ }).click()",
      "await page.getByRole('button', { name: /^合計\\$$/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 3])
  })

  test('オプションに展開（...）を含み、name が読めないときは見つける。name が読めれば展開があっても判定する', () => {
    const text = [
      "await page.getByRole('button', { ...opts }).click()",
      "await page.getByRole('button', { ...opts, exact: true }).click()",
      "await page.getByRole('button', { ...opts, name: '保存', exact: true }).click()",
      "await page.getByRole('button', { name: '保存', ...opts }).click()",
      "await page.getByRole('button', { ...opts, name: /^保存$/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 4])
  })

  test('ボタン以外の役割と、名前で探さない呼び出しと、コメントと文字列の中は見つけない', () => {
    const text = [
      "await page.getByRole('cell', { name: '2,200 円' }).click()",
      "await page.getByRole('button').click()",
      "await page.getByRole('button', { pressed: true }).click()",
      "// page.getByRole('button', { name: '保存' })",
      'const text = "page.getByRole(\'button\', { name: \'保存\' })"',
    ].join('\n')
    assert.deepEqual(findButtonNameChecks(text), [])
  })

  test('名前の中に括弧の文字列や別の呼び出しがあっても、閉じ括弧を取り違えない', () => {
    const text = [
      "await page.getByRole('button', { name: label('a)'), exact: true }).click()",
      "await page.getByRole('button', { name: label('a)') }).click()",
      "await page.getByRole('button', { name: '保存', exact: true }).filter({ has: page.getByRole('button', { name: '削除' }) })",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [2, 3])
  })

  test('タイムアウトの数字を見つける（timeout の項目、setTimeout の引数、intervals の項目）', () => {
    const text = [
      'await expect(x).toBeVisible({ timeout: 10_000 })',
      'await expect(x).toBeVisible({ timeout: 5 * 1000 })',
      'test.setTimeout(60_000)',
      'test.setTimeout(test.info().timeout + 5000)',
      'setTimeout(() => done(), 100)',
      '}).toPass({ timeout: ASYNC_TIMEOUT, intervals: [500, 1_000] })',
      'await expect(x).toBeVisible({',
      '  timeout: ASYNC_TIMEOUT * 2,',
      '})',
      'await expect(x).toBeVisible({ timeout: 1000 }, { intervals: [CHECK_TIMEOUT] })',
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [1, 2, 3, 4, 5, 6, 8, 10])
  })

  test('timeout を含む項目（引用符のキーを含む）と、setDefaultTimeout・setDefaultNavigationTimeout の引数も見つける', () => {
    const text = [
      'use: { actionTimeout: 10_000 }',
      'use: { navigationTimeout: 10000, expectTimeout: ASYNC_TIMEOUT }',
      "{ 'timeout': 1000 }",
      '{ "actionTimeout": 5 * 1000 }',
      'page.setDefaultTimeout(5000)',
      'page.setDefaultNavigationTimeout(30_000)',
      'page.setDefaultTimeout(ASYNC_TIMEOUT)',
      "{ 'timeout': ASYNC_TIMEOUT }",
      "{ message: 'x', 'intervals': [100] }",
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [1, 2, 3, 4, 5, 6, 9])
  })

  test('timeout の後に語が続く項目（timeoutMs、navigationTimeoutMs など。大小文字を区別しない）も見つける', () => {
    const text = [
      'use: { timeoutMs: 10_000 }',
      'use: { navigationTimeoutMs: 5000 }',
      "{ 'timeoutMs': 1000 }",
      '{ TimeoutSeconds: 30 }',
      '{ timeoutMs: ASYNC_TIMEOUT }',
      'const value = ok ? timeoutMs : 5000',
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [1, 2, 3, 4])
  })

  test('型の注釈つきのデフォルト引数と、三項演算子の timeout は項目として見つけない', () => {
    const text = [
      'function wait(timeout: number = 5000) {}',
      'const wait2 = (page: Page, timeout: number = 5_000) => timeout',
      'async function poll(options: { timeout?: number }, actionTimeout: number = 100) {}',
      'const value = ok ? timeout : 5000',
      'const value2 = ok ? actionTimeout : 5 * 1000',
      'await expect(x).toBeVisible({ timeout: ok ? ASYNC_TIMEOUT : 5000 })',
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [6])
  })

  test('文字列の中の引用符つきのキーの形は見つけない', () => {
    const text = ['const note = "a \'timeout\': 5000"', "const other = 'x \"actionTimeout\": 1000'"].join('\n')
    assert.deepEqual(findTimeoutLiterals(text), [])
  })

  test('waits.ts の定数、名前だけの式、型の注釈、文字列とコメントの中の数字は見つけない', () => {
    const text = [
      'await expect(x).toBeVisible({ timeout: CHECK_TIMEOUT })',
      'test.setTimeout(test.info().timeout + PAYMENT_EXPIRY_TIMEOUT)',
      '}).toPass({ timeout, intervals })',
      '{ timeout: RESERVATION_MILLIS + ASYNC_TIMEOUT }',
      '{ message: `在庫が作られない`, timeout: ASYNC_TIMEOUT }',
      'options: { timeout?: number; intervals?: number[] }',
      "{ message: 'timeout: 5000 は書かない', timeout: MAIL_TIMEOUT }",
      '// { timeout: 5000 }',
      'const REPORT_TIMEOUT2 = ASYNC_TIMEOUT',
      '{ timeout: TIMEOUT2 }',
      'await expect(x).toHaveText("a", { timeout: CHECK_TIMEOUT }) // 1000',
      'await page.waitForTimeout(ms)',
    ].join('\n')
    assert.deepEqual(findTimeoutLiterals(text), [])
  })

  test('報告に行の番号と元の行の文字を含む', () => {
    assert.deepEqual(findPositionSelectors("x\n  page.getByRole('row').first()  "), [{ line: 2, text: "page.getByRole('row').first()" }])
  })

  test('タイムアウトの数字の検査は、タイムアウトの定数を定義する waits.ts を対象にしない', () => {
    const files = ['e2e/src/waits.ts', 'e2e/src/store.ts', 'e2e/tests/order.spec.ts', 'e2e/playwright.config.ts', 'e2e/src/waits.test.ts']
    assert.deepEqual(timeoutTargets(files), ['e2e/src/store.ts', 'e2e/tests/order.spec.ts', 'e2e/playwright.config.ts', 'e2e/src/waits.test.ts'])
  })
})

describe('組み込みの名前をシャドーイングする const・let の検査', () => {
  const names = new Set(['status', 'name', 'alert', 'cookieStore', 'Set'])
  const found = (text) => findBuiltinShadowing(text, names).map((hit) => `${hit.line}:${hit.name}`)

  test('const と let の宣言の名前が一覧にあれば見つける', () => {
    const text = ['const status = 200', "let name = 'a'", 'const total = 1', 'const cookieStore = createStore()'].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '4:cookieStore'])
  })

  test('オブジェクトの分割代入は、右側（宣言される名前）を見る', () => {
    const text = [
      'const { status } = response',
      'const { code: name } = response',
      'const { name: label } = response',
      'const { a, ...alert } = response',
      'const { name = 1 } = response',
      'const { x: { y: status } } = response',
      'const { [key]: name } = response',
    ].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '4:alert', '5:name', '6:status', '7:name'])
  })

  test('配列の分割代入は、全ての要素を見る', () => {
    const text = ['const [a, status] = pair', 'const [, name] = pair', 'const [first, ...Set] = pair', 'const [[alert], b] = pair', 'const [a, b] = pair'].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '3:Set', '4:alert'])
  })

  test('for…of と for…in の宣言を見つける', () => {
    const text = ['for (const status of statuses) {', '}', 'for (let name = 0; name < 3; name += 1) {', '}', 'for (const { name } of items) {', '}', 'for (const key in object) {', '}'].join('\n')
    assert.deepEqual(found(text), ['1:status', '3:name', '5:name'])
  })

  test('1 つの宣言に並べた名前と、型の注釈の付いた名前を見つける', () => {
    const text = ['const a = 1, status = 2', 'let b, name', 'const alert: string = "a"', 'const map: Map<string, number> = new Map()', 'const total = sum(1, 2),', '  Set = 1'].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '3:alert', '6:Set'])
  })

  test('初期値のジェネリクスのカンマの後ろの宣言も見つける。比較の < と > は取り違えない', () => {
    const text = [
      'const m = new Map<string, number>(), status = 3',
      'const a = f<A, B<C, D>>(1), b = 2, name = 3',
      'const cb = new Map<string, (x: number) => void>(), alert = 1',
      'const lt = a < b, Set = 1',
      'const gt = a > b, cookieStore = 1',
      'const mix = x < y ? p : q, status = 1',
    ].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '3:alert', '4:Set', '5:cookieStore', '6:status'])
  })

  test('型引数を持つ矢印関数（= の直後の <）の後ろの宣言も見つける。= の後ろの比較は取り違えない', () => {
    const text = [
      'const f = <T,>(a: T) => a, alert = 1',
      'const g = <T, U>(a: T, b: U) => a, status = 2',
      'const h = (<T,>(a: T) => a), name = 3',
      'const lt = a < b, Set = 1',
    ].join('\n')
    assert.deepEqual(found(text), ['1:alert', '2:status', '3:name', '4:Set'])
  })

  test('関数の引数、catch の変数、関数・クラスの宣言、import は対象外', () => {
    const text = [
      'function check(status) {}',
      'const run = (name) => name',
      'const fn = async (alert, other) => {}',
      'try {} catch (status) {}',
      'function name() {}',
      'class Set {}',
      "import { name } from './a'",
      "import status from './b'",
      'items.map((status) => status)',
      'test.each([1])("a", (name) => {})',
    ].join('\n')
    assert.deepEqual(found(text), [])
  })

  test('一覧にない名前、プロパティ、as const は見つけない', () => {
    const text = [
      'const orderStatus = 200',
      'const { code } = response',
      'const body = { status: 200, name: "a" }',
      'expect(response.status).toBe(200)',
      'const items = [1] as const',
      'status = 3',
      'const value = status',
    ].join('\n')
    assert.deepEqual(found(text), [])
  })

  test('コメントと文字列の中は見つけない', () => {
    const text = ['// const status = 1', '/* let name = 2 */', "const text = 'const alert = 1'", 'const html = `let name = ${1}`'].join('\n')
    assert.deepEqual(found(text), [])
  })

  test('複数の行にまたがる分割代入も見つける。行は宣言の始まり', () => {
    const text = ['const {', '  total,', '  status,', '} = response'].join('\n')
    assert.deepEqual(found(text), ['1:status'])
  })

  test('報告に行の番号、元の行の文字、名前を含む', () => {
    assert.deepEqual(findBuiltinShadowing('x\n  const status = 1  ', names), [{ line: 2, text: 'const status = 1', name: 'status' }])
  })
})

describe('テストの間の空行の検査', () => {
  const lines = (hits) => hits.map((hit) => hit.line)

  test('}) の直後の行に test・it・describe が続く箇所を見つける。行は続く側の行', () => {
    const text = ['test("a", () => {', '  expect(1).toBe(1)', '})', 'test("b", () => {', '})', 'it("c", () => {', '})', 'describe("d", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [4, 6, 8])
  })

  test('test.each・describe.skip・test.only などの形と、インデントのある行も見つける', () => {
    const text = [
      'describe("x", () => {',
      '  test("a", () => {',
      '  })',
      '  test.each([1, 2])("b %s", (n) => {',
      '  })',
      '  it.skip("c", () => {',
      '  })',
      '  describe.only("d", () => {',
      '  })',
      '  test.fixme("e", () => {',
      '  })',
      '  test.describe("f", () => {',
      '  })',
      '})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [4, 6, 8, 10, 12])
  })

  test('1 行で閉じるテストの直後に、空行なしで続くテストも見つける。1 行のテストが続いても同じ', () => {
    const text = [
      'test("a", () => {})',
      'test("b", () => {})',
      '',
      'it("c", async () => { await run() });',
      'test.each([1, 2])("d %s", (n) => {})',
      'test("e", () => {})',
      '',
      'const user = userEvent.setup()',
      'test("f", () => {})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [2, 5, 6])
  })

  test('test.beforeEach・test.use・test.step・test.info などのテストでない呼び出しは見つけない', () => {
    const text = [
      'test("a", () => {',
      '})',
      '',
      'test.beforeEach(() => {',
      '})',
      'test.use({ locale: "ja" })',
      'test.afterAll(async () => {',
      '})',
      'test.step("s", async () => {',
      '})',
      'test.info().annotations.push({ type: "x" })',
      'test.describe.configure({ mode: "serial" })',
      'test.setTimeout(1000)',
    ].join('\n')
    assert.deepEqual(findMissingTestSpacing(text), [])
  })

  test('test.describe.serial・test.skip・test.todo・test.concurrent などのテスト・グループの形は見つける', () => {
    const text = [
      'test("a", () => {})',
      'test.describe.serial("b", () => {',
      '})',
      'test.skip("c", () => {',
      '})',
      'test.todo("d")',
      'test.concurrent("e", () => {})',
      'test.fail("f", () => {})',
      'test.describe.parallel("g", () => {})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [2, 4, 6, 7, 8, 9])
  })

  test('閉じる行が }, 30_000) や }); の形でも見つける', () => {
    const text = ['test("a", async () => {', '}, 30_000)', 'test("b", () => {', '});', 'test("c", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [3, 5])
  })

  test('空行を挟むものは見つけない', () => {
    const text = ['test("a", () => {', '})', '', 'test("b", () => {', '})', '', '', 'describe("c", () => {', '})'].join('\n')
    assert.deepEqual(findMissingTestSpacing(text), [])
  })

  test('コメントの行を挟んでも、空行がなければ見つける。空行があれば見つけない', () => {
    const attached = ['test("a", () => {', '})', '// b の説明', 'test("b", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(attached)), [4])
    const blockAttached = ['test("a", () => {', '})', '/**', ' * 説明', ' */', 'test("b", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(blockAttached)), [6])
    const spaced = ['test("a", () => {', '})', '', '// b の説明', 'test("b", () => {', '})'].join('\n')
    assert.deepEqual(findMissingTestSpacing(spaced), [])
  })

  test('テストでない呼び出しの直後と、テストの前がテストの閉じでない箇所は見つけない（hook の閉じの直後と、1 行のテストの直後のテストは見つける）', () => {
    const text = [
      'beforeEach(() => {',
      '})',
      'test("a", () => {',
      '  expect(1).toBe(1)',
      '})',
      '',
      'const user = userEvent.setup()',
      'test("b", () => {})',
      'test("c", () => {})',
      'await test.step("s", async () => {',
      '})',
      'await test.step("t", async () => {',
      '})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [3, 9])
  })

  test('文字列とコメントの中の }) と test( は見つけない', () => {
    const text = ['const a = `', '})', 'test(', '`', '// })', '// test(', "const b = '})'"].join('\n')
    assert.deepEqual(findMissingTestSpacing(text), [])
  })

  test('報告に行の番号と、元の行の文字を含む', () => {
    assert.deepEqual(findMissingTestSpacing('  })\n  test("b", () => {  '), [{ line: 2, text: 'test("b", () => {' }])
  })
})
