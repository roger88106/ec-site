# Plan 522：テストの準備と確認の重複を、補助のクラスにまとめる

ステータス：草案　対象：[spec.md](spec.md)

前提：spec の DS-522-1（DC-522-1〜10）と、利用者が承認した確認（下の「利用者に確認すること」の「承認済み」の行）に従う。読む文書は[文書の書き方](../../05-開発規則/writing.md)、[文書の構成](../../05-開発規則/doc-structure.md)、[文書の記号と番号](../../05-開発規則/notation.md)、[テストの書き方](../../05-開発規則/test-writing.md)、[バックエンドのテストの書き方](../../05-開発規則/test-writing-backend.md)。

| 本書の節 | 主な担当 |
|----------|----------|
| 注文の状態ごとの作り方 | DS-522-1（DC-522-1） |
| 注文のイベントの確認 | DS-522-1（DC-522-2） |
| キャンセル申請の準備 | DS-522-1（DC-522-3） |
| 受付の注文の準備 | DS-522-1（DC-522-4） |
| 設定の範囲の検証 | DS-522-1（DC-522-5） |
| 境界の値 | DS-522-1（DC-522-6, 9, 10） |
| 既存の確認の維持と検査 | DS-522-1（DC-522-7〜8） |
| 段階と変えるファイル | 全て |
| 利用者に確認すること | [spec.md](spec.md)の「Open questions」 |

共通の決定：

- 危険度：高（経路による見込み。最終判定は差分の危険度の判定 script で行う）。理由：変えるファイルに支払のテストが含まれ、支払の経路に当たるため。
- 危険度が高いので、重要な観点のレビューと spec の最後のレビューを、高性能（opus）で行う。
- 実装は汎用の `implementer` に 1 タスクずつ任せる。
- 1 タスクを 1 commit にする。
- push の時点は[開発の進め方](../../05-開発規則/workflow.md)の「push のタイミング」に従う。
- 変えたクラスの統合テストを絞って実行する。`./gradlew check` は段階の最後に 1 回（tasks の T522-01-25）。実行の前に、issue 担当のセッションの合図を待つ。理由：`./gradlew check` が重く、重い検査の回数を減らすため。
- 整理のテストは本番のコードが既に通るので、赤の確認をそのまま使えない。扱いは「利用者に確認すること」の 2 番で決める（通ることと、値をずらすと失敗すること〔break-check〕で確認する）。

## 注文の状態ごとの作り方（DS-522-1：DC-522-1）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| `TestOrders` に `accepted`・`requested`・`shipped` を追加する | `backend/ecsite/src/test/java/com/ecsite/module/order/domain/order/TestOrders.java` | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| private の作り方を `TestOrders` の呼び出しに置き換える（8 箇所） | `OrderPaymentTest.java`（3）、`OrderShipmentTest.java`（3）、`OrderCancellationTest.java`（2）。同じパッケージ | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`private static Order` の定義が残らない。 |

理由：同じ準備が 3 ファイルに複製されており、仕様の変更で修正が漏れるため。3 ファイルのずれ（イベントの取り出し、受付の時刻）は、補助に揃える前に確認する（tasks の [調査]）。

## 注文のイベントの確認（DS-522-1：DC-522-2）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| private の `assertCancelledEventOnly` を `OrderEventAssertions` に移す | `OrderCancellationTest.java`（定義は 655 行）→ `OrderEventAssertions.java`（同じパッケージ） | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。private の定義が残らない。 |
| 移した定義の呼び出し（`OrderCancellationTest.java` の 253・411 行の 2 件。`grep -rn assertCancelledEventOnly` で数えた件数）を、`OrderEventAssertions` の呼び出しに直す | `OrderCancellationTest.java` | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| 重なる既存テストを、組ごとに統合する | 組の一覧は tasks の [調査]（T522-01-14）の結果 | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。統合の前後で、確認するイベントの件数と種類が同じ。 |

統合の基準：同じ準備と同じ操作の組だけを統合する。確認の内容は落とさない。

補助の追加：`OrderEventAssertions` に、イベントを 1 回だけ取り出し、登録されたイベントが全体で 1 件だけ（種類を問わない）であることと、`assertStatusChangedEventOnly` と同じ 6 項目を確認するメソッドを加える。6 項目の確認は両方から共有し、テストの中では直接書かない（組 2 の直接の形は、この補助を使う形に修正する）。

理由：同じ準備と同じ操作のテストが、確認の内容を変えずに重複しているため。統合の組は実装の前に 1 件ずつ特定し、tasks に書く。

※1：ステータス変更のイベントを確認する呼び出しは、定義を除くと 9 件（起票時は 13 件）。起票時の件数は古いため、今の件数で進める。

## キャンセル申請の準備（DS-522-1：DC-522-3）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| `OrderTestData` に申請の操作を追加する | `backend/ecsite/src/integrationTest/java/com/ecsite/support/data/OrderTestData.java` | 9 ファイルから使い、通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| 9 ファイルの申請の手順を、補助の呼び出しに置き換える | `OrderStatusChangedEventTest`、`CheckoutCleanupJobTest`、`AdminOrderApiTest`、`CapturedOrderCancellationRefundTest`、`MemberOrderApiTest`、`OrderPaymentApiTest`、`OrderPaymentCartRemovalApiTest`、`ShipmentApiTest`、`StoreOrderShipmentApiTest`（`backend/ecsite/src/integrationTest/java/com/ecsite/module/order/` の下） | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。申請の HTTP の呼び出しが残らない。 |

対象外：`CancellationApiTest`（申請そのものを確認する）。

理由：同じ申請の手順が 9 ファイルに複製されているため。`CapturedOrderCancellationRefundTest` は、受付の注文の準備（次の節）も変えるので、この節の後に続けて変える。

## 受付の注文の準備（DS-522-1：DC-522-4）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| `OrderTestData` に、受付の注文の準備（在庫、税区分、商品、会員、注文、与信の待ち）を追加する | `OrderTestData.java` | 6 ファイルから使い、通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| `OrderTestData` に、運営者のキャンセルと、管理 API で支払の状態を読む処理を追加する | `OrderTestData.java` | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| 対象の 6 ファイルの準備を、補助の呼び出しに置き換える | `CapturedOrderCancellationRefundTest`、`ReportFlowTest`（`module/reporting/presentation/`）、`ReturnedOrderRefundTest`、`CancellationAtomicityTest`、`CancellationApiTest`、`ReturnedOrderCancellationApiTest`（`module/order/presentation/`） | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。`CancellationApiTest` と `ReturnedOrderCancellationApiTest` は、申請の確認を残す。 |
| 報告のテストから補助のパッケージを使う依存の向きを、境界のテスト（`ModulithTest`）で確認する | `ModulithTest.java`（確認のために読む。変えない） | 結果を 1 行で書く（tasks の T522-01-21）。 |

対象外：`JooqOrderRepositoryTest` と `MemberOrderApiTest` の `acceptedOrder`（戻り値が別の意味の準備）。

理由：同じ準備が 6 ファイルに複製されているため。依存の向きが未確認なので、境界のテストで確認してから補助を使う。

## 設定の範囲の検証（DS-522-1：DC-522-5）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 範囲の検証を、`TestSettings.bindValidated` のテストに移す（4 件） | `OrderPropertiesTest.java`、`PaymentPropertiesTest.java`、`ImgproxyPropertiesTest.java`、`ReportingPropertiesTest.java` | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| 残す `ApplicationContextRunner` の確認に、残す理由をテストのコメントに書く | 同じ 4 ファイル | レビュー |

理由：同じ範囲の検証が 2 つの方法で書かれているため。報告の設定のテストは組み立ての確認だけを使うので、範囲の検証は新しく `bindValidated` のテストに書く。

## 境界の値（DS-522-1：DC-522-6, 9, 10）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 下限と上限を別の `@Test` にし、「ちょうど」と「1 つ外」を確認する（DC-522-6） | 対象は tasks の T522-01-02 の一覧（単体テスト 12 ファイル、統合テスト `ProductListApiTest.java` の品番（32・33）と商品名（200・201）の境界）。代表例は `ValidationErrorTranslatorTest.java` の 175・182 行付近 | 通る。値を 1 つずらすと失敗することを確認し、元に戻す（break-check）。 |
| 上限・下限の値を、本番の定数から作る（DC-522-9） | 同じ対象 | レビュー |
| 拒否のテストの名前を「400」にそろえる（統合テスト 5 ファイルを含む。※2）。ページの件数の呼び名を「1 ページの件数」にそろえる（DC-522-10） | 同じ対象（拒否と件数の名前）と、統合テスト 5 ファイル（※2） | レビュー |
| 境界の規則を 1 節で書く（DC-522-6） | `docs/05-開発規則/test-writing-backend.md` | 文書の検査（`doc-refs.mjs`、`ja-text.mjs`）が通る |

※2：統合テストの対象は `ProductApiTest.java`・`ProductImageApiTest.java`・`ProductListApiTest.java`・`ProductVariantApiTest.java`（`module/catalog/presentation/`）と `StorefrontApiTest.java`（`module/storefront/presentation/`）の「リクエストの誤り」（`@DisplayName` とコメント）。本番の `GlobalExceptionHandler`・`StripePaymentGateway` の同じ語は変えない（spec の Non-goals）。

※3：名前は、既存の使用数に合わせた。拒否は「400」が 30 件、「リクエストの誤り」が 7 件。件数は「1 ページの件数」が 11 件、「1 ページの大きさ」が 1 件。

理由：境界の 1 つのテストに上限と下限の両方を入れると、どちらが失敗したかが名前から分からないため。

## 既存の確認の維持と検査（DS-522-1：DC-522-7〜8）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 変えた各テストで、確認する項目が減っていないことを見る | 全ての変えたテスト | 段階の最後のレビューで、変更の前後を並べて読む |
| 段階の最後に `./gradlew check` を 1 回実行する（cpdCheck を含む） | `backend/ecsite/` | `./gradlew check` が通る |

理由：統合と分割で、確認の内容が落ちやすいため。重い検査を 1 回にまとめるため。

## 段階と変えるファイル

| 段階 | ファイル | 内容 | DC | 開始の条件 |
|------|----------|------|----|------------|
| 1 | `docs/08-spec/522-test-dedup/spec.md` の「tasks」 | 境界、設定、注文の準備、イベント、キャンセル申請、受付の注文の順 | DC-522-1〜10 | この plan の承認と、「利用者に確認すること」の回答 |

| まとまり | ファイル |
|----------|----------|
| 単体テスト（注文、設定、境界） | `backend/ecsite/src/test/java/com/ecsite/` の下（spec の「変える範囲」） |
| 統合テストの補助と対象 | `backend/ecsite/src/integrationTest/java/com/ecsite/` の下（spec の「変える範囲」） |
| 文書 | `docs/05-開発規則/test-writing-backend.md`、`docs/08-spec/522-test-dedup/` |

## 利用者に確認すること

| # | 確認 | 案 | 推奨と理由 |
|---|------|----|------------|
| 1 | 境界の形（下限と上限を別の `@Test`、本番の定数から作る、「ちょうど」と「1 つ外」） | 承認済み | 承認済み |
| 2 | 整理のテストの赤の確認。本番が既に通るので、赤にならない | A（通ることと、値をずらすと失敗すること〔break-check〕で確認する）で承認済み | 承認済み（A）。理由：ずらして失敗すれば、新しいテストが確認を実際に行っていると分かるため。 |
| 3 | M の spec の構造検査は、spec.md の `## tasks` を要求する。タスクの置き場所 | A：タスクを spec.md の `## tasks` に移す | 承認済み（A）。理由：検査（`spec-structure.mjs`）が M で `## tasks` を要求するため。 |
| 4 | 件数：ステータス変更のイベントの確認は定義を除く 9 件（起票時 13 件）。使う側は 3 ファイル（定義を含めて 4 件） | 今の件数で進める | 今の件数で進める。理由：起票時の値は古いため。 |
| 5 | 拒否のテストの名前：「400」か「リクエストの誤り」か | A：「400」。B：「リクエストの誤り」 | 承認済み（A：「400」）。理由：「400」が 30 件で、「リクエストの誤り」の 7 件より多いため（※3）。 |
| 6 | 件数（CancellationApiTest を除く申請の対象 9 件） | 承認済み | 承認済み |
| 7 | 範囲の検証（4 件を `bindValidated` に移し、残す `ApplicationContextRunner` の理由をコメントに書く） | 承認済み | 承認済み |
| 8 | 受付の注文の準備の対象（`CancellationApiTest`・`ReturnedOrderCancellationApiTest` の準備を含む） | 承認済み | 承認済み |
