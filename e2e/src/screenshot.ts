import path from 'node:path'
import { expect, type Locator, type Page } from '@playwright/test'

/** 注文のお届け先の氏名（撮った画像に写る） */
export const RECIPIENT = '山田 花子'

/** local の seed の ID（db/seed/local/） */
export const SEED = {
  /** マグカップ（色を選ぶ商品） */
  mug: '00000000-0000-0000-0000-000000002014',
  /** マグカップの白 */
  mugWhite: '00000000-0000-0000-0000-000000003131',
  /** 板チョコレート（軽減税率 8%） */
  chocolate: '00000000-0000-0000-0000-000000002012',
}

/**
 * 画面の全体を撮る。表示が出て、画像が読み込まれるのを待ってから撮る。
 *
 * @param outDir 画像の出力先のディレクトリ
 * @param page 撮るページ
 * @param name ファイルの名前（拡張子なし）
 * @param ready 表示されたら撮ってよい要素
 */
export async function shot(outDir: string, page: Page, name: string, ready: Locator): Promise<void> {
  await expect(ready).toBeVisible()
  await expect.poll(() => page.evaluate(() => Array.from(document.images).every((image) => image.complete))).toBe(true)
  // 店舗画面の開発サーバーが表示する Next.js のインジケーター（画面の左下）は、本番の画面にないので写さない
  await page.screenshot({
    path: path.join(outDir, `${name}.png`),
    fullPage: true,
    style: 'nextjs-portal { display: none; }',
  })
}
