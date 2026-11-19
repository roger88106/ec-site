# 段階 2：店舗画面と文書

- 前提：段階 1 が完了した。
- 完了条件：AC-518-6、7 のテストが通り、`scripts/dev/check-frontend.sh`、`doc-refs.mjs`、`ja-text.mjs` が通る。
- 参照：[plan](../plan.md)の「店舗画面」「文書の更新」、[spec](../spec.md)の US-518-1

## タスク

実装は実装役（汎用）に 1 タスクずつ任せる。文書の更新は、本文の書き手（[文書の書き方](../../../05-開発規則/writing.md)の「本文の書き手」）の条件に従う。

- [x] **T518-02-01** [テスト] 注文が拒否されたとき、カートへの導線を表示する単体テストを書き、レッドを確認する（AC-518-7）。
  - 依存：T518-01-07（新しいエラーコードが OpenAPI にある）
  - 範囲：`frontend/store/src/features/checkout/placeOrderErrors.test.ts`
  - 確認方法：テストが失敗する
- [x] **T518-02-02** 型を再生成し、T518-02-01 のテストが通る実装を書く。
  - 依存：T518-02-01
  - 範囲：`frontend/store/src/api/generated/`（生成物。git の管理外）、`frontend/store/src/features/checkout/placeOrderErrors.ts`
  - 確認方法：T518-02-01 のテストが通り、型の検査が通る
- [ ] **T518-02-03** 注文手続きの開始が拒否されたとき、カートの画面に文言を表示するテストを加え、通ることを確認する（AC-518-6）。
  - 依存：T518-02-02
  - 範囲：`frontend/store/src/features/cart/components/StartCheckoutForm.test.tsx`
  - 確認方法：テストが通る
- [x] **T518-02-04** 機能仕様と設計の文書を更新する（plan の「文書の更新」の表）。機能仕様の業務規則の文に、BR-518-1、BR-518-2 の番号を付ける。AC 一覧から機能仕様の「今有効な AC」を再生成する（`node scripts/ci/spec/ac-check.mjs --write`）。
  - 依存：T518-01-09
  - 範囲：`docs/03-機能仕様/payment.md`、`docs/03-機能仕様/order.md`、`docs/02-設計/order.md`、`docs/02-設計/payment.md`
  - 確認方法：`node scripts/ci/docs/doc-refs.mjs`、`node scripts/ci/ja/ja-text.mjs`、`node scripts/ci/spec/ac-check.mjs` が通る
- [ ] **T518-02-05** 段階の最後のレビュー。指摘を修正する。
  - 依存：T518-02-01〜04
  - 確認する項目：変えたコードを[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。
  - 結果：
- [ ] **T518-02-06** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T518-02-05
- [ ] **T518-02-07** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T518-02-06
- [ ] **T518-02-08** spec をアーカイブに移す。
  - 依存：T518-02-07
