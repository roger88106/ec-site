import { isSafeReturnPath } from '@ec-site/shared/lib/login'
import { paths } from '@/app/paths'

/**
 * ログインの後に戻る画面（管理画面の構成 第 11 節）。IdP へ移る前に state に入れたパスを読む。
 * 画面の中のパスでなければ、トップにする（外のサイトへは戻さない）。
 *
 * @param state IdP から戻ってきた state
 */
export function returnPath(state: unknown): string {
  const returnTo = typeof state === 'object' && state !== null ? (state as { returnTo?: unknown }).returnTo : undefined
  return isSafeReturnPath(returnTo) ? returnTo : paths.top()
}
