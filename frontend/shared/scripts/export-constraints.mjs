// API の契約（OpenAPI）の入力の制約を、画面が使う定数のファイルに書き出す（フロントの書き方 第 2.3 節）。
// 使い方：node export-constraints.mjs <OpenAPI の YAML> <書き出す .ts>
import { readFileSync, writeFileSync } from 'node:fs'
import { basename } from 'node:path'
import { load } from 'js-yaml'
import { isMain } from '../../../scripts/ci/lib/is-main.mjs'

/** 書き出す制約のキーワード */
const KEYWORDS = [
  'minLength',
  'maxLength',
  'minimum',
  'maximum',
  'exclusiveMinimum',
  'exclusiveMaximum',
  'minItems',
  'maxItems',
]

/**
 * OpenAPI の文書から、入力の制約を取り出す。
 *
 * @param {object} doc OpenAPI の文書
 * @returns {{ schemas: object, parameters: object }} スキーマの名前ごとの制約と、操作の ID ごとのクエリのパラメーターの制約
 */
export function exportConstraints(doc) {
  const schemas = {}
  for (const [name, schema] of Object.entries(doc.components?.schemas ?? {})) {
    const constraints = schemaConstraints(doc, schema)
    if (constraints) schemas[name] = constraints
  }
  const parameters = {}
  for (const operations of Object.values(doc.paths ?? {})) {
    for (const operation of Object.values(operations)) {
      const found = {}
      for (const parameter of operation.parameters ?? []) {
        const resolved = resolve(doc, parameter)
        const constraints = schemaConstraints(doc, resolved.schema ?? {})
        if (constraints) found[resolved.name] = constraints
      }
      if (Object.keys(found).length > 0) parameters[operation.operationId] = found
    }
  }
  return { schemas, parameters }
}

/**
 * スキーマの制約。値のスキーマはキーワードを、オブジェクトは項目ごとの制約を、配列は要素の制約（items）を返す。
 * 項目の参照の先がオブジェクトなら写さない（そのスキーマの名前で引く）。制約がなければ undefined。
 */
function schemaConstraints(doc, schema) {
  if (schema.type === 'object' || schema.properties) {
    const properties = {}
    for (const [name, property] of Object.entries(schema.properties ?? {})) {
      const target = resolve(doc, property)
      if (property.$ref && (target.type === 'object' || target.properties)) continue
      const constraints = schemaConstraints(doc, target)
      if (constraints) properties[name] = constraints
    }
    return nonEmpty(properties)
  }
  const constraints = {}
  for (const keyword of KEYWORDS) {
    if (schema[keyword] !== undefined) constraints[keyword] = schema[keyword]
  }
  if (schema.type === 'array' && schema.items) {
    const items = resolve(doc, schema.items)
    if (!(schema.items.$ref && (items.type === 'object' || items.properties))) {
      const itemConstraints = schemaConstraints(doc, items)
      if (itemConstraints) constraints.items = itemConstraints
    }
  }
  return nonEmpty(constraints)
}

/** 参照（#/components/...）なら、その先を返す */
function resolve(doc, node) {
  if (!node.$ref) return node
  const target = node.$ref
    .replace(/^#\//, '')
    .split('/')
    .reduce((current, key) => current?.[key], doc)
  if (!target) throw new Error(`参照の先がない：${node.$ref}`)
  return resolve(doc, target)
}

/** 空のオブジェクトなら undefined */
function nonEmpty(object) {
  return Object.keys(object).length > 0 ? object : undefined
}

/**
 * 制約を TypeScript の定数のファイルにする。
 *
 * @param {{ schemas: object, parameters: object }} constraints 取り出した制約
 * @param {string} source 元の OpenAPI のファイルの名前
 */
export function renderModule(constraints, source) {
  const constant = (name, value) => `export const ${name} = ${JSON.stringify(value, null, 2)} as const\n`
  return [
    `// ${source} の入力の制約。frontend/shared/scripts/export-constraints.mjs が作る。手で直さない\n`,
    '/** スキーマの名前ごとの制約（オブジェクトは項目ごと、配列の要素は items） */',
    constant('schemas', constraints.schemas),
    '/** 操作の ID ごとの、クエリのパラメーターの制約 */',
    constant('parameters', constraints.parameters),
  ].join('\n')
}

if (isMain(import.meta.url)) {
  const [input, output] = process.argv.slice(2)
  const doc = load(readFileSync(input, 'utf8'))
  writeFileSync(output, renderModule(exportConstraints(doc), basename(input)))
}
