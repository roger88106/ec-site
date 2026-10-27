# 共通コード・ログ・可観測性

バックエンドのモジュールの外にある共通のコード（`backend/ecsite/src/main/java/com/ecsite/common/`、`config/`）の一覧と、ログ・メトリクス・トレースの形を書く。新しい機能を追加するときに、使えるものとログの書き方を調べるための文書で、今あるものを説明する。規則は書き方の文書にあり、ここでは重複して書かない。

| 知りたいこと | 文書 |
|--------------|------|
| 例外と業務のエラー、共通の型、入力の検証のメッセージ | [バックエンドの書き方](../rules/backend-coding.md) 第 1.5 節、第 3 節 |
| イベントの再発行、定期ジョブ、分散ロック | [バックエンドのアーキテクチャ](backend-architecture.md) 第 5 節 |
| 認証・認可、運用ポートを外部に公開しない前提 | [バックエンドのアーキテクチャ](backend-architecture.md) 第 7 節 |
| エラーの応答の形 | [API の規約](../rules/api-conventions.md) 第 5 節 |
| 環境変数 | [環境変数](../guides/environment.md) 第 1 節 |

## 1. 共通コード（common/）

`common/` と `config/` はモジュールではない。`application.yml` の `spring.modulith.detection-strategy: explicitly-annotated` により、`package-info.java` に `@ApplicationModule` を付けたパッケージだけがモジュールになるためである。`common/` はどのモジュールからも使える。`common/` と `config/` はモジュールに依存せず、モジュールも `config/` に依存しない（[バックエンドの書き方](../rules/backend-coding.md) 第 1.1 節）。

| パッケージ | 置くもの | 使う場面 |
|------------|----------|----------|
| `common/error/` | エラーの定義の基底。`ErrorCodeProvider`（各モジュールの `XxxErrorCode` が実装する）、`ErrorCode`、`ErrorType`（HTTP ステータスを決める種類）、全モジュール共通の `CommonErrorCode`、エラーのコードとメッセージを組にした `ErrorDetail`、形の検証の誤りを業務のエラーに対応づける `ValidationErrorMapping` | モジュールのエラーの定義を作る。メッセージのキーは `ErrorCodeProvider.errorCode` が作る |
| `common/error/exception/` | 例外の基底 `BaseException`、業務の例外 `BusinessException`、システムの例外 `SystemException` | 業務の規則の違反は `BusinessException.of(...)` で投げる |
| `common/error/handler/` | `GlobalExceptionHandler`（例外を Problem Details に変換する唯一の場所）、`TraceIdResolver`、`ValidationMessages`（入力の検証の文を作る） | 業務のコードは直接使わない。第 3 節 |
| `common/message/` | `MessageHelper`（メッセージのコードとパラメータから日本語の文を作る）、`I18nMessage`、`ErrorMessage` | エラーの定義と例外が持つ。エラー以外の文（メールの文面など）は `MessageHelper` で作る |
| `common/model/base/` | 集約の基底 `AggregateRoot`、`DomainEvent`、集約のバージョンの比較と復元の失敗の扱い `AggregateGuards` | 集約とイベントを定義する |
| `common/model/value/` | 規則を持つ共通の値。`Money`（円）、`BoundedText`（長さの判定。code point で数える）、`BusinessTime`（業務の日付に切る時間帯）、`Operator`（操作した運営者の写し） | 金額、文字の項目、業務の日付、操作の記録 |
| `common/paging/` | `PageRequest`（ページは 1 始まり、件数は既定 20・最大 100）、`PageResult` | 一覧の API。応答の型への変換は presentation で行う |
| `common/persistence/` | `ConstraintViolationTranslator`（DB の制約の違反を業務のエラーに変える） | リポジトリの実装 |
| `common/presentation/` | `ApiDateTimes`（API の日時と `Instant` の変換）、`CreatedResponses`（作成の応答 201）、`StorePrices`（店舗 API の税込・税抜の価格） | Controller と応答への変換 |
| `common/scheduling/` | `ExpiredDataCleanup`（期限切れのデータの片付けの共通の処理。件数の上限ごとに削除し、削除した件数が上限と等しい間繰り返す。合計の件数はログに出力する） | カート、在庫の引当、注文手続きの片付けのジョブ。`@Scheduled` と `@SchedulerLock` は各ジョブに残す |
| `common/security/` | `CurrentMember`（店舗 API の token の会員）、`CurrentOperator`（管理 API の token の運営者）、`AuthenticatedMember`、`CurrentToken`（認証情報から token を取り出す。`find()` は token がなければ空の `Optional` を返し、`require()` は未認証のエラーにする） | presentation 層で呼び出し元を取り出す。Spring Security の型は application・domain に出さない（[バックエンドのアーキテクチャ](backend-architecture.md) 第 7.2 節） |

- common に置く基準は、2 つ以上のモジュールが使うものだけ。ただし、エラーの応答、ログ、入力の検証のメッセージ、認証の情報の取り出しは、横断の仕組みとして除く（[バックエンドの書き方](../rules/backend-coding.md) 第 1.5 節）。

## 2. 設定（config/）

| クラス | 役割 |
|--------|------|
| `ClockConfig` | アプリの `Clock`。現在時刻はここから取る（`Instant.now()` を直接呼ばない） |
| `ApiPathConfig` | 生成したインターフェースの実装に付ける API のパスのプレフィックス（管理 API は `/api/admin`、店舗 API は `/api/store`） |
| `ValidationConfig` | Bean Validation のバリデーター。制約のメッセージを `messages.properties` から解決する |
| `OpenApiEnumConverterConfig` | クエリパラメータの文字列を、OpenAPI から生成した列挙型の `fromValue` で変換する |
| `event/` の `EventResubmissionConfig`、`EventResubmissionJob`、`EventPublicationRecords`、`EventPublicationMetrics` | 未完了のイベントを定期ジョブで再発行し、再発行を停止したイベントにマークを付け、件数をメトリクスとして公開する。`EventPublicationRecords` は、イベントの記録のテーブルを読み書きする（片付けのジョブが使う削除を含む） |
| `event/` の `EventCleanupConfig`、`EventCleanupJob` | 処理の済んだイベントの記録を、保存期間を過ぎたら削除する。削除の SQL は `EventPublicationRecords` に置く |
| `SchedulingConfig`、`SchedulerLockConfig` | 定期ジョブの起動（`ecsite.scheduling.enabled`）と、同じジョブを 1 インスタンスだけで動かす分散ロック |
| `security/` | 認証・認可。`SecurityConfig` は filter chain、`RealmKeys` は token の検証、`PermissionConverter` は権限の変換。管理 API の権限は `AdminOperationPermissions` と `AdminPermissionAuthorizationManager`、店舗 API の token の要否は `StoreOperationSecurity` と `StoreTokenAuthorizationManager`。`SecurityProblemResponder` は認証・認可の失敗の応答、`SecurityProperties` は認証の設定の値の型、`OpenApiOperations` は契約の操作ごとの値の一覧、`MeController` は `GET /me` |

- イベント・ジョブ・分散ロックの動きと設定の値は [バックエンドのアーキテクチャ](backend-architecture.md) 第 5 節に書く。
- 認証・認可の方式は [バックエンドのアーキテクチャ](backend-architecture.md) 第 7 節に、ロールと権限の対応は [ロールと権限の対応表](role-permissions.md) に書く。

## 3. ログ

- 標準出力に JSON（Elastic Common Schema）で出力する（`application.yml` の `logging.structured.format.console: ecs`）。トレース ID と span ID が入る。
- ローカルの profile（`application-local.yml`）は、読みやすさを優先して JSON にせず通常のテキストで出力する（トレース ID は行に入る）。`com.ecsite` を DEBUG にし、SQL とバインドパラメータ（`org.jooq.tools.LoggerListener`）も出力する。
- エラーの応答の `traceId` とエラーのログは、同じ ID を使う。`TraceIdResolver` は現在の span のトレース ID を返し、span がない（トレーシングが無効、サンプリングの対象外）ときは 32 桁の 16 進数を新しく作る。

`GlobalExceptionHandler` が出力するログのレベルは、例外の種類で決まる。

| 例外 | レベル | 応答 | ログに出力するもの |
|------|--------|------|----------------|
| `BusinessException` | debug | 業務のエラーの種類から 4xx | エラーコード、トレース ID |
| 入力の検証のエラー、Spring MVC の標準の例外（4xx） | debug | 400、404、405 など | 検証の誤りの一覧、または例外とステータス、トレース ID |
| `SystemException`（外部のサービスに接続できないエラー） | warn | 502 | エラーコード、トレース ID、例外のスタックトレース |
| `SystemException`（それ以外）、想定外の例外、Spring MVC の 5xx | error | 500 | トレース ID、例外のスタックトレース。応答には原因を含めず、共通のシステムエラーを返す |

- 業務のコードがログを書くときは、Lombok の `@Slf4j` を使う。定期ジョブの結果（片付けた件数）は info、継続できる異常（外部サービスの通知の拒否、次回に持ち越す処理）は warn、止めたイベントは error で出力している。
- ログに出力しないものとして文書とコードに書いてあるのは、イベントの中身と、集約の復元の失敗の値の 2 つ。
  - 止めたイベントのログには、イベントの種類・イベント ID・リスナー・処理の回数だけを出力する（[バックエンドのアーキテクチャ](backend-architecture.md) 第 5 節）。
  - 集約の復元に失敗したとき、`AggregateGuards` が投げる `SystemException` のメッセージは、集約の名前（`category` など）と原因の例外の種類だけを持つ。原因の例外は、元の例外の種類とスタックトレースだけを持つ例外に置き換え、原因の原因と抑制した例外も同じく置き換える。保存した状態と原因の例外のメッセージには値（個人情報を含みうる）が入りうるため。HTTP の要求のほか、定期のジョブとイベントのリスナーも例外の連鎖をそのままログに出すので、ログに出す所でなく例外を作る所で除く。集約の ID も書かない（この補助は共通の部品で、後に加わる集約の ID が利用者を指す値になりうるため）。要求との対応は、トレース ID で追う。
- 秘密の値（Stripe の鍵、物流サービスの署名の鍵）は、環境変数で渡して Git に入れない（`application.yml` のコメント）。ログに出力してよいものと出力しないものを定める規則は、書き方の文書にない。

## 4. メトリクスと死活監視

- 運用ポート（既定 9080、`MANAGEMENT_SERVER_PORT`）で、`health` と `prometheus` だけを公開する（`management.endpoints.web.exposure.include`）。業務ポート（8080）では公開せず、運用ポートは外部に公開しない（[バックエンドのアーキテクチャ](backend-architecture.md) 第 7.3 節）。
- `health` は liveness（`/actuator/health/liveness`）と readiness（`/actuator/health/readiness`）を有効にし、UP / DOWN だけを返す（`show-details: never`）。
- ローカルの profile だけ、`beans`、`conditions`、`env`、`mappings`、`modulith` も公開し、`health` の詳細を返す。
- 独自のメトリクスは、イベントの未完了の件数 `ecsite.events.incomplete` と止めた件数 `ecsite.events.stopped` の 2 つ（`EventPublicationMetrics` の `Gauge`）。再発行のジョブが再集計して値を設定するので、収集のたびに DB を読まない。値を更新するのはジョブを実行したインスタンスだけなので、全インスタンスの最大値で確認する（[バックエンドのアーキテクチャ](backend-architecture.md) 第 5 節）。`MeterRegistry` を使うコードは `EventPublicationMetrics` だけ。

## 5. トレース

- 分散トレースの送信先は Zipkin。記録するリクエストの割合は既定 0.1 で、`MANAGEMENT_TRACING_SAMPLING_PROBABILITY` で変える。
- 送信は既定で無効で、`MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENABLED` を true にしたときだけ送る。送信先は `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT` で指定する。共通の設定は送信先を書かないので、指定しなければ Spring Boot の既定の `http://localhost:9411/api/v2/spans` に送る。
- ローカルの profile は送信を有効にし、割合を 1.0 にして全リクエストを記録し、compose の Zipkin（`http://localhost:9411`）で確認する。E2E の profile はローカルの profile と一緒に使うので、送信と割合もローカルと同じになる。
- トレース ID は、ログ（第 3 節）とエラーの応答の `traceId` に入る。問い合わせのトレース ID から、ログとトレースを関連づけられる。
