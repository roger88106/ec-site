import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { duplicatedFiles, findDefinitions, findExportedNames, sharedPublicNames } from './shared-names.mjs'

describe('店舗画面と管理画面の同じ内容のファイル', () => {
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

  test('除外を外した同じ内容のファイルの一覧に、除外がまだ必要なファイルが出る', () => {
    const files = {
      'frontend/store/src/lib/utils.ts': 'same',
      'frontend/admin/src/lib/utils.ts': 'same',
      'frontend/store/src/lib/price.ts': 'store',
      'frontend/admin/src/lib/price.ts': 'admin',
    }
    assert.deepEqual(duplicatedFiles(Object.keys(files), (file) => files[file], []), ['lib/utils.ts'])
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
