import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findMissingComments, isTargetFile } from './message-comments.mjs'

describe('文言のキーのコメントの検査', () => {
  test('JSX の子の式の中のコメントは通る', () => {
    assert.deepEqual(findMissingComments("<Button>{t('common.save') /* 保存 */}</Button>"), [])
  })

  test('属性の値の後のコメントは通る', () => {
    assert.deepEqual(findMissingComments("<PageHeader title={t('tax.list.title') /* 税区分 */} />"), [])
  })

  test('JSX の外の式の行の後のコメントは通る', () => {
    assert.deepEqual(findMissingComments("const label = t('tax.list.title') // 税区分"), [])
  })

  test('複数行の呼び出しは、閉じ括弧の行のコメントで通る', () => {
    const text = ["{t('axes', {", '  names: axes.join(separator),', '}) /* 軸：色、サイズ */}'].join('\n')
    assert.deepEqual(findMissingComments(text), [])
  })

  test('複数行の JSX の式の終わりのコメントは、式の中の呼び出しすべてのコメントになる', () => {
    const text = [
      '<TableCell>',
      '  {',
      "    item.orderNumber ? t('orderNumber', { orderNumber: item.orderNumber })",
      '      : item.memo /* 注文番号かメモ */',
      '  }',
      '</TableCell>',
    ].join('\n')
    assert.deepEqual(findMissingComments(text), [])
  })

  test('引数のオブジェクトの中の呼び出しも、包む JSX の式の終わりのコメントで通る', () => {
    const text = [
      '{',
      "  t('axes', {",
      "    values: axes.map((axis) => tCommon('axisValue', { name: axis })).join(tCommon('listSeparator')),",
      '  }) /* 軸の名前と値 */',
      '}',
    ].join('\n')
    assert.deepEqual(findMissingComments(`<p>${text}</p>`), [])
  })

  test('JSX の式でない波括弧（関数の本体）の中のコメントは、呼び出しのコメントにしない', () => {
    const text = ['function f() {', "  const a = t('a')", '  return a // 返す', '}'].join('\n')
    assert.deepEqual(findMissingComments(text), [{ line: 2, key: 'a' }])
  })

  test('t で始まる別の名前の関数（tCommon など）も見る', () => {
    assert.deepEqual(findMissingComments("<span>{tCommon('save')}</span>"), [{ line: 1, key: 'save' }])
  })

  test('コメントのない呼び出しは、行とキーを出す', () => {
    const text = ['<div>', "  <Badge>{t('suspendedBadge')}</Badge>", '</div>'].join('\n')
    assert.deepEqual(findMissingComments(text), [{ line: 2, key: 'suspendedBadge' }])
  })

  test('同じ行の別の式のコメントは、前の呼び出しのコメントにしない', () => {
    assert.deepEqual(findMissingComments("{t('a')} {t('b') /* B */}"), [{ line: 1, key: 'a' }])
  })

  test('三元演算子の両方の枝を 1 つのコメントで書く形は通る', () => {
    assert.deepEqual(findMissingComments("{pending ? t('adding') : t('submit') /* 送信中か、カートに入れる */}"), [])
  })

  test('同じ行の次の属性のコメントは、前の属性の呼び出しのコメントにしない', () => {
    assert.deepEqual(findMissingComments("<X a={t('a')} b={t('b') /* B */} />"), [{ line: 1, key: 'a' }])
  })

  test('式の中の波括弧（オブジェクト、テンプレート、引数）の後ろの行末のコメントは通る', () => {
    const text = [
      "return { title: t('title') } // カート",
      // biome-ignore lint/suspicious/noTemplateCurlyInString: 検査の対象のソースの例
      "label: `${t('indent').repeat(depth)}${name}`, // 字下げ",
      "{pending ? t('placing') : t('submit', { total }) /* 注文中か、注文する */}",
      "<SelectValue placeholder={placeholder ?? t('placeholder')} /* 選択を促す文 */ />",
    ].join('\n')
    assert.deepEqual(findMissingComments(text), [])
  })

  test('キーを組み立てる呼び出しは見ない', () => {
    // biome-ignore lint/suspicious/noTemplateCurlyInString: 検査の対象のソースの例
    assert.deepEqual(findMissingComments('{t(`status.${status}`)}'), [])
  })

  test('名前の途中の t( は見ない', () => {
    assert.deepEqual(findMissingComments("const x = format('a')"), [])
  })
})

describe('translate の呼び出し', () => {
  test("translate('…') のコメントのない呼び出しは出す", () => {
    assert.deepEqual(findMissingComments("const label = translate('common.save')"), [{ line: 1, key: 'common.save' }])
  })

  test("translate('…') のコメントのある呼び出しは通る", () => {
    assert.deepEqual(findMissingComments("const label = translate('common.save') // 保存"), [])
  })

  test('キーを組み立てる translate と、名前の途中の translate( は見ない', () => {
    // biome-ignore lint/suspicious/noTemplateCurlyInString: 検査の対象のソースの例
    assert.deepEqual(findMissingComments('translate(`status.${s}`)'), [])
    assert.deepEqual(findMissingComments("i18n.translate('a')"), [])
  })
})

describe('調べるファイル', () => {
  test('画面の本番のソース（2 つの画面と共有パッケージの .ts と .tsx）を見る', () => {
    assert.equal(isTargetFile('frontend/admin/src/features/tax/pages/TaxListPage.tsx'), true)
    assert.equal(isTargetFile('frontend/store/src/app/page.tsx'), true)
    assert.equal(isTargetFile('frontend/store/src/lib/labels.ts'), true)
    assert.equal(isTargetFile('frontend/shared/src/format.ts'), true)
  })

  test('テスト、components/ui/、共有の ui、generated/、src の外は見ない', () => {
    assert.equal(isTargetFile('frontend/admin/src/features/tax/pages/TaxListPage.test.tsx'), false)
    assert.equal(isTargetFile('frontend/admin/src/components/ui/sidebar.tsx'), false)
    assert.equal(isTargetFile('frontend/shared/src/ui/button.tsx'), false)
    assert.equal(isTargetFile('frontend/admin/src/generated/api.ts'), false)
    assert.equal(isTargetFile('frontend/admin/vite.config.ts'), false)
  })
})

describe('調べるファイル（共通の定義より広い部分）', () => {
  test('src/test/ のテストの補助と generated/ の .tsx も見る', () => {
    assert.equal(isTargetFile('frontend/admin/src/test/renderApp.tsx'), true)
    assert.equal(isTargetFile('frontend/store/src/test/render.tsx'), true)
    assert.equal(isTargetFile('frontend/admin/src/generated/Foo.tsx'), true)
    assert.equal(isTargetFile('frontend/admin/src/test/helper.ts'), false)
    assert.equal(isTargetFile('frontend/admin/src/components/ui/sidebar.tsx'), false)
    assert.equal(isTargetFile('frontend/admin/src/a.test.tsx'), false)
  })
})
