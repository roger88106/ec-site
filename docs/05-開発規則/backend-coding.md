# バックエンドの書き方

バックエンドのコードを書く・レビューするときに読む、今のコードで守っている書き方。構成の考え方は [バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)、API の規約は [API の規約](api-conventions.md)、確認方法が「レビュー」の規則の読み方は[書き方の規則とレビュー](review.md)にある。

## 1. 層とパッケージ

### 1.1 標準の構成

どのモジュールも、[バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)の「層とパッケージ」の構成と依存の表のとおりにし、不要な層は作らない（イベントを出さないなら `event/` は不要）。どの層も config に依存せず、`application/model/` は application の `port/` に依存しない。

- **参照と更新（レベル A）**：参照の service は `application/model/` の view を返し、集約を返さない（presentation は集約を受け取らない）。画面の操作の可否は、view を作る所で集約のメソッドを呼んで view に入れる。
- **外部の呼び出しの前の読み取り**：外部のサービスを呼ぶ前に集約の状態を読む処理（外部へのリクエストを作る）も参照の service に置き、リクエストの型か view を返す。呼んだ後の更新は更新の service で行う。
- **イベント**：レベル A の application はイベントを送り出さない（送り出し方はバックエンドのアーキテクチャの「モジュール間の連携」）。domain は自分のモジュールの `event/` を参照してよい。モジュールの中だけのイベントは、レベル A は集約のパッケージ、レベル B は application に置く。
- **公開の API**：`api/` は interface を持たない `@Component` のクラスで、application の service に委ね、view を `api/` の型に変換する。`api/` の型を `api/` の外が実装・継承せず、service は `api/` の型に依存しない。
- **domain の直下**：エラーの定義と、複数の集約が使う値（商品とカテゴリが使う表示順の再採番など）を置く。1 つの集約だけが使う値はその集約の `vo/` に置くが、集約のパッケージの型（列挙など）を使う値は集約のパッケージの直下に置く（例：order の `BuyerProfile`）。理由：`vo/` に置くと、`vo/` と集約のパッケージが循環するため。
- **認証の情報の取り出し**：`CurrentToken`、`CurrentMember`、`CurrentOperator`、`CurrentMemberIds` を使えるのは、presentation 層と `common.security` だけにする。application 以降へは取り出した値（会員の ID など）を引数で渡し、Spring Security の型を公開しない。例外は次の表で、増やすときは表に理由を書く。

| 呼び出し元 | 置き場所 | 例外の理由 |
|------------|----------|------------|
| `CurrentMemberIds` | `member/api/` | 他のモジュールの presentation 層に会員の ID を渡す公開の API。呼び出し元は presentation 層に限る ※1 |
| `MeController` | `config/security/` | モジュールに属さず、役割が presentation 層と同じエンドポイント（「生成した interface を実装する」） |

- **jOOQ の置き場所**：jOOQ（`org.jooq` と、生成したコード `com.ecsite.jooq`）を使えるのは、モジュールの `infrastructure/persistence/` だけにする。モジュールの外の共通の設定（`config/event/EventPublicationRecords`）は `SchemaOwnershipTest` が見る。例外は次の表で、増やすときは表に理由を書く。

| クラス | 置き場所 | 例外の理由 |
|--------|----------|------------|
| `MockShipmentQuery` | `shipping/infrastructure/mock/` | mock の物流の画面だけが使う問い合わせ。`persistence/` に置くと、本番で使うクラスと区別しにくくなる |

※1：会員の ID を読むだけのために、腐敗防止層の port と実装の組をモジュールごとに作らないため。presentation 層に限るのは、他のモジュールの `api/` の規則だけでは、application 層が directory を経てリクエストの会員 ID を得る形を防げないため。

理由：どのモジュールでも同じ場所に同じものを見つけられ、集約と永続化の形を変えても presentation と公開の API が変わらないようにするため。

確認方法：

- 層の間の依存：依存の表の組と表にない依存、`api/` の interface と外からの実装・継承、domain のパッケージの循環は自動（ArchUnit）。presentation が依存する domain をエラーの定義と値の型に限ることはレビュー（集約に依存しないことは次の行で自動）
- 参照の service が返す型：presentation が集約に依存しないことは自動（ArchUnit）、外部の呼び出しの前の読み取りの置き場所はレビュー
- 公開の API：`api/` のクラスが service に委ねることはレビュー、application が `api` に依存しないことは自動（ArchUnit）
- イベントの置き場所と送り出し：レベル A の application が送り出さないことは自動（ArchUnit）、置き場所はレビュー
- application の `port/` と共通コード・設定への依存、認証の情報の取り出し、jOOQ の置き場所：自動（ArchUnit）

### 1.2 モジュールの外に出すのは `api/` と `event/` だけ

他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。

理由：モジュールの中を、他のモジュールに影響なく変えられるようにするため。

確認方法：自動（ModulithTest）

### 1.3 他のモジュールは腐敗防止層で包む

- 他のモジュールの `api/` は、port（`<相手>Directory` の interface と、相手の情報を変換して持つ自分の型）を通して使い、相手の型を内部で使わない。port は使う層の `port/` に、実装（`<相手>ApiDirectory`。public にしない）は `infrastructure/directory/` に置く。
- 他のモジュールの `api/` を使うのは `infrastructure/directory/` だけにする。例外は、presentation から使う `member.api.CurrentMemberIds`（「標準の構成」の表）。
- モジュールの直下に、クラスが 1 つだけのパッケージ（`<相手>dir` など）を作らない。
- 列挙を別の列挙に変換するとき（相手の型と自分の型、ドメインとイベント、ドメインと生成したモデル）は、`switch` 式で値ごとに書き、`valueOf(x.name())`・`fromValue(x.name())` を使わない。
- モジュールの間とレイヤーの間で、列挙を名前の文字列で受け渡さない（command・query・view・api の型は列挙で持つ）。名前の文字列から列挙にするのは、DB の文字列を読む `infrastructure/persistence/` と、ドメインの中だけにする。

理由：相手の変更の影響を実装の 1 か所に限り、列挙に値を追加したときの変換の書き忘れをコンパイルで気づけるようにするため。

確認方法：置き場所、`api` を使うのが `infrastructure/directory/` だけであること（許可の一覧の 1 件を除く）、実装が public でないことは自動（ArchUnit）。変換の書き忘れは自動（コンパイル）、値の対応は変換ごとの単体テスト。名前の文字列からの `valueOf`・`fromValue` は自動（`scripts/ci/java-rules.mjs`）。port と実装の分け方はレビュー

### 1.4 パッケージの大きさ

1 つのパッケージの直下の `.java` は 10 を目安、20 を上限にする（`package-info.java`、テスト、生成したコード（OpenAPI と jOOQ）は数えない）。目安を超えて分けないときは、下の表に理由を書く。

| パッケージ | 分けない理由 |
|------------|--------------|
| `catalog.infrastructure.persistence` | jOOQ の Repository と、それだけが使う package-private のクラス。分けるとクラスを公開しなければならない |
| `config.security` | 認証と認可の設定。組み立てられるクラスは package-private で、分けるとクラスを公開しなければならない |

`ModuleStructureTest` が表を読むので、表の書式を変えない。1 列目は、バッククォートで囲んだパッケージ名（`com.ecsite.` と `module.` を除く）にする。見出しの名前を変えるときは、`PACKAGE_SIZE_TITLE` も修正する。

確認方法：上限は自動（ArchUnit）。目安を超えるパッケージの一覧を「パッケージの大きさ」の表と照合することは自動（`ModuleStructureTest`）。表の「分けない理由」の妥当性はレビュー

### 1.5 共通の型

- 共通コード（`common/`）には、2 つ以上のモジュールが使うものと、横断の仕組み（エラーの応答・ログ・入力の検証のメッセージ・認証の情報の取り出し）だけを置く。1 つのモジュールだけが使うものは、そのモジュールに置く。
- モジュールに共通の型は `common/model/` に置く（集約の基底は `base/`、値は `value/`）。共通の値にするのは、2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つものだけにする。
- 金額は、モジュールの内部と公開の型（`api/` の record、command、view）で `Money` を使う。整数で持つのは presentation、infrastructure（DB）、設定の値の型だけで、設定の値の金額は使う所で `Money` にする。
- 金額の掛け算・割り算は、`Money` の操作と税の計算（`TaxCalculator`）の中だけで行い、端数の扱い（切り捨てなど）をメソッドで明示する。例外は商品の一覧の税込価格の絞り込みと並べ替えで、`catalog.infrastructure.persistence.PriceInclTaxField` が SQL で計算する ※2。

※2：税区分ごとに税率が違って税抜と税込の順が一致せず、一覧は DB で `LIMIT`・`OFFSET` を使ってページングするため。`TaxCalculator` と同じ結果になることを、端数の境界値を含めて `PriceInclTaxFieldTest` で確認する。

理由：同じ規則（金額の端数の扱いなど）を 1 か所に置きつつ、共通の型を増やしすぎてモジュールが互いに依存しないようにするため。

確認方法：金額は自動（ArchUnit）、置き場所と基準、金額の計算はレビュー

## 2. API の実装

### 2.1 生成した interface を実装する

Controller は OpenAPI から生成した interface（`XxxApi`）を実装し、`@RequestMapping` を書かない。`@RestController` は presentation だけに付ける。例外は次の 2 つ。

- [API の規約](api-conventions.md)の「OpenAPI の外のエンドポイント」（決済・物流の通知、mock の画面）：そのサービスの型と一緒に `infrastructure` に置き、`@Controller` と `ResponseEntity` で書く（[決済の設計](../02-設計/payment.md)、[出荷の設計](../02-設計/shipping.md)）。
- ログインした人の情報を返す `MeController`：どのモジュールにも属さず、認可の設定と同じ権限の変換（`PermissionConverter`）を使うので、`config/security/` に置く。

理由：契約（`api/openapi/`）を正本にし、実装とずれないようにするため。

確認方法：`@RestController` の置き場所は自動（ArchUnit）、実装は自動（コンパイル）、契約にない endpoint はレビュー

### 2.2 応答への変換は手動で書く

- 変換はモジュールごとの mapper（`XxxMapper`）に手動で書き、変換のライブラリーは使わない。
- 生成したモデルは、引数なしのコンストラクターと項目名のメソッドで組み立て（`new OrderLine().productCode(...)`）、位置で並べるコンストラクターは使わない。項目が 1 つの型（作成した ID など）も同じ。
- 作成の応答（201、Location、作成した ID）は `common/presentation` の共通の補助で作る。`Location` の形は [API の規約](api-conventions.md)の「HTTP メソッド」。
- 入れ子の型（明細、金額、住所など）は型ごとに private メソッドに分け、上位のメソッドには項目の並びだけを残す。自分で定義した record は、引数が多ければ 1 行に 1 つ書く。

理由：項目の取り違えを防ぎ、変換を読みやすくするため。

確認方法：生成したモデルの組み立ては自動（ArchUnit）、他はレビュー

## 3. 入力の検証とエラー

### 3.1 形の検証は presentation、業務の規則は domain

- 長さ・必須・形式は OpenAPI の制約（生成した Bean Validation）で、業務の規則（重複、状態、期間の前後など）は domain で確認する。
- リクエストの数値の項目には範囲（`minimum` と `maximum` の両方）、文字の項目には長さ（`maxLength`）か形式（`pattern`・`format`・`enum`）を書く。上限を書かないのは計算に使わない項目（楽観ロックのバージョン、ページ番号、絞り込みの値など）だけで、契約のテストの一覧に理由を書く。理由：上限のない数値を計算に使うと、桁あふれや DB の範囲の超過で 500 になるため。
- 同じ項目の契約の制約、ドメインの上限の定数、DB の列の長さは一致させる。項目を追加したら、`OpenApiRequestConstraintTest` と `FieldConstraintContractTest` の表にも追加する。
- 文字の「空白でない」「長さを code point で数える」の判定は `BoundedText` で行い、各ドメインは上限とエラーの種類だけを持つ。

理由：契約に書ける検証は契約に、業務の規則は 1 か所に置くため。

確認方法：自動（契約のテスト）、判定の重複はレビュー

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- 業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum）で表し、`BusinessException.of(...)` で投げる。定数は種類（`ErrorType`）と、いつ起きるかの Javadoc を持つ。
- メッセージのキーは `ecsite.<モジュール>.<定数の名前の小文字>`、文は `messages.properties` に置く（例：`TOO_MANY_CART_ITEMS` → `ecsite.cart.too_many_cart_items`）。
- 文に上限などの値を入れるときは、数字を書かず名前で差し込み（`{max} 品目までです`）、値はドメインの定数から渡す。
- レスポンスへの変換は `GlobalExceptionHandler` だけが行う。

理由：フロントが `errorCode` で分岐でき、画面の日本語を 1 か所で管理し、規則の値が変わっても文が取り残されないようにするため。

確認方法：自動（メッセージのテスト）、文に数字がないことは自動（CI の検査 `message-numbers`）

### 3.3 入力の検証のメッセージ

入力の検証の文は、フレームワークの既定の文（JVM のロケールで言語が変わる）を使わず、`messages.properties` の `ecsite.validation.<種類>`（`notnull`、`size`、`min` など。対応のない種類は `ecsite.validation.invalid`）から取る。上限・下限は `{max}`、`{min}`、`{value}` で差し込む。

形の検証の誤りに対応する業務のエラーがある項目は、リクエストの本文の項目のパス（`shippingAddress.postalCode` など）ごとに、形の検証の文を業務のエラーのコードへ対応づけられる。対応があれば、そのコードの文と、ドメインの定数から差し込む値を使う。対応の表は、そのモジュールの presentation に `ValidationErrorMapping` を実装した bean として置く。モジュールの間で同じパスを対応づけると、起動で失敗する。

理由：どの環境でも同じ日本語を返し、形の検証と業務の検証のどちらで見つかっても同じ文を返すため。

確認方法：自動（検証のメッセージのテスト）

### 3.4 DB の制約の違反は業務のエラーに変えるか、重複を吸収する

DB の制約でしか守れない規則（一意性など）の違反は、リポジトリが次のどちらかで扱う。

| 重複のとき | 扱い方 |
|------------|--------|
| 業務のエラーにする（同じ品番の登録など） | `ConstraintViolationTranslator` で業務のエラーに変える。制約名とエラーの対応は、各リポジトリの `CONSTRAINTS` に置く |
| 吸収する（同じ通知の受け取り、同じメールの送信の記録など、冪等にする）か、同じトランザクションで処理を続ける | 重複を無視する挿入（`ON CONFLICT DO NOTHING`）にし、挿入したかを真偽値で返す。呼び出し元が真偽値で分ける |

理由：制約の違反を 500 にせず、違反でトランザクション全体を失敗させる PostgreSQL でも処理を続けられるようにするため。

確認方法：自動（リポジトリの統合テスト）、扱い方の選び方はレビュー

### 3.5 更新の競合は `version`

契約の形は [API の規約](api-conventions.md)の「HTTP メソッド」にある。同時に編集されうる集約の更新は、`version` が合わなければ `CONCURRENT_MODIFICATION` を投げる。

理由：他の利用者の変更を、通知なしに上書きしないため。

確認方法：自動（API の統合テスト）

## 4. トランザクションと時刻

### 4.1 トランザクションは application

- `@Transactional` は application の service のクラスに付け、参照だけなら `readOnly = true` にする。api と presentation には付けない。
- 例外は、別のトランザクションが必要な infrastructure のもの（呼び出し元と別に確定させる記録など）で、付けた所に理由を書く。
- 外部サービス（決済・画像の変換・ファイル保存・物流・メールなど）は、トランザクションの外で、タイムアウトを設定して呼ぶ（BR-40）。呼ぶ前の読み取りと呼んだ後の更新は、それぞれのトランザクションにする（「標準の構成」）。イベントのリスナーで呼ぶときは、リスナーをトランザクションなし（`@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)`）にする。
- 外で呼べないものは、呼ぶ所と設計書に理由を書く（出荷のメール：[通知モジュール](../03-機能仕様/notification.md)）。

理由：1 つの操作を 1 つのトランザクションにして境界を層で決め、外部サービスを待つ間に DB の接続を持って接続が尽きないようにするため。

確認方法：自動（ArchUnit）、infrastructure の例外の理由と外部サービスの呼び出しはレビュー

### 4.2 現在時刻は `Clock` から

現在時刻は注入した `Clock` から取り（`Instant.now()` を直接呼ばない）、domain には `Instant` を引数で渡す。統合テストは `TestClockConfig` で時計を固定する。

理由：テストで時刻を固定し、時刻で切り替わるデータを確認するため。

確認方法：自動（ArchUnit）

## 5. 命名と書き方

### 5.1 Lombok は main だけ

getter・コンストラクターの注入などの定型コードは、main では Lombok で書く。テストのソースセットには Lombok を入れていない。

理由：テストのクラスは、JUnit でコンストラクターの注入に設定が必要なのでフィールドの注入を使い、定型コードもほとんどないため。

確認方法：自動（`scripts/ci/java-rules.mjs`）

### 5.2 名前の形

| 対象 | 形 | 例 |
|------|----|----|
| リポジトリの port（domain） | `XxxRepository` | `OrderRepository` |
| 参照用のリポジトリの port（application） | `XxxQueryRepository` | `OrderQueryRepository` |
| jOOQ の実装 | `JooqXxxRepository` | `JooqOrderRepository` |
| service（レベル A） | 参照は `XxxQueryService`、更新は `XxxCommandService` か用途の名前 ※3 | `OrderQueryService`、`OrderPlacementService` |
| 腐敗防止層の port | `<相手>Directory`（使う層の `port/`） | `OrderDirectory` |
| その実装 | `<相手>ApiDirectory`（`infrastructure/directory/`） | `OrderApiDirectory` |
| イベント | 過去形 | `OrderShipped` |

※3：用途の名前を使うのは、1 つの集約に用途が複数あるときと、他のモジュールの呼び出しを組み合わせるとき。データを変えずに他のモジュールの結果を組み合わせる service（注文の手続きの見積もりなど）も用途の名前にし、読み取り専用のトランザクションにする。

別のモジュールに同じ単純名のクラスがあってよい（Spring の Bean の名前は完全修飾名にしている）。

確認方法：リポジトリと腐敗防止層は自動（ArchUnit）、service とイベントはレビュー

### 5.3 インスタンスを作らないクラス

static のメンバーだけの最上位のクラス（定数や補助の関数をまとめたもの）は `final` にし、コンストラクターを `private` にする（Lombok の `@NoArgsConstructor(access = AccessLevel.PRIVATE)` でも、手動でもよい）。enum・record・interface・注釈は対象でない。検査に Checkstyle の `HideUtilityClassConstructor` は使わない（ソースを読むので、Lombok で作るコンストラクタが見えずに誤検出される）。

理由：インスタンスを作っても使い道がなく、作れると使い方を誤るため。

確認方法：自動（ArchUnit）

### 5.4 書式

Java の整形は IntelliJ IDEA の Reformat Code を推奨し、強制しない（CI と commit の前の hook では検査しない）。既存のファイルの書式だけを修正する変更は、他の変更と同じ commit に混ぜない。理由：差分のレビューと `git blame` を読みやすく保つため。

import は次のグループの順に並べ、グループの間は空行 1 行で分け、グループの中は文字コードの順にする。使われていない import と、`*` の import（static を含む。例外は設けない）は書かない。

| 順 | グループ |
|----|----------|
| 1 | `java`・`javax` 以外 |
| 2 | `java`・`javax` |
| 3 | static |

- IntelliJ の Optimize Imports を上の表の並びにする設定と、エージェントが整形するときの注意は、[開発環境の起動](../06-手順書/setup.md)の「IDE の設定」にある。
- import は、全てのソースセットで Checkstyle（`backend/ecsite/config/checkstyle/checkstyle.xml`）が検査し、違反があれば `./gradlew check` と CI が失敗する。Checkstyle は import を修正しないので、使われていない import は削除し、順序の違反は並べ替え、`*` の import は個々の import に書き換える。

理由：今のコードは IntelliJ の書式でほぼそろい、Spotless で強制すると読みやすさが下がる変更が出たため（[issue #19 のコメント](https://github.com/roger88106/ec-site/issues/19)）。

確認方法：

- 整形：なし
- import：自動（Checkstyle）

## 6. 設定の値

### 6.1 設定の値の型

- 設定は `@ConfigurationProperties` の record で受け、`@Validated` を付ける。型は使う層に置く（画像の変換の設定は `infrastructure/converter/` など）。
- 接頭辞は `ecsite.<モジュール>`、まとまりを分けるときは `ecsite.<モジュール>.<範囲>`（`ecsite.media.storage` など）、モジュールでない共通の設定は `ecsite.<範囲>`（`ecsite.security` など）。
- 環境で変わる値（接続先・資格情報・ID・バケット名）は既定値を持たず必須にし（BR-31）、環境によらない値（上限、時間など）だけ既定値を持つ ※4。
- モジュールの `@Configuration` のクラスは `infrastructure/`（またはその下）に置き、`@EnableConfigurationProperties` は `@Configuration` のクラスに付ける。条件で切り替える実装（mock など）の設定は、同じ条件の設定のクラスに付ける。
- presentation は設定の値の型を受け取らず、必要な値は application の service が読んで view で返す（`api/` は対象外）。
- 定期の処理の間隔も設定の型の項目で受け、`@Scheduled` は SpEL で設定の型の Bean を参照する（`${...}` の既定値を注釈に書かない）。注釈が定数しか取らず、環境で変えない値（定期の処理のロックの上限など）は、設定にせず定数にし、理由をコメントに書く。
- 秘密の値（鍵、パスワード、署名の鍵、ソルト）を持つ設定の record は、`toString` を上書きして秘密の値を `***` にする（`StripeProperties` の形。項目の決め方は `ConfigurationPropertiesTest`）。理由：record の既定の `toString` は全ての項目を出し、ログから秘密の値が漏れるため。
- 設定の型では、Lombok の `@ToString`・`@Data`・`@Value` を使わず、`toString` を手動で書く。理由：Lombok の注釈はクラスファイルに残らないので、構成の検査が、値を出力する `toString` を見逃すため。

※4：本番では設定しない接続の値（S3 互換ストレージの接続先と資格情報。本番は AWS の既定と IAM ロール）は任意にし、本番で使う値（path style を使わない）を既定値にする。

理由：設定を忘れたまま、別の環境の値で動き出さないようにするため（足りなければ起動に失敗する）。

確認方法：

- 設定の値の型：自動（起動のテスト）。`@EnableConfigurationProperties` と `@Configuration` の置き場所、presentation の依存は自動（ArchUnit）。型の置き場所と接頭辞、`@Scheduled` の間隔の取り方はレビュー
- 秘密の値の `toString`：`toString` の宣言は自動（`ConfigurationPropertiesTest`）。値のマスクは単体テスト（`toString` に秘密の値が含まれないこと）。Lombok の注釈を使わないことはレビュー

## 7. 静的解析

### 7.1 null の扱い

- main の `com.ecsite` の下（生成したコードの `com.ecsite.jooq`、`com.ecsite.api` を除く）では、注釈のない引数・戻り値・フィールドを null でないとみなす（NullAway の設定は `backend/ecsite/build.gradle.kts`）。
- null を取りうる所には JSpecify の `org.jspecify.annotations.Nullable` を付け、他の `Nullable`（`jakarta.annotation`、`javax.annotation`、`org.springframework.lang`）は使わない。配列や修飾した型では型の直前に付け、迷ったら JSpecify の位置にする（`String @Nullable []` など）。
- 選び方：
  - null が正しい値（API の応答の省略できる項目、DB の null を取りうる列など）は `@Nullable` にし、それ以外は null を作らない書き方にする。
  - 値がないことを返す参照のメソッド（`findXxx`）は `Optional` を返し、集約の null を取りうるフィールドは `@Nullable` にする。
  - getter は、値の有無が状態に依存しないなら `Optional`（`Cart.memberId()`）、状態で決まるなら `@Nullable`（`Order.cancellation()`）を返す。後者は、呼び出し側が状態を確認してから `Objects.requireNonNull` で受ける。
- `@Nullable` の値を受ける `Objects.requireNonNull` のメッセージは、null でない根拠の状態を肯定の文で書く（「キャンセルした注文にはキャンセルの記録がある」）。メッセージのない呼び出し（`Operator` の生成など）は対象外。
- jOOQ のレコードの getter は NullAway が確認しないので、null を取りうる列の値は `@Nullable` のローカル変数・引数か `Optional` で受け、注釈のない引数に渡さない。
- `lombok.NonNull` は実行時の確認として使ってよい（外さない）。

理由：null の誤りを、Spring の API の注釈（同じ JSpecify）も含めて、実行より前のコンパイルで見つけるため。

確認方法：自動（NullAway、`scripts/ci/java-rules.mjs`）、`@Nullable` と `Optional` の選び方はレビュー

### 7.2 検査を抑えるとき

- Error Prone と NullAway の報告は修正する。次のものだけ、最も狭い範囲（宣言 1 つ）に `@SuppressWarnings("<検査の名前>")` を付ける。
  - 誤った報告（フレームワークが初期化するフィールドなど）
  - 修正すると振る舞いが変わるもの
  - mock（本番で動かないコード）で、修正する手間に見合わないもの
- 抑える理由は `@SuppressWarnings` の直前の行に `//` のコメントで書く。他の注釈が間にあるときは、その注釈の行を飛ばして直前の行に書く。
- 検査を全体で無効にする、重大度を変えるのは `backend/ecsite/build.gradle.kts`（今無効にしている検査と理由もここ）で行い、先に spec で決める。意味のない注釈を足さないと消えない報告は、注釈でなく設定で扱う。生成したコードは検査しない。

```java
// 本番で動かない mock。body は HTTP の本文として送るだけなので、配列の成分を許す
@SuppressWarnings("ArrayRecordComponent")
public record SignedNotification(byte[] body, String signature) {
}
```

理由：検査を抑える範囲と理由が見えれば、後で外せるかをレビューで判断できるため。

確認方法：`@SuppressWarnings` の理由のコメントは自動（`scripts/ci/java-rules.mjs`）

## 8. マイグレーション

### 8.1 前のリリースのアプリが動き続けられる変更だけにする

マイグレーションはデプロイごとに 1 回適用し、起動時には適用しない。適用してからアプリを入れ替えるまでは、前のバージョンのアプリが新しいスキーマで動く。そのため、前のリリースのアプリが動き続けられる変更だけにする。次の変更は 1 つのリリースでせず、下の 3 段に分ける（検出する変更の種類と、SQL の書き方ごとの扱いは `scripts/ci/migrations.mjs` の先頭のコメント）。

- 表・列の削除、改名、列の型の変更
- 既存の列を NOT NULL にすること、既定値のない NOT NULL の列の追加
- 既存の列の既定値の削除 ※5

| 段 | リリース | すること |
|----|----------|----------|
| 1 | 最初 | 新しい表・列を追加する（前のバージョンのアプリは使わない） |
| 2 | 次 | アプリを新しい表・列に切り替えてリリースする |
| 3 | その次 | 使わなくなった表・列を削除する ※6 |

※5：前のリリースのアプリは既定値を前提に列を指定せずに INSERT するので、NOT NULL の列では失敗し、NULL を許す列では値が NULL に変わるため。既定値の削除は段 1 がなく、段 2 でアプリが列に値を必ず書くようにし、段 3 で既定値を削除する。

※6：段 3 の削除（既定値の削除を含む）は、マイグレーションの検査の除外の一覧（`scripts/ci/migrations-exclusions.json`）に置き、理由に、どのリリースで使わなくなったかを書く。書き方は `scripts/ci/migrations.mjs` の先頭のコメント。

理由：前のバージョンのアプリが、新しいスキーマで失敗しないため。

確認方法：自動（CI の検査 `migrations`）。3 段に分ける手順の妥当性はレビュー
