// slot-env-names.mjs のテスト（node --test scripts/dev/lib/slot-env-names.test.mjs）
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { slotEnvNames } from './slot-env-names.mjs'
import { slotEnv } from './slot.mjs'

test('スロットが付ける環境変数の名前を全て含む', () => {
  const names = slotEnvNames()
  for (const target of ['backend', 'store', 'admin']) {
    for (const key of Object.keys(slotEnv(1, target))) {
      assert.ok(names.includes(key), `${key} がない`)
    }
  }
  assert.ok(names.includes('SPRING_DATASOURCE_URL'))
})

test('重複がない', () => {
  const names = slotEnvNames()
  assert.equal(new Set(names).size, names.length)
})
