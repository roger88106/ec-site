import { Badge } from '@ec-site/shared/ui/badge'
import { Button } from '@ec-site/shared/ui/button'
import { ArrowDownIcon, ArrowUpIcon } from 'lucide-react'
import { useTranslations } from 'use-intl'
import type { ProductDetail } from '@/api/types'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { OptionSelect } from '@/components/OptionSelect'
import { TableCell, TableRow } from '@/components/ui/table'
import { StockLink } from '@/features/product/components/StockLink'

/** バリアント */
type Variant = ProductDetail['variants'][number]

/** バリアントの 1 行の操作。送る値（商品のバージョンなど）は一覧の側で決める */
export type VariantRowActions = {
  moveUp: () => void
  moveDown: () => void
  /** バリアントの画像を選ぶ。null は代表画像を使う */
  setImage: (imageId: string | null) => void
  suspend: () => void
  resume: () => void
  remove: () => void
}

/**
 * バリアントの一覧の 1 行。画像の選択、販売の停止・再開、並べ替え、削除。
 *
 * @param productId    商品の ID（在庫の画面へのリンク）
 * @param axes         商品のバリアント軸（軸の値の列）
 * @param imageOptions 選べる画像（代表画像と、商品の画像）
 * @param first        先頭の行（上へ移せない）
 * @param last         最後の行（下へ移せない）
 * @param deletable    削除できる（C 側に表示されたことのない商品）
 * @param pending      ほかの操作を待っている
 */
export function VariantRow({
  productId,
  variant,
  axes,
  imageOptions,
  first,
  last,
  deletable,
  pending,
  actions,
}: {
  productId: string
  variant: Variant
  axes: string[]
  imageOptions: { value: string | null; label: string }[]
  first: boolean
  last: boolean
  deletable: boolean
  pending: boolean
  actions: VariantRowActions
}) {
  const t = useTranslations('product.variantRow')
  const tCommon = useTranslations('common')
  return (
    <TableRow>
      <TableCell className="font-mono">{variant.variantCode}</TableCell>
      {axes.map((axis, axisIndex) => (
        <TableCell key={axis}>{variant.axisValues[axisIndex]}</TableCell>
      ))}
      <TableCell>
        <OptionSelect
          ariaLabel={t('image', { code: variant.variantCode }) /* 画像の選択 */}
          className="w-28"
          onChange={actions.setImage}
          options={imageOptions}
          value={variant.imageId ?? null}
        />
      </TableCell>
      <TableCell>
        {
          variant.salesSuspended ? (
            <Badge variant="secondary">{t('suspendedBadge') /* 販売停止 */}</Badge>
          ) : (
            t('onSale')
          ) /* 販売停止か販売中 */
        }
      </TableCell>
      <TableCell className="text-right">
        <StockLink productId={productId} variantId={variant.id} />
      </TableCell>
      <TableCell className="space-x-1 whitespace-nowrap text-right">
        <Button
          aria-label={tCommon('moveUp', { name: variant.variantCode }) /* 上へ */}
          disabled={first || pending}
          onClick={actions.moveUp}
          size="icon-xs"
          variant="ghost"
        >
          <ArrowUpIcon />
        </Button>
        <Button
          aria-label={tCommon('moveDown', { name: variant.variantCode }) /* 下へ */}
          disabled={last || pending}
          onClick={actions.moveDown}
          size="icon-xs"
          variant="ghost"
        >
          <ArrowDownIcon />
        </Button>
        {/* 販売停止のバリアントも C 側の選択肢に出るが、選べない */}
        {variant.salesSuspended ? (
          <Button
            aria-label={t('resumeLabel', { code: variant.variantCode }) /* 販売を再開 */}
            disabled={pending}
            onClick={actions.resume}
            size="xs"
            variant="outline"
          >
            {t('resume') /* 販売を再開 */}
          </Button>
        ) : (
          <Button
            aria-label={t('suspendLabel', { code: variant.variantCode }) /* 販売停止 */}
            disabled={pending}
            onClick={actions.suspend}
            size="xs"
            variant="outline"
          >
            {t('suspend') /* 販売停止 */}
          </Button>
        )}
        {/* C 側に表示されたことのある商品のバリアントは削除できない */}
        {deletable && (
          <ConfirmDialog
            confirmLabel={tCommon('deleteConfirm') /* 削除する */}
            description={t('deleteDescription') /* あわせて消すもの */}
            onConfirm={actions.remove}
            title={t('deleteTitle', { code: variant.variantCode }) /* 削除の確認 */}
            trigger={
              <Button disabled={pending} size="xs" variant="outline">
                {tCommon('delete') /* 削除 */}
              </Button>
            }
          />
        )}
      </TableCell>
    </TableRow>
  )
}
