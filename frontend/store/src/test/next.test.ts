import { notFound, redirect } from 'next/navigation'
import { describe, expect, test } from 'vitest'
import { expectNotFound, redirectedTo } from '@/test/next'

describe('redirect したことの確認', () => {
  test('redirect した処理の移動先を返す', async () => {
    expect(await redirectedTo(async () => redirect('/login?next=/cart'))).toBe('/login?next=/cart')
  })

  test('redirect しなければ失敗する', async () => {
    await expect(redirectedTo(async () => 'done')).rejects.toThrow('redirect していない')
  })

  test('redirect でない例外では失敗する', async () => {
    await expect(redirectedTo(async () => notFound())).rejects.toThrow('redirect していない')
  })
})

describe('見つからないことの確認', () => {
  test('notFound() を呼んだ処理は通る', async () => {
    await expectNotFound(async () => notFound())
  })

  test('例外を投げなければ失敗する', async () => {
    await expect(expectNotFound(async () => 'done')).rejects.toThrow('notFound() していない')
  })

  test('notFound() でない例外では失敗する', async () => {
    await expect(expectNotFound(async () => redirect('/login'))).rejects.toThrow('notFound() していない')
    await expect(expectNotFound(async () => Promise.reject(new Error('boom')))).rejects.toThrow('notFound() していない')
  })
})
