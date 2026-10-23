# 段階 5：レベル A の構成、reporting の domain の分割、店舗の service のトランザクション

- 前提：段階 2、段階 3、段階 4。段階 5 は段階 4 の後に行い、並行しない。理由：在庫の設計書とバックエンドのアーキテクチャを、両方の段階が変えるため。
- 完了条件：DC-155-33〜42 を満たす。DC-155-34 は、利用者が選んだ推奨の案で満たす（[plan](../plan.md) 第 10 節）。店舗の service が読み取り専用のトランザクションで動き、書き込みの経路がないことをレビューで確認した。関係する文書を更新した。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。
- 参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 10 節、[spec](../spec.md) の DS-155-7 と DC-155-33〜42、DN-155-1〜5、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.1 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.4 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.5 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 4.1 節

## タスク

実装は実装役のサブエージェントに任せる。`[リスク]` のタスク（T155-05-09）の実装役は高性能にし、実装の後に高性能のレビューを行う（T155-05-10）。パスの書き方は、[plan](../plan.md) の共通の決定の最後の項目のとおりである。

T155-05-01、02、04、08、09 は、変えるファイルが重ならないので、互いに `[P]` である。エラーの定義の移動（T155-05-06、07）は多くのモジュールのファイルを変えるので、他のコードのタスクの後に行う。

- [x] **T155-05-01** [P] catalog の application を分ける。商品、バリアント、画像、公開の 7 クラス（`ProductCommandService`、`ProductQueryService`、`ProductPublicationService`、`VariantCommandService`、`ProductImageService`、`ProductImageCommandService`、`ProductLoader`）を `application/product/` に、カテゴリの 2 クラスを `application/category/` に移す。`ProductLoader` は package-private のままにする。直下は `OrderableVariantQueryService`、`StoreProductQueryService` の 2 つになる（DC-155-33）。
  - 依存：なし
  - 範囲：`main/module/catalog/application/`（9 クラス）、`main/module/catalog/presentation/{ProductController,CategoryController}.java`（`ProductController.java:20-24`、`CategoryController.java:11-12`）、`main/module/catalog/api/{StoreCatalogApi,ReportCatalogApi}.java`（`StoreCatalogApi.java:5`、`ReportCatalogApi.java:4`）
- [x] **T155-05-02** [P] 販売可能数の参照を参照の service に移す。`StockReservationService.sellableQuantities` を `StockQueryService` に移す（名前は変えない）。`StockQueryService` に `Clock` を注入し、`StockReservationService` から参照の Repository を削除する。`StockAvailabilityApi` は `StockQueryService` に、`StockReservationApi` は参照を `StockQueryService`、引当を `StockReservationService` に委譲する（DC-155-35）。
  - 依存：なし
  - 範囲：`main/module/inventory/application/{StockReservationService,StockQueryService}.java`（`StockReservationService.java:49-51,77-86`、`StockQueryService.java:212-260`）、`main/module/inventory/api/{StockAvailabilityApi,StockReservationApi}.java`（`:34`、`:35`）。`it/module/inventory/api/StockReservationApiTest.java` と `StockOrderApiTest.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-05-03** 商品画像の追加の確認を参照の service の 1 か所にする。商品の参照の service に、バージョンなしで画像を加えられるか確認する操作を追加し、商品の取り出しにバージョンなしの取り出しを追加する。画像のアップロードの要求は、それを使い、集約を直接読まない。アップロードの要求から読み取り専用のトランザクションの注釈と、使われなくなった import を削除する。404（商品なし）と 400（20 枚）の応答が変わらないことを確認する（DC-155-36）。
  - 依存：T155-05-01
  - 範囲：`main/module/catalog/application/product/{ProductImageService,ProductQueryService,ProductLoader}.java`（`ProductImageService.java:59-66,79-89,146-149`、`ProductQueryService.java:57-59`、`ProductLoader.java:41-48`）。`it/module/catalog/presentation/ProductImageApiTest.java`（`:176-408`）は、変更せず、テストが通ることを確認する。
- [x] **T155-05-04** [P] 商品カタログのイベントのリスナーが、Repository を直接使わない形にする。在庫の作成と削除を持つ用途の名前の service `StockRegistrationService`（`inventory/application/`）を作り、リスナーはそれに委譲する。冪等の作成（既にあれば何もしない）は変えない（DC-155-37）。
  - 依存：なし
  - 範囲：`main/module/inventory/application/CatalogEventHandler.java`（`:22,31,40,50`）、`main/module/inventory/application/StockRegistrationService.java`（新規）。`it/module/inventory/application/CatalogEventHandlerTest.java` は、変更せず、テストが通ることを確認する。
- [ ] **T155-05-05** 在庫の設計書を今のコードに合わせる（DC-155-34。利用者が推奨の案を選んだ）。「在庫（集約）」の記述を、在庫数を DB の差分の加算で更新し、集約で読み書きしないことに修正する。バックエンドのアーキテクチャ 第 2 節とバックエンドの書き方 第 1.1 節のレベル A の定義に、「整合性を DB の更新の条件で保つモジュールは、集約のクラスを持たなくてよい」の例外と理由を書く。
  - 依存：なし（段階 4 の T155-04-04 の後に行う）
  - 範囲：`docs/design/modules/inventory.md`（`:7,11,13,16,40,105-106`）、`docs/design/backend-architecture.md`（バックエンドのアーキテクチャ 第 2 節）、`docs/rules/backend-coding.md`（バックエンドの書き方 第 1.1 節、`:68`）
- [ ] **T155-05-06** エラーの定義を全て `domain/` の直下に置く。`CategoryErrorCode`、`ProductErrorCode`（catalog）、`InventoryErrorCode`、`OrderErrorCode`、`PaymentErrorCode`、`TaxErrorCode` を移し、参照する main、test、integrationTest、testFixtures の import と package 行を修正する。同じパッケージの package-private の参照は、移動後に import が必要になる。エラーのメッセージのキーが変わらないことを確認する（DC-155-38、39）。
  - 依存：T155-05-01〜04、T155-05-08、T155-05-09
  - 範囲：`main/module/catalog/domain/{category/CategoryErrorCode,product/ProductErrorCode}.java`、`main/module/inventory/domain/stock/InventoryErrorCode.java`、`main/module/order/domain/order/OrderErrorCode.java`、`main/module/payment/domain/payment/PaymentErrorCode.java`、`main/module/tax/domain/taxcategory/TaxErrorCode.java`。参照するファイル（重なりを除いて 52）：main、`test/module/*/domain/`、`test/common/message/MessageInterpolationTest.java`（`:3-12,53-57`）、`it/` の 6 ファイル、`fix/` の 1 ファイル。`main/common/persistence/ConstraintViolationTranslator.java`（`:19`）の説明の名前も修正する。
- [ ] **T155-05-07** order の `domain/order` と値のパッケージの循環をなくす。`BuyerProfile` と `Cancellation`（値のパッケージから `Gender`、`CancellationKind` を使う 2 つ）を `domain/order/` に移し、参照する import と package 行を修正する。`AdminOrderMapper`、`StoreOrderMapper` の本文の完全修飾名と、`MessageInterpolationTest` の完全修飾名の文字列のうち、移したクラスを指すものを修正する（DC-155-39）。
  - 依存：T155-05-06
  - 範囲：`main/module/order/domain/order/vo/{BuyerProfile,Cancellation}.java`、`main/module/order/domain/order/Order.java`、`main/module/order/presentation/{AdminOrderMapper,StoreOrderMapper}.java`（`AdminOrderMapper.java:175,213,229,241,325`、`StoreOrderMapper.java:180,205,221`）、`test/common/message/MessageInterpolationTest.java`（`:53-57`）。`BuyerProfile` を使う 9 ファイル、`Cancellation` を使う 6 ファイルの import。
- [ ] **T155-05-08** [P] reporting の domain を分ける。`domain/snapshot/` に ReportOrder、ReportOrderStatus、ReportCancellationKind、DeliveryStatus、HistoryKind、StatusHistoryEntry、ReportBuyerGender、HistoryStatus（8）を、`domain/sales/` に AgeGroup、Basis、DailySales、Granularity、ReportPeriod、SalesTrend、TrendBucket、TrendPoint、ReportGender（9）を置く。`ReportingErrorCode` は直下に残す。依存の向きが `sales` から `snapshot` への一方向であることを確認する。参照する 37 ファイルの import と、動かすクラスの単体テストの package 行を修正する（DC-155-41）。
  - 依存：なし
  - 範囲：`main/module/reporting/domain/`（17 クラス）、`main/module/reporting/application/`、`main/module/reporting/application/port/`、`main/module/reporting/infrastructure/`、`main/module/reporting/presentation/` の import、`test/module/reporting/domain/{AgeGroupTest,BasisTest,ReportPeriodTest,StatusHistoryEntryTest,ReportGenderTest}.java`、`it/module/reporting/` の import。`scripts/ci/report/testdata/pit-mutations.xml` は固定のテスト用データなので変えない。
- [ ] **T155-05-09** [P] [リスク] 店舗の service に、クラス全体の `@Transactional(readOnly = true)` を付ける。理由：他のモジュールの結果を組み合わせる service は読み取り専用のトランザクションにするため（バックエンドの書き方 第 5.2 節）。外部サービスを呼ばないことと、全ての経路（商品の一覧、検索、詳細、カテゴリ、在庫の有無、カートの見積もり、店舗の情報）が既に読み取り専用であることを確認する（DC-155-42）。
  - 依存：なし
  - 範囲：`main/module/storefront/application/StorefrontService.java`。統合テスト（`it/module/storefront/presentation/`）は、変更せず、テストが通ることを確認する。
- [ ] **T155-05-10** [リスク] T155-05-09 の後のレビュー（profile「厚め」：高性能）。トランザクションの伝播（内側の読み取り専用が外側に加わる）、書き込みの経路がないこと、リクエストの間に接続を持つ影響を確認する。
  - 依存：T155-05-09
- [ ] **T155-05-11** [バックエンドの書き方](../../../rules/backend-coding.md) を修正する。バックエンドの書き方 第 1.1 節の構成の図で、エラーの定義を「`<モジュール>ErrorCode`（集約ごとに分けてよい）」にする。バックエンドの書き方 第 1.4 節の表から `reporting.domain` の行を削除し、レベル B でもクラスが多いときは用途で分けてよいことを、バックエンドの書き方 第 1.1 節のレベルの表に書く。バックエンドの書き方 第 1.5 節に、商品の一覧の税込価格の絞り込みと並べ替えのために SQL で計算する例外を書く。端数は切り捨てで、税の計算と同じである。例外の理由は [plan](../plan.md) 第 5 節のとおりで、税の計算との一致は `PriceInclTaxFieldTest` で確認している（DC-155-38〜41）。
  - 依存：T155-05-05、T155-05-06〜08
  - 範囲：`docs/rules/backend-coding.md`（`:16,44-58,61,68-69,98-112,147-152`）
- [ ] **T155-05-12** 各モジュールの設計書の「構成」を、段階 5 のコードに合わせる。catalog（`application/product/`、`application/category/`、`domain/` 直下のエラーの定義）、inventory（`StockRegistrationService`、参照の service、エラーの定義）、order、payment、tax（エラーの定義の置き場所、order の値のパッケージ）、reporting（`domain/snapshot/`、`domain/sales/`）である。AC 一覧のテストの列を `node scripts/ci/ac-check.mjs --write` で再生成し、`ac-check.mjs` が通ることを確認する。
  - 依存：T155-05-11
  - 範囲：`docs/design/modules/catalog.md`（`:55-68,140-155`）、`docs/design/modules/inventory.md`（`:81-83,100-106`）、`docs/design/modules/order.md`（`:149-165`）、`docs/design/modules/payment.md`（`:173`）、`docs/design/modules/tax.md`（`:66`）、`docs/design/modules/reporting.md`（`:126-139`）、`docs/design/acceptance-criteria.md`
- [ ] **T155-05-13** 段階のレビューと完了の確認。段階のレビューは profile「厚め」で、一般の観点は汎用 2 回、重要な観点は高性能と汎用で実施する。重要な観点は、ファイルをまたぐ影響である。判定は spec 担当のセッションが行う。確認の内容は次のとおり。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。pitest は、エラーの定義と reporting の domain を移動したため実行する。基準（90.2、許容 0）を下回るときは、そのクラスの単体テストを追加する。
  - 依存：T155-05-01〜12
