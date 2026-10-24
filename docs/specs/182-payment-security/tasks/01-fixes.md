# 段階 1：決済の設定、セキュリティと設定、出荷の読み取り

- 前提：plan が承認され、issue 担当のセッションが実装の開始を指示した。最新の `develop` を取り込み、plan の前提と照合した（[開発の進め方](../../../rules/workflow.md) 第 5.6 節）。
- 完了条件：DC-182-1〜16 を満たす。`[テスト]` のタスクが実装の前に失敗することを確認した。関係する文書を更新し、AC 一覧を再生成した。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1〜6 節、[spec](../spec.md) の DS-182-1〜5 と DC-182-1〜16、DN-182-1〜5、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[バックエンドの書き方](../../../rules/backend-coding.md)、[テストの書き方](../../../rules/test-writing.md)

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方は、[plan](../plan.md) の共通の決定の最後の項目のとおりである。

T182-01-01、03、05、07、09、10、12 は、変えるファイルが重ならないので、互いに `[P]` である。1 タスクを 1 commit にし、`[テスト]` のタスクはレッドを確認して commit する（[plan](../plan.md) の共通の決定）。T182-01-06 と T182-01-11 はバックエンドの書き方を、T182-01-04 と T182-01-11 は `store.yaml` を変えるので、順に行う。

- [x] **T182-01-01** [P] [テスト] 決済の設定の検証のテストを追加する。`ApplicationContextRunner` で、支払の期限が 30 分なら起動でき、31 分なら起動に失敗し、失敗の原因に `deadline` が含まれることを確認する。書き方は `test/config/RequiredPropertiesTest.java` に倣う。実装の前に失敗することを確認する（DC-182-3）。
  - 依存：なし
  - 範囲：`test/module/payment/application/PaymentPropertiesTest.java`（新規）
- [x] **T182-01-02** 決済の設定の支払の期限に `@DurationMax(minutes = 30)` を付け、Javadoc に上限と理由を書く。支払画面の期限の定数のコメントに、支払の期限の上限（30 分）がこの値を前提にすることを 1 文で追加する。決済のモジュールの設計書の「支払の期限」の節に、上限と理由を書く（DC-182-1、2）。
  - 依存：T182-01-01
  - 範囲：`main/module/payment/application/PaymentProperties.java`（`:17,27`）、`main/module/payment/infrastructure/stripe/StripePaymentGateway.java`（`:32-35`）、`docs/design/modules/payment.md`（「支払の期限」の節）
- [x] **T182-01-03** [P] [テスト] `StoreOperationSecurityTest` に、`security` のない操作を含む契約の例を新しく作り、読み込むと `IllegalStateException` で失敗し、メッセージに操作のメソッドとパスが含まれるテストを追加する。テストの契約の例の `security` のない操作を `security: []` に直し、AC-233 のテストの名前の「security のない操作」を「security が空の一覧の操作」に直す。実装の前に失敗することを確認する（DC-182-6）。
  - 依存：なし
  - 範囲：`test/config/security/StoreOperationSecurityTest.java`
- [x] **T182-01-04** [リスク] 店舗の操作の公開を、契約の明示に変える（DC-182-4、5）。
  - `store.yaml` の `security` のない 9 つの操作に `security: []` を追加する（plan 第 2 節の※1）。
  - 契約を読み込む処理で、`security` のない操作があれば `IllegalStateException` を投げる。メッセージに操作のメソッドとパスを含める。クラスの Javadoc を新しい扱いに直す。
  - バックエンドのアーキテクチャの店舗 API の token の要否の記述を直す。
  - `node scripts/ci/ac-check.mjs --write` で AC 一覧を再生成する。
  - 統合テスト `StoreApiTokenContractTest` が通ることを確認する。
  - 実装の直後に、高性能のレビュー役がこのタスクの差分をセキュリティの観点でレビューし、指摘を直す（skill `review`）。
  - 依存：T182-01-03
  - 範囲：`api/openapi/store.yaml`、`main/config/security/StoreOperationSecurity.java`（`:10-14,37-40`）、必要なら `main/config/security/OpenApiOperations.java`、`docs/design/backend-architecture.md`（`:183`）、`docs/design/acceptance-criteria.md`
- [x] **T182-01-05** [P] [テスト] 画像の保存先の設定と画像の変換の設定の `toString` に、秘密の値（S3 のアクセスキー ID と秘密の鍵、imgproxy の鍵と salt）が含まれないことを確認するテストを追加する。実装の前に失敗することを確認する（DC-182-7）。
  - 依存：なし
  - 範囲：`test/module/media/infrastructure/storage/MediaStoragePropertiesTest.java`、`test/module/media/infrastructure/converter/ImgproxyPropertiesTest.java`（新規）
- [x] **T182-01-06** 2 つの設定の record の `toString` を上書きし、秘密の値を `***` にする。書き方は `StripeProperties` に倣う。バックエンドの書き方 第 6.1 節と規則の一覧の表に、秘密の値を持つ設定の record の `toString` を伏せる規則と確認方法（単体テスト、レビュー）を追加する（DC-182-7、8）。
  - 依存：T182-01-05
  - 範囲：`main/module/media/infrastructure/storage/MediaStorageProperties.java`、`main/module/media/infrastructure/converter/ImgproxyProperties.java`、`docs/rules/backend-coding.md`
- [x] **T182-01-07** [P] [テスト] `AggregateGuardsTest` の期待値を、集約の名前だけのメッセージに直し、保存した状態の文字列を含まないことを確認する。呼び出しの引数は変えない（T182-01-08 で直す）。理由：コンパイルに失敗させると、並行するタスクのレッドの確認ができなくなるため。実装の前に失敗することを確認する（DC-182-10）。
  - 依存：なし
  - 範囲：`test/common/model/base/AggregateGuardsTest.java`（`:58`）
- [x] **T182-01-08** 集約を戻す補助から保存した状態の引数を削除し、デバッグ用のメッセージを集約の名前だけにする。原因の例外は残す。呼び出し元 3 つと、テストの呼び出し 2 か所を直す（DC-182-9）。
  - 依存：T182-01-07
  - 範囲：`main/common/model/base/AggregateGuards.java`、`main/module/catalog/domain/category/CategoryFactory.java`、`main/module/catalog/domain/product/ProductFactory.java`、`main/module/tax/domain/taxcategory/TaxCategoryFactory.java`、`test/common/model/base/AggregateGuardsTest.java`（`:45,53`）
- [x] **T182-01-09** [P] 共通の設定から Zipkin の送信先を削除し、`management.tracing.export.zipkin.enabled: false` を置く。local の profile に `enabled: true` と送信先を置く。環境変数の一覧に送信を有効にする環境変数（既定 false。local と E2E は true）を追加し、送信先の説明を「送信を有効にしたときの送信先」に直す。共通コードの設計書のトレースの節も同じに直す。local の profile で起動し、自動構成の判定（`/actuator/conditions`）に Zipkin の送信の Bean があることを確認する（DC-182-11、12。plan 第 4 節）。
  - 依存：なし
  - 範囲：`main/resources/application.yml`（`:76-79`）、`main/resources/application-local.yml`（`:47-50`）、`docs/guides/environment.md`（`:20`）、`docs/design/common.md`（トレースの節）
- [x] **T182-01-10** [P] [テスト] `CreatedResponsesTest` の 3 つの期待値を、ホストを含まない相対の URL に直す。実装の前に失敗することを確認する（DC-182-14）。
  - 依存：なし
  - 範囲：`test/common/presentation/CreatedResponsesTest.java`（`:38-68`）
- [ ] **T182-01-11** 作成の応答の補助で、`Location` をホストを含まない相対の URL にする。基にするパスは今のまま（今のリクエストのパス、またはアプリの起点）で、スキーム・ホスト・ポートを除く。メソッドの Javadoc を直す。契約の `Location` の説明 2 か所、API の規約の作成の行の※1、バックエンドの書き方 第 2.2 節の作成の応答の行に、相対の URL であることを書く。`Location` の URL で取得する既存の API のテスト（カテゴリ、商品、税区分）が通ることを確認する（DC-182-13、14）。
  - 依存：T182-01-04、T182-01-06、T182-01-10
  - 範囲：`main/common/presentation/CreatedResponses.java`、`api/openapi/store.yaml`（`:637-640`）、`api/openapi/admin.yaml`（`:1370-1373`）、`docs/rules/api-conventions.md`（`:33`）、`docs/rules/backend-coding.md`（`:144`）
- [ ] **T182-01-12** [P] 出荷の登録の service が、注文の参照の service の運営者用の詳細を読み、操作の可否で出荷できるかを判定するようにする。注文の Repository の依存を削除し、クラスの Javadoc を直す。既存の出荷の登録の API のテストが通ることを確認する（DC-182-15、16）。
  - 依存：なし
  - 範囲：`main/module/order/application/shipment/OrderShipmentService.java`
- [ ] **T182-01-13** 段階のレビュー（profile「厚め」）と全体の検査：一般の観点を汎用で 2 回、重要な観点（セキュリティ、ファイルをまたぐ影響）を高性能と汎用で 1 回ずつ、計 4 回実施し、指摘の扱いを決めて修正する。spec の最初の commit からの差分で、契約の差分が `security: []` の追加と `Location` の説明だけであり、`db/migration/` に差分がないことを確認する（DN-182-2）。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T182-01-01〜12
