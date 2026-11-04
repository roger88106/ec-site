# バックエンドのテストの書き方

バックエンドのテストを書く・レビューするときの規則。共通の規則（テストの種類と基準、テスト名、待ち方）は [テストの書き方](test-writing.md)。

## 1. 統合テストの土台

- 統合テストは `@IntegrationTest` を付ける。Spring と、Testcontainers の PostgreSQL・SeaweedFS・imgproxy が起動する ※1。例外は、本番の jar を別のプロセスで起動するテスト（`ProductionJarStartupTest`）で、Spring の外で動くので、必要な PostgreSQL を自分で起動する。
- API のテストは `@AutoConfigureMockMvc` の MockMvc で呼び、時刻は `TestClockConfig` で固定する。
- データを残さないテストは `@Transactional` で巻き戻す。
- 巻き戻せないテスト（非同期のリスナー、並行、別のトランザクションで確定させるもの）は、テストごとに作る値（UUID や実行ごとの印）でデータを分ける。確認するのは、自分のデータだけである。表の全体の数や一覧の全体は確認しない（テストごとに `reset` する mock の記録は、そのテストのものとみなしてよい）。後処理（`@AfterEach` で削除する）は不要で、今あるものは残してよい。理由：DB はテストの実行ごとに作り、削除には漏れと順序（外部キー）の手間があるため。
- ログを確認するテストは、そのテストの中で出た出力だけを確認する。確認する処理の前に `CapturedLogs.mark` で位置を記録し、確認では `CapturedLogs.since` でその後の出力だけを読む。理由：`CapturedOutput` に残る他の出力（`CapturedLogs` の Javadoc）が件数に数えられ、件数を誤っていても通るため。
- 巻き戻せないテストで、他のテストのデータも処理の対象になるときは、処理の前に対象の行を数え、ログの件数がその数と等しいことを確認する（「1 件以上」だけにしない）。

※1：IdP は起動せず、`TestSecurityConfig` が署名の鍵を `TestTokens` の鍵に差し替える。Keycloak は、それを確認するテストだけが自分で起動する。

理由：テストごとにコンテナを再起動せず、データが他のテストに漏れないようにするため。

確認方法：レビュー

## 2. テストデータは補助のクラスで作る

複数のテストで使うデータは、統合テストの補助（次の表）で作る。複数のモジュールのテストで使う補助は `src/integrationTest` の `com.ecsite` の直下に、1 つのパッケージのテストだけが使う補助はそのパッケージに置く。テストのクラスで同じ手順を再定義しない。

| 補助 | 作るもの |
|------|----------|
| `CatalogTestData` | 税区分、カテゴリ、商品（バリアント・画像・公開・販売の終了・公開期間） |
| `InventoryTestData` | 在庫と、入荷の調整 |
| `OrderTestData` | カート、注文手続き、注文、支払画面 |
| `ShippingTestData` | 出荷の登録、配送状態の通知、送り状番号 |
| `PaymentTestData` | 支払の作成、与信、売上確定、状態の取得 |
| `TestJson` | 応答の本文から読む ID（作成の応答の ID など） |

token は `TestTokens`、画像は `TestImages`、ログの確認は `CapturedLogs` を使う。

補助以外のテストは、補助が持つ手順を直接書かない（その操作そのものを確認するテストを除く）。手順と、手順を書いてよい補助・テストの対応の正本は `scripts/ci/test-helpers.mjs` の `HELPER_STEPS`。

次の 3 つは手順に含めず、テストが直接書いてよい。

- Repository のテストが集約を直接作る処理（支払の Repository のテストなど）
- 作った後に変更する呼び出し（店舗の鮮度のテストで税率を変えるなど）
- 一覧の応答から複数の ID を読む処理（画像の並び、税率の一覧など）

イベントの発行の記録（`EventPublicationRegistry`）を読む操作は、非同期のイベントの補助（`AsyncEvents`、`EventPublications`）の中だけに置く（`scripts/ci/test-helpers.mjs` の冒頭のコメント）。

理由：テストの本文に、確認する項目だけを残すため。

package-private の本番クラス（ジョブ、スケジュールのキャッシュなど）を別のパッケージの統合テストから使うときは、同じパッケージの `src/integrationTest` に public final の補助クラスを置く。補助クラスは、Spring のコンテキストから Bean を取り出して呼ぶ。テストのために可視性を広げない。

確認方法：レビュー（記録を読む操作と、手順を補助の外で書かないことは自動。`scripts/ci/test-helpers.mjs`）

## 3. テストの置き場所

main と同じパッケージに置く。Spring を起動しないものは `src/test`、起動するものは `src/integrationTest`。

確認方法：自動（ソースセット）

## 4. 単体テストの補助

単体テストの補助は、`src/test` の `com.ecsite` の直下に置く。ただし `Contracts` は、単体テストと統合テストが共有するので `src/testFixtures` の `contract/` に置く。

| 補助 | 使う場面 | 使い方 |
|------|----------|--------|
| `ProductionClasses` | 構成の検査（`architecture/`、`modulith/`）と契約のテスト（`common/message/`、`contract/`） | 本番のクラスとモジュールの一覧を、この補助から読む。テストのクラスごとに `ClassFileImporter` や `ApplicationModules.of` で読み込まない |
| `TestSettings` | 設定のテスト | `load(profile…)` と `bind(接頭辞, 設定の型, 値)` で読む。設定ファイルの読み込みと `Binder` の組み立てを、テストのクラスに書かない |
| `Contracts`（`src/testFixtures` の `contract/`） | 契約のテストと構成の検査、店舗 API の token と Keycloak の統合テスト | OpenAPI の契約と realm の設定と設計書の置き場所は、この補助の関数（`openApi`・`realm`・`path`）で読む。単体テストのクラスに `System.getProperty` や既定の相対パスを書かない ※2 |

※2：統合テストの `RegionApiTest` と `ProductionJarStartupTest` は、Gradle が渡す別の property を読むので対象にしない。

理由：クラスの複製で heap を使わず（`ProductionClasses`）、全ての設定のテストが同じ重ね方で値を読み（`TestSettings`）、Gradle の設定の誤りに気づく（`Contracts`）ため。

確認方法：`ProductionClasses` を使うことは自動（`ProductionClassesTest`。探す呼び出しの一覧はそのテストにある）、`TestSettings` と `Contracts` を使うことはレビュー

## 5. テストのコードの重複

PMD の CPD（Gradle のプラグイン `de.aaschmid.cpd`）が、`./gradlew check` で重複を検出し、見つかると失敗にする（タスクは `cpdCheck`。報告の置き場所は[テストの実行方法](../06-手順書/running-tests.md)の「バックエンド」）。

| 項目 | 内容 |
|------|------|
| 対象 | `src/test`、`src/integrationTest`、`src/testFixtures` の Java のファイル。`src/main` は、生成したコードを含むので見ない |
| 基準 | 100 トークン以上の同じ並び（CPD の既定。変数名と値の違いは同じとみなさない）。基準を上げるときは、値と理由をこの節に書く |
| 修正方法 | 補助に移す。同じ種類のテストデータの作り方は、補助のクラス（「テストデータは補助のクラスで作る」）か、テストのパッケージの補助に置く |
| 修正しない重複 | 重複の場所を `// CPD-OFF 理由` と `// CPD-ON` で囲む。理由は `CPD-OFF` の行に書く。理由：除外と理由を一緒に読めるため |

囲むのは、補助に移すとテストが何を確認するかが読めなくなるとき、または補助にするほどの共通の意味がないときだけ（例：各 API のテストの登録の応答の確認の並び、テストデータの並び）。「修正が面倒」は理由にならない。

理由：同じコードを複数のテストに複製すると、仕様の変更で修正漏れが出て、テストごとに確認の内容がずれるため。

確認方法：自動（`./gradlew check` の `cpdCheck`。PMD の CPD）
