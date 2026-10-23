# 段階 2：層の依存の向き

- 前提：段階 1
- 完了条件：DC-155-8〜16 を満たす。列挙の対応を変える 3 か所（order の見積もりの変換、catalog の並び順の変換、reporting の性別と履歴の状態）は、先にテストを追加した。追加した構成の検査の規則について、一時的に違反を入れて失敗することを確認した。関係する文書を更新し、AC 一覧のテストの列を再生成した。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。
- 参照：[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 7 節、[spec](../spec.md) の DS-155-2 と DC-155-8〜16、DN-155-1〜5、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.1 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.3 節

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。パスの書き方は、[plan](../plan.md) の共通の決定の最後の項目のとおりである。

公開の API の戻り値の型は変えない。各モジュールの腐敗防止層（利用者）は書き換えない。

タスクは 6 つのグループに分かれる。グループの中は順に行い、グループの先頭の `[P]` のタスクは互いに並行できる。T155-02-16、17 は、グループと並行して行える。T155-02-18 は、全てのグループの後に行う。

- order：T155-02-01、02、09、10、11
- cart：T155-02-03
- media：T155-02-04
- catalog：T155-02-05〜08
- storefront：T155-02-12
- reporting：T155-02-13〜15

- [x] **T155-02-01** [P] [テスト] 注文の見積もりの変換を固定する。今の `CartQuoteService` の変換について、見積もり結果の全ての項目（品目、軸の値、金額、税額）と、買えない理由の全ての値の対応を確認する単体テストを追加する。今のコードで通ることを確認し、一時的に変換の実装か期待値を変えて、失敗しうることを確認してから元に戻す。AC-277 のテスト名を残す（DC-155-8、9）。
  - 依存：なし
  - 範囲：`test/module/order/application/checkout/CartQuoteServiceTest.java`（`:3,20`）
- [x] **T155-02-02** 注文の見積もりの service を、カート ID と見積もり結果を持つ view `CartQuoteView`（`order/application/model/`）を返す形にする。公開の API `OrderQuoteApi` が公開の型 `CartQuote` に変換する。変換（`toCartQuote`、`toItem`、`toReason`、`toAmounts`、`toTaxAmount`）を API に移し、`CartQuoteServiceTest` を `test/module/order/api/OrderQuoteApiTest.java` に移す。`toReason` は package-private の static のままにし、単体テストから呼ぶ（DC-155-8、9）。
  - 依存：T155-02-01
  - 範囲：`main/module/order/application/checkout/CartQuoteService.java`（`:3,57,68-123`）、`main/module/order/application/model/CartQuoteView.java`（新規）、`main/module/order/api/OrderQuoteApi.java`（`:32-34`）、`test/module/order/application/checkout/CartQuoteServiceTest.java`（移動）。利用者の `main/module/storefront/infrastructure/directory/OrderApiDirectory.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-02-03** [P] カートの内容を返す service を、`cart/application/model/CartContentsView` を返す形にする。`CartContentsApi` が `CartContents` に変換する。`MergedCart` は動かさない（DC-155-8、9）。
  - 依存：なし
  - 範囲：`main/module/cart/application/CartService.java`（`:6,145-150`）、`main/module/cart/application/model/CartContentsView.java`（新規）、`main/module/cart/api/CartContentsApi.java`（`:31`）。利用者の `main/module/order/infrastructure/directory/CartApiDirectory.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-02-04** [P] 画像の service を、アセット ID を返す形にする。`prepareImage` は `UUID` を返し、`registerImage`、`discardImage` は `UUID` を受ける。`requestImageUpload` は `media/application/model/ImageUploadView` を返す。`MediaApi` が `PreparedImage`、`ImageUpload` に変換する（DC-155-8、9）。
  - 依存：なし
  - 範囲：`main/module/media/application/MediaService.java`（`:4-5,71,81,88,99,108,117,160`）、`main/module/media/application/model/ImageUploadView.java`（新規）、`main/module/media/api/MediaApi.java`（`:36-37,51-52,63-64,73-74`）。`it/module/media/api/MediaApiTest.java` と、利用者の `main/module/catalog/infrastructure/directory/MediaApiDirectory.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-02-05** [P] catalog の `OrderableVariantView`（入れ子 `AxisValue`）を `catalog/application/model/view/` に作る。`OrderableVariantQueryService`、`OrderableVariantQueryRepository`、`JooqOrderableVariantQueryRepository` はこの view を使う。`OrderCatalogApi` が `OrderableVariant` に変換する。`ReportCatalogApi` が使うのは値の `productId` だけで、view の型の変更に影響されない（DC-155-8、9）。
  - 依存：なし
  - 範囲：`main/module/catalog/application/OrderableVariantQueryService.java`（`:3,39`）、`main/module/catalog/application/port/OrderableVariantQueryRepository.java`（`:3,22`）、`main/module/catalog/application/model/view/OrderableVariantView.java`（新規）、`main/module/catalog/infrastructure/persistence/JooqOrderableVariantQueryRepository.java`（`:4-5,51,66,75,88-97`）、`main/module/catalog/api/OrderCatalogApi.java`（`:30-32`）、`main/module/catalog/api/ReportCatalogApi.java`（`:39-44`）。`it/module/catalog/api/OrderCatalogApiTest.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-02-06** [テスト] 店舗向けの商品の並び順の変換の単体テストを書き、レッドを確認する。公開の並び順（3 値）から application の並び順への変換について、全ての値の対応を確認する。テスト名に AC-277 を入れる。クラスの名前は統合テストの `StoreCatalogApiTest` と衝突させない（`StoreCatalogApiSortTest`）（DC-155-8、9）。
  - 依存：T155-02-05
  - 範囲：`test/module/catalog/api/StoreCatalogApiSortTest.java`（新規）
- [x] **T155-02-07** T155-02-06 のテストが通る実装を書く。店舗向けの一覧の view `StoreProductSummaryView` を `catalog/application/model/view/` に、絞り込み条件 `StoreProductListQuery`（入れ子の列挙 `Sort`）を `catalog/application/model/query/` に作る。一覧と検索の経路は、これらを使う。経路のクラスは範囲に挙げた。`StoreCatalogApi` が公開の型に変換し、並び順の変換を switch 式で書く。`JooqStoreProductQueryRepository.orderBy` の switch は application の列挙に付け替える。`PageResult.map` で変換する（DC-155-8、9）。
  - 依存：T155-02-06
  - 範囲：`main/module/catalog/application/StoreProductQueryService.java`（`:5-7,55,67`）、`main/module/catalog/application/port/{ProductSearch,StoreProductQueryRepository}.java`（`ProductSearch.java:5,28`、`StoreProductQueryRepository.java:5-7,31-32`）、`main/module/catalog/application/model/view/StoreProductSummaryView.java`（新規）、`main/module/catalog/application/model/query/StoreProductListQuery.java`（新規）、`main/module/catalog/infrastructure/persistence/{JooqStoreProductQueryRepository,PostgresProductSearch,StoreProductSummaries}.java`（`JooqStoreProductQueryRepository.java:63,94,107,134-140,156,166`、`PostgresProductSearch.java:6,49`、`StoreProductSummaries.java:4,39-44`）、`main/module/catalog/api/StoreCatalogApi.java`（`:40,51`）。`it/module/catalog/api/StoreCatalogApiTest.java`、`StoreProductSearchTest.java` は、変更せず、テストが通ることを確認する。
- [ ] **T155-02-08** 店舗向けの商品の詳細の view `StoreProductDetailView`（入れ子 `Variant`）を `catalog/application/model/view/` に作る。詳細の経路（`StoreProductQueryService`、`StoreProductQueryRepository` の詳細、`JooqStoreProductQueryRepository`）はこの view を使う。`StoreCatalogApi` が `StoreProductDetail` に変換する。移した後に、catalog の application、port、infrastructure が `api` の型を import していないことを確認する（DC-155-8、9）。
  - 依存：T155-02-07
  - 範囲：`main/module/catalog/application/StoreProductQueryService.java`（`:78`）、`main/module/catalog/application/port/StoreProductQueryRepository.java`（`:42`）、`main/module/catalog/application/model/view/StoreProductDetailView.java`（新規）、`main/module/catalog/infrastructure/persistence/JooqStoreProductQueryRepository.java`（`:107,117`）、`main/module/catalog/api/StoreCatalogApi.java`（`:61`）。
- [x] **T155-02-09** 注文のレポートの view `OrderReportView`（`order/application/model/`）を作る。`OrderDetailView` と同じく domain の値を持つ record で、配送料の税額を含む。`OrderReportQueryService` が返し、`OrderReportApi` が view から変換する。`Result` を削除する。金額は `Money` で持つ（DC-155-10）。
  - 依存：T155-02-02
  - 範囲：`main/module/order/application/OrderReportQueryService.java`（`:5,47-49,73`）、`main/module/order/application/model/OrderReportView.java`（新規）、`main/module/order/api/OrderReportApi.java`（`:42-65,89,102,113`）。`it/module/order/api/OrderReportApiTest.java`、`it/module/reporting/application/ReportCollectionTest.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-02-10** 注文の一覧の view `OrderSummaryView` を、`OrderQueryRepository` の入れ子から `order/application/model/` の最上位の record に出す。`Filter` は port に残す。`OrderQueryService`、`JooqOrderQueryRepository`、4 つの presentation のクラスの import を修正する（DC-155-11）。
  - 依存：T155-02-09
  - 範囲：`main/module/order/application/port/OrderQueryRepository.java`（`:61`）、`main/module/order/application/model/OrderSummaryView.java`（新規）、`main/module/order/application/OrderQueryService.java`（`:129`）、`main/module/order/infrastructure/persistence/JooqOrderQueryRepository.java`（`:6,21,36,46`）、`main/module/order/presentation/{AdminOrderController,StoreOrderController,AdminOrderMapper,StoreOrderMapper}.java`（`AdminOrderController.java:14,79-81,104-106`、`StoreOrderController.java:13,99-101`、`AdminOrderMapper.java:25-27`、`StoreOrderMapper.java:32-33`）。
- [x] **T155-02-11** 配送と支払の port の型を application の型にする。`ShippingDirectory.Shipment` を `order/application/model/OrderShipment`（入れ子の列挙 `Status`）に、`PaymentDirectory.Status` を `OrderPayment.Status`（入れ子の列挙）にする。port の戻り値をこの型にし、腐敗防止層の実装が api の型を直接変換する。`isReturned()` を移す。`OrderDetailView` と `OrderPayment` は port の型を使わなくなる。mapper の import が生成した `Shipment` と衝突しない名前にする。移した後に、`order/application/model` が 9 クラスになり、presentation と `application.model` が `application.port` を import していないことを確認する（DC-155-11）。
  - 依存：T155-02-10
  - 範囲：`main/module/order/application/port/{ShippingDirectory,PaymentDirectory}.java`（`ShippingDirectory.java:50,57,65`、`PaymentDirectory.java:80,89,95`）、`main/module/order/application/model/{OrderShipment,OrderPayment,OrderDetailView}.java`（`OrderPayment.java:3,17`、`OrderDetailView.java:3,38`）、`main/module/order/application/OrderQueryService.java`（`:8-12,91,108,140-158,170`）、`main/module/order/application/shipment/{OrderShipmentService,OrderShipmentCommandService}.java`（`OrderShipmentService.java:5-6,49`、`OrderShipmentCommandService.java:5,31,48`）、`main/module/order/infrastructure/directory/{ShippingApiDirectory,PaymentApiDirectory}.java`（`ShippingApiDirectory.java:31,37,48-51,78-85`、`PaymentApiDirectory.java:55,73-85`）、`main/module/order/presentation/{AdminOrderMapper,StoreOrderMapper}.java`（`AdminOrderMapper.java:49-50,110,153-154,284,312`、`StoreOrderMapper.java:245-246,258-259,313`）。
- [ ] **T155-02-12** [P] storefront の view と条件の型を `storefront/application/model/` に移す。port の `CartView`、`CatalogCategory`、`ProductCondition` と、application の直下の `ProductSummaryView`、`ProductDetailView` である。名前は変えない。port の戻り値をこの型にする。`CatalogProduct`、`CatalogProductDetail` は port に残す。`StorefrontMapperTest`、`OrderApiDirectoryTest` の import を付け替える。移した後に、`storefront/application` の直下が 3 クラス、`port` が 5 クラスになることを確認する（DC-155-11）。
  - 依存：なし
  - 範囲：`main/module/storefront/application/{StorefrontService,ProductSummaryView,ProductDetailView}.java`（`StorefrontService.java:54,75-86,96-97,105-106,118-126`）、`main/module/storefront/application/port/{CartView,CatalogCategory,ProductCondition,CatalogDirectory,OrderDirectory}.java`、`main/module/storefront/infrastructure/directory/{OrderApiDirectory,CatalogApiDirectory}.java`（`OrderApiDirectory.java:36,42-90`、`CatalogApiDirectory.java:44-47,79-81,88-94,120-122`）、`main/module/storefront/presentation/StorefrontMapper.java`（`:25-27,46-55,66-70,78,120,133,188-244`）、`test/module/storefront/infrastructure/directory/OrderApiDirectoryTest.java`（`:4`）、`test/module/storefront/presentation/StorefrontMapperTest.java`（`:4`）。
- [ ] **T155-02-13** [P] [テスト] reporting の性別と履歴の状態を固定する単体テストを追加する。`ReportGender.of` の全ての値（MALE、FEMALE、OTHER、UNDISCLOSED、null）の対応と、`StatusHistoryEntry` の判定（出荷を示すか、配送状態を返すか）を確認する。今のコードで通ることを確認し、一時的に実装か期待値を変えて、失敗しうることを確認してから元に戻す。テスト名に AC-277、278 を入れる（DC-155-12）。
  - 依存：なし
  - 範囲：`test/module/reporting/domain/ReportGenderTest.java`（新規）、`test/module/reporting/domain/StatusHistoryEntryTest.java`
- [ ] **T155-02-14** reporting の注文時の性別とキャンセルの種類を列挙で受け渡す。新しい列挙 `ReportBuyerGender`（MALE、FEMALE、OTHER、UNDISCLOSED）を domain に追加し、`ReportOrder.buyerGender` と `SegmentTotal.gender` を列挙にする。`ReportGender.of` を `ReportBuyerGender` を受ける switch 式にする。`ReportOrder.cancellationKind` と `StatusHistoryEntry.cancellationKind` を `ReportCancellationKind` にする。腐敗防止層の変換は列挙を返す switch 式にする。DB の文字列との変換は `infrastructure/persistence/` だけに置く。`EnumColumnConstraintTest` の対応を付け替える（DC-155-12）。
  - 依存：T155-02-13
  - 範囲：`main/module/reporting/domain/{ReportOrder,StatusHistoryEntry,ReportGender,ReportBuyerGender}.java`（`ReportOrder.java:37,39`、`ReportGender.java:37-47`）、`main/module/reporting/application/{ReportEventHandler,CustomerBreakdownQueryService}.java`（`ReportEventHandler.java:36,38,50`、`CustomerBreakdownQueryService.java:73`）、`main/module/reporting/application/port/ReportQueryRepository.java`（`:77`）、`main/module/reporting/infrastructure/directory/OrderApiDirectory.java`（`:53,59,97-127`）、`main/module/reporting/infrastructure/persistence/{JooqReportOrderRepository,JooqReportQueryRepository}.java`（`JooqReportOrderRepository.java:72-76,130-140`、`JooqReportQueryRepository.java:109-124`）、`test/module/reporting/domain/{ReportGenderTest,StatusHistoryEntryTest}.java`、`main/module/reporting/application/model/view/GenderSales.java` と `main/module/reporting/presentation/ReportMapper.java`（変更せず、テストが通ることを確認する）、`test/module/reporting/infrastructure/directory/OrderApiDirectoryTest.java`（`:26-45`）、`it/EnumColumnConstraintTest.java`（`:57,58,63`）
- [ ] **T155-02-15** reporting の状態の履歴の `from`、`to` を列挙で持つ。`ReportOrderStatus` と `DeliveryStatus` が実装する sealed interface `HistoryStatus` を domain に追加する。`StatusHistoryEntry` の `from`、`to` をこの型にし、`marksShipment()` と `deliveryStatus()` を型の判定にする。`ReportEventHandler`、`ReportCollectionService` から `.name()` と文字列からの変換を削除する。DB の文字列との変換（`kind` に合う列挙の読み込み）は `JooqReportOrderRepository` だけに置く。`StatusHistoryEntryTest` の入力を列挙にする（DC-155-12）。
  - 依存：T155-02-14
  - 範囲：`main/module/reporting/domain/{StatusHistoryEntry,HistoryStatus,ReportOrderStatus,DeliveryStatus}.java`（`StatusHistoryEntry.java:21,29-31,38-43`）、`main/module/reporting/application/{ReportEventHandler,ReportCollectionService}.java`（`ReportCollectionService.java:105-110`）、`main/module/reporting/infrastructure/persistence/JooqReportOrderRepository.java`（`:72-76,95-97`）、`test/module/reporting/domain/StatusHistoryEntryTest.java`。`it/module/reporting/application/ReportCollectionTest.java` は DB の値が変わらないので、変更せず、テストが通ることを確認する。
- [ ] **T155-02-16** [P] 公開の API の検査を実装に合わせる。`ModuleRoleTest.apiInterfacesAreImplementedInApi`（対象が 0 件で常に通る）を削除する（DC-155-13、14）。
  - 依存：なし
  - 範囲：`test/architecture/ModuleRoleTest.java`（`:66-74`）
- [ ] **T155-02-17** 対象が 0 件になる構成の検査を修正する。`levelAPresentationDoesNotDependOnAggregates`（payment）、`domainDependsOnNoOtherLayer`（notification、storefront）、`entryLayersAndInfrastructureAreIndependent` の 1 つ目（notification）は、その層を持つモジュールだけで繰り返し、`allowEmptyShould` を外す。層を持つモジュールを求める補助を追加する。他の 5 つの `allowEmptyShould` も外す。外すと失敗するものがあれば、理由を調べて報告し、spec 担当のセッションが plan に書く。外した規則について、対象が 0 件のとき失敗することを確認する（DC-155-14）。
  - 依存：T155-02-16
  - 範囲：`test/architecture/ModuleRoleTest.java`（`:50`）、`test/architecture/ModuleStructureTest.java`（行番号は段階 1 の変更でずれるので、規則の名前で探す）、`test/architecture/SchemaOwnershipTest.java`（`:91`）
- [ ] **T155-02-18** `ModuleStructureTest` に 2 つのテストメソッドを追加する。application は `api` に依存しない。presentation と `application.model` は `application.port` に依存しない。`allowEmptyShould` を付けない。違反を修正した後に有効にし、一時的に違反を入れて、失敗することを確認する（DC-155-15、16）。
  - 依存：T155-02-01〜15、T155-02-17
  - 範囲：`test/architecture/ModuleStructureTest.java`
- [ ] **T155-02-19** [バックエンドの書き方](../../../rules/backend-coding.md) を修正する。バックエンドの書き方 第 1.1 節「公開の API」を、`api/` の interface を持たない `@Component` のクラスが application の service に委譲する、という実装に合わせる。規則の一覧の該当行も修正する。層の依存の表の presentation の行を「application（`port/` を除く）」にする。[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.4 節の表の `reporting.domain` の数を 16 から 18 に修正する。規則の一覧に、application と `api`、presentation と `port` の規則の行を追加する（DC-155-13、15、16）。
  - 依存：T155-02-18
  - 範囲：`docs/rules/backend-coding.md`（`:7-32,44,58-64,76,98-103`）
- [ ] **T155-02-20** 各モジュールの設計書の「構成」を、段階 2 のコードに合わせる。order（`model/` の 9 クラス、`port/`）、cart（`model/`）、media（`model/`）、catalog（`model/view/`、`model/query/`）、storefront（`model/`、`port/`）、reporting（`ReportBuyerGender`、`HistoryStatus`）である。storefront の設計書の port の型に関する記述（`:35`）を、application の型に修正する。DC-155-45 のため、次の 3 点も個別に修正する。決済の設計書の「構成」の `model/` を、コードの `model/view/` に合わせる。カートの設計書の「構成」と公開の表の利用者を、注文だけにする（コードの `cart/api/package-info.java` と一致する）。税の設計書の公開の表と `tax/api/package-info.java` の利用者に、注文を追加する。
  - 依存：T155-02-15、T155-02-18
  - 範囲：`docs/design/modules/order.md`（`:149-179`）、`docs/design/modules/cart.md`（`:53-65`）、`docs/design/modules/media.md`（`:68-80`）、`docs/design/modules/catalog.md`（`:140-155`）、`docs/design/modules/storefront.md`（`:15-22,35`）、`docs/design/modules/reporting.md`（`:23,25,95-101,126-139`）、`docs/design/modules/payment.md`（`:173` の付近の `model/`）、`docs/design/modules/tax.md`（`:73` の公開の表）、`backend/ecsite/src/main/java/com/ecsite/module/tax/api/package-info.java`、`backend/ecsite/src/main/java/com/ecsite/module/cart/api/package-info.java`（利用者の記述を確認する）
- [ ] **T155-02-21** AC 一覧のテストの列を `node scripts/ci/ac-check.mjs --write` で再生成し、`ac-check.mjs` が通ることを確認する（AC-277、278）。
  - 依存：T155-02-20
  - 範囲：`docs/design/acceptance-criteria.md`
- [ ] **T155-02-22** 段階のレビューと完了の確認。段階のレビューは profile「厚め」で、一般の観点は汎用 2 回、重要な観点は高性能と汎用で実施する。重要な観点は、ファイルをまたぐ影響である。判定は spec 担当のセッションが行う。確認の内容は次のとおり。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。pitest は、reporting の domain の列挙を変えたため実行する。基準（90.2、許容 0）を下回るときは、そのクラスの単体テストを追加する。
  - 依存：T155-02-01〜21
