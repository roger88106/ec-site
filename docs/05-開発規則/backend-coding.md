# バックエンドの書き方

今のコードで守っている書き方。構成の考え方は [バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)、API の規約は [API の規約](api-conventions.md)。確認方法が「レビュー」のものは、各 spec のレビューで、[書き方の規則とレビュー](review.md)に従って読む。

## 1. 層とパッケージ

### 1.1 標準の構成

どのモジュールも、[バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)の「層とパッケージ」の構成にする。不要な層は作らない（イベントを出さないなら `event/` は不要）。層の間の依存は、バックエンドのアーキテクチャの依存の表のとおりにする。どの層も、config に依存しない。`application/model/` は、application の `port/` に依存しない。

層の中の役割：

- **参照と更新（レベル A）**：参照の service は、`application/model/` の view を返し、集約を返さない。presentation は集約を受け取らない。画面の操作の可否は、view を作る所で集約のメソッドを呼んで view に入れる。
- **外部の呼び出しの前の読み取り**：更新の流れで、外部のサービスを呼ぶ前に集約の状態を読む処理（外部への要求を作る）も、参照の service に置き、要求の型か view を返す。外部の呼び出しをトランザクションの外で行い、呼んだ後の更新を更新の service で行う。
- **イベント**：レベル A の application は、イベントを送り出さない。送り出し方は [バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)の「モジュール間の連携」に従う。domain は自分のモジュールの `event/` を参照してよい（集約が公開のイベントを記録するため）。モジュールの中だけのイベントは、レベル A は集約のパッケージ、レベル B は application に置く。
- **公開の API**：`api/` は、interface を持たない `@Component` のクラスで、application の service に委ねる。`api/` の型を `api/` の外が実装・継承しない。service は `api/` の型に依存しない（`api/` のクラスが application の view を `api/` の型に変換する）。
- **domain の直下**：モジュールの domain の直下には、エラーの定義と、複数の集約が使う値を置く（商品とカテゴリが使う表示順の再採番など）。1 つの集約だけが使う値は、その集約の `vo/` に置く。ただし、値が集約のパッケージの型（列挙など）を使うときは、集約のパッケージの直下に置く。`vo/` に置くと、`vo/` と集約のパッケージが互いに依存して循環するためである（例：order の `BuyerProfile`、`Cancellation`）。

- **認証の情報の取り出し**：token の取り出し（`CurrentToken`）と、会員・運営者・会員 ID の取り出し（`CurrentMember`、`CurrentOperator`、`CurrentMemberIds`）を使えるのは、presentation 層と `common.security` だけにする。application 以降へは、取り出した値（会員の ID など）を引数で渡し、Spring Security の型を公開しない。例外は次の 2 つで、増やすときはこの表に理由を書く。

| 呼び出し元 | 置き場所 | 例外の理由 |
|------------|----------|------------|
| `CurrentMemberIds` | `member/api/` | 他のモジュールの presentation 層に会員の ID を渡す公開の API。呼び出し元は presentation 層に限る ※1 |
| `MeController` | `config/security/` | モジュールに属さないエンドポイントで、役割は presentation 層と同じ（「生成した interface を実装する」の例外） |

- **jOOQ の置き場所**：jOOQ（`org.jooq` と、生成したコード `com.ecsite.jooq`）を使えるのは、モジュールの `infrastructure/persistence/` のクラスだけにする。application と domain が永続化の形に依存しないようにするためである。モジュールの外の共通の設定（`config/event/EventPublicationRecords`）は、schema の持ち主の検査（`SchemaOwnershipTest`）が別に見る。例外は次の 1 つで、増やすときはこの表に理由を書く。

| クラス | 置き場所 | 例外の理由 |
|--------|----------|------------|
| `MockShipmentQuery` | `shipping/infrastructure/mock/` | mock の物流の画面だけが使う問い合わせ。`persistence/` に置くと、本番で使うクラスと区別しにくくなる |

理由：どのモジュールでも同じ場所に同じものを見つけられるようにし、domain を永続化と API の形に依存させないため。参照の型を集約から分けると、集約の中を変えても presentation と公開の API が変わらない。

確認方法：

- 層の間の依存：モジュールの中の層の間は、バックエンドのアーキテクチャの依存の表にある全ての組と、表にない層・パッケージへの依存が自動（ArchUnit）。`api/` に interface がないこと、`api/` の型を `api/` の外が実装・継承しないこと、domain のパッケージに循環がないことも自動（ArchUnit）。presentation が依存する domain をエラーの定義と値の型に限ることはレビュー（集約に依存しないことは次の行で自動）
- 参照の service が返す型：presentation が集約に依存しないことは自動（ArchUnit）、外部の呼び出しの前の読み取りの置き場所はレビュー
- 公開の API：`api/` のクラスが service に委ねることはレビュー、application が `api` に依存しないことは自動（ArchUnit）
- application の `port/` への依存：自動（ArchUnit）
- 認証の情報の取り出し：自動（ArchUnit）
- jOOQ の置き場所：自動（ArchUnit）
- イベントの置き場所と送り出し：レベル A の application が送り出さないことは自動（ArchUnit）、置き場所はレビュー
- 共通コードと設定の依存：自動（ArchUnit）

### 1.2 モジュールの外に出すのは `api/` と `event/` だけ

他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。

理由：モジュールの中を、他のモジュールに影響なく変えられるようにするため。

確認方法：自動（ModulithTest）

### 1.3 他のモジュールは腐敗防止層で包む

- 他のモジュールの `api/` を呼ぶときは、port（`<相手>Directory` の interface と、相手の情報を変換して持つ自分の型）を使う層の `port/` に置き、実装は `infrastructure/directory/` に置く。
- 他のモジュールの `api/` を使うのは `infrastructure/directory/` だけにする。例外は 1 件で、`member.api.CurrentMemberIds` を presentation から使う（「標準の構成」の認証の情報の取り出しの表、※1）。
- 相手の型を内部で使わない。
- 腐敗防止層の実装（`<相手>ApiDirectory`）は public にしない。理由：使う側は port の interface を通して使うため。
- モジュールの直下に、クラスが 1 つだけのパッケージ（`<相手>dir` など）を作らない。
- 列挙を別の列挙に変換するとき（相手の型と自分の型、ドメインとイベント、ドメインと生成したモデル）は、`switch` 式で値ごとに書く。`valueOf(x.name())`、`fromValue(x.name())` は使わない。
- モジュールの間とレイヤーの間で、列挙を名前の文字列で受け渡さない。command・query・view・api の型は列挙で持つ。名前の文字列から列挙にするのは、DB の文字列を読む `infrastructure/persistence/` と、ドメインの中だけ（`java-rules` が、それ以外の `valueOf`・`fromValue` を見つける）。

理由：相手の変更の影響を、実装の 1 か所に限るため。`switch` 式は列挙の全ての値を書かないとコンパイルに失敗するので、片方に値を追加して変換を書き忘れると気づける（名前で変換すると、実行するまで気づけない）。

※1：`CurrentMemberIds` は、要求の token から会員の ID を得る、API の入口（presentation）のためのクラスで、会員のモジュールが公開している。会員の腐敗防止層（port と実装）をモジュールごとに作ると、読み取りだけのために同じ port と実装の組が増える。`CurrentMemberIds` 自身も取り出しの対象に含め、呼び出し元を presentation 層に限る。理由：他のモジュールの `api/` の規則は `infrastructure/directory/` からの利用を除くので、それだけでは directory を経て application 層が要求の会員 ID を得る形を防げないため。

確認方法：置き場所と、他のモジュールの `api` を使うのが `infrastructure/directory/` だけであること（許可の一覧の 1 件を除く）、実装が public でないことは自動（ArchUnit）。変換の書き忘れは自動（コンパイル）で、全ての値の対応は変換ごとの単体テストで確認する。`valueOf(x.name())` と、domain・永続化以外での名前の文字列からの `valueOf`・`fromValue` を使わないことは自動（`scripts/ci/java-rules.mjs`）。port と実装の分け方はレビュー

### 1.4 パッケージの大きさ

1 つのパッケージの直下の `.java` は 10 を目安、20 を上限にする（`package-info.java`、テスト、生成したコード（OpenAPI と jOOQ）は数えない）。目安を超えて分けないときは、下の表に理由を書く。

| パッケージ | 分けない理由 |
|------------|--------------|
| `catalog.infrastructure.persistence` | jOOQ の Repository と、それだけが使う package-private のクラス。分けるとクラスを公開しなければならない |
| `config.security` | 認証と認可の設定。組み立てられるクラスは package-private で、分けるとクラスを公開しなければならない |

`ModuleStructureTest` が表を読むので、表の書式を変えない。1 列目は、バッククォートで囲んだパッケージ名（`com.ecsite.` と `module.` を除く。例：`catalog.infrastructure.persistence`）にする。節の見出しの名前（「パッケージの大きさ」）を変えるときは、`PACKAGE_SIZE_TITLE` も修正する。

確認方法：上限は自動（ArchUnit）。目安を超えるパッケージの一覧を「パッケージの大きさ」の表と照合することは自動（`ModuleStructureTest`）。表の「分けない理由」の妥当性はレビュー

### 1.5 共通の型

- 共通コード（`common/`）には、2 つ以上のモジュールが使うものだけを置く。1 つのモジュールだけが使うものは、そのモジュールに置く。例外は横断の仕組み（エラーの応答、ログ、入力の検証のメッセージ、認証の情報の取り出し）で、全てのモジュールの要求の処理に関わり、どのモジュールにも属さないので、`common/` に置く。
- モジュールに共通の型は `common/model/` に置く。`base/`：集約の基底（`AggregateRoot`、`DomainEvent`、`AggregateGuards`）、`value/`：値（`Money`、`BoundedText`、`BusinessTime`、`Operator`）。
- 共通の値にするのは、2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つものだけ。1 つのモジュールだけのものは、そのモジュールの domain に置く。置き場所（集約の `vo/` か domain の直下）は「標準の構成」のとおり。
- 金額は、モジュールの内部と公開の型（`api/` の record、command、view）で `Money` を使う。整数で持つのは presentation、infrastructure（DB）、設定の値の型だけ。設定の値の金額は、使う所で `Money` にする。
- 金額の掛け算・割り算は、`Money` の操作と税の計算（`TaxCalculator`）の中だけで行う。端数が出る計算は、端数の扱い（切り捨て など）をメソッドで明示する。
- 例外として、商品の一覧の税込価格の絞り込みと並べ替えは、`catalog.infrastructure.persistence.PriceInclTaxField` が SQL で税込価格を計算する。理由：税区分ごとに税率が違い、税抜の順と税込の順が一致しない。一覧は DB で `LIMIT`・`OFFSET` を使ってページングするので、絞り込みと並べ替えも DB で行う必要がある。`TaxCalculator` と同じ結果になることは、端数の境界値を含めて `PriceInclTaxFieldTest` で確認する。

理由：同じ規則を 1 か所に置きつつ、共通の型を増やしすぎて、モジュールが互いに依存しないようにするため。金額の計算を複数の場所に書くと、端数の扱いが場所によって変わる。

確認方法：金額は自動（ArchUnit）、置き場所と基準、金額の計算はレビュー

## 2. API の実装

### 2.1 生成した interface を実装する

Controller は OpenAPI から生成した interface（`XxxApi`）を実装する。パスと入力の形は interface に任せ、`@RequestMapping` を書かない。

例外：[API の規約](api-conventions.md)の「OpenAPI の外のエンドポイント」（決済・物流の通知、mock の画面）は、OpenAPI に書かず、そのサービスの型と一緒に `infrastructure` に置く。`@RestController` は presentation だけに付けるので、`@Controller` と `ResponseEntity` で書く（[決済の設計](../02-設計/payment.md)、[出荷の設計](../02-設計/shipping.md)）。

例外：ログインした人の情報を返す操作（`MeController`）は、モジュールの presentation でなく `config/security/` に置く。認可の設定と同じ権限の変換（`PermissionConverter`）を使い、どのモジュールにも属さないため。ArchUnit の `@RestController` の置き場所の検査は、`MeController` だけを完全修飾名で除く。

理由：契約（`api/openapi/`）を正本にし、実装とずれないようにするため。

確認方法：`@RestController` の置き場所は自動（ArchUnit）、実装は自動（コンパイル）、契約にない endpoint はレビュー

### 2.2 応答への変換は手動で書く

- 変換はモジュールごとの mapper（`XxxMapper`）に手動で書く。変換のライブラリーは使わない。
- 生成したモデルは、引数なしのコンストラクターと項目名のメソッドで組み立てる（`new OrderLine().productCode(...)`）。位置で並べるコンストラクターは使わない。項目が 1 つの型（作成した ID など）も同じ。
- 作成の応答（201、Location、作成した ID）は、`common/presentation` の共通の補助で作る。`Location` はホストを含まない相対の URL（`/api/...`）で、プロキシのヘッダーの扱いによらず同じ値になる。
- 入れ子の型（明細、金額、住所など）は型ごとに private メソッドに分け、上位のメソッドには項目の並びだけを残す。
- 自分で定義した record は、引数が多ければ 1 行に 1 つ書く。

理由：項目の取り違えを防ぎ、変換を読みやすくするため。

確認方法：生成したモデルの組み立ては自動（ArchUnit）、他はレビュー

## 3. 入力の検証とエラー

### 3.1 形の検証は presentation、業務の規則は domain

- 長さ・必須・形式は OpenAPI の制約（生成した Bean Validation）、業務の規則（重複、状態、期間の前後など）は domain で確認する。
- リクエストの数値の項目には範囲（`minimum` と `maximum` の両方）、文字の項目には長さ（`maxLength`）か形式（`pattern`・`format`・`enum`）を書く。上限を書かないのは、計算に使わない項目（楽観ロックのバージョン、ページ番号、絞り込みの値など）だけで、契約のテストの一覧に理由を書く。理由：上限のない数値を計算に使うと、桁あふれや DB の範囲の超過で 500 になるため。
- 同じ項目の契約の制約、ドメインの上限の定数、DB の列の長さは一致させる ※2。
- 文字の「空白でない」「長さを code point で数える」の判定は `BoundedText` で行い、各ドメインは上限とエラーの種類だけを持つ。

※2：`OpenApiRequestConstraintTest`（制約の漏れ）と `FieldConstraintContractTest`（契約・ドメイン・DB の一致）で確認する。項目を追加したら、テストの表にも追加する。

理由：契約に書ける検証は契約に、業務の規則は 1 か所に置くため。

確認方法：自動（契約のテスト）、判定の重複はレビュー

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- 業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum）で表し、`BusinessException.of(...)` で投げる。定数は種類（`ErrorType`）と、いつ起きるかの Javadoc を持つ。
- メッセージのキーは `ecsite.<モジュール>.<定数の名前の小文字>`、文は `messages.properties` に置く（例：`TOO_MANY_CART_ITEMS` → `ecsite.cart.too_many_cart_items`）。
- 文に上限などの値を入れるときは、数字を書かず名前で差し込み（`{max} 品目までです`）、値はドメインの定数から渡す。
- レスポンスへの変換は `GlobalExceptionHandler` だけが行う。

理由：フロントが `errorCode` で分岐でき、画面に表示する日本語を 1 か所で管理し、規則の値が変わっても文が取り残されないようにするため。

確認方法：自動（メッセージのテスト）、文に数字がないことは自動（CI の検査 `message-numbers`）

### 3.3 入力の検証のメッセージ

入力の検証の文は、フレームワークの既定の文（JVM のロケールで言語が変わる）を使わず、`ecsite.validation.<種類>`（`notnull`、`size`、`min` など）の文を `messages.properties` から取る。上限・下限は `{max}`、`{min}`、`{value}` で差し込み、知らない種類は `ecsite.validation.invalid`。

業務の検証（ドメイン）が同じ誤りに業務のエラーを持つ項目は、形の検証の文を、項目のパス（`shippingAddress.postalCode` など）ごとに業務のエラーのコードへ対応づけられる。対応があれば、種類ごとの文の代わりに、そのコードの文とドメインの定数から差し込む値を使う。

- 対応の表は、そのモジュールの presentation に置く（`ValidationErrorMapping` の実装の bean）。
- パスはリクエストの本文の項目のパス。モジュールの間で同じパスを対応づけると、起動で失敗する。

理由：どの環境でも同じ日本語を返し、形の検証と業務の検証のどちらで見つかっても、消費者に同じ文を返すため。

確認方法：自動（検証のメッセージのテスト）

### 3.4 DB の制約の違反は業務のエラーに変えるか、重複を吸収する

DB の制約でしか守れない規則（一意性など）の違反は、リポジトリが次のどちらかで扱う。

| 重複のとき | 扱い方 |
|------------|--------|
| 業務のエラーにする（同じ品番の登録など） | `ConstraintViolationTranslator` で業務のエラーに変える。制約名とエラーの対応は、各リポジトリの `CONSTRAINTS` に置く |
| 吸収する（同じ通知の受け取り、同じメールの送信の記録など、冪等にする）か、同じトランザクションで処理を続ける | 重複を無視する挿入（`ON CONFLICT DO NOTHING`）にし、挿入したかを真偽値で返す。呼び出し元が真偽値で分ける |

理由：制約の違反を 500 にしないため。PostgreSQL は制約の違反でトランザクション全体を失敗させるので、続ける処理では違反を起こさない挿入にする。

確認方法：自動（リポジトリの統合テスト）、扱い方の選び方はレビュー

### 3.5 更新の競合は `version`

契約の形（`version` を送る、状態の切り替えは冪等にして送らない）は、[API の規約](api-conventions.md)の「HTTP メソッド」。同時に編集されうる集約の更新は、`version` が合わなければ `CONCURRENT_MODIFICATION` を投げる。

理由：他の利用者の変更を、通知なしに上書きしないため。

確認方法：自動（API の統合テスト）

## 4. トランザクションと時刻

### 4.1 トランザクションは application

- `@Transactional` は application の service のクラスに付ける。参照だけなら `readOnly = true`。api と presentation には付けない。
- 例外：infrastructure で、別のトランザクションが必要なもの（呼び出し元と別に確定させる記録など）。付けた所に理由を書く。
- 外部サービス（決済・画像の変換・ファイル保存・物流・メールなど）は、トランザクションの外で呼び、タイムアウトを設定する（BR-40）。呼ぶ前の読み取りと呼んだ後の更新を、それぞれのトランザクションにする（「標準の構成」「外部の呼び出しの前の読み取り」）。イベントのリスナーで呼ぶときは、リスナーをトランザクションなし（`@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)`）にする。
- 外で呼べないものは、呼ぶ所と設計書に理由を書く（出荷のメール：[通知モジュール](../03-機能仕様/notification.md)）。

理由：1 つの操作を 1 つのトランザクションにし、境界を層で決めるため。外部サービスを待つ間に DB の接続を持つと、外部サービスが遅いときに接続が尽き、関係のない操作まで止まるため。

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

※3：用途の名前を使うのは、1 つの集約に用途が複数あるとき、他のモジュールの呼び出しを組み合わせるとき。データを変えずに他のモジュールの結果を組み合わせる service（注文の手続きの見積もりなど）も用途の名前にし、読み取り専用のトランザクションにする。

別のモジュールに同じ単純名のクラスがあってよい（Spring の Bean の名前は完全修飾名にしている）。

確認方法：リポジトリと腐敗防止層は自動（ArchUnit）、service とイベントはレビュー

### 5.3 インスタンスを作らないクラス

static のメンバーだけのクラス（定数や補助の関数をまとめたもの）は `final` にし、コンストラクタを `private` にする。Lombok の `@NoArgsConstructor(access = AccessLevel.PRIVATE)` でも、手動で書いてもよい。

- 対象は最上位のクラス。enum・record・interface・注釈は対象でない。

理由：インスタンスを作っても使い道がなく、作れると使い方を誤る。

検査に Checkstyle の `HideUtilityClassConstructor` を使わない理由：ソースを読むので、Lombok で作るコンストラクタが見えずに誤って当たる。ArchUnit はコンパイルした後のクラスを読むので、Lombok で書いても手動で書いても同じに確認できる。

確認方法：自動（ArchUnit）

### 5.4 書式

Java の整形は、IntelliJ IDEA の Reformat Code を推奨する。整形は強制しない（CI と commit の前の hook では検査しない）。ただし、import の並びと使われていない import と `*` の import は、Checkstyle が検査する（下の import の項）。

- 既存のファイルの書式だけを直す変更は、他の変更と同じ commit に混ぜない。理由：差分のレビューと `git blame` を読みやすく保つため。

import は次のグループの順に並べる。グループの間は空行 1 行で分け、グループの中は文字コードの順にする。

| 順 | グループ |
|----|----------|
| 1 | `java`・`javax` 以外 |
| 2 | `java`・`javax` |
| 3 | static |

- IntelliJ の Optimize Imports を上の表の並びにする設定と、エージェントが整形するときの注意は、[開発環境の起動](../06-手順書/setup.md)の「IDE の設定」にある。
- Checkstyle の `UnusedImports`、`AvoidStarImport`、`CustomImportOrder`（`backend/ecsite/config/checkstyle/checkstyle.xml`）が、使われていない import、`*` の import（static を含む。例外は設けない）、上の並びを、全てのソースセット（main、test、integrationTest、testFixtures、tools）で検査する。違反があれば `./gradlew check` と CI が失敗する。Javadoc の記載漏れの検査だけは main に限る。

理由：今のコードは IntelliJ の書式でほぼそろっている。Spotless で強制する案は、今の書式に最も近い設定でも、読みやすさが下がる変更が出た（測定の記録は [issue #19 のコメント](https://github.com/roger88106/ec-site/issues/19)）。

確認方法：

- 整形：なし
- import：自動（Checkstyle）

## 6. 設定の値

### 6.1 設定の値の型

- 設定は `@ConfigurationProperties` の record で受け、`@Validated` を付ける。型は使う層に置く（画像の変換の設定は `infrastructure/converter/` など）。
- 接頭辞は `ecsite.<モジュール>`、まとまりを分けるときは `ecsite.<モジュール>.<範囲>`（`ecsite.media.storage` など）。モジュールでない共通の設定は `ecsite.<範囲>`（`ecsite.security` など）。
- 環境で変わる値（接続先、資格情報、ID、バケット名）は既定値を持たず必須にする（BR-31）。環境によらない値（上限、時間など）だけ既定値を持つ ※4。
- モジュールの `@Configuration` のクラスは、`infrastructure/`（またはその下）に置く。
- presentation は、設定の値の型（`@ConfigurationProperties` を付けたクラス）を受け取らない。設定の値が必要なときは、application の service が設定の値の型を読み、view で返す。`api/` は対象にしない。
- `@EnableConfigurationProperties` は `@Configuration` のクラスに付ける。条件で切り替える実装（mock など）の設定は、同じ条件の設定のクラスに付ける。
- 定期の処理の間隔も設定の型の項目で受け、`@Scheduled` は SpEL で設定の型の Bean を参照する（`${...}` の既定値を注釈に書かない）。
- 注釈が定数しか取らず、環境で変えない値（定期の処理のロックの上限など）は、設定にせず定数にし、理由をコメントに書く。
- 秘密の値（鍵、パスワード、署名の鍵、ソルト）を持つ設定の record は `toString` を上書きして、秘密の値を `***` にする（`StripeProperties` と同じ形にする）。理由：record の既定の `toString` は全ての項目を出力し、設定の値をログや例外のメッセージに出力したときに秘密の値が漏れるため。秘密の値の項目は、名前（小文字にして判定）に `secret`、`password`、`salt`、`token`、`credential` を含むか、`key` で終わるものとする。
- 設定の型では、Lombok の `@ToString`・`@Data`・`@Value` を使わず、`toString` を手動で書く。理由：Lombok の注釈はクラスファイルに残らず、作られた `toString` は手動で宣言したものと区別できないので、構成の検査が値を出力する `toString` を見逃すため。

※4：本番では設定しない接続の値（S3 互換ストレージの接続先と資格情報。本番は AWS の既定と IAM ロール）は任意にし、本番で使う値（path style を使わない）を既定値にする。

理由：設定を忘れたまま、別の環境の値で動き出さないようにするため（足りなければ起動に失敗する）。

確認方法：

- 設定の値の型：自動（起動のテスト）。`@EnableConfigurationProperties` の置き場所と、`@Configuration` が infrastructure にあること、presentation が設定の値の型に依存しないことは自動（ArchUnit）。型の置き場所と接頭辞、`@Scheduled` の間隔を設定の型から取ることはレビュー
- 秘密の値の `toString`：秘密の値の項目を持つ設定の型（入れ子を含む）が `toString` を宣言していることは自動（`ConfigurationPropertiesTest`。対象の範囲はその Javadoc）。値をマスクしていることは単体テスト（`toString` に秘密の値が含まれないこと）。Lombok の注釈を使わないことはレビュー

## 7. 静的解析

### 7.1 null の扱い

- main では、`com.ecsite` の下の全てのパッケージ（生成したコードの `com.ecsite.jooq`、`com.ecsite.api` を除く）で、注釈のない引数・戻り値・フィールドを null でないとみなす。NullAway がコンパイルのときに確認する（テストのソースセットと tools は対象外）。
- null を取りうる所には JSpecify の `org.jspecify.annotations.Nullable` を付ける。他の `Nullable`（`jakarta.annotation`、`javax.annotation`、`org.springframework.lang`）は使わない。型の注釈なので、配列や修飾した型では型の直前に付け、迷ったら JSpecify の位置にする（`String @Nullable []` など）。
- OpenAPI から生成した interface とモデルは null の注釈を持たないが、明示した `@Nullable` は NullAway が読む（設定 `AcknowledgeRestrictiveAnnotations`）。生成した interface の `@Nullable` の引数を、注釈なしでオーバーライドすると、その引数は null でないとみなされる。そこにモデルの `@Nullable` の getter の値を渡すと、NullAway が報告する。
- 選び方：
  - 今の振る舞いで null が正しい値（API の応答の省略できる項目、DB の null を取りうる列など）には `@Nullable` を付ける。
  - null を渡す必要がない所は、null を作らない書き方にする。
  - 値がないことを返す参照のメソッド（`findXxx`）は、今のコードのとおり `Optional` を返す。集約が null を取りうるフィールドを持つときは、フィールドを `@Nullable` にする。
  - getter の戻り値は、値の有無を何が決めるかで選ぶ。
    - 値の有無が状態に依存しない（会員でないカートには会員 ID がない）ときは、`Optional` を返す（`Cart.memberId()`）。
    - 値の有無が状態で決まる（キャンセルした注文だけにキャンセルの記録がある）ときは、`@Nullable` を返す（Lombok が生成する `Order.cancellation()`）。呼び出し側は、状態を確認してから `Objects.requireNonNull` で受ける。
- この規則の対象は、`@Nullable` の値を受ける `Objects.requireNonNull` のメッセージ。メッセージには、null でない根拠となる状態を肯定の文で書く（「キャンセルした注文にはキャンセルの記録がある」）。「〜がない」のように、null のときの失敗を述べる形にしない。メッセージを付けずに引数を確認するだけの呼び出し（`Operator` の生成など）は対象外とする。
- jOOQ のレコードの getter は注釈がないので、NullAway は null を確認しない。null を取りうる列の値は、`@Nullable` を付けたローカル変数・引数か `Optional` で受ける（null を取りうる列を、注釈のない引数に渡さない）。
- `lombok.NonNull` は実行時の確認として使ってよい（外さない）。

理由：null の誤りを、実行より前のコンパイルで見つけるため。Spring Framework 7 と同じ JSpecify の注釈なので、Spring の API の null の注釈も NullAway が読む。

確認方法：自動（NullAway、`scripts/ci/java-rules.mjs`）、`@Nullable` と `Optional` の選び方はレビュー

### 7.2 検査を抑えるとき

- Error Prone と NullAway の報告は修正する。次のものだけ、最も狭い範囲（宣言 1 つ）に `@SuppressWarnings("<検査の名前>")` を付ける。
  - 誤った報告（フレームワークが初期化するフィールドなど）
  - 修正すると振る舞いが変わるもの
  - mock（本番で動かないコード）で、修正する手間に見合わないもの
- 抑える理由は `@SuppressWarnings` の直前の行に `//` のコメントで書く。他の注釈が間にあるときは、その注釈の行を飛ばして直前の行に書く。
- 検査を全体で無効にする、重大度を変えるのは `build.gradle.kts` で行い、先に spec で決める。意味のない注釈を足さないと消えない報告は、注釈でなく設定で扱う。今無効にしている検査と理由は、`backend/ecsite/build.gradle.kts` の Error Prone の設定。
- 生成したコード（`build/generated` の下）は検査しない。

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

マイグレーションはデプロイごとに 1 回適用し、起動時には適用しない。マイグレーションを適用してからアプリを入れ替えるまでの間、前のバージョンのアプリが新しいスキーマで動く。

新しいマイグレーションは、前のリリースのアプリが動き続けられる変更だけにする。次の変更は、1 つのリリースでしない。

- 表・列の削除、改名、列の型の変更
- 既存の列を NOT NULL にすること、既定値のない NOT NULL の列の追加
- 既存の列の既定値の削除

検出する変更の種類と、SQL の書き方ごとの扱いは、`scripts/ci/migrations.mjs` の先頭のコメント。

既定値の削除を同じ扱いにする理由：NOT NULL の列では、既定値が入ることを前提に列を指定しない、前のリリースのアプリの INSERT が失敗する。NULL を許す列でも、前のリリースのアプリが既定値を前提にしていれば、列に入る値が NULL に変わる。

これらは、次の 3 段に分ける。

| 段 | リリース | すること |
|----|----------|----------|
| 1 | 最初 | 新しい表・列を追加する（前のバージョンのアプリは使わない） |
| 2 | 次 | アプリを新しい表・列に切り替えてリリースする |
| 3 | その次 | 使わなくなった表・列を削除する ※5 |

既存の列の既定値の削除は、段 1 がない。段 2 で、アプリが列に値を必ず書くようにしてリリースし、段 3 で既定値を削除する。除外の一覧に置くのは段 3 の削除（※5 と同じ）。

※5：段 3 の削除は、マイグレーションの検査の除外の一覧（`scripts/ci/migrations-exclusions.json`）に置き、理由に、どのリリースで使わなくなったかを書く。書き方は `scripts/ci/migrations.mjs` の先頭のコメント。

理由：前のバージョンのアプリが、新しいスキーマで失敗しないため。

確認方法：自動（CI の検査 `migrations`）。3 段に分ける手順の妥当性はレビュー
