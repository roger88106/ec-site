# Plan 181：バックエンドのテストを強める

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 181 が確定した。文書の書き方は skill `docs` と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| テストの補助と書き方の規則 | 第 1 節 | DS-181-4（DC-181-24〜27） |
| 他のテストのデータの影響 | 第 2 節 | DS-181-3（DC-181-18〜23） |
| AC の確認とテスト名の番号 | 第 3 節 | DS-181-1（DC-181-1〜16）、DS-181-2（DC-181-17） |
| Repository の統合テストとカートの作成 | 第 4 節 | DS-181-5（DC-181-28〜31） |
| 文書 | 第 5 節 | 全て |
| 段階と確認の方法 | 第 6 節、第 7 節 | 全て |
| 利用者に確認すること | 第 8 節 | DC-181-31 |

DC の列は「181-1」の形で書く。spec の番号 181 を付けた DC-181-1 の略である。欠番の DC-181-32〜44 と DN-181-5 は、spec 186 に移した。本書では扱わない。

共通の決定：

- パスは、断りがなければ `backend/ecsite/src/` からの相対で書く。`main/`、`test/`、`it/`、`fix/` は、`main/java/com/ecsite/`、`test/java/com/ecsite/`、`integrationTest/java/com/ecsite/`、`testFixtures/java/com/ecsite/` の略である。文書のパスはリポジトリのルートからの相対で書く。
- 行の番号は、2026-10-24 の再レビューの結果（`.local/review/202610241000-181-retest/` の result-A・B・C）のものである。実装を始める前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）で、今のコードと合わせる。
- 補助を先に作り、後の段階で追加するテストは補助を使う。理由：テストを追加してから補助に移すと、同じテストを 2 回書き換えるため。
- 強めたテスト、追加したテストは、一時的に期待値かコードを変えて、失敗することを確認する（DN-181-4）。確認の結果は commit の本文に書く。
- テストを強めて不具合が見つかったら、DN-181-1 のとおり扱う。AC の意味が変わらない不具合は、同じ段階にタスクを追加し、テストから修正する。AC の意味が変わる不具合は、修正せずに起票の案に入れ、段階の最後の報告で利用者に伝える。
- 確認を削除するのは、常に成り立つ確認だけで、理由を commit の本文に書く（DN-181-6）。
- API の契約（`api/openapi/`）とマイグレーション（`db/migration/`）を変えない（DN-181-2）。確認方法は、最後の段階のレビューで、spec の最初の commit からの差分にこの 2 つのディレクトリのファイルがないことを確認する。
- AC は変えない（DN-181-3）。テスト名の番号を変えたら、`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する。番号を外した結果、テストのない AC ができたら、その AC を確認するテストがあることを確認してから外す（第 3.2 節の表）。
- 実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` は DC-181-28、29（会員のカートの作成。並行と一意の制約を扱うため）だけである。このタスクの実装役は高性能にし、実装の後に高性能のレビュー（profile「厚め」）を実施する。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。
- 並行する spec 182（決済と設定）が、支払と出荷のテストを変えうる。後でマージする側が `develop` を取り込んで解消する。段階 1 の前に、spec 182 のマージの有無を確認し、マージ済みなら取り込んでから始める。理由：段階 1 の最初のタスクから、支払と出荷のテストを変えるため。
- spec 186（構成の検査）は、本 spec のマージの後に実装する（利用者の決定、2026-10-24）。理由：T181-01-11（契約の検査の置き場所）、T181-01-12（テストの書き方の補助の一覧）、T181-03-13（起動のテストの schema の一覧）、T181-03-14（`AdminRealmPermissionContractTest` のテスト名）と、spec 186 のテストのコメントの書き換えが、同じファイルを変えるため。

## 1. テストの補助と書き方の規則（DS-181-4：DC-181-24〜27）

### 1.1 テストデータの補助（DC-181-27）

| テストデータ | 補助の置き場所と形 | 使う側（今、再定義しているもの） |
|--------------|--------------------|----------------------------------|
| 在庫を作って入荷として調整する | 新しい `it/InventoryTestData`。`stock(variantId, productId, quantity)` は、今のテストと同じく在庫の Repository の作成と調整を呼ぶ | 在庫・店舗・注文・カートの統合テスト（約 27 ファイル。`createIfAbsent` と入荷の調整を書いているもの全て） |
| 公開した商品（税区分の登録を含む） | `it/CatalogTestData`。`TestProduct` に販売の終了（`discontinue()`）と公開期間（`period(...)`）を追加する。品番を指定する `product(...)` のオーバーロードを追加する | `StorefrontApiTest` の内部クラス `TestProduct` と、`StoreCatalogApiTest`、`StoreProductSearchTest`、`StorefrontFreshnessTest` の商品と税区分の登録 |
| カテゴリ | `it/CatalogTestData` に `category(name, parentId)` | `CategoryApiTest`、`ProductListApiTest`、`StoreCatalogApiTest`、`StorefrontApiTest` |
| 画像の受付・アップロード・確定 | `it/CatalogTestData.TestProduct` に `upload()`（受付とアップロード。受付の ID を返す）と `addImage(uploadId, version)`（確定。結果の `ResultActions` を返す）。`image()` はこの 2 つで組み立てる | `ProductImageApiTest`、`ProductImageServiceErrorApiTest`、`ProductImageTransactionApiTest` |
| カートに入れて注文手続きを始める | `it/OrderTestData` に `cart()`、`add(cartId, variantId, quantity)`、`startCheckout(cartId)`。会員のカートは token を受け取るオーバーロード | `CheckoutApiTest`、`CheckoutCartChangeApiTest`、`PlaceOrderApiTest`、`PlaceOrderConcurrencyTest`、`MemberOrderApiTest` |
| 出荷の登録、配送状態の通知、送り状番号 | 新しい `it/ShippingTestData`。`ship(orderId, carrier, trackingNumber)`、`notifyStatus(...)`、`trackingNumber()`（12 桁の一意な番号） | 再レビューの結果 B の No.11 の 9 クラス |
| 支払の作成、与信、売上確定、状態の取得 | 新しい `it/PaymentTestData` | 再レビューの結果 B の No.12 の 6 クラス |
| 作成の応答から ID を取り出す | 新しい `it/TestJson.createdId(...)` | `CatalogTestData`、`OrderTestData` と、`UUID.fromString(JsonPath.read(...))` を書いている 9 クラス |
| イベントの記録の表の定義と、発行して待つ処理 | 新しい `it/config/event/EventPublications`（同じパッケージの 2 つのテストだけが使うので、package-private） | `EventCleanupJobTest`、`EventResubmissionJobTest` |

- 補助は、今のテストの書き方をそのまま移す。テストの確認は変えない。理由：補助への移動と確認の強化を同じ commit に混ぜると、テストが弱まっていないことを差分で確認しにくいため。
- 在庫の補助は、API でなく今と同じく Repository を呼ぶ。理由：商品の登録のイベントのリスナーも在庫を作るので、API で作ると処理の終わりを待つ必要が出るため。
- 補助の一覧を、テストの書き方 第 3.2 節に追記する（第 5 節）。
- 確認方法：`./gradlew check`。最後の段階のレビューで、補助以外のテストに上の表の書き方が残っていないことを検索で確認する（`createIfAbsent(`、`/api/admin/tax-categories` の POST、`JsonPath.read` と `UUID.fromString` の組など）。

### 1.2 待ち方と置き場所（DC-181-24〜26）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 24 | 重複したイベントを無視するテストで、2 回目の発行の前に、1 回目のイベントの処理の完了を待つ（`AsyncEvents.awaitHandled`） | `it/module/reporting/application/ReportCollectionTest.java`（`:197-210`） | テストが通る。レビュー |
| 25 | 注文のキャンセルの一貫性のテストは、イベントの記録を直接読む待ちを `AsyncEvents.awaitHandled` に置き換える。イベントの再発行のテストは、リスナーの試行の回数でなく `AsyncEvents.awaitFailed` で失敗を待つ | `it/module/order/presentation/CancellationAtomicityTest.java`（`:121-130`）、`it/config/event/EventResubmissionJobTest.java` | テストが通る。レビュー |
| 26 | エラーの種類の検査の、契約を読んで照合する処理を `fix/` に移す。MockMvc に接続する設定だけを `it/` に残す。検査のテストを `test/` に移す。`fix/` の依存に JSON の path と YAML の読み込みのライブラリを追加する | `it/config/security/ErrorCodeCheck.java`、`ErrorCodeCheckTest.java`、`build.gradle.kts` の `testFixturesImplementation` | `./gradlew check`（単体テストとして実行される） |

## 2. 他のテストのデータの影響（DS-181-3：DC-181-18〜23）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 18 | 支払の期限切れのジョブのテストは、ジョブを固定の回数で呼ばず、自分の 101 件が全て処理されるまで呼ぶ。上限は、表の全体の未処理の件数を 1 回の件数で割った回数に 1 を加えたもの | `it/module/payment/application/PaymentExpiryJobTest.java`（`:131-157`） | 期限切れの支払を他のテストの分として 200 件余分に作ってから実行しても通ることを、一時的に確認する |
| 19 | 出荷の新しい順の読み取りのテストは、自分の 2 件の時刻を、表にありうるどの出荷より後（3000 年）にする。読み取りの件数の上限で自分の 2 件だけが返ることを確認する。配送業者の mock のテストの時刻（2095 年）のコメントを、このテストと合わせて修正する | `it/module/shipping/infrastructure/persistence/JooqShipmentRepositoryTest.java`（`:119-131`）、`it/module/shipping/infrastructure/mock/MockCarrierTest.java`（`:33-35`） | 配送業者の mock のテストを先に実行しても通ることを、テストのクラスをまとめて実行して確認する |
| 20 | 一覧の全体を確認するテストは、下の表のとおり、テストごとに作るカテゴリ、キーワード、注文番号で絞った結果を確認する | 下の表 | 後処理を削除した後（DC-181-21、23）に `./gradlew check` が通る |
| 21、23 | コミットするテストの、データを削除する後処理は削除する。理由：DC-181-20 の後は一覧のテストが他のテストのデータの影響を受けず、テストの書き方 第 3.1 節は後処理を不要とするため。後処理がなくなるので、非同期のリスナーとの順序の問題も起きない | `it/module/order/presentation/CheckoutCartChangeApiTest.java`、`PlaceOrderConcurrencyTest.java`、`OrderPaymentApiTest.java`、`CancellationAtomicityTest.java`、`it/module/catalog/presentation/ProductImageApiTest.java`、`ProductConcurrencyTest.java`、`ProductImageTransactionApiTest.java` の `@AfterEach` | `./gradlew check` が通る。最後の段階のレビューで、左の 7 ファイルにデータを削除する後処理が残っていないことを確認する。他のテストの後処理は、本 spec では変えない（テストの書き方 第 3.1 節 ※4） |
| 22 | 失敗したイベントを再発行するテストは、支払の取消・返金の記録とレポートの写しを、自分の注文の ID で絞って確認する | `it/module/payment/application/PaymentRefundTest.java`（`:125-127,174-177`）、`PaymentVoidTest.java`（`:109-111`）、`it/module/reporting/application/ReportCollectionTest.java`（`:249-251`） | テストが通る。レビュー |

DC-181-20 の対象と絞り方：

| テスト | 絞り方 |
|--------|--------|
| `StorefrontApiTest`（一覧、件数、並び） | テストごとにカテゴリを作り、店舗の一覧をカテゴリで絞る |
| `StoreCatalogApiTest`（ページ、並び、価格の範囲） | 同上。1 ページに収まる前提（36 件）も、カテゴリの中の件数にする |
| `StoreProductSearchTest` | テストごとの印を商品名に入れ、検索の語に印を含める |
| `ProductListApiTest`（管理の商品の一覧） | テストごとにカテゴリを作り、カテゴリで絞る |
| `CategoryApiTest`（カテゴリの木） | テストで作った親のカテゴリの部分木だけを確認する。木の全体と根の数を確認しない |
| `AdminOrderApiTest`、`AdminOrderShipmentViewApiTest`（注文の一覧） | 自分の注文の ID、または注文番号で絞った結果を確認する。全体の件数は確認しない |

- 絞り込みの条件そのものを確認するテスト（カテゴリで絞る、など）は、もともと自分のデータで確認しているので、変えない。
- 一覧の API は、カテゴリと検索の語で絞れる（契約にある）。契約は変えない。

## 3. AC の確認とテスト名の番号（DS-181-1、2：DC-181-1〜17）

### 3.1 確認の強化（DC-181-1〜16）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 1 | 会員のカートに 30 品目を入れ、移す元に別のバリアントを入れて移す。移せなかった品目のバリアント ID と数量を確認する | `it/module/cart/presentation/MemberCartApiTest.java`（`:194-214`） | テスト。一時的に期待値を変えて失敗する |
| 2 | 注文手続きの表示で、税率ごとの消費税額を確認する。10% と 8% の商品を 1 つずつ入れる | `it/module/order/presentation/CheckoutApiTest.java`（`:160-178`） | 同上 |
| 3 | 支払待ちの注文へのキャンセルの申請が、業務のエラー（`CANCELLATION_NOT_REQUESTABLE`）の 400 で拒否され、状態が支払待ちのままであることを確認する。運営者のキャンセルは、`4xx` でなく実際の状態コードを確認する | `it/module/order/presentation/OrderPaymentApiTest.java`（`:305-315` と、支払待ちの節） | 同上 |
| 4 | 出荷の登録の入力の検証を、入力ごとのパラメーター化したテストにし、エラーの種類と項目を確認する | `it/module/order/presentation/ShipmentApiTest.java`（`:238-252`） | 同上 |
| 5 | 商品の画像の追加に変換できないファイルを渡すと、`IMAGE_CONVERSION_FAILED` の 400 になり、商品の画像とバージョンが変わらないテストを追加する。媒体の API のテストの、常に成り立つ確認（`isSubsetOf`）を削除する | `it/module/catalog/presentation/ProductImageApiTest.java`、`it/module/media/api/MediaApiTest.java`（`:197-206`） | 同上 |
| 6 | バリアントの画像と代表画像のある品目で、カートの表示の画像の URL を確認する | `it/module/storefront/presentation/StorefrontCartTest.java` | 同上 |
| 7 | 最後の変更から 30 日の 1 ミリ秒前では、品目が残ることを確認する | `it/module/storefront/presentation/StorefrontCartTest.java`（`:156-172`） | 同上 |
| 8 | 注文の確定の後、カートの品目が 1 つで数量が 2 のままであることを確認する | `it/module/order/presentation/PlaceOrderApiTest.java`（`:107-135`） | 同上 |
| 9 | 商品を指定した内訳の API の呼び出しを追加し、その商品を含む注文だけが数えられることを確認する | `it/module/reporting/presentation/ReportApiTest.java`（`:188-211`） | 同上 |
| 10 | 生年月日のない会員の注文を追加し、年齢層「不明」の値を確認する | 同上 | 同上 |
| 11 | 同じ期間を日・週・月の粒度で取得し、粒度ごとの行の数と、行の合計が同じことを確認するテストを追加する。テスト名に AC-198 を入れる | `it/module/reporting/presentation/ReportApiTest.java` | 同上。AC の除外の一覧は変えない |
| 12 | 2 つ目の注文の挿入が、一意の制約の違反の例外になることを確認する | `it/module/order/infrastructure/persistence/JooqOrderRepositoryTest.java`（`:87`） | 同上 |
| 13 | 他のテストの影響を受けない古い時刻のデータだけを削除する条件で、ログの件数が削除した件数と一致することを確認する | `it/module/cart/application/CartCleanupJobTest.java`（`:98`）、`it/module/order/application/checkout/CheckoutCleanupJobTest.java`（`:125`） | 同上 |
| 14 | 写しの型の項目名を、入れ子の record まで再帰して確認する | `it/module/order/api/OrderReportApiTest.java`（`:166-175`） | 同上 |
| 15 | 業務のポートで管理用のエンドポイントを呼んだ応答は 404 を、物流の通知のエンドポイントは契約の応答（400）を確認する | `it/observability/ManagementPortTest.java`（`:50-51`）、`it/config/security/BusinessPortAccessTest.java`（`:47`） | 同上 |
| 16 | 全てのモジュールの schema（13）を一覧にし、全てが作られることを確認する | `it/EcSiteApplicationTests.java`（`:48`） | 同上 |

- DC-181-11 は、除外でなくテストを追加する。理由：粒度ごとの行は API の応答にあり、バックエンドで確認できるため。
- DC-181-12 の例外の型は、実装の時に今の永続化が投げる型（データの一貫性の違反）を確認して指定する。

### 3.2 テスト名の番号（DC-181-17）

| テスト | 修正方法 | 外した番号を確認する他のテスト |
|--------|--------|------------------------------|
| `InventoryApiTest`（`:121-127`） | AC-44 を AC-42 にし、名前を「増減量を続けて加えると、調整後の在庫数が加えた値になる」にする | AC-44 は `JooqStockItemRepositoryTest` |
| `PaymentAuthorizationDeadlineTest`（`:89-98`） | AC-188 を外す | `ShipmentApiTest`、同じクラスの別のテスト |
| `ReportApiTest`（`:106-120`） | AC-198 を外し、AC-199、200、BR-26 にする。商品を指定した集計（AC-199）も確認しているため | AC-198 は DC-181-11 |
| `TaxRateApiTest`（`:86-92`） | AC-6 を外す | `ProductApiTest` |
| `ProductActionsTest`（`:65-69,83-84`） | AC-33、71 と併記する | — |
| `PlaceOrderApiTest`（`:107-135`） | AC-90 は残し、名前を、今も有効な注文番号の採番の確認にする。注文番号の形を確認する | — |
| `PlaceOrderApiTest`（`:411-417`） | 日付と性別のテストに分け、性別のテストに BR-29 を付ける | — |
| `ReportGenderTest`（`:23,29`） | AC-277、278 を AC-219、BR-29 にする | AC-277、278 は `ReportGender` の対応の他のテスト ※1 |
| `ShippingApiTest`（`:60-69,87-96`） | 出荷を作るテストは BR-19 にし、形に合わない送り状番号のテストは番号を外す（送り状番号の形とハイフンの除去は BR にない） | AC-173 は `ShipmentApiTest`（DC-181-4） |
| `CartApiTest`（`:175-179`） | AC-73 を外す | `CartTest`、`cart.test.ts` |
| `PaymentExpiryJobTest`（`:172-180`） | AC-154 は残し、名前を「期限切れの確認の間隔の既定は 5 分以内」にする | — |
| `VariantSalesQueryTest`（`:133-139`） | 呼び出しを 1 回にし、商品名の確認を別のテストに分ける | — |
| `AdminRealmPermissionContractTest`（`:98,107`） | 名前の先頭の「spec 010 BR-28」を「BR-28」にする | — |

※1：実装の前に、AC-277、278 を名前に持つ他のテストがあることを、AC 一覧で確認する。なければ、列挙の全ての値の対応を確認するテストに AC-277、278 を付ける。

## 4. Repository の統合テストとカートの作成（DS-181-5：DC-181-28〜31）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 28 | カートの作成の永続化を、挿入できたかを返す形にする。会員のカートの ID を求める操作は、挿入できたら作った ID を返し、できなかったときだけ読み直す。注文の挿入（`OrderRepository.insert`）と同じ書き方にする。呼び出し元は `CartService` の 2 か所と、`CartCleanupJobTest`（`:184`）である | `main/module/cart/application/port/CartRepository.java`（`:19`）、`main/module/cart/infrastructure/persistence/JooqCartRepository.java`（`:43-52`）、`main/module/cart/application/CartService.java`（`:81,183`） | 新しい Repository のテスト（DC-181-31）で、2 回目の挿入が「できなかった」を返すことを確認する |
| 29 | 同じ会員の最初のカートの要求を同時に送り、カートが 1 つで、全ての要求が同じ ID を受け取ることを確認する。会員の確定の並行のテスト（`MemberConfirmationTest`）と同じ書き方にする | `it/module/cart/presentation/MemberCartConcurrencyTest.java`（新規） | テスト。一時的に一意の制約の扱いを外して失敗することを確認する |
| 30 | 注文の Repository のテストに、状態を変えた更新、行の lock、注文番号の存在の確認を追加する。注文の更新はバージョンで競合を判定しない（行の lock で防ぐ）ので、競合は確認しない | `it/module/order/infrastructure/persistence/JooqOrderRepositoryTest.java` | テスト |
| 31 | 下の表の Repository に、永続化と再構築を確認する統合テストを追加する | 下の表 | テスト |

DC-181-31 の対象：

| Repository | テスト（新規） | 確認すること |
|------------|----------------|--------------|
| カート | `it/module/cart/infrastructure/persistence/JooqCartRepositoryTest.java` | 作成と再構築、品目の差分の更新、会員のカートの重複の挿入、楽観ロック |
| 注文手続き | `it/module/order/infrastructure/persistence/JooqCheckoutRepositoryTest.java` | 作成と再構築、更新 |
| 支払 | `it/module/payment/infrastructure/persistence/JooqPaymentRepositoryTest.java` | 作成と再構築、状態の更新 |
| 会員 | `it/module/member/infrastructure/persistence/JooqMemberRepositoryTest.java` | 作成と再構築、更新 |
| 送ったメール | `it/module/notification/infrastructure/persistence/JooqSentMailRepositoryTest.java` | 記録と読み取り |
| レポートの注文の写し | `it/module/reporting/infrastructure/persistence/JooqReportOrderRepositoryTest.java` | 写しの保存と再構築、重複の無視 |
| 在庫の引当 | `it/module/inventory/infrastructure/persistence/JooqStockReservationRepositoryTest.java` | 引当の置き換え、合計、所有者ごとの削除、期限切れの削除 |
| 画像の資産 | `it/module/media/infrastructure/persistence/JooqAssetRepositoryTest.java` | 記録、ID での読み取り、削除 |

- 地域の Repository は対象にしない。理由：読み取りだけの基本データで、永続化・差分更新・イベントの発行がないため。地域の API のテストが読み取りを確認している。
- 各テストのクラス名と置き場所は、実装の Repository の置き場所に合わせる（テストの書き方 第 3.3 節）。実装のクラスの名前が表と違えば、実装に合わせる。
- 在庫の引当と画像の資産を DC-181-31 に追加した経緯は、第 8 節にある。

## 5. 文書

| 文書 | 変えること | 段階 |
|------|------------|------|
| [テストの書き方](../../rules/test-writing.md) 第 3.2 節 | 補助の一覧（`InventoryTestData`、`ShippingTestData`、`PaymentTestData`、`TestJson`）を追記する | 1 |
| [AC 一覧](../../design/acceptance-criteria.md) | テストの列を再生成する（`ac-check.mjs --write`） | 3、4 |
| [用語集](../../design/glossary.md) | spec の用語（コミットするテスト、一覧の全体を確認するテスト）は追加しない。理由：spec の中だけの語で、規則の語はテストの書き方の「巻き戻せないテスト」のため。補助のクラス名は、用語集の識別子の検査が求めるときだけ追加する | 1 |

## 6. 段階

| 段階 | ファイル | 内容 | DC |
|------|----------|------|----|
| 1 | `tasks/01-helpers.md` | テストデータの補助、待ち方、置き場所 | 181-24〜27 |
| 2 | `tasks/02-isolation.md` | 他のテストのデータの影響、後処理の削除 | 181-18〜23 |
| 3 | `tasks/03-ac-tests.md` | AC の確認の強化、テスト名の番号 | 181-1〜17 |
| 4 | `tasks/04-repositories.md` | カートの作成、Repository の統合テスト | 181-28〜31 |
| 5 | `tasks/05-review.md` | spec の最後のレビュー、起票のまとめの案、pull request の前のレビュー、アーカイブ | 全て |

- 段階は 1 → 2 → 3 → 4 の順に進める。段階 3 と 4 は、段階 1 の補助を使う。段階 2 の後処理の削除は、同じ段階の一覧のテストの絞り込みの後に行う。
- 段階 3 と 4 の新しいテストは、コミットするなら自分のデータだけを確認し、後処理を書かない。
- 各段階の最後のタスクは、段階のレビュー（profile「厚め」）と、全体の検査の実行である。段階 4 は main のコードを変えるので、`./gradlew pitest` も実行する。
- pull request の作成はタスクにしない（[開発の進め方](../../rules/workflow.md) 第 4.2 節）。
- アーカイブ先は `docs/specs/archive/1to10/181-backend-tests/` である。

## 7. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・統合 | `backend/ecsite/` で `./gradlew check` |
| CI の検査 | `scripts/ci/check-all.sh`（`ac-check`、`test-names`、`time-waits`、`coverage-baseline` など） |
| mutation | `backend/ecsite/` で `./gradlew pitest`（段階 4 と段階 5） |
| テストの順序に依存しないこと（DC-181-18〜23） | 段階 2 の最後に、統合テストのクラスの分け方（`-PintegrationTestShard` の 2 分割と 3 分割）を変えて `./gradlew integrationTest` を実行する |
| API の契約とマイグレーション（DN-181-2） | 段階 5 のレビューで、差分に `api/openapi/` と `db/migration/` のファイルがないことを確認する |
| E2E | 実行しない。理由：消費者・運営者に見える振る舞いを変えず、main のコードの変更はカートの作成の内部だけで、統合テストが確認するため |

- スロット（作業ツリーごとの環境）は、他の spec と共有し、同時に 1 つまで使う。

## 8. 確認すること

1. DC-181-31 に、在庫の引当と画像の資産の Repository を追加した。plan で Repository を数えて、spec の草案の一覧に抜けていたのを見つけたため。テストの書き方 第 1.1 節の必須の統合テストに当たる。地域の Repository は、読み取りだけなので対象にしない。
