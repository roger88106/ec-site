import { fetchClient } from '@/api/client'

/** 受け付ける画像の形式（AC-39、67）。登録のときに WebP に変換される（AC-68） */
export const IMAGE_CONTENT_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/bmp',
  'image/heic',
  'image/heif',
  'image/avif',
] as const

/**
 * 拡張子からの形式。ブラウザが HEIC などの形式を空で渡すことがあるため（Windows の Chrome など）、拡張子で補う
 */
const CONTENT_TYPES_BY_EXTENSION: Readonly<Record<string, (typeof IMAGE_CONTENT_TYPES)[number]>> = {
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

/** 1 ファイルの大きさの上限（10MB。AC-39） */
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024

/** 1 商品あたりの画像の上限（AC-40） */
export const MAX_IMAGES = 20

/** 管理 API の外（ストレージへの PUT など）で起きた、画像のアップロードの失敗。メッセージをそのまま出す */
export class ImageUploadError extends Error {}

/**
 * 受付に申告する画像の形式。ブラウザの形式が空（または汎用のもの）なら、拡張子で補う。
 *
 * @returns 形式。受け付けない形式なら null
 */
export function imageContentType(file: Pick<File, 'type' | 'name'>): string | null {
  const type = file.type.toLowerCase()
  if ((IMAGE_CONTENT_TYPES as readonly string[]).includes(type)) {
    return type
  }
  if (type !== '' && type !== 'application/octet-stream') {
    return null
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return CONTENT_TYPES_BY_EXTENSION[extension] ?? null
}

/**
 * 送る前に確かめられる、画像のファイルの誤り（形式と大きさ）。サーバーも同じ検証をする。
 *
 * @returns 誤りのメッセージ。なければ null
 */
export function imageFileError(file: Pick<File, 'type' | 'name' | 'size'>): string | null {
  if (imageContentType(file) === null) {
    return '画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。'
  }
  if (file.size === 0 || file.size > MAX_IMAGE_BYTES) {
    return '画像は 1 ファイル 10MB までです。'
  }
  return null
}

/**
 * 画像を 1 枚アップロードして登録する（plan 001 第 7.2 節）。<br />
 * 受付で署名付き URL を受け取り、ファイルをストレージへ直接 PUT してから、受付 ID で登録する。
 * API のエラーは ApiError として投げる。
 *
 * @param productId 商品 ID
 * @param file      画像のファイル
 * @param version   登録のときに送る商品のバージョン
 */
export async function uploadProductImage(productId: string, file: File, version: number): Promise<void> {
  const { data: upload } = await fetchClient.POST('/products/{productId}/image-uploads', {
    params: { path: { productId } },
    // 形式は imageFileError で確かめてから呼ぶ
    body: { contentType: imageContentType(file) ?? file.type, contentLength: file.size },
  })
  if (!upload) {
    throw new ImageUploadError('画像の受付に失敗しました。もう一度やり直してください。')
  }
  const stored = await fetch(upload.uploadUrl, { method: 'PUT', headers: upload.headers, body: file })
  if (!stored.ok) {
    throw new ImageUploadError('画像のファイルを送れませんでした。もう一度やり直してください。')
  }
  await fetchClient.POST('/products/{productId}/images', {
    params: { path: { productId } },
    body: { uploadId: upload.uploadId, version },
  })
}
