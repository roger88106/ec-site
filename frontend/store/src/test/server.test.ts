import { describe, expect, test } from 'vitest'
import { networkError, STORE_API, server } from '@/test/server'

describe('通信の失敗の応答', () => {
  test('指定した方法と URL の要求を、通信の失敗にする', async () => {
    server.use(networkError('post', `${STORE_API}/carts`))

    await expect(fetch(`${STORE_API}/carts`, { method: 'POST' })).rejects.toThrow(TypeError)
  })

  test('URL のパスのパラメーターを使える', async () => {
    server.use(networkError('get', `${STORE_API}/prefectures/:prefectureCode/municipalities`))

    await expect(fetch(`${STORE_API}/prefectures/13/municipalities`)).rejects.toThrow(TypeError)
  })
})
