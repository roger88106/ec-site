# Plan 182：決済の設定の検証、セキュリティと設定の指摘、出荷の読み取りを整える

ステータス：草案（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 182 が確定した。文書の書き方は skill `docs`（[SKILL.md](../../../.claude/skills/docs/SKILL.md)）と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`（[SKILL.md](../../../.claude/skills/review/SKILL.md)）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 支払の期限の上限 | 第 1 節 | DS-182-1（DC-182-1〜3） |
| 店舗の操作の公開 | 第 2 節 | DS-182-2（DC-182-4〜6） |
| ログに出さない値 | 第 3 節 | DS-182-3（DC-182-7〜10） |
| 環境に依存する値 | 第 4 節 | DS-182-4（DC-182-11〜14） |
| 出荷の登録の読み取り | 第 5 節 | DS-182-5（DC-182-15、16） |
| 文書 | 第 6 節 | 全て |
| 段階と確認の方法 | 第 7 節、第 8 節 | 全て |

DC の列は「182-1」の形で書く。spec の番号 182 を付けた DC-182-1 の略である。

共通の決定：

- 実装は、利用者が issue 担当のセッションに開始を指示してから始める。利用者は使用量を見て時期を決める。
- 実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` は T182-01-04（店舗の操作の公開の実装）だけである。理由：認可の判定を読む処理を変えるため。このタスクの実装役は高性能にし、タスクの完了の条件に、実装の直後の高性能のレビュー（観点：セキュリティ）を含める。
- 1 タスクを 1 commit にする。`[テスト]` のタスクはレッドを確認して commit し、続く実装のタスクで通す。理由：[開発の進め方](../../rules/workflow.md) 第 2.1 節の 2 つのタスクを 1 commit にする例外は、CI の検査の script のテストだけのため。
- 新しいテストは単体テストにし、統合テストの補助（`src/testFixtures/`、`src/integrationTest/` の共通のクラス）は変えない。理由：並行する spec 181 が統合テストの補助を変えうるため（spec 第 7 節）。
- AC を変えない（DN-182-3）。AC-233 のテストの名前だけを変える。テストの名前を変えたら `node scripts/ci/ac-check.mjs --write` で AC 一覧を再生成する。
- データベースの構造を変えない（DN-182-2）。確認方法は、段階 1 の最後のレビューと段階 2 の照合で、spec の最初の commit からの差分に `db/migration/` のファイルがないことを確認する。API の契約（`api/openapi/`）の差分は、`security: []` の追加と `Location` の説明だけであることを確認する。
- 起動の検証（支払の期限の上限、`security` の書き忘れ）は、違反を直した後に有効にし、違反を含む入力で失敗することを恒久のテストにする（DN-182-4）。
- パスは、断りがなければ `backend/ecsite/src/` からの相対で書く。`main/`、`test/`、`it/` は、`main/java/com/ecsite/`、`test/java/com/ecsite/`、`integrationTest/java/com/ecsite/` の略である。文書と契約のパスはリポジトリのルートからの相対で書く。

## 1. 支払の期限の上限（DS-182-1：DC-182-1〜3）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 1 | 決済の設定の支払の期限に、Hibernate Validator の `@DurationMax(minutes = 30)` を付ける。Javadoc に上限と理由を書く | `main/module/payment/application/PaymentProperties.java` | 単体テスト |
| 3 | 設定の検証のテストを追加する。`ApplicationContextRunner` で、30 分は起動でき、31 分は起動に失敗し、失敗の原因に設定の名前（`deadline`）が含まれることを確認する。書き方は `test/config/RequiredPropertiesTest.java` に倣う | `test/module/payment/application/PaymentPropertiesTest.java`（新規） | 実装の前に失敗する（レッド） |
| 2 | 決済のモジュールの設計書の「支払の期限」の節に、上限（30 分）と理由を書く。理由は、支払画面が支払の期限より前に閉じないため | `docs/design/modules/payment.md` | レビュー |

| 1 | 支払画面の期限の定数は変えない。定数のコメントに、支払の期限の上限（30 分）がこの値を前提にすることを 1 文で追加する | `main/module/payment/infrastructure/stripe/StripePaymentGateway.java`（`PAGE_LIFETIME`） | レビュー |

- 上限は 30 分にする。理由：支払画面は注文の後に作るので、支払画面の期限（作ってから 31 分）は注文日時から 31 分以上後になる。上限を 30 分にすると、時計のずれの余裕（1 分）を残して、支払画面が支払の期限より前に閉じない。今の既定値とも同じである。

## 2. 店舗の操作の公開（DS-182-2：DC-182-4〜6）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 4 | `security` を書かない 9 つの操作に `security: []` を追加する ※1 | `api/openapi/store.yaml` | DC-182-5 の起動の検証（統合テストが起動する） |
| 5 | 契約を読み込む処理で、操作に `security` がなければ `IllegalStateException` を投げる。メッセージに、操作のメソッドとパスを含める。`security: []` は今のとおり token が不要とする | `main/config/security/StoreOperationSecurity.java`（`load`）。メソッドとパスを渡せないときは `main/config/security/OpenApiOperations.java` を変える | 単体テスト |
| 6 | `StoreOperationSecurityTest` に、`security` のない操作を含む契約の例を新しく作り、読み込むと失敗するテストを追加する。今の契約の例の `security` のない操作を `security: []` に直す。AC-233 のテストの名前の「security のない操作」を「security が空の一覧の操作」に直す | `test/config/security/StoreOperationSecurityTest.java` | 実装の前に失敗する（レッド） |

※1：`GET /products`、`GET /products/search`、`GET /products/{productId}`、`GET /categories`、`POST /carts`、`GET /shop`、`GET /prefectures`、`GET /prefectures/{prefectureCode}/municipalities`、`GET /postal-codes/{postalCode}`（2026-10-24 の `develop`）。

| 4、5 | 契約の変更が認可の判定を変えないことを、既存の統合テスト（AC-232、233）で確認する。このテストの契約の読み方（`security` がなければ不要とする）は変えない。理由：起動の検証が `security` のない契約を許さないため | `it/config/security/StoreApiTokenContractTest.java` | `./gradlew check` |

- 管理 API の操作の権限の対応は変えない（spec 第 8 節）。

## 3. ログに出さない値（DS-182-3：DC-182-7〜10）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 7 | 画像の保存先の設定（S3 のアクセスキー ID と秘密の鍵）と画像の変換の設定（imgproxy の鍵と salt）の `toString` を上書きし、秘密の値を `***` にする。Stripe の設定（`StripeProperties`）と同じ書き方にする | `main/module/media/infrastructure/storage/MediaStorageProperties.java`、`main/module/media/infrastructure/converter/ImgproxyProperties.java` | 単体テスト（秘密の値が `toString` に含まれない） |
| 7 | 上の 2 つのテストを追加する | `test/module/media/infrastructure/storage/MediaStoragePropertiesTest.java`、`test/module/media/infrastructure/converter/ImgproxyPropertiesTest.java`（新規） | 実装の前に失敗する（レッド） |
| 8 | バックエンドの書き方 第 6.1 節に「秘密の値（鍵、パスワード、署名の鍵）を持つ設定の record は、`toString` で値を伏せる」と確認方法（単体テスト、レビュー）を追加する。規則の一覧の表にも行を追加する | `docs/rules/backend-coding.md` | レビュー |
| 9 | 集約を戻す補助のデバッグ用のメッセージから、保存した状態を除く。引数の保存した状態は、使わなくなるので削除し、呼び出し元を全て直す ※2 | `main/common/model/base/AggregateGuards.java`、呼び出し元 | 単体テスト |
| 10 | `AggregateGuardsTest` の期待値を、集約の名前だけのメッセージ（`"<名前> restore error."`）に直し、保存した状態の文字列を含まないことを確認する。呼び出しの引数は、実装のタスクで直す。理由：テストのタスクでコンパイルに失敗させると、並行する他のテストのレッドの確認ができなくなるため | `test/common/model/base/AggregateGuardsTest.java`（`:58`） | 実装の前に失敗する（レッド） |

※2：呼び出し元は、`main/module/catalog/domain/category/CategoryFactory.java`、`main/module/catalog/domain/product/ProductFactory.java`、`main/module/tax/domain/taxcategory/TaxCategoryFactory.java` と、`test/common/model/base/AggregateGuardsTest.java`（2 か所）の 5 か所（2026-10-24 の `develop`、全文検索）。

## 4. 環境に依存する値（DS-182-4：DC-182-11〜14）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 11 | 共通の設定から Zipkin の送信先を削除し、`management.tracing.export.zipkin.enabled: false` を置く。local の profile に `enabled: true` と送信先（`http://localhost:9411/api/v2/spans`）を置く ※3 | `main/resources/application.yml`、`main/resources/application-local.yml` | local の profile で起動し、自動構成の判定（`/actuator/conditions`）に Zipkin の送信の Bean があることを確認する。Zipkin への到達は確認しない ※4 |
| 12 | 環境変数の一覧に `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENABLED`（既定 false。local と E2E は true）を追加する。送信先の既定（Spring Boot の既定の `http://localhost:9411/api/v2/spans`）は変わらないので、説明に「送信を有効にしたときの送信先」と書く。共通コードの設計書のトレースの節も同じに直す | `docs/guides/environment.md`（`:20`）、`docs/design/common.md`（トレースの節） | レビュー ※4 |
| 13 | 作成の応答の補助で、`Location` をホストを含まない相対の URL にする。基にするパスは今のとおりで、管理 API と店舗 API の作成は今のリクエストのパス、別のパスを指す店舗 API の作成はアプリの起点（コンテキストパス）である。どちらも URI のパスの部分だけを使い、スキーム・ホスト・ポートを除く。メソッドの Javadoc を直す | `main/common/presentation/CreatedResponses.java` | 単体テスト |
| 14 | `CreatedResponsesTest` の期待値 3 つを相対の URL（`/api/admin/products/<ID>` など）に直す | `test/common/presentation/CreatedResponsesTest.java`（`:43,55,67`） | 実装の前に失敗する（レッド） |
| 14 | `Location` の URL で取得する既存の API のテスト（カテゴリ、商品、税区分。AC-8、13、20、276、1）が通ることを確認する。MockMvc は相対の URL を受け付ける | `it/module/catalog/presentation/CategoryApiTest.java`、`it/module/catalog/presentation/ProductApiTest.java`、`it/module/tax/presentation/TaxCategoryApiTest.java` | `./gradlew check` |
| 13 | 契約の `Location` の説明を「作成したリソースの URL（ホストを含まない相対の URL）」に直す。API の規約の作成の行の※1と、バックエンドの書き方 第 2.2 節の作成の応答の行に、相対の URL であることを追加する | `api/openapi/store.yaml`（`:637`）、`api/openapi/admin.yaml`（`:1370`）、`docs/rules/api-conventions.md`、`docs/rules/backend-coding.md` | レビュー |

※3：Spring Boot 4.1 は、`management.tracing.export.zipkin.enabled` で Zipkin への送信の有無を決める（Spring Boot の `OnEnabledTracingExportCondition`、2026-10-24 確認）。送信先を消しただけでは Spring Boot の既定の送信先に送るので、無効にする設定が必要だ。E2E の profile は local に重ねて使うので、今と同じく送信が有効になる。

※4：`scripts/ci/` の `env-defaults` は設定の record だけを見るので、yml と環境変数の一覧の食い違いを検出しない。

## 5. 出荷の登録の読み取り（DS-182-5：DC-182-15、16）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 15 | 出荷の登録の service は、注文の参照の service の運営者用の詳細（`OrderQueryService.get`）を読み、操作の可否（`actions().shippable()`）で出荷できるかを判定する。注文の Repository の依存を削除する | `main/module/order/application/shipment/OrderShipmentService.java` | レビュー、`./gradlew check` |
| 16 | 既存の出荷の登録の API のテストで、振る舞いが変わらないことを確認する | `it/module/order/presentation/ShipmentApiTest.java` | `./gradlew check` |

- 新しい参照のメソッドは作らない。理由：支払画面の service が同じく詳細を読んで判定しており、型を増やさずに済むため。詳細を作るために出荷と決済の公開の API を読む分、読み取りが増える。負荷は測っていないが、運営者の操作に限られるので問題にしない。
- 注文がないときの 404 は、参照の service が今と同じ例外を投げる。
- 出荷の登録の service の単体テストはない。追加しない。理由：振る舞いを変えず、出荷の登録の API のテストが、注文がない、受付でない、与信の期限切れ、決済サービスに接続できない、の 4 つを確認しているため。

## 6. 文書

| 文書 | 変えること | DC |
|------|------------|----|
| `docs/design/modules/payment.md` | 「支払の期限」の節に上限と理由 | 182-2 |
| `docs/design/backend-architecture.md`（`:183`） | 店舗 API の token の要否：`security` のない操作は起動に失敗する。公開の操作は `security: []` で明示する | 182-4、5 |
| `docs/rules/backend-coding.md` | バックエンドの書き方 第 6.1 節に秘密の値の `toString` の規則。バックエンドの書き方 第 2.2 節の作成の応答に相対の URL | 182-8、13 |
| `docs/rules/api-conventions.md` | 作成の行の※1に、`Location` は相対の URL | 182-13 |
| `docs/guides/environment.md`、`docs/design/common.md` | Zipkin の送信の有効と、送信先の説明 | 182-12 |
| AC 一覧 | AC-233 のテストの名前を変えた後に再生成する。AC 一覧はテストのクラスの名前だけを持つので、差分が出ないこともある。AC の文は変えない | DN-182-3 |

- 用語集に追加する語はない。理由：spec の用語は、本 spec の中の説明のためのもので、コードと設計書に新しい名前を作らないため。
- 店舗画面の設計書とモジュールの設計書で `security` に触れる所は、段階 2 の照合で読み、`security` のない操作を公開とする記述があれば直す。
- spec 第 5 節の「店舗 API の認可を書く設計書と規則」は、バックエンドのアーキテクチャ（上の表）に当たる。規則（`docs/rules/`）には、店舗 API の `security` の扱いの記述がない（2026-10-24 の全文検索）。

## 7. 段階

| 段階 | ファイル | 内容 | DC |
|------|----------|------|----|
| 1 | [tasks/01-fixes.md](tasks/01-fixes.md) | 支払の期限の上限、店舗の操作の公開、ログに出さない値、環境に依存する値、出荷の登録の読み取り、関係する文書 | 182-1〜16 |
| 2 | [tasks/02-review.md](tasks/02-review.md) | 文書の照合、全体の検査、spec の最後のレビュー、E2E、起票のまとめの案、pull request の前のレビュー、アーカイブ | 全て |

- 段階 1 の各タスクは、変えるファイルが重ならないものは並行してよい（tasks の `[P]`）。
- 段階 1 の最後のタスクは、段階のレビュー（profile「厚め」）と全体の検査の実行である。domain で変えるのは集約のファクトリーの補助の呼び出しの引数だけで、判定の論理を変えないので、`./gradlew pitest` は段階 2 の全体の検査で 1 回だけ実行する。
- pull request の作成はタスクにしない（[開発の進め方](../../rules/workflow.md) 第 4.2 節）。
- アーカイブ先は `docs/specs/archive/1to10/182-payment-security/` である。

## 8. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・統合・構成の検査 | `backend/ecsite/` で `./gradlew check` |
| CI の検査 | `scripts/ci/check-all.sh`（`env-defaults`、`ac-check`、`doc-refs`、`java-rules` など） |
| mutation | `backend/ecsite/` で `./gradlew pitest`（段階 2 で 1 回） |
| カバレッジ | CI の検査（`coverage-baseline`） |
| 契約とマイグレーション（DN-182-2） | 段階 1 の最後のレビューと段階 2 の照合で差分を確認する（共通の決定） |
| E2E | 段階 2 で 1 回。`scripts/dev/e2e.sh`。`Location` と契約の `security` を変えるので、店舗と管理の作成の操作を E2E で通して確認する |
| 画面 | 目視は不要。画面の振る舞いを変えないため。E2E で確認する |

- スロット（作業ツリーごとの環境）は他の spec と共有する。同時に使う spec は 1 つまでにする。

## 9. 確認すること

なし。spec の Q1〜Q3 は利用者が決めた（2026-10-24）。
