import { LinkButton } from '@ec-site/shared/components/LinkButton'
import { Link } from 'react-router'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'

/**
 * バリアントの在庫数と、在庫の画面へのリンク。<br />
 * 在庫はバリアントの登録のイベントで作られるので、登録の直後はまだないことがある（「—」を出す）。
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
