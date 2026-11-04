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

/** local の seed のレポート用の注文の期間（db/seed/local/README.md「レポート用の注文」） */
export const SEED_ORDERS = {
  /** 最初の日 */
  from: '2024-10-01',
  /** 最後の 1 年の最初の日 */
  lastYear: '2025-10-01',
  /** 最後の日 */
  to: '2026-09-30',
}

/**
 * 画面の全体を撮って、出力先のディレクトリに置く関数を作る。撮る関数は、表示が出て、画像が読み込まれるのを待ってから撮る。
 *
 * @param outDir 画像の出力先のディレクトリ
 * @returns 撮る関数。page は撮るページ、name はファイルの名前（拡張子なし）、ready は表示されたら撮ってよい要素
 */
export function makeShot(outDir: string): (page: Page, name: string, ready: Locator) => Promise<void> {
  return async (page, name, ready) => {
    await expect(ready).toBeVisible()
    await expect
      .poll(() => page.evaluate(() => Array.from(document.images).every((image) => image.complete)))
      .toBe(true)
    // スクロールしていると、fullPage の画像でサイドバーが途中から表示された状態で撮影されるので、先頭に戻す
    await page.evaluate(() => window.scrollTo(0, 0))
    // 店舗画面の開発サーバーが表示する Next.js のインジケーター（画面の左下）は、本番の画面にないので写さない
    await page.screenshot({
      path: path.join(outDir, `${name}.png`),
      fullPage: true,
      style: 'nextjs-portal { display: none; }',
    })
  }
}
