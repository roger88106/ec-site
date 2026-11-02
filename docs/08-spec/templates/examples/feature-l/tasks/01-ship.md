# 段階 1：出荷の登録

見本。実在の spec ではない。

- 前提：plan が承認された。実装を始める前の照合（[開発の進め方](../../../../../05-開発規則/workflow.md)の「実装を始める前の照合」）が完了した。
- 完了条件：AC-900-1〜5 を満たす。既存のテストが通り、`./gradlew check` が通る。
- 参照：[plan](../plan.md)の「出荷の登録の処理の順」、[plan](../plan.md)の「送り状番号の検証」、[注文の設計](../../../../../02-設計/order.md)の「出荷の登録」、[spec](../spec.md)の US-900-1、US-900-2

## タスク

実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` のタスクは高性能のモデルに任せる。

- [ ] **T900-01-01** [テスト] [リスク] 出荷登録の処理のテストを書き、レッドを確認する（AC-900-2〜5、BR-900-1）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/order/presentation/ShipmentApiTest.java`
  - 確認方法：テストが失敗する。テストを通すためにテストを弱めない
- [ ] **T900-01-02** [リスク] T900-01-01 のテストが通る実装を書く。
  - 依存：T900-01-01
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/order/application/shipment/`
  - 確認方法：AC-900-2〜5 のテストが通る
- [ ] **T900-01-03** [P] [テスト] 送り状番号の形（BR-900-2）の検証のテストを書き、レッドを確認する。実装も同じ実装役が続けて書く。
  - 依存：なし
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/order/presentation/ShipmentApiTest.java`、`backend/ecsite/src/main/java/com/ecsite/module/order/application/shipment/`
  - 確認方法：形が正しくない送り状番号で、売上確定の前に拒否される
- [ ] **T900-01-04** [P] 管理画面の注文詳細に、配送業者と送り状番号の入力と登録の操作を作る（AC-900-1）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/order/components/ShipmentCard.tsx`
  - 確認方法：画面のテストが通る
- [ ] **T900-01-05** 段階の最後のレビュー。売上確定と出荷の順（BR-900-1）の観点は、高性能のモデルでレビューする。指摘を修正する。
  - 依存：T900-01-01〜04
  - 確認する項目：変えたコードを[書き方の規則とレビュー](../../../../../05-開発規則/review.md)と照合する。
  - 結果：
- [ ] **T900-01-06** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T900-01-05
- [ ] **T900-01-07** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T900-01-06
- [ ] **T900-01-08** spec をアーカイブに移す。
  - 依存：T900-01-07
