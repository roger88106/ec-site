import { $api } from '@/api/client'

/** 管理 API の権限（admin.yaml の x-required-permission）のうち、画面で出し分けに使うもの */
export type Permission = 'report-sales-trend:read' | 'report-variant-sales:read' | 'report-customer:read'

/** 自分の権限の一覧（読み込み中は空） */
export type Permissions = {
  /** 読み込み中か */
  isLoading: boolean
  /** その権限を持つか（読み込み中は持たないとみなす） */
  has: (permission: Permission) => boolean
}

/**
 * ログインしている運営者の権限を、管理 API の自分の権限の一覧から読む（管理画面の構成 第 10 節）。<br />
 * 画面は token を自分で読まない。権限はログインし直すまで変わらないので、取り直さない。
 */
export function usePermissions(): Permissions {
  const query = $api.useQuery('get', '/me', {}, { staleTime: Number.POSITIVE_INFINITY })
  const granted = new Set(query.data?.permissions ?? [])
  return { isLoading: query.isPending, has: (permission) => granted.has(permission) }
}
