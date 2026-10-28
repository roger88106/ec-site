import { fetchClient } from '@/api/client'
import { createImageUploadRequestContentTypeValues } from '@/api/generated/admin'
import { schemas } from '@/api/generated/constraints'
import { translate } from '@/i18n/translate'

/** 受け付ける画像の形式（契約の形式の一覧）。登録のときに WebP に変換される */
const IMAGE_CONTENT_TYPES = createImageUploadRequestContentTypeValues

/** 受け付ける画像の形式 */
type ImageContentType = (typeof IMAGE_CONTENT_TYPES)[number]

/** 形式の表示の名前（HEIC と HEIF は同じ名前にまとめる） */
const FORMAT_LABELS: Readonly<Record<ImageContentType, string>> = {
  'image/jpeg': 'JPEG',
  'image/png': 'PNG',
  'image/webp': 'WebP',
  'image/bmp': 'BMP',
  'image/heic': 'HEIC / HEIF',
  'image/heif': 'HEIC / HEIF',
  'image/avif': 'AVIF',
}

/**
 * 拡張子からの形式。ブラウザーが HEIC などの形式を空で渡すことがあるため（Windows の Chrome など）、拡張子で補う
 */
const CONTENT_TYPES_BY_EXTENSION: Readonly<Record<string, ImageContentType>> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  bmp: 'image/bmp',
  heic: 'image/heic',
  heif: 'image/heif',
  avif: 'image/avif',
}

/** ファイルの選択で出す形式（MIME タイプと拡張子。拡張子がないと HEIC を選べない OS がある） */
export const IMAGE_ACCEPT = [
  ...IMAGE_CONTENT_TYPES,
  ...Object.keys(CONTENT_TYPES_BY_EXTENSION).map((ext) => `.${ext}`),
].join(',')

/** 1 ファイルの大きさの上限（バイト。契約の値） */
const MAX_IMAGE_BYTES = schemas.CreateImageUploadRequest.contentLength.maximum

/** 1 ファイルの大きさの上限（MB。文に差し込む） */
export const MAX_IMAGE_MEGABYTES = MAX_IMAGE_BYTES / (1024 * 1024)

/** 1 商品あたりの画像の上限（契約の値） */
export const MAX_IMAGES = schemas.ProductDetail.images.maxItems

/**
 * 受け付ける形式の表示の名前の一覧（文に差し込む）。
 *
 * @returns 例：「JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF」
 */
export function imageFormatsLabel(): string {
  return [...new Set(IMAGE_CONTENT_TYPES.map((type) => FORMAT_LABELS[type]))].join(
    translate('product.images.formatSeparator'), // 形式の区切り（、）
  )
}

/** 管理 API の外（ストレージへの PUT など）で起きた、画像のアップロードの失敗。メッセージをそのまま出す */
export class ImageUploadError extends Error {}

/**
 * 受付に申告する画像の形式。ブラウザーの形式が空（または汎用のもの）なら、拡張子で補う。
 *
 * @returns 形式。受け付けない形式なら null
 */
export function imageContentType(file: Pick<File, 'type' | 'name'>): ImageContentType | null {
  const type = file.type.toLowerCase()
  const listed = IMAGE_CONTENT_TYPES.find((contentType) => contentType === type)
  if (listed) {
    return listed
  }
  if (type !== '' && type !== 'application/octet-stream') {
    return null
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return CONTENT_TYPES_BY_EXTENSION[extension] ?? null
}

/**
 * 送る前に確認できる、画像のファイルの誤り（形式と大きさ）。サーバーも同じ検証をする。
 *
 * @returns 誤りのメッセージ。なければ null
 */
export function imageFileError(file: Pick<File, 'type' | 'name' | 'size'>): string | null {
  if (imageContentType(file) === null) {
    return translate('product.images.invalidType', { formats: imageFormatsLabel() }) // 形式の誤り
  }
  if (file.size === 0 || file.size > MAX_IMAGE_BYTES) {
    return translate('product.images.tooLarge', { max: MAX_IMAGE_MEGABYTES }) // 大きさの誤り
  }
  return null
}

/**
 * 画像を 1 枚アップロードして登録する。<br />
 * 受付で署名付き URL を受け取り、ファイルをストレージへ直接 PUT してから、受付 ID で登録する。
 * API のエラーは ApiError として投げる。
 *
 * @param productId 商品 ID
 * @param file      画像のファイル
 * @param version   登録のときに送る商品のバージョン
 */
export async function uploadProductImage(productId: string, file: File, version: number): Promise<void> {
  // 形式は imageFileError で確認してから呼ぶ（受け付けない形式は送らない）
  const contentType = imageContentType(file)
  if (contentType === null) {
    throw new ImageUploadError(translate('product.images.invalidType', { formats: imageFormatsLabel() })) // 形式の誤り
  }
  const { data: upload } = await fetchClient.POST('/products/{productId}/image-uploads', {
    params: { path: { productId } },
    body: { contentType, contentLength: file.size },
  })
  if (!upload) {
    throw new ImageUploadError(translate('product.images.uploadRejected')) // 受付の失敗
  }
  const stored = await fetch(upload.uploadUrl, { method: 'PUT', headers: upload.headers, body: file })
  if (!stored.ok) {
    throw new ImageUploadError(translate('product.images.uploadFailed')) // 送信の失敗
  }
  await fetchClient.POST('/products/{productId}/images', {
    params: { path: { productId } },
    body: { uploadId: upload.uploadId, version },
  })
}
