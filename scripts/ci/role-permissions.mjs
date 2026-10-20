// ロールと権限の対応表（docs/design/role-permissions.md）が、API の契約と運営者の realm の設定と一致することを確かめる。
// 表は、管理 API の各操作の x-required-permission、運営者の realm のロール、店舗 API の各操作の security から作る。
// 使い方：node scripts/ci/role-permissions.mjs（リポジトリのルートで。--write で文書の表を書き直す）
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** 対応表の文書 */
const DOCUMENT = 'docs/design/role-permissions.md'
const ADMIN_API = 'api/openapi/admin.yaml'
const STORE_API = 'api/openapi/store.yaml'
const ADMIN_REALM = 'docker/keycloak/realms/ec-admin-realm.json'

/** 管理 API の client（権限はこの client のロール） */
const ADMIN_CLIENT = 'ec-admin-api'

/** ログインしていればよい操作の印（権限ではない） */
const AUTHENTICATED = 'authenticated'

const START = '<!-- generated:role-permissions -->'
const END = '<!-- /generated:role-permissions -->'

const METHODS = ['get', 'post', 'put', 'patch', 'delete']

/**
 * OpenAPI の契約から操作を読む。<br />
 * YAML のライブラリを使わず、`paths:` の下を字下げで読む（契約のファイルは 2 字ずつ字下げする）。
 *
 * @param {string} text OpenAPI の契約
 * @returns {{ method: string, path: string, summary: string, permission: string | null, security: string[] | null }[]}
 *   操作（契約の順）。security は操作に書いた要件の名前の一覧（`{}` は空の文字）で、書いていなければ null
 */
export function parseOperations(text) {
  const operations = []
  let inPaths = false
  let path = null
  let operation = null
  let inSecurity = false
  for (const raw of text.split('\n')) {
    const line = raw.replace(/\r$/, '')
    if (/^\S/.test(line)) {
      inPaths = line === 'paths:'
      path = null
      operation = null
      continue
    }
    if (!inPaths) continue
    const pathMatch = /^ {2}(\/\S*):\s*$/.exec(line)
    if (pathMatch) {
      path = pathMatch[1]
      operation = null
      continue
    }
    const methodMatch = /^ {4}(\w+):\s*$/.exec(line)
    if (methodMatch) {
      operation = null
      if (path && METHODS.includes(methodMatch[1])) {
        operation = { method: methodMatch[1].toUpperCase(), path, summary: '', permission: null, security: null }
        operations.push(operation)
      }
      continue
    }
    if (!operation) continue
    const key = /^ {6}([\w-]+):\s*(.*)$/.exec(line)
    if (key) {
      inSecurity = false
      const value = key[2].trim().replace(/^'(.*)'$/, '$1')
      if (key[1] === 'summary') operation.summary = value
      if (key[1] === 'x-required-permission') operation.permission = value
      if (key[1] === 'security') {
        operation.security = []
        inSecurity = value !== '[]'
      }
      continue
    }
    if (inSecurity) {
      const item = /^ {8}- (\{\}|([\w-]+): \[\])\s*$/.exec(line)
      if (item) operation.security.push(item[2] ?? '')
      else if (!/^ {8,}/.test(line)) inSecurity = false
    }
  }
  return operations
}

/**
 * 運営者の realm から、管理 API の権限を持つロールを読む。
 *
 * @param {object} realm realm の設定
 * @returns {{ roles: { name: string, description: string, permissions: string[] }[], permissions: string[] }}
 *   ロール（設定の順）と、管理 API の権限の一覧（設定の順）
 */
export function parseRealmRoles(realm) {
  const roles = (realm.roles?.realm ?? [])
    .map((role) => ({
      name: role.name,
      description: role.description ?? '',
      permissions: role.composites?.client?.[ADMIN_CLIENT] ?? [],
    }))
    .filter((role) => role.permissions.length > 0)
  const permissions = (realm.roles?.client?.[ADMIN_CLIENT] ?? []).map((role) => role.name)
  return { roles, permissions }
}

/** 表に書く要約。AC などの番号の括弧を除く */
function summaryOf(operation) {
  return operation.summary.replace(/（[^（）]*AC-[^（）]*）/g, '').trim()
}

/** 店舗 API の操作の、ログインの要否 */
function storeLogin(operation) {
  const security = operation.security ?? []
  if (security.length === 0) return '不要'
  return security.includes('') ? '任意' : '会員'
}

/**
 * 対応表の Markdown を作る。
 *
 * @param {{ admin: ReturnType<typeof parseOperations>, store: ReturnType<typeof parseOperations>,
 *   realm: ReturnType<typeof parseRealmRoles> }} input
 * @returns {{ markdown: string, problems: string[] }} 表と、契約と設定の食い違い
 */
export function renderTables({ admin, store, realm }) {
  const problems = []
  const { roles, permissions } = realm
  const mark = (granted) => (granted ? '○' : '')
  const lines = []

  lines.push('### ロールと権限', '')
  lines.push(`| ロール | 説明 | ${permissions.join(' | ')} |`)
  lines.push(`|--------|------|${permissions.map(() => '---|').join('')}`)
  for (const role of roles) {
    const cells = permissions.map((permission) => mark(role.permissions.includes(permission)))
    lines.push(`| \`${role.name}\` | ${role.description} | ${cells.join(' | ')} |`)
  }

  lines.push('', '### 管理 API', '')
  lines.push(`| 操作 | 内容 | 権限 | ${roles.map((role) => `\`${role.name}\``).join(' | ')} |`)
  lines.push(`|------|------|------|${roles.map(() => '---|').join('')}`)
  for (const operation of admin) {
    const name = `${operation.method} ${operation.path}`
    if (!operation.permission) {
      problems.push(`${name}：x-required-permission がない`)
      continue
    }
    const authenticatedOnly = operation.permission === AUTHENTICATED
    if (!authenticatedOnly && !permissions.includes(operation.permission)) {
      problems.push(`${name}：権限 ${operation.permission} が運営者の realm にない`)
      continue
    }
    const permission = authenticatedOnly ? 'ログインだけ' : `\`${operation.permission}\``
    const cells = roles.map((role) => mark(authenticatedOnly || role.permissions.includes(operation.permission)))
    lines.push(`| \`${name}\` | ${summaryOf(operation)} | ${permission} | ${cells.join(' | ')} |`)
  }

  lines.push('', '### 店舗 API', '')
  lines.push('| 操作 | 内容 | ログイン |')
  lines.push('|------|------|----------|')
  for (const operation of store) {
    lines.push(`| \`${operation.method} ${operation.path}\` | ${summaryOf(operation)} | ${storeLogin(operation)} |`)
  }

  return { markdown: `${lines.join('\n')}\n`, problems }
}

/**
 * 文書の生成の範囲と、作り直した表を比べる。
 *
 * @param {string} document 文書
 * @param {string} markdown 作り直した表
 * @returns {string[]} 食い違い（なければ空）
 */
export function checkDocument(document, markdown) {
  const from = document.indexOf(START)
  const to = document.indexOf(END)
  if (from < 0 || to < from) return [`文書に ${START} の範囲がない`]
  const current = `${document.slice(from + START.length, to).trim()}\n`
  if (current === markdown) return []
  return [
    '文書の対応表が、契約と realm の設定から作った表と違う。node scripts/ci/role-permissions.mjs --write で書き直し、差分を確かめる',
  ]
}

/**
 * 文書の生成の範囲を、作り直した表に置き換える。
 *
 * @param {string} document 文書
 * @param {string} markdown 作り直した表
 * @returns {string} 置き換えた文書
 */
export function replaceGenerated(document, markdown) {
  const from = document.indexOf(START)
  const to = document.indexOf(END)
  if (from < 0 || to < from) throw new Error(`文書に ${START} の範囲がない`)
  return `${document.slice(0, from + START.length)}\n${markdown}${document.slice(to)}`
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { markdown, problems } = renderTables({
    admin: parseOperations(readFileSync(ADMIN_API, 'utf8')),
    store: parseOperations(readFileSync(STORE_API, 'utf8')),
    realm: parseRealmRoles(JSON.parse(readFileSync(ADMIN_REALM, 'utf8'))),
  })
  const document = readFileSync(DOCUMENT, 'utf8')
  if (process.argv.includes('--write') && problems.length === 0) {
    writeFileSync(DOCUMENT, replaceGenerated(document, markdown))
    console.log(`書き直した：${DOCUMENT}`)
    process.exit(0)
  }
  problems.push(...checkDocument(document, markdown))
  for (const problem of problems) console.log(problem)
  console.log(`対応表の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
