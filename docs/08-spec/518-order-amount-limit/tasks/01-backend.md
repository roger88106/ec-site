# 段階 1：バックエンド

- 前提：plan と tasks が承認された。issue 担当のセッションから開始の合図があり、実装を始める前の照合（[開発の進め方](../../../05-開発規則/workflow.md)）が完了した。
- 完了条件：AC-518-1〜5、8 のテストが通り、`./gradlew check` が通る。重い検査は、issue 担当のセッションの合図を待って実行する。
- 参照：[plan](../plan.md)の「範囲の定め方と取得」「注文手続きの開始と注文の判定」「支払画面」、[spec](../spec.md)の US-518-1

## タスク

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T518-01-01** [テスト] 決済の範囲を表す値オブジェクトの単体テストを書き、レッドを確認する。境界の 4 つの金額（49・50・99,999,999・100,000,000 円）を確認する（BR-518-1）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/module/payment/domain/payment/`
  - 確認方法：テストが失敗する
- [x] **T518-01-02** T518-01-01 のテストが通る実装を書く。
  - 依存：T518-01-01
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/payment/domain/payment/`
  - 確認方法：T518-01-01 のテストが通る
- [x] **T518-01-03** [テスト] 決済の公開の API で、範囲を返す操作の統合テストを書き、レッドを確認する（BR-518-1）。
  - 依存：T518-01-02
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/payment/api/PaymentApiTest.java`
  - 確認方法：テストが失敗する
- [x] **T518-01-04** T518-01-03 のテストが通る実装を書く。公開の API の型と操作、注文の腐敗防止層（決済）の操作を加える。
  - 依存：T518-01-03
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/payment/api/`、`backend/ecsite/src/main/java/com/ecsite/module/order/application/port/`、`backend/ecsite/src/main/java/com/ecsite/module/order/infrastructure/directory/`
  - 確認方法：T518-01-03 のテストが通る
- [x] **T518-01-05** [テスト] 注文手続きの開始の統合テストを書き、レッドを確認する。下限の外と上限の外で拒否し、注文手続きと引当がないことを確認する。下限ちょうどと上限ちょうどで開始でき、拒否の文言に値が差し込まれることを確認する（AC-518-1、2、4、5）。総額を作る準備は、T518-01-06 でも使える形にする。
  - 依存：T518-01-04
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/order/presentation/CheckoutAmountRangeApiTest.java`（新規）
  - 確認方法：テストが失敗する
- [ ] **T518-01-06** [テスト] 注文の統合テストと、判定の順の単体テストを書き、レッドを確認する。範囲の外で拒否し、注文と支払を作らず、在庫を変えないことを確認する。下限ちょうどと上限ちょうどで注文でき、上限ちょうどでは保存した注文の総額と支払の金額を読み戻せることを確認する。判定の順は単体テストで確認する（AC-518-3、4、5、BR-518-2）。
  - 依存：T518-01-05
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/order/presentation/PlaceOrderAmountRangeApiTest.java`（新規）、`backend/ecsite/src/test/java/com/ecsite/module/order/application/checkout/OrderPlacementCommandServiceTest.java`
  - 確認方法：テストが失敗する
- [ ] **T518-01-07** T518-01-05、T518-01-06 のテストが通る実装を書く。エラーコード、文言、判定、OpenAPI を加える。
  - 依存：T518-01-05、T518-01-06
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/order/`、`backend/ecsite/src/main/resources/messages.properties`、`api/openapi/store.yaml`
  - 確認方法：AC-518-1〜5 のテスト、`ErrorCodeContractTest`、`MessageContractTest` が通る
- [x] **T518-01-08** 上限ちょうどの金額を決済サービスに渡す単体テストを加え、テストが通ることを確認する（AC-518-8）。コードを変える必要が出たら、変える前に報告する。
  - 依存：T518-01-02
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/module/payment/infrastructure/stripe/StripePaymentGatewayTest.java`
  - 確認方法：テストが通る
- [ ] **T518-01-09** 段階の最後のレビュー（厚め）。指摘を修正する。
  - 依存：T518-01-01〜08
  - 確認する項目：変えたコードを[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。
  - 結果：
