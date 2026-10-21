# Plan 005：バックエンドの整理

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 004 が完了している（バックエンドは `backend/ecsite/`、規則の文書は `docs/style/`、カバレッジの報告と AC の検査が CI にある）。パスは `backend/ecsite/src/main/java/com/ecsite/` を `…/` と略す。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 標準の構成と検査 | 第 1 節 | DS-8 |
| 値と入力の検証 | 第 2 節 | DS-9 |
| エラーとメッセージ | 第 3 節 | DS-10 |
| 重複と大きいクラス、全体の走査 | 第 4 節 | DS-11 |
| 設定とビルド | 第 5 節 | DS-12 |
| コメント | 第 6 節 | DS-13 |

## 1. 標準の構成（DS-8）

### 1.1 層とパッケージ（DC-27、DC-28）

レベル A・B とも、層の名前を同じにする（constitution 4.2 を修正する。レベル B は「中身が簡略な 4 層」と書き換える）。

```
module/<モジュール>/
├── api/                公開の同期の API（@NamedInterface("api")）。他のモジュールが呼ぶ
├── event/              公開のイベント（@NamedInterface("event")）
├── presentation/       API の入口（生成した interface の実装）と、API の型への変換
├── application/        use case（取引の境界。@Transactional はここだけ）
│   ├── model/          command・query・view（レベル A。レベル B は必要なときだけ）
│   └── port/           application が使う port（参照の Repository、他のモジュールの Directory）
├── domain/             業務の型。エラーの定義（<モジュール>ErrorCode）
│   └── <集約>/         集約、vo/（値）、port/（Repository、domain が使う Directory）
└── infrastructure/     port の実装
    ├── persistence/    jOOQ の Repository と、その部品
    ├── directory/      他のモジュールの api を呼ぶ Directory の実装（腐敗防止層）
    └── <外部>/          外部の仕組み（ストレージ、画像の変換など）
```

| レベル | 違い |
|--------|------|
| A | domain は何にも依存しない。application は domain の port を通す。CQRS（command と query の Repository を分ける） |
| B | domain の型は振る舞いの少ない型でよい。port は application/port に置き、domain/<集約>/port は作らない。CQRS を求めない |

- **腐敗防止層**（DC-28）：port（interface と、相手のモジュールの情報を写す自分の型）は、使う層の `port/` に置く。実装は `infrastructure/directory/`。`*dir` のパッケージは作らない。
- **設定の値の型**（`@ConfigurationProperties`）は、使う層に置く（第 5 節）。
- 図と表は `docs/style/backend.md` に置き、constitution 4.2 からリンクする。

### 1.2 今のモジュールの移し方

| モジュール | 今 | 移し方 |
|------------|----|--------|
| cart（B） | `controller/`、`service/`（`Cart`、`CartItem`、`CartService`、エラー、Repository の port）、`catalogdir/`、`stockdir/` | `presentation/`、`application/`（`CartService`）、`application/port/`（`CartRepository`、Directory の port）、`domain/`（`Cart`、`CartItem`、`CartMergeResult`、`CartErrorCode`）、`infrastructure/directory/`（Directory の実装） |
| media（B） | `service/` 11 | `application/`（`MediaService`、`AssetFileHandler`）、`application/port/`（`AssetRepository`、`ObjectStorage`、`ImageConverter`）、`domain/`（`Asset`、`AssetKeys`、`ImageFormat`、`AssetReleased`、`MediaErrorCode`）、`infrastructure/storage/`・`infrastructure/converter/`・`infrastructure/persistence/` |
| member（B） | `service/` 3 | `application/`（`MemberService`）、`application/port/`（`MemberRepository`）、`domain/`（`Member`） |
| storefront（B、参照だけ） | `controller/`、`service/`、`catalogdir/`、`orderdir/`、`stockdir/` | `presentation/`（Controller、`StorefrontMapper`）、`application/`（`StorefrontService`、view、`ShopProperties`）、`application/port/`（Directory の portと、その型）、`infrastructure/directory/` |
| catalog（A） | `infrastructure/` 16 | `infrastructure/persistence/`（jOOQ の Repository と部品）、`infrastructure/directory/`（`MediaApiDirectory`、`TaxApiDirectory`）、`infrastructure/cache/`（`TaxRateScheduleCache`）。application の参照の Repository の port 4 つは `application/port/` |
| order（A） | `application/` 12、`domain/port/` | application の portを `application/port/`、`domain/port/` の Directory は使う集約の `port/` か `application/port/`（呼ぶ層で決める） |
| tax・inventory（A） | ほぼ標準 | application の参照の Repository の portを `application/port/` |

正確な移し先は、段階 2 の最初のタスクで全ファイルの表にして利用者に確認する。

### 1.3 パッケージの大きさ（DC-29）

- パッケージの直下の `.java`（`package-info.java` を除く）は、目安 10、上限 20。テストは数えない。
- 今の目安超え：catalog/infrastructure 16（1.2 で分ける）、order/application 12、catalog/application 11、media/service 11（1.2 で分ける）。order と catalog の application は、use case のまとまり（第 4.2 節の商品の更新の分割）で分ける。分けないと判断したものは、理由を `docs/style/backend.md` の例外の表に書く。

### 1.4 自動の検査（DC-30）

- `ModulithTest` を `src/integrationTest` から `src/test` に移す（Spring もコンテナも起動しない。移す前に Docker なしで通ることを確認する）。
- ArchUnit（Modulith が入れている）で、`src/test/java/com/ecsite/architecture/` に規則を書く：
  - 層の依存の向き（presentation → application → domain ← infrastructure。レベル A の domain は Spring・jOOQ・他の層に依存しない）
  - パッケージの名前（モジュールの直下は `api`・`event`・`presentation`・`application`・`domain`・`infrastructure` だけ）
  - `@Transactional` は application だけ（infrastructure は、外の仕組みの都合の例外として許す。`docs/style/backend.md` 4.1）。`@RestController` は presentation だけ。今 api に付いている `catalog/api/OrderCatalogApi` は application に移す
  - パッケージの直下のファイルが 20 を超えない（目安の 10 は検査しない）
- 先にテストを書いて今のコードで失敗することを確認（レッド）、移した後に通す。

## 2. 値と入力の検証（DS-9）

### 2.1 文字の検証（DC-31）

- `common/model/value/BoundedText`：「空白でない」「長さの上限を code point で数える」の判定を 1 か所にする（静的な判定の関数）。各ドメインは上限の定数とエラーの種類だけを持ち、判定をこの部品に任せる。
- 置き換える所：`TaxCategory`、`Category`、`Product`（名前、説明、軸の名前・値）、`ShippingAddress`、`StockAdjustment`、`Order`（理由）。

### 2.2 制約の一致（DC-32、DC-33）

- `FieldConstraintContractTest`（`src/test/…/contract/`）：同じ項目の、API の契約の `maxLength`・`minimum`・`maximum`、ドメインの上限の定数、データベースの列の長さ（jOOQ の生成した型の長さ）を表にして照合する。表はテストの中に書く（項目を追加したら表も追加する）。
  - 食い違いが見つかったら、修正する前に利用者に確認する（DN-6）。
- `OpenApiRequestConstraintTest`：`admin.yaml`・`store.yaml` のリクエスト（本文、クエリ、パス）の数値の項目に範囲、文字の項目に長さか形式（`pattern`・`format`・`enum`）があることを確認する。
  - 今の漏れ（2026-10-12）：`version` 8 か所（`minimum: 0`）、画像の受付の `contentLength`（`maximum` は 10MB）・`contentType`、在庫の調整の `delta`、店舗の `expectedTotal`（`minimum: 0`）、お届け先の都道府県（`maxLength`）。値は spec の AC と DB の列に合わせ、plan の段階 3 の最初のタスクで一覧にして確認する。
- 統合テストに「長すぎる・形式の誤り → 400」のテストがある項目を数え、無い種類（長さ、範囲、形式）だけ代表を追加する。

### 2.3 金額（DC-34）

- モジュールの間の公開の型（`api/` の record）の金額を `Money` にする（`StoreProductDetail`、`StoreProductSummary` など）。command の型（`CreateProductCommand` など）も `Money`。整数は presentation（API の型との変換）と infrastructure（DB）だけ。

### 2.4 共通の型の置き場所（DC-35）

| パッケージ | 中身 |
|------------|------|
| `common/model/base/` | `AggregateRoot`、`DomainEvent` |
| `common/model/value/` | `Money`、`BoundedText`、`Operator`（操作した運営者の写し） |

共通の値の型にする基準（`docs/style/backend.md`）：2 つ以上のモジュールで使い、規則（範囲、丸め、比べ方）を持つもの。1 つのモジュールだけのものは、そのモジュールの `vo/`。

## 3. エラーとメッセージ（DS-10）

### 3.1 1 か所の定義（DC-36）

- 残すもの：`<モジュール>ErrorCode`（enum）。種類（`ErrorType`）と、Javadoc（いつ起きるか）を持つ。
- 削除するもの：`<モジュール>Message` と、その `Code`（7 クラス、約 1,100 行）。
- メッセージのキーは enum から決める：`ecsite.<モジュール>.<enum の名前の小文字>`。`ErrorCodeProvider` に、モジュールの名前を返すメソッドだけを各 enum に書かせ、キーは共通の既定のメソッドで作る。
- `MessageContractTest`：すべての `ErrorCodeProvider` の enum の定数に `messages.properties` のキーがあり、`messages.properties` の `ecsite.<モジュール>.*` のキーに enum の定数があることを確認する（検証のキー `ecsite.validation.*` と共通の `ecsite.common.*` は別の表で確認する）。
- エラーではないメッセージ（`CommonMessage` など）があれば、移す前に一覧にして扱いを決める。2026-10-13 に調べた結果：`CommonMessage` もすべてエラーのメッセージで、ほかの `<モジュール>Message` と同じく削除する。削除するのは Java の定数のクラスだけで、`messages.properties` の文は残り、エラーの応答は今と同じく文を返す。
- `CommonErrorCode.RESTORE_ERROR` は `SYSTEM_ERROR` の文を共有している。キーを規則で決めるため、`ecsite.common.restore_error` に同じ文を追加する（キーを個別に指定する例外の仕組みは作らない。2026-10-13 に利用者が確認した）。

### 3.2 入力の検証のメッセージ（DC-37）

- `GlobalExceptionHandler` で、制約の種類（`NotNull`、`Size`、`Min`、`Max`、`Pattern`、`Email`、`DecimalMin` など）からキー `ecsite.validation.<種類>` を選び、制約の値（上限・下限）を差し込んでメッセージを作る。知らない種類は `ecsite.validation.invalid`。
- フレームワークの既定のメッセージ（JVM のロケールで言語が変わる）を使わない。項目の名前は今のまま（API の項目名）。
- 新しい文（2026-10-13 に利用者が確認した）。今の業務のメッセージの言い方に合わせる：

| キー | 文 |
|------|----|
| `ecsite.validation.notnull`、`notblank` | 入力してください。 |
| `ecsite.validation.size` | {max} 文字以内にしてください。（下限があれば {min} 文字以上 {max} 文字以内にしてください。） |
| `ecsite.validation.min`、`decimalmin` | {value} 以上にしてください。 |
| `ecsite.validation.max`、`decimalmax` | {value} 以下にしてください。 |
| `ecsite.validation.pattern` | 形式が正しくありません。 |
| `ecsite.validation.email` | メールアドレスの形式が正しくありません。 |
| `ecsite.validation.invalid` | 値が正しくありません。 |

- 段階 4 の最初のタスクで、今の既定の文（ローカルと CI の JVM のロケールで）と、管理画面の表示が変わる所を一覧にする（spec DN-4 の例外）。

### 3.3 差し込み（DC-38）

- 2026-10-13 に調べた結果：`messages.properties` に差し込みのあるメッセージはない。業務の規則の値（文字の数の上限、品目の数の上限など）は、文に数字で書いてある。
- 書式は名前で差し込む（`{max}`）。今の `I18nMessage`（キーと差し込む値の名前）と `MessageHelper`（`MessageSource` から取り出した文の `{名前}` を値で置き換える）をそのまま使う。Java の標準の `MessageFormat`（`{0}`）は使わない：名前のほうが文を読みやすく、Bean Validation の制約の属性（`max`・`min`・`value`）と同じ形で検証のメッセージ（第 3.2 節）にも使えるため。
- 差し込む値の名前は、enum の定数に書く（例：`TOO_MANY_CART_ITEMS(ErrorType.BUSINESS_RULE_ERROR, "max")`）。削除するのは `<モジュール>Message` の定数のクラスだけで、`I18nMessage` は enum の中で作る。
- `MessageContractTest` は、文の中の `{名前}` と、enum の定数に書いた値の名前が一致することも確認する。
- 業務の例外は、エラーの定義と一緒に、差し込む値（名前と値）を持てる。値はドメインの定数から渡す（例：`カートに入れられるのは {max} 品目までです。` の `max` は、カートの品目の上限の定数）。
- 文に規則の値を数字で書いているメッセージを一覧にし、すべて差し込みにする（2026-10-13 に利用者が決めた）。差し込みの前後で同じ文になることを、テストで確認する（spec DN-4）。

### 3.4 画像の変換の結果の確認（DC-64）

- `ImgproxyImageConverter` は、imgproxy の応答の本文の先頭のバイト（`RIFF` と `WEBP`）で WebP であることを確認する。違えば、今の変換の失敗と同じエラー（`IMAGE_CONVERSION_FAILED`）にする。`Content-Type` には頼らない（受け付けのときの形式の判定と同じく、中身で決める）。
- テストは今の `ImgproxyImageConverterFailureTest`（JDK の HttpServer で imgproxy の代わりをする）に、WebP でない本文を返す場合を追加する。

## 4. 重複と大きいクラス（DS-11）

### 4.1 現在の会員（DC-39）

- `member/api/CurrentMemberIds`：`require()`（ログインが必要だ。なければ 401）、`find()`（任意）。会員の識別（IdP の subject → 会員 ID）は会員モジュールの中に閉じる。
- `CartController`、`StoreCheckoutController`、`StoreOrderController`、`StorefrontCartController` の `requester()`・`memberId()` を置き換える。

### 4.2 商品（DC-40）

- 並び順の再採番（`reorder`、`nextDisplayOrder`）を `common/model/value/` の部品にし、商品のバリアント・画像、カテゴリで使う。
- 文字の検証は第 2.1 節の部品へ。
- 商品の更新の処理（`ProductCommandService`、312 行）を、商品、バリアント、公開、画像（既存の `ProductImageService`）に分ける。取引の境界（1 つの操作 = 1 つのトランザクション）は今のまま。
- `Product` の行数は目安 400（規則にはしない）。

### 4.3 全体の走査（DC-41）

- 段階 2〜5 の後に、バックエンドの全体を `docs/style/` と本 spec の規則に照合して読む。読む量が多いので、別のセッションで行ってもよい。
- 見つけたもの（重複、使われていないコード、規則に合わない所）は、段階 7 の tasks に 1 項目ずつチェックボックスで追加し、修正したか、修正しない理由を書く。
- 修正しないと決めているもの：カートと注文の両方にある `isAccessibleBy`（モジュールの境界を越えて型を共有しない。constitution 6）、手動で書いた型の変換（spec の Non-goals）。

## 5. 設定とビルド（DS-12、P2）

### 5.1 設定の値（DC-42）

| 今 | 後 |
|----|----|
| `config/security/SecurityProperties` | 今のまま（モジュールではない共通の設定） |
| `media/infrastructure/ImgproxyProperties`、`MediaStorageProperties` | `media/infrastructure/converter/`、`…/storage/` |
| `order/application/OrderProperties` | 今のまま（application が使う） |
| `storefront/service/ShopProperties` | `storefront/application/` |

- 規則（`docs/style/backend.md`）：接頭辞は `ecsite.<モジュール>.<範囲>`、`@Validated` を付け、環境ごとに変わる値（接続先、資格情報、ID）は既定値を持たず必須にする。環境によらない値だけ既定値を持つ。
- 起動に失敗することを、必須の値がない設定で確認する（統合テストか、設定の型の単体テスト）。

### 5.2 jOOQ のコード生成（DC-43）

- 今は `generateJooq` が `Task.upToDateWhen is false` で毎回走る（約 6 秒。うちコンテナの起動 2 秒。2026-10-12 に計測）。プラグインの `allInputsDeclared` を true にし、入力（マイグレーションのディレクトリと生成の設定）が変わらなければ省かれるようにする。
- 確認方法：続けて 2 回ビルドし、2 回目に `generateJooq UP-TO-DATE`。マイグレーションを追加すると再生成する。CI（キャッシュなし）は毎回生成する。

## 6. コメント（DS-13）

- `docs/style/comments.md` に「複雑な処理」の基準と書き方を追加する（段階 1）：
  - 基準：stream の 2 段以上の変換、`groupingBy`・`flatMap`・`reduce`、入れ子の繰り返し、`&&`・`||` が 3 つ以上の条件、3 つ以上の分岐。
  - 書き方：何の結果を得るか・なぜそうするかを 1 行。規則は番号ではなく言葉で書く。
- 段階 8 で、基準に当たる所を一覧にし（stream は約 120 か所。直前の行にコメントがあるのは 2 か所）、読んで意味を確認してから追加する。分からない所は一覧にして利用者に聞く。
- spec・plan・tasks の番号を、規則の言葉に書き換える（DC-65。`docs/style/comments.md` 第 1 節）。今は約 570 か所（AC 約 440、BR・US・NFR 約 70、spec・plan の節 約 60）。コメントがすでに規則を言葉で書いていれば、番号を削除するだけにする。番号の指す規則が分からない所は、spec を読んで書く。モジュールごとに commit。
- コメントだけの commit にする（DN-5）。

## 7. 確認方法

| DC | 確認方法 |
|----|----------|
| 27〜30 | ArchUnit と Modulith のテスト（単体テスト）が通る。図が `docs/style/backend.md` にある |
| 31 | 文字の判定の重複がない（レビューで `git grep` で探す） |
| 32、33 | 契約テストが通る |
| 34 | ArchUnit：`api` の record に金額の整数の項目がない（名前が `price`・`amount`・`total`・`fee` で `long` の項目を検出） |
| 35 | レビューで読む |
| 36〜38 | `MessageContractTest` と、検証のメッセージのテストが通る |
| 39、40 | テストが通る。同じメソッドがない（レビュー） |
| 41 | 段階 7 の tasks のチェックボックスがすべて埋まる |
| 42 | 必須の値がないときに起動に失敗するテスト |
| 43 | 第 5.2 節の手順 |
| 44 | レビューで読む（第 6 節の一覧を使う） |
| 64 | `ImgproxyImageConverterFailureTest` の WebP でない応答の場合が通る |
| 65 | レビューで読む：`src/main` で、spec の番号（`AC-` など）、`spec NNN`、`plan … 第`、タスク番号を `git grep` で探し、残っているものがコメントの番号でないことを確認する |
| DN-4 | すべてのテストが通る。`api/` の差分は第 2.2 節の制約の追加だけ |
| DN-5 | `git log --stat` |
| DN-6 | `db/migration` に新しいファイルがない（食い違いを修正する場合を除く） |

## 8. 実装の順序

tasks は段階ごとに `tasks/` の 1 ファイルにする（[01](tasks/01-docs.md)、[02](tasks/02-structure.md)、[03](tasks/03-values.md)、[04](tasks/04-errors.md)、[05](tasks/05-duplication.md)、[06](tasks/06-config-build.md)、[07](tasks/07-scan.md)、[08](tasks/08-comments.md)、[09](tasks/09-review.md)）。タスク番号は `T5` ＋ 段階 ＋ 連番（例：T5101）。

| # | 段階 | 完了条件 |
|---|------|----------|
| 1 | 規則の文書（constitution 4.2、`docs/style/backend.md` の標準の構成・大きさ・共通の型・エラーのキー・設定の値、`comments.md` の複雑な処理） | 文書がある |
| 2 | 標準の構成（第 1 節。検査のテストを先に書き、移動の commit と参照の修正の commit を分ける） | DC-27〜30 のテストが通る |
| 3 | 値と入力の検証（第 2 節。契約テストを先に） | DC-31〜35 のテストが通る |
| 4 | エラーとメッセージ（第 3 節） | DC-36〜38 のテストが通る。変わる文を利用者が確認した |
| 5 | 重複と大きいクラス（第 4.1、4.2 節） | DC-39、40 のテストが通る |
| 6 | 設定とビルド（第 5 節、P2） | DC-42、43 |
| 7 | 全体の走査（第 4.3 節） | 一覧のすべての項目に結論がある |
| 8 | コメント（第 6 節） | DC-44 |
| 9 | レビュー | `docs/style/review.md` で確認し、`check-zh.mjs --all` が通り、カバレッジが段階 1 の前より下がっていない。README の spec の状態を修正する |
