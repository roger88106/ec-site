# Plan 155：バックエンドのモジュールの構成と依存の向きを整える

ステータス：草案　対象：[spec.md](spec.md)

前提：spec 155 が確定した。実装は spec 17 のマージ（`09730eeb`）の後に始める。文書の書き方は skill `docs`（[SKILL.md](../../../.claude/skills/docs/SKILL.md)）と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`（[SKILL.md](../../../.claude/skills/review/SKILL.md)）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 共通コードと設定 | 第 1 節 | DS-155-1（DC-155-1〜7、43） |
| 層の依存の向き | 第 2 節 | DS-155-2（DC-155-8〜16） |
| モジュールの間の呼び出しと設定の型 | 第 3 節 | DS-155-3（DC-155-17〜19）、DS-155-4（DC-155-21〜23） |
| テスト専用の操作と複製 | 第 4 節 | DS-155-5（DC-155-25、26）、DS-155-6（DC-155-29） |
| レベル A の構成 | 第 5 節 | DS-155-7（DC-155-33〜42） |
| 注文の AC のテスト | 第 6 節 | DS-155-9（DC-155-47、48） |
| 規則と設計書 | 第 7 節 | DS-155-8（DC-155-43〜46） |
| 段階と確認の方法 | 第 8 節、第 9 節 | 全て |
| 利用者に確認すること | 第 10 節 | DC-155-34 |

DC の列は「155-1」の形で書く。spec の番号 155 を付けた DC-155-1 の略である。

欠番の DC-155-20、24、27、28、30〜32 は、#85 の spec に移した。本書では扱わない。

共通の決定：

- 実装は spec 17 のマージの後に行う。spec 17 で入った基準は次のとおり。バックエンドの line は 98.09、branch は 85.26、domain の mutation は 90.2 である。許容は順に 0.1、0.3、0 である。値は `scripts/ci/report/quality-baseline.json` にある。mutation の対象は `com.ecsite.module.*.domain.*` である。
- domain のクラスを追加または移動する段階（段階 1 の表示順の再採番、段階 2 の reporting、段階 5）は、段階の最後に `./gradlew pitest` を実行して基準と比べる。下回るときは、そのクラスの単体テストを追加する。テストを弱めない。
- API の契約（`api/openapi/`）とマイグレーション（`db/migration/`）を変えない（DN-155-2）。確認方法は、段階 6 のレビューで、spec の最初の commit からの差分にこの 2 つのディレクトリのファイルがないことを確認する。
- 振る舞いを変えない（DN-155-1）。既存の統合テストが今の振る舞いを固定している。先に固定のテストを追加するのは、単体テストがなく、列挙の対応を変える次の 3 か所だけにする。reporting の性別と履歴の状態、catalog の並び順の変換、order の見積もりの変換の移動である。
- 構成の検査（ArchUnit）の規則は、違反を修正した後に有効にする。有効にした後、一時的に違反を入れて失敗することを確認する（DN-155-4）。確認の方式は、違反する例のクラスをテストの中に置き、規則が検出することを恒久のテストにする。例は `NamingTest.java:56-67` にある。この方式が使えない規則は、手動で一時的に違反を入れて実行し、結果を commit の本文に書く。
- 構成の検査に追加する規則には、全て `allowEmptyShould(true)` を付けない（DC-155-14）。規則を追加するタスクは、この項目に従う。
- 実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` は DC-155-42（店舗の service を読み取り専用のトランザクションにする）だけである。理由：トランザクションを扱うため。このタスクの実装役は高性能にし、実装の後に高性能のレビュー（profile「厚め」）を実施する。金額、セキュリティ、データの移行、外部サービスの振る舞いは変えない。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。
- 段階の最後のタスクは、段階のレビューと全体の検査である。段階のレビューは profile「厚め」で、一般の観点を汎用で 2 回、重要な観点を高性能と汎用で 1 回ずつ実施する。重要な観点は、ファイルをまたぐ影響である。
- 段階の間では、確認のために中断しない。
- 文書は、変えたコードと同じ段階で修正する。モジュールの設計書の「構成」、バックエンドの書き方、共通コードの設計書が対象である。最後の段階で全体を照合する（DC-155-44〜46）。
- AC 一覧のテストの列は、テストのクラスを移したら `node scripts/ci/ac-check.mjs --write` で修正する。AC は変えない（DN-155-3）。
- 並行する #156（文書）が、共通コード・ログ・可観測性と各モジュールの設計書を変えうる。後でマージする側が `develop` を取り込んで解消する。
- パスは、断りがなければ `backend/ecsite/src/` からの相対で書く。`main/`、`test/`、`it/`、`fix/` は、`main/java/com/ecsite/`、`test/java/com/ecsite/`、`integrationTest/java/com/ecsite/`、`testFixtures/java/com/ecsite/` の略である。文書のパスはリポジトリのルートからの相対で書く。

## 1. 共通コードと設定（DS-155-1：DC-155-1〜7、43）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 1、2 | `config/` 直下のイベントの再発行と片付けの 6 クラス（`EventCleanupConfig`、`EventCleanupJob`、`EventPublicationMetrics`、`EventPublicationRecords`、`EventResubmissionConfig`、`EventResubmissionJob`）を移す。`@Scheduled` の式にある設定の型の Bean の名前の文字列 2 か所を修正する。`config/` 直下は 6 になる | `main/config/event/` | `./gradlew check`（統合テストがアプリケーションを起動し、Bean の名前の誤りを検出する） |
| 1、2 | テスト 4 つを `config/event` のパッケージに移す。`CleanupJobSettingsTest` は 4 モジュールの設定を 1 つのファイルで見ているので、片付けのジョブの部分だけを `config/event/` の新しいテストに分ける ※1 | `test/config/event/`、`it/config/event/` | 移したテストと、残る `CleanupJobSettingsTest` が通る |
| 1 | `config/security`（11）は分けず、バックエンドの書き方 第 1.4 節の表に行を追加する ※2 | [バックエンドの書き方](../../rules/backend-coding.md) 第 1.4 節 | レビュー |
| 3、4 | 表示順の再採番（`DisplayOrders`）を、モジュールの domain の直下に移す。テスト `DisplayOrdersTest` も移す。バックエンドの書き方 第 1.1 節に「モジュールの domain の直下には、複数の集約が使う値を置く」を 1 行追加する | `main/module/catalog/domain/`、`test/module/catalog/domain/` | `DisplayOrdersTest`、段階 1 の最後の `./gradlew pitest` |
| 3、4 | LIKE の検索の文字の扱い（`LikePatterns`）を、使う 2 クラスの隣に移し、package-private にする。バックエンドの書き方 第 1.4 節の表の `catalog.infrastructure.persistence` を 13 から 14 に修正する | `main/module/catalog/infrastructure/persistence/` | 統合テスト `StoreProductSearchTest`、`ProductListApiTest` の `escapesWildcards` |
| 5 | ログインした人の情報を返す操作（`MeController`）は `config/security/` に残す。`ModuleStructureTest.restControllerOnlyInPresentation` の対象を `com.ecsite..` に広げ、`MeController` を完全修飾名で除く。バックエンドの書き方 第 2.1 節に例外と理由を書く | `test/architecture/ModuleStructureTest.java`、[バックエンドの書き方](../../rules/backend-coding.md) 第 2.1 節 | 構成の検査。別の場所の `@RestController` を一時的に置いて失敗することを確認する |
| 6、7 | `ModuleStructureTest` に 2 つの規則を追加する。共通コードと設定はモジュールに依存しない。モジュールは設定に依存しない。今の違反は 0 | `test/architecture/ModuleStructureTest.java`。規則の一覧と依存の表に行を追加する | 構成の検査。一時的に違反を入れて失敗することを確認する |
| 43 | 共通コードの設計書：エラーの詳細、認証の設定の値の型、契約の操作の一覧を一覧に追加する。表示順の再採番と LIKE の検索の行を削除する。イベントのクラスの置き場所を修正する。調査のメモの 2 行を削除し、基準はバックエンドの書き方 第 1.5 節に「共通コードには、2 つ以上のモジュールが使うものだけを置く」を追加して指す | [共通コード・ログ・可観測性](../../design/common.md)、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.5 節 | レビュー。`doc-refs.mjs`、`glossary-ids.mjs` |

※1：理由は、設定の型が package-private で、同じパッケージのテストだけが使えるため。

※2：理由は、分けると、認証と認可の設定のクラスが組み立てる 5 クラス（認可の対応表、認可の判定 2 つ、契約の操作の一覧）を public にしなければならないため。バックエンドの書き方 第 1.4 節の既存の行と同じ理由である。

- 振る舞いは変わらない。`@ConfigurationProperties` の接頭辞、`application.yml`、環境変数は、パッケージと無関係で変わらない。
- 表示順の再採番は、置き場所を domain の直下にする。理由：商品とカテゴリの 2 つの集約が使い、どちらかの集約の `vo/` に置くと、片方の集約がもう片方のパッケージに依存するため。
- 表示順の再採番は mutation の対象に入る。単体テストは既にあるが、基準を下回るか上回るかは実行しないと分からない。
- 構成の検査の対象を広げても、生成コード（`com.ecsite.api..`）に `@RestController` を持つクラスはないので、`MeController` 以外の違反は検出されない。
- 共通コードと設定の jOOQ の生成クラスへの依存は、既存の `SchemaOwnershipTest` が禁じる。移動後の 6 クラスも、イベント記録の表を名前で指す今の書き方を続ける。

呼び出し元とテスト：

| 移すもの | 呼び出し元とテスト |
|----------|--------------------|
| イベントの 6 クラス | main：`config/EventCleanupJob.java:72-73`、`config/EventResubmissionJob.java:63-64` の `@Scheduled` の式の文字列。設定とスクリプトに、クラス名とパスはない。test：`config/CleanupJobSettingsTest.java:29,52,53,68,74`（片付けのジョブの設定の型を使い、クラス名の文字列を `Class.forName` で読む）、`config/EventResubmissionSettingsTest.java:27,40`。integrationTest：`config/EventCleanupJobTest.java:51`、`config/EventResubmissionJobTest.java:81,105,108,114,260-276`。文書：`docs/design/common.md:41,42,72`、`docs/design/backend-architecture.md:107`（持ち主の名前だけで、パスは書かない）、AC 一覧の AC-286〜293 はクラス名だけで、変わらない |
| 表示順の再採番 | main：`module/catalog/domain/category/Category.java:6,144`、`module/catalog/domain/product/Product.java:7,337,593,613`。test：`common/model/value/DisplayOrdersTest.java`。integrationTest と testFixtures に参照はない。文書：`docs/design/common.md:24,31`、`docs/design/modules/catalog.md:140-156` |
| LIKE の検索の文字の扱い | main：`JooqProductQueryRepository.java:6,106,107,111,114`、`PostgresProductSearch.java:5,56,60,61`（どちらも移動先と同じパッケージ）。クラスを直接使う単体テストはない。文書：`docs/design/common.md:26,31` |
| ログインした人の情報を返す操作 | test：`test/architecture/ModuleStructureTest.java:144-151`。文書：`docs/rules/backend-coding.md:7-32,116-122`、`docs/design/common.md:44` |
| 共通コードと設定の依存の規則 | test：`test/architecture/ModuleStructureTest.java`（`CLASSES` は `com.ecsite` の全体を持つ）。文書：`docs/rules/backend-coding.md:7-16,58-64`、`docs/design/common.md:15` |

## 2. 層の依存の向き（DS-155-2：DC-155-8〜16）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 8、9 | order：見積もりの service は、カート ID と見積もり結果を持つ view `CartQuoteView` を返す。公開の API `OrderQuoteApi` が公開の型 `CartQuote` に変換する。理由の変換（`toReason` など）も API に移し、`CartQuoteServiceTest` を `OrderQuoteApiTest` に移す。AC-277 のテスト名は残す | `main/module/order/application/model/CartQuoteView.java`、`main/module/order/api/OrderQuoteApi.java`、`test/module/order/api/OrderQuoteApiTest.java` | 移した単体テスト、統合テスト（`StorefrontCartTest` など） |
| 8、9 | cart：`CartService.findContents` は `CartContentsView` を返す。`CartContentsApi` が `CartContents` に変換する。`MergedCart` は動かさない | `main/module/cart/application/model/CartContentsView.java` | 統合テスト（`CartApiTest`、`MemberCartApiTest`）。`CartContents` の利用者は変わらない |
| 8、9 | media：`MediaService.prepareImage` はアセット ID を返し、`registerImage`、`discardImage` はアセット ID を受ける。`requestImageUpload` は `ImageUploadView` を返す。`MediaApi` が `PreparedImage`、`ImageUpload` に変換する | `main/module/media/application/model/ImageUploadView.java` | `MediaApiTest` |
| 8、9 | catalog：`OrderableVariantView`、`StoreProductSummaryView`、`StoreProductDetailView` を view に、`StoreProductListQuery`（入れ子の列挙 `Sort`）を query に作る。参照の service、port 3 つ、infrastructure の実装はこれらを使う。`OrderCatalogApi`、`StoreCatalogApi` が公開の型に変換する。並び順の変換（3 値の switch 式）に単体テストを追加する（AC-277。名前は統合テストの `StoreCatalogApiTest` と衝突させない。例：`StoreCatalogApiSortTest`） | `main/module/catalog/application/model/view/`、`main/module/catalog/application/model/query/`、`test/module/catalog/api/` | 新しい単体テスト、統合テスト（`OrderCatalogApiTest`、`StoreCatalogApiTest`、`StoreProductSearchTest`） |
| 10 | order：レポート用の view `OrderReportView` を作る。`OrderDetailView` と同じく domain の値を持つ record で、配送料の税額を含む。`OrderReportQueryService` が返し、`OrderReportApi` が view から変換する | `main/module/order/application/model/OrderReportView.java` | `OrderReportApiTest`、`ReportCollectionTest` |
| 11 | order：`OrderSummaryView` を `OrderQueryRepository` の入れ子から最上位の record に出す（絞り込み条件 `Filter` は port に残す）。`ShippingDirectory.Shipment` を `OrderShipment`（入れ子の列挙 `Status`）に、`PaymentDirectory.Status` を `OrderPayment.Status`（入れ子の列挙）にする。port の戻り値をこれらの型にし、腐敗防止層の実装が直接変換する。`order/application/model` は 5 から 9 になる | `main/module/order/application/model/` | 統合テスト（`Admin*`、`Member*`、`Shipment*` の API のテスト） |
| 11 | storefront：`CartView`、`CatalogCategory`、`ProductCondition`（port から）と `ProductSummaryView`、`ProductDetailView`（application の直下から）を `application/model/` に移す。名前は変えない。port の戻り値をこの型にする。`CatalogProduct`、`CatalogProductDetail` は port に残す | `main/module/storefront/application/model/` | `StorefrontMapperTest`、`OrderApiDirectoryTest`、統合テスト |
| 12 | reporting：キャンセルの種類は既存の `ReportCancellationKind` で持つ。注文時の性別は新しい列挙 `ReportBuyerGender`（MALE、FEMALE、OTHER、UNDISCLOSED）で持ち、`ReportGender.of` を列挙の switch 式にする。状態の履歴の `from`、`to` は、`ReportOrderStatus` と `DeliveryStatus` が実装する sealed interface `HistoryStatus` で持つ。※1 | `main/module/reporting/domain/`、`main/module/reporting/infrastructure/persistence/` | 新しい単体テスト、`EnumColumnConstraintTest`、段階 2 の最後の `./gradlew pitest` |
| 13 | バックエンドの書き方 第 1.1 節「公開の API」と規則の一覧の記述を、今の実装に合わせる。今の実装では、`api/` の `@Component` のクラスが application の service に委譲する。`ModuleRoleTest.apiInterfacesAreImplementedInApi` を削除する | [バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節、`test/architecture/ModuleRoleTest.java` | 構成の検査 |
| 14 | モジュールごとに繰り返す規則のうち、層を持たないモジュールで対象が 0 件になる 3 つの規則は、その層を持つモジュールだけで繰り返し、`allowEmptyShould` を外す ※2。他の 5 つも外す。外せない規則があれば、実装役が理由を報告し、spec 担当のセッションが plan に書く | `test/architecture/ModuleRoleTest.java`、`ModuleStructureTest.java`、`SchemaOwnershipTest.java` | 構成の検査。外した規則の対象が 0 件なら失敗する |
| 15、16 | `ModuleStructureTest` に 2 つのテストメソッドを追加する。application は `api` に依存しない。presentation と `application.model` は `application.port` に依存しない。層の依存の表の presentation の行を「application（`port/` を除く）」に修正する | `test/architecture/ModuleStructureTest.java`、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節 | 構成の検査。一時的に違反を入れて失敗することを確認する |

※1：DB の文字列との変換は `infrastructure/persistence/` だけに置く。先に `[テスト]` で、`ReportGender.of` の全ての値の対応と `StatusHistoryEntry` の判定の単体テストを追加する。理由：単体テストがなく、列挙の対応を変えるため、mutation の基準（90.2、許容 0）を下回るおそれがあるため。

※2：対象が 0 件になる 3 つの規則は次のとおり。`levelAPresentationDoesNotDependOnAggregates`（payment に presentation がない）、`domainDependsOnNoOtherLayer`（notification と storefront に domain がない）、`entryLayersAndInfrastructureAreIndependent` の 1 つ目（notification に presentation、api、event がない）。

- 層を持つモジュールの一覧は、クラスから層の有無を求める補助を `ModuleStructureTest` に追加して得る。
- 他の 5 つの `allowEmptyShould` は、全体も全てのモジュールも対象が 0 件にならない。外せない理由は調査では見つからなかった。外して失敗するものがあれば、実装役が理由を調べて報告し、spec 担当のセッションが plan に書く。
- order の見積もりの変換には単体テストがある。移動の前に、見積もり結果の全ての項目と、買えない理由の全ての値の対応を固定するテストを追加する（`[テスト]`）。
- cart と media と catalog の各 service の呼び出しは、`@Transactional(readOnly = true)` または `MANDATORY` のまま変わらない。変換は戻った後に API で行う。view は値だけを持つので、トランザクションの外で読んでも問題ない。
- 公開の API の戻り値の型は変えない。利用者（各モジュールの腐敗防止層）は書き換えない。
- 同じ違反が catalog の infrastructure にもある（層の依存の表は、infrastructure から `api` を許さない）。port の戻り値を view にすれば、DC-155-8 で同時に解消する。
- order の見積もりの view に domain の値を持たせる。理由：`CheckoutView` と `OrderDetailView` が同じ作りで、見積もり結果の入れ子の型を複製しないため。
- 列挙の変換は switch 式で書く（バックエンドの書き方 第 1.3 節）。
- 並び順の変換の単体テストは、全ての値の対応を確認する。
- 規則（DC-155-15、16）は、違反を修正した後に有効にする。この節の変更が全て終わった後のタスクで追加する。

呼び出し元とテスト：

| 変えるもの | 呼び出し元とテスト |
|------------|--------------------|
| order の見積もり | main：`order/application/checkout/CartQuoteService.java:3,57,68-123`、`order/api/OrderQuoteApi.java:32-34`。利用者（変更なし）：`storefront/infrastructure/directory/OrderApiDirectory.java:4,42,53,70,82`。test：`order/application/checkout/CartQuoteServiceTest.java:3,20`（`toReason` を直接呼ぶ）。`storefront/infrastructure/directory/OrderApiDirectoryTest` は変更なし。文書：AC 一覧 AC-277（`ac-check.mjs --write`）、`docs/design/modules/order.md:149-165` |
| cart の内容 | main：`cart/application/CartService.java:6,146-150`、`cart/api/CartContentsApi.java:31`。利用者（変更なし）：`order/infrastructure/directory/CartApiDirectory.java:3,29-30`。直接使うテストはない。文書：`docs/design/modules/cart.md:57-61` |
| media の画像 | main：`media/application/MediaService.java:4-5,71,81,88,99,108,117,160`、`media/api/MediaApi.java:36-37,51-52,63-64,73-74`。利用者（変更なし）：`catalog/infrastructure/directory/MediaApiDirectory.java`。test：`it/module/media/api/MediaApiTest.java` は変更なし。文書：`docs/design/modules/media.md:68-80` |
| catalog の参照 | main：`catalog/application/OrderableVariantQueryService.java`、`StoreProductQueryService.java`、`application/port/{OrderableVariantQueryRepository,ProductSearch,StoreProductQueryRepository}.java`、`infrastructure/persistence/{JooqOrderableVariantQueryRepository,JooqStoreProductQueryRepository,PostgresProductSearch,StoreProductSummaries}.java`、`api/{OrderCatalogApi,StoreCatalogApi}.java`。利用者（変更なし）：cart、order、storefront、reporting の `infrastructure/directory/CatalogApiDirectory.java`。test：書き換えはない。`it/module/catalog/api/OrderCatalogApiTest.java:8`、`StoreCatalogApiTest.java:96-211` は公開の型だけを使う。文書：`docs/design/modules/catalog.md:140-152` |
| order のレポート | main：`order/application/OrderReportQueryService.java:48-49,73`、`order/api/OrderReportApi.java:42-65,89,102,113`。利用者（変更なし）：`reporting/infrastructure/directory/OrderApiDirectory.java`。test：`it/module/order/api/OrderReportApiTest.java`、`it/module/reporting/application/ReportCollectionTest.java` は変更なし。文書：`docs/design/modules/order.md:149-179` |
| order の一覧、配送、支払 | main：`order/application/OrderQueryService.java:8-12,91,108,129,140-158`、`order/application/model/{OrderDetailView,OrderPayment}.java`、`order/application/port/{OrderQueryRepository,ShippingDirectory,PaymentDirectory}.java`、`order/application/shipment/{OrderShipmentService,OrderShipmentCommandService}.java`、`order/infrastructure/directory/{ShippingApiDirectory,PaymentApiDirectory}.java`、`order/infrastructure/persistence/JooqOrderQueryRepository.java`、`order/presentation/{AdminOrderController,StoreOrderController,AdminOrderMapper,StoreOrderMapper}.java`。test：入れ子の型を直接使うテストはない。文書：`docs/design/modules/order.md:160-161` |
| storefront の型 | main：`storefront/application/{StorefrontService,ProductSummaryView,ProductDetailView}.java`、`application/port/{CartView,CatalogCategory,ProductCondition,CatalogDirectory,OrderDirectory}.java`、`infrastructure/directory/{OrderApiDirectory,CatalogApiDirectory}.java`、`presentation/StorefrontMapper.java`。test：`storefront/infrastructure/directory/OrderApiDirectoryTest.java:4`、`storefront/presentation/StorefrontMapperTest.java:4`（`CartView` の import を付け替える）。文書：`docs/design/modules/storefront.md:19-21,35` |
| reporting の列挙 | main：`reporting/domain/{ReportOrder,StatusHistoryEntry,ReportGender}.java`、`reporting/application/model/view/GenderSales.java`、`reporting/presentation/ReportMapper.java`（この 2 つは `ReportGender` の値を使うだけで、型は変わらないので変更せず、テストが通ることを確認する）、`application/{ReportEventHandler,CustomerBreakdownQueryService,ReportCollectionService}.java`、`application/port/ReportQueryRepository.java`、`infrastructure/directory/OrderApiDirectory.java`、`infrastructure/persistence/{JooqReportOrderRepository,JooqReportQueryRepository}.java`。test：`reporting/domain/ReportGenderTest.java`（新規）、`reporting/domain/StatusHistoryEntryTest.java`、`reporting/infrastructure/directory/OrderApiDirectoryTest.java:26-45`、`it/EnumColumnConstraintTest.java:57,58,63`。`ReportCollectionTest`、`CustomerBreakdownQueryTest`、`ReportTestData` は DB の値が変わらないので変更なし。文書：`docs/design/modules/reporting.md:23,25,95-101,126-139`、AC 一覧 AC-277、278 |
| 公開の API の検査 | test：`test/architecture/ModuleRoleTest.java:66-74`。文書：`docs/rules/backend-coding.md:11,44,76` |

パッケージの大きさ（直下の `.java`。目安 10、上限 20）：

| パッケージ | 今 | 後 |
|------------|----|----|
| `order.application.model` | 5 | 9 |
| `cart.application.model`（新） | 0 | 1 |
| `media.application.model`（新） | 0 | 1 |
| `catalog.application.model.view` | 5 | 8 |
| `catalog.application.model.query` | 1 | 2 |
| `storefront.application.model`（新） | 0 | 5 |
| `storefront.application.port` | 8 | 5 |
| `storefront.application` | 5 | 3 |
| `reporting.domain` | 16 | 18（段階 5 で分ける） |

## 3. モジュールの間の呼び出しと設定の型（DS-155-3：DC-155-17〜19、DS-155-4：DC-155-21〜23）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 17、18 | `ModuleStructureTest` に規則を追加する。他のモジュールの `api` を使うのは `infrastructure.directory` だけにする。許可の一覧は 1 件で、`member.api.CurrentMemberIds` を presentation から使う。規則はモジュールごとに作り、対象を「そのモジュールの `api` を、外のクラスが使う依存」と記述する。バックエンドの書き方 第 1.3 節に同じ例外を書く | `test/architecture/ModuleStructureTest.java`、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.3 節 | 構成の検査。許可の一覧にない使い方を一時的に置いて失敗することを確認する |
| 19 | 腐敗防止層の実装の public を外す（cart の `CatalogApiDirectory`、`StockApiDirectory`、storefront の `CatalogApiDirectory`、`OrderApiDirectory`、`StockApiDirectory`）。規則：`*Directory` を実装する `infrastructure.directory` のクラスは public にしない。`TaxRateScheduleCache` は Directory を実装しないので対象外にする。その `evict` は #85 で扱う | `main/module/cart/infrastructure/directory/`、`main/module/storefront/infrastructure/directory/`、`test/architecture/NamingTest.java` | 構成の検査。public な例のクラスで検出することを恒久のテストにする |
| 21 | `ShippingProperties` を出荷の infrastructure に、`NotificationProperties` を `infrastructure/mail/` に移す。`ShopProperties` は application に残す。理由：使う層が application になるため（DC-155-23） | `main/module/shipping/infrastructure/`、`main/module/notification/infrastructure/mail/` | 起動のテスト（`RequiredPropertiesTest`）、統合テスト |
| 22 | `StorefrontConfig` を `storefront/infrastructure/` に移す。バックエンドの書き方 第 6.1 節に「モジュールの `@Configuration` は infrastructure（またはその下）に置く」を追加し、`ConfigurationPropertiesTest` に検査を追加する | `main/module/storefront/infrastructure/`、`test/architecture/ConfigurationPropertiesTest.java` | 構成の検査。infrastructure の外に置いた例で失敗することを確認する |
| 23 | 店舗：`StorefrontService` に店舗の情報を返す操作を追加し、`ShopView` を返す。presentation は view を受ける。レポート：新しい `ReportSettingsQueryService` が `ReportingProperties` を読み、`ReportSettingsView` を返す。`ReportController` は service を使う。バックエンドの書き方 第 6.1 節に「presentation は設定の値の型を受け取らない」を追加し、構成の検査を追加する（`com.ecsite.module.*.presentation..` が、`@ConfigurationProperties` を付けたクラスに依存しない。api は対象にしない） | `main/module/storefront/application/model/ShopView.java`、`main/module/reporting/application/ReportSettingsQueryService.java`、`main/module/reporting/application/model/view/ReportSettingsView.java`、`test/architecture/ConfigurationPropertiesTest.java` | 統合テスト `StorefrontShopTest`、`ReportPermissionApiTest`。構成の検査 |

- 許可の一覧の例外の理由：`CurrentMemberIds` は、token を読む presentation 向けのクラスで、使う層が presentation に限られるため。
- 設定の型は、使う層に置く。同じ層の設定のクラス（`ShippingConfig` など）が、`@EnableConfigurationProperties` で設定の型を有効にする。移動による Bean の名前の変更で壊れる式はない。通知、出荷、店舗の設定の型の完全修飾名を書いた文字列は、`src` に 0 件だった。
- 店舗の情報の応答への変換は、手動で書き、メソッドチェーンの setter の形に従う（`ModuleRoleTest` が位置引数の構築を禁じる）。
- 腐敗防止層の実装の検査で、対象が 0 件にならないことを確認する。
- 設定の型の移動後、`RequiredPropertiesTest` の `ShopProperties` の import は変わらない。

呼び出し元とテスト：

| 変えるもの | 呼び出し元とテスト |
|------------|--------------------|
| 会員 ID の取り出し | main：`cart/presentation/CartController.java:12,36,65,79,92,103,114`、`order/presentation/StoreCheckoutController.java:9,54,64,77,91`、`order/presentation/StoreOrderController.java:9,51,61,73,85,98`、`storefront/presentation/StorefrontCartController.java:5,33,43`。いずれも変更しない。検査の対象外のテスト：`it/module/cart/presentation/CartApiTest.java:9` ほか。文書：`docs/rules/backend-coding.md:86-94`、`docs/design/modules/member.md:8,55`（既に例外と合っている） |
| 腐敗防止層の実装 | main：5 クラスの宣言（`cart/.../CatalogApiDirectory.java:17`、`StockApiDirectory.java:17`、`storefront/.../CatalogApiDirectory.java:29`、`OrderApiDirectory.java:20`、`StockApiDirectory.java:20`）。外からの参照はない。test：`storefront/infrastructure/directory/OrderApiDirectoryTest.java:20` は同じパッケージから呼ぶので変更なし |
| 出荷、通知の設定の型 | main：`shipping/application/ShippingProperties.java`、`shipping/infrastructure/ShippingConfig.java:22`、`notification/application/NotificationProperties.java`、`notification/infrastructure/mail/SmtpMailer.java`、`notification/infrastructure/NotificationConfig.java:11`。参照するテストはない。文書：`docs/design/modules/shipping.md:127-139`、`docs/design/modules/notification.md:45-53` |
| 店舗の設定のクラス、情報 | main：`storefront/application/StorefrontConfig.java:10`、`storefront/presentation/StorefrontShopController.java:5,20,34`、`StorefrontMapper.java:24,147-149`。test：`config/RequiredPropertiesTest.java:7,97` は変更なし、`it/module/storefront/presentation/StorefrontShopTest.java:19-32`。文書：`docs/design/modules/storefront.md:15-22,40-42`、`docs/design/glossary.md:211`（名前は変わらない） |
| レポートの設定 | main：`reporting/presentation/ReportController.java:11,57,127`、`reporting/presentation/ReportMapper.java`、`reporting/application/SalesTrendQueryService.java:27,39`（変更なし）。test：`it/module/reporting/presentation/ReportPermissionApiTest.java`。文書：`docs/design/modules/reporting.md:126-139` |

## 4. テスト専用の操作と複製（DS-155-5：DC-155-25、26、DS-155-6：DC-155-29）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 25、26 | 配送業者の mock（`MockCarrierTracking`）から記録（`started`、`started()`、`Started`）を削除する。testFixtures に `TestCarrierTracking` を作る。`CarrierTracking` を実装して mock を包み、記録を持つ。統合テストの設定 `TestCarrierTrackingConfig`（`@Primary`、`TestPaymentGatewayConfig` と同じ形）で差し替え、`IntegrationTest` の `@Import` に追加する。`ShippingApiTest` は包む実装を使う | `main/module/shipping/infrastructure/mock/`、`fix/module/shipping/infrastructure/mock/TestCarrierTracking.java`、`it/TestCarrierTrackingConfig.java` | `ShippingApiTest`、`ExternalServiceSelectionTest` |
| 29 | 期限切れの片付けのループとログを、`common/scheduling/` の新しい static の final クラス `ExpiredDataCleanup` の 1 つのメソッドにする。cart、inventory、order の 3 つのジョブが使う。ログの文面とロガーは変えない（ロガーと文の名詞を渡す）。`@Scheduled`、`@SchedulerLock` はジョブに残す。単体テストを `common/scheduling/` に追加する ※1 | `main/common/scheduling/ExpiredDataCleanup.java`、`test/common/scheduling/ExpiredDataCleanupTest.java` | 新しい単体テスト（先にレッド）、3 つのジョブの統合テスト、`CleanupJobSettingsTest` |

※1：ループは、一定の件数ずつ削除し、削除した件数が上限と等しい間繰り返す。ログは、1 件以上なら info、0 件なら debug で出す。

- 配送業者の mock の呼び出しの確認は、包む実装で行う。理由：本番の mock にテスト専用の記録を持たせないため。
- `ExternalServiceSelectionTest` が mock の型を public として使うので、mock のクラスの可視性は変えない。
- 設定の型（3 つの record）は、接頭辞と既定値が違い、`CleanupJobSettingsTest` が型と接頭辞を対応づけて検査するため、共通にしない。
- 共通のクラスは `StaticOnlyClassTest` の対象になる。final にして、コンストラクターを private にする。
- 片付けのループを共有しないジョブがある。決済の期限切れのジョブは 1 回だけ処理し、イベントの片付けのジョブは 1 文で削除する。この 2 つは対象外にする。
- テスト専用の記録を削除すると、本番のクラスの行数が減り、カバレッジの比率が変わりうる。段階の最後に CI のカバレッジの検査で、基準を下回らないことを確認する。

呼び出し元とテスト：

| 変えるもの | 呼び出し元とテスト |
|------------|--------------------|
| 配送業者の mock の記録 | main：参照なし。test：`config/ExternalServiceSelectionTest.java:10,90`（mock の型を使い続ける）。integrationTest：`module/shipping/api/ShippingApiTest.java:10,52,136-137,144-147`、`IntegrationTest.java:26-27`（`@Import`）。testFixtures：既存の `module/payment/infrastructure/mock/TestPaymentGateway.java`、`module/shipping/infrastructure/mock/TestCarrierNotifications.java`。文書：`docs/design/modules/shipping.md:113-114`、`docs/design/backend-architecture.md`（第 8 節の mock の記述） |
| 片付けのジョブ | main：`cart/application/CartCleanupJob.java:44-55`、`inventory/application/StockReservationCleanupJob.java:44-55`、`order/application/checkout/CheckoutCleanupJob.java:45-56`。test：`config/CleanupJobSettingsTest.java:67-79`（ジョブのクラス名と `cleanUp` の注釈を読む。変更なし）。integrationTest：`cart/application/CartCleanupJobTest.java:63,93-156`、`inventory/application/StockReservationCleanupJobTest.java:51,83-101`、`order/application/checkout/CheckoutCleanupJobTest.java:76,122-185`（ログの文面を正規表現で確認する。変更なし）。文書：`docs/design/common.md:24-34`、`docs/design/modules/cart.md:33`、`order.md:27`、`inventory.md:59,102` |

## 5. レベル A の構成（DS-155-7：DC-155-33〜42）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 33 | catalog の application を、商品、バリアント、画像、公開の 7 クラス（`ProductLoader` は package-private のまま）と、カテゴリの 2 クラスに分ける。直下には `OrderableVariantQueryService`、`StoreProductQueryService` の 2 つが残る | `main/module/catalog/application/product/`、`main/module/catalog/application/category/` | `./gradlew check` |
| 34 | 利用者に確認する（第 10 節）。推奨は、在庫をレベル A のまま、設計書を今のコードに合わせ、レベル A の定義に「整合性を DB の更新の条件で保つモジュールは、集約のクラスを持たなくてよい」の例外を書くことである。在庫の設計書の修正は第 7 節にある。集約のクラスは作らない | [在庫モジュール](../../design/modules/inventory.md)、[バックエンドのアーキテクチャ](../../design/backend-architecture.md) 第 2 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節 | レビュー |
| 35 | `StockReservationService.sellableQuantities` を `StockQueryService` に移す（名前は変えない）。`StockAvailabilityApi`、`StockReservationApi` の委譲先を替える | `main/module/inventory/application/` | `StockReservationApiTest`、`StockOrderApiTest` |
| 36 | 商品の参照の service に、バージョンなしで画像を加えられるか確認する操作を追加し、商品の取り出しにバージョンなしの取り出しを追加する。画像のアップロードの要求は、それを使い、集約を直接読まない。トランザクションの注釈を外す | `main/module/catalog/application/product/` | `ProductImageApiTest`（404、20 枚の確認は変わらない） |
| 37 | 在庫に、用途の名前の新しい service `StockRegistrationService`（在庫の作成と削除）を作り、商品カタログのイベントのリスナーはそれに委譲する | `main/module/inventory/application/` | `CatalogEventHandlerTest` |
| 38、39 | 全てのモジュールのエラーの定義を `domain/` の直下に置く。catalog の 2 つ、inventory、order、payment、tax の 6 つを移す。catalog は集約ごとの 2 つのままにし、規則の図の「エラーの定義」を「集約ごとに分けてよい」と修正する。order の `BuyerProfile`、`Cancellation`（値のパッケージから集約の列挙を使う 2 つ）を `domain/order/` に移し、`domain/order` と値のパッケージの循環をなくす ※1 | `main/module/*/domain/`、`main/module/order/domain/order/` | `./gradlew check`、段階 5 の最後の `./gradlew pitest` |
| 40 | バックエンドの書き方 第 1.5 節に、税込価格の SQL の計算の例外を書く。理由：絞り込みと並べ替えを DB で行うために必要で、一致を確認するテストが既にあるため。コードは変えない | [バックエンドの書き方](../../rules/backend-coding.md) 第 1.5 節 | レビュー |
| 41 | reporting の domain を分ける。注文の写しと状態の履歴は `domain/snapshot/`（8）、集計と期間は `domain/sales/`（9）、直下に `ReportingErrorCode`（1）。パッケージの名前はこの 2 つに確定する。バックエンドの書き方 第 1.4 節の表から `reporting.domain` の行を削除し、レベル B でもクラスが多いときは用途で分けてよいことを規則に書く ※2。段階 2 の reporting の列挙の変更の後に行う | `main/module/reporting/domain/snapshot/`、`main/module/reporting/domain/sales/` | `./gradlew check`、段階 5 の最後の `./gradlew pitest` |
| 42 | `[リスク]` 店舗の service にクラス全体の読み取り専用のトランザクションを付ける。全ての経路が既に読み取り専用であることを、レビューで確認する | `main/module/storefront/application/StorefrontService.java` | 統合テスト、高性能のレビュー |

※1：移すもの。`CategoryErrorCode`、`ProductErrorCode`、`InventoryErrorCode`、`OrderErrorCode`、`PaymentErrorCode`、`TaxErrorCode`。エラーの定義以外が原因の循環のうち、注文の見積もりの `domain/quote` と `domain/quote/port` の循環は、エラーの定義が原因でないので範囲外にする。

※2：`domain/snapshot/` に、ReportOrder、ReportOrderStatus、ReportCancellationKind、DeliveryStatus、HistoryKind、StatusHistoryEntry、ReportBuyerGender、HistoryStatus を置く。`domain/sales/` に、AgeGroup、Basis、DailySales、Granularity、ReportPeriod、SalesTrend、TrendBucket、TrendPoint、ReportGender を置く。依存の向きは `sales` から `snapshot` への一方向になる。

- 商品の画像の確認が 2 か所にある理由は、画像の追加がバージョンを受けるのに、アップロードの要求は受けないためである。バージョンなしの確認を追加して 1 か所にする。
- 在庫の集約を作らない理由：在庫数は、更新の条件で負の値にならないようにしており、集約に持たせる不変条件がないため。
- エラーの定義を動かしても、メッセージのキーは変わらない。キーは定数の名前から作るため。
- 値のパッケージの 2 クラスを動かすので、完全修飾名を書いた文字列を修正する。`AdminOrderMapper`、`StoreOrderMapper` の本文と、`MessageInterpolationTest` の文字列を確認する。
- 店舗の service の読み取り専用のトランザクションは、リクエストの間、接続を 1 本保持する。外部サービスは呼ばないので、バックエンドの書き方 第 4.1 節に反しない。
- 読み取り専用の経路が書き込みの service を呼ぶと失敗する。今は呼ばない。
- 動かすクラスの単体テストは、パッケージに対応する位置のまま、package 行を合わせて動かす。

呼び出し元とテスト：

| 変えるもの | 呼び出し元とテスト |
|------------|--------------------|
| catalog の application の分割 | main：`catalog/presentation/ProductController.java:20-24`、`CategoryController.java:11-12`、`catalog/api/StoreCatalogApi.java:5,6`、`ReportCatalogApi.java:3,4`。テストから直接の参照はない。文書：`docs/design/modules/catalog.md:68,147-150` |
| 販売可能数の参照 | main：`inventory/api/StockAvailabilityApi.java:34`、`StockReservationApi.java:35`。直接の呼び出しのテストはない。同名の `StockReservationApi.sellableQuantities` の利用者（cart、order の腐敗防止層と統合テスト 14 か所）は、署名が変わらないので影響なし。文書：変更なし |
| 画像の追加の確認 | main：`catalog/application/ProductImageService.java:59-66,79-89,146-149`、`ProductQueryService.java:57-59`、`ProductLoader.java:41-48`、`catalog/presentation/ProductController.java:248`。test：`catalog/domain/product/ProductTest.java:566-567` は変更なし。integrationTest：`catalog/presentation/ProductImageApiTest.java:176-408`。文書：`docs/design/modules/catalog.md:55-68` |
| 在庫のリスナー | main：`inventory/application/CatalogEventHandler.java:22,31,40,50`。integrationTest：`inventory/application/CatalogEventHandlerTest.java`（イベントを発行して表で確認する。変更なし）。文書：`docs/design/modules/inventory.md:81-83,102` |
| エラーの定義 | 参照するファイルの数は、catalog のカテゴリ 6、商品 14、inventory 6、order 14、payment 7、tax 9（重なりを除いて 52）。うち main の import を修正する。test：`common/message/MessageInterpolationTest.java:3-12,53-57`（6 つ全てを import する）、`module/order/domain/order/`、`module/catalog/domain/`、`module/tax/domain/`、`module/inventory/domain/` の単体テスト（package-private の参照を含む）。integrationTest：`PaymentErrorCode` を使う 3 ファイルほか。`common/persistence/ConstraintViolationTranslator.java:19` の説明に名前がある。文書：`docs/rules/backend-coding.md:44-58,61,147-152`、各モジュールの設計書の構成の「エラー」の語（`catalog.md:151-152`、`order.md:165`、`payment.md:173`、`tax.md:66`、`inventory.md:106`） |
| 値の 2 クラス | main：`BuyerProfile` を使う 9 ファイル、`Cancellation` を使う 6 ファイル、`presentation/AdminOrderMapper.java:175,213,229,241,325`、`StoreOrderMapper.java:180,205,221`（完全修飾名を本文に書いている）。test：`common/message/MessageInterpolationTest.java:53-57`。文書：`docs/design/modules/order.md:165` |
| reporting の domain | 参照するファイルは 37（main、test、integrationTest）。test：`reporting/domain/` の `AgeGroupTest`、`BasisTest`、`ReportPeriodTest`、`StatusHistoryEntryTest`（動かす）。`scripts/ci/report/testdata/pit-mutations.xml` の完全修飾名はテスト用の固定データで、変えない。文書：`docs/rules/backend-coding.md:103`、`docs/design/modules/reporting.md:133` |
| 店舗の service | main：`storefront/presentation/StorefrontCartController.java:6,23`、`StorefrontCategoryController.java:5,20`、`StorefrontProductController.java:8,26`。テストが名前で参照することはない。文書：変更なし |
| 税込価格の SQL | 変えるのは文書だけ。参照：`catalog/infrastructure/persistence/PriceInclTaxField.java`、`it/module/catalog/infrastructure/persistence/PriceInclTaxFieldTest.java`、`docs/design/modules/catalog.md:110-120`（既に一致の確認を書いている） |

パッケージの大きさ：

| パッケージ | 今 | 後 |
|------------|----|----|
| `catalog.application` | 11 | 2 |
| `catalog.application.product`（新） | 0 | 7 |
| `catalog.application.category`（新） | 0 | 2 |
| `inventory.application` | 6 | 7 |
| `reporting.domain` | 18 | 1 |
| `reporting.domain.snapshot`（新） | 0 | 8 |
| `reporting.domain.sales`（新） | 0 | 9 |
| `catalog.domain`、`inventory.domain`、`order.domain`、`payment.domain`、`tax.domain` | 0 | 3、1、1、1、1 |

## 6. 注文の AC のテスト（DS-155-9：DC-155-47、48）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 47 | AC-84 のテストを、新しい非トランザクションの統合テストのクラスに移す。カートの変更の API（数量を減らす `PUT`）を呼び、`AsyncEvents.awaitHandled` でカートの変更のイベントの処理を待ってから、販売可能数が戻ることを確認する。品目の追加の後も、注文手続きを始める前にイベントの処理を待つ。理由：後から動いた解除で引当が消えないため。`CheckoutApiTest` から AC-84 のテストと `CheckoutService` の注入を削除する | `it/module/order/presentation/CheckoutCartChangeApiTest.java` | 新しいテスト。一時的に期待値を変えて失敗することを確認する |
| 48 | AC-128 のテスト：持ち主の `sub` を変数に持ち、会員の表から会員 ID を引いて、注文の会員 ID と等しいことを確認する | `it/module/order/presentation/MemberOrderApiTest.java` | 一時的に期待値を変えて失敗することを確認する |

- 2 つとも、今のコードで通るテストの強化である。一時的に期待値を変えて、失敗することを確認する（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。
- 新しいテストは、作ったデータを `@AfterEach` で削除する（`OrderPaymentApiTest` の形）。`CheckoutApiTest` の補助（商品と在庫の作成、注文手続きの開始）を複製するか、共通のクラスにする。実装役が選ぶ。
- AC 一覧は変えない（DN-155-3）。AC-84 のテストの列に新しいクラスを追加する。

呼び出し元とテスト：

| 変えるもの | 呼び出し元とテスト |
|------------|--------------------|
| AC-84 のテスト | integrationTest：`order/presentation/CheckoutApiTest.java:33-37,49,70,152-171`（`releaseReservations` を直接呼ぶのはここだけ）。参照：`order/application/checkout/CartChangedHandler.java:27-29`、`cart/application/CartService.java:253-258`、`AsyncEvents.java`、`OrderPaymentApiTest.java:60-112`。文書：AC 一覧 AC-84（`ac-check.mjs --write`） |
| AC-128 のテスト | integrationTest：`order/presentation/MemberOrderApiTest.java:76,161-166`。参照：`module/member/api/MemberConfirmationTest.java:62-72`（会員を `sub` で引く書き方）。文書：AC 一覧 AC-128 は変えない |

## 7. 規則と設計書（DS-155-8：DC-155-43〜46）

文書は、変えたコードと同じ段階で修正する（共通の決定）。段階 6 で全体をコードと照合する。

| 文書 | 修正 | 段階 |
|------|------|------|
| [共通コード・ログ・可観測性](../../design/common.md) | 一覧の抜け 3 つ、移したクラスの行、イベントの置き場所、調査のメモの置き換え（DC-155-43） | 1 |
| [共通コード・ログ・可観測性](../../design/common.md) | 期限切れの片付けの共通のクラスの行 | 4 |
| [バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.4 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.5 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 2.1 節、規則の一覧、依存の表 | 段階 1 の変更：domain の直下の規則、`config/security` と `catalog.infrastructure.persistence` の行、共通コードの基準、`MeController` の例外、共通コードと設定の依存の規則 | 1 |
| [バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節、規則の一覧、依存の表 | 公開の API の記述（DC-155-13）、application と `api`、presentation と `port` の規則（DC-155-15、16） | 2 |
| [バックエンドの書き方](../../rules/backend-coding.md) 第 1.4 節 | `reporting.domain` の数を 16 から 18 に修正する（段階 5 で行を削除する） | 2 |
| [決済モジュール](../../design/modules/payment.md) の「構成」 | `model/` の記述を、コードの `model/view/` に合わせる（DC-155-45） | 2 |
| [カートモジュール](../../design/modules/cart.md) の「構成」と公開の表、[税モジュール](../../design/modules/tax.md) の公開の表、`tax/api/package-info.java` | カートの公開の API の利用者から店舗を除いて注文だけにし、税の公開の API の利用者に注文を追加する（DC-155-45） | 2 |
| [バックエンドの書き方](../../rules/backend-coding.md) 第 1.3 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 6.1 節 | `api` の例外の許可、設定のクラスの層、presentation が設定の型を受けないこと | 3 |
| [バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.4 節、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.5 節、規則の図 | 在庫の例外（DC-155-34）、エラーの定義、reporting の行の削除と用途で分けてよいこと、税込価格の例外 | 5 |
| 各モジュールの設計書の「構成」 | 段階ごとの変更（order、cart、media、catalog、storefront、reporting、shipping、notification、inventory、payment、tax）。DC-155-44、45 は段階 6 で全体を照合する | 2〜6 |
| [在庫モジュール](../../design/modules/inventory.md) | 「在庫（集約）」の記述（DC-155-34 の推奨の案） | 5 |
| [バックエンドのアーキテクチャ](../../design/backend-architecture.md) 第 2 節、第 8 節 | レベル A の例外、mock とテスト専用の記録の記述 | 4、5 |
| [AC 一覧](../../design/acceptance-criteria.md) | テストの列（`ac-check.mjs --write`）。AC は変えない | 1、2、5 |
| [用語集](../../design/glossary.md) | 追加する語はない。追加するクラスは型の名前で、業務の用語でないため。名前が変わるものがないことを `glossary-ids.mjs` で確認する | 6 |

- 並行する #156 が、共通コードの設計書とモジュールの設計書を変えうる。同じ行を触るので、後でマージする側が `develop` を取り込んで解消する。
- 節の番号を変える文書の移動はない。

## 8. 段階

| 段階 | ファイル | 内容 | DC |
|------|----------|------|----|
| 1 | [tasks/01-common.md](tasks/01-common.md) | 注文の AC のテスト、共通コードと設定、共通コードの設計書 | 155-1〜7、43、47、48 |
| 2 | [tasks/02-layers.md](tasks/02-layers.md) | 層の依存の向き、構成の検査の規則 | 155-8〜16 |
| 3 | [tasks/03-boundaries.md](tasks/03-boundaries.md) | モジュールの間の呼び出し、設定の型と設定のクラス | 155-17〜19、21〜23 |
| 4 | [tasks/04-test-only.md](tasks/04-test-only.md) | 配送業者の mock の記録、片付けのジョブのループ | 155-25、26、29 |
| 5 | [tasks/05-level-a.md](tasks/05-level-a.md) | レベル A の構成、reporting の domain の分割、店舗の service のトランザクション | 155-33〜42 |
| 6 | [tasks/06-review.md](tasks/06-review.md) | 規則と設計書の全体の照合、spec の最後のレビュー、E2E、起票のまとめの案、pull request の前のレビュー、アーカイブ | 155-44〜46 と全て |

- 段階 4 と 5 は、段階 2、3 の後に行う。理由：段階 4、5 が、段階 2、3 で作る設定の型と view の置き場所に依存するため。
- 段階 5 は段階 4 の後に行い、並行しない。理由：在庫の設計書とバックエンドのアーキテクチャを、段階 4 と段階 5 の両方が変えるため。
- 段階 5 の reporting の分割は、段階 2 の reporting の列挙の変更の後に行う。
- 各段階の最後のタスクは、段階のレビュー（profile「厚め」）と、全体の検査の実行である。検査は第 9 節にある。domain に触れた段階（1、2、5）は `./gradlew pitest` も実行する。
- 段階 6 の内容は上の表のとおりである。pull request の作成はタスクにしない（[開発の進め方](../../rules/workflow.md) 第 4.2 節）。
- アーカイブ先は `docs/specs/archive/1to10/155-backend-structure/` である。

## 9. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・統合・構成の検査 | `backend/ecsite/` で `./gradlew check` |
| CI の検査 | `scripts/ci/check-all.sh`（`coverage-baseline`、`doc-refs`、`glossary-ids`、`ac-check`、`java-rules` など） |
| mutation | `backend/ecsite/` で `./gradlew pitest`（段階 1、2、5 の最後） |
| カバレッジ | CI の検査（spec 17 の `coverage-baseline`） |
| API の契約とマイグレーション（DN-155-2） | 段階 6 のレビューで、spec の最初の commit からの差分に `api/openapi/` と `db/migration/` のファイルがないことを確認する |
| E2E | 段階 6 で 1 回。`scripts/dev/e2e.sh` |
| 画面 | 目視は不要。API の応答を変えないため。E2E で確認する |

- スロット（作業ツリーごとの環境）は、他の spec と共有し、同時に 1 つまで使う。
- 段階 6 の全体の検査で、`./gradlew pitest` を全体で実行する。

## 10. 確認すること

利用者が plan と tasks を確認するときに、次を決める。

1. DC-155-34：在庫の扱い。推奨は、レベル A のまま、設計書を今のコードに合わせることである。plan と tasks はこの案で書いた。代案を選んだときに変わるタスクは次のとおり。
   - 在庫の集約のクラスを最小限で作る案：T155-05-05 を、在庫の集約のクラスを作るタスクに替える。集約の単体テストと、mutation の基準の確認を含める。
   - レベル B にする案：T155-05-05 を、在庫の domain を `domain/` に、port を `application/port/` に移すタスクに替える。参照する 44 ファイルを修正し、`ModuleStructureTest` と `ModuleRoleTest` のレベル A の一覧から在庫を外す。
2. （報告）DC-155-20、24、27、28、30〜32 は、issue 担当のセッションが伝えた利用者の決定で、#85 に移した。
