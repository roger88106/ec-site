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
  findCssClassChecks,
  findInternalSelectors,
  findLineThrough,
  findLocaleLiterals,
  findQueryUrlStrings,
  findDefinitions,
  findExportedNames,
  isCommonFile,
  sharedPublicNames,
} from './frontend-rules.mjs'

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

  test('fireEvent を別名で import した呼び出しと、名前空間の import の呼び出しも見つける', () => {
    const alias = ["import { fireEvent as fe, render } from '@testing-library/react'", 'fe.click(x)', 'fe(window, new Event("a"))', 'const fee = 1'].join('\n')
    assert.deepEqual(findFireEvents(alias).map((hit) => hit.line), [2, 3])
    const namespace = ["import * as rtl from '@testing-library/react'", 'rtl.fireEvent.click(x)', 'rtl.render(y)'].join('\n')
    assert.deepEqual(findFireEvents(namespace).map((hit) => hit.line), [2])
    const crlf = "import { fireEvent as fe } from 'x'\r\n// 理由\r\nfe.click(a)\r\nfe.click(b)\r\n"
    assert.deepEqual(findFireEvents(crlf).map((hit) => hit.line), [4])
  })
})
