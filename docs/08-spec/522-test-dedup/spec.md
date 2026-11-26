# Spec 522：テストの準備と確認の重複を、補助のクラスにまとめる

種類：整理　規模：M　ステータス：草案

## 背景と目的

テストのクラスに、同じ準備と確認の手順が複数残っている。補助のクラスにまとめ、テストの本文には確認する項目だけを残す（issue #522）。テストの振る舞いは変えない（DN-522-1）。

## スコープ

| 含める | 含めない |
|--------|----------|
| 単体テストの注文の準備、イベントの確認、設定の範囲の検証、境界の値 | 本番のコードの変更（DN-522-1） |
| 統合テストのキャンセル申請、受付の注文、運営者のキャンセル、支払の読み取りの準備 | 範囲外の config 系のテスト（Non-goals） |
| テストの書き方の規則（境界の節） | 新しい機能、API の契約、DB の構造の変更 |

決定：

- 対象は 6 項目である。項目ごとに、DC-522-1〜10 のいずれかが対応する。
- 境界のテストは、下限と上限を別の `@Test` にし、「ちょうど」と「1 つ外」の両方を確認する（[テストの書き方](../../05-開発規則/test-writing.md)の「テストの観点」）。
- 範囲の検証は `TestSettings.bindValidated` で行う。
- 統合テストの補助は、`OrderTestData` に追加する。
- `ApplicationContextRunner` は、Spring の組み立ての確認（Bean の有無、条件付きの設定、型の登録）だけに使う。

## 目的と完了条件

### DS-522-1 テストの準備と確認を、補助のクラスと境界の形にまとめる（D / P1）

- **DC-522-1** 注文の状態（`pending`・`accepted`・`requested`・`shipped`）の準備は補助のクラスだけにあり、テストごとの private の作り方は無い。
- **DC-522-2** 同じ準備と同じ操作の既存のテストの組だけを統合し、統合の後も確認するイベントの件数と種類を変えない。
- **DC-522-3** 変える範囲の申請のテストは、申請の手順を補助の申請の操作で行い、HTTP の呼び出しを直接書かない。
- **DC-522-4** 受付の注文の準備は補助にまとめられ、対象の 6 ファイルが使う。
- **DC-522-5** 範囲の検証（上限・下限・必須）は全て、テスト用の範囲検証の補助で確認する。
- **DC-522-6** 境界のテストは、下限と上限を別のテストにし、「ちょうど」と「1 つ外」の両方を確認する。
- **DC-522-7** 置き換えと統合の前後で、各テストが確認する項目（結果の値、DB の状態、イベントの件数）が減らない。
- **DC-522-8** 全体の検査（コードの検査を含む）が通る。
- **DC-522-9** 境界の上限・下限の値は、本番の定数から作る。
- **DC-522-10** 拒否のテストの名前と、ページの件数の呼び名が、既存の使用数の多い呼び名にそろっている。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 単体テスト（注文の状態、イベントの確認） | `backend/ecsite/src/test/java/com/ecsite/module/order/domain/order/` の `TestOrders.java`、`OrderPaymentTest.java`、`OrderShipmentTest.java`、`OrderCancellationTest.java`、`OrderEventAssertions.java` |
| 単体テスト（設定の範囲の検証） | `OrderPropertiesTest.java`（`module/order/application/`）、`PaymentPropertiesTest.java`（`module/payment/application/`）、`ImgproxyPropertiesTest.java`（`module/media/infrastructure/converter/`）、`ReportingPropertiesTest.java`（`module/reporting/application/`）、`TestSettings.java`（`support/`） |
| 単体テスト（境界の値） | `backend/ecsite/src/test/java/com/ecsite/` の下の 12 ファイル（確定。一覧は tasks の T522-01-02）。11 クラスに `CartTest.java`（`module/cart/domain/`、品目の数の上限と数量の上限の境界）を加えた |
| 統合テスト（境界の値と拒否の名前） | `backend/ecsite/src/integrationTest/java/com/ecsite/` の下の 5 ファイル（確定）：`module/catalog/presentation/` の `ProductApiTest.java`・`ProductImageApiTest.java`・`ProductListApiTest.java`（品番と商品名の境界）・`ProductVariantApiTest.java`、`module/storefront/presentation/` の `StorefrontApiTest.java`。拒否の「リクエストの誤り」を「400」にそろえる |
| 統合テストの補助 | `backend/ecsite/src/integrationTest/java/com/ecsite/support/data/OrderTestData.java` |
| 統合テスト（キャンセル申請の準備） | `backend/ecsite/src/integrationTest/java/com/ecsite/` の 9 ファイル（一覧は tasks） |
| 統合テスト（受付の注文の準備） | `CapturedOrderCancellationRefundTest`、`ReportFlowTest`（`module/reporting/presentation/`）、`ReturnedOrderRefundTest`、`CancellationAtomicityTest`、`CancellationApiTest`、`ReturnedOrderCancellationApiTest`（`module/order/presentation/` ほか） |
| 文書 | `docs/05-開発規則/test-writing-backend.md`（境界の節）、`docs/08-spec/522-test-dedup/` の spec・plan・tasks |

※1：起票時の件数と今の件数の差は次のとおり。`assertStatusChangedEventOnly` の呼び出しは、定義を除くと 9 件（起票時は 13 件）。その呼び出しを持つテストファイルは 3 件で、定義のファイル `OrderEventAssertions` を含めると 4 件。`ApplicationContextRunner` を使うテストは、範囲内の 4 件。`CancellationApiTest` を除く申請の対象は 9 件（起票時は 8 クラス）。

理由：起票時の件数から、今の件数に合わせて対象を決めるため。

※2：`TestOrders.pending` と 3 ファイルの private の作り方の差（行番号は作業ツリーの現在のファイル）。
- `TestOrders.pending`：成立のイベントを取り出す（`TestOrders.java` 55 行）。受付・申請・出荷の作り方は持たない。
- 受付の時刻：注文の日時から、`OrderPaymentTest.java` 443 行は +60 秒、`OrderShipmentTest.java` 495 行は +30 秒、`OrderCancellationTest.java` 671 行は +30 秒。
- 受付のイベント：`OrderPaymentTest.java` 444 行、`OrderShipmentTest.java` 496 行、`OrderCancellationTest.java` 672 行で取り出す。
- 申請の時刻と理由：`OrderPaymentTest.java` 457 行は +120 秒と `REQUEST_REASON`、`OrderShipmentTest.java` 507 行は 1 日後と理由なし、`OrderCancellationTest.java` 685 行は +60 秒と「理由」。
- 申請のイベント：`OrderPaymentTest.java` 458 行、`OrderCancellationTest.java` 686 行で取り出す。`OrderShipmentTest.java` 505〜508 行は取り出さず、申請のイベントが残る。
- 出荷の時刻と追跡番号：`OrderPaymentTest.java` 471 行は +120 秒と番号の直書き、`OrderShipmentTest.java` 520 行は +1 時間と `TRACKING_NUMBER`。
- 出荷のイベント：`OrderPaymentTest.java` 472 行、`OrderShipmentTest.java` 521 行で取り出す。
- `pendingOrder`：`OrderPaymentTest.java` 431 行・`OrderShipmentTest.java` 483 行は `TestOrders.pending` に委譲し、`OrderCancellationTest.java` 670 行は直接呼ぶ。

## 非機能要件

- **DN-522-1** 本番のコード（`src/main`）、API の契約（`api/openapi/`）、DB の構造（Flyway）は変えない。テストの振る舞いも変えない。
- **DN-522-2** 変えるテストの名前は、[テストの書き方](../../05-開発規則/test-writing.md)の「名前は振る舞いを書く」に従う。

## 依存

| 対象 | 関係 |
|------|------|
| issue #522 | 追跡の issue。 |
| 並行する作業 | 同じテストのファイルに触れる作業は、確認していない（Open questions の 4 は別の依存）。 |

## Non-goals

| 項目 | 理由 |
|------|------|
| 範囲外の config 系テスト 5 件（`ExternalServiceSelectionTest`・`RequiredPropertiesTest`・`ManagementPortCheckTest`・`MockFlagWarningTest`・`SecurityPropertiesTest`） | 承認された範囲は、`ApplicationContextRunner` と `bindValidated` の両方を使う 3 件と、`ApplicationContextRunner` だけを使う 1 件に限るため。 |
| `FlywayConnectionConfigTest`（integrationTest） | 承認された範囲に入っていないため。 |
| `JooqOrderRepositoryTest` と `MemberOrderApiTest` の `acceptedOrder`、`JooqReportOrderRepositoryTest` の `shippedOrder` | 別の意味の準備（戻り値の型、対象の集約が違う）のため。 |
| `CancellationApiTest` の申請の確認 | 申請そのものを確認するテストのため（DC-522-3）。 |
| 本番のコードの「リクエストの誤り」（`GlobalExceptionHandler`・`StripePaymentGateway`） | 名前をそろえる対象はテストだけのため。本番のコードは DN-522-1 で変えない。 |
| 本番のコードの変更 | DN-522-1。 |

## Open questions

1. 注文の private の作り方 8 箇所のずれ（イベントの取り出し、受付の時刻）は未確認。tasks の [調査] で確定し、確定した差を変える範囲の注に書く。
2. 整理のテストは、本番のコードが既に通るため、テストの赤の確認が成り立たない。承認済み（plan の利用者確認 2・3 番）。
3. M の spec の構造検査（`scripts/ci/spec/spec-structure.mjs`）は、spec.md の `## tasks` を要求する。承認済み（plan の利用者確認 2・3 番）。
4. 報告のテストから補助のパッケージを使う依存の向きは未確認。Modulith の境界のテストで確認する（tasks の T522-01-21）。

## tasks

### 段階 1：テストの準備と確認を補助にまとめる

- 前提：[plan](plan.md)の「利用者に確認すること」の回答を受けてから始める。
- 完了条件：DC-522-1〜10 を満たし、`./gradlew check` が通る（タスク T522-01-25）。
- 参照：[plan](plan.md)の「境界の値」「注文の状態ごとの作り方」「注文のイベントの確認」「キャンセル申請の準備」「受付の注文の準備」「設定の範囲の検証」「既存の確認の維持と検査」、[spec](spec.md)の DS-522-1。

実装は汎用の `implementer` に 1 タスクずつ任せる。1 タスク 1 commit にする。変えたクラスの統合テストを絞って実行する。`./gradlew check` は段階の最後に 1 回（T522-01-25）。

- [ ] **T522-01-01** [テスト] `ValidationErrorTranslatorTest` の長さの境界（175・182 行付近）を、下限と上限の別の `@Test` に分け、「ちょうど」の確認を追加する（DC-522-6）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/common/error/handler/ValidationErrorTranslatorTest.java`
  - 確認方法：通る（本番は変えない）。上限の定数を 1 つずらすと失敗することを確認し、元に戻す（break-check）。
- [ ] **T522-01-02** [調査] 境界の対象を確定する。対象は次の 13 ファイル（単体テスト 12、統合テスト 1）。一覧は下に書く（文書の更新だけ）（DC-522-6）。
  - 依存：T522-01-01
  - 範囲：`backend/ecsite/src/test/java/` と `backend/ecsite/src/integrationTest/java/` の境界の確認。`OrderTest` は文字列を作る補助で、境界の確認ではないため除く。`docs/08-spec/522-test-dedup/spec.md` の「tasks」
  - 確認方法：確定した一覧を、この節に書く（送り直さない。承認済み）。
  - 対象の一覧（確定）。パスは `backend/ecsite/src/test/java/com/ecsite/`（単体）と `backend/ecsite/src/integrationTest/java/com/ecsite/`（統合）の下：

    ```text
    単体テスト
    common/error/handler/ValidationErrorTranslatorTest.java
    common/model/value/BoundedTextTest.java
    common/validation/CodePointSizeValidatorTest.java
    module/cart/domain/CartTest.java
    module/catalog/domain/category/CategoryTest.java
    module/catalog/domain/product/ProductTest.java
    module/catalog/domain/product/ProductVariantTest.java
    module/inventory/domain/stock/StockAdjustmentTest.java
    module/order/domain/order/OrderCancellationTest.java
    module/order/domain/order/ShippingAddressTest.java
    module/shipping/domain/TrackingNumberTest.java
    module/tax/domain/taxcategory/TaxCategoryTest.java

    統合テスト
    module/catalog/presentation/ProductListApiTest.java
    ```

  - 除外：`OrderTest`（文字列の補助で、境界の確認ではない）。
- [ ] **T522-01-03** [テスト] T522-01-02 の一覧のテストを、「ちょうど」と「1 つ外」の別の `@Test` にする（DC-522-6）。
  - 依存：T522-01-02（一覧の確認を受けたもの）
  - 範囲：T522-01-02 の一覧のファイル
  - 確認方法：通る。各上限・下限の値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。
- [ ] **T522-01-04** 境界の上限・下限の値を、本番の定数から作るように書き換える（DC-522-9）。
  - 依存：T522-01-03
  - 範囲：T522-01-02 の一覧のファイル
  - 確認方法：通る。`repeat` の数字のリテラルが境界の値に残らない。
- [ ] **T522-01-05** 拒否のテストの名前を「400」にそろえ、ページの件数の呼び名を「1 ページの件数」にそろえる（DC-522-10）。
  - 依存：T522-01-04
  - 範囲：名前が「リクエストの誤り」「1 ページの大きさ」のテスト（T522-01-02 の一覧のファイル）。統合テストは、「リクエストの誤り」を含む 5 ファイル（`module/catalog/presentation/` の `ProductApiTest.java`・`ProductImageApiTest.java`・`ProductListApiTest.java`・`ProductVariantApiTest.java`、`module/storefront/presentation/` の `StorefrontApiTest.java`）の `@DisplayName` とコメント。本番の `src/main` の同じ語は変えない（spec の Non-goals）
  - 確認方法：`DisplayName` とコメントの grep で、「リクエストの誤り」の拒否と「1 ページの大きさ」が対象から消える。
- [ ] **T522-01-06** `docs/05-開発規則/test-writing-backend.md` に、境界の形を 1 節で書く（DC-522-6）。
  - 依存：T522-01-05
  - 範囲：`docs/05-開発規則/test-writing-backend.md`
  - 確認方法：`node scripts/ci/docs/doc-refs.mjs` と `node scripts/ci/ja/ja-text.mjs` が通る。
- [ ] **T522-01-07** [テスト] `OrderPropertiesTest`・`PaymentPropertiesTest`・`ImgproxyPropertiesTest`・`ReportingPropertiesTest` の範囲の検証（上限・下限・必須）を、`TestSettings.bindValidated` のテストとして書く（DC-522-5）。
  - 依存：T522-01-06
  - 範囲：上の 4 ファイル
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。
- [ ] **T522-01-08** 同じ 4 ファイルで、`ApplicationContextRunner` に残す確認（Bean、条件、型の登録）の理由をテストのコメントに書き、移した範囲の検証を `ApplicationContextRunner` 側から削除する（DC-522-5）。
  - 依存：T522-01-07
  - 範囲：上の 4 ファイル
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`ApplicationContextRunner` の範囲の検証が残らない。
- [ ] **T522-01-09** [調査] `OrderPaymentTest`・`OrderShipmentTest`・`OrderCancellationTest` の private の作り方（8 箇所）と `TestOrders` の差（イベントの取り出し、受付の時刻）を確認する。差を、[spec](spec.md)の「変える範囲」の注に書き追加する（文書の更新だけ）（DC-522-1）。
  - 依存：T522-01-08
  - 範囲：上の 3 ファイル、`TestOrders.java`、`docs/08-spec/522-test-dedup/spec.md`
  - 確認方法：差の一覧の各行に、確認した行番号を書く。
- [ ] **T522-01-10** `TestOrders` に `accepted`・`requested`・`shipped` を追加する（DC-522-1）。
  - 依存：T522-01-09
  - 範囲：`TestOrders.java`
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。
- [ ] **T522-01-11** `OrderPaymentTest` の private の `acceptedOrder`・`requestedOrder`・`shippedOrder` を `TestOrders` の呼び出しに置き換える（DC-522-1）。
  - 依存：T522-01-10
  - 範囲：`OrderPaymentTest.java`
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`private static Order` の定義が残らない。
- [ ] **T522-01-12** `OrderShipmentTest` の private の 3 つを、`TestOrders` の呼び出しに置き換える（DC-522-1）。
  - 依存：T522-01-11
  - 範囲：`OrderShipmentTest.java`
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`private static Order` の定義が残らない。
- [ ] **T522-01-13** `OrderCancellationTest` の private の `acceptedOrder`・`requestedOrder` を、`TestOrders` の呼び出しに置き換える（DC-522-1）。
  - 依存：T522-01-12
  - 範囲：`OrderCancellationTest.java`
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`private static Order` の定義が残らない。
- [ ] **T522-01-14** [調査] `assertStatusChangedEventOnly` の 9 件の呼び出しと、`OrderEventAssertions` の使う側（3 ファイル）を基に、重なる既存テストの組（ファイルとメソッド名）を特定する。組の一覧を、このファイルの T522-01-16 の範囲に書き追加する（文書の更新だけ）（DC-522-2）。
  - 依存：T522-01-13
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/module/order/domain/order/` の 3 ファイルと `OrderEventAssertions.java`、`docs/08-spec/522-test-dedup/spec.md` の「tasks」
  - 確認方法：組ごとに、同じ準備と同じ操作であることを、準備と操作の行を並べて確認する。
- [ ] **T522-01-15** `OrderCancellationTest` の private の `assertCancelledEventOnly` を、`OrderEventAssertions` に移す（DC-522-2）。
  - 依存：T522-01-14
  - 範囲：`OrderCancellationTest.java`、`OrderEventAssertions.java`
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。private の定義が残らない。
- [ ] **T522-01-16** 重なる既存テストを、組ごとに 1 commit で統合する（DC-522-2、DC-522-7）。組は T522-01-14 の一覧の順に、1 組ずつ行う。
  - 依存：T522-01-15
  - 範囲：T522-01-14 の一覧の組
  - 確認方法：通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。統合の前後で、確認するイベントの件数と種類が同じ。統合の基準（同じ準備と同じ操作の組だけを統合する）に合う。
- [ ] **T522-01-17** `OrderTestData` に、キャンセル申請の操作を追加する（DC-522-3）。
  - 依存：T522-01-16
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/support/data/OrderTestData.java`
  - 確認方法：T522-01-18 で使うテストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。
- [ ] **T522-01-18** 次の 3 ファイルの申請の手順を、`OrderTestData` の呼び出しに置き換える（DC-522-3）：`OrderStatusChangedEventTest`、`CheckoutCleanupJobTest`、`AdminOrderApiTest`。
  - 依存：T522-01-17
  - 範囲：上の 3 ファイル（`backend/ecsite/src/integrationTest/java/com/ecsite/module/order/` の下）
  - 確認方法：絞り込んだ統合テストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。申請の HTTP の呼び出しが残らない。
- [ ] **T522-01-19** 次の 3 ファイルの申請の手順を、`OrderTestData` の呼び出しに置き換える（DC-522-3）：`CapturedOrderCancellationRefundTest`、`MemberOrderApiTest`、`OrderPaymentApiTest`。
  - 依存：T522-01-18
  - 範囲：上の 3 ファイル（`module/order/presentation/` の下、`OrderPaymentApiTest` と `MemberOrderApiTest`）
  - 確認方法：絞り込んだ統合テストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。申請の HTTP の呼び出しが残らない。
- [ ] **T522-01-20** 次の 3 ファイルの申請の手順を、`OrderTestData` の呼び出しに置き換える（DC-522-3）：`OrderPaymentCartRemovalApiTest`、`ShipmentApiTest`、`StoreOrderShipmentApiTest`。
  - 依存：T522-01-19
  - 範囲：上の 3 ファイル（`module/order/presentation/` の下）
  - 確認方法：絞り込んだ統合テストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。申請の HTTP の呼び出しが残らない。
- [ ] **T522-01-21** [調査] 受付の注文の 6 ファイルの準備の差（在庫、税区分、商品、会員、注文、与信の待ち、運営者のキャンセル、支払の読み取り）を確認する。`ReportFlowTest` から `com.ecsite.support.data` を使う依存の向きを、`ModulithTest` で確認する。結果を [plan](plan.md)の「受付の注文の準備」に書き追加する（DC-522-4）。
  - 依存：T522-01-20
  - 範囲：対象の 6 ファイル、`ModulithTest.java`（確認のために読む。変えない）、`docs/08-spec/522-test-dedup/plan.md`
  - 確認方法：差の一覧に、ファイルごとの準備の行番号を書く。依存の向きの結果を 1 行で書く。
- [ ] **T522-01-22** `OrderTestData` に、受付の注文の準備、運営者のキャンセル、支払の状態の読み取りを追加する（DC-522-4）。
  - 依存：T522-01-21
  - 範囲：`OrderTestData.java`
  - 確認方法：T522-01-23 で使うテストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。
- [ ] **T522-01-23** 次の 4 ファイルの準備を、`OrderTestData` の呼び出しに置き換える（DC-522-4）：`CapturedOrderCancellationRefundTest`（T522-01-19 の後に続けて変える）、`ReportFlowTest`、`ReturnedOrderRefundTest`、`CancellationAtomicityTest`。
  - 依存：T522-01-22
  - 範囲：上の 4 ファイル
  - 確認方法：絞り込んだ統合テストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`acceptedOrder` と同じ準備の定義が残らない。
- [ ] **T522-01-24** `CancellationApiTest` と `ReturnedOrderCancellationApiTest` の準備を、`OrderTestData` の呼び出しに置き換える。申請の確認は残す（DC-522-4）。
  - 依存：T522-01-23
  - 範囲：上の 2 ファイル（`module/order/presentation/` の下）
  - 確認方法：絞り込んだ統合テストが通る。通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。申請の確認の検査が残る。
- [ ] **T522-01-25** [実行] `./gradlew check` を 1 回実行する（`backend/ecsite/`）。実行の前に、issue 担当のセッションの合図を待つ（DC-522-8）。
  - 依存：T522-01-24
  - 範囲：`backend/ecsite/`
  - 確認方法：`./gradlew check`（`cpdCheck` を含む）が通る。
- [ ] **T522-01-26** 段階の最後のレビュー。変えたテストを、変更の前後で並べて読み、確認する項目が減っていないことを確認する。[書き方の規則とレビュー](../../05-開発規則/review.md)と照合する。指摘を修正する（DC-522-7）。
  - 依存：T522-01-25
  - 確認する項目：DC-522-1〜10。危険度が高いので、高性能（opus）で行う。
  - 結果：レビューごとに 1 行の表で書く（skill `review` の手順 6 の形）。
- [ ] **T522-01-27** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T522-01-26
- [ ] **T522-01-28** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T522-01-27
- [ ] **T522-01-29** spec をアーカイブに移す。
  - 依存：T522-01-28
