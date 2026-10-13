# バックエンドの書き方

今のコードで守っている書き方。構成の考え方は [バックエンドのアーキテクチャ](architecture.md)、API の規約は [API の規約](../api/conventions.md)。

## 規則の一覧

| 節 | 規則 | 確かめ方 |
|----|------|----------|
| 1.1 | 標準の構成（層とパッケージ） | 自動（ArchUnit） |
| 1.2 | モジュールの外に出すのは `api/` と `event/` だけ | 自動（ModulithTest） |
| 1.3 | 他のモジュールは腐敗防止層で包む | 置き場所は自動（ArchUnit）、口と実装の分け方は人手 |
| 1.4 | パッケージの大きさ | 上限は自動（ArchUnit）、目安は人手 |
| 1.5 | 共通の型と金額 | 金額は自動（ArchUnit）、置き場所と基準は人手 |
| 2.1 | 生成した interface を実装する | 自動（コンパイル）、契約にない endpoint は人手 |
| 2.2 | 応答への変換は手で書く | 人手 |
| 3.1 | 形の検証は presentation、業務の規則は domain | 自動（契約のテスト）、判定の重複は人手 |
| 3.2 | エラーは `XxxErrorCode` と `BusinessException` | 自動（メッセージのテスト）、数字の差し込みは人手 |
| 3.3 | 入力の検証のメッセージ | 自動（検証のメッセージのテスト） |
| 3.4 | DB の制約の違反は業務のエラーに変える | 自動（リポジトリの統合テスト） |
| 3.5 | 更新の競合は `version` | 自動（API の統合テスト） |
| 4.1 | トランザクションは application | 自動（ArchUnit）、infrastructure の例外の理由は人手 |
| 4.2 | 現在時刻は `Clock` から | 人手 |
| 5.1 | Lombok は main だけ | 人手 |
| 5.2 | 名前の形 | 人手 |
| 6.1 | 設定の値の型 | 自動（起動のテスト）、置き場所と接頭辞は人手 |

人手のものは、各 spec の [見直し](../process/review.md) で読む。

## 1. 層とパッケージ

### 1.1 標準の構成

どのモジュールも次の構成にする。要らない層は作らない（イベントを出さないなら `event/` は要らない）。

```
module/<モジュール>/
├── api/                公開の同期の API（@NamedInterface("api")）
├── event/              公開のイベント（@NamedInterface("event")）
├── presentation/       API の入口（生成した interface の実装）と、API の型への変換
├── application/        use case（@Transactional はここ）
│   ├── model/          command・query・view
│   └── port/           application が使う口（参照の Repository、他のモジュールの Directory）
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
| A | 集約は jMolecules で明示し、Repository の口は `domain/<集約>/port/`。command と query の service・Repository を分ける（CQRS） |
| B | domain は振る舞いの少ない型でよく、集約ごとのパッケージは作らない。口はすべて `application/port/`。CQRS なし。`application/model/` は必要なときだけ |

理由：どのモジュールでも同じ場所に同じものを見つけられるようにし、domain を永続化と API の形から切り離すため。

### 1.2 モジュールの外に出すのは `api/` と `event/` だけ

他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。

理由：モジュールの中を、他のモジュールに影響なく変えられるようにするため。

### 1.3 他のモジュールは腐敗防止層で包む

- 他のモジュールの `api/` を呼ぶときは、口（`<相手>Directory` の interface と、相手の情報を写す自分の型）を使う層の `port/` に置き、実装は `infrastructure/directory/` に置く。
- 相手の型を内部に持ち込まない。
- モジュールの直下に、クラスが 1 つだけのパッケージ（`<相手>dir` など）を作らない。

理由：相手の変更を、実装の 1 か所で受け止めるため。

### 1.4 パッケージの大きさ

1 つのパッケージの直下の `.java` は 10 を目安、20 を上限にする（`package-info.java` とテストは数えない）。目安を超えて分けないときは、下の表に理由を書く。

| パッケージ | 数 | 分けない理由 |
|------------|----|--------------|
| `catalog.infrastructure.persistence` | 13 | jOOQ の Repository と、それだけが使う package-private のクラス。分けるとクラスを公開しなければならない |

### 1.5 共通の型

- モジュールに共通の型は `common/model/` に置く。`base/`：集約の土台（`AggregateRoot`、`DomainEvent`）、`value/`：値（`Money`、`BoundedText`、`Operator`）。
- 共通の値にするのは、2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つものだけ。1 つのモジュールだけのものはその `vo/` に置く。
- 金額は、モジュールの内部と公開の型（`api/` の record、command）で `Money` を使う。整数で持つのは presentation と infrastructure（DB）だけ。

理由：同じ規則を 1 か所に置きつつ、共通の型を増やしすぎてモジュールを結びつけないため。

## 2. API の実装

### 2.1 生成した interface を実装する

Controller は OpenAPI から生成した interface（`XxxApi`）を実装する。パスと入力の形は interface に任せ、`@RequestMapping` を書かない。

理由：契約（`api/openapi/`）を正本にし、実装とずれないようにするため。

### 2.2 応答への変換は手で書く

- 変換はモジュールごとの mapper（`XxxMapper`）に手で書く。変換のライブラリーは使わない。
- 生成したモデルは、引数なしのコンストラクターと項目名のメソッドで組み立てる（`new OrderLine().productCode(...)`）。位置で並べるコンストラクターは使わない。
- 入れ子の型（明細、金額、住所など）は型ごとに private メソッドに分け、上位のメソッドには項目の並びだけを残す。
- 自分で定義した record は、引数が多ければ 1 行に 1 つ書く。

理由：項目の取り違えを防ぎ、変換を読みやすくするため。

## 3. 入力の検証とエラー

### 3.1 形の検証は presentation、業務の規則は domain

- 長さ・必須・形式は OpenAPI の制約（生成した Bean Validation）、業務の規則（重複、状態、期間の前後など）は domain で確かめる。
- リクエストの数値の項目には範囲（`minimum`・`maximum`）、文字の項目には長さ（`maxLength`）か形式（`pattern`・`format`・`enum`）を書く。
- 同じ項目の契約の制約、ドメインの上限の定数、DB の列の長さは一致させる ※1。
- 文字の「空白でない」「長さを code point で数える」の判定は `BoundedText` で行い、各ドメインは上限とエラーの種類だけを持つ。

※1：`OpenApiRequestConstraintTest`（制約の漏れ）と `FieldConstraintContractTest`（契約・ドメイン・DB の一致）で確かめる。項目を足したら、テストの表にも足す。

理由：契約に書ける検証は契約に、業務の規則は 1 か所に置くため。

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- 業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum）で表し、`BusinessException.of(...)` で投げる。定数は種類（`ErrorType`）と、いつ起きるかの Javadoc を持つ。
- メッセージのキーは `ecsite.<モジュール>.<定数の名前の小文字>`、文は `messages.properties` に置く（例：`CART_ITEM_LIMIT_EXCEEDED` → `ecsite.cart.cart_item_limit_exceeded`）。
- 文に上限などの値を入れるときは、数字を書かず名前で差し込み（`{max} 品目までです`）、値はドメインの定数から渡す。
- レスポンスへの変換は `GlobalExceptionHandler` だけが行う。

理由：フロントが `errorCode` で分岐でき、画面に出す日本語を 1 か所で管理し、規則の値が変わっても文が取り残されないようにするため。

### 3.3 入力の検証のメッセージ

入力の検証の文は、枠組みの既定の文（JVM のロケールで言語が変わる）を使わず、`ecsite.validation.<種類>`（`notnull`、`size`、`min` など）の文を `messages.properties` から取る。上限・下限は `{max}`、`{min}`、`{value}` で差し込み、知らない種類は `ecsite.validation.invalid`。

理由：どの環境でも同じ日本語を返すため。

### 3.4 DB の制約の違反は業務のエラーに変える

DB の制約でしか守れない規則（一意性など）の違反は、リポジトリが `ConstraintViolationTranslator` で業務のエラーに変える。制約名とエラーの対応は、各リポジトリの `CONSTRAINTS` に置く。

理由：制約の違反を 500 にしないため。

### 3.5 更新の競合は `version`

同時に編集されうる集約の更新は、`version` が合わなければ `CONCURRENT_MODIFICATION`（409）。状態の切り替えは冪等にし、`version` を取らない。

理由：他の人の変更を黙って上書きしないため。

## 4. トランザクションと時刻

### 4.1 トランザクションは application

- `@Transactional` は application の service のクラスに付ける。参照だけなら `readOnly = true`。api と presentation には付けない。
- 例外：infrastructure で、別のトランザクションが必要なもの（呼び出し元と別に確定させる記録など）。付けた所に理由を書く。

理由：1 つの操作を 1 つのトランザクションにし、境界を層で決めるため。

### 4.2 現在時刻は `Clock` から

現在時刻は注入した `Clock` から取り（`Instant.now()` を直接呼ばない）、domain には `Instant` を引数で渡す。統合テストは `TestClockConfig` で時計を固定する。

理由：テストで時刻を固定し、時刻で切り替わるデータを確かめるため。

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
| 腐敗防止層の口 | `<相手>Directory`（使う層の `port/`） | `OrderDirectory` |
| その実装 | `<相手>ApiDirectory`（`infrastructure/directory/`） | `OrderApiDirectory` |
| イベント | 過去形 | `OrderPlaced` |

※2：1 つの集約に用途が複数あるとき、他のモジュールの呼び出しを組み合わせるとき。

別のモジュールに同じ単純名のクラスがあってよい（Spring の Bean の名前は完全修飾名にしている）。

## 6. 設定の値

### 6.1 設定の値の型

- 設定は `@ConfigurationProperties` の record で受け、`@Validated` を付ける。型は使う層に置く（画像の変換の設定は `infrastructure/converter/` など）。
- 接頭辞は `ecsite.<モジュール>`、まとまりを分けるときは `ecsite.<モジュール>.<範囲>`（`ecsite.media.storage` など）。モジュールでない共通の設定は `ecsite.<範囲>`（`ecsite.security` など）。
- 環境で変わる値（接続先、資格情報、ID、バケット名）は既定値を持たず必須にする。環境によらない値（上限、時間など）だけ既定値を持つ ※3。

※3：本番では設定しない接続の値（S3 互換ストレージの接続先と資格情報。本番は AWS の既定と IAM ロール）は任意にし、本番の動き（path style を使わない）を既定値にする。

理由：設定を忘れたまま、別の環境の値で動き出さないようにするため（足りなければ起動に失敗する）。
