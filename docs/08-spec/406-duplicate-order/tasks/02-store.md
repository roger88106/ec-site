# 段階 2：店舗画面

- 前提：段階 1 の完了
- 完了条件：AC-406-4〜8、13 のテストが通る。確認の流れの E2E が通る。`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md)の「店舗画面」、「テストの割り当て」、[spec](../spec.md)の US-406-1、US-406-2

## タスク

実装は実装役が行い、汎用で effort は中。

- [x] **T406-02-01** [テスト] アクションのテストを書く（AC-406-7、13）。
  - 依存：なし
  - 範囲：`cart.test.ts`、`checkout.test.ts`
  - 確認方法：テストが失敗することを確認する
- [ ] **T406-02-02** T406-02-01 のテストが通る実装を書く。開始と注文の 409 を受けたら確認の画面へ遷移し、「新しく注文する」は表示した注文の ID を送って開始する。
  - 依存：T406-02-01
  - 範囲：`frontend/store/src/actions/cart.ts`、`frontend/store/src/actions/checkout.ts`
  - 確認方法：テストが通る
- [ ] **T406-02-03** 確認の画面と文言を実装し、画面のテストを書く（AC-406-4、5、6、8）。
  - 依存：T406-02-02
  - 範囲：`frontend/store/src/app/checkout/pending-orders/page.tsx`、`frontend/store/src/features/checkout/components/PendingOrdersConfirmation.tsx`、画面のテスト
  - 確認方法：テストが通る
- [ ] **T406-02-04** E2E の補助関数と既存の E2E を、確認の画面が表示されたら「新しく注文する」を選ぶ形に合わせる。
  - 依存：T406-02-03
  - 範囲：`e2e/src/store.ts`、`e2e/tests/member.spec.ts`、`e2e/tests/stock-hold-race.spec.ts`、`e2e/docs/order-flow.spec.ts`、`e2e/showcase/capture.spec.ts`
  - 確認方法：既存の E2E が通る
- [ ] **T406-02-05** [E2E] 確認の流れの E2E を書く。
  - 依存：T406-02-04
  - 範囲：`e2e/tests/pending-order-confirmation.spec.ts`
  - 確認方法：E2E が通る
- [ ] **T406-02-06** 手動の確認。店舗画面で、開始、3 つの選択（支払へ進む、新しく注文する、戻る）、別のタブで注文を成立させた場合を操作する。
  - 依存：T406-02-05
  - 確認方法：操作の結果を記録する
- [ ] **T406-02-07** 段階の最後のレビュー。指摘を修正する。
  - 依存：T406-02-01〜06
  - 確認する項目：変えたコードを、[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。ファイルをまたぐ影響も見る
  - 結果：レビューごとに 1 行の表で書く。列の形は skill `review` の手順 6
- [ ] **T406-02-08** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T406-02-07
- [ ] **T406-02-09** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T406-02-08
- [ ] **T406-02-10** spec をアーカイブに移す。
  - 依存：T406-02-09
