# 段階 0：基盤

空のアプリケーションが起動し、CI が通る状態を作る。以降の段階は、この上に機能を足すだけにする。

- 完了条件：ローカル用 profile でアプリが起動する。`./gradlew build`（単体・統合テスト）が通る。CI が通る。
- 参照：constitution 2、5、7、9、12〜14、[plan](../plan.md) 第 4 節、MyIAM（`build.gradle.kts`、`libs.versions.toml`、`common/error`）

## プロジェクトの骨格

- [ ] **T001** `backend/` に Gradle（Kotlin DSL）のプロジェクトを作る。
  - `gradle/libs.versions.toml` は MyIAM を起点に、着手時点の最新安定版にする（Spring Boot 4.0.x、Spring Modulith、jOOQ、Testcontainers、Flyway、OpenAPI Generator 7.16 以降）。
  - `lombok.config` は MyIAM から写す。ルートパッケージは `com.ecsite`。
  - 完了：空のアプリが `./gradlew bootRun` で起動する。
- [ ] **T002** ソースセットを MyIAM と同じく `src/test`（単体）と `src/integrationTest`（統合）に分け、`./gradlew check` で両方が走るようにする。
- [ ] **T003** ルートに `compose.yaml` を作る（PostgreSQL 18、Zipkin）。
- [ ] **T004** 設定ファイルを分ける。
  - `application.yml`：環境に依存しない設定。値は環境変数で上書きできるようにする。
  - `application-local.yml`：ローカル開発用（compose の接続先、起動時のマイグレーション実行）。
  - 本番相当の profile では、起動時のマイグレーションを無効にする（constitution 5-10）。

## DB

- [ ] **T005** Flyway を入れる（`spring-boot-starter-flyway` と PostgreSQL 用モジュール）。
  - 最初のマイグレーションで schema（`common`、`tax`、`catalog`、`inventory`）を作る。
  - Spring Modulith のイベント発行記録のテーブルを、使用バージョンの公式 DDL から `common` schema に作る。
  - 完了：ローカルで起動するとマイグレーションが適用される。
- [ ] **T006** jOOQ のコード生成を、Gradle で「Testcontainers の PostgreSQL を起動 → Flyway を適用 → 生成」の順で行うようにする（MyIAM の `generateJooq` を Flyway 適用に置き換える）。生成物はコミットしない。
- [ ] **T007** ローカル用の seed の仕組みを作る（ローカル用 profile でだけ適用される別の場所のマイグレーション）。中身は各段階で足す。

## API の生成

- [ ] **T008** OpenAPI Generator の Gradle プラグインで、`api/openapi/admin.yaml` と `store.yaml` から Spring のインターフェースとモデルを生成する。
  - 設定：`interfaceOnly`、`useSpringBoot4`、`useJackson3`、`useTags`。
  - パッケージ：`com.ecsite.api.admin`、`com.ecsite.api.store`（生成物はコミットしない）。
  - 完了：生成コードを含めてコンパイルが通る。
- [ ] **T009** 権限の記載漏れを検出するテストを作る：`admin.yaml` のすべての操作に `x-required-permission` があり、`resource:action` の形であること（constitution 10）。

## 共通部品（`common`）

- [ ] **T010** [テスト] 例外とエラーレスポンスの統合テスト。
  - 入力検証エラー：400、`errors` にフィールドごとのエラーが入る。
  - 業務ルール違反：400。見つからない：404。バージョン不一致：409（`CONCURRENT_MODIFICATION`）。想定外の例外：500。
  - すべてのエラーで `errorCode`、`message`（日本語）、`traceId` が入り、Content-Type が `application/problem+json`。
- [ ] **T011** 例外体系を MyIAM から移植する（`ErrorCode`、`ErrorType`、`BusinessException`、`SystemException`、`GlobalExceptionHandler`、i18n メッセージ）。MyIAM との違い：`traceId` をすべてのエラーで返す。メッセージは日本語。T010 を通す。
- [ ] **T012** [テスト] 金額（`Money`）の単体テスト：円単位の整数、負数の拒否、加算。
- [ ] **T013** 金額を実装する。T012 を通す。
- [ ] **T014** [テスト] ページングの単体テスト：`page` は 1 始まり、`size` は既定 20・最大 100、総件数からの総ページ数の計算。
- [ ] **T015** ページングの共通部品を実装する（リクエストの変換と、レスポンスの組み立て）。T014 を通す。
- [ ] **T016** `Clock` を Bean として定義する。統合テストでは固定の `Clock` に差し替えられるようにする。

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
