import type { Problem } from '@/api/types'

/** 応答の本文がエラーレスポンスの形（message を持つ）なら、その型で返す */
export function asProblem(body: unknown): Problem | undefined {
  if (typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string') {
    return body as Problem
  }
  return undefined
}
