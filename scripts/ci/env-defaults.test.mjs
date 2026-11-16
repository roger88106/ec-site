import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { compareAdminIdp } from './env-defaults.mjs'

describe('管理画面の IdP の既定値の検査', () => {
  const AUTHORITY = 'http://localhost:8180/realms/ec-admin'
  const CLIENT_ID = 'ec-admin-web'

  /** 5 つの置き場所の文。値は引数で変える */
  const texts = ({ oidc = [AUTHORITY, CLIENT_ID], knip = [AUTHORITY, CLIENT_ID], script = [AUTHORITY, CLIENT_ID], workflow = [AUTHORITY, CLIENT_ID], doc = [AUTHORITY, CLIENT_ID] } = {}) => ({
    'frontend/admin/src/auth/oidc.ts': [
      '// authority: import.meta.env.VITE_OIDC_AUTHORITY ?? "http://comment.example"',
      "  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? '" + oidc[0] + "',",
      "  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? '" + oidc[1] + "',",
    ].join('\n'),
    'knip.ts': [
      "process.env.VITE_OIDC_AUTHORITY ??= '" + knip[0] + "'",
      "process.env.VITE_OIDC_CLIENT_ID ??= '" + knip[1] + "'",
    ].join('\n'),
    'scripts/dev/check-frontend.sh': [
      '# export VITE_OIDC_AUTHORITY="${VITE_OIDC_AUTHORITY:-http://comment.example}"',
      'export VITE_OIDC_AUTHORITY="${VITE_OIDC_AUTHORITY:-' + script[0] + '}"',
      'export VITE_OIDC_CLIENT_ID="${VITE_OIDC_CLIENT_ID:-' + script[1] + '}"',
    ].join('\n'),
    '.github/workflows/ci.yml': [
      '        env:',
      '          # VITE_OIDC_AUTHORITY: http://comment.example',
      '          VITE_OIDC_AUTHORITY: ' + workflow[0],
      '          VITE_OIDC_CLIENT_ID: ' + workflow[1],
    ].join('\n'),
    'docs/06-手順書/environment.md': [
      '| 環境変数 | 内容 | 既定 |',
      '|----------|------|------|',
      '| `VITE_OIDC_AUTHORITY` | IdP（運営者の realm） | `' + doc[0] + '` |',
      '| `VITE_OIDC_CLIENT_ID` | client ID | `' + doc[1] + '` |',
    ].join('\n'),
  })

  test('全ての置き場所の値が一致すれば、問題はない（コメントの行は読まない）', () => {
    assert.deepEqual(compareAdminIdp(texts()), [])
  })

  test('管理画面の認証の設定を正とし、ローカルの検査の script、CI、環境変数の文書、knip の値が違えば、置き場所と名前を示して失敗にする', () => {
    const wrong = [AUTHORITY.replace('8180', '9999'), 'ec-other']
    for (const place of ['knip', 'script', 'workflow', 'doc']) {
      const problems = compareAdminIdp(texts({ [place]: wrong }))
      assert.equal(problems.length, 2, place)
      assert.match(problems[0], /^VITE_OIDC_AUTHORITY：.*「http:\/\/localhost:9999\/realms\/ec-admin」.*「http:\/\/localhost:8180\/realms\/ec-admin」/, place)
      assert.match(problems[1], /^VITE_OIDC_CLIENT_ID：.*「ec-other」.*「ec-admin-web」/, place)
    }
  })

  test('どれか 1 つの項目だけが違うときは、その項目だけを失敗にする', () => {
    const problems = compareAdminIdp(texts({ script: [AUTHORITY, 'ec-other'] }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /scripts\/dev\/check-frontend\.sh/)
    assert.match(problems[0], /^VITE_OIDC_CLIENT_ID：/)
  })

  test('管理画面の認証の設定の既定値を読めないときは、他の値と比べずに失敗にする', () => {
    const input = texts()
    input['frontend/admin/src/auth/oidc.ts'] = 'export const oidcSettings = {}'
    const problems = compareAdminIdp(input)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /^VITE_OIDC_AUTHORITY：frontend\/admin\/src\/auth\/oidc\.ts の既定値を読めない/)
  })

  test('他の置き場所に値がない（読めない）ときも、失敗にする', () => {
    for (const file of ['knip.ts', 'scripts/dev/check-frontend.sh', '.github/workflows/ci.yml', 'docs/06-手順書/environment.md']) {
      const input = texts()
      input[file] = ''
      const problems = compareAdminIdp(input)
      assert.equal(problems.length, 2, file)
      assert.ok(problems.every((problem) => problem.includes(`${file} の`)), file)
    }
  })

  test('CI の値が複数あるときは、全ての値が一致していなければ失敗にする', () => {
    const input = texts()
    input['.github/workflows/ci.yml'] += '\n          VITE_OIDC_AUTHORITY: http://localhost:1/realms/x'
    const problems = compareAdminIdp(input)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /http:\/\/localhost:1\/realms\/x/)
  })

  test('リポジトリの今のファイルで、値が一致している', () => {
    const files = Object.keys(texts())
    const actual = Object.fromEntries(files.map((file) => [file, readFileSync(file, 'utf8')]))
    assert.deepEqual(compareAdminIdp(actual), [])
  })
})
