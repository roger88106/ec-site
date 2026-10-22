import { useRef } from 'react'
import { $api } from '@/api/client'

/** 管理 API の権限（admin.yaml の x-required-permission）のうち、画面で出し分けに使うもの */
export type Permission = 'report-sales-trend:read' | 'report-variant-sales:read' | 'report-customer:read'

/** 自分の権限の一覧の取得の状態：取得中・失敗・取得済み */
export type PermissionsStatus = 'loading' | 'failed' | 'ready'

/** 自分の権限の一覧（取得中と失敗は空） */
export type Permissions = {
  /** 取得の状態。失敗は、権限がないのとは区別する（権限がないのは取得済みで has が false） */
  status: PermissionsStatus
  /** 取得の失敗（status が failed のとき） */
  error: unknown
  /** 取得中か（失敗の後の取り直しの間も true） */
  isFetching: boolean
  /** 取得をやり直す（失敗のときの再試行に使う） */
  retry: () => void
  /** その権限を持つか（取得済みでなければ持たないとみなす） */
  has: (permission: Permission) => boolean
}

/**
 * ログインしている運営者の権限を、管理 API の自分の権限の一覧から読む（管理画面の構成 第 10 節）。<br />
 * 画面は token を自分で読まない。権限はログインし直すまで変わらないので、取り直さない（失敗したときの再試行は除く）。
 */
export function usePermissions(): Permissions {
  const query = $api.useQuery('get', '/me', {}, { staleTime: Number.POSITIVE_INFINITY })
  const granted = new Set(query.data?.permissions ?? [])
  // 失敗の後の取り直しの間、TanStack Query は状態を取得中に戻し、error も空にする。
  // 失敗の表示を残したまま再試行のボタンだけ押せなくするため、直近の失敗を持つ
  const lastError = useRef<unknown>(undefined)
  if (query.isError) {
    lastError.current = query.error
  } else if (query.isSuccess) {
    lastError.current = undefined
  }
  const retrying = query.isPending && query.isFetching && lastError.current !== undefined
  const status: PermissionsStatus = query.isError || retrying ? 'failed' : query.isPending ? 'loading' : 'ready'
  return {
    status,
    error: query.isError ? query.error : lastError.current,
    isFetching: query.isFetching,
    retry: () => {
      void query.refetch()
    },
    has: (permission) => granted.has(permission),
  }
}
