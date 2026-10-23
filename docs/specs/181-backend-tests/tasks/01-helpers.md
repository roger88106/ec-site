# 段階 1：テストの補助、待ち方、置き場所

- 前提：plan が承認された。実装を始める前の照合（[開発の進め方](../../../rules/workflow.md) 第 5.6 節）を済ませた。spec 182 のマージの有無を確認し、マージ済みなら `develop` を取り込んだ（[plan](../plan.md) の共通の決定）。
- 完了条件：DC-181-24〜27 を満たす。補助へ移したテストの確認は変わっていない（差分で確認する）。テストの書き方 第 3.2 節に補助の一覧がある。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-181-4 と DC-181-24〜27、DN-181-1〜7、[テストの書き方](../../../rules/test-writing.md) 第 3 節、[テストの書き方](../../../rules/test-writing.md) 第 5 節

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方は、[plan](../plan.md) の共通の決定のとおりである。補助へ移すタスクは、テストの確認を変えない（[plan](../plan.md) 第 1.1 節）。確認を強めないので、DN-181-4 の失敗の確認は求めない。待ち方を変えるタスク（T181-01-08〜10）は、変えたテストを 3 回続けて実行して通ることを確認する。

T181-01-01〜03 は同じテストのファイル（`StorefrontApiTest` など）と `CatalogTestData` を変えるので、順に行う。T181-01-04〜06 も注文と出荷のテストを共有するので、順に行う。T181-01-08、09、11 は、他のタスクとファイルが重ならないので `[P]` である。

- [ ] **T181-01-01** 在庫を作って入荷として調整する補助 `InventoryTestData` を作り、`createIfAbsent` と入荷の調整を書いている全ての統合テストから使う（DC-181-27）。
  - 依存：なし
  - 範囲：`it/InventoryTestData.java`（新規）、`it/module/` の下で `StockAdjustmentReason.ARRIVAL` を使うテスト（在庫の Repository のテスト `JooqStockItemRepositoryTest` を除く。調整そのものを確認する箇所は替えない）
- [ ] **T181-01-02** `CatalogTestData` に、カテゴリの登録、販売の終了、販売期間、品番を指定する商品の登録を追加する。店舗とカタログのテストの、商品・税区分・カテゴリの登録の再定義（`StorefrontApiTest` の内部クラス `TestProduct` を含む）を補助の呼び出しに置き換える（DC-181-27）。
  - 依存：T181-01-01
  - 範囲：`it/CatalogTestData.java`、`it/module/storefront/presentation/StorefrontApiTest.java`（`:98-109,391-419,421-428,440-537`）、`StorefrontFreshnessTest.java`（`:74-94`）、`it/module/catalog/api/StoreCatalogApiTest.java`（`:74-80,230-278`）、`StoreProductSearchTest.java`（`:61-72,137-161`）、`it/module/catalog/presentation/CategoryApiTest.java`（`:377-383`）、`ProductListApiTest.java`（`:232-236`）
- [ ] **T181-01-03** `CatalogTestData.TestProduct` に、画像の受付とアップロード（`upload()`）と確定（`addImage(uploadId, version)`）を追加し、`image()` をこの 2 つで組み立てる。画像のテスト 3 つの再定義を補助の呼び出しに置き換える（DC-181-27）。
  - 依存：T181-01-02
  - 範囲：`it/CatalogTestData.java`（`:170-197`）、`it/module/catalog/presentation/ProductImageApiTest.java`（`:394-425`）、`ProductImageServiceErrorApiTest.java`（`:124-148`）、`ProductImageTransactionApiTest.java`（`:159-183`）
- [ ] **T181-01-04** `OrderTestData` に、カートの取得、カートに入れる、注文手続きを始める補助を追加し（会員の token を受け取るオーバーロードを含む）、注文のテスト 5 つの再定義を補助の呼び出しに置き換える（DC-181-27）。
  - 依存：T181-01-01
  - 範囲：`it/OrderTestData.java`、`it/module/order/presentation/CheckoutApiTest.java`（`:193-218`）、`CheckoutCartChangeApiTest.java`（`:151-172`）、`PlaceOrderApiTest.java`（`:465-488`）、`PlaceOrderConcurrencyTest.java`（`:201-225`）、`MemberOrderApiTest.java`（`:281-310`）
- [ ] **T181-01-05** 出荷の登録、配送状態の通知、一意な送り状番号を作る補助 `ShippingTestData` を作り、出荷と注文のテストの再定義を補助の呼び出しに置き換える。送り状番号は 12 桁にそろえる（DC-181-27）。
  - 依存：T181-01-04
  - 範囲：`it/ShippingTestData.java`（新規）、`it/module/order/presentation/ReturnedOrderCancellationApiTest.java`（`:197-204`）、`StoreOrderShipmentApiTest.java`（`:166-173`）、`AdminOrderShipmentViewApiTest.java`（`:127-132,264-270`）、`ShipmentApiTest.java`（`:269-274`）、`it/module/order/OrderStatusChangedEventTest.java`（`:220-238`）、`it/module/shipping/DeliveryStatusChangedEventTest.java`（`:130-137`）、`it/module/shipping/infrastructure/carrier/CarrierWebhookTest.java`（`:259-261`）、`it/module/shipping/infrastructure/mock/MockCarrierTest.java`（`:179-181`）、`it/module/shipping/infrastructure/persistence/JooqShipmentRepositoryTest.java`（`:136-138`）
- [ ] **T181-01-06** 支払の作成、与信、売上確定、状態の取得の補助 `PaymentTestData` を作り、支払のテスト 6 つの再定義を補助の呼び出しに置き換える（DC-181-27）。
  - 依存：T181-01-05
  - 範囲：`it/PaymentTestData.java`（新規）、`it/module/payment/api/PaymentApiTest.java`（`:149-160`）、`PaymentAuthorizationDeadlineTest.java`（`:126-146`）、`it/module/payment/application/PaymentCaptureTest.java`（`:144-171`）、`PaymentRefundTest.java`（`:184-219`）、`PaymentVoidTest.java`（`:163-181`）、`PaymentExpiryJobTest.java`（`:184-197`）
- [ ] **T181-01-07** 作成の応答から ID を取り出す補助 `TestJson.createdId` を作り、`CatalogTestData`、`OrderTestData` と、`UUID.fromString(JsonPath.read(...))` を書いている全てのテストから使う（DC-181-27）。
  - 依存：T181-01-03、T181-01-06
  - 範囲：`it/TestJson.java`（新規）、`it/CatalogTestData.java`（`:105`）、`it/OrderTestData.java`（`:226`）、`it/` の下で `UUID.fromString(JsonPath.read(` を書いているテスト
- [ ] **T181-01-08** [P] イベントの記録の表の定義と、発行して待つ処理を、package-private の補助 `EventPublications` にまとめる。イベントの再発行のテストは、リスナーの試行の回数でなく `AsyncEvents.awaitFailed` で失敗を待つ（DC-181-25、27）。
  - 依存：なし
  - 範囲：`it/config/event/EventPublications.java`（新規）、`it/config/event/EventCleanupJobTest.java`（`:48,97-103`）、`EventResubmissionJobTest.java`（`:58,304-310`）
- [ ] **T181-01-09** [P] 重複したイベントを無視するレポートの集計のテストで、2 回目の発行の前に、1 回目のイベントの処理の完了を `AsyncEvents.awaitHandled` で待つ（DC-181-24）。
  - 依存：なし
  - 範囲：`it/module/reporting/application/ReportCollectionTest.java`（`:197-210`）
- [ ] **T181-01-10** 注文のキャンセルの一貫性のテストの、イベントの記録を直接読む待ちを `AsyncEvents.awaitHandled` に置き換える（DC-181-25）。
  - 依存：T181-01-01
  - 範囲：`it/module/order/presentation/CancellationAtomicityTest.java`（`:121-130`）
- [ ] **T181-01-11** [P] エラーの種類の検査の、契約を読んで照合する処理を `fix/` に移し、MockMvc に接続する設定だけを `it/` に残す。検査のテストを `test/` に移す。`fix/` の依存に JSON の path と YAML の読み込みのライブラリを追加する（DC-181-26）。
  - 依存：なし
  - 範囲：`it/config/security/ErrorCodeCheck.java`、`it/config/security/ErrorCodeCheckTest.java`、`fix/config/security/`（新規）、`test/config/security/ErrorCodeCheckTest.java`（移動先）、`backend/ecsite/build.gradle.kts`
- [ ] **T181-01-12** [テストの書き方](../../../rules/test-writing.md) 第 3.2 節に、補助の一覧（`InventoryTestData`、`ShippingTestData`、`PaymentTestData`、`TestJson`）を追記する。用語集の識別子の検査が求めるときは、用語集に追加する（[plan](../plan.md) 第 5 節）。
  - 依存：T181-01-01〜07
  - 範囲：`docs/rules/test-writing.md`、`docs/design/glossary.md`
- [ ] **T181-01-13** 段階のレビューと全体の検査：profile「厚め」で、一般の観点（規則、重複と抽象）を汎用で 2 回実施する。補助へ移したテストの確認が変わっていないことを、差分で確認させる。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T181-01-01〜12
