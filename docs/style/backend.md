# バックエンドの書き方

原則は [constitution](../constitution.md) 第 4〜9 節。本書は、今のコードで守っている書き方を書く。

## 1. 層とパッケージ

### 1.1 標準の構成

- **規則**：どのモジュールも、次の構成にする。レベル（constitution 4.2）で変わるのは中身だけで、層とパッケージの名前は同じ。要らない層は作らない（例：イベントを出さないモジュールに `event/` は要らない）。

```
module/<モジュール>/
├── api/                公開の同期の API（@NamedInterface("api")）。他のモジュールが呼ぶ
├── event/              公開のイベント（@NamedInterface("event")）
├── presentation/       API の入口（生成した interface の実装）と、API の型への変換
├── application/        use case（取引の境界。@Transactional はここ）
│   ├── model/          command・query・view
│   └── port/           application が使う口（参照の Repository、他のモジュールの Directory）
├── domain/             業務の型。エラーの定義（<モジュール>ErrorCode）
│   └── <集約>/         集約、vo/（値）、port/（Repository、domain が使う Directory）
└── infrastructure/     port の実装
    ├── persistence/    jOOQ の Repository と、その部品
    ├── directory/      他のモジュールの api を呼ぶ Directory の実装（腐敗防止層）
    └── <外部>/          外部の仕組み（storage、converter、cache など）
```

| 層 | 依存してよい先 |
|----|----------------|
| api・event | application、domain、common（infrastructure に依存しない） |
| presentation | application、domain（エラーの定義と値の型）、common（infrastructure に依存しない） |
| application | domain、common |
| domain | common（レベル A は Spring・jOOQ・他の層に依存しない） |
| infrastructure | application の port、domain、common（presentation に依存しない） |

| レベル | 中身の違い |
|--------|------------|
| A | domain は何にも依存しない。集約は jMolecules で明示し、Repository の口は `domain/<集約>/port/`。CQRS（command と query の service・Repository を分ける）。`application/model/` に command・query・view |
| B | domain の型は振る舞いの少ない型でよく、集約ごとのパッケージを作らなくてよい。口はすべて `application/port/` に置き、`domain/<集約>/port/` は作らない。CQRS を求めない。`application/model/` は要るときだけ |

- **理由**：読む人が、どのモジュールでも同じ場所に同じものを見つけられるようにするため（constitution 4.2）。domain を永続化と API の形から切り離すため。
- **確かめ方**：自動。`src/test/java/com/ecsite/architecture/` の ArchUnit の規則（層の依存の向き、モジュールの直下のパッケージの名前）。

### 1.2 モジュールの外に出すのは `api/` と `event/` だけ

- **規則**：他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。
- **理由**：モジュールの中を、他のモジュールに影響を与えずに変えられるようにするため（constitution 4.1）。
- **確かめ方**：自動。[ModulithTest](../../backend/ecsite/src/test/java/com/ecsite/modulith/ModulithTest.java)。

### 1.3 他のモジュールは腐敗防止層で包む

- **規則**：他のモジュールの `api/` を呼ぶときは、口（`<相手>Directory` の interface と、相手の情報を写す自分の型）を、それを使う層の `port/`（`application/port/` か `domain/<集約>/port/`）に置く。実装は `infrastructure/directory/` に置く。相手の型を内部に持ち込まない。モジュールの直下に、部品が 1 つだけのパッケージ（`<相手>dir` など）を作らない。
- **理由**：相手のモジュールの変更を、実装の 1 か所で受け止めるため。置き場所を決め、同じ種類の部品をまとめるため。
- **確かめ方**：自動（ArchUnit：`*dir` のパッケージがない、モジュールの直下のパッケージの名前）。口と実装の分け方は人手（見直し）。

### 1.4 パッケージの大きさ

- **規則**：1 つのパッケージの直下の `.java` は、10 を目安、20 を上限にする。`package-info.java` とテストは数えない。目安を超えたら、use case のまとまりや種類で分けるかを考え、分けないなら下の表に理由を書く。
- **理由**：1 つのパッケージを一覧で見渡せる大きさに保つため。
- **確かめ方**：上限は自動（ArchUnit）。目安は人手（見直しで下の表と照らす）。

| パッケージ | ファイルの数 | 分けない理由 |
|------------|--------------|--------------|
| `catalog.infrastructure.persistence` | 13 | jOOQ の Repository と、それらだけが使う package-private の部品（条件、表示の判定、画像の URL など）。分けると、部品を公開しなければならない |

### 1.5 共通の型

- **規則**：モジュールに共通の型は `common/model/` に、種類ごとに分けて置く。`base/`：集約の土台（`AggregateRoot`、`DomainEvent`）。`value/`：値の型（`Money`、`BoundedText`、`Operator`）。値を共通の型にするのは、2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つものだけ。1 つのモジュールだけのものは、そのモジュールの `vo/` に置く。
- **規則**：金額は、モジュールの内部とモジュールの間の公開の型（`api/` の record、command）で `Money` を使う。整数で持つのは、presentation（API の型との変換）と infrastructure（DB）だけ。
- **理由**：同じ規則（文字の数え方、金額の範囲）を 1 か所に置くため。共通の型を増やしすぎて、モジュールの間の結びつきを強くしないため。
- **確かめ方**：金額は自動（ArchUnit：`api` の record と command に、金額の名前の整数の項目がない）。置き場所と基準は人手（見直し）。

## 2. API の実装

### 2.1 生成した interface を実装する

- **規則**：Controller は、OpenAPI から生成した interface（`XxxApi`）を実装する。パスと入力の形は interface に任せ、Controller に `@RequestMapping` を書かない。
- **理由**：API の契約（`api/openapi/`）を正本にし、実装と契約がずれないようにするため（constitution 9）。
- **確かめ方**：自動（コンパイル）。契約にない endpoint を足していないかは人手（見直し）。

### 2.2 応答への変換は手で書く

- **規則**：生成したモデルへの変換は、モジュールごとの mapper（`XxxMapper`）に手で書く。組み立ては引数なしのコンストラクターと項目名のメソッド（`new OrderLine().productCode(...)`）。入れ子の型は private メソッドに分ける。
- **理由**：項目の取り違えを防ぎ、変換を読みやすくするため（constitution 4.5）。
- **確かめ方**：人手（見直し）。

## 3. 入力の検証とエラー

### 3.1 形の検証は presentation、業務の規則は domain

- **規則**：長さ・必須・形式は OpenAPI の制約（生成した interface の Bean Validation）で、業務の規則（重複、状態、期間の前後など）は domain で確かめる。
- **規則**：リクエストの数値の項目（版、件数、増減、金額、大きさ）には範囲（`minimum`・`maximum`）を、文字の項目には長さ（`maxLength`）か形式（`pattern`・`format`・`enum`）を書く。同じ項目の契約の制約、ドメインの上限の定数、DB の列の長さは一致させる。
- **規則**：文字の項目の「空白でない」「長さの上限を code point で数える」の判定は、`common/model/value/BoundedText` で行う。各ドメインは上限の定数とエラーの種類だけを持つ。
- **理由**：契約に書ける検証を契約に、業務の規則を 1 か所に置くため（constitution 9）。同じ上限を 3 か所に手で書いても、食い違いに気づけるようにするため。
- **確かめ方**：自動。`OpenApiRequestConstraintTest`（制約の漏れ）と `FieldConstraintContractTest`（契約・ドメイン・DB の一致。項目を足したら、テストの表にも足す）。判定の重複は人手（見直し）。

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- **規則**：業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum）で表し、`BusinessException.of(...)` で投げる。enum の定数は、種類（`ErrorType`）と、いつ起きるかの Javadoc を持つ。エラーは enum の 1 か所で定義し、メッセージの定数のクラスは作らない。レスポンスへの変換は `GlobalExceptionHandler` だけが行う。
- **規則**：メッセージのキーは enum から決まる：`ecsite.<モジュール>.<定数の名前の小文字>`（例：`CartErrorCode.CART_ITEM_LIMIT_EXCEEDED` → `ecsite.cart.cart_item_limit_exceeded`）。文は `messages.properties` に置く。2 つの定数が同じ文なら、キーを 2 つ書く。
- **規則**：文に業務の規則の値（上限の数など）を入れるときは、数字を書かずに名前で差し込む（`カートに入れられるのは {max} 品目までです。`）。値は、例外を投げるときにドメインの定数から渡す。
- **理由**：フロントが `errorCode` で分岐でき、画面にそのまま出せる日本語を 1 か所で管理するため（constitution 9）。規則の値が変わったときに、文が取り残されないようにするため。
- **確かめ方**：自動。`MessageContractTest`（enum の定数と `messages.properties` のキーが過不足なく対応する）、[ErrorResponseTest](../../backend/ecsite/src/integrationTest/java/com/ecsite/common/error/ErrorResponseTest.java)。差し込みに数字を書いていないかは人手（見直し）。

### 3.5 入力の検証のメッセージ

- **規則**：入力の検証のエラーの文は、枠組みの既定の文（JVM のロケールで言語が変わる）を使わず、制約の種類から決めたキー `ecsite.validation.<種類の小文字>`（`notnull`、`size`、`min` など）の文を、`messages.properties` から取る。上限・下限は名前（`{max}`、`{min}`、`{value}`）で差し込む。知らない種類は `ecsite.validation.invalid`。項目の名前は API の項目名。
- **理由**：どの環境でも同じ日本語を返し、業務のエラーと同じ所で文を管理するため。
- **確かめ方**：自動（検証のメッセージのテスト。種類ごとの文と、ロケールによらないこと）。

### 3.3 DB の制約の違反は、リポジトリで業務のエラーに変える

- **規則**：一意性など、同時の操作で DB の制約でしか守れない規則の違反は、リポジトリが [ConstraintViolationTranslator](../../backend/ecsite/src/main/java/com/ecsite/common/persistence/ConstraintViolationTranslator.java) で業務のエラーに変える。制約名と業務のエラーの対応は、各リポジトリの `CONSTRAINTS` に置く。
- **理由**：制約の違反を 500 にしないため（constitution 9）。
- **確かめ方**：自動（各リポジトリの統合テストで、違反が業務のエラーになることを確かめる）。

### 3.4 更新の競合は `version`

- **規則**：同時に編集されうる集約の更新は、`version` が一致しなければ `CONCURRENT_MODIFICATION`（409）にする。状態の切り替えは冪等にし、`version` を取らない。
- **理由**：他の人の変更を黙って上書きしないため（constitution 9）。
- **確かめ方**：自動（API の統合テスト）。

## 4. トランザクションと時刻

### 4.1 トランザクションは application

- **規則**：`@Transactional` は application の service のクラスに付ける。参照だけの service は `readOnly = true`。api（ほかのモジュールに見せる口）や presentation には付けない。
- **例外**：infrastructure で、外の仕組みの都合で別のトランザクションが要るもの（例：呼び出し元と別に確定させる記録）。付けた所に理由を書く。
- **理由**：1 つの操作を 1 つのトランザクションにし、境界を層で決めるため。
- **確かめ方**：自動（ArchUnit：`@Transactional` は application と infrastructure だけ）。infrastructure の例外の理由は人手（見直し）。

### 4.2 現在時刻は `Clock` から

- **規則**：現在時刻は注入した `Clock` から取る（`Instant.now()` を直接呼ばない）。domain には `Instant` を引数で渡す。
- **理由**：テストで時刻を固定し、時刻で切り替わるデータを確かめるため（constitution 5-6）。
- **確かめ方**：人手（見直し）。統合テストは `TestClockConfig` で時計を固定する。

## 5. 命名と書き方

### 5.1 Lombok は main だけ

- **規則**：getter・コンストラクターの注入などの定型コードは、main では Lombok で書く。テストのソースセットでは使わない。
- **理由**：constitution 4.5。
- **確かめ方**：人手（見直し）。

### 5.2 名前の形

| 対象 | 形 | 例 |
|------|----|----|
| リポジトリの port（domain） | `XxxRepository` | `TaxCategoryRepository` |
| 参照用のリポジトリの port（application） | `XxxQueryRepository` | `TaxCategoryQueryRepository` |
| jOOQ の実装 | `JooqXxxRepository` | `JooqTaxCategoryRepository` |
| service（レベル A） | `XxxCommandService`、`XxxQueryService` | `TaxCategoryCommandService` |
| 他モジュールの腐敗防止層の口 | `<相手>Directory`（使う層の `port/` の interface） | `TaxDirectory` |
| その実装 | `<相手>ApiDirectory`（`infrastructure/directory/`） | `TaxApiDirectory` |

- 別のモジュールに同じ単純名のクラスがあってよい（腐敗防止層の実装など）。Spring の部品の名前は完全修飾名にしている（`EcSiteApplication`）。
| イベント | 過去形 | `OrderPlaced` |

- **確かめ方**：人手（見直し）。

## 6. 設定の値

### 6.1 設定の値の型

- **規則**：設定の値は `@ConfigurationProperties` の record で受け、`@Validated` を付ける。接頭辞は `ecsite.<モジュール>.<範囲>`（モジュールでない共通の設定は `ecsite.<範囲>`）。型は、それを使う層に置く（例：画像の変換の設定は `infrastructure/converter/`）。
- **規則**：環境ごとに変わる値（接続先、資格情報、ID、バケットの名前）は既定値を持たず、必須（`@NotBlank`・`@NotNull`）にする。環境によらない値（上限、時間など）だけ既定値を持つ。
- **理由**：設定を忘れたまま、別の環境の値で動き出さないようにするため（足りなければ起動に失敗する）。
- **確かめ方**：自動（必須の値がないと起動に失敗するテスト）。置き場所と接頭辞は人手（見直し）。
