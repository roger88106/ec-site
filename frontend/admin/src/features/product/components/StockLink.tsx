import { Button } from '@ec-site/shared/ui/button'
import { Link } from 'react-router'
import { $api } from '@/api/client'

/**
 * バリアントの在庫数と、在庫の画面へのリンク（T710）。<br />
 * 在庫はバリアントの登録のイベントで作られるので、登録の直後はまだないことがある（「—」を出す）。
 */
export function StockLink({ productId, variantId }: { productId: string; variantId: string }) {
  const query = $api.useQuery('get', '/inventory/variants/{variantId}', { params: { path: { variantId } } })
  const label = query.isSuccess ? String(query.data.quantity) : query.isError ? '—' : '…'
  return (
    <Button
      className="tabular-nums"
      nativeButton={false}
      render={<Link to={`/products/${productId}/variants/${variantId}/stock`} />}
      size="xs"
      variant="link"
    >
      {label}
    </Button>
  )
}
