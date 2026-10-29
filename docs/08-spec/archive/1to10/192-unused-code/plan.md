# Plan 192：使われていないコードを削除し、静的解析の検査を広げる

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 192 が確定した。文書の書き方は skill `docs` と[開発の進め方](../../../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../../../rules/notation.md)。レビューは skill `review`。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| テスト専用の操作と可視性 | 第 1 節 | DS-192-1（DC-192-1〜4） |
| 同じ処理を 1 か所に | 第 2 節 | DS-192-2（DC-192-5〜8） |
| NullAway と規則の検査 | 第 3 節 | DS-192-5（DC-192-16〜20） |
| Java の import | 第 4 節 | DS-192-4（DC-192-13〜15） |
| フロントの使われていないもの | 第 5 節 | DS-192-3（DC-192-9〜12） |
| 文書 | 第 6 節 | 全て |
| 段階、確認の方法、並行する spec | 第 7〜9 節 | 全て |
| 利用者に確認すること | 第 10 節 | DC-192-3 |

DC の列は「192-1」の形で書く。spec の番号 192 を付けた DC-192-1 の略である。

共通の決定：

- パスは、断りがなければ `backend/ecsite/src/` からの相対で書く。文書とフロントのパスは、リポジトリのルートからの相対で書く。次の略を使う。

| 略 | パス |
|----|------|
| `main/` | `main/java/com/ecsite/` |
| `test/` | `test/java/com/ecsite/` |
| `it/` | `integrationTest/java/com/ecsite/` |
| `fix/` | `testFixtures/java/com/ecsite/` |
| `tools/` | `tools/java/com/ecsite/` |

- 行の番号は、2026-10-26 の再調査（`develop` の 821af1a5）のものである。実装を始める前の照合（[開発の進め方](../../../../rules/workflow.md) 第 5.6 節）で、今のコードと合わせる。
- 削除・移動する操作の呼び出し元は、テストを含めて本書の表に挙げた（[開発の進め方](../../../../rules/workflow.md) 第 1.4 節）。実装役は、削除の前に IDE の Call Hierarchy か `git grep` で、表の外の呼び出し元がないことを確認する。
- テストを本番の操作で確認する形に替えるときも、確認する条件は変えない（DN-192-3）。替えたテストは、一時的にコードか期待値を変えて、失敗することを確認する。確認の結果は commit の本文に書く。
- 追加した検査は、違反する例で違反を検出できることを確認する（DN-192-4）。CI の検査の script はテストに違反の例を書き、Gradle の検査は一時的に違反を入れて失敗を確認する。
- API の契約（`api/openapi/`）とマイグレーション（`db/migration/`）を変えない（DN-192-2）。確認方法は、最後の段階のレビューで、spec の最初の commit からの差分にこの 2 つのディレクトリのファイルがないことを確認する。
- 実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` は次の 2 つである。このタスクの実装役は高性能にし、実装の後に高性能のレビュー（profile「厚め」）を実施する。
  - token 取り出しの集約（DC-192-8）。理由：認証の判定を変えるため。
  - NullAway の設定の追加（DC-192-16）。理由：直す箇所が main の全体に広がりうるため。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../../../rules/workflow.md) 第 2.1 節）。

## 1. テスト専用の操作と可視性（DS-192-1：DC-192-1〜4）

### 1.1 本番コードから削除する操作（DC-192-1）

| 操作 | 置き場所 | 呼び出し元 | テストの替え方 |
|------|----------|------------|----------------|
| 注文がキャンセル済みか | `main/module/order/domain/order/Order.java:291` | `test/module/order/domain/order/OrderCancellationTest.java:247-249` | 注文の状態がキャンセルであることを確認する |
| 集計基準が注文の状態を数えるか | `main/module/reporting/domain/sales/Basis.java:50` | `test/module/reporting/domain/sales/BasisTest.java:30` | 削除する。`BasisTest` の他のテストが、本番の集計が使う操作を確認しているかを調べ、足りなければ、その操作のテストを追加する |
| 商品の代表画像 | `main/module/catalog/domain/product/Product.java:202` | `test/module/catalog/domain/product/ProductTest.java:545,551,580` | 商品の画像の並びの先頭を確認する |
| 地域 API の郵便番号検索 | `main/module/region/api/RegionLookupApi.java:74`（応答の型 `RegionPostalAddress` を含む） | `it/module/region/api/RegionApiTest.java:81-105` | AC-253、AC-254 のテストを `it/module/region/presentation/StoreRegionApiTest.java` に移す。AC-254 の並び順と、2 つの市区町村にまたがる例は、移す先にないので追加する |

- 応答の型は、他に使われていなければ一緒に削除する。
- 用語集、地域の設計書、AC 一覧の直しは、第 6 節にある。

### 1.2 郵便番号の CSV を読む処理を tools に移す（DC-192-2）

| 決定 | 置き場所 | 確認方法 |
|------|----------|----------|
| `PostalCodeCsv`、`PostalCodeData`、`PostalCodeEntry` を、`main/module/region/infrastructure/source/` から `tools/tools/region/` に移す | 移す先は `RegionMigrationGenerator` と同じパッケージ | `./gradlew generateRegionMigration` で作るマイグレーションが、今の地域のマイグレーションと一致する（DN-192-2） |
| CSV の読み込みのテスト `PostalCodeCsvTest` を `test/tools/region/` に移す。単体テストのソースセットのクラスパスに tools の出力を加える | `build.gradle.kts` の source set の定義、`test/tools/region/PostalCodeCsvTest.java` | `./gradlew test` で実行される。AC-283 の行のテストの列が変わらない |
| 移す 2 つのクラスの Lombok（private のコンストラクタ、`@NonNull`）を、Java の書き方に置き換える。理由：Lombok は main だけで使う規則で、tools のソースセットには Lombok がないため | 移した 3 クラス | `./gradlew compileToolsJava` が通る |

- 統合テストが読む郵便番号の一覧（`tools/resources/region/postal-codes.txt`）は、置き場所を変えない。
- tools は本番の jar に入らないので、構成の検査の対象から外れる。地域モジュールの `infrastructure/source/` は空になるので、パッケージを消す。

### 1.3 その他のテスト専用の操作（DC-192-3）

| 操作 | 置き場所 | 呼び出し元 | 決定 |
|------|----------|------------|------|
| 税率キャッシュの破棄と、クラスの public | `main/module/catalog/infrastructure/directory/TaxRateScheduleCache.java:22,65` | `it/module/catalog/api/StoreCatalogApiTest.java:80`、`StoreProductSearchTest.java:71`、`it/module/storefront/presentation/StorefrontApiTest.java:93`、`StorefrontFreshnessTest.java:93` | 案 A（第 10 節の 1）。破棄を package-private にし、クラスの public を外す。統合テストの同じパッケージにテストの補助クラスを置き、4 つのテストはそれを呼ぶ |
| ファイル保存設定の複製 2 つ | `main/module/media/infrastructure/storage/MediaStorageProperties.java:48,61` | `it/module/media/infrastructure/storage/S3ObjectStorageTest.java:97`、`test/module/media/infrastructure/storage/S3ObjectStorageFailureTest.java:172` | 削除する。テストは、変えたい値を入れて設定の型を直接作る |
| ファイル保存のクライアントを作る static の操作 | `main/module/media/infrastructure/storage/MediaStorageConfig.java:44` | 同じクラスの `@Bean` メソッド（`:35`）、`S3ObjectStorageFailureTest.java:173` | static の操作の中身を `@Bean` メソッドに戻す。テストは `@Bean` メソッド（同じパッケージ）を呼ぶ |
| 最近の出荷を一覧する問い合わせ | port `main/module/shipping/application/port/ShipmentRepository.java:61`、実装 `main/module/shipping/infrastructure/persistence/JooqShipmentRepository.java:103` | `main/module/shipping/infrastructure/mock/MockCarrierController.java:121`、`it/module/shipping/infrastructure/persistence/JooqShipmentRepositoryTest.java:119-132` | port と実装から外し、mock の側の問い合わせのクラス（`main/module/shipping/infrastructure/mock/MockShipmentQuery`）に移す。テストも mock の側（`it/module/shipping/infrastructure/mock/MockShipmentQueryTest`）に移す。DC-181-19 の時刻の決め方は変えない |

- 決済クライアントを組み立てる static の操作は、spec の確定の後に DC-192-3 から外した（第 10 節の 2）。理由：本番の `@Bean` メソッドが使い、テストもビルダーの値で設定の渡り方を確認するので、テスト専用の操作ではないため。

### 1.4 テストのために広げた可視性（DC-192-4）

| クラス | 置き場所 | 他のパッケージの呼び出し元 | 決定 |
|--------|----------|----------------------------|------|
| 決済の期限切れジョブ | `main/module/payment/application/PaymentExpiryJob.java:22` | `it/module/order/presentation/OrderPaymentApiTest.java:76` | package-private にする。統合テストの同じパッケージに、ジョブを Spring のコンテキストから取り出して実行するテストの補助クラス（`it/module/payment/application/PaymentJobs`）を置き、注文のテストはそれを呼ぶ |

- 再調査で、テストか tools だけが外から使う public のクラスが、他に 9 つ見つかった（下の表）。地域の CSV のクラスは第 1.2 節で移すので、表に含めない。実装役は、それぞれが public である理由を確認し、テストのためだけに広げたものを package-private にする。理由があって残したものは、理由と共に段階の報告に書く。

| クラス | 種類 | public でありうる理由 |
|--------|------|------------------------|
| `RealmKeys`、`SecurityProperties`、`ImgproxyProperties` | 設定 | 設定の値の型の作り方 |
| `OrderPaymentService` | application のサービス | なし（確認する） |
| `MockCarrier`、`MockPaymentGateway`、`MockCarrierTracking` | mock | testFixtures からの利用 |
| `AssetReleased`、`TrackingRequested` | イベント | イベントの型 |

## 2. 同じ処理を 1 か所に（DS-192-2：DC-192-5〜8）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 5 | 配送業者の表示名のキーを、出荷モジュールのキー（`ecsite.shipping.carrier.*`）にする。通知のメールと mock の画面が、どちらもこのキーを使う。理由：配送業者は出荷モジュールの型で、表示名も出荷モジュールが決めるため | `main/resources/messages.properties:108-110`、`main/module/notification/application/ShipmentMailComposer.java:32`、`MockCarrierController.java:128` | 通知のメールのテストと mock の画面のテストが通る。`git grep "ecsite.notification.carrier"` が 0 件 |
| 6 | 変換元のファイルの場所（`s3://` のバケットとキー）は、`S3ObjectStorage` の操作が作る。画像変換のクラスは、その操作を呼んで場所を得て、ファイル保存設定の型を持たない。画像変換の port と、それを呼ぶ媒体のサービスは変えない | `main/module/media/infrastructure/converter/ImgproxyImageConverter.java:9,65,75,124`、`main/module/media/infrastructure/storage/S3ObjectStorage.java` | 画像変換のテストが通る。画像変換のクラスが、ファイル保存設定の型を import しない |
| 7 | 片付けのジョブが行う削除の SQL を、`EventPublicationRecords` に移す。片付けのジョブは表の定義を持たない | `main/config/event/EventCleanupJob.java:35-50,76`、`main/config/event/EventPublicationRecords.java` | 片付けと再発行のジョブのテストが通る。`git grep "event_publication" -- backend/ecsite/src/main/java` が `EventPublicationRecords` だけ |
| 8 | 認証情報から token を取り出し、なければ未認証のエラーにする処理を、`common/security/` の 1 つの操作（`CurrentToken`）にする。会員、運営者、ログイン中の利用者の情報を返す API は、それを呼ぶ | `main/common/security/CurrentMember.java:26,44`、`CurrentOperator.java:30`、`main/config/security/MeController.java:37` | 認証の統合テスト（token なし、会員の token、運営者の token）が通る。`git grep "instanceof JwtAuthenticationToken" -- backend/ecsite/src/main` が 1 件 |

- DC-192-7 で、統合テストの補助クラス（`it/config/event/EventPublications.java`）の表の定義は変えない。理由：テストが本番のクラスを経由せずに表を確認するため。
- DC-192-8 は `[リスク]` である。会員の取り出しは、token がないときに空を返す今の振る舞いを残す。

## 3. NullAway と規則の検査（DS-192-5：DC-192-16〜20）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 16 | NullAway に `AcknowledgeRestrictiveAnnotations` を加える。NullAway がエラーを出したら、上書き側の引数に `@Nullable` を付ける。設定のコメントに、何を見つけるかを書く | `build.gradle.kts` の NullAway の設定（`:769-777`） | `./gradlew check` が通る。生成した interface の `@Nullable` の引数を、注釈なしで上書きした例を一時的に入れて、コンパイルが失敗することを確認する |
| 17 | 必須でないヘッダー・パラメータを受ける 3 つの引数には、既に `@Nullable` がある（2026-10-26 に確認した）。コードは変えない | `main/module/payment/infrastructure/stripe/StripeWebhookController.java:38`、`main/module/shipping/infrastructure/carrier/CarrierWebhookController.java:54,55` | 段階 3 のレビューで、`git grep "required = false"` の全ての引数に `@Nullable` があることを確認する。規則の検査にはしない。理由：対象が 3 か所だけで、生成した interface の上書きは DC-192-16 の NullAway が検出するため |
| 18 | 規則の検査に、`@SuppressWarnings` の直前の行が `//` のコメントであることを求める規則を加える。間にある他の注釈は飛ばす。今の 12 か所に理由を書く | `scripts/ci/java-rules.mjs`、テスト、`@SuppressWarnings("unchecked")` のある 11 ファイル | 規則の検査のテストに、理由のない例と、Javadoc だけの例（違反）を書く |
| 19 | 規則の検査にある「JSpecify 以外の `Nullable` を禁じる」規則の対象に、`jakarta.annotation`、`javax.annotation`、`org.springframework.lang` のワイルドカード import を加える | `scripts/ci/java-rules.mjs`、テスト | 規則の検査のテストに、ワイルドカードの import の例を書く |
| 20 | バックエンドの書き方 第 7.1 節に、getter の基準と `Objects.requireNonNull` のメッセージの書き方を書き、今のコードをそろえる。テストの実行方法からバックエンドの書き方 第 7.2 節へリンクする | 第 6 節の表 | 文書のレビュー。`git grep -n "requireNonNull(" -- backend/ecsite/src/main` のメッセージが規則の形にそろう |

DC-192-20 の規則の内容：

| 項目 | 規則 |
|------|------|
| getter の基準 | 状態によらず値がないことがあるもの（会員でないカートの会員 ID など）は `Optional` を返す。状態から有無が決まるもの（キャンセルした注文のキャンセルの記録など）は `@Nullable` を返し、呼び出し側は状態を確認してから `Objects.requireNonNull` で受ける |
| `Objects.requireNonNull` のメッセージ | null でない根拠となる状態を、肯定の文で書く（「キャンセルした注文にはキャンセルの記録がある」）。null のときの失敗を述べる形（「〜がない」）にしない |

- 今のメッセージのうち、規則の形でないのは `main/config/security/OpenApiOperations.java:83,85` の「OpenAPI の契約に servers がない」「〜paths がない」の 2 つである。「契約には servers がある」の形に直す。
- メッセージの規則は、`@Nullable` の値を受ける `Objects.requireNonNull` に当てはめる。値の型の生成で引数を確認するだけで、メッセージのないもの（`main/common/model/value/Operator.java:25,26` など）は対象外にし、バックエンドの書き方にもそう書く。

## 4. Java の import（DS-192-4：DC-192-13〜15）

| 決定 | 置き場所 | 確認方法 |
|------|----------|----------|
| Checkstyle に `UnusedImports` と `CustomImportOrder` を加える | `config/checkstyle/checkstyle.xml` | 違反の例を一時的に入れて、`./gradlew checkstyleMain` などが失敗することを確認する |
| Checkstyle を全てのソースセット（main、test、integrationTest、testFixtures、tools）で実行する。Javadoc の書き漏れの検査は、ファイルのパスで抑止し、今のとおり main だけにする | `build.gradle.kts` の Checkstyle の設定（`:325-343`）、`config/checkstyle/` の抑止の設定 | `./gradlew check` が全てのソースセットの Checkstyle を実行する。test の Javadoc の書き漏れを報告しない |
| import の並びは、今のコードの多くと同じにする（下の表） | バックエンドの書き方 第 5.4 節。「CI と commit の前の hook では検査しない」の文を、import だけは Checkstyle が検査する形に直す | Checkstyle の違反が 0 件 |

| 順 | まとまり | まとまりの中 |
|----|----------|--------------|
| 1 | `java`・`javax` 以外 | 文字コードの順 |
| 2 | `java`・`javax` | 文字コードの順 |
| 3 | static | 文字コードの順 |

- まとまりの間は空行で分ける。再調査では、787 ファイルのうち 735 がこの並びで、違反は 52 ファイルと、使われていない import の 2 件である。

- import を直す commit は、並びの修正だけを入れる。理由：差分が大きく、他の変更と混ざるとレビューで確認しにくいため。
- 直すのは、段階 1〜3 の後にする。理由：段階 1〜3 で変えたファイルの import を、再び直さずに済むため。
- IntelliJ の import 整理の設定をこの並びに合わせる方法を、バックエンドの書き方に書く。

## 5. フロントの使われていないもの（DS-192-3：DC-192-9〜12）

### 5.1 削除するもの（DC-192-9〜11）

| 対象 | 置き場所 | 決定 |
|------|----------|------|
| どこからも使われない API 型の別名 | `frontend/store/src/api/types.ts:17,20,21,23`（`AxisValue`、`Amounts`、`TaxAmount`、`Checkout`）、`frontend/admin/src/api/types.ts:10,30,34,42`（`TaxRate`、`OrderTaxAmount`、`Me`、`PrefectureSales`） | 削除する |
| テストだけが使う API 型の別名 | `frontend/admin/src/api/types.ts:15,25`（`ProductActions`、`OrderSummary`） | 残す。理由：テストが API の応答の形を作るのに使うため。knip もテストを使用箇所として数える。再調査でテストだけが使うとした `StockAdjustment` は、実際はテストが同じ名前の型を自分で定義していて、どこからも使われていなかったので、T192-05-03 で削除した |
| `cn` を再び export するファイル | `frontend/admin/src/lib/utils.ts`、`frontend/store/src/lib/utils.ts`、`frontend/shared/src/lib/cn.ts` | 削除する。店舗画面のレイアウト（`frontend/store/src/app/layout.tsx:7`）は、npm のパッケージ `cn` から import する。shadcn/ui の設定（`components.json` の `utils`）は変えない。理由：2026-09 の shadcn/ui の変更で、CLI が追加する部品はパッケージ `cn` を直接 import し、`lib/utils.ts` は初期化のときに作る 1 行の再 export だけになったため。出典：shadcn/ui の changelog「September 2026 - cn」 |
| 定義したファイルの中だけで使う export | 下の表 | export を外す。外すと使われていないことが分かったものは削除する |
| shadcn/ui の生成物の、使われていない小部品 | `frontend/admin/src/components/ui/`、`frontend/shared/src/ui/` | 削除しない（spec 第 9 節の 1）。knip の export の検査の対象外にする |

再調査で見つけた、定義したファイルの中だけで使う export：

| 置き場所 | export |
|----------|--------|
| `frontend/store/src/auth/config.ts:40`、`auth/index.ts:94` | `ServerConfig`、`CurrentMember` |
| `frontend/store/src/features/catalog/searchQuery.ts:5` | `SearchQuery` |
| `frontend/store/src/features/checkout/placeOrderErrors.ts:8`、`postalCodeFill.ts:4` | `PlaceOrderErrors`、`PostalCodeFill` |
| `frontend/store/src/features/order/orderState.ts:10,24` | `CancellationState`、`PaymentState` |
| `frontend/store/src/components/ui/native-select.tsx:60` | `NativeSelectOptGroup`（shadcn/ui の生成物なので対象外にする） |
| `frontend/admin/src/api/useApiMutation.ts:25` | `ApiMutationOptions` |
| `frontend/admin/src/auth/permissions.ts:9,15,27,35` | `PermissionsLoading`、`PermissionsFailed`、`PermissionsReady`、`Permissions` |
| `frontend/admin/src/components/OptionSelect.tsx:5` | `Option` |
| `frontend/admin/src/features/product/components/ProductFields.tsx:14` | `ProductFieldConstraints` |
| `frontend/admin/src/features/report/period.ts:14` | `Period` |
| `frontend/admin/src/features/product/imageUpload.ts:7,44` | `IMAGE_CONTENT_TYPES`、`MAX_IMAGE_BYTES` |
| `frontend/shared/src/components/AmountsSummary.tsx:6` | `AmountsSummaryValue` |
| `frontend/shared/src/ui/alert.tsx:75`、`badge.tsx:51`、`button.tsx:57` | `AlertAction`、`badgeVariants`、`buttonVariants`（shadcn/ui の生成物なので対象外にする） |
| `e2e/src/auth.ts` | `latestIdpLink`、`Account` |

- 一覧は 2026-10-26 の再調査のものである。実装役は、knip の結果と照合し、一覧の外のものも同じに扱う。

### 5.2 検査（DC-192-12）

| 決定 | 置き場所 | 確認方法 |
|------|----------|----------|
| knip 6 を、ルートの devDependencies に加える。検査の対象は、`frontend/*` と `e2e` の、使われていないファイル、export、依存である | `package.json`、`knip.ts`（新規） | 違反の例（使われていない export）を一時的に入れて、knip が失敗することを確認する |
| 定義したファイルの中だけで使う export も報告する（`ignoreExportsUsedInFile` を有効にしない） | `knip.ts` | 同上 |
| 対象外は、openapi-typescript の型、制約の定数の生成物、shadcn/ui の生成物の export だけにし、`knip.ts` に理由のコメントを書く。生成物のファイル自体が使われていないことは検査する | `knip.ts` | knip の結果に、生成物の export が出ない |
| `scripts/dev/check-frontend.sh` と CI のフロントのジョブで、ルートで 1 回実行する | `scripts/dev/check-frontend.sh`、`.github/workflows/` のフロントのジョブ | `check-frontend.sh` の結果に knip の行がある。CI で knip の手順が実行される |
| knip のライセンス（ISC）は、今の許可の一覧にある（2026-10-26 に `policy.json` で確認した） | `scripts/ci/licenses/policy.json` | 依存のライセンスの検査が通る |

- 設定のファイルは `knip.ts` にする（実装のときに `knip.json` から変えた）。理由：knip が管理画面の Vite の設定を読むときに、IdP の環境変数がないと失敗するので、設定のファイルの先頭で既定値を入れる必要があり、対象外の理由もコメントに書けるため。既定値は knip のプロセスの中だけで、本番のビルドの検査には影響しない。
- knip 6 は、TypeScript の言語サービスに頼る機能（クラスのメンバーの検査）を外した。理由は、TypeScript 7 が Go で書き直されたためである（knip の文書「Announcing Knip v6」）。TypeScript 7 の上で動くかは確認していないので、spec 195 と後でマージする側が確認する（第 9 節）。

## 6. 文書

| 文書 | 変えること | 段階 |
|------|------------|------|
| [地域の設計書](../../../../design/modules/region.md) | 公開 API の表から郵便番号検索を外す。初期データの表で、CSV 変換の置き場所を tools にする | 1 |
| [出荷の設計書](../../../../design/modules/shipping.md) | Repository の port から最近の出荷を一覧する問い合わせを外し、mock の問い合わせを書く。配送業者の表示名のキーを出荷モジュールが持つことを書く | 1、2 |
| [共通コードの設計](../../../../design/common.md) | `common/security/` に token の取り出し、`event/` の表を読み書きするクラスの役割に片付けの削除を加える | 2 |
| [用語集](../../../../design/glossary.md) | 「代表画像」の識別子の列から、削除する操作の名前を外す。新しいクラスは追加しない。理由：テストの補助クラスと内部のクラスのため。用語集の識別子の検査が求めるときだけ追加する | 1、2 |
| [バックエンドの書き方](../../../../rules/backend-coding.md) 第 7.1 節 | getter の基準、`Objects.requireNonNull` の理由の文の形 | 3 |
| [バックエンドの書き方](../../../../rules/backend-coding.md) 第 7.2 節 | `@SuppressWarnings` の理由のコメントを規則の検査が確認すること | 3 |
| [バックエンドの書き方](../../../../rules/backend-coding.md) 第 5.4 節 | import の並びと、IntelliJ の設定、Checkstyle が確認すること | 4 |
| [テストの実行方法](../../../../guides/running-tests.md) | コンパイルの失敗の読み方から、バックエンドの書き方 第 7.2 節へのリンク | 3 |
| [フロントの書き方](../../../../rules/frontend-coding.md) | `cn` の import 元、定義したファイルの中だけで使うものを export しないこと、knip が確認すること | 5 |
| [フロントの構成](../../../../design/frontend-architecture.md) | 共有パッケージに置くものから「クラス名の結合」を外す | 5 |
| [管理画面の構成](../../../../design/apps/admin.md)、[店舗画面の構成](../../../../design/apps/store.md) | ディレクトリの図から `lib/utils.ts` を外す | 5 |
| [CI の検査](../../../../guides/ci.md) | knip と、Checkstyle の対象の広がり | 4、5 |
| [AC 一覧](../../../../design/acceptance-criteria.md) | テストの列を再生成する（`ac-check.mjs --write`） | 1 |

## 7. 段階

| 段階 | ファイル | 内容 | DC |
|------|----------|------|----|
| 1 | `tasks/01-test-only.md` | テスト専用の操作と可視性 | 192-1〜4 |
| 2 | `tasks/02-single-place.md` | 同じ処理を 1 か所に | 192-5〜8 |
| 3 | `tasks/03-nullaway.md` | NullAway と規則の検査、null の基準 | 192-16〜20 |
| 4 | `tasks/04-imports.md` | Java の import の検査と修正 | 192-13〜15 |
| 5 | `tasks/05-frontend.md` | フロントの削除と knip | 192-9〜12 |
| 6 | `tasks/06-review.md` | spec の最後のレビュー、起票のまとめの案、pull request の前のレビュー、アーカイブ | 全て |

- 段階は 1 → 2 → 3 → 4 → 5 の順に進める。段階 4 は、段階 1〜3 が変えたファイルを含めて import を直すので、バックエンドの最後にする。段階 5 は、段階 3、4 と [CI の検査](../../../../guides/ci.md) を共に変えるので、その後にする。
- spec 194 の E2E の変更と重なるので、段階 5 の前に spec 194 のマージの有無を確認する（第 9 節）。
- 各段階の最後のタスクは、段階のレビュー（profile「厚め」）と、全体の検査の実行である。段階 1、2 は main のコードを変えるので、`./gradlew pitest` も実行する。
- pull request の作成はタスクにしない（[開発の進め方](../../../../rules/workflow.md) 第 4.2 節）。
- アーカイブ先は `docs/specs/archive/1to10/192-unused-code/` である。

## 8. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・統合 | `backend/ecsite/` で `./gradlew check` |
| 画面 | `scripts/dev/check-frontend.sh`（knip を含む） |
| CI の検査 | `scripts/ci/check-all.sh`（`ac-check`、`java-rules`、`ci-docs` など） |
| mutation | `backend/ecsite/` で `./gradlew pitest`（段階 1、2、6） |
| 地域のマイグレーション（DN-192-2） | 段階 1 で `./gradlew generateRegionMigration` を実行し、出力が今のマイグレーションと一致することを確認する |
| API の契約とマイグレーション（DN-192-2） | 段階 6 のレビューで、差分に `api/openapi/` と `db/migration/` のファイルがないことを確認する |
| E2E | 段階 6 で全体を 1 回実行する。理由：認証の取り出し（DC-192-8）と画面の import 元の変更（DC-192-10）は、消費者・運営者の操作で動くため |

- スロット（作業ツリーごとの環境）は、他の spec と共有する。同時に使えるのは 1 つまでである。
- `./gradlew check` と `check-frontend.sh` は続けて実行し、同時に実行しない。理由：CPU が足りず、管理画面の結合テストがタイムアウトするため。

## 9. 並行する spec

2026-10-26 に、並行する spec の spec・tasks が挙げる変えるファイルと、本書の変えるファイルを照合した。

| spec | 重なるファイル | 判断 |
|------|----------------|------|
| 193（店舗画面） | なし。193 は失敗の表示、注文フォーム、サーバーアクションの共通処理を変える。本書は API 型、認証設定の型、検索条件、注文状態などを変える | 並行してよい |
| 194（E2E） | `e2e/src/auth.ts`（本書は export を 2 つ外す。194 は `e2e/src/` の補助を整理する） | 並行してよい。段階 5 の前に spec 194 がマージ済みなら取り込んでから始める。未マージなら、`e2e/` の変更を最小（export を外すだけ）にし、後でマージする側が解消する |
| 185（新しい検査） | `scripts/ci/lib/`（185 が整理する。本書は `java-rules.mjs` だけ）、[CI の検査](../../../../guides/ci.md) の表 | 並行してよい。後でマージする側が解消する |
| 195（TypeScript 7・js-yaml 5） | ルートと各ワークスペースの `package.json`、`pnpm-lock.yaml`、型の別名のファイル | 並行してよい。後でマージする側が、lock を再生成し、TypeScript 7 の上で knip と型チェックが通ることを確認する |

- 照合した結果を、実装開始のコメントとして #192 に書く（[開発の進め方](../../../../rules/workflow.md) 第 5.6 節）。

## 10. 確認したこと

利用者が 2026-10-26 に、次の全てを推奨のとおり決めた。

1. **税率キャッシュの破棄（DC-192-3）の扱い。** 4 つの統合テストが、前のテストのキャッシュを読まないよう、準備で破棄している。テストは共通の時計を使い、多くのテストが同じ時刻に設定するので、時計を進めても有効期間が切れない。操作を本番から完全に消す案（B、C）は、振る舞いか規模に影響がある。
   - 案 A（推奨）：破棄の操作を残し、package-private にする。統合テストの同じパッケージに、Spring のコンテキストから取り出して破棄するテストの補助クラスを置き、4 つのテストはそれを呼ぶ。クラスの public は外す。spec の DC-192-3 を「税率キャッシュを破棄する操作は public でなく、テストの補助クラスだけが呼ぶ」に直す。理由：本番の振る舞いを変えず、外から呼べる範囲を最小にできるため。
   - 案 B：キャッシュを Spring Cache に替え、テストはその管理の API で消す。本番のキャッシュの実装を替えるので、段階 1 が大きくなる。
   - 案 C：統合テストの設定で有効期間を 0 にする。有効期間（30 秒）を確認する鮮度のテストが成り立たなくなるので、鮮度のテストだけ別の設定にする必要がある。
2. **spec の修正（確定の後）の確認。** plan を書く中で、次の 3 つを spec に反映した。spec は確定のままにしてよいかを確認する。
   - DC-192-3 から、決済クライアントを組み立てる static の操作を外した（第 1.3 節）。本番が使い、テストを替えると確認が弱まるため。
   - DC-192-10 を、管理画面だけでなく店舗画面と共有パッケージの、`cn` を再び export するファイルに広げた（第 5.1 節）。
   - 文書のレビューで、「の」の続く言い回しを短くした（意味は変えていない）。
3. **DC-192-17 は既に満たしている。** 3 つの引数には、既に `@Nullable` がある（第 3 節）。コードを変えず、段階 3 のレビューで確認するだけにする。
