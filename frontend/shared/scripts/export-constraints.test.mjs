import { describe, expect, test } from 'vitest'
import { exportConstraints, renderModule } from './export-constraints.mjs'

/** OpenAPI の文書の一部を作る */
function openapi({ schemas = {}, paths = {}, parameters = {} }) {
  return { openapi: '3.0.3', paths, components: { schemas, parameters } }
}

describe('API の契約の制約の書き出し', () => {
  test('項目の長さと範囲を、スキーマと項目の名前で書き出す', () => {
    const doc = openapi({
      schemas: {
        CreateTaxCategoryRequest: {
          type: 'object',
          properties: { name: { type: 'string', minLength: 1, maxLength: 50 }, memo: { type: 'string' } },
        },
        Quantity: { type: 'integer', minimum: 1, maximum: 10 },
      },
    })
    expect(exportConstraints(doc).schemas).toEqual({
      CreateTaxCategoryRequest: { name: { minLength: 1, maxLength: 50 } },
      Quantity: { minimum: 1, maximum: 10 },
    })
  })

  test('参照（$ref）の先が値のスキーマなら、その制約を項目に写す。オブジェクトなら写さない（そのスキーマの名前で引く）', () => {
    const doc = openapi({
      schemas: {
        ProductCode: { type: 'string', minLength: 1, maxLength: 32 },
        ShippingAddress: { type: 'object', properties: { name: { type: 'string', maxLength: 50 } } },
        CreateProductRequest: {
          type: 'object',
          properties: {
            productCode: { $ref: '#/components/schemas/ProductCode' },
            shippingAddress: { $ref: '#/components/schemas/ShippingAddress' },
          },
        },
      },
    })
    expect(exportConstraints(doc).schemas.CreateProductRequest).toEqual({
      productCode: { minLength: 1, maxLength: 32 },
    })
  })

  test('入れ子のオブジェクトと配列の要素の制約も書き出す', () => {
    const doc = openapi({
      schemas: {
        RatePercent: { type: 'number', minimum: 0, maximum: 100, exclusiveMaximum: true },
        ReplaceTaxRatesRequest: {
          type: 'object',
          properties: {
            rates: {
              type: 'array',
              maxItems: 20,
              items: { type: 'object', properties: { ratePercent: { $ref: '#/components/schemas/RatePercent' } } },
            },
            note: { type: 'object', properties: { text: { type: 'string', maxLength: 500 } } },
          },
        },
      },
    })
    expect(exportConstraints(doc).schemas.ReplaceTaxRatesRequest).toEqual({
      rates: { maxItems: 20, items: { ratePercent: { minimum: 0, maximum: 100, exclusiveMaximum: true } } },
      note: { text: { maxLength: 500 } },
    })
  })

  test('クエリのパラメーターの制約を、操作の ID とパラメーターの名前で書き出す（共通のパラメーターの参照も引く）', () => {
    const doc = openapi({
      parameters: {
        Size: { name: 'size', in: 'query', schema: { type: 'integer', minimum: 1, maximum: 100 } },
      },
      paths: {
        '/products': {
          get: {
            operationId: 'searchProducts',
            parameters: [
              { name: 'code', in: 'query', schema: { type: 'string', maxLength: 32 } },
              { name: 'sort', in: 'query', schema: { type: 'string' } },
              { $ref: '#/components/parameters/Size' },
            ],
          },
        },
      },
    })
    expect(exportConstraints(doc).parameters).toEqual({
      searchProducts: { code: { maxLength: 32 }, size: { minimum: 1, maximum: 100 } },
    })
  })

  test('TypeScript の定数のファイルにする', () => {
    const module = renderModule({ schemas: { Quantity: { minimum: 1, maximum: 10 } }, parameters: {} }, 'store.yaml')
    expect(module).toContain('store.yaml')
    expect(module).toContain('export const schemas = {')
    expect(module).toContain('"Quantity": {')
    expect(module).toContain('} as const')
    expect(module).toContain('export const parameters = {} as const')
  })
})
