import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftIcon, ArrowRightIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { useTranslations } from 'use-intl'
import { useInvalidate } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  IMAGE_ACCEPT,
  ImageUploadError,
  imageFileError,
  MAX_IMAGES,
  uploadProductImage,
} from '@/features/product/imageUpload'
import { move } from '@/lib/array'

/**
 * 商品の画像の登録・並び替え・削除。先頭が代表画像。<br />
 * ファイルは署名付き URL でストレージへ直接送る。並び替えと削除は、表示している商品のバージョンで送る。
 */
export function ProductImagesCard({ product }: { product: ProductDetail }) {
  const invalidate = useInvalidate()
  const t = useTranslations('product.images')
  const tApp = useTranslations('app.common')
  const fileInput = useRef<HTMLInputElement>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const images = product.images
  const ids = images.map((image) => image.id)

  const upload = useMutation({
    // 複数のファイルは 1 枚ずつ登録する。登録ごとに商品のバージョンが 1 つ上がる
    mutationFn: async (files: File[]) => {
      for (const [index, file] of files.entries()) {
        await uploadProductImage(product.id, file, product.version + index)
      }
    },
    onSuccess: (_, files) => toast.success(t('registered', { count: files.length })), // 登録の知らせ
    // 途中で失敗しても、それまでに登録した画像を出す
    onSettled: () => invalidate('/products'),
  })
  const reorder = useApiMutation('put', '/products/{productId}/images/order', { invalidate: ['/products'] })
  const remove = useApiMutation('delete', '/products/{productId}/images/{imageId}', {
    success: t('deleted'), // 削除の知らせ
    invalidate: ['/products'],
  })
  const group = useMutationGroup(upload, reorder, remove)
  const { pending, error } = group
  // 画面で確かめた誤りと、管理 API の外での失敗は、メッセージをそのまま出す
  const localError = fileError ?? (error instanceof ImageUploadError ? error.message : null)
  /** 前の操作の誤りを消す（次の操作を始めるとき） */
  const resetErrors = () => {
    setFileError(null)
    group.resetErrors()
  }

  /** 選んだファイルを、形式・大きさ・枚数を確かめてから登録する。誤りがあれば 1 枚も送らない */
  const selectFiles = (files: File[]) => {
    resetErrors()
    const invalid = files.map(imageFileError).find((error) => error !== null)
    if (invalid) {
      setFileError(invalid)
    } else if (images.length + files.length > MAX_IMAGES) {
      setFileError(t('tooMany', { max: MAX_IMAGES })) // 枚数の上限
    } else if (files.length > 0) {
      upload.mutate(files)
    }
  }
  /** 表示している商品のバージョンで、画像の並びを送る */
  const reorderTo = (imageIds: string[]) => {
    resetErrors()
    reorder.mutate({ params: { path: { productId: product.id } }, body: { imageIds, version: product.version } })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 画像 */}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground text-sm">{t('help', { max: MAX_IMAGES }) /* 形式・大きさ・枚数と変換 */}</p>
        {images.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('empty') /* まだない */}</p>
        ) : (
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {images.map((image, index) => (
              <li className="space-y-1" key={image.id}>
                <img
                  alt={t('image', { number: index + 1 }) /* 画像の番号 */}
                  className="aspect-square w-full rounded-md border object-cover"
                  src={image.url}
                />
                <div className="flex items-center justify-between gap-1 text-xs">
                  <span className="text-muted-foreground">
                    {index === 0 ? t('representative') : t('image', { number: index + 1 }) /* 代表画像か番号 */}
                  </span>
                  <span className="flex gap-1">
                    <Button
                      aria-label={t('moveBefore', { number: index + 1 }) /* 前へ */}
                      disabled={index === 0 || pending}
                      onClick={() => reorderTo(move(ids, index, index - 1))}
                      size="icon-xs"
                      variant="ghost"
                    >
                      <ArrowLeftIcon />
                    </Button>
                    <Button
                      aria-label={t('moveAfter', { number: index + 1 }) /* 後へ */}
                      disabled={index === ids.length - 1 || pending}
                      onClick={() => reorderTo(move(ids, index, index + 1))}
                      size="icon-xs"
                      variant="ghost"
                    >
                      <ArrowRightIcon />
                    </Button>
                    <ConfirmDialog
                      confirmLabel={tApp('deleteConfirm') /* 削除する */}
                      description={t('deleteDescription') /* 削除の影響 */}
                      onConfirm={() => {
                        resetErrors()
                        remove.mutate({
                          params: {
                            path: { productId: product.id, imageId: image.id },
                            query: { version: product.version },
                          },
                        })
                      }}
                      title={t('deleteTitle', { number: index + 1 }) /* 削除の確認 */}
                      trigger={
                        <Button
                          aria-label={t('deleteLabel', { number: index + 1 }) /* 削除 */}
                          disabled={pending}
                          size="xs"
                          variant="outline"
                        >
                          {tApp('delete') /* 削除 */}
                        </Button>
                      }
                    />
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
        <input
          accept={IMAGE_ACCEPT}
          aria-label={t('file') /* 画像のファイル */}
          className="hidden"
          multiple
          onChange={(event) => {
            selectFiles(Array.from(event.target.files ?? []))
            // 同じファイルを選び直せるようにする
            event.target.value = ''
          }}
          ref={fileInput}
          type="file"
        />
        <Button
          disabled={pending || images.length >= MAX_IMAGES}
          onClick={() => fileInput.current?.click()}
          type="button"
          variant="outline"
        >
          {upload.isPending ? t('uploading') : t('add') /* 送信中か、追加 */}
        </Button>
        {localError ? (
          <Alert variant="destructive">
            <AlertDescription>{localError}</AlertDescription>
          </Alert>
        ) : (
          <ErrorMessage error={error} />
        )}
      </CardContent>
    </Card>
  )
}
