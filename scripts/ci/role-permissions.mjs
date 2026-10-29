// ロールと権限の対応表（docs/02-設計/role-permissions.md）が、API の契約と運営者の realm の設定と一致することを確認する。
// 表は、管理 API の各操作の x-required-permission、運営者の realm のロール、店舗 API の各操作の security から作る。
// 権限のない管理 API の操作、security のない店舗 API の操作、realm にない権限、契約の知らない書き方の行（読み飛ばさずに）、文書の表の食い違いで失敗にする。
// 使い方：node scripts/ci/role-permissions.mjs（リポジトリのルートで。--write で文書の表を書き直す）
import { readFileSync, writeFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** 対応表の文書 */
const DOCUMENT = 'docs/02-設計/role-permissions.md'
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

/** 値として読めない書き方（空、ブロック、フロー形式、アンカーなど）の先頭 */
const NOT_PLAIN_VALUE = /^([|>[{&*!#]|$)/

/**
 * OpenAPI の契約から操作を読む。<br />
 * YAML のライブラリを使わず、`paths:` の下を字下げで読む（契約のファイルは 2 字ずつ字下げする）。<br />
 * 読む節（`paths:` の下のパス、メソッド、操作の直下のキー、`security` の項目）の中で、知っている書き方に当たらない行
 * （1 行で書くフロー形式、字下げの違い、引用符で囲んだパス、ブロックの値、項目のない `security:` など）は、読み飛ばさずに `unknown` に入れる。
 * 空行とコメントは除く。
 *
 * @param {string} text OpenAPI の契約
 * @returns {{ operations: { method: string, path: string, summary: string, permission: string | null,
 *   security: string[] | null }[], unknown: { line: number, text: string }[] }}
 *   操作（契約の順）と、知らない書き方の行（行の順）。security は操作に書いた要件の名前の一覧（`{}` は空の文字）で、書いていなければ null
 */
export function readOperations(text) {
  const operations = []
  const unknown = []
  let inPaths = false
  let path = null
  let operation = null
  let inSecurity = false
  // 項目を持つはずの「security:」の行（操作 → 行）
  const securityHeads = new Map()
  // パスやメソッドの次の行が、決まった字下げで始まるか見る（親の字下げと、子の字下げ）
  let child = null
  text.split('\n').forEach((raw, index) => {
    const line = raw.replace(/\r$/, '')
    if (line.trim() === '' || line.trim().startsWith('#')) return
    const bad = () => unknown.push({ line: index + 1, text: line })
    if (/^\S/.test(line)) {
      inPaths = /^paths:/.test(line)
      if (inPaths && line.trimEnd() !== 'paths:') {
        inPaths = false
        bad()
      }
      path = null
      operation = null
      inSecurity = false
      child = null
      return
    }
    if (!inPaths) return
    const indent = /^ */.exec(line)[0].length
    if (line[indent] === '\t') return bad()
    // security の中の行（読めた項目か、失敗にした行）があれば、項目のない security: ではない
    if (inSecurity && indent > 4 && !/^ {6}[\w-]+:/.test(line)) securityHeads.delete(operation)
    if (child && indent > child.parent && indent !== child.indent) {
      child = null
      return bad()
    }
    child = null
    if (indent === 2) {
      const pathMatch = /^ {2}(\/\S*):\s*$/.exec(line)
      path = pathMatch ? pathMatch[1] : null
      operation = null
      inSecurity = false
      if (!pathMatch) return bad()
      child = { parent: 2, indent: 4 }
    } else if (indent === 4) {
      operation = null
      inSecurity = false
      const methodMatch = /^ {4}(\w+):\s*$/.exec(line)
      if (methodMatch && METHODS.includes(methodMatch[1])) {
        if (!path) return
        operation = { method: methodMatch[1].toUpperCase(), path, summary: '', permission: null, security: null }
        operations.push(operation)
        child = { parent: 4, indent: 6 }
      } else if (!/^ {4}(parameters:\s*$|(summary|description|servers):)/.test(line)) {
        bad()
      }
    } else if (indent === 6 && operation) {
      const key = /^ {6}([\w-]+):\s*(.*)$/.exec(line)
      if (key) {
        inSecurity = false
        const value = key[2].trim().replace(/^(['"])(.*)\1$/, '$2')
        if (key[1] === 'summary' || key[1] === 'x-required-permission') {
          if (NOT_PLAIN_VALUE.test(value)) return bad()
          if (key[1] === 'summary') operation.summary = value
          else operation.permission = value
        }
        if (key[1] === 'security') {
          if (value !== '' && value !== '[]') return bad()
          operation.security = []
          inSecurity = value !== '[]'
          if (inSecurity) securityHeads.set(operation, { line: index + 1, text: line })
          else securityHeads.delete(operation)
        }
      } else if (inSecurity || !/^ {6}- /.test(line)) {
        bad()
      }
    } else if (indent < 2 || indent === 3 || (indent === 5 && operation)) {
      bad()
    } else if (operation && indent > 6) {
      // 操作のほかの項目（responses、parameters など）の中身は、権限と認証の判定に使わないので読まない。
      // security の中は、字下げ 8 の決まった形の項目だけを読み、それ以外の行（字下げ 7、9 以上など）は全て失敗にする
      if (!inSecurity) return
      const item = /^ {8}- (\{\}|([\w-]+): \[\])\s*$/.exec(line)
      if (item) operation.security.push(item[2] ?? '')
      else bad()
    }
    // 操作の外（パスの parameters の中身など）の字下げ 6 以上の行は、操作の判定に使わないので読まない
  })
  // 項目のない「security:」は YAML では null で、[]（認証が不要）と同じでないので、不要と読まずに失敗にする
  unknown.push(...securityHeads.values())
  unknown.sort((a, b) => a.line - b.line)
  return { operations, unknown }
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

/** 店舗 API の操作の、ログインの要否。security を書いていない操作は呼ばない（問題にする） */
function storeLogin(operation) {
  const security = operation.security
  if (security.length === 0) return '不要'
  return security.includes('') ? '任意' : '会員'
}

/**
 * 対応表の Markdown を作る。
 *
 * @param {{ admin: ReturnType<typeof readOperations>, store: ReturnType<typeof readOperations>,
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
    if (operation.security === null) {
      // 書き忘れを「不要」と読まない。token が要らない操作は security: [] で明示する
      problems.push(`${operation.method} ${operation.path}：security がない。token が要らない操作は security: [] と書く`)
      continue
    }
    lines.push(`| \`${operation.method} ${operation.path}\` | ${summaryOf(operation)} | ${storeLogin(operation)} |`)
  }

  return { markdown: `${lines.join('\n')}\n`, problems }
}

/**
 * 文書の生成の範囲と、再作成した表を比べる。
 *
 * @param {string} document 文書
 * @param {string} markdown 再作成した表
 * @returns {string[]} 食い違い（なければ空）
 */
export function checkDocument(document, markdown) {
  const from = document.indexOf(START)
  const to = document.indexOf(END)
  if (from < 0 || to < from) return [`文書に ${START} の範囲がない`]
  const current = `${document.slice(from + START.length, to).trim()}\n`
  if (current === markdown) return []
  return [
    '文書の対応表が、契約と realm の設定から作った表と違う。node scripts/ci/role-permissions.mjs --write で書き直し、差分を確認する',
  ]
}

/**
 * 文書の生成の範囲を、再作成した表に置き換える。
 *
 * @param {string} document 文書
 * @param {string} markdown 再作成した表
 * @returns {string} 置き換えた文書
 */
export function replaceGenerated(document, markdown) {
  const from = document.indexOf(START)
  const to = document.indexOf(END)
  if (from < 0 || to < from) throw new Error(`文書に ${START} の範囲がない`)
  return `${document.slice(0, from + START.length)}\n${markdown}${document.slice(to)}`
}

if (isMain(import.meta.url)) {
  const admin = readOperations(readFileSync(ADMIN_API, 'utf8'))
  const store = readOperations(readFileSync(STORE_API, 'utf8'))
  const unknownLines = [
    ...admin.unknown.map((entry) => ({ file: ADMIN_API, ...entry })),
    ...store.unknown.map((entry) => ({ file: STORE_API, ...entry })),
  ].map((entry) => describeUnreadableLine({ ...entry, reason: '知らない書き方。検査の読み方を追加するか、契約を今の書き方にそろえる' }))
  const { markdown, problems } = renderTables({
    admin: admin.operations,
    store: store.operations,
    realm: parseRealmRoles(JSON.parse(readFileSync(ADMIN_REALM, 'utf8'))),
  })
  problems.push(...unknownLines)
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
