# テストの書き方

テストの種類と、今のテストで守っている書き方。実行方法は [テストの実行方法](../guides/running-tests.md)、テスト先行の進め方は [開発の進め方](workflow.md) 第 2 節。

## 規則の一覧

| 節 | 規則 | 確認方法 |
|----|------|----------|
| 1.2 | カバレッジと mutation score は、基準の値（許容の幅を含む）を下回らない | 自動（`scripts/ci/report/coverage-baseline.mjs`） |
| 2.1 | テスト名に AC 番号を入れる | 自動（`ac-check.mjs`） |
| 2.2 | テスト名は振る舞いを書く | レビュー（`@DisplayName` があることは自動。`scripts/ci/test-names.mjs`）。フロントの `describe` の名前が識別子だけでないことは自動（`scripts/ci/frontend-rules.mjs`） |
| 3.1 | 統合テストの土台を使う。ログを確認するテストは、そのテストの中で出た出力だけを確認する | レビュー |
| 3.2 | テストデータは補助のクラスで作る。イベントの発行の記録を読む操作は、非同期のイベントの補助の中だけに置く | レビュー（記録を読む操作と、表の手順を補助の外で書かないことは自動。`scripts/ci/test-helpers.mjs`） |
| 3.3 | テストは main と同じパッケージ | 自動（ソースセット） |
| 3.4 | 単体テストでは補助（`ProductionClasses`、`TestSettings`、`Contracts`）を使う | `ProductionClasses` を使うことは自動（`ProductionClassesTest`）、`TestSettings` と `Contracts` を使うことはレビュー |
| 3.5 | テストのコードに、100 トークン以上の重複を置かない。修正しない重複は `// CPD-OFF 理由` と `// CPD-ON` で囲む | 自動（`./gradlew check` の `cpdCheck`。PMD の CPD） |
| 4.1 | 画面に見えるもので探す。テストでは `toBeTruthy`・`toBeDefined` を使わない。HTML の属性や要素の並びに依存して確認しない。E2E で、`.first()`・`.nth()`・`.last()` で選ばず、ボタンの名前は完全一致で探す | CSS のクラスの確認、`toBeTruthy`・`toBeDefined`、`getAllBy…` などの結果を `[数字]`・`.at(数字)` で選ぶことは、フロントのテストと E2E で自動。画面のテストの `data-slot`・`querySelector`、HTML の属性の並びに依存する正規表現も自動（`scripts/ci/frontend-rules.mjs`）、E2E の位置で選ぶメソッド（`.first()`・`.nth()`・`.last()`）とボタンの名前の完全一致（`exact: true`、先頭と末尾を固定した正規表現）も自動（同じ script）、他はレビュー |
| 4.2 | API の差し替えは型付きの MSW | 自動（型チェック） |
| 4.3 | E2E のデータに実行ごとの印 | レビュー |
| 4.4 | テストの関数は `test` にそろえる | レビュー |
| 4.5 | 複数のテストで使うものは、テストの補助に置く | レビュー |
| 4.6 | テストは型を回避する変換を使わない | 理由のコメントのない `as never`・`as unknown as` は自動（`scripts/ci/frontend-rules.mjs`）、理由が妥当かはレビュー |
| 4.7 | 要素のプロパティ・属性・状態を `toBe` で比べず、jest-dom の照合で確認する | 受け手が要素のもの（`getBy…` の結果やそれを入れた変数など）のプロパティ・属性は自動（`scripts/ci/frontend-rules.mjs`）、ほかはレビュー |
| 4.8 | テストのファイルで `fireEvent` を直接使わない | 理由のコメントのないものは自動（`scripts/ci/frontend-rules.mjs`）、理由が妥当かはレビュー |
| 4.9 | 環境変数は `vi.stubEnv` で変え、`process.env` に代入しない | レビュー |
| 4.10 | フロントのテストとテストの補助、E2E（`e2e/` の下の全ての TypeScript）の `const`・`let` で、TypeScript の lib のグローバルの名前をシャドーイングしない | 自動（`scripts/ci/frontend-rules.mjs`。名前の一覧は `scripts/ci/globals/lib-globals.mjs` が lib から生成し、CI が一致を確認する） |
| 4.11 | テストとテストの間に空行を置く | 自動（`scripts/ci/frontend-rules.mjs`。フロントのテストと補助、E2E） |
| 4.12 | 管理画面の `renderApp` が返す `user` は、入力のキーの間で待たない（`renderApp` を使わないテストは対象外） | レビュー |
| 5 | 時間で待たない。E2E で `networkidle` で待たず、タイムアウトを数字のリテラルで書かない | 時間で待つことは自動（`scripts/ci/time-waits.mjs`）、`networkidle` と、タイムアウトの数字のリテラル（`timeout` を含む項目、`intervals`、`setTimeout`・`setDefaultTimeout` などの引数。`e2e/src/waits.ts` を除く）も自動（`scripts/ci/frontend-rules.mjs`） |

確認方法が「レビュー」のものは、各 spec の [レビュー](review.md) で読む。

## 1. テストの種類と基準

### 1.1 テストの種類

| 種類 | 対象 | ツール | 置き場所 |
|------|------|------|----------|
| バックエンドの単体テスト | Spring を起動しないもの | JUnit、AssertJ | `src/test` |
| バックエンドの統合テスト | Spring と DB などをつないだもの | `@SpringBootTest`、Testcontainers | `src/integrationTest` |
| フロントの単体テスト | 画面に依存しない関数（判定、変換、表示の整形） | Vitest | 対象の横（`*.test.ts`） |
| フロントの結合テスト | コンポーネントを描画し、利用者の操作で振る舞いを確認する（API は MSW） | Vitest、React Testing Library | 対象の横（`*.test.tsx`） |
| E2E | 管理画面・店舗画面・バックエンドをつないだ、コアフローの少数のシナリオ | Playwright | `e2e/` |

- 必須の統合テスト：Modulith の境界、Repository（永続化・再構築・差分更新・イベント発行）、並行性（並行の不変条件を持つモジュール）、外部連携の port（mock と実装の挙動の一致）。
- 店舗画面には結合テストを置かない ※1。表示のコンポーネントは HTML にして DOM に読み込み、役割と名前で探して jest-dom で確認し、サーバーの処理（サーバーアクション、ログインのコールバックなど）は Node で直接呼んで確認し、画面をつないだ流れは E2E で確認する。例外：サーバーアクションを呼ばず、ブラウザーの中だけで終わる操作（バリアントの選択、残り時間の表示など）は、jsdom 上で Testing Library を使って操作し、確認する。もう 1 つの例外：サーバーアクションの結果の表示の遷移（同じ読み上げの領域が残り、前の文が消えるなど）は、サーバーアクションを `vi.mock` で差し替えて、jsdom 上で操作して確認する。
- E2E は専用の DB・IdP・画像のバケットで、毎回再作成した状態から実行する（ローカルの開発のデータを変えない）。
- 見た目（配置、色、余白）は目視で確認する。
- テストに出ない AC は CI が失敗にする。テストで確認できない AC は、理由と一緒に除外の一覧に書く。
- IdP（Keycloak）の振る舞いだけで決まる AC は、realm の設定ファイルの契約テスト（`RealmSettingsContractTest`）で確認する ※2。

※1：操作がサーバーアクションを通り、ブラウザーの中だけでは再現できないため。サーバーアクションを呼ばない操作はこの理由に当たらないので、例外にする。結果の表示の遷移は、同じ要素が残ることを、結果を変えて再描画する静的な HTML では確認できないので、サーバーアクションの処理は差し替え、表示だけを確認する（処理はサーバーアクションのテストと E2E で確認する）。

※2：IdP は自分たちで作っていないので、設定が正しければ IdP が振る舞いを守る、とみなす。IdP を取り替えたときは、会員の登録・ログインの E2E で振る舞いを確認する。対象の AC は、[AC 一覧](../design/acceptance-criteria.md) の「テスト」の列に `RealmSettingsContractTest` がある AC で、次はその例。

| AC | 振る舞い | 契約テストで確認する設定 |
|----|----------|--------------------------|
| AC-109 | 登録済みのメールアドレスでは登録できない | メールアドレスの重複を許さない |
| AC-110 | パスワードの条件を満たさない登録は拒否される | パスワードの方針 |
| AC-116 | パスワードを続けて間違えると一定時間ログインできない | 総当たりの防止の回数と時間 |
| AC-118 | パスワードの再設定を申し込める | パスワードを忘れたときの画面とメール |
| AC-134 | 運営者は自分で登録できない | 登録の画面が消費者の realm だけで有効 |
| AC-210、211 | 生年月日は任意で、後から入れられる | 利用者の属性の必須と、編集の可否 |

### 1.2 カバレッジと mutation score の基準

カバレッジ（行・分岐）と、domain の mutation score は、基準の値（許容の幅を含む）を下回らない。CI が基準の値と比べ、下回ると失敗にする。

- 対象は、行と分岐がバックエンド、管理画面、店舗画面、共有パッケージ（`frontend/shared`）、mutation score がバックエンドの domain。mutation score は、PIT で測る、テストが検出できた変異の割合。
- 基準の値は `scripts/ci/report/quality-baseline.json` に置く。対象ごとの合計だけで、ファイルやパッケージごとの基準はない。基準のファイルは、値ごとに、実行ごとのばらつきを吸収する許容の幅（`tolerance`）を持つ。
- 基準を下げるときは、`--write` で基準のファイルを書き換え、pull request に理由を書く。今回の値が基準を上回ったときは、失敗にせず、基準の更新を促す警告を表示する。
- カバレッジが低い箇所は、CI の job の summary に出る合計と、行のカバレッジが低い順の 10 件で見つける。mutation score は、生き残った変異（テストが検出できなかった変異）が多いクラスの上位 10 件が出る。

理由：基準がないと、テストが減っても弱くなっても気づけないため。

## 2. テスト名

### 2.1 AC 番号を入れる

AC を確認するテストは、名前（Java は `@DisplayName`、Vitest・Playwright は `test` の第 1 引数）の先頭に AC 番号を入れ、続けて振る舞いを日本語で書く（`AC-43 調整後に負になる調整は拒否される`）。複数の AC は 1 つずつ並べ（`AC-8、9、10`）、範囲（`AC-8〜10`）でまとめず、空白で区切った並び（`AC-1 AC-2`）にもしない。1→10 の AC は、spec の番号を付けて `AC-7-1、AC-7-2` と書く（`AC-7-1、2` は曖昧なので、ac-check が失敗にする）。

理由：AC からテストを、テストから AC を引けるようにするため。

### 2.2 名前は振る舞いを書く

「〜すると〜になる」の形で、画面や API に起きることを書く。メソッド名やクラス名をそのまま書かない。フロントの `describe` の名前も同じで、関数名・コンポーネント名だけにせず、日本語で振る舞いを書く（`describe('ページ送り', …)`。`describe('Pagination', …)` は書かない）。Java のテストは、全ての `@Test`・`@ParameterizedTest` に `@DisplayName` を付ける。名前に書く AC の番号は、spec の AC の文と照合してから書く（`ac-check` は番号があることだけを確認し、中身が合うかは見ない）。

理由：失敗したときに、どの振る舞いが壊れたかが名前だけで分かるようにするため。

## 3. バックエンド

### 3.1 統合テストの土台

- 統合テストは `@IntegrationTest` を付ける。Spring と、Testcontainers の PostgreSQL・SeaweedFS・imgproxy が起動する ※3。例外は、本番の jar を別のプロセスで起動するテスト（`ProductionJarStartupTest`）で、Spring の外で動くので、必要な PostgreSQL を自分で起動する。
- API のテストは `@AutoConfigureMockMvc` の MockMvc で呼び、時刻は `TestClockConfig` で固定する。
- データを残さないテストは `@Transactional` で巻き戻す。
- 巻き戻せないテスト（非同期のリスナー、並行、別のトランザクションで確定させるもの）は、テストごとに作る値（UUID、実行ごとの印）でデータを分け、自分のデータだけを確認する。表の全体の数や、一覧の全体を確認しない（テストごとに `reset` する mock の記録は、そのテストのものとみなしてよい）。後処理（`@AfterEach` で削除する）は不要 ※4。
- ログを確認するテストは、そのテストの中で出た出力だけを確認する。`CapturedOutput` には、テストの外で出た出力（Spring の起動のログ、前のテストの非同期の処理が後から出したログ）と、同じテストの中で確認の前に出た出力（準備の処理が出したログ）が残りうるので、確認する処理の前に `CapturedLogs.mark` で位置を記録し、確認では `CapturedLogs.since` でその後の出力だけを読む ※5。巻き戻せないテストで、ほかのテストのデータも処理の対象になるときは、処理の前に対象の行を数え、ログの件数がその数と等しいことを確認する（「1 件以上」だけにしない）。

※3：IdP は起動せず、`TestSecurityConfig` が署名の鍵を `TestTokens` の鍵に差し替える。Keycloak は、それを確認するテストだけが自分で起動する。

※4：DB はテストの実行ごとに作る（Testcontainers）。削除する処理には、削除の漏れと順序（外部キー）の手間がある。今ある後処理は残してよい。

※5：テストの外や準備の処理が同じ件数のログを出していると、その出力が件数に数えられ、件数を誤っていても通る。`CapturedOutput` はテストごとの capture を、終了時に解除するので、前のテストの中で出た出力は残らない。

理由：テストごとにコンテナを再起動せず、データが他のテストに漏れないようにするため。

### 3.2 テストデータは補助のクラスで作る

複数のテストで使うデータは、統合テストの補助（次の表）で作る。複数のモジュールのテストで使う補助は `src/integrationTest` の `com.ecsite` の直下に、1 つのパッケージのテストだけが使う補助はそのパッケージに置く。テストのクラスで同じ手順を再定義しない。

| 補助 | 作るもの |
|------|----------|
| `CatalogTestData` | 税区分、カテゴリ、商品（バリアント、画像、公開、販売の終了、公開期間） |
| `InventoryTestData` | 在庫と、入荷の調整 |
| `OrderTestData` | カート、注文手続き、注文、支払画面 |
| `ShippingTestData` | 出荷の登録、配送状態の通知、送り状番号 |
| `PaymentTestData` | 支払の作成、与信、売上確定、状態の取得 |
| `TestJson` | 応答の本文から読む ID（作成の応答の ID など） |

token は `TestTokens`、画像は `TestImages`、ログの確認は `CapturedLogs` を使う（`CapturedLogs` は `src/integrationTest` の `com.ecsite` の直下）。

補助以外のテストは、次の表の手順を直接書かない。ただし、その操作そのものを確認するテストは除く。CI の `scripts/ci/test-helpers.mjs` が、この表の全ての行について、「補助」と「その操作そのものを確認するテスト」の列のクラス以外のファイルで手順を書いていないかを調べる。2 つの列は、バッククォートで囲んだクラス名をそのまま読むので、クラスの名前を実際のファイルと合わせて書く（括弧の注記は読まない）。表の行や列を変えるときは、script の手順の対応も直す。

| 手順 | 補助 | その操作そのものを確認するテスト |
|------|------|----------------------------------|
| 在庫の作成 | `InventoryTestData` | `JooqStockItemRepositoryTest` |
| 税区分の登録の要求 | `CatalogTestData` | `TaxCategoryApiTest`、`AuthorizationTest`、`KeycloakIntegrationTest` |
| 作成の応答から ID を取り出す処理 | `TestJson`、`CatalogTestData` | `TaxCategoryApiTest`、`CategoryApiTest`、`ProductApiTest`、`PlaceOrderConcurrencyTest`（確認する API の作成の応答） |
| カテゴリの登録の要求 | `CatalogTestData` | `CategoryApiTest` |
| 支払の API の作成の操作 | `PaymentTestData` | `PaymentApiTest` |

表に手順の行を追加するときは、`scripts/ci/test-helpers.mjs` の `STEP_FINDERS` に探す関数を追加する（ないと検査が失敗する）。

表の手順に、次のものは含めない。

- Repository のテストが、集約を直接作る処理（支払の Repository のテストなど）
- 作った後の変更の要求（店舗の鮮度のテストの税率の変更など）
- 一覧の応答から、複数の ID を読む処理（画像の並び、税率の一覧など）

イベントの発行の記録（`EventPublicationRegistry`）を読む操作（`find` で始まる操作）は、非同期のイベントの補助（`AsyncEvents`、`EventPublications`）の中だけに置く。テストは記録を読んで待たずに、補助の待つ操作を呼ぶ。補助を作るために記録を注入して渡すだけのテストは、この規則に反しない。CI の `scripts/ci/test-helpers.mjs` が、統合テストで補助の外の呼び出しを見つける。

理由：テストの本文に、確認する項目だけを残すため。

package-private の本番クラス（ジョブ、スケジュールのキャッシュなど）を別のパッケージの統合テストから使うときは、その本番クラスと同じパッケージの `src/integrationTest` に、public final の補助クラスを置く。補助クラスは、Spring のコンテキストから Bean を取り出して呼ぶ。今ある補助クラスは、`TaxRateScheduleCaches`（catalog の `infrastructure/directory`）、`PaymentJobs`（payment の `application`）、`OrderPayments`（order の `application/payment`）、`TrackingRequests`（shipping の `application`）。本番クラスを public にしたり、テストのために可視性を広げたりしない。

### 3.3 テストの置き場所

main と同じパッケージに置く。Spring を起動しないものは `src/test`、起動するものは `src/integrationTest`。

### 3.4 単体テストの補助

単体テストの補助は、`src/test` の `com.ecsite` の直下に置く。ただし `Contracts` は、単体テストと統合テストが共有するので `src/testFixtures` の `contract/` に置く。

| 補助 | 使う場面 | 使い方 |
|------|----------|--------|
| `ProductionClasses` | 構成の検査（`architecture/`、`modulith/`）と契約のテスト（`common/message/`、`contract/`） | 本番のクラスとモジュールの一覧を、この補助から読む（モジュールの一覧は、Spring Modulith が自分でもクラスを読み込むので、クラスの読み込みとは別になる）。テストのクラスごとに `ClassFileImporter` や `ApplicationModules.of` で読み込まない |
| `TestSettings` | 設定のテスト | `load(profile…)` で `application.yml` に profile の設定ファイルを重ねた値を読み、`bind(接頭辞, 設定の型, 値)` で設定の型に値を当てはめる。設定ファイルの読み込みと `Binder` の組み立てを、テストのクラスに書かない |
| `Contracts`（`src/testFixtures` の `contract/` の下） | 契約のテスト（`contract/`）、構成の検査（`architecture/`、`modulith/`、`ProductionClassesTest`）、店舗 API の token の契約のテストと Keycloak の統合テスト（`src/integrationTest`） | `openApi(ファイル名)` で契約（OpenAPI）を、`realm(ファイル名)` で realm の設定を読み、`openApiDir()`、`realmsDir()`、`themeDir()` で置き場所を読み、設計書などそれ以外の置き場所（ファイルもディレクトリも）は `path(property の名前)` で読む。置き場所は、Gradle が system property で渡す。単体テストのクラスに `System.getProperty` や既定の相対パスを書かない（統合テストの `RegionApiTest` と `ProductionJarStartupTest` は、Gradle が渡す別の property を読むので対象にしない）。渡されないときは、置き場所が渡されなかったことを示して失敗する |

理由：同じクラスの読み込みをテストのクラスごとに繰り返すと、heap に同じクラスの複製が残り、メモリを使うため（`ProductionClasses` の理由）。設定の読み込みを 1 か所にして、全ての設定のテストが、同じ設定ファイルの重ね方で値を読むため（`TestSettings` の理由）。契約や realm の置き場所を既定の相対パスで読むと、Gradle が置き場所を渡さなくても通り、設定の誤りに気づけないため（`Contracts` の理由）。

本番のクラスの読み込みの検査（`ProductionClassesTest`）は、`src/test/java`、`src/integrationTest/java`、`src/testFixtures/java` の全ての Java のファイルを読む。次の文字列があれば失敗にする。ただし `ProductionClasses` のファイルは対象にしない。

- `importPackages(`
- `importPackagesOf(`
- `importClasspath(`
- `importPath(`
- `importLocations(`
- `ApplicationModules.of(`
- `@AnalyzeClasses`

`importClasses(` は、検査自身のテストが、違反する例のクラスを読むために使う。本番のクラスを読まないので、対象にしない。

### 3.5 テストのコードの重複

PMD の CPD（Gradle のプラグイン `de.aaschmid.cpd`）が、`./gradlew check` で重複を検出し、見つかると失敗にする（タスクは `cpdCheck`、報告は `backend/ecsite/build/reports/cpd/`）。

| 項目 | 内容 |
|------|------|
| 対象 | `src/test`、`src/integrationTest`、`src/testFixtures` の Java のファイル。`src/main` は、生成したコードを含むので見ない |
| 基準 | 100 トークン以上の同じ並び（CPD の既定。変数名と値の違いは同じとみなさない）。基準を上げるときは、値と理由をこの節に書く |
| 修正方法 | 補助に移す。同じ種類のテストデータの作り方は、補助のクラス（第 3.2 節）か、テストのパッケージの補助に置く |
| 修正しない重複 | 重複の場所を `// CPD-OFF 理由` と `// CPD-ON` で囲む。理由は `CPD-OFF` の行に書く |

修正しない重複の例は、API の仕様そのものを読めるように、各 API のテストに書く確認の並び（登録の応答の確認）と、テストデータの並び。補助に移すと、テストが何を確認するかが読めなくなるとき、または補助にするほどの共通の意味がないときだけ囲む。「修正が面倒」は理由にならない。

理由：同じコードを複数のテストに複製すると、仕様の変更で修正漏れが出て、テストごとに確認の内容がずれるため。除外を重複の場所のコメントに残すと、理由と一緒に読める。

## 4. フロント

### 4.1 画面に見えるもので探す

結合テストと E2E は、役割と名前で要素を探す（`getByRole('button', { name: '保存' })`、`getByLabel('名称')`）。CSS のクラス、`data-testid`、内部の状態、xpath、並びの 1 つ目は使わない。名前がなければ、画面に支援技術向けの名前を追加する。

- E2E は、位置で要素を選ぶメソッド（`.first()`・`.nth()`・`.last()`）と、`locator` の文字列のセレクターの中の位置（`nth=`、`:nth-match(`、`:nth-child(`、`:first-child`、`:last-child` など）を使わない。複数の要素に一致するときは、名前や、親の要素（行、領域）で絞る。
- E2E でボタンを名前で探すときは、`exact: true` を付けた文字列か、先頭（`^`）と末尾（`$`）を固定した正規表現で書く（`getByRole('button', { name: '保存', exact: true })`、`getByRole('button', { name: /^注文する（[\d,]+ 円）$/ })`）。固定した正規表現は、金額のように変わる値を含む名前に使う。ボタンの名前を変数で渡すときは文字列にし、`exact: true` を付ける（正規表現を入れた変数は、`exact` が効かず部分一致になるので使わない。検査は変数の中身を追わない）。

- 自分たちで作っていない画面（IdP）の要素の選び方は、`e2e/src/auth.ts` だけに置く。
- 詳細の項目の値（`dd`）は、Biome の検査が名前を認めないので、項目名の次の値を選ぶ書き方を `e2e/src/detail.ts` だけに置く。
- 結合テストで、詳細の項目の値と一覧の行を探す書き方は、テストの補助だけに置く（第 4.5 節）。考え方は `e2e/src/detail.ts` と同じ。テストのファイルは補助を呼ぶだけにする。
- Testing Library の文字の比較は、画面の側の空白（全角の空白を含む）を半角 1 つにそろえるが、探す文字はそろえない。探す文字の空白は半角で書く（`'全 45 件 2 / 3 ページ'`）。
- テストでは `toBeTruthy`・`toBeDefined` を使わない。探した要素があることは `toBeInTheDocument`（E2E は `toBeVisible` など）で、値は型や部分的な構造（`expect.any(…)`、`toMatchObject` など）で確認する。
- 表示のコンポーネントを HTML にして確認するとき、HTML の属性や要素の並びに依存する正規表現・文字の一致で確認しない。役割と名前で探して jest-dom で確認する。例外は、描画の結果が空文字であることの確認（`expect(renderMarkup(…)).toBe('')`）。

理由：画面を再作成しても、振る舞いが同じならテストが壊れないようにするため。位置で選ぶと、並びが変わったときに別の要素を操作しても通ってしまい、ボタンの名前が部分一致だと、名前が似た別のボタンに一致する。`getBy…` は見つからないと先に失敗するので `toBeTruthy` は確認にならず、`queryBy…` の `null` は `toBeDefined` を通るので、どちらも確認として弱いため。HTML の属性や要素の並びへの依存は、見た目に影響しない変更でテストを壊すため。

### 4.2 API の差し替えは型付きの MSW

管理画面の結合テストと、店舗画面のサーバーの処理（サーバーアクション、API の接続）とページのテストは、`src/test/` の `http`（openapi-msw）で OpenAPI の型に合ったハンドラーを作る。店舗画面は `frontend/store/src/test/server.ts` の `http` を使う（店舗画面に結合テストは置かない。第 1.1 節）。既定のハンドラーは置かず、テストごとに `server.use(...)` で渡す。

契約にない応答（通信の失敗、JSON でない本文）だけは、型のない msw のハンドラーで作る。複数のテストで使うものは `src/test/server.ts` の補助にし（例：`networkError`）、契約にない応答なので型付きの `http` を使わない理由をコメントに書く。この場合も、第 4.6 節の例外（ライブラリの型が合わない場合）には当たらず、型を回避する変換（`as never` など）は使わない。

理由：契約にない応答をテストで作らないため。契約にない応答を作る場所を限り、理由をコメントに残して、契約の応答と区別できるようにするため。

### 4.3 E2E のデータに実行ごとの印

E2E で作るデータの品番・名称に、実行ごとの印（`E2E-<印>-…`）を付ける（`e2e/src/data.ts`）。

理由：前の実行や手動で作ったデータと混ざらないようにするため。

### 4.4 テストの関数は `test` にそろえる

フロントのテストの関数は `test` を使い、`it` を使わない。`describe` でまとめてよい。

理由：テストの関数名を 1 つにそろえ、どのファイルでも同じ名前で検索できるようにするため。

### 4.5 複数のテストで使うものは、テストの補助に置く

複数のテストで使うデータ、API の差し替え、操作、確認は、各画面のテストの補助の置き場所に置き、テストのファイルで再定義しない。

| 画面 | 補助の置き場所 |
|------|----------------|
| 管理画面 | `frontend/admin/src/test/` |
| 店舗画面 | `frontend/store/src/test/` |
| E2E と撮影（`e2e/tests/`、`e2e/showcase/`） | `e2e/src/` |

- 補助のファイルは、データ、API の差し替え、操作のように、役割ごとに分ける。
- 共有パッケージ（`frontend/shared`）には補助を置かない。

理由：同じデータと手順の修正を 1 か所で済ませ、テストごとに食い違わないようにするため。

### 4.6 型を回避する変換を使わない

テストは型を回避する変換（`as never`、`as unknown as`）を使わない。型に合う値を補助で作る。ライブラリの型が合わず避けられないときだけ、理由をコメントに書いて使う。

理由：型を回避する変換で、第 4.2 節が禁じる契約にない応答を、型付きの `http` を通さずに作れてしまうため。

### 4.7 要素のプロパティ・属性・状態を jest-dom で確認する

要素のプロパティや属性（`.disabled`、`getAttribute(…)`、`.value`）を `toBe` で比べない。jest-dom の `toBeDisabled`、`toHaveAttribute`、`toHaveValue` などで確認する。要素の状態（表示、選択、フォーカスなど）も、`toBeVisible`、`toBeChecked`、`toHaveFocus` などで確認する。

理由：失敗したときの表示が分かりやすく、支援技術から見える状態で確認するため。

### 4.8 `fireEvent` を直接使わない

テストのファイルで `fireEvent` を直接使わず、`userEvent` で操作する。`userEvent` で起こせない出来事（入力できない種類の入力欄への値の設定、ブラウザーが起こす出来事など）だけは、理由をコメントに書いて使ってよい。理由は呼び出しごとに、同じ行のコメントか、コメントだけの直前の行に書く。

理由：`fireEvent` はイベントを 1 つ発火するだけで、利用者の操作で起きる一連の出来事（フォーカス、キー入力など）を再現せず、実際の操作では起きない状態でもテストが成功することがあるため。

### 4.9 環境変数は `vi.stubEnv` で変える

テストで環境変数を変えるときは `vi.stubEnv` を使い、`process.env` に代入しない。

理由：`process.env` への代入は、テストが終わっても残り、他のテストの結果に影響するため。`vi.stubEnv` は、後始末で元の値に戻せる。

### 4.10 組み込みの名前をシャドーイングしない

フロントのテストとテストの補助、E2E（`e2e/` の下の全ての TypeScript）の定数・変数（`const`・`let`）の名前は、TypeScript の lib（ES と DOM）が宣言するグローバルの名前（`URL`、`screen`、`location`、`name`、`status` など）と同じにしない。関数の引数は対象外。

理由：変数の宣言を忘れたときに、エラーにならずにグローバルの値が使われ、意図しない値でテストが通るため。

### 4.11 テストとテストの間に空行を置く

テストとテストの間、`describe` とその前後のテストの間には、空行を置く（テストを閉じる行（`})` と、`test('a', () => {})` のように 1 行で閉じるテスト）の直後の行に、`test(`・`it(`・`describe(`（`test.each(`・`test.skip(` などの形を含む）を続けない）。`test.beforeEach(`・`test.use(`・`test.step(` などは、テスト・グループでないので対象にしない。

理由：テストの境目を、一目で分かるようにするため。

### 4.12 入力のキーの間で待たない

管理画面の `renderApp` が返す `user` は、入力のキーの間で待たない（`userEvent.setup({ delay: null })`）。キーの間の非同期の処理（キーごとの検索など）を確認するテストだけは、既定の delay の `userEvent.setup()` を使う。`renderApp` を使わないテストの `userEvent.setup()` は対象外（入力が少なく遅くならないため）。

理由：既定では 1 文字ごとにタイマーで待ち、入力の多いテストが遅くなって、タイムアウトで失敗しやすくなるため。

## 5. 待ち方

時間で待たない（`Thread.sleep`、`waitForTimeout`）。条件で待つ（Playwright の `expect(...).toBeVisible()` などの自動の再試行、状態を直接確認する）。

- 決まった時間だけ見て「起きないこと」を確認しない（Awaitility の `during`）。バックエンドでは、`AsyncEvents` でイベントのリスナーが終わる（または失敗する）のを待ってから、起きた数を確認する。
- E2E は、通信がなくなるまで待つ `networkidle` で待たない。画面の状態（要素が見えるなど）を条件にして待つ。
- タイムアウトは、バックエンドは `AsyncEvents.TIMEOUT`、E2E は `e2e/src/waits.ts` の定数を使う。E2E は、定数を定義する `waits.ts` の外で、`timeout` などのタイムアウトを数字のリテラル（`10_000`、`5 * 1000` など）で書かない。長く待つ必要がある場合は、`waits.ts` に理由を書いた定数を加えて使う。
- E2E で裏の処理の結果を画面を再度開きながら待つときは、`e2e/src/waits.ts` の `reopenUntil` を使う。
- 同じイベントを連続して発行するテストは、前の発行の処理が終わるのを待ってから次を発行する。Spring Modulith は処理中の発行を（イベント、リスナー）の組で持つので、同じイベントの処理が重なると発行の記録が完了にならないことがある。

理由：処理が遅い環境では失敗し、速い環境では待ち時間が無駄になるため。決まった時間だけ見ると、遅い環境では、処理が終わる前に確認が終わり、誤って通るため。
