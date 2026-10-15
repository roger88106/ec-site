import { $api } from '@/api/client'

/** レポートの設定（会計年度の始まりの月）。設定はバックエンドの起動の間は変わらないので、取り直さない */
export function useReportSettings() {
  return $api.useQuery('get', '/reports/settings', {}, { staleTime: Number.POSITIVE_INFINITY })
}
