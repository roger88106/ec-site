# Plan 223：バックエンドの機械の検査と静的解析を補う

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

第 11 節の 5 つの選択は推奨どおりに決めた。利用者の確認は、第 6 弾の特例で省いた（2026-10-27。利用者は後のリファクタリングの段階でまとめて確認する）。

前提：spec 223 が確定した。文書の書き方は skill `docs` と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 設定と契約の記載漏れ・誤記 | 第 1 節 | DS-223-1（DC-223-1〜4） |
| テストの書き方の取り決め | 第 2 節 | DS-223-2（DC-223-5〜9） |
| 構成の検査の補助 | 第 3 節 | DS-223-3（DC-223-10〜12） |
| spec の番号の検査 | 第 4 節 | DS-223-4（DC-223-13〜17） |
| import と認証の情報の取り出し | 第 5 節 | DS-223-5（DC-223-18、19） |
| モジュールの境界と重複 | 第 6 節 | DS-223-6（DC-223-20〜23） |
| 文書 | 第 7 節 | 全て |
| 段階、確認の方法、並行する spec | 第 8〜10 節 | 全て |
| 利用者に確認すること（決定済み） | 第 11 節 | DC-223-5、7〜9、20〜22、24 |

第 1〜6 節の表の DC の列は、`DC-223-` の後の番号だけを書く（`1` は DC-223-1）。第 8 節の表の DC の列は「223-1」の形で書く。

共通の決定：

- パスは、断りがなければ `backend/ecsite/src/` からの相対で書く。文書、CI の検査の script、フロントのパスは、リポジトリのルートからの相対で書く。次の略を使う。

| 略 | パス |
|----|------|
| `main/` | `main/java/com/ecsite/` |
| `test/` | `test/java/com/ecsite/` |
| `it/` | `integrationTest/java/com/ecsite/` |
| `fix/` | `testFixtures/java/com/ecsite/` |
| `tools/` | `tools/java/com/ecsite/` |

- 行の番号は、2026-10-27 の調査（`develop` の d3174a65）のものである。実装を始める前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）で、今のコードと合わせる。
- 追加した検査は、違反する例で違反を検出できることを確認する（DN-223-6）。構成の検査は `test/architecture/` の下に違反の例のクラスを置き（今の `NamingTest` などと同じ方式）、CI の検査の script はテストに違反の例を書き、Checkstyle は一時的に違反を入れて失敗を確認する。
- API の契約（`api/openapi/`）とマイグレーション（`db/migration/`）を変えない（DN-223-2）。確認方法は、最後の段階のレビューで、spec の最初の commit からの差分にこの 2 つのディレクトリのファイルがないことを確認する。
- 実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` は契約の読み込みの変更（DC-223-3、4）だけである。理由：認可の条件を作る処理を変えるため。このタスクの実装役は高性能にし、実装の後に高性能のレビュー（profile「厚め」）を実施する。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組と、CI の検査の script のテストと実装で、2 つのタスクを 1 commit にする（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。
- 手元の環境（スロット）は、`./gradlew check` を実行するタスクで使う。段階 2 では、段階のレビューだけで使う。他のタスクは CI の検査の script だけを実行するので使わない。
- 本書の「補助」は、断りがなければ次のどれかを指す。

| 語 | 指すもの |
|----|----------|
| 構成の検査の補助 | 本番のクラスの読み込みと定数を持つ単体テストのクラス（`test/ProductionClasses.java`） |
| 契約の補助 | 契約と realm の置き場所を読む単体テストのクラス（`Contracts`。spec 224 が `fix/` に移す） |
| テストの補助 | 統合テストがデータの作成と応答の読み取りに共通で使うクラス（`it/` の直下の `InventoryTestData`、`CatalogTestData`、`TestJson`、`AsyncEvents` など） |

## 1. 設定と契約の記載漏れ・誤記（DS-223-1：DC-223-1〜4）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 1 | 設定の型（`@ConfigurationProperties` の付いたクラス）のうち、秘密の値の項目を持つものは、`toString` を自分で宣言する。この規則を構成の検査に加える | `test/architecture/ConfigurationPropertiesTest.java`、違反の例のクラス | 違反の例（秘密の値の項目を持ち、`toString` を宣言しない設定の型）で検査が失敗する。今の 4 クラス（第 1.1 節）で通る |
| 2 | 店舗の操作の `security` がないとき、表の「不要」にせず、問題に加える | `scripts/ci/role-permissions.mjs:160-164`、テスト | テストに、`security` のない店舗の操作が問題になる例と、`security: []` が「不要」になる例を書く |
| 3 | パスの下のキーが HTTP メソッドの大文字小文字を変えた形（`GET` など）のとき、契約の読み込みが例外を投げ、起動に失敗する | `main/config/security/OpenApiOperations.java:36,90-94`、`test/config/security/OpenApiOperationsTest.java` | 単体テストに、大文字のメソッドで失敗する例を加える |
| 4 | パスの下のキーは、HTTP メソッド、OpenAPI の Path Item の項目（`summary`、`description`、`servers`、`parameters`、`$ref`）、拡張（`x-` で始まるもの）だけを認め、他は例外を投げる | 同上 | 単体テストに、誤記の項目（`gett` など）で失敗する例と、認める項目で通る例を加える。今の 2 つの契約で起動が通る（統合テスト） |

### 1.1 秘密の値とみなす項目の名前（DC-223-1）

項目の名前を小文字にして、次のどれかに当たれば秘密の値とみなす。

| 当てはめ | 例（今のコード） |
|----------|------------------|
| `secret`、`password`、`salt`、`token`、`credential` を含む | `secretAccessKey`、`webhookSecret`、`salt` |
| `key` で終わる | `key`、`secretKey` |

- 今の対象は、画像変換の設定（`ImgproxyProperties`）、画像の保存先の設定（`MediaStorageProperties`）、決済の設定（`StripeProperties`）、出荷の設定（`ShippingProperties`）の 4 つで、全て `toString` を宣言している。
- `accessKeyId` のように `key` を途中に含む名前は、上の当てはめに当たらないが、同じ型が秘密の値の項目（`secretAccessKey`）を持つので対象になる。
- 対象の型は、`@ConfigurationProperties` の型と、その項目からたどれる `com.ecsite` の型（入れ子の record など。注釈がない）である。秘密の値の項目を自分で持つ型が、`toString` を宣言する。理由：外側の型の既定の `toString` は入れ子の型の `toString` を呼ぶので、入れ子の型が宣言しないと値が出るため（段階 1 のレビュー、2026-10-27）。
- Lombok の `@ToString`・`@Data`・`@Value` で作る `toString` は、宣言したものと区別できない（注釈がクラスファイルに残らない）。そのため、設定の型でこれらを使わないことを、バックエンドの書き方の規則に書く。
- 検査は `toString` の宣言の有無だけを確認する。値を隠しているかは、各設定の型の単体テストが確認する（今のとおり）。理由：隠し方（固定の文字、項目を出さない）が設定ごとに違い、構成の検査では判定できないため。

### 1.2 契約の読み込みの変更の範囲（DC-223-3、4）

- 例外のメッセージに、契約のファイル、パス、キーを入れる。理由：起動のログだけで直す場所が分かるようにするため。
- 操作の下のキー（`security`、`x-required-permission` など）の誤記は、今も起動に失敗する（店舗の操作の認可の条件の読み込みと、運営者の権限の読み込みが、値がないときに例外を投げる）。本 spec では変えない。
- エラーコードの契約のテスト（`test/contract/ErrorCodeContractTest.java`）が持つ HTTP メソッドの一覧は、本 spec では変えない。理由：契約の操作の応答を読むテストで、契約の読み込みの規則とは役割が違うため。

## 2. テストの書き方の取り決め（DS-223-2：DC-223-5〜9）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 5 | PMD の CPD（Gradle のプラグイン）で、`src/test`、`src/integrationTest`、`src/testFixtures` の Java のコードの重複を検出し、`./gradlew check` が実行する（第 11.1 節の案 A）。段階 3 で行う ※1 | `backend/ecsite/build.gradle.kts`、`backend/ecsite/gradle/libs.versions.toml`、ライセンスの許可の一覧（プラグインのライセンスによる） | 一時的に重複を入れて `./gradlew check` が失敗する。基準と、今の重複の扱いをテストの書き方に書く（DN-223-5） |
| 6 | 新しい CI の検査の script `test-helpers` が、統合テストで、イベントの発行の記録を読む操作（`EventPublicationRegistry` の `find` で始まる操作）を呼ぶ行を探し、テストの補助（`it/AsyncEvents.java`、`it/config/event/EventPublications.java`）の外にあれば失敗にする | `scripts/ci/test-helpers.mjs`、テスト | テストに、補助の外で記録を読む例（違反）と、補助へ渡すために注入するだけの例（違反でない）を書く |
| 7〜9、24 | 同じ script に、テストの補助を除くテストで、在庫の作成、税区分の登録の要求、応答の JSON から ID を取り出す処理、カテゴリの登録の要求、支払の作成の操作を探す規則を加える。補助と、操作そのもののテストの一覧は、テストの書き方の統合テストのデータの表（spec 224 が書く）を読む。表のどの行を検査するかは第 11.2 節で決める | 同上 | テストに、規則ごとの違反の例と、表の「除く」のファイルの例を書く。spec 224 のマージの後の `develop` で違反が 0 件 |

※1：spec 224 が統合テストとテストの補助を書き換えるので、そのマージの後に重複を測る。先に測ると、spec 224 の変更で重複と除外の位置が変わるため。

- DC-223-6 と DC-223-7〜9、24 を 1 つの script にする。理由：どちらも「統合テストの補助だけが書いてよい呼び出し」を探す規則で、補助のファイルの一覧と、コメントを除いて読む処理を共有できるため。
- `test-helpers` は、Java のコメントを除いて読む（`lib/strip-comments.mjs`）。他の検査の script と同じく、対象のファイルが 0 件のときは失敗にする。
- DC-223-6 で、補助へ渡すために注入するだけのテスト（イベントの片付けと再発行のジョブのテスト）は違反にしない。理由：記録を読む処理は補助の中にあり、テストは補助を作るために依存を渡しているだけのため。

### 2.1 重複の検出の基準（DC-223-5）

| 項目 | 決定 |
|------|------|
| 最小の長さ | CPD の既定の 100 トークン。段階 3 の最初に今のテストのコードで測り、検出された重複を一覧にする |
| 今の重複 | 補助へ移して直せるものは、そのタスクで直す。直さないもの（テストのデータの並びなど）は、`CPD-OFF` と `CPD-ON` のコメントで囲む。`CPD-OFF` の行に、直さない理由を書く |
| 基準を緩めるとき | 最小の長さを上げるときは、値と理由をテストの書き方に書く（DN-223-5） |

- 測った結果、直すものが 20 か所を超えるときは、段階 3 を止めて利用者に確認する（DN-223-4）。理由：重複の修正が本 spec の検査の追加より大きくなり、spec の範囲を変える判断になるため。

## 3. 構成の検査の補助（DS-223-3：DC-223-10〜12）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 10 | 文書の置き場所の system property を読む 3 つのテストは、契約の補助（spec 224 が `fix/` に移す `Contracts`）の読み取りの操作を使う。値がないときの例外のメッセージは、契約の補助と同じ形（property の名前と、ビルドの設定の確認の案内）にする | `test/architecture/ModuleStructureTest.java:130`、`test/modulith/ModuleDiagramTest.java:39`、`test/ProductionClassesTest.java:30` | Gradle の単体テストのタスク（`test`）が渡す property を一時的に外して、3 つのテストが property の名前を示して失敗することを確認する |
| 11 | 生成したコードの接頭辞（`com.ecsite.api`、`com.ecsite.jooq`）とモジュールの接頭辞（`com.ecsite.module.`）を、`test/ProductionClasses.java` の定数だけに置く。他のテストの同じ定数と、接頭辞を組み立てる文字列は、その定数を使う | `ProductionClasses.java:48,53,58`、`SchemaOwnershipTest.java:30,72,81`、`ModuleStructureTest.java:66,154`、`ConfigurationPropertiesTest.java:32,53,57`、`ModuleRoleTest.java:28,42,52,54`、`ProductionClassesTest.java:50,52,53,62,109-111` | `git grep -n '"com.ecsite.\(api\|jooq\|module\)' -- backend/ecsite/src/test` が `ProductionClasses.java` の定数と、クラスの完全修飾名をデータとして書くテストだけになる |
| 12 | エラーの種類の列挙を探す処理を `ProductionClasses` の操作にし、文言の契約のテストとエラーコードの契約のテストがそれを使う | `ProductionClasses.java`、`test/common/message/MessageContractTest.java:61-64`、`test/contract/ErrorCodeContractTest.java:72-76` | 2 つのテストが通り、探す処理の重複がない。`ProductionClasses` の操作を一時的に空の一覧にして、2 つのテストが失敗することを確認する |

- DC-223-11 の対象は、接頭辞として使う文字列だけである。クラスの完全修飾名をデータとして書くテスト（文言の補間のテスト、項目の制約の契約のテストなど）は変えない。理由：クラスを特定するための値で、接頭辞の重複ではないため。
- DC-223-10 は段階 3 で行う。理由：spec 224 が契約の補助を `fix/` に移すので、その後に同じ操作を使うため（第 10 節）。

## 4. spec の番号の検査（DS-223-4：DC-223-13〜17）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 13 | 番号の正規表現に、記号と数字の間に区切りのない形（`AC10`、`DC7` など）を加える | `scripts/ci/spec-numbers.mjs:22`、テスト | テストに、`AC10` が違反になる例と、記号で始まる別の語（`ACME10`、`US1A` など）が違反にならない例を書く |
| 14 | 読んだ文字を Unicode の NFKC で正規化してから、同じ正規表現で照合する。報告する行の文字は正規化の前のものにする | 同上 | テストに、全角の英字・数字・ハイフンの違反の例を書く |
| 15 | 対象に、Gradle の設定のファイル（`*.gradle.kts`）のコメントを加える | 同上 | テストに、Gradle の設定のファイルのコメントの違反の例を書く |
| 16 | 対象に、設定のファイル（下の表）を加える。読むのはコメントだけにする | 同上 | テストに、設定のファイルの違反の例を書く |
| 17 | 新しく見つかる番号（`backend/ecsite/build.gradle.kts` の 3 件、`e2e/playwright.config.ts` の 2 件、`e2e/playwright.showcase.config.ts` の 1 件）を、規則を言葉で書く形に書き換える | 各ファイル | `node scripts/ci/spec-numbers.mjs` が通る |

設定のファイルとして対象に加えるもの：

| 置き場所 | ファイル |
|----------|----------|
| リポジトリのルート | `*.ts`、`*.mjs`（今は `knip.ts`） |
| 各パッケージの直下（`frontend/admin`、`frontend/store`、`frontend/shared`、`e2e`） | `*.config.ts`、`*.config.mjs` |
| `backend/ecsite/` | `*.gradle.kts` |

- DC-223-13 の誤検出が多いときは、記号の後に数字だけが続く語（`AC10`）に限り、英字が続く語（`US1A` など）は対象にしない。正規表現は、テストの例で確認してから commit する。
- spec 226 は、番号の検査の対象を E2E とフロントのテストのコードに広げる（spec 226 の第 6 節の 2）。E2E の設定のファイルを spec 226 も対象に含めたときは、後でマージする側が対象の定義を 1 つにまとめる（第 10 節）。

## 5. import と認証の情報の取り出し（DS-223-5：DC-223-18、19）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 18 | Checkstyle に `AvoidStarImport` を加える。例外（`excludes`、`allowStaticMemberImports` など）は設定しない | `backend/ecsite/config/checkstyle/checkstyle.xml:20-24` | 一時的にワイルドカードの import と static のワイルドカードの import を入れて、`./gradlew checkstyleMain checkstyleTest` が失敗する。今のコードは 0 件 |
| 19 | token の取り出し（`main/common/security/CurrentToken`）と、会員・運営者の取り出し（`CurrentMember`、`CurrentOperator`）を使えるのは、presentation 層と `common.security` だけであることを、構成の検査に加える。例外は下の表の 2 つ | `test/architecture/ModuleStructureTest.java`（`ALLOWED_API_USES` の近く）、違反の例のクラス、バックエンドの書き方 | 違反の例（application 層から取り出す）で検査が失敗する |

DC-223-19 の例外：

| 呼び出し元 | 置き場所 | 例外の理由 |
|------------|----------|------------|
| 会員 ID を返す公開の API（`CurrentMemberIds`） | `main/module/member/api/` | 他のモジュールの presentation 層に会員 ID を渡すための公開の API。`CurrentMemberIds` 自身も取り出しの対象に加え、呼び出し元を presentation 層に限る ※2 |
| ログイン中の利用者の情報を返す API（`MeController`） | `main/config/security/` | モジュールに属さない API の入口で、役割は presentation 層と同じ |

- 2 つとも残す。理由：どちらも presentation 層の役割を持ち、移すとモジュールの構成が変わる（spec の範囲を超える）ため。

※2：他のモジュールの公開 API を使う規則（`ALLOWED_API_USES`）は、`infrastructure/directory/` からの利用を対象から除くので、それだけでは directory を経て application 層が会員 ID を得る形を防げない（段階 1 のレビュー、2026-10-27）。

## 6. モジュールの境界と重複（DS-223-6：DC-223-20〜23）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 20 | 出荷モジュールの公開 API に、配送業者の表示名のキーを返す操作を置く。通知モジュールは、配送業者の表示名のキーを得る port を application に置き、`infrastructure/directory/` の実装が出荷モジュールの公開 API を呼ぶ。実装は、出荷イベント（`OrderShipped`）の配送業者を、出荷モジュールの配送業者に `default` のない `switch` 式で変換する。通知モジュールの依存に出荷モジュールの公開 API を加え、package-info の説明を直す（第 11.3 節の案 A） | `main/module/shipping/api/`（新しい操作）、`main/module/notification/application/ShipmentMailComposer.java:31-32`、`main/module/notification/application/port/`（新しい port）、`main/module/notification/infrastructure/directory/`（新しい実装）、`main/module/notification/package-info.java`、`main/module/shipping/infrastructure/mock/MockCarrierController.java:133` | 出荷通知メールのテスト（`ShipmentMailTest`）が、今と同じ文面を確認して通る。`git grep '"ecsite.shipping.carrier.' -- backend/ecsite/src/main` が出荷モジュールの中だけ。Spring Modulith の検査が通る |
| 21 | jOOQ（`org.jooq` と生成したコード）を使えるのは、モジュールの `infrastructure/persistence/` だけであることを、構成の検査に加える。mock の出荷の問い合わせ（`MockShipmentQuery`）は例外にし、理由をバックエンドの書き方に書く（第 11.4 節の案 A）。モジュールの外の共通の設定（`config/event/EventPublicationRecords`）は今の規則（`SchemaOwnershipTest`）のとおり | `test/architecture/SchemaOwnershipTest.java`、バックエンドの書き方 第 1 節 | 違反の例（application 層で jOOQ を使う）で検査が失敗する |
| 22 | 管理画面の IdP の既定値は、管理画面の認証の設定（`frontend/admin/src/auth/oidc.ts`）の 1 か所に置く。ローカルの検査の script と CI は、本番のビルドに値を明示して渡すので、同じ値を重ねて書く（spec の「重ねて書く必要があるとき」）。その値と環境変数の文書の値が、既定値と一致することを CI の検査が確認する（第 11.5 節の案 B） | `scripts/ci/env-defaults.mjs`（規則の追加）、テスト、`frontend/admin/src/auth/oidc.ts:9-10`、`scripts/dev/check-frontend.sh:25-26`、`.github/workflows/ci.yml:293-294`、`docs/guides/environment.md:67-68` | テストに、どれかの値が違う例を書く。今の値で通る |
| 23 | 地域のマイグレーションの生成の処理が書く先頭のコメントを、コミット済みのマイグレーションの 1 行目と同じにする。Javadoc に、適用済みのマイグレーションに合わせる理由と、正しい設計書のパス（`docs/design/modules/region.md`）を書く。理由：コミット済みのコメントは、文書を移す前のパス（`docs/backend/modules/region.md`）を指すため | `tools/tools/region/RegionMigrationGenerator.java:87`、`test/tools/region/`（新しいテスト） | 生成の処理の先頭の 3 行と、コミット済みのマイグレーションの先頭の 3 行を比べる単体テストを加える。`./gradlew generateRegionMigration` の出力がコミット済みのものと一致する（郵便番号の CSV があるメインの作業ツリーで確認する） |

- DC-223-20 で、キーの文字列は出荷モジュールのドメインの配送業者の 1 か所にし、公開 API の操作はそれを呼ぶ。出荷モジュールの mock の画面は、ドメインの配送業者から直接キーを得る。理由：構成の検査の層の表で、infrastructure は同じモジュールの api に依存できないため（実装の T223-01-06 で判明、2026-10-27）。
- DC-223-20 の port と directory の形は、他のモジュールの公開 API を `infrastructure/directory/` だけから使う今の規則（バックエンドの書き方 第 1.3 節、構成の検査）に合わせる。規則の例外の一覧は変えない。
- DC-223-23 の単体テストは、生成の処理の先頭のコメントを返す部分を呼んで比べる。全体の生成は郵便番号の CSV（Git に入らない）が必要なので、単体テストでは行わない。

## 7. 文書

| 文書 | 変えること | DC | 段階 |
|------|------------|----|------|
| [バックエンドの書き方](../../rules/backend-coding.md) | 規則の一覧に、設定の型の `toString`、ワイルドカードの import、token の取り出しの置き場所、jOOQ の置き場所を加える（確認方法：構成の検査、Checkstyle）。token の取り出しと jOOQ の例外の表を加える | 1、18、19、21 | 1 |
| [テストの書き方](../../rules/test-writing.md) | 重複の検出の基準と除外の書き方、統合テストの補助を使う規則と `test-helpers` の検査（テストの書き方の統合テストのデータの表を読むことを含む） | 5〜9 | 2、3 |
| [CI の検査](../../guides/ci.md) | `test-helpers` の行を加える。`spec-numbers`、`role-permissions`、`env-defaults` の行を直す | 2、6〜9、13〜16、22 | 2、3 |
| [コメントの書き方](../../rules/comments.md) 第 1 節 | 番号を書かない対象に、設定のファイルを加える | 15、16 | 2 |
| 出荷・通知のモジュールの設計書 | 出荷の公開 API の操作と、通知の依存を加える | 20 | 1 |
| 地域のモジュールの設計書 | 生成の処理が、マイグレーションの先頭のコメントに文書を移す前のパスを書く理由（1 行） | 23 | 1 |
| [用語集](../../design/glossary.md) | 「テストの補助」の行に、バックエンドの統合テストの補助の置き場所（`backend/ecsite/src/integrationTest/java/com/ecsite/` の直下）を加える | 6〜9 | 2 |

## 8. 段階

| 段階 | ファイル | 内容 | DC | 開始の条件 |
|------|----------|------|----|------------|
| 1 | `tasks/01-main-rules.md` | 契約の読み込み、構成の検査、Checkstyle、通知と出荷、地域の生成 | 223-1、3、4、11、12、18〜21、23 | spec 222 のマージと、実装を始める前の照合 |
| 2 | `tasks/02-ci-scripts.md` | CI の検査の script | 223-2、6、13〜17、22 | 段階 1 と、spec 225 のマージ |
| 3 | `tasks/03-after-224.md` | テストの補助の規則、重複の検出、置き場所の読み取り | 223-5、7〜10、24 | 段階 2 と、spec 224 のマージ |
| 4 | `tasks/04-review.md` | spec の最後のレビュー、起票のまとめの案、pull request の前のレビュー、アーカイブ | 全て | 段階 3 |

- 段階 2、3 の前に、最新の `develop` を取り込み（merge）、開始の条件の spec がマージされたことと、その変更がこの段階のタスクの前提と合うことを確認する。合わなければ、tasks を先に直す（[開発の進め方](../../rules/workflow.md) 第 5.6 節と同じ方法）。結果を追跡の issue にコメントする。
- 開始の条件の spec がまだマージされていないときは、その段階に入らずに待つ。待つ間に他の段階を進めない（段階は順に進める）。
- 各段階の最後のタスクは、段階のレビュー（profile「厚め」）と、全体の検査の実行である。段階 1 は main のコードを変えるので、`./gradlew pitest` も実行する。
- pull request の作成はタスクにしない（[開発の進め方](../../rules/workflow.md) 第 4.2 節）。
- アーカイブ先は `docs/specs/archive/1to10/223-backend-checks/` である。

## 9. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・統合 | `backend/ecsite/` で `./gradlew check`（Checkstyle と CPD を含む） |
| CI の検査 | `scripts/ci/check-all.sh` |
| mutation | `backend/ecsite/` で `./gradlew pitest`（段階 1、4） |
| 画面 | `scripts/dev/check-frontend.sh admin`（段階 2 の DC-223-22 で、管理画面のビルドが通ることを確認する） |
| API の契約とマイグレーション（DN-223-2） | 段階 4 のレビューで、差分に `api/openapi/` と `db/migration/` のファイルがないことを確認する |
| 地域のマイグレーション（DC-223-23） | 段階 1 で、メインの作業ツリーの郵便番号の CSV を使って `./gradlew generateRegionMigration` を実行し、出力がコミット済みのものと一致することを確認する |

- E2E は実行しない。理由：消費者・運営者に見える処理を変えるのは、出荷通知メールの配送業者の表示名の取り方（DC-223-20）だけで、統合テストがメールの文面を確認するため。管理画面のコードは変えず、DC-223-22 は CI の検査を加えるだけである。
- `./gradlew check` と `check-frontend.sh` は続けて実行し、同時に実行しない。理由：同時に実行すると CPU が足りず、管理画面の結合テストがタイムアウトすることがある（2026-10-20 に起きた）ため。

## 10. 並行する spec

2026-10-27 に、各 spec の作業ブランチの spec（spec 224 は tasks を含む）と照合した。spec 225、226 の tasks は、照合の後に変わりうるので、段階 2 の前の照合で照合し直す。

| 本 spec の段階 | 変えるファイル | 重なる spec | 順序 |
|----------------|----------------|-------------|------|
| 全体 | 文書とコードのコメント | spec 222（#187 の語の置き換え） | spec 222 のマージの後に始める |
| 1 | `test/architecture/`、`ProductionClasses`、`test/contract/ErrorCodeContractTest.java`、`main/config/security/`、通知・出荷、tools、Checkstyle、バックエンドの書き方 | spec 224（`test/contract/` の契約の補助を `fix/` に移す。T224-02-04） | 並行してよい ※3 |
| 2 | `scripts/ci/`（`role-permissions`、`spec-numbers`、`env-defaults`、新しい `test-helpers`）、`build.gradle.kts` のコメント、`e2e/` の設定のファイル、テストの書き方、CI の検査、コメントの書き方、用語集 | spec 225（`scripts/ci/lib/` の走査の補助と、検査の script が守る関数の表）、spec 226（`spec-numbers` の対象、E2E、テストの書き方、コメントの書き方）、spec 224（`build.gradle.kts`、テストの書き方） | spec 225 のマージの後に始める ※4 |
| 3 | `scripts/ci/test-helpers.mjs`、`build.gradle.kts`（CPD）、重複を直すテスト、`test/` の 3 つのテスト、テストの書き方 | spec 224（テストの書き方の統合テストのデータの表、契約の補助、統合テスト） | spec 224 のマージの後に始める（spec の第 7 節） |

※3：段階 1 は契約の補助を変えない。spec 224 が契約の補助を移すと、`ErrorCodeContractTest` の import が変わり、段階 1 の T223-01-01 と同じファイルを変える。後でマージする側が import を合わせる。

※4：段階 2 の新しい検査の script が、spec 225 が整える共通の関数と走査のテストの表に従うため。spec 226 も spec 225 のマージの後に実装するので、本 spec の段階 2 と同じ時期に `spec-numbers` を変えることがある。そのときは、後でマージする側が `spec-numbers` の対象の定義とコメントの書き方を 1 つにまとめる。

- spec 227（店舗画面）とは、変えるファイルが重ならない。
- 共有の文書（テストの書き方、CI の検査、コメントの書き方、用語集）の衝突は、後でマージする pull request が `develop` を取り込んで解消する。

## 11. 利用者に確認すること

全て推奨の案に決めた（2026-10-27。第 6 弾の特例で、利用者の確認は省いた）。

### 11.1 重複の検出のツール（DC-223-5）

| 案 | 内容 |
|----|------|
| 案 A（推奨） | PMD の CPD を Gradle のプラグインで使い、`./gradlew check` で実行する。除外は `CPD-OFF`・`CPD-ON` のコメントで囲み、理由を書く |
| 案 B | jscpd をルートの `package.json` に加え、CI の画面の job（knip と同じ）で実行する。除外は設定のファイルに書く |

- 案 A の理由：対象が Java のテストのコードで、バックエンドの開発者が普段実行する `./gradlew check` で見つかる。除外が重複の場所にコメントとして残り、理由と一緒に読める。
- 案 B の理由：画面のテストにも同じツールを広げやすい。ただし、バックエンドの変更でも画面の依存の install が必要になり、手元の `./gradlew check` では見つからない。

### 11.2 テストの補助の規則の範囲（DC-223-7〜9、24）

spec 224 は、テストの書き方 第 3.2 節に、検査の対象と除くファイルの表を書く（DC-224-22）。その表は、本 spec の 3 つ（在庫の作成、税区分の登録、応答の ID の取り出し）に加え、カテゴリの登録と、支払の API の作成の操作も対象にする。

| 案 | 内容 |
|----|------|
| 案 A（推奨） | `test-helpers` は表の全ての行を検査する。spec に DC-223-24 として、カテゴリの登録と支払の作成の操作を加える |
| 案 B | 本 spec の 3 つだけを検査する。表の他の行は、検査しないことを表に書く |

- 案 A の理由：表と検査が一致し、表の行を「検査するもの」と「しないもの」に分けずに済む。追加の 2 つも spec 224 が直す対象で、spec 224 の DC-224-8 が違反のない状態を完了の条件にしている。段階 3 の前の照合で、違反が 0 件であることを確認する。
- 案 B の理由：spec の範囲のまま進められる。ただし、表の一部だけを機械で確認することになる。

### 11.3 通知モジュールの配送業者の表示名（DC-223-20）

| 案 | 内容 |
|----|------|
| 案 A（推奨） | 出荷モジュールの公開 API に、配送業者から表示名のキーを返す操作を置く。通知モジュールは、port と `infrastructure/directory/` の実装を置き、実装がイベントの配送業者を出荷モジュールの配送業者に変換して呼ぶ。通知モジュールが出荷モジュールの公開 API に依存する |
| 案 B | 注文の出荷のイベントに、配送業者の文言のキーを載せる。注文モジュールが出荷モジュールの公開 API でキーを得る |

- 案 A の理由：表示名は出荷モジュールが決める（spec 192 の決定）ので、出荷モジュールの公開 API から得る。依存は公開 API だけである。列挙の変換を `default` のない `switch` 式にするので、配送業者を追加したときにコンパイルで気づく。
- 案 B の理由：通知モジュールの依存が増えない。ただし、イベントに表示のための値が入り、注文モジュールが表示の都合を持つ。

### 11.4 mock の出荷の問い合わせの jOOQ（DC-223-21）

| 案 | 内容 |
|----|------|
| 案 A（推奨） | mock の置き場所（`infrastructure/mock/`）に残し、jOOQ の置き場所の規則の例外にする。理由をバックエンドの書き方に書く |
| 案 B | `infrastructure/persistence/` に移す |

- 案 A の理由：この問い合わせは mock の画面だけが使い、mock を無効にすると Bean が作られない。`persistence/` は port の実装（Repository）の置き場所で、mock だけが使うクラスを置くと、本番で使うクラスと区別しにくくなる。spec 192 も、この問い合わせを出荷の Repository から外して mock の側に置いた。
- 案 B の理由：例外がなくなる。ただし、`persistence/` に本番で使わないクラスが入る。

### 11.5 管理画面の IdP の既定値（DC-223-22）

| 案 | 内容 |
|----|------|
| 案 A | 値を 1 つのファイル（Vite の環境変数のファイルなど）に置き、3 か所がそれを読む |
| 案 B（推奨） | 管理画面の認証の設定を正とし、ローカルの検査の script と CI の値が一致することを CI の検査（`env-defaults`）が確認する |

- 案 B の理由：管理画面の本番のビルドは、IdP の値を明示しないと失敗する。誤ってローカルの値で本番をビルドしないためである。案 A で CI のビルドが値を 1 つのファイルから読むと、本番のビルドも同じファイルを読み、ローカルの値で成功しうる。
- 案 A の理由：値が 1 か所だけになる。ただし、本番のビルドで既定の値を読まないことを、別に保証する必要がある。
