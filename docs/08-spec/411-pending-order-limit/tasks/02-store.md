# 段階 2：店舗画面

- 前提：段階 1 の完了
- 完了条件：AC-411-9〜11 のテストが通る。既存の E2E が通る。`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md)の「店舗画面」、「テストの割り当て」、[spec](../spec.md)の US-411-2

## タスク

実装役が実装し、汎用で effort は中。

- [ ] **T411-02-01** [テスト] アクションのテストを書く（AC-411-9、10 の遷移）。
  - 依存：なし
  - 範囲：`cart.test.ts`、`checkout.test.ts`
  - 確認方法：テストが失敗することを確認する
- [ ] **T411-02-02** T411-02-01 のテストが通る実装を書く。開始と注文の 409 で上限の拒否を受けたら、上限を示すクエリパラメーターを付けた確認の画面へ遷移する。
  - 依存：T411-02-01
  - 範囲：`frontend/store/src/actions/cart.ts`、`frontend/store/src/actions/checkout.ts`、`frontend/store/src/lib/pendingOrders.ts`
  - 確認方法：テストが通る
- [ ] **T411-02-03** [テスト] 確認の画面のテストを書く（AC-411-9、10 の文、11）。
  - 依存：T411-02-02
  - 範囲：`PendingOrdersConfirmation.test.tsx`、`app/checkout/pending-orders/page.test.tsx`
  - 確認方法：テストが失敗することを確認する
- [ ] **T411-02-04** T411-02-03 のテストが通る実装を書く。クエリパラメーターがあれば上限の文を表示する。
  - 依存：T411-02-03
  - 範囲：`frontend/store/src/app/checkout/pending-orders/page.tsx`、`frontend/store/src/features/checkout/components/PendingOrdersConfirmation.tsx`、`frontend/store/messages/ja.json`
  - 確認方法：テストが通る
- [ ] **T411-02-05** 既存の E2E を全て実行する。上限で失敗するものがあれば、テストを弱めずに修正する。
  - 依存：T411-02-04
  - 範囲：`e2e/`
  - 確認方法：E2E が通る
- [ ] **T411-02-06** 手動の確認。店舗画面で支払待ちを上限まで作り、次の 3 つを確認する。「新しく注文する」で上限の文が表示される。別のタブで注文を成立させた後の注文で、上限の文が表示される。「支払へ進む」で支払画面に移る。
  - 依存：T411-02-05
  - 確認方法：操作の結果を記録する
  - 結果：
- [ ] **T411-02-07** 段階の最後のレビュー。指摘を修正する。
  - 依存：T411-02-01〜06
  - 確認する項目：変えたコードを、[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。ファイルをまたぐ影響も見る
  - 結果：
- [ ] **T411-02-08** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T411-02-07
- [ ] **T411-02-09** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T411-02-08
- [ ] **T411-02-10** spec をアーカイブに移す。
  - 依存：T411-02-09
