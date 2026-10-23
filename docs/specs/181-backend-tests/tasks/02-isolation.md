# 段階 2：他のテストのデータの影響

- 前提：段階 1
- 完了条件：DC-181-18〜23 を満たす。[plan](../plan.md) 第 2 節の 7 ファイルに、データを削除する後処理が残っていない。統合テストを、分け方を変えて 2 回実行し、どちらも通る。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 7 節、[spec](../spec.md) の DS-181-3 と DC-181-18〜23、DN-181-1〜7、[テストの書き方](../../../rules/test-writing.md) 第 3.1 節

## タスク

T181-02-01〜06 は、変えるファイルが重ならないので、互いに `[P]` である。T181-02-07 は、一覧のテストの絞り込み（T181-02-03〜05）と、他のテストのデータに依存するテストの修正（T181-02-01、02、06）の後に行う。理由：後処理を先に削除すると、残ったデータで一覧のテストが失敗するため。

T181-02-01〜06 は、確認を自分のデータに絞ったテストについて、一時的に期待値を変えて失敗することを確認し、結果を commit の本文に書く（DN-181-4）。

- [ ] **T181-02-01** [P] 支払の期限切れのジョブのテストを、ジョブを固定の回数で呼ばず、自分の 101 件が全て処理されるまで呼ぶ形にする。上限の回数は [plan](../plan.md) 第 2 節のとおり。他のテストの分として期限切れの支払を 200 件余分に作って実行しても通ることを、一時的に確認する（DC-181-18）。
  - 依存：なし
  - 範囲：`it/module/payment/application/PaymentExpiryJobTest.java`（`:131-157`）
- [ ] **T181-02-02** [P] 出荷の新しい順の読み取りのテストの 2 件の時刻を 3000 年にし、件数の上限で自分の 2 件だけが返ることを確認する。配送業者の mock のテストの時刻のコメントを合わせて修正する。配送業者の mock のテストと一緒に実行して通ることを確認する（DC-181-19）。
  - 依存：なし
  - 範囲：`it/module/shipping/infrastructure/persistence/JooqShipmentRepositoryTest.java`（`:119-131`）、`it/module/shipping/infrastructure/mock/MockCarrierTest.java`（`:33-35`）
- [ ] **T181-02-03** [P] 店舗の一覧と検索のテストを、テストごとのカテゴリ（一覧）と、商品名の印（検索）で絞った結果を確認する形にする。1 ページに収まる前提も、カテゴリの中の件数にする（DC-181-20）。
  - 依存：なし
  - 範囲：`it/module/storefront/presentation/StorefrontApiTest.java`（`:117-126,217-230,261-270`）、`it/module/catalog/api/StoreCatalogApiTest.java`（`:84-102,144-176`）、`StoreProductSearchTest.java`（`:87-126`）
- [ ] **T181-02-04** [P] 管理の商品の一覧のテストをテストごとのカテゴリで絞り、カテゴリの木のテストは自分の親のカテゴリの部分木だけを確認する形にする（DC-181-20）。
  - 依存：なし
  - 範囲：`it/module/catalog/presentation/ProductListApiTest.java`（`:83-101,193-207`）、`CategoryApiTest.java`（`:171-191`）
- [ ] **T181-02-05** [P] 管理の注文の一覧のテストを、自分の注文の ID か注文番号で絞った結果を確認する形にし、全体の件数を確認しない（DC-181-20）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/AdminOrderApiTest.java`（`:111,118,154`）、`AdminOrderShipmentViewApiTest.java`（`:216-253`）
- [ ] **T181-02-06** [P] 失敗したイベントを再発行するテストで、支払の取消・返金の記録とレポートの写しを、自分の注文の ID で絞って確認する（DC-181-22）。
  - 依存：なし
  - 範囲：`it/module/payment/application/PaymentRefundTest.java`（`:125-127,174-177`）、`PaymentVoidTest.java`（`:109-111`）、`it/module/reporting/application/ReportCollectionTest.java`（`:249-251`）
- [ ] **T181-02-07** コミットするテストの、データを削除する後処理（`@AfterEach`）を削除する。削除の後に `./gradlew integrationTest` が通ることを確認する。対象の 7 ファイルにデータを削除する後処理が残っていないことを確認する（DC-181-21、23）。
  - 依存：T181-02-01〜06
  - 範囲：`it/module/order/presentation/CheckoutCartChangeApiTest.java`（`:101-117`）、`PlaceOrderConcurrencyTest.java`（`:105-117`）、`OrderPaymentApiTest.java`（`:125-141`）、`CancellationAtomicityTest.java`（`:108-119`）、`it/module/catalog/presentation/ProductImageApiTest.java`（`:127-132`）、`ProductConcurrencyTest.java`（`:69-74`）、`ProductImageTransactionApiTest.java`（`:115-123`）
- [ ] **T181-02-08** 段階のレビューと全体の検査：profile「厚め」で、一般の観点（テスト、規則）を汎用で 2 回実施する。`./gradlew integrationTest` を、統合テストの分け方（`-PintegrationTestShard=1/2` と `2/2`、続けて `1/3`〜`3/3`）を変えて実行し、全て通ることを確認する。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T181-02-07
