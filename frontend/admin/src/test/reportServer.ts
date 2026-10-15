import { http, server } from '@/test/server'

/** 結合テストの商品 ID */
export const SHIRT_ID = '00000000-0000-0000-0000-000000002001'

/**
 * レポートの画面が条件の入力で使う API（レポートの設定、商品の一覧）に応答する（結合テストだけで使う）。<br />
 * 会計年度は 4 月始まり、商品は T シャツ 1 つ。
 */
export function serveReportBasics() {
  server.use(
    http.get('/reports/settings', ({ response }) => response(200).json({ fiscalYearStartMonth: 4 })),
    http.get('/products', ({ response }) =>
      response(200).json({
        items: [
          { id: SHIRT_ID, productCode: 'TEE-001', name: 'ベーシック T シャツ', status: 'PUBLISHED', variantCount: 2 },
        ],
        page: 1,
        size: 100,
        totalItems: 1,
        totalPages: 1,
      }),
    ),
  )
}
