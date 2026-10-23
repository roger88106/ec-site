# 段階 2：店舗画面と共有のパッケージの HTML のテストの書き換え

- 前提：段階 1
- 完了条件：店舗画面と共有のパッケージのテストが、描画した HTML の文字を確認せず、DOM に読み込んで役割と名前で探す（DC-184-1、3）。jsdom のテストの状態と属性の確認が jest-dom の照合の関数である（DC-184-7）。開始タグの補助を削除した（DC-184-4）。spec 第 6 節の違反（`toBeDefined` 3 か所、HTML の正規表現・文字の一致 14 か所）が 0 件。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 3 節、[spec](../spec.md) の DS-184-1、2、[spec](../spec.md) 第 6 節、[テストの書き方](../../../rules/test-writing.md) 第 4.1 節、レビューの結果（`/.local/review/202610231200-157-frontend-tests/store-1.md`、`store-2.md`）

## タスク

実装は実装役のサブエージェントに任せる。書き換えたテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-184-4）。探す名前がない要素に支援技術向けの名前を追加したときは、`e2e/` の探し方と衝突しないかを確認する（plan 第 3 節）。

- [ ] **T184-02-01** [P] 共通の部品のテストを書き換える（DC-184-1、3）。
  - 依存：なし
  - 範囲：`frontend/store/src/components/ActionError.test.tsx`、`LoginLinks.test.tsx`、`PageLinks.test.tsx`、`QuoteItemSummary.test.tsx`、`SiteHeader.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [ ] **T184-02-02** [P] カートのテストを書き換える。購入できない品目の小計のテストは、購入できる品目との違いを確認する名前と確認にする（DC-184-1、3、22）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/cart/components/CartItemControls.test.tsx`、`CartMergeNoticeView.test.tsx`、`StartCheckoutForm.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [ ] **T184-02-03** [P] 商品のテストを書き換える。jsdom のテストの `.disabled`、`getAttribute` の比較を jest-dom にする（DC-184-1、3、7）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/catalog/components/CategoryList.test.tsx`、`ListFilterForm.test.tsx`、`ProductCard.test.tsx`、`ProductShowcase.test.tsx`、`VariantPicker.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [ ] **T184-02-04** [P] 注文のテストを書き換える。jsdom のテストの `getAttribute` の比較を jest-dom にする（DC-184-1、3、7）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/order/components/CancellationRequestForm.test.tsx`、`CancellationSection.test.tsx`、`OrderStatusBadge.test.tsx`、`PaymentSection.test.tsx`、`ShippingAddressDetails.test.tsx`、`frontend/store/src/app/orders/[orderId]/page.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [ ] **T184-02-05** [P] 共有のパッケージの金額の内訳のテストを、DOM に読み込んで確認する形にする（plan 第 1 節 ※2）。2 つの見た目の種類のテストの名前に、種類ごとに見える違いを書き、`expectInOrder` を見つからない文言が分かる確認にする（DC-184-1、22）。
  - 依存：なし
  - 範囲：`frontend/shared/src/components/AmountsSummary.test.tsx`
- [ ] **T184-02-06** 開始タグの補助とそのテストを削除し、店舗画面の構成 第 7 節の補助の表から `markup.ts` の行を削除する（DC-184-4、5）。
  - 依存：T184-02-01〜05
  - 範囲：`frontend/store/src/test/markup.ts`、`markup.test.ts`、`docs/design/apps/store.md`
- [ ] **T184-02-07** `node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DN-184-3）。
  - 依存：T184-02-01〜06
  - 範囲：`docs/design/acceptance-criteria.md`
- [ ] **T184-02-08** 段階のレビュー（profile「厚め」）と全体の検査を行う。spec 第 6 節の違反が 0 件であることを `git grep` で確認する。結果をこのファイルの末尾に書く。
  - 依存：T184-02-01〜07
  - 範囲：レビューの結果、`/.local/review/`
