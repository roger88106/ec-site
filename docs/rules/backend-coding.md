# バックエンドの書き方

今のコードで守っている書き方。構成の考え方は [バックエンドのアーキテクチャ](../design/backend-architecture.md)、API の規約は [API の規約](api-conventions.md)。

## 規則の一覧

| 節 | 規則 | 確認方法 |
|----|------|----------|
| 1.1 | 標準の構成（層とパッケージ） | 自動（ArchUnit） |
| 1.1 | 参照の service は view を返し、presentation は集約を受け取らない（レベル A） | presentation が集約に依存しないことは自動（ArchUnit）、外部の呼び出しの前の読み取りの置き場所はレビュー |
| 1.1 | 公開の API は `api/` のクラスが service に委ねる | 自動（ArchUnit） |
| 1.1 | イベントの置き場所と送り出し | レベル A の application が送り出さないことは自動（ArchUnit）、置き場所はレビュー |
| 1.2 | モジュールの外に出すのは `api/` と `event/` だけ | 自動（ModulithTest） |
| 1.3 | 他のモジュールは腐敗防止層で包む。列挙は `switch` 式で変換する | 置き場所は自動（ArchUnit）、変換の書き忘れは自動（コンパイル）、`valueOf(name())` を使わないことは自動（`scripts/ci/java-rules.mjs`）、port と実装の分け方はレビュー |
| 1.4 | パッケージの大きさ | 上限は自動（ArchUnit）、目安はレビュー |
| 1.5 | 共通の型と金額 | 金額は自動（ArchUnit）、置き場所と基準、金額の計算はレビュー |
| 2.1 | 生成した interface を実装する | 自動（コンパイル）、契約にない endpoint はレビュー |
| 2.2 | 応答への変換は手動で書く | 生成したモデルの組み立ては自動（ArchUnit）、他はレビュー |
| 3.1 | 形の検証は presentation、業務の規則は domain | 自動（契約のテスト）、判定の重複はレビュー |
| 3.2 | エラーは `XxxErrorCode` と `BusinessException` | 自動（メッセージのテスト）、文に数字がないことは自動（CI の検査 `message-numbers`） |
| 3.3 | 入力の検証のメッセージ | 自動（検証のメッセージのテスト） |
| 3.4 | DB の制約の違反は業務のエラーに変えるか、重複を吸収する | 自動（リポジトリの統合テスト）、扱い方の選び方はレビュー |
| 3.5 | 更新の競合は `version` | 自動（API の統合テスト） |
| 4.1 | トランザクションは application。外部サービスはトランザクションの外で呼ぶ | 自動（ArchUnit）、infrastructure の例外の理由と外部サービスの呼び出しはレビュー |
| 4.2 | 現在時刻は `Clock` から | 自動（ArchUnit） |
| 5.1 | Lombok は main だけ | 自動（`scripts/ci/java-rules.mjs`） |
| 5.2 | 名前の形 | リポジトリと腐敗防止層は自動（ArchUnit）、service とイベントはレビュー |
| 5.3 | インスタンスを作らないクラス | 自動（ArchUnit） |
| 6.1 | 設定の値の型 | 自動（起動のテスト）、`@EnableConfigurationProperties` の置き場所は自動（ArchUnit）、型の置き場所と接頭辞、`@Scheduled` の間隔を設定の型から取ることはレビュー |

確認方法が「レビュー」のものは、各 spec の [レビュー](review.md) で読む。

## 1. 層とパッケージ

### 1.1 標準の構成

どのモジュールも次の構成にする。不要な層は作らない（イベントを出さないなら `event/` は不要）。

```
module/<モジュール>/
├── api/                公開の同期の API（@NamedInterface("api")）
├── event/              公開のイベント（@NamedInterface("event")）
├── presentation/       API の入口（生成した interface の実装）と、API の型への変換
├── application/        use case（@Transactional はここ）
│   ├── model/          command・query・view
│   └── port/           application が使う port（参照の Repository、他のモジュールの Directory）
├── domain/             業務の型。エラーの定義（<モジュール>ErrorCode）
│   └── <集約>/         集約、vo/（値）、port/（Repository、domain が使う Directory）
└── infrastructure/     port の実装
    ├── persistence/    jOOQ の Repository と、それが使うクラス
    ├── directory/      他のモジュールの api を呼ぶ Directory の実装（腐敗防止層）
    └── <外部>/          外部の仕組み（storage、converter、cache など）
```

| 層 | 依存してよい先 |
|----|----------------|
| api・event | application、domain、common |
| presentation | application、domain（エラーの定義と値の型）、common |
| application | domain、common |
| domain | common（レベル A は Spring・jOOQ にも依存しない） |
| infrastructure | application の port、domain、common |

| レベル | 中身の違い |
|--------|------------|
| A | 集約は jMolecules で明示し、Repository の port は `domain/<集約>/port/`。command と query の service・Repository を分ける（CQRS） |
| B | domain は振る舞いの少ない型でよく、集約ごとのパッケージは作らない。port は全て `application/port/`。CQRS なし。`application/model/` は必要なときだけ |

層の中の役割：

- **参照と更新（レベル A）**：参照の service は、`application/model/` の view を返し、集約を返さない。presentation は集約を受け取らない。画面の操作の可否は、view を作る所で集約のメソッドを呼んで view に入れる。
- **外部の呼び出しの前の読み取り**：更新の流れで、外部のサービスを呼ぶ前に集約の状態を読む処理（外部への要求を作る）も、参照の service に置き、要求の型か view を返す。外部の呼び出しをトランザクションの外で行い、呼んだ後の更新を更新の service で行う。
- **イベント**：送り出し方は [バックエンドのアーキテクチャ](../design/backend-architecture.md) に従う。domain は自分のモジュールの `event/` を参照してよい（集約が公開のイベントを記録するため）。モジュールの中だけのイベントは、レベル A は集約のパッケージ、レベル B は application に置く。
- **公開の API**：`api/` の interface は、`api/` の `@Component` が実装し、application の service に委ねる。service は `api/` の interface を実装しない。

理由：どのモジュールでも同じ場所に同じものを見つけられるようにし、domain を永続化と API の形に依存させないため。参照の型を集約から分けると、集約の中を変えても presentation と公開の API が変わらない。

### 1.2 モジュールの外に出すのは `api/` と `event/` だけ

他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。

理由：モジュールの中を、他のモジュールに影響なく変えられるようにするため。

### 1.3 他のモジュールは腐敗防止層で包む

- 他のモジュールの `api/` を呼ぶときは、port（`<相手>Directory` の interface と、相手の情報を変換して持つ自分の型）を使う層の `port/` に置き、実装は `infrastructure/directory/` に置く。
- 相手の型を内部で使わない。
- モジュールの直下に、クラスが 1 つだけのパッケージ（`<相手>dir` など）を作らない。
- 列挙を別の列挙に変換するとき（相手の型と自分の型、ドメインとイベント、ドメインと生成したモデル）は、`switch` 式で値ごとに書く。`valueOf(x.name())`、`fromValue(x.name())` は使わない。

理由：相手の変更の影響を、実装の 1 か所に限るため。`switch` 式は列挙の全ての値を書かないとコンパイルに失敗するので、片方に値を追加して変換を書き忘れると気づける（名前で変換すると、実行するまで気づけない）。

### 1.4 パッケージの大きさ

1 つのパッケージの直下の `.java` は 10 を目安、20 を上限にする（`package-info.java` とテストは数えない）。目安を超えて分けないときは、下の表に理由を書く。

| パッケージ | 数 | 分けない理由 |
|------------|----|--------------|
| `catalog.infrastructure.persistence` | 13 | jOOQ の Repository と、それだけが使う package-private のクラス。分けるとクラスを公開しなければならない |
| `reporting.domain` | 15 | 集約がなく、集計の値（区切り、推移の点、日ごとの売上 など）と列挙が多い。集約ごとに分ける区切りがない |

### 1.5 共通の型

- モジュールに共通の型は `common/model/` に置く。`base/`：集約の土台（`AggregateRoot`、`DomainEvent`）、`value/`：値（`Money`、`BoundedText`、`Operator`）。
- 共通の値にするのは、2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つものだけ。1 つのモジュールだけのものはその `vo/` に置く。
- 金額は、モジュールの内部と公開の型（`api/` の record、command、view）で `Money` を使う。整数で持つのは presentation、infrastructure（DB）、設定の値の型だけ。設定の値の金額は、使う所で `Money` にする。
- 金額の掛け算・割り算は、`Money` の操作と税の計算（`TaxCalculator`）の中だけで行う。端数が出る計算は、端数の扱い（切り捨て など）をメソッドで明示する。

理由：同じ規則を 1 か所に置きつつ、共通の型を増やしすぎて、モジュールが互いに依存しないようにするため。金額の計算を複数の場所に書くと、端数の扱いが場所によって変わる。

## 2. API の実装

### 2.1 生成した interface を実装する

Controller は OpenAPI から生成した interface（`XxxApi`）を実装する。パスと入力の形は interface に任せ、`@RequestMapping` を書かない。

例外：外部のサービスとのやり取りのエンドポイント（mock の支払画面、Stripe の通知のエンドポイント）は EC の API ではないので、OpenAPI に書かず、そのサービスの型と一緒に `infrastructure` に置く。`@RestController` は presentation だけに付けるので、`@Controller` と `ResponseEntity` で書く（[決済モジュール](../design/modules/payment.md)）。

理由：契約（`api/openapi/`）を正本にし、実装とずれないようにするため。

### 2.2 応答への変換は手動で書く

- 変換はモジュールごとの mapper（`XxxMapper`）に手動で書く。変換のライブラリーは使わない。
- 生成したモデルは、引数なしのコンストラクターと項目名のメソッドで組み立てる（`new OrderLine().productCode(...)`）。位置で並べるコンストラクターは使わない。項目が 1 つの型（作成した ID など）も同じ。
- 作成の応答（201、Location、作成した ID）は、`common/presentation` の共通の補助で作る。
- 入れ子の型（明細、金額、住所など）は型ごとに private メソッドに分け、上位のメソッドには項目の並びだけを残す。
- 自分で定義した record は、引数が多ければ 1 行に 1 つ書く。

理由：項目の取り違えを防ぎ、変換を読みやすくするため。

## 3. 入力の検証とエラー

### 3.1 形の検証は presentation、業務の規則は domain

- 長さ・必須・形式は OpenAPI の制約（生成した Bean Validation）、業務の規則（重複、状態、期間の前後など）は domain で確認する。
- リクエストの数値の項目には範囲（`minimum`・`maximum`）、文字の項目には長さ（`maxLength`）か形式（`pattern`・`format`・`enum`）を書く。
- 同じ項目の契約の制約、ドメインの上限の定数、DB の列の長さは一致させる ※1。
- 文字の「空白でない」「長さを code point で数える」の判定は `BoundedText` で行い、各ドメインは上限とエラーの種類だけを持つ。

※1：`OpenApiRequestConstraintTest`（制約の漏れ）と `FieldConstraintContractTest`（契約・ドメイン・DB の一致）で確認する。項目を追加したら、テストの表にも追加する。

理由：契約に書ける検証は契約に、業務の規則は 1 か所に置くため。

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- 業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum）で表し、`BusinessException.of(...)` で投げる。定数は種類（`ErrorType`）と、いつ起きるかの Javadoc を持つ。
- メッセージのキーは `ecsite.<モジュール>.<定数の名前の小文字>`、文は `messages.properties` に置く（例：`CART_ITEM_LIMIT_EXCEEDED` → `ecsite.cart.cart_item_limit_exceeded`）。
- 文に上限などの値を入れるときは、数字を書かず名前で差し込み（`{max} 品目までです`）、値はドメインの定数から渡す。
- レスポンスへの変換は `GlobalExceptionHandler` だけが行う。

理由：フロントが `errorCode` で分岐でき、画面に表示する日本語を 1 か所で管理し、規則の値が変わっても文が取り残されないようにするため。

### 3.3 入力の検証のメッセージ

入力の検証の文は、フレームワークの既定の文（JVM のロケールで言語が変わる）を使わず、`ecsite.validation.<種類>`（`notnull`、`size`、`min` など）の文を `messages.properties` から取る。上限・下限は `{max}`、`{min}`、`{value}` で差し込み、知らない種類は `ecsite.validation.invalid`。

業務の検証（ドメイン）が同じ誤りに業務のエラーを持つ項目は、形の検証の文を、項目のパス（`shippingAddress.postalCode` など）ごとに業務のエラーのコードへ対応づけられる。対応があれば、種類ごとの文の代わりに、そのコードの文とドメインの定数から差し込む値を使う。

- 対応の表は、そのモジュールの presentation に置く（`ValidationErrorMapping` の実装の bean）。
- パスはリクエストの本文の項目のパス。モジュールの間で同じパスを対応づけると、起動で失敗する。

理由：どの環境でも同じ日本語を返し、形の検証と業務の検証のどちらで見つかっても、消費者に同じ文を返すため。

### 3.4 DB の制約の違反は業務のエラーに変えるか、重複を吸収する

DB の制約でしか守れない規則（一意性など）の違反は、リポジトリが次のどちらかで扱う。

| 重複のとき | 扱い方 |
|------------|--------|
| 業務のエラーにする（同じ品番の登録など） | `ConstraintViolationTranslator` で業務のエラーに変える。制約名とエラーの対応は、各リポジトリの `CONSTRAINTS` に置く |
| 吸収する（同じ通知の受け取り、同じメールの送信の記録など、冪等にする）か、同じトランザクションで処理を続ける | 重複を無視する挿入（`ON CONFLICT DO NOTHING`）にし、挿入したかを真偽値で返す。呼び出し元が真偽値で分ける |

理由：制約の違反を 500 にしないため。PostgreSQL は制約の違反でトランザクション全体を失敗させるので、続ける処理では違反を起こさない挿入にする。

### 3.5 更新の競合は `version`

同時に編集されうる集約の更新は、`version` が合わなければ `CONCURRENT_MODIFICATION`（409）。状態の切り替えは冪等にし、`version` を取らない。

理由：他の利用者の変更を、通知なしに上書きしないため。

## 4. トランザクションと時刻

### 4.1 トランザクションは application

- `@Transactional` は application の service のクラスに付ける。参照だけなら `readOnly = true`。api と presentation には付けない。
- 例外：infrastructure で、別のトランザクションが必要なもの（呼び出し元と別に確定させる記録など）。付けた所に理由を書く。
- 外部サービス（決済、画像の変換、ファイル保存、物流、メールなど）は、トランザクションの外で呼び、タイムアウトを設定する。呼ぶ前の読み取りと呼んだ後の更新を、それぞれのトランザクションにする（第 1.1 節「外部の呼び出しの前の読み取り」）。イベントのリスナーで呼ぶときは、リスナーをトランザクションなし（`@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)`）にする。
- 外で呼べないものは、呼ぶ所と設計書に理由を書く（出荷のメール：[通知モジュール](../design/modules/notification.md)）。

理由：1 つの操作を 1 つのトランザクションにし、境界を層で決めるため。外部サービスを待つ間に DB の接続を持つと、外部サービスが遅いときに接続が尽き、関係のない操作まで止まるため。

### 4.2 現在時刻は `Clock` から

現在時刻は注入した `Clock` から取り（`Instant.now()` を直接呼ばない）、domain には `Instant` を引数で渡す。統合テストは `TestClockConfig` で時計を固定する。

理由：テストで時刻を固定し、時刻で切り替わるデータを確認するため。

## 5. 命名と書き方

### 5.1 Lombok は main だけ

getter・コンストラクターの注入などの定型コードは、main では Lombok で書く。テストのソースセットには Lombok を入れていない。

理由：テストのクラスは、JUnit でコンストラクターの注入に設定が必要なのでフィールドの注入を使い、定型コードもほとんどないため。

### 5.2 名前の形

| 対象 | 形 | 例 |
|------|----|----|
| リポジトリの port（domain） | `XxxRepository` | `OrderRepository` |
| 参照用のリポジトリの port（application） | `XxxQueryRepository` | `OrderQueryRepository` |
| jOOQ の実装 | `JooqXxxRepository` | `JooqOrderRepository` |
| service（レベル A） | 参照は `XxxQueryService`、更新は `XxxCommandService` か用途の名前 ※2 | `OrderQueryService`、`OrderPlacementService` |
| 腐敗防止層の port | `<相手>Directory`（使う層の `port/`） | `OrderDirectory` |
| その実装 | `<相手>ApiDirectory`（`infrastructure/directory/`） | `OrderApiDirectory` |
| イベント | 過去形 | `OrderShipped` |

※2：1 つの集約に用途が複数あるとき、他のモジュールの呼び出しを組み合わせるとき。データを変えずに他のモジュールの結果を組み合わせる service（注文の手続きの見積もりなど）も用途の名前にし、読み取り専用のトランザクションにする。

別のモジュールに同じ単純名のクラスがあってよい（Spring の Bean の名前は完全修飾名にしている）。

### 5.3 インスタンスを作らないクラス

static のメンバーだけのクラス（定数や補助の関数をまとめたもの）は `final` にし、コンストラクタを `private` にする。Lombok の `@NoArgsConstructor(access = AccessLevel.PRIVATE)` でも、手動で書いてもよい。

```java
/** 金額の表示の補助 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MoneyFormat {
    ...
}
```

- 対象は最上位のクラス。enum・record・interface・注釈は対象でない。
- 確認方法：ArchUnit（`src/test/java/com/ecsite/architecture/`）で、static でないメンバーを持たない最上位のクラスが `final` で、コンストラクタが全て `private` であることを確認する。

理由：インスタンスを作っても使い道がなく、作れると使い方を誤る。Checkstyle の `HideUtilityClassConstructor` はソースを読むので、Lombok で作るコンストラクタが見えずに誤って当たる。ArchUnit はコンパイルした後のクラスを読むので、Lombok で書いても手動で書いても同じに確認できる。

## 6. 設定の値

### 6.1 設定の値の型

- 設定は `@ConfigurationProperties` の record で受け、`@Validated` を付ける。型は使う層に置く（画像の変換の設定は `infrastructure/converter/` など）。
- 接頭辞は `ecsite.<モジュール>`、まとまりを分けるときは `ecsite.<モジュール>.<範囲>`（`ecsite.media.storage` など）。モジュールでない共通の設定は `ecsite.<範囲>`（`ecsite.security` など）。
- 環境で変わる値（接続先、資格情報、ID、バケット名）は既定値を持たず必須にする。環境によらない値（上限、時間など）だけ既定値を持つ ※3。
- `@EnableConfigurationProperties` は `@Configuration` のクラスに付ける。条件で切り替える実装（mock など）の設定は、同じ条件の設定のクラスに付ける。
- 定期の処理の間隔も設定の型の項目で受け、`@Scheduled` は SpEL で設定の型の Bean を参照する（`${...}` の既定値を注釈に書かない）。
- 注釈が定数しか取らず、環境で変えない値（定期の処理のロックの上限など）は、設定にせず定数にし、理由をコメントに書く。

※3：本番では設定しない接続の値（S3 互換ストレージの接続先と資格情報。本番は AWS の既定と IAM ロール）は任意にし、本番の動き（path style を使わない）を既定値にする。

理由：設定を忘れたまま、別の環境の値で動き出さないようにするため（足りなければ起動に失敗する）。
