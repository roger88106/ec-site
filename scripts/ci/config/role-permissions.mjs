// ロールと権限の対応表（docs/02-設計/role-permissions.md）が、API の契約と運営者の realm の設定と一致することを確認する。
// 表は、管理 API の各操作の x-required-permission、運営者の realm のロール、店舗 API の各操作の security から作る。
//
// 権限が定義されていない管理 API の操作、realm にない権限、security のない店舗 API の操作も失敗にする。
// token が要らない店舗 API の操作は、security: [] で明示する（書き忘れを「不要」と読まないため）。
//
// 失敗したとき：リポジトリのルートで --write を付けて実行して表を再生成し、差分を確認する。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/role-permissions.mjs（リポジトリのルートで。--write で文書の表を書き直す）
import { readFileSync, writeFileSync } from 'node:fs'
import { load } from 'js-yaml'

/** 対応表の文書 */
const DOCUMENT = 'docs/02-設計/role-permissions.md'

/** 管理 API の OpenAPI */
const ADMIN_API = 'api/openapi/admin.yaml'

/** 店舗 API の OpenAPI */
const STORE_API = 'api/openapi/store.yaml'

/** 管理画面の realm（ロールと権限の割り当て） */
const ADMIN_REALM = 'docker/keycloak/realms/ec-admin-realm.json'

/** 管理 API の client（権限はこの client のロール） */
const ADMIN_CLIENT = 'ec-admin-api'

/** ログインしていればよい操作の印（権限ではない） */
const AUTHENTICATED = 'authenticated'

/** 対応表の文書の、生成する範囲の開きのマーカー */
const START = '<!-- generated:role-permissions -->'

/** 対応表の文書の、生成する範囲の閉じのマーカー */
const END = '<!-- /generated:role-permissions -->'

/** OpenAPI の paths の下で、操作として読む HTTP のメソッド */
const METHODS = ['get', 'post', 'put', 'patch', 'delete']

/**
 * OpenAPI の契約から操作を読む。
 *
 * 項目のない `security:`（YAML では null）は、認証が不要（`[]`）と同じでないので、書いていないもの（null）として読む。
 *
 * @param {string} text OpenAPI の契約
 * @returns {{ method: string, path: string, summary: string, permission: string | null, security: string[] | null }[]}
 *   操作（契約の順）。security は操作に書いた要件の名前の一覧（`{}` は空の文字）で、書いていなければ null
 */
export function readOperations(text) {
  return Object.entries(load(text)?.paths ?? {}).flatMap(([path, item]) =>
    Object.keys(item ?? {}).filter((method) => METHODS.includes(method)).map((method) => {
      const operation = item[method]
      const permission = operation['x-required-permission']
      return {
        method: method.toUpperCase(),
        path,
        summary: String(operation.summary ?? '').trim(),
        permission: typeof permission === 'string' && permission !== '' ? permission : null,
        security: Array.isArray(operation.security) ? operation.security.map((requirement) => Object.keys(requirement ?? {})[0] ?? '') : null,
      }
    }),
  )
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
  lines.push('| ロール | 説明 |')
  lines.push('|--------|------|')
  for (const role of roles) lines.push(`| \`${role.name}\` | ${role.description} |`)
  lines.push('')
  lines.push(`| 権限 | ${roles.map((role) => `\`${role.name}\``).join(' | ')} |`)
  lines.push(`|------|${roles.map(() => '---|').join('')}`)
  for (const permission of permissions) {
    const cells = roles.map((role) => mark(role.permissions.includes(permission)))
    lines.push(`| \`${permission}\` | ${cells.join(' | ')} |`)
  }

  lines.push('', '### 管理 API', '')
  lines.push('| 操作 | 内容 | 権限 |')
  lines.push('|------|------|------|')
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
    lines.push(`| \`${name}\` | ${operation.summary} | ${permission} |`)
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
    lines.push(`| \`${operation.method} ${operation.path}\` | ${operation.summary} | ${storeLogin(operation)} |`)
  }

  return { markdown: `${lines.join('\n')}\n`, problems }
}

/**
 * 文書の生成の範囲（START と END の位置）を探す。
 *
 * @param {string} document 文書
 * @returns {{ from: number, to: number } | null} START と END の始まりの位置。START がないか、END が START より前なら null
 */
function findGeneratedRange(document) {
  const from = document.indexOf(START)
  const to = document.indexOf(END)
  return from < 0 || to < from ? null : { from, to }
}

/**
 * 文書の生成の範囲と、再作成した表を比べる。
 *
 * @param {string} document 文書
 * @param {string} markdown 再作成した表
 * @returns {string[]} 食い違い（なければ空）
 */
export function checkDocument(document, markdown) {
  const range = findGeneratedRange(document)
  if (range === null) return [`文書に ${START} の範囲がない`]
  const { from, to } = range
  const current = `${document.slice(from + START.length, to).trim()}\n`
  if (current === markdown) return []
  return [
    '文書の対応表が、契約と realm の設定から作った表と違う。node scripts/ci/config/role-permissions.mjs --write で書き直し、差分を確認する',
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
  const range = findGeneratedRange(document)
  if (range === null) throw new Error(`文書に ${START} の範囲がない`)
  const { from, to } = range
  return `${document.slice(0, from + START.length)}\n${markdown}${document.slice(to)}`
}

if (import.meta.main) {
  const { markdown, problems } = renderTables({
    admin: readOperations(readFileSync(ADMIN_API, 'utf8')),
    store: readOperations(readFileSync(STORE_API, 'utf8')),
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
  // --write で問題があるときは、書かずにここへ来る
  if (process.argv.includes('--write')) console.log(`契約に問題があるので、${DOCUMENT} は書き直さなかった（問題を直してから --write を実行する）`)
  console.log(`対応表の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
