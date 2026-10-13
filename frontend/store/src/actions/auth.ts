'use server'

import type { Route } from 'next'
import { redirect } from 'next/navigation'
import { deleteCartId } from '@/api/cartCookie'
import { idpLogoutUrl, signOut } from '@/auth'

/**
 * ログアウトする（AC-115、124）。セッションの Cookie と Cookie のカート ID を消し、IdP のログアウトへ移る
 * （IdP のログインの状態も終え、パスワードなしではログインし直せないようにする）。
 */
export async function logout(): Promise<void> {
  // ID token を添えるので、セッションの Cookie を消す前に URL を作る
  const logoutUrl = await idpLogoutUrl()
  await signOut({ redirect: false })
  await deleteCartId()
  // IdP の URL は店舗画面の外なので、画面の URL の型（typedRoutes）では確かめられない
  redirect(logoutUrl as Route)
}
