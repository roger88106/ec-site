// slot-env-names.mjs のテスト（node --test scripts/dev/lib/slot-env-names.test.mjs）
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { slotEnvNames } from './slot-env-names.mjs'

// E2E の入口が引き継がない環境変数。スロットが付ける名前を増やしたら、ここも増やす
const EXPECTED = [
  'SERVER_PORT',
  'MANAGEMENT_SERVER_PORT',
  'SPRING_DATASOURCE_URL',
  'ECSITE_MEDIA_STORAGE_INCOMING_BUCKET',
  'ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET',
  'ECSITE_MEDIA_STORAGE_PUBLIC_BASE_URL',
  'ECSITE_PAYMENT_RETURN_URL',
  'ECSITE_PAYMENT_MOCK_BASE_URL',
  'STORE_API_BASE_URL',
  'AUTH_URL',
  'STORE_COOKIE_PREFIX',
  'ADMIN_API_PROXY_TARGET',
]

test('バックエンド、店舗画面、管理画面にスロットが付ける環境変数の名前を、過不足なく返す', () => {
  assert.deepEqual([...slotEnvNames()].sort(), [...EXPECTED].sort())
})
