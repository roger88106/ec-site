import type { Locator, Page } from '@playwright/test'

/**
 * 詳細の項目（`dt` の項目名と `dd` の値）の値。<br />
 * 値（`dd`）に項目名を指す名前を付けられない（Biome の検査が `dd` の `aria-labelledby` を認めない）ので、
 * 項目名の次の値を選ぶ書き方は、ここだけに置く。
 *
 * @param page 店舗画面か管理画面のページ
 * @param label 項目名（全体が一致するもの）
 * @returns 項目の値
 */
export function detailValue(page: Page, label: string): Locator {
  return page.locator('dt', { hasText: new RegExp(`^${label}$`) }).locator('xpath=following-sibling::dd[1]')
}
