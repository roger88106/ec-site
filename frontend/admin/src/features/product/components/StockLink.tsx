import { LinkButton } from '@ec-site/shared/components/LinkButton'
import { Link } from 'react-router'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'

/**
 * バリアントの在庫数と、在庫の画面へのリンク
 *
 * 在庫の行がないバリアントは、在庫の取得が失敗するので「—」を出す。
 */
export function StockLink({ productId, variantId }: { productId: string; variantId: string }) {
  const query = $api.useQuery('get', '/inventory/variants/{variantId}', { params: { path: { variantId } } })
  const label = query.isSuccess ? String(query.data.quantity) : query.isError ? '—' : '…'
  return (
    <LinkButton
      className="tabular-nums"
      link={<Link to={paths.stock(productId, variantId)} />}
      size="xs"
      variant="link"
    >
      {label}
    </LinkButton>
  )
}
