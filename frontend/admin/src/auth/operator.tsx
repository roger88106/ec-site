import { createContext, useContext } from 'react'

/** ログインしている運営者（管理画面の構成の「ログイン」） */
export type Operator = {
  /** 氏名（姓 名） */
  name: string
  /** ログアウトする（IdP のログアウトへ移る） */
  logout: () => void
}

/** ログインしている運営者。AuthGate が渡し、結合テストでは renderApp が差し替える */
const OperatorContext = createContext<Operator | null>(null)

/** ログインしている運営者を渡す */
export const OperatorProvider = OperatorContext.Provider

/** ログインしている運営者を読む。画面の部品は react-oidc-context を直接使わず、これを使う */
export function useOperator(): Operator {
  const operator = useContext(OperatorContext)
  if (operator === null) {
    throw new Error('ログインしている運営者がありません（AuthGate か OperatorProvider で包む）')
  }
  return operator
}
