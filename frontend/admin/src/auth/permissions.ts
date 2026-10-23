import { useQueryClient } from '@tanstack/react-query'
import { $api } from '@/api/client'
import { lastQueryError } from '@/app/queryClient'

/** 管理 API の権限（admin.yaml の x-required-permission）のうち、画面で出し分けに使うもの */
export type Permission = 'report-sales-trend:read' | 'report-variant-sales:read' | 'report-customer:read'

/** 権限の一覧を取得している間の状態。読める項目は status だけ */
export type PermissionsLoading = {
  /** 取得の状態（取得中） */
  status: 'loading'
}

/** 権限の一覧の取得に失敗した状態。権限がないのとは区別する（権限がないのは取得済みで has が false） */
export type PermissionsFailed = {
  /** 取得の状態（失敗） */
  status: 'failed'
  /** 取得の失敗の中身 */
  error: unknown
  /** 取得中か（失敗の後の取り直しの間も true） */
  isFetching: boolean
  /** 取得をやり直す（失敗のときの再試行に使う） */
  retry: () => void
}

/** 権限の一覧を取得できた状態 */
export type PermissionsReady = {
  /** 取得の状態（取得済み） */
  status: 'ready'
  /** その権限を持つか */
  has: (permission: Permission) => boolean
}

/** 自分の権限の一覧。status で絞り込んでから、その状態の項目を読む */
export type Permissions = PermissionsLoading | PermissionsFailed | PermissionsReady

/**
 * ログインしている運営者の権限を、管理 API の自分の権限の一覧から読む（管理画面の構成 第 10 節）。<br />
 * 画面は token を自分で読まない。権限はログインし直すまで変わらないので、取り直さない（失敗したときの再試行は除く）。
 */
export function usePermissions(): Permissions {
  const queryClient = useQueryClient()
  const query = $api.useQuery('get', '/me', {}, { staleTime: Number.POSITIVE_INFINITY })
  const granted = new Set(query.data?.permissions ?? [])
  // 失敗の後の取り直しの間、TanStack Query は状態を取得中に戻し、error も空にする（errorUpdatedAt は残る）。
  // 失敗の表示を残したまま再試行のボタンだけ押せなくするため、「取得中で、過去に失敗した」なら失敗とする。
  // 画面の部品に持たせると、取り直しの間に新しく開いた画面が失敗を知らないので、判定も失敗の中身も、キャッシュの側から求める
  const retrying = query.isPending && query.errorUpdatedAt > 0
  const failed = query.isError || retrying
  if (failed) {
    const queryKey = $api.queryOptions('get', '/me', {}).queryKey
    return {
      status: 'failed',
      error: query.isError ? query.error : lastQueryError(queryClient, queryKey),
      isFetching: query.isFetching,
      retry: () => {
        void query.refetch()
      },
    }
  }
  if (query.isPending) {
    return { status: 'loading' }
  }
  return { status: 'ready', has: (permission) => granted.has(permission) }
}
