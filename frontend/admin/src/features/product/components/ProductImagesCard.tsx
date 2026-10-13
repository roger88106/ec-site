import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftIcon, ArrowRightIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { useInvalidate } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import {
  IMAGE_ACCEPT,
  ImageUploadError,
  imageFileError,
  MAX_IMAGES,
  uploadProductImage,
} from '@/features/product/imageUpload'
import { move } from '@/lib/array'

/**
 * 商品の画像の登録・並び替え・削除（T1021、T1039。AC-37、39、40、67、70）。先頭が代表画像。<br />
 * ファイルは署名付き URL でストレージへ直接送る（plan 001 第 7.2 節）。並び替えと削除は、表示している商品のバージョンで送る。
 */
export function ProductImagesCard({ product }: { product: ProductDetail }) {
  const invalidate = useInvalidate()
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
    onSuccess: (_, files) => toast.success(`画像を ${files.length} 枚登録しました`),
    // 途中で失敗しても、それまでに登録した画像を出す
    onSettled: () => invalidate('/products'),
  })
  const reorder = useApiMutation('put', '/products/{productId}/images/order', { invalidate: ['/products'] })
  const remove = useApiMutation('delete', '/products/{productId}/images/{imageId}', {
    success: '画像を削除しました',
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
      setFileError(`画像は 1 商品あたり ${MAX_IMAGES} 枚まで登録できます。`)
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
        <CardTitle>画像</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground text-sm">
          JPEG、PNG、WebP、BMP、HEIC、AVIF で 1 ファイル 10MB まで、{MAX_IMAGES}{' '}
          枚まで登録できます。先頭の画像が代表画像です。 登録した画像は WebP
          に変換し、位置情報などの情報は取り除きます。
        </p>
        {images.length === 0 ? (
          <p className="text-muted-foreground text-sm">画像はまだありません。</p>
        ) : (
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {images.map((image, index) => (
              <li className="space-y-1" key={image.id}>
                <img
                  alt={`画像 ${index + 1}`}
                  className="aspect-square w-full rounded-md border object-cover"
                  src={image.url}
                />
                <div className="flex items-center justify-between gap-1 text-xs">
                  <span className="text-muted-foreground">{index === 0 ? '代表画像' : `画像 ${index + 1}`}</span>
                  <span className="flex gap-1">
                    <Button
                      aria-label={`画像 ${index + 1}を前へ`}
                      disabled={index === 0 || pending}
                      onClick={() => reorderTo(move(ids, index, index - 1))}
                      size="icon-xs"
                      variant="ghost"
                    >
                      <ArrowLeftIcon />
                    </Button>
                    <Button
                      aria-label={`画像 ${index + 1}を後へ`}
                      disabled={index === ids.length - 1 || pending}
                      onClick={() => reorderTo(move(ids, index, index + 1))}
                      size="icon-xs"
                      variant="ghost"
                    >
                      <ArrowRightIcon />
                    </Button>
                    <ConfirmDialog
                      confirmLabel="削除する"
                      description="この画像を指定しているバリアントは、代表画像を使うようになります。"
                      onConfirm={() => {
                        resetErrors()
                        remove.mutate({
                          params: {
                            path: { productId: product.id, imageId: image.id },
                            query: { version: product.version },
                          },
                        })
                      }}
                      title={`画像 ${index + 1}を削除しますか？`}
                      trigger={
                        <Button aria-label={`画像 ${index + 1}を削除`} disabled={pending} size="xs" variant="outline">
                          削除
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
          aria-label="画像のファイル"
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
          {upload.isPending ? 'アップロード中…' : '画像を追加'}
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
