# バックエンドの書き方

原則は [constitution](../constitution.md) 第 4〜9 節。本書は、今のコードで守っている書き方を書く。パッケージの標準の形とエラーのキーの決まりは spec 005 で足す。

## 1. 層とパッケージ

### 1.1 モジュールの外に出すのは `api/` と `event/` だけ

- **規則**：他のモジュールから使う型は `api/`（同期の呼び出し）と `event/`（イベント）に置く。呼ぶ側は、相手の型を `XxxDirectory`（例：`cart/catalogdir/CartCatalogDirectory`）で自分の型に包み、内部に持ち込まない。
- **理由**：モジュールの中を、他のモジュールに影響を与えずに変えられるようにするため（constitution 4.1）。
- **確かめ方**：自動。[ModulithTest](../../backend/ecsite/src/integrationTest/java/com/ecsite/modulith/ModulithTest.java)。

### 1.2 レベル A の層

- **規則**：`domain/<集約>/`（集約、値オブジェクトの `vo/`、リポジトリの port の `port/`）、`application/`（Command・Query の service、参照用のリポジトリの port、`model/{command,query,view}/`）、`infrastructure/`（jOOQ のリポジトリ `JooqXxxRepository`）、`presentation/`（Controller と mapper）に分ける。
- **理由**：domain を何にも依存させず、永続化と API の形から切り離すため（constitution 4.2）。
- **確かめ方**：人手（見直し）。依存の向きの自動の検査は spec 005 で足す。

### 1.3 レベル B の層

- **規則**：今は `service/`（業務の処理、リポジトリの port、エラー）、`infrastructure/`（リポジトリの実装、外部の連携）、`controller/`（API の実装）の 3 つ。モジュールの設計書の構成の図に合わせる。
- **理由**：ルールが単純なモジュールに、レベル A の層を持ち込まないため（constitution 4.2）。
- **確かめ方**：人手（見直し）。constitution 4.2 の表の層の名前（controller / service / repository）と、コードの名前（`infrastructure/`）が違う。spec 005 でレベル A と同じ名前に揃える。

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
- **理由**：契約に書ける検証を契約に、業務の規則を 1 か所に置くため（constitution 9）。
- **確かめ方**：人手（見直し）。制約の契約のテストは spec 005 で足す。

### 3.2 エラーは `XxxErrorCode` と `BusinessException`

- **規則**：業務のエラーは、モジュールごとの `XxxErrorCode`（`ErrorCodeProvider` の enum。`ErrorType` とメッセージ）で表し、`BusinessException.of(...)` で投げる。メッセージの文は `messages.properties` の `ecsite.<モジュール>.<名前>` に置き、`XxxMessage` の enum で引く。レスポンスへの変換は `GlobalExceptionHandler` だけが行う。
- **理由**：フロントが `errorCode` で分岐でき、画面にそのまま出せる日本語を 1 か所で管理するため（constitution 9）。
- **確かめ方**：自動（[ErrorResponseTest](../../backend/ecsite/src/integrationTest/java/com/ecsite/common/error/ErrorResponseTest.java)）と人手（見直し）。

### 3.3 DB の制約の違反は、リポジトリで業務のエラーに変える

- **規則**：一意性など、同時の操作で DB の制約でしか守れない規則の違反は、リポジトリが [ConstraintViolationTranslator](../../backend/ecsite/src/main/java/com/ecsite/common/persistence/ConstraintViolationTranslator.java) で業務のエラーに変える。制約名と業務のエラーの対応は、各リポジトリの `CONSTRAINTS` に置く。
- **理由**：制約の違反を 500 にしないため（constitution 9）。
- **確かめ方**：自動（各リポジトリの統合テストで、違反が業務のエラーになることを確かめる）。

### 3.4 更新の競合は `version`

- **規則**：同時に編集されうる集約の更新は、`version` が一致しなければ `CONCURRENT_MODIFICATION`（409）にする。状態の切り替えは冪等にし、`version` を取らない。
- **理由**：他の人の変更を黙って上書きしないため（constitution 9）。
- **確かめ方**：自動（API の統合テスト）。

## 4. トランザクションと時刻

### 4.1 トランザクションは application（レベル B は service）

- **規則**：`@Transactional` は application の service（レベル B は `service/`）のクラスに付ける。参照だけの service は `readOnly = true`。
- **理由**：1 つの操作を 1 つのトランザクションにし、境界を層で決めるため。
- **確かめ方**：人手（見直し）。今は `catalog/api/OrderCatalogApi` だけが `api/` のクラスに付けている。

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
| 他モジュールの腐敗防止層 | `<自モジュール><相手>Directory`（パッケージ `<相手>dir`） | `CartCatalogDirectory` |
| イベント | 過去形 | `OrderPlaced` |

- **確かめ方**：人手（見直し）。
