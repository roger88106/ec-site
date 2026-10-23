import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { duplicatedFiles, featureOf, findCrossFeatureImports, findLocaleLiterals } from './frontend-rules.mjs'

describe('フロントの書き方の検査', () => {
  test('機能のファイルの、ほかの機能の import を見つける', () => {
    const text = [
      "import { CartLine } from '@/features/cart/components/CartLine'",
      "import { formatPrice } from '@/lib/format'",
      "import { OrderSummary } from '@/features/order/components/OrderSummary'",
      "import { checkoutQuery } from '../checkoutQuery'",
    ].join('\n')
    assert.deepEqual(findCrossFeatureImports(text, 'cart').map((hit) => hit.line), [3])
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
})
