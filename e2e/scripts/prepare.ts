/**
 * E2E の準備（plan 004 第 6.1 節）。`pnpm test` が Playwright の前に流す。<br />
 * E2E 用の DB（ec_site_e2e）と IdP の database（keycloak_e2e）を再作成し、E2E 用の Keycloak を起動し直す。
 * 毎回、マイグレーションと seed/base だけの DB と、realm の JSON の状態の IdP から始まる。
 * ローカルの DB（ec_site）と Keycloak（keycloak、8180）には触れない。
 *
 * 前提：compose の PostgreSQL が起動している（docs/guides/running-tests.md の「E2E」）。
 * 初めに、E2E のサーバーのポートで待ち受けられるかを確認する（Windows の予約範囲に入ったポートは、起動の後まで分からないため）。
 *
 * 使い方：node scripts/prepare.ts（e2e/ で）
 */
import { execFileSync } from 'node:child_process'
import { createServer } from 'node:net'
import path from 'node:path'
import { ADMIN_URL, BACKEND_MANAGEMENT_URL, BACKEND_URL, STORE_URL } from '../src/urls.ts'

/** リポジトリのルート。ルートの .env の COMPOSE_FILE が docker/compose.yaml を指す */
const ROOT = path.resolve(import.meta.dirname, '../..')

/** 再作成する database と、その持ち主 */
const DATABASES = [
  { name: 'ec_site_e2e', owner: 'ec_site' },
  { name: 'keycloak_e2e', owner: 'keycloak' },
]

/** docker compose を、出力をそのまま見せて流す */
function compose(...args: string[]): void {
  execFileSync('docker', ['compose', ...args], { cwd: ROOT, stdio: 'inherit' })
}

/** compose の PostgreSQL で SQL を 1 文ずつ流す（DROP DATABASE はトランザクションの中で流せないため） */
function psql(...statements: string[]): void {
  const commands = statements.flatMap((statement) => ['-c', statement])
  compose('exec', '-T', 'postgres', 'psql', '-U', 'ec_site', '-d', 'ec_site', '-v', 'ON_ERROR_STOP=1', ...commands)
}

/**
 * ポートで待ち受けられるかを、画面のサーバーと同じく localhost の IPv4・IPv6 の両方で確認する。
 * OS が拒んだ（EACCES）ときだけ投げる。使用中（起動済みのサーバー）や、IPv6 のない環境はよいとする。
 * Windows では、WinNAT などの予約範囲（netsh interface ipv4 show excludedportrange protocol=tcp）に入ったポートが EACCES になる。
 */
async function assertListenable(url: string): Promise<void> {
  const port = Number(new URL(url).port)
  for (const host of ['127.0.0.1', '::1']) {
    const code = await new Promise<string | null>((resolve) => {
      const server = createServer()
      server.once('error', (error: NodeJS.ErrnoException) => resolve(error.code ?? null))
      server.listen(port, host, () => server.close(() => resolve(null)))
    })
    if (code === 'EACCES') {
      throw new Error(
        `ポート ${port}（${url}）で待ち受けられない（${host}、EACCES）。Windows なら予約範囲に入っていないかを確認する：` +
          'netsh interface ipv4 show excludedportrange protocol=tcp',
      )
    }
  }
}

const started = Date.now()

for (const url of [BACKEND_URL, BACKEND_MANAGEMENT_URL, ADMIN_URL, STORE_URL]) {
  await assertListenable(url)
}

// 古い database を使っている Keycloak を先に止める。前の E2E のバックエンドの接続は FORCE で切る
compose('--profile', 'e2e', 'stop', 'keycloak-e2e')
psql(
  ...DATABASES.flatMap(({ name, owner }) => [
    `DROP DATABASE IF EXISTS ${name} WITH (FORCE)`,
    `CREATE DATABASE ${name} OWNER ${owner}`,
  ]),
)
// 空の database に realm の JSON を取り込んで起動する。準備ができるまで待つ
compose('--profile', 'e2e', 'up', '-d', '--wait', 'keycloak-e2e')

console.log(`E2E の準備ができた（${Math.round((Date.now() - started) / 1000)} 秒）`)
