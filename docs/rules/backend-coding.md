# バックエンドの書き方

今のコードで守っている書き方。構成の考え方は [バックエンドのアーキテクチャ](../design/backend-architecture.md)、API の規約は [API の規約](api-conventions.md)。

## 規則の一覧

| 節 | 規則 | 確認方法 |
|----|------|----------|
| 1.1 | 標準の構成（層とパッケージ） | モジュールの中の層の間は、依存の表にある全ての組と、表にない層・パッケージへの依存が自動（ArchUnit）。`api/` に interface がないこと、`api/` の型を `api/` の外が実装・継承しないこと、domain のパッケージに循環がないことも自動（ArchUnit）。presentation が依存する domain をエラーの定義と値の型に限ることはレビュー（集約に依存しないことは次の行で自動） |
| 1.1 | 参照の service は view を返し、presentation は集約を受け取らない（レベル A） | presentation が集約に依存しないことは自動（ArchUnit）、外部の呼び出しの前の読み取りの置き場所はレビュー |
| 1.1 | 公開の API は、`api/` のクラスが application の service に委ねる。application は `api` に依存しない | `api/` のクラスが service に委ねることはレビュー、application が `api` に依存しないことは自動（ArchUnit） |
| 1.1 | presentation と `application/model/` は、application の `port/` に依存しない | 自動（ArchUnit） |
| 1.1 | イベントの置き場所と送り出し | レベル A の application が送り出さないことは自動（ArchUnit）、置き場所はレビュー |
| 1.1 | 共通コードと設定はモジュールに依存しない。共通コードとモジュールは設定に依存しない | 自動（ArchUnit） |
| 1.2 | モジュールの外に出すのは `api/` と `event/` だけ | 自動（ModulithTest） |
| 1.3 | 他のモジュールは腐敗防止層で包む。列挙は `switch` 式で変換する | 置き場所と、他のモジュールの `api` を使うのが `infrastructure/directory/` だけであること（許可の一覧の 1 件を除く）、実装が public でないことは自動（ArchUnit）。変換の書き忘れは自動（コンパイル）で、全ての値の対応は変換ごとの単体テストで確認する。`valueOf(x.name())` と、domain・永続化以外での名前の文字列からの `valueOf`・`fromValue` を使わないことは自動（`scripts/ci/java-rules.mjs`）。port と実装の分け方はレビュー |
| 1.4 | パッケージの大きさ | 上限は自動（ArchUnit）。目安を超えるパッケージの一覧と数を第 1.4 節の表と照合することは自動（`ModuleStructureTest`）。表の「分けない理由」の妥当性はレビュー |
| 1.5 | 共通の型と金額 | 金額は自動（ArchUnit）、置き場所と基準、金額の計算はレビュー |
| 2.1 | 生成した interface を実装する。`@RestController` は presentation だけに付ける（`MeController` を除く） | `@RestController` の置き場所は自動（ArchUnit）、実装は自動（コンパイル）、契約にない endpoint はレビュー |
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
| 5.4 | 書式は IntelliJ の整形を推奨する（強制しない） | なし（強制しない） |
| 6.1 | 設定の値の型 | 自動（起動のテスト）。`@EnableConfigurationProperties` の置き場所と、`@Configuration` が infrastructure にあること、presentation が設定の値の型に依存しないことは自動（ArchUnit）。型の置き場所と接頭辞、`@Scheduled` の間隔を設定の型から取ることはレビュー |
| 6.1 | 秘密の値を持つ設定の record は、`toString` で値を伏せる | 単体テスト（`toString` に秘密の値が含まれないこと）、上書きの書き忘れはレビュー |
| 7.1 | null の扱い（JSpecify の `@Nullable`） | 自動（NullAway、`scripts/ci/java-rules.mjs`）、`@Nullable` と `Optional` の選び方はレビュー |
| 7.2 | 検査を抑えるとき | `@SuppressWarnings` の理由のコメントはレビュー |
| 8.1 | 新しいマイグレーションは、前のリリースのアプリが動き続けられる変更だけにする（表・列の削除と改名などは 3 段に分ける） | CI（`migrations`）。3 段に分ける手順の妥当性はレビュー |

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
├── domain/             業務の型。エラーの定義（<モジュール>ErrorCode。集約ごとに分けてよい）
│   └── <集約>/         集約、vo/（値）、port/（Repository、domain が使う Directory）
└── infrastructure/     port の実装
    ├── <モジュール>Config  設定のクラス（設定の値の型を有効にする、domain の部品を Bean にする。第 6.1 節）
    ├── persistence/    jOOQ の Repository と、それが使うクラス
    ├── directory/      他のモジュールの api を呼ぶ Directory の実装（腐敗防止層）
    └── <外部>/          外部の仕組み（storage、converter、cache など）
```

| 層 | 依存してよい先 |
|----|----------------|
| api・event | application、domain、common |
| presentation | application（`port/` を除く）、domain（エラーの定義と値の型）、common |
| application | domain、common、event（自分のモジュールのイベントを発行する。他のモジュールのイベントを購読する） |
| domain | common、自分のモジュールの event（レベル A は Spring・jOOQ にも依存しない） |
| infrastructure | application の port と model（port が返す view）、設定の値の型（設定のクラスが有効にしたもの）、domain、common |
| common | モジュールに依存しない |
| config | common。モジュールに依存しない |

どの層も、config に依存しない。

| レベル | 中身の違い |
|--------|------------|
| A | 集約は jMolecules で明示し、Repository の port は `domain/<集約>/port/`。command と query の service・Repository を分ける（CQRS）。例外：状態を DB の更新の条件で守るモジュール（今は在庫だけ）は、集約のクラスを持たなくてよい（理由は [バックエンドのアーキテクチャ](../design/backend-architecture.md) 第 2 節 ※3） |
| B | domain は振る舞いの少ない型でよく、集約ごとのパッケージは作らない。port は全て `application/port/`。CQRS なし。`application/model/` は必要なときだけ。クラスが多いときは、用途で domain を分けてよい（`domain/<用途>/`。reporting の `domain/snapshot/` と `domain/sales/`） |

層の中の役割：

- **参照と更新（レベル A）**：参照の service は、`application/model/` の view を返し、集約を返さない。presentation は集約を受け取らない。画面の操作の可否は、view を作る所で集約のメソッドを呼んで view に入れる。
- **外部の呼び出しの前の読み取り**：更新の流れで、外部のサービスを呼ぶ前に集約の状態を読む処理（外部への要求を作る）も、参照の service に置き、要求の型か view を返す。外部の呼び出しをトランザクションの外で行い、呼んだ後の更新を更新の service で行う。
- **イベント**：送り出し方は [バックエンドのアーキテクチャ](../design/backend-architecture.md) に従う。domain は自分のモジュールの `event/` を参照してよい（集約が公開のイベントを記録するため）。モジュールの中だけのイベントは、レベル A は集約のパッケージ、レベル B は application に置く。
- **公開の API**：`api/` は、interface を持たない `@Component` のクラスで、application の service に委ねる。`api/` の型を `api/` の外が実装・継承しない。service は `api/` の型に依存しない（`api/` のクラスが application の view を `api/` の型に変換する）。
- **domain の直下**：モジュールの domain の直下には、エラーの定義と、複数の集約が使う値を置く（商品とカテゴリが使う表示順の再採番など）。1 つの集約だけが使う値は、その集約の `vo/` に置く。ただし、値が集約のパッケージの型（列挙など）を使うときは、集約のパッケージの直下に置く。`vo/` に置くと、`vo/` と集約のパッケージが互いに依存して循環するためである（例：order の `BuyerProfile`、`Cancellation`）。

理由：どのモジュールでも同じ場所に同じものを見つけられるようにし、domain を永続化と API の形に依存させないため。参照の型を集約から分けると、集約の中を変えても presentation と公開の API が変わらない。

### 1.2 モジュールの外に出すのは `api/` と `event/` だけ

他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。

理由：モジュールの中を、他のモジュールに影響なく変えられるようにするため。

### 1.3 他のモジュールは腐敗防止層で包む

- 他のモジュールの `api/` を呼ぶときは、port（`<相手>Directory` の interface と、相手の情報を変換して持つ自分の型）を使う層の `port/` に置き、実装は `infrastructure/directory/` に置く。
- 他のモジュールの `api/` を使うのは `infrastructure/directory/` だけにする。例外は 1 件で、`member.api.CurrentMemberIds` を presentation から使う ※1。
- 相手の型を内部で使わない。
- 腐敗防止層の実装（`<相手>ApiDirectory`）は public にしない。理由：使う側は port の interface を通して使うため。
- モジュールの直下に、クラスが 1 つだけのパッケージ（`<相手>dir` など）を作らない。
- 列挙を別の列挙に変換するとき（相手の型と自分の型、ドメインとイベント、ドメインと生成したモデル）は、`switch` 式で値ごとに書く。`valueOf(x.name())`、`fromValue(x.name())` は使わない。
- モジュールの間とレイヤーの間で、列挙を名前の文字列で受け渡さない。command・query・view・api の型は列挙で持つ。名前の文字列から列挙にするのは、DB の文字列を読む `infrastructure/persistence/` と、ドメインの中だけ（`java-rules` が、それ以外の `valueOf`・`fromValue` を見つける）。

理由：相手の変更の影響を、実装の 1 か所に限るため。`switch` 式は列挙の全ての値を書かないとコンパイルに失敗するので、片方に値を追加して変換を書き忘れると気づける（名前で変換すると、実行するまで気づけない）。

※1：`CurrentMemberIds` は、要求の token から会員の ID を得る、API の入口（presentation）のためのクラスで、会員のモジュールが presentation 向けに公開している。会員の ID は presentation が token から読み、application へ引数で渡す。会員の腐敗防止層（port と実装）をモジュールごとに作ると、読み取りだけのために同じ port と実装の組が増える。

### 1.4 パッケージの大きさ

1 つのパッケージの直下の `.java` は 10 を目安、20 を上限にする（`package-info.java`、テスト、生成したコード（OpenAPI と jOOQ）は数えない）。目安を超えて分けないときは、下の表に理由を書く。

| パッケージ | 数 | 分けない理由 |
|------------|----|--------------|
| `catalog.infrastructure.persistence` | 14 | jOOQ の Repository と、それだけが使う package-private のクラス。分けるとクラスを公開しなければならない |
| `config.security` | 11 | 認証と認可の設定。filter chain の設定のクラスが組み立てるクラスは package-private。対象は認可の対応表、認可の判定、契約の操作の一覧で、対応表は管理用と店舗用がある。分けるとクラスを公開しなければならない |

目安を超えるパッケージの一覧と数は、テスト（`ModuleStructureTest`）が表と照合する。表にない、目安を超えるパッケージ、目安以下のパッケージの行、数の違いのどれかがあると、テストが失敗する。

テストが表を読むので、表の書式を変えない。1 列目は、バッククォートで囲んだパッケージ名（`com.ecsite.` と `module.` を除く。例：`catalog.infrastructure.persistence`）、2 列目は数にする。

テストは節の見出し（`### 1.4`）で表を探す。節の番号を変えるときは、`ModuleStructureTest` の `PACKAGE_SIZE_HEADING` も修正する。

### 1.5 共通の型

- 共通コード（`common/`）には、2 つ以上のモジュールが使うものだけを置く。1 つのモジュールだけが使うものは、そのモジュールに置く。例外は横断の仕組み（エラーの応答、ログ、入力の検証のメッセージ、認証の情報の取り出し）で、全てのモジュールの要求の処理に関わり、どのモジュールにも属さないので、`common/` に置く。
- モジュールに共通の型は `common/model/` に置く。`base/`：集約の基底（`AggregateRoot`、`DomainEvent`、`AggregateGuards`）、`value/`：値（`Money`、`BoundedText`、`BusinessTime`、`Operator`）。
- 共通の値にするのは、2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つものだけ。1 つのモジュールだけのものは、そのモジュールの domain に置く。置き場所（集約の `vo/` か domain の直下）は第 1.1 節のとおり。
- 金額は、モジュールの内部と公開の型（`api/` の record、command、view）で `Money` を使う。整数で持つのは presentation、infrastructure（DB）、設定の値の型だけ。設定の値の金額は、使う所で `Money` にする。
- 金額の掛け算・割り算は、`Money` の操作と税の計算（`TaxCalculator`）の中だけで行う。端数が出る計算は、端数の扱い（切り捨て など）をメソッドで明示する。
- 例外として、商品の一覧の税込価格の絞り込みと並べ替えは、SQL で税込価格を計算する。計算するクラスは `catalog.infrastructure.persistence.PriceInclTaxField` で、式は 税抜価格 × (100 + 税率) ÷ 100 の切り捨て。理由：税区分ごとに税率が違い、税抜の順と税込の順が一致しない。一覧は DB で `LIMIT`・`OFFSET` を使ってページングするので、絞り込みと並べ替えも DB で行う必要がある。`TaxCalculator` と同じ結果になることは、端数の境界値を含めて `PriceInclTaxFieldTest` で確認する。

理由：同じ規則を 1 か所に置きつつ、共通の型を増やしすぎて、モジュールが互いに依存しないようにするため。金額の計算を複数の場所に書くと、端数の扱いが場所によって変わる。

## 2. API の実装

### 2.1 生成した interface を実装する

Controller は OpenAPI から生成した interface（`XxxApi`）を実装する。パスと入力の形は interface に任せ、`@RequestMapping` を書かない。

例外：外部のサービスとのやり取りのエンドポイント（決済・物流の通知、mock の支払画面と物流の画面。[API の規約](api-conventions.md) 第 4.1 節）は EC の API ではないので、OpenAPI に書かず、そのサービスの型と一緒に `infrastructure` に置く。`@RestController` は presentation だけに付けるので、`@Controller` と `ResponseEntity` で書く（[決済モジュール](../design/modules/payment.md)、[出荷モジュール](../design/modules/shipping.md)）。

例外：ログインした人の情報を返す操作（`MeController`）は、モジュールの presentation でなく `config/security/` に置く。認可の設定と同じ権限の変換（`PermissionConverter`）を使い、どのモジュールにも属さないため。`@RestController` の置き場所の検査（ArchUnit）は、`MeController` だけを完全修飾名で除く。

理由：契約（`api/openapi/`）を正本にし、実装とずれないようにするため。

### 2.2 応答への変換は手動で書く

- 変換はモジュールごとの mapper（`XxxMapper`）に手動で書く。変換のライブラリーは使わない。
- 生成したモデルは、引数なしのコンストラクターと項目名のメソッドで組み立てる（`new OrderLine().productCode(...)`）。位置で並べるコンストラクターは使わない。項目が 1 つの型（作成した ID など）も同じ。
- 作成の応答（201、Location、作成した ID）は、`common/presentation` の共通の補助で作る。`Location` はホストを含まない相対の URL（`/api/...`）で、プロキシのヘッダーの扱いによらず同じ値になる。
- 入れ子の型（明細、金額、住所など）は型ごとに private メソッドに分け、上位のメソッドには項目の並びだけを残す。
- 自分で定義した record は、引数が多ければ 1 行に 1 つ書く。

理由：項目の取り違えを防ぎ、変換を読みやすくするため。

## 3. 入力の検証とエラー

### 3.1 形の検証は presentation、業務の規則は domain

- 長さ・必須・形式は OpenAPI の制約（生成した Bean Validation）、業務の規則（重複、状態、期間の前後など）は domain で確認する。
- リクエストの数値の項目には範囲（`minimum` と `maximum` の両方）、文字の項目には長さ（`maxLength`）か形式（`pattern`・`format`・`enum`）を書く。上限を書かないのは、計算に使わない項目（楽観ロックの版、ページ番号、絞り込みの値など）だけで、契約のテストの一覧に理由を書く。理由：上限のない数値を計算に使うと、桁あふれや DB の範囲の超過で 500 になるため。
- 同じ項目の契約の制約、ドメインの上限の定数、DB の列の長さは一致させる ※2。
- 文字の「空白でない」「長さを code point で数える」の判定は `BoundedText` で行い、各ドメインは上限とエラーの種類だけを持つ。

※2：`OpenApiRequestConstraintTest`（制約の漏れ）と `FieldConstraintContractTest`（契約・ドメイン・DB の一致）で確認する。項目を追加したら、テストの表にも追加する。

理由：契約に書ける検証は契約に、業務の規則は 1 か所に置くため。

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- 業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum）で表し、`BusinessException.of(...)` で投げる。定数は種類（`ErrorType`）と、いつ起きるかの Javadoc を持つ。
- メッセージのキーは `ecsite.<モジュール>.<定数の名前の小文字>`、文は `messages.properties` に置く（例：`TOO_MANY_CART_ITEMS` → `ecsite.cart.too_many_cart_items`）。
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
| service（レベル A） | 参照は `XxxQueryService`、更新は `XxxCommandService` か用途の名前 ※3 | `OrderQueryService`、`OrderPlacementService` |
| 腐敗防止層の port | `<相手>Directory`（使う層の `port/`） | `OrderDirectory` |
| その実装 | `<相手>ApiDirectory`（`infrastructure/directory/`） | `OrderApiDirectory` |
| イベント | 過去形 | `OrderShipped` |

※3：用途の名前を使うのは、1 つの集約に用途が複数あるとき、他のモジュールの呼び出しを組み合わせるとき。データを変えずに他のモジュールの結果を組み合わせる service（注文の手続きの見積もりなど）も用途の名前にし、読み取り専用のトランザクションにする。

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

### 5.4 書式

Java の書式は、IntelliJ IDEA の整形（Reformat Code）を推奨する。強制しない（CI と commit の前の hook では検査しない）。

- エージェントは、IntelliJ の MCP（`reformat_file`）を使えるとき、変えた Java のファイルを整形してよい。
- `reformat_file` が整形できるのは、IntelliJ で開いているプロジェクトのファイルだけ。`.claude/worktrees/` の下の作業ツリーのファイルは、その作業ツリーを IntelliJ で開いていなければ、MCP が「ok」を返しても整形しない。整形の後に `git diff` で変わったかを確認する。
- 既存のファイルの書式だけを直す変更は、他の変更と同じ commit に混ぜない。理由：差分のレビューと `git blame` を読みやすく保つため。

理由：今のコードは IntelliJ の書式でほぼそろっている。Spotless で強制する案は、今の書式に最も近い設定でも、読みやすさが下がる変更が出た（測定の記録は [issue #19 のコメント](https://github.com/roger88106/ec-site/issues/19)）。

## 6. 設定の値

### 6.1 設定の値の型

- 設定は `@ConfigurationProperties` の record で受け、`@Validated` を付ける。型は使う層に置く（画像の変換の設定は `infrastructure/converter/` など）。
- 接頭辞は `ecsite.<モジュール>`、まとまりを分けるときは `ecsite.<モジュール>.<範囲>`（`ecsite.media.storage` など）。モジュールでない共通の設定は `ecsite.<範囲>`（`ecsite.security` など）。
- 環境で変わる値（接続先、資格情報、ID、バケット名）は既定値を持たず必須にする。環境によらない値（上限、時間など）だけ既定値を持つ ※4。
- モジュールの `@Configuration` のクラスは、`infrastructure/`（またはその下）に置く。
- presentation は、設定の値の型（`@ConfigurationProperties` を付けたクラス）を受け取らない。設定の値が必要なときは、application の service が設定の値の型を読み、view で返す。`api/` は対象にしない。
- `@EnableConfigurationProperties` は `@Configuration` のクラスに付ける。条件で切り替える実装（mock など）の設定は、同じ条件の設定のクラスに付ける。
- 定期の処理の間隔も設定の型の項目で受け、`@Scheduled` は SpEL で設定の型の Bean を参照する（`${...}` の既定値を注釈に書かない）。
- 注釈が定数しか取らず、環境で変えない値（定期の処理のロックの上限など）は、設定にせず定数にし、理由をコメントに書く。
- 秘密の値（鍵、パスワード、署名の鍵、ソルト）を持つ設定の record は、`toString` を上書きし、秘密の値を `***` にする（`StripeProperties` に倣う）。理由：record の既定の `toString` は全ての項目を出し、設定の値をログや例外のメッセージに出したときに秘密の値が漏れるため。確認方法は単体テスト（`toString` に秘密の値が含まれないこと）で、秘密の値の項目を足したときの上書きの書き忘れはレビューで読む。

※4：本番では設定しない接続の値（S3 互換ストレージの接続先と資格情報。本番は AWS の既定と IAM ロール）は任意にし、本番で使う値（path style を使わない）を既定値にする。

理由：設定を忘れたまま、別の環境の値で動き出さないようにするため（足りなければ起動に失敗する）。

## 7. 静的解析

### 7.1 null の扱い

- main では、`com.ecsite` の下の全てのパッケージ（生成したコードの `com.ecsite.jooq`、`com.ecsite.api` を除く）で、注釈のない引数・戻り値・フィールドを null でないとみなす。NullAway がコンパイルのときに確認する（テストのソースセットと tools は対象外）。
- null を取りうる所には JSpecify の `org.jspecify.annotations.Nullable` を付ける。他の `Nullable`（`jakarta.annotation`、`javax.annotation`、`org.springframework.lang`）は使わない（確認方法：`scripts/ci/java-rules.mjs`）。型の注釈なので、配列や修飾した型では型の直前に付け、迷ったら JSpecify の位置にする（`String @Nullable []` など）。
- 選び方：
  - 今の振る舞いで null が正しい値（API の応答の省略できる項目、DB の null を取りうる列など）には `@Nullable` を付ける。
  - null を渡す必要がない所は、null を作らない書き方にする。
  - 値がないことを返す参照のメソッド（`findXxx`）は、今のコードのとおり `Optional` を返す。集約が null を取りうるフィールドを持つときは、フィールドを `@Nullable` にする。getter は、今のコードでは次の 2 通りがある。
    - `Cart.memberId()` のように `Optional` を返す。
    - Lombok の getter が `@Nullable` の値を返す（`Order.cancellation()`、`Payment.authorizationRef()` など）。状態から null でないと分かる所は、呼び出し側で `Objects.requireNonNull` に理由を付けて受ける。
- jOOQ のレコードの getter は注釈がないので、NullAway は null を確認しない。null を取りうる列の値は、`@Nullable` を付けたローカル変数・引数か `Optional` で受ける（null を取りうる列を、注釈のない引数に渡さない）。
- `lombok.NonNull` は実行時の確認として使ってよい（外さない）。

```java
public Optional<Cart> findById(UUID id) {
    ...
}

public Optional<UUID> memberId() {
    return Optional.ofNullable(memberId);
}
```

理由：null の誤りを、実行より前のコンパイルで見つけるため。Spring Framework 7 と同じ JSpecify の注釈なので、Spring の API の null の注釈も NullAway が読む。

### 7.2 検査を抑えるとき

- Error Prone と NullAway の報告は修正する。誤った報告（フレームワークが初期化するフィールドなど）、修正すると振る舞いが変わるもの、mock（本番で動かないコード）で修正する手間に見合わないものだけ、最も狭い範囲（宣言 1 つ）に `@SuppressWarnings("<検査の名前>")` を付け、理由を同じ所のコメントに書く。
- 検査を全体で無効にする、重大度を変えるのは `build.gradle.kts` で行い、先に spec で決める。意味のない注釈を足さないと消えない報告は、注釈でなく設定で扱う。今無効なのは、全てのソースセットで `StringConcatToTextBlock` と `ImmutableEnumChecker`、テストのソースセット（test、integrationTest、testFixtures）で `ArrayRecordComponent` と `TypeParameterUnusedInFormals`（理由は `build.gradle.kts` のコメント）。
- 生成したコード（`build/generated` の下）は検査しない。

```java
// 本番で動かない mock。body は HTTP の本文として送るだけなので、配列の成分を許す
@SuppressWarnings("ArrayRecordComponent")
public record SignedNotification(byte[] body, String signature) {
}
```

理由：検査を抑える範囲と理由が見えれば、後で外せるかをレビューで判断できるため。

## 8. マイグレーション

### 8.1 前のリリースのアプリが動き続けられる変更だけにする

マイグレーションはデプロイごとに 1 回適用し、起動時には適用しない（[環境変数](../guides/environment.md) 第 1 節）。マイグレーションを適用してからアプリを入れ替えるまでの間、前のバージョンのアプリが新しいスキーマで動く。

新しいマイグレーションは、前のリリースのアプリが動き続けられる変更だけにする。次の変更は、1 つのリリースでしない。

- 表・列の削除（スキーマの削除 `DROP SCHEMA` を含む）
- 表・列の改名（スキーマの改名と、表の別のスキーマへの移動 `SET SCHEMA` を含む）
- 列の型の変更
- 既存の列を NOT NULL にすること（既存の列への主キーの制約の追加 `ADD PRIMARY KEY` を含む）
- 既定値のない NOT NULL の列の追加（主キーの列の追加と、`DEFAULT NULL` を指定した NOT NULL の列の追加を含む。`IDENTITY` の列は既定値があるので除く）

これらは、次の 3 段に分ける。

| 段 | リリース | すること |
|----|----------|----------|
| 1 | 最初 | 新しい表・列を追加する（前のバージョンのアプリは使わない） |
| 2 | 次 | アプリを新しい表・列に切り替えてリリースする |
| 3 | その次 | 使わなくなった表・列を削除する ※5 |

※5：段 3 の削除は、マイグレーションの検査の除外の一覧（`scripts/ci/migrations-exclusions.json`）に置く。キーを `<マイグレーションのファイル名>:<変更の種類>`、値を理由にし、理由には、どのリリースで使わなくなったかを書く（詳しくは [CI の検査](../guides/ci.md) の `migrations` の行）。今ある 38 のマイグレーションは本番に適用していないので、検査の対象にしない。

確認方法は CI の検査（`migrations`）。

理由：適用の後に入れ替え前のアプリが動くため、前のバージョンのアプリが新しいスキーマで失敗しないようにするため。
