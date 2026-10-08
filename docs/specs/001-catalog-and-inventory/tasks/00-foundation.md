# 段階 0：基盤

空のアプリケーションが起動し、CI が通る状態を作る。以降の段階は、この上に機能を足すだけにする。

- 完了条件：ローカル用 profile でアプリが起動する。`./gradlew build`（単体・統合テスト）が通る。CI が通る。
- 参照：constitution 2、5、7、9、12〜14、[plan](../plan.md) 第 4 節、MyIAM（`build.gradle.kts`、`libs.versions.toml`、`common/error`）

## プロジェクトの骨格

- [x] **T001** `backend/` に Gradle（Kotlin DSL）のプロジェクトを作る。
  - `gradle/libs.versions.toml` は MyIAM を起点に、着手時点の最新安定版にする（Spring Boot 4.1.x、Spring Modulith、jOOQ、Testcontainers、Flyway、OpenAPI Generator 7.16 以降）。
  - `lombok.config` は MyIAM から写す。ルートパッケージは `com.ecsite`。
  - MyIAM から除く依存：Spring Security と OAuth2（constitution 10）、Thymeleaf、Redis（constitution 2）。Guava を使う場合は `-android` ではなく `-jre` 版にする。
  - `.gitattributes` で改行を LF に揃える（Windows の `.bat` は CRLF）。`gradlew` が CRLF になると、Linux の CI で実行できないため。
  - 完了：空のアプリが `./gradlew bootRun` で起動する。
- [x] **T002** ソースセットを MyIAM と同じく `src/test`（単体）と `src/integrationTest`（統合）に分け、`./gradlew check` で両方が走るようにする。
- [x] **T003** ルートに `compose.yaml` を作る（PostgreSQL 18、Zipkin）。
- [x] **T004** 設定ファイルを分ける。
  - `application.yml`：環境に依存しない設定。値は環境変数で上書きできるようにする。
  - `application-local.yml`：ローカル開発用（compose の接続先、起動時のマイグレーション実行）。
  - 本番相当の profile では、起動時のマイグレーションを無効にする（constitution 5-10）。

## DB

- [x] **T005** Flyway を入れる（`spring-boot-starter-flyway` と PostgreSQL 用モジュール）。
  - 最初のマイグレーションで schema（`common`、`tax`、`catalog`、`inventory`）を作る。
  - Spring Modulith のイベント発行記録のテーブルを、使用バージョンの公式 DDL から `common` schema に作る。
  - 完了：ローカルで起動するとマイグレーションが適用される。
- [x] **T006** jOOQ のコード生成を、Gradle で「Testcontainers の PostgreSQL を起動 → Flyway を適用 → 生成」の順で行うようにする（MyIAM の `generateJooq` を Flyway 適用に置き換える）。生成物はコミットしない。
- [x] **T007** ローカル用の seed の仕組みを作る（ローカル用 profile でだけ適用される別の場所のマイグレーション）。中身は各段階で足す。

## API の生成

- [x] **T008** OpenAPI Generator の Gradle プラグインで、`api/openapi/admin.yaml` と `store.yaml` から Spring のインターフェースとモデルを生成する。
  - 設定：`interfaceOnly`、`useSpringBoot4`、`useJackson3`、`useTags`。
  - パッケージ：`com.ecsite.api.admin`、`com.ecsite.api.store`（生成物はコミットしない）。
  - nullable の項目：既定（`openApiNullable=true`）では `JsonNullable` が使われ、jackson-databind-nullable に依存する。これが Jackson 3 に対応しているかを確認し、対応していなければ `openApiNullable=false` にする。確認の結果は本タスクに追記する。
    - 結果：jackson-databind-nullable 0.2.12 は Jackson 3 用のモジュールを含み、自動で登録される。`openApiNullable=true` のままにする。null を送った場合と送らなかった場合を区別できることを、統合テスト（`OpenApiModelJsonTest`）で確認している。
    - あわせて分かったこと：受け取った日時は UTC に揃えて出力される（`+09:00` → `Z`）。同じ時点で、ISO 8601 のオフセット付き文字列でもあるので、constitution 9 に合う。
  - 完了：生成コードを含めてコンパイルが通る。
- [x] **T009** 権限の記載漏れを検出するテストを作る：`admin.yaml` のすべての操作に `x-required-permission` があり、`resource:action` の形であること（constitution 10）。

## 共通部品（`common`）

- [x] **T010** [テスト] 例外とエラーレスポンスの統合テスト。
  - 入力検証エラー：400、`errors` にフィールドごとのエラーが入る。
  - 業務ルール違反：400。見つからない：404。バージョン不一致：409（`CONCURRENT_MODIFICATION`）。想定外の例外：500。業務例外ではなくシステム例外：500。
  - Spring MVC が投げる標準の例外も、同じ形のエラーになる。
    - パスの UUID の形式誤り、必須パラメータの欠落、型の不一致、JSON の構文誤り：400
    - メソッド引数の検証エラー（生成インターフェースの `size` の上限超えなど）：400、`errors` 付き
    - 存在しないパス：404。許可されていないメソッド：405
  - すべてのエラー（500 を含む）で `errorCode`、`message`、`traceId` が入り、Content-Type が `application/problem+json`。
    - `message` は画面にそのまま出せる日本語の文字列（メッセージのキーやパラメータの構造ではない）。
    - トレースの span がない状態（サンプリングされない場合など）でも、`traceId` を返し、例外ハンドラー自体が失敗しない。
- [x] **T011** 例外体系を MyIAM から移植する（`ErrorCode`、`ErrorType`、`BusinessException`、`SystemException`、`GlobalExceptionHandler`、i18n メッセージ）。T010 を通す。
  - MyIAM のまま写すと constitution 9 に合わない箇所があるので、次を直す。
    - `message`：MyIAM はメッセージのキーとパラメータの構造を入れていて、日本語の文字列は `detail` に入っている。`message` に日本語の文字列を入れる。
    - `traceId`：MyIAM は 500 のときだけ返す。すべてのエラーで返す。
    - 500：MyIAM は `errorCode` と `message` を返さない。`SYSTEM_ERROR` とその文言を返す。
    - トレース情報の取得：MyIAM は span がないと null を返し、呼び出し側で NullPointerException になる。span がない場合も扱う。
    - Spring MVC の標準の例外：MyIAM は入力検証エラー以外を既定の処理に任せていて、追加の項目が付かない。すべて同じ形に変換する。
    - Spring Security への依存：MyIAM は認可例外を扱うために依存している。MVP では認証を入れないので（constitution 10）、その処理は移植しない。
    - エラー画面（`ErrorControllerImpl`、Thymeleaf）：API だけなので移植しない。
    - メッセージのキーの接頭辞：`myiam.` を `ecsite.` にする。
  - DB の制約違反は、各リポジトリで業務のエラーに変換する（constitution 9）。ここでは変換に使う共通の部品（制約名から業務のエラーを引く仕組みなど）を用意する。
- [x] **T012** [テスト] 金額（`Money`）の単体テスト：円単位の整数、負数の拒否、加算。
- [x] **T013** 金額を実装する。T012 を通す。
- [x] **T014** [テスト] ページングの単体テスト：`page` は 1 始まり、`size` は既定 20・最大 100、総件数からの総ページ数の計算。並びの最後に ID を加えて順序を確定させる仕組み（constitution 9）。
- [x] **T015** ページングの共通部品を実装する（リクエストの変換と、レスポンスの組み立て）。T014 を通す。
- [ ] **T016** `Clock` を Bean として定義する。統合テストでは固定の `Clock` に差し替えられるようにする。
  - MyIAM のリポジトリは更新日時に現在時刻を直接取っている。移植するコードでは、現在時刻は必ず `Clock` から取る（constitution 5-6）。

## モジュール

- [ ] **T017** 4 モジュールのパッケージと `package-info.java` を作り、依存を宣言する（[plan](../plan.md) 第 1 節）。
  - 税：依存なし
  - 商品カタログ：税の API
  - 在庫：商品カタログのイベント
  - 店舗：商品カタログの API、在庫の API
- [ ] **T018** モジュール境界の検証テスト（`ModulithTest`）を作る。

## 運用

- [ ] **T019** 可観測性を設定する。
  - liveness / readiness、Prometheus のエンドポイントを公開する。
  - トレーシングを Zipkin へ送る。
  - ログを JSON の構造化ログで標準出力に出し、トレース ID を含める。
  - graceful shutdown を有効にする。
  - 完了：統合テストで liveness / readiness が 200 を返す。

## CI

- [ ] **T020** GitHub Actions を作る。
  - push と pull request で、バックエンドのビルド（単体・統合テスト）を実行する。
  - OpenAPI の構文を検証する。
  - フロントのジョブは段階 7・8 で追加する。
- [ ] **T021** ルートの `README.md` に、起動とテストの手順を書く。
