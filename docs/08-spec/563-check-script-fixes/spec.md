# Spec 563：既存の検査の script と構成の検査のテストの、漏れと誤検出を修正する

種類：整理　規模：M　ステータス：確定

## 背景と目的

既存の CI の検査の script、構成の検査のテスト（バックエンドの ArchUnit のテスト）、契約の読み込みに、検出の漏れと誤検出がある。今のコードと文書に該当する違反はないが、書き方によって将来の違反を見逃す。消費者・運営者に見える振る舞いは変えない。

追跡の issue は #563。sub-issue は #235、#536、#237 で、全体の issue #544 の一部である。

#235 が名前を挙げた検査のうち 2 つは、issue の後に形が変わった。

- 記録を読む箇所の検査の script は削除され、検査はバックエンドの構成の検査のテストに移った（commit 64419862a）。#235 の項目は、このテストで扱う。
- 環境変数の既定値を照合する検査の script は削除された。既定値が意味を持つのは管理画面の認証の設定の 1 か所だけと判断し、照合をやめたため（commit 6d131b0fa）。#235 のこの検査の 2 つの項目は、修正する対象がない。

## スコープ

| 含める | 含めない |
|--------|----------|
| ローカルのポートの検査：`${変数:-ポート}` の形を読む（#536） | 環境変数の既定値の検査の 2 つの項目（ci.yml の行末のコメント、E2E の管理画面のクライアントの ID） |
| 文言のキーのコメントの検査：関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッド）を JSX の式と読まない（#536） | テストが jOOQ や SQL で発行の記録のテーブルを直接読む書き方 |
| spec の番号の検査：`#` の直後の番号と、数字の間の区切りの字（#235、#536） | 秘密の値の項目の名前のうち、secret・password・salt・token の判定 |
| spec の番号の検査：`libs.versions.toml` と `gradle.properties` の `#` のコメント、Gradle の Kotlin 設定ファイルの構文（#235） | |
| ロールと権限の対応表の検査：店舗の契約の最上位の `security`（#235） | バックエンドの店舗の契約の読み込みの、最上位の `security` の扱い |
| イベントの発行の記録を読む箇所の検査：記録を読む他の型と、補助のメソッドを経由した読み取り（#235） | |
| 構成の検査のテストの補助：入れ子のクラスを外側のクラスに対応付ける判定を 1 か所にまとめる（#237） | |
| 設定の値の型の検査：秘密の値の項目の名前の判定を狭める。利用者が書いた `final` の `toString` を宣言と見なす（#237） | |
| 契約の読み込み：`$ref` の Path Item と、YAML の重複したキー（#237） | |

決定：

- 判定の区分は、どの検査も誤りの防止とする。誤って書いたものを見つけ、意図して作った回避は対象にしない。
- 既存の検査を広げるだけで、新しい検査は作らない。検査の一覧（[CI の検査の script](../../06-手順書/ci-checks.md)）と、commit の前の hook の対象の表は変えない。
- イベントの発行の記録を読む操作は、次の表の型の、名前が find か count で始まる操作とする。記録の型を非同期のイベントの補助のクラスに渡すだけのクラスは対象にしない（今の規則のとおり）。
- 補助のメソッドを経由した間接の読み取りは、今の検査がクラスの単位で見るので、テストの中の補助のメソッドからの読み取りも検査の対象になる。違反の例のクラスに、補助のメソッドを経由する例を加えて確認する。
- バックエンドのテストの書き方（[バックエンドのテストの書き方](../../05-開発規則/test-writing-backend.md)）の、記録を読む操作の規則の文を、表の全ての型に合わせて書き換える。今は 1 つの型（`EventPublicationRegistry`）だけを挙げている。
- 入れ子のクラスを外側のクラスに対応付ける判定が、2 つのテストに重複している。テストの補助のクラスの 1 つのメソッドにまとめる。外側のクラスの名前を返すメソッドは、補助のクラスに既にある。
- ローカルのポートの検査は、`${変数:-ポート}` の形を、変数の名前が PORT を含み、既定値が 4〜5 桁の数字のときだけ読む。`:-` のほかに `:=`・`-`・`=` の既定値の書き方と、引用符で囲んだ既定値も読む。理由：ポートでない既定値（時間、行数）を誤って読まないため。
- 文言のキーのコメントの検査は、関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッドの本体）の `{` を JSX の式と読まず、外側へ探し続ける。外側に JSX の式があれば、その式の中のコメントで通す（issue #536 の期待のとおり）。
- spec の番号の検査が数字の間の区切りと読む字は、`-` と、U+2010、U+2011、U+2013、U+2212 とする。`#` の直後は、全体が 16 進の字だけで 3・4・6・8 字の語だけを色として除く。
- spec の番号の検査は、`libs.versions.toml` を TOML の書き方（`#` のコメント、`"…"` と `'…'` の文字列）で、`gradle.properties` を properties の書き方（行頭の `#` と `!` だけがコメント）で読む。
- 契約の YAML の重複したキーは、起動を失敗にする。理由：後のキーが黙って前のキーを上書きし、操作を読み落とすため。
- 店舗の契約の最上位に `security` があれば、対応表の検査を失敗にする。失敗の文は「最上位に書かず、操作ごとに書く」とする。理由：バックエンドの店舗の契約の読み込みも、操作ごとの `security` だけを読み、ない操作で起動を失敗にするので、最上位の `security` は反映されず、読む人を誤らせるため。
- パスの下に `$ref` があれば、起動を失敗にし、対応表の検査も失敗にする。issue の本文の「読む」とは違う。理由：今の契約は `$ref` の Path Item を使っておらず、書いたときに気づける方が、バックエンドと検査の両方で同じ扱いにしやすいため。
- 秘密の値の項目の名前は、名前を語に分けて判定する。理由：今の判定（key で終わる、credential を含む）は、`cacheKey`、`credentialsFile` のような秘密でない項目にも該当するため。
  - 語の分け方：区切りは、小文字から大文字への変わり目と、大文字の並びの終わり（`stripeAPIKey` は stripe・API・Key）。最後に付く数字と、`V2` のようなバージョンの語は除いてから判定する。
  - credential：いずれかの語が credential か credentials で、最後の語が file・path・dir でないときに秘密とする。
  - key：最後の語が key で、名前が key だけか、直前の語が access・api・encryption・hmac・master・private・secret・signing のどれかのときに秘密とする。名前の全体が小文字で、これらの語と key をつないだ形（`apikey` など）も秘密とする。
- 上の 3 つは、issue 担当のセッションが利用者の代わりに確認して決めた。
- ポートの変数の名前、区切りの字、TOML と properties の読み方、秘密の値の語の分け方は、T563-01-01 の例の結果から決めた。issue 担当のセッションが確認した Open questions の決定の範囲を、例に合わせて詳しくしたものである。
- CI の検査の script のテストと、バックエンドの単体テスト（`./gradlew check`）で確認する。各タスクの確認方法に書く。

イベントの発行の記録を読む操作の型を、次の表に示す。再送の操作だけを持ち、記録を読む操作のない 2 つの型（`IncompleteEventPublications`、`FailedEventPublications`）は対象に入れない（Spring Modulith 2.1.2 で確認）。

| 型 | 役割 | 対象 |
|----|------|------|
| `EventPublicationRegistry` | 記録を読む | 今の対象 |
| `EventPublicationRepository` | 記録を読む | 加える |
| `CompletedEventPublications` | 完了した記録を読む | 加える |
| `EventPublicationRecords` | 本番のコードの設定クラス。未完了と停止の件数を数える | 加える |

検出すべき例と検出してはいけない例を、検査ごとの表に示す。最初の `[テスト]` の前に、spec の文脈を持たないレビュー役に表を読ませ、例を追加させる（[文書の構成](../../05-開発規則/doc-structure.md)の「形を判定する検査」）。

ローカルのポートの検査の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | 一覧にないポートを `PORT="${PORT:-9999}"` と書いたシェルの script | 検出する |
| 2 | 一覧にないポートを `${PORT:-9999}` の形で書いた文書の行 | 検出する |
| 3 | 一覧にあるポートを `${PORT:-8180}` と書いた行 | 検出しない |
| 4 | compose の `${MAILPIT_SMTP_PORT:-2525}`（compose の読み方で読む。今のとおり） | 検出しない |
| 5 | `TAIL_LINES="${TAIL_LINES:-30}"`（ポートでない 2 桁の既定値） | 検出しない |
| 6 | `WAIT_MS="${WAIT_MS:-5000}"`（名前に PORT を含まない変数の 4 桁の既定値） | 検出しない |
| 7 | 一覧にないポートを `PORT="${PORT:=9999}"` と書いた行 | 検出する |
| 8 | 一覧にないポートを `PORT="${PORT-9999}"` と書いた行 | 検出する |
| 9 | 一覧にないポートを `PORT="${PORT:-"9999"}"` と書いた行 | 検出する |
| 10 | `${LOCAL_BIND_ADDRESS:-127.0.0.1}`（既定値が IP アドレス） | 検出しない |
| 11 | 検査の対象の一覧にない script（`scripts/dev/e2e.sh` など）の `PORT="${PORT:-9999}"` | 検出しない（今のとおり） |

文言のキーのコメントの検査の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | `const labels = () => {` の本体で、`t('cart.title')` の行にコメントがなく、同じ本体の別の行にコメントがある | 検出する |
| 2 | `function f(): Promise<string> {` の本体で、1 と同じ書き方 | 検出する |
| 3 | `{items.map((item) => {` … `})` の後に `/* 表示 */}` がある JSX の式（式の終わりのコメント） | 検出しない |
| 4 | JSX の要素の中の `{t('k')}` の直後のコメント（今のとおり） | 検出しない |
| 5 | `onClick={() => {` の本体で、`toast(t('order.cancelled'))` の行にコメントがなく、同じ属性の式の別の行にある | 検出しない（外側の JSX の式へ探し続ける。3 と同じ） |
| 6 | `{orders.items.map((order) => {` の本体の `t('status')` の行にコメントがなく、同じ式の後の行に `{deadline /* 支払の期限 */}` がある | 検出しない（5 と同じ） |
| 7 | `function f(price: Price): { inclTax: string } {` の本体（戻り値の型がオブジェクトの型）で、1 と同じ書き方 | 検出する |
| 8 | `): Promise<{ cartId: string }> {` と `): Promise<T \| Extract<R, { ok: false }>> {` の本体で、1 と同じ書き方 | 検出する |
| 9 | `class ErrorFallback extends Component<Props> {` のメソッド `render() {` の本体で、1 と同じ書き方 | 検出する |
| 10 | `<p className="text-sm">{` の後の行に `t('k')`、その後の行に `/* 表示 */}`（要素の直後の、複数行の式の終わりのコメント） | 検出しない |

spec の番号の検査の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | `#AC-12`、`# 理由 #BR-3`、`#T7-01-01`、`#spec 7`、全角の `＃ＡＣ－１０` | 検出する |
| 2 | `AC−12`（U+2212 の区切り） | 検出する |
| 3 | `libs.versions.toml` の `# AC-12 のため` のコメント、`gradle.properties` の `# AC-12` のコメント | 検出する |
| 4 | Gradle の Kotlin の設定の、入れ子のコメント `/* 外 /* 内 */ AC-12 */` の後半 | 検出する |
| 5 | `#DC2626`、`#AC1234`、`#ACE`（16 進の色） | 検出しない |
| 6 | Gradle の Kotlin の設定の、raw 文字列 `"""AC-12"""` の中（文字列なので見ない） | 検出しない |
| 7 | `libs.versions.toml` の文字列の値の中の `#`（例：`"a#AC-1"`。コメントでない） | 検出しない |
| 8 | `AC‐12`（U+2010）、`AC‑12`（U+2011）、`AC–12`（U+2013） | 検出する |
| 9 | `#BR12`、`#DS1234`、`#DN3`、`#T123` | 検出する |
| 10 | `#DC26`（4 桁の色）、`#DC262680`（8 桁の色） | 検出しない |
| 11 | `libs.versions.toml` の行末のコメント `version = "1.0" # AC-12` | 検出する |
| 12 | `libs.versions.toml` のリテラル文字列 `name = 'a#AC-1'` | 検出しない |
| 13 | `gradle.properties` の `!` のコメント `! AC-12` | 検出する |
| 14 | `gradle.properties` の値の途中の `#`（`org.gradle.jvmargs=-Xmx2g #AC-12`） | 検出しない（properties では値の一部） |
| 15 | Gradle の Kotlin の設定の、文字列の埋め込みの中の引用符 `"${dir.replace("/", "//")}/T123"` | 検出しない |
| 16 | raw 文字列の中の引用符 `"""a "b" AC-12"""` | 検出しない |

ロールと権限の対応表の検査の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | 店舗の契約の最上位の `security` | 検出する |
| 2 | 管理の契約の最上位の `security`（今のとおり） | 検出しない |
| 3 | 店舗の契約の最上位の `security: []` | 検出する |
| 4 | 店舗の契約の最上位の、値のない `security:` | 検出する |
| 5 | 店舗の契約のパスの下の `$ref: './paths/carts.yaml'` | 検出する |
| 6 | 管理の契約のパスの下の `$ref` | 検出する |
| 7 | パスの共通の `parameters:` の下の `- $ref: '#/components/parameters/CartId'` | 検出しない |
| 8 | 操作の応答の `$ref: '#/components/responses/Created'` | 検出しない |

イベントの発行の記録を読む箇所の検査の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | 補助の外のテストの、`EventPublicationRepository` の find の呼び出し、`CompletedEventPublications` の findAll のメソッド参照、`EventPublicationRecords` の count の呼び出し | 検出する |
| 2 | 補助の外のテストの、private の補助のメソッドの中の、記録を読む呼び出し | 検出する |
| 3 | 補助の外のテストが、記録の型を注入して補助やジョブに渡すだけの書き方 | 検出しない |
| 4 | 補助（`AsyncEvents`、`EventPublications`）の中の読み取り | 検出しない |
| 5 | 補助の外のテストの、ラムダの中の `repository.findIncompletePublications()` | 検出する |
| 6 | 補助の外のテストの、`@Nested` のクラスの中の `completedPublications.findAll()` | 検出する |
| 7 | 補助の外のテストが、表の型の実装の型（`JdbcEventPublicationRepository` など）で注入して find を呼ぶ | 検出する |
| 8 | `registry.markStalePublicationsFailed(staleness)`（名前が find・count で始まらない） | 検出しない |
| 9 | 本番のコードの `publicationRecords.countIncomplete()` | 検出しない（対象はテストのコード） |
| 10 | 表にない型の `findAll()`（`taxCategoryQueryRepository.findAll()` など） | 検出しない |
| 11 | モックの準備 `when(publicationRecords.countIncomplete()).thenReturn(3)` | 検出する（読む操作の呼び出しと区別しない。今のリポジトリに該当はない） |

秘密の値の項目の名前の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | `secretKey`、`apiKey`、`privateKey`、`signingKey`、`key`、`credentials`、`clientCredential`、`secretAccessKey`、`webhookSecret`、`accessToken`、`apiPassword`、`salt`、`hmacKey`、`masterKey`、`stripeAPIKey`、`apikey`、`apiKeyV2`、`signingKey2`、`credentialsJson` | 秘密とする |
| 2 | `cacheKey`、`objectKey`、`idempotencyKey`、`credentialsFile`、`accessKeyId`、`publicKey`、`publishableKey`、`primaryKey`、`privateKeyPath`、`apiKeyHeader`、`signingKeyId` | 秘密としない |

`toString` の宣言の例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | record に利用者が書いた `public final String toString()` | 宣言と見なす |
| 2 | record のコンパイラが作る `toString` | 宣言と見なさない |
| 3 | record でないクラスに利用者が書いた `public final String toString()` | 宣言と見なす |
| 4 | record に利用者が書いた `public String toString()`（final なし） | 宣言と見なす（今のとおり） |
| 5 | record に、引数のある `public String toString(boolean masked)` だけを書く | 宣言と見なさない |

契約の読み込みの例：

| 番号 | 例 | 結果 |
|------|----|------|
| 1 | パスの下に同じキー（`get:` が 2 つ）がある | 起動を失敗にする |
| 2 | 同じパスが 2 つある | 起動を失敗にする |
| 3 | パスの下の `$ref` | 起動を失敗にする |
| 4 | 操作の中に `x-required-permission:` が 2 つある | 起動を失敗にする |
| 5 | `/carts/{cartId}:` と `'/carts/{cartId}':`（引用符だけが違う同じパス） | 起動を失敗にする |
| 6 | 最上位に `paths:` が 2 つある | 起動を失敗にする |
| 7 | `components.schemas` の下に同じ名前のスキーマが 2 つある | 起動を失敗にする（重複したキーは契約の全体で禁じる） |
| 8 | パスの共通の `parameters:` の下の `- $ref: '#/components/parameters/CartId'` | 起動を失敗にしない |
| 9 | 操作の応答の `$ref: '#/components/responses/Created'` | 起動を失敗にしない |

## 目的と完了条件

### DS-563-1 CI の検査の script が、書き方によって見逃していた違反を見つける（D / P1）

- **DC-563-1** ローカルのポートの検査が、`${変数:-ポート}` の形のポートを読み、一覧にないときは失敗にする。
- **DC-563-2** 文言のキーのコメントの検査が、関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッドの本体）を JSX の式と読まず、外側へ探し続ける。
- **DC-563-3** spec の番号の検査が、`#` の直後の番号を見つけ、16 進の色は番号にしない。
- **DC-563-4** spec の番号の検査が、U+2010、U+2011、U+2013、U+2212 を `-` と同じ区切りとして読む。
- **DC-563-5** spec の番号の検査が、`libs.versions.toml` と `gradle.properties` の `#` のコメントを見る。
- **DC-563-6** spec の番号の検査が、Gradle の Kotlin の設定を、Kotlin の書き方（raw 文字列、入れ子のコメント）で読む。
- **DC-563-7** ロールと権限の対応表の検査が、店舗の契約の最上位の `security` を失敗にする。

### DS-563-2 構成の検査のテストが、記録を読む型と秘密の値の項目を正しく判定する（D / P1）

- **DC-563-8** イベントの発行の記録を読む箇所の検査が、記録を読む操作の型の表の 4 つの型の読む操作を、補助の外で検出する。補助のメソッドを経由した読み取りも検出する。
- **DC-563-9** バックエンドのテストの書き方の、記録を読む操作の規則の文が、検査の対象の型と一致する。
- **DC-563-10** 入れ子のクラスを外側のクラスに対応付ける判定が、テストの補助の 1 か所にあり、構成の検査のテストはそれを使う。
- **DC-563-11** 秘密の値の項目の名前の判定が「決定」のとおりで、「秘密としない」の例に該当しない。
- **DC-563-12** 利用者が書いた `final` の `toString` を、宣言と見なす。record のコンパイラが作る `toString` は、宣言と見なさない。

### DS-563-3 契約の読み込みが、操作を黙って読み落とさない（D / P1）

- **DC-563-13** 契約の YAML に重複したキーがあれば、起動を失敗にする。
- **DC-563-14** パスの下に `$ref` があれば、起動を失敗にする。

### DS-563-4 例の表をテストで確認し、既存の検査が通る（D / P1）

- **DC-563-15** 「検出すべき例」の全てで、テストの失敗（または起動の失敗）を確認する。「検出してはいけない例」の全てで、テストの成功を確認する。
- **DC-563-16** 変えた検査の script の先頭のコメント（判定の範囲、失敗したとき）が、変えた後の動きに合う。
- **DC-563-17** `scripts/ci/check-all.sh` と `./gradlew check` が成功する。今のコードと文書に、新しい違反が出ない。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 検査の script | `scripts/ci/config/local-ports.mjs`、`scripts/ci/config/local-ports.test.mjs`、`scripts/ci/frontend/message-comments.mjs`、`scripts/ci/frontend/message-comments.test.mjs`、`scripts/ci/spec/spec-numbers.mjs`、`scripts/ci/spec/spec-numbers.test.mjs`、`scripts/ci/config/role-permissions.mjs`、`scripts/ci/config/role-permissions.test.mjs` |
| コメントの読み方の共通のモジュール（Kotlin の読み方を加えるとき） | `scripts/shared/source/strip-comments.mjs`、`scripts/ci/lib/source/comment-text.mjs` とそのテスト |
| 構成の検査のテスト | `backend/ecsite/src/test/java/com/ecsite/architecture/EventPublicationReadTest.java`、`SchemaOwnershipTest.java`、`TokenAccessTest.java`、`ConfigurationPropertiesTest.java`、`backend/ecsite/src/test/java/com/ecsite/support/ProductionClasses.java`、違反の例のクラス（`backend/ecsite/src/test/java/com/ecsite/architecture/sample/` の下） |
| 契約の読み込み | `backend/ecsite/src/main/java/com/ecsite/config/security/OpenApiOperations.java`、`backend/ecsite/src/test/java/com/ecsite/config/security/OpenApiOperationsTest.java` |
| 規則 | `docs/05-開発規則/test-writing-backend.md` |
| spec | `docs/08-spec/563-check-script-fixes/spec.md` |

## 非機能要件

- **DN-563-1** 消費者・運営者に見える振る舞いを変えない。今の店舗と管理の契約で、起動と認可の結果が変わらない。
- **DN-563-2** 検査を広げても、今のコードと文書を変えずに通る。通らない所が見つかったら、修正の前に issue 担当のセッションに知らせる。
- **DN-563-3** アーカイブした spec を変えない。

## 依存

| 対象 | 関係 |
|------|------|
| #564（フロントの規則の検査） | フロントの規則の検査の script（`scripts/ci/frontend/frontend-rules.mjs`）は #564 が変える。この spec は変えない。同じフォルダーの文言のキーのコメントの検査だけを変える |
| #565（workflow と追跡のファイルの検査） | 検査の一覧と commit の前の hook の表を変える。この spec は両方を変えないので、重ならない |
| #566（文書の検査の script） | `scripts/ci/docs/` を変える。この spec は変えないので、重ならない |
| #567（JDK を 25 に上げる） | 全体のブランチに入ると Gradle の JDK が変わる。Gradle の検査の前に、全体のブランチを取り込む |
| バックエンドの線（#553、#476、#555 など） | 構成の検査のテストを変えることがある。同じファイルを変えたら、この spec の変更に関わる行だけを変え、マージの前に全体のブランチを取り込む |

## Non-goals

| 項目 | 理由 |
|------|------|
| 環境変数の既定値の検査の 2 つの項目（ci.yml の行末のコメント、E2E の管理画面のクライアントの ID） | 検査の script が削除され、照合をやめたため（commit 6d131b0fa） |
| テストが jOOQ や SQL で発行の記録のテーブルを直接読む書き方 | issue の範囲外で、規則の対象（記録を読む操作）でもないため |
| secret・password・salt・token を含む名前の判定 | issue が狭めるのは key と credential だけで、今の判定で誤りの例が見つかっていないため |
| バックエンドの店舗の契約の読み込みの、最上位の `security` の扱い | 今も操作ごとの `security` だけを読み、ない操作で起動を失敗にするので、修正する点がないため |
| 対応表の検査が読む HTTP メソッドが、バックエンドの契約の読み込みより少ない（head・options・trace を読まない）こと | 今の契約はこれらの操作を使っておらず、issue の範囲外のため。issue 担当のセッションに知らせる |
| 変数の名前だけが違う同じ形のパス（`/carts/{cartId}` と `/carts/{id}`）を、契約の読み込みで失敗にすること | YAML の重複したキーではなく、issue の範囲外のため。issue 担当のセッションに知らせる |
| モックの準備の呼び出しを、記録を読む操作と区別すること | 今のリポジトリに該当がなく、呼び出しの形だけでは区別できないため |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T563-01-01** 検出すべき例と検出してはいけない例の表を、spec の文脈を持たないレビュー役に読ませ、例を追加する。
  - 依存：なし
  - 範囲：`docs/08-spec/563-check-script-fixes/spec.md`
  - 確認方法：追加した例の採否を結果に書く
  - 結果：候補 54 件のうち 52 件を例の表に加え、採らなかった候補はない。Non-goals に回したのは 3 件で、うちモックの準備の呼び出しは、例の表にも加えた。決定を詳しくした点は、ポートの変数の名前、区切りの字、TOML と properties の読み方、秘密の値の語の分け方。文言のキーのコメントの 2 件は、候補の「検出する」を issue の期待に合わせて「検出しない」にした。
- [x] **T563-01-02** [テスト] ローカルのポートの検査と、文言のキーのコメントの検査のテストを書き、レッドを確認する（DC-563-1、DC-563-2、DC-563-15）。
  - 依存：T563-01-01
  - 範囲：`scripts/ci/config/local-ports.test.mjs`、`scripts/ci/frontend/message-comments.test.mjs`
  - 確認方法：`node --test` が失敗する。commit は次のタスクと同じにする（[開発の進め方](../../05-開発規則/workflow.md)の「循環」）
- [x] **T563-01-03** T563-01-02 のテストが通る実装を書き、先頭のコメントを修正する（DC-563-1、DC-563-2、DC-563-16）。
  - 依存：T563-01-02
  - 範囲：`scripts/ci/config/local-ports.mjs`、`scripts/ci/frontend/message-comments.mjs`
  - 確認方法：`node --test`、`node scripts/ci/config/local-ports.mjs`、`node scripts/ci/frontend/message-comments.mjs`
- [x] **T563-01-04** [テスト] spec の番号の検査のテストを書き、レッドを確認する（DC-563-3〜6、DC-563-15）。
  - 依存：T563-01-01
  - 範囲：`scripts/ci/spec/spec-numbers.test.mjs`
  - 確認方法：`node --test` が失敗する。commit は次のタスクと同じにする
- [x] **T563-01-05** T563-01-04 のテストが通る実装を書き、先頭のコメントを修正する（DC-563-3〜6、DC-563-16）。
  - 依存：T563-01-04
  - 範囲：`scripts/ci/spec/spec-numbers.mjs`、`scripts/shared/source/strip-comments.mjs`、`scripts/ci/lib/source/comment-text.mjs` とそのテスト
  - 確認方法：`node --test`、`node scripts/ci/spec/spec-numbers.mjs`
- [x] **T563-01-06** [テスト] ロールと権限の対応表の検査のテストを書き、レッドを確認する（DC-563-7、DC-563-14 の検査の側、DC-563-15）。
  - 依存：T563-01-01
  - 範囲：`scripts/ci/config/role-permissions.test.mjs`
  - 確認方法：`node --test` が失敗する。commit は次のタスクと同じにする
- [x] **T563-01-07** T563-01-06 のテストが通る実装を書く（DC-563-7、DC-563-14、DC-563-16）。
  - 依存：T563-01-06
  - 範囲：`scripts/ci/config/role-permissions.mjs`
  - 確認方法：`node --test`、`node scripts/ci/config/role-permissions.mjs`
- [ ] **T563-01-08** [テスト] イベントの発行の記録を読む箇所の検査に、違反の例を加え、レッドを確認する。例は、4 つの型と、補助のメソッドの経由を含む（DC-563-8、DC-563-15）。
  - 依存：T563-01-01
  - 範囲：違反の例のクラスと、`backend/ecsite/src/test/java/com/ecsite/architecture/EventPublicationReadTest.java`
  - 確認方法：`./gradlew test --tests '*EventPublicationReadTest'` が失敗する。重い検査ではないが、Gradle を使う前に issue 担当のセッションに知らせる
- [ ] **T563-01-09** T563-01-08 のテストが通る実装を書き、バックエンドのテストの書き方の規則の文を修正する（DC-563-8、DC-563-9）。
  - 依存：T563-01-08
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/architecture/EventPublicationReadTest.java`、`docs/05-開発規則/test-writing-backend.md`
  - 確認方法：`./gradlew test --tests '*EventPublicationReadTest'` が通る。規則の文が検査の対象の型と一致することを読んで確認する
- [x] **T563-01-10** 入れ子のクラスを外側のクラスに対応付ける判定を、テストの補助にまとめる（DC-563-10）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/support/ProductionClasses.java`、`backend/ecsite/src/test/java/com/ecsite/architecture/SchemaOwnershipTest.java`、`backend/ecsite/src/test/java/com/ecsite/architecture/TokenAccessTest.java`
  - 確認方法：振る舞いを変えないので、`./gradlew test --tests '*SchemaOwnershipTest' --tests '*TokenAccessTest'` が通る
- [ ] **T563-01-11** [テスト] 秘密の値の項目の名前と `toString` の宣言の、テストと例を加え、レッドを確認する（DC-563-11、DC-563-12、DC-563-15）。
  - 依存：T563-01-01
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/architecture/ConfigurationPropertiesTest.java`、違反の例のクラス
  - 確認方法：`./gradlew test --tests '*ConfigurationPropertiesTest'` が失敗する。commit は次のタスクと同じにする
- [ ] **T563-01-12** T563-01-11 のテストが通る実装を書く（DC-563-11、DC-563-12）。
  - 依存：T563-01-11
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/architecture/ConfigurationPropertiesTest.java`
  - 確認方法：`./gradlew test --tests '*ConfigurationPropertiesTest'` が通る。record のコンパイラが作る `toString` と利用者が書いた `toString` を見分ける方法が見つからなければ、作業を止めて報告する
- [ ] **T563-01-13** [テスト] 契約の読み込みのテスト（重複したキー、`$ref`）を書き、レッドを確認する（DC-563-13、DC-563-14、DC-563-15）。
  - 依存：T563-01-01
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/config/security/OpenApiOperationsTest.java`
  - 確認方法：`./gradlew test --tests '*OpenApiOperationsTest'` が失敗する。commit は次のタスクと同じにする
- [ ] **T563-01-14** T563-01-13 のテストが通る実装を書く（DC-563-13、DC-563-14）。
  - 依存：T563-01-13
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/config/security/OpenApiOperations.java`
  - 確認方法：`./gradlew test --tests '*OpenApiOperationsTest'` が通る
- [ ] **T563-01-15** 段階の最後のレビュー（厚め）。指摘を修正し、`./gradlew check` と `scripts/ci/check-all.sh` を通す（DC-563-17）。
  - 依存：T563-01-03、T563-01-05、T563-01-07、T563-01-09、T563-01-10、T563-01-12、T563-01-14
  - 確認する項目：変えたコードを、[書き方の規則とレビュー](../../05-開発規則/review.md)と照合する
  - 結果：レビューごとに 1 行の表で書く
- [ ] **T563-01-16** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T563-01-15
- [ ] **T563-01-17** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T563-01-16
- [ ] **T563-01-18** spec をアーカイブに移す。
  - 依存：T563-01-17
