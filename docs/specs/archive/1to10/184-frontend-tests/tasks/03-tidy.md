# 段階 3：テストの構成、書き方、補助とデータ

- 前提：段階 2（テストの実行とカバレッジの確認が重ならないため）
- 完了条件：管理画面と店舗画面のテストが DC-184-11〜21 を満たす。補助の単体テストは先に書き、レッドを確認した。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-184-4、5、[テストの書き方](../../../../../rules/test-writing.md) 第 4.5 節、[管理画面の構成](../../../../../design/apps/admin.md) 第 9 節、レビューの結果（`/.local/review/202610231200-157-frontend-tests/admin-1.md`、`admin-2.md`、`/.local/review/202610232100-157-final/general-2.md`）

## タスク

実装は実装役のサブエージェントに任せる。書き換えたテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-184-4）。

- [x] **T184-03-01** 偽の時計の後始末を店舗画面と管理画面の setup に置き、各ファイルから削除する。店舗画面の環境変数の直接の代入を `vi.stubEnv` にする（DC-184-16、17）。
  - 依存：なし
  - 範囲：`frontend/store/src/test/setup.ts`、`frontend/admin/src/test/setup.ts`、`frontend/admin/src/features/report/pages/SalesTrendPage.test.tsx`、`frontend/store/src/features/checkout/components/RemainingTime.test.tsx`、`frontend/store/src/lib/server/timeout.test.ts`
- [x] **T184-03-02** [テスト] 管理画面の日時の入力の補助と、店舗画面の現在の秒の補助のテストを書き、レッドを確認する。管理画面の一覧のセルの補助（行の見出しのセル、入れ子の表）と、ダウンロードの記録の補助（`download` の属性のないリンク）の想定外の使い方のテストを加え、レッドを確認する（DC-184-15、16、20）。
  - 依存：T184-03-01
  - 範囲：`frontend/admin/src/test/inputs.test.tsx`（新規）、`queries.test.tsx`、`download.test.ts`、`frontend/store/src/test/auth.test.ts`（新規）
- [x] **T184-03-03** T184-03-02 のテストが通る補助を書く。店舗画面の現在の秒の計算を、補助を使う形にする（DC-184-15、16、20）。
  - 依存：T184-03-02
  - 範囲：`frontend/admin/src/test/inputs.ts`（新規）、`queries.ts`、`download.ts`、`frontend/store/src/test/auth.ts`、`frontend/store/src/auth/index.test.ts`、`frontend/store/src/lib/server/timeout.test.ts`
- [x] **T184-03-04** [P] 商品の編集のテストを整える。409 の流れのテストを振る舞いごとに分け、`const URL` の名前を変え、日時の入力とファイルの選択を補助と `user.upload` にし、テストの間に空行を入れ、補助を包むだけの関数を削除する（DC-184-11〜13、15、18）。
  - 依存：T184-03-03
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T184-03-05** [P] 税区分、税率、在庫、注文の詳細のテストを整える。税率の 409 の流れのテストを分け、`const URL` の名前を変え、税率の日時の入力の補助を共通の補助に置き換え、テストの間に空行を入れる（DC-184-11〜13、15、18）。
  - 依存：T184-03-03
  - 範囲：`frontend/admin/src/features/tax/pages/TaxRatesPage.test.tsx`、`TaxCategoryListPage.test.tsx`、`frontend/admin/src/features/inventory/pages/StockPage.test.tsx`、`frontend/admin/src/features/order/pages/OrderDetailPage.test.tsx`
- [x] **T184-03-06** [P] 管理画面の hook、ログイン、レポートの条件のテストを整える。変更をまとめる hook を `renderHook` で呼び、ログインの戻り先のテストから共有のテストとの重複を削除し、レポートの条件の商品 ID は補助を使う（DC-184-14、18、19）。
  - 依存：T184-03-03
  - 範囲：`frontend/admin/src/api/useMutationGroup.test.ts`、`frontend/admin/src/lib/login.test.ts`、`frontend/shared/src/lib/login.test.ts`（網羅に漏れがあるときだけ）、`frontend/admin/src/features/report/reportCondition.test.ts`
- [x] **T184-03-07** [P] 店舗画面の地域のデータを補助に移す。地域のサーバーアクションと注文のフォームのテストは、補助を使う（DC-184-18）。
  - 依存：T184-03-03
  - 範囲：`frontend/store/src/test/fixtures.ts`、`frontend/store/src/actions/region.test.ts`、`frontend/store/src/features/checkout/components/OrderForm.test.tsx`
- [x] **T184-03-08** 管理画面のカテゴリの木と商品の画像の、重ねた支援技術向けの名前を 1 つにする。テストと、名前の変更で探せなくなる E2E を合わせる（DC-184-21）。
  - 依存：T184-03-04（商品の編集のテストが重なるため）
  - 範囲：`frontend/admin/src/features/category/components/CategoryTreeList.tsx`、`frontend/admin/src/features/product/components/ProductImagesCard.tsx`、`frontend/admin/src/features/category/pages/CategoryPage.test.tsx`、`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`（商品の画像のテストだけ）、`e2e/`（当たる所だけ）
- [x] **T184-03-09** 管理画面の構成 第 9 節と店舗画面の構成 第 7 節の補助の表に、追加・移動した補助を書く。`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DN-184-3）。
  - 依存：T184-03-01〜08
  - 範囲：`docs/design/apps/admin.md`、`docs/design/apps/store.md`、`docs/design/acceptance-criteria.md`
- [x] **T184-03-10** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T184-03-01〜09
  - 範囲：レビューの結果、`/.local/review/`

## 段階のレビューの結果

2026-10-24 に行った。対象は段階 3 の差分（T184-03-01〜09）。管理画面の支援技術向けの名前とテストの補助を変えたので、ファイルをまたぐ影響の観点を加えた。

| レビュー役 | モデル | 高 | 必須 |
|------------|--------|----|------|
| 一般の観点 1 回目（テストと規則） | 汎用 | 0 | 1 |
| 一般の観点 2 回目（一貫性、重複、文書） | 汎用 | 0 | 0 |
| ファイルをまたぐ影響 | 高性能 | 0 | 1 |

修正したもの（必須 2 件と、2 回目のレビューが表の件数に含めずに報告した重複 2 件）：

- 別のタブから戻る操作（`visibilitychange` の発火）を 4 か所で書いていた（テストの書き方 第 4.5 節）。管理画面の補助 `returnToTab` にまとめ、`fireEvent` を使う理由を TSDoc に書いた。
- 単体テストが、MSW のサーバーを読み込むレポートの補助から商品 ID を読んでいた。管理画面の構成 第 9 節は、その補助を結合テストだけで使うと書いている。商品 ID をテストデータの補助に移した。
- 郵便番号から住所を補う処理のテストが、補助と同じ形の住所を自前で作っていた。補助を使う形にした（補助が作れる県に合わせて、県のコードを 13 にした）。
- 409 の競合の文言を 2 つのファイルで定義していた。テストデータの補助に 1 つ置いた。

検査に移せる指摘：テストのファイルの `fireEvent(` の直接の使用は、`frontend-rules` で検出できる（理由付きのものを除く）。#185 の spec に伝える。

全体の検査：`scripts/dev/check-frontend.sh all`、カバレッジの基準（3 つとも基準以上）、`CI=true scripts/ci/check-all.sh` が通った。
