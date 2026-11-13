# 共通コード・ログ・可観測性

バックエンドのモジュールの外にある共通のコード（`backend/ecsite/src/main/java/com/ecsite/common/`、`config/`）の一覧と、ログ・メトリクス・トレースの形を書く。新しい機能を追加するときに、使えるものとログの書き方を調べるための文書で、今あるものを説明する。規則は書き方の文書にあり、ここでは重複して書かない。

| 知りたいこと | 文書 |
|--------------|------|
| 例外と業務のエラー、共通の型、入力の検証のメッセージ | [バックエンドの書き方](../05-開発規則/backend-coding.md)の「共通の型」、[バックエンドの書き方](../05-開発規則/backend-coding.md)の「入力の検証とエラー」 |
| イベントの再発行、定期ジョブ、分散ロック | [バックエンドのアーキテクチャ](backend-architecture.md)の「水平スケール」 |
| 認証・認可、運用ポートを外部に公開しない前提 | [バックエンドのアーキテクチャ](backend-architecture.md)の「認証・認可」 |
| エラーの応答の形 | [API の規約](../05-開発規則/api-conventions.md)の「エラーの本文」 |
| 環境変数 | [環境変数](../06-手順書/environment.md)の「バックエンド」 |

## 共通コード（common/）

`common/` と `config/` はモジュールではない。`application.yml` の `spring.modulith.detection-strategy: explicitly-annotated` により、`package-info.java` に `@ApplicationModule` を付けたパッケージだけがモジュールになるためである。`common/` はどのモジュールからも使える。`common/` と `config/` はモジュールに依存せず、モジュールも `config/` に依存しない（[バックエンドのアーキテクチャ](backend-architecture.md)の「層とパッケージ」）。

| パッケージ | 置くもの |
|------------|----------|
| `common/error/` | エラーの定義の基底と、例外から Problem Details への変換（`error/exception/`、`error/handler/`） |
| `common/message/` | メッセージのコードとパラメータから日本語の文を作るクラス |
| `common/model/base/` | 集約とドメインイベントの基底 |
| `common/model/value/` | 規則を持つ共通の値（金額、文字の項目、業務の日付、操作した運営者）。業務の日付の時間帯（日本時間）は 1 か所で決める |
| `common/paging/` | ページ指定と一覧の結果 |
| `common/persistence/` | DB の制約の違反を業務のエラーに変えるクラス |
| `common/presentation/` | API の日時の変換、作成の応答、店舗 API の価格など、Controller と応答への変換の共通のクラス |
| `common/scheduling/` | 期限切れのデータの片付けの共通の処理。`@Scheduled` と `@SchedulerLock` は各ジョブに残す |
| `common/security/` | 認証情報から、店舗 API の会員と管理 API の運営者を取り出すクラス。Spring Security の型は application・domain に出さない（[バックエンドのアーキテクチャ](backend-architecture.md)の「業務のコードとの境界」） |

- common に置く基準は、2 つ以上のモジュールが使うものだけ。ただし、エラーの応答、ログ、入力の検証のメッセージ、認証の情報の取り出しは、横断の仕組みとして除く（[バックエンドの書き方](../05-開発規則/backend-coding.md)の「共通の型」）。

## 設定（config/）

| パッケージ | 役割 |
|------------|------|
| `config/`（直下） | 時刻（`Clock`）、API のパスのプレフィックス、Bean Validation、OpenAPI の列挙型の変換、定期ジョブの起動と分散ロックの設定 |
| `config/event/` | イベントの再発行と片付け |
| `config/security/` | 認証・認可の設定。管理 API の権限と店舗 API の token の要否を、契約の操作ごとに適用する |

- イベント・ジョブ・分散ロックの動きと設定の値は [バックエンドのアーキテクチャ](backend-architecture.md)の「水平スケール」に書く。
- 認証・認可の方式は [バックエンドのアーキテクチャ](backend-architecture.md)の「認証・認可」に、ロールと権限の対応は [ロールと権限の対応表](role-permissions.md) に書く。

## ログ

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
- 文書とコードでログに出力しないとしているものは、次の 4 つ。イベントの中身、集約の復元の失敗の値、Repository の変換の失敗の値、カートと会員を作れないときの ID。
  - 止めたイベントのログに出力するものは、[バックエンドのアーキテクチャ](backend-architecture.md)の「水平スケール」。
  - 集約の復元に失敗したとき、`RestoreGuard` が投げる `SystemException` のメッセージは、集約の名前（`category` など）と原因の例外の種類だけを持つ。原因の例外は、元の例外の種類とスタックトレースだけを持つ例外に置き換え、原因の原因と抑制した例外も同じく置き換える。保存した状態と原因の例外のメッセージには値（個人情報を含みうる）が入りうるため。HTTP の要求のほか、定期のジョブとイベントのリスナーも例外の連鎖をそのままログに出すので、ログに出力する箇所でなく、例外を作る箇所で除く。集約の ID も書かない（この補助は共通の部品で、後に加わる集約の ID が利用者を指す値になりうるため）。要求との対応は、トレース ID で追跡する。
  - Repository が DB の値を変換する処理のうち、例外のメッセージに値が入るもの（列挙の変換、注文番号、注文の明細の軸の値）は、読み取りのモデルも含めて `RestoreGuard` で包む。包むのは値が入る変換だけで、金額の検証の業務のエラーは包まない。業務のエラーのログはエラーコードだけで値が出ず、包むと応答が変わるため。
  - カートと会員を作れないときの `SystemException` のメッセージに、カート ID、会員 ID、IdP のアカウントの ID を書かない。利用者を指す値のため。
- 秘密の値（Stripe の鍵、物流サービスの署名の鍵）は、環境変数で渡して Git に入れない（`application.yml` のコメント）。ログに出力してよいものと出力しないものを定める規則は、書き方の文書にない。

## メトリクスと死活監視

- 運用ポート（既定 9080、`MANAGEMENT_SERVER_PORT`）で、`health` と `prometheus` だけを公開する（`management.endpoints.web.exposure.include`）。業務ポート（8080）では公開せず、運用ポートは外部に公開しない（[バックエンドのアーキテクチャ](backend-architecture.md)の「公開ネットワークへのデプロイの前提」）。
- `health` は liveness（`/actuator/health/liveness`）と readiness（`/actuator/health/readiness`）を有効にし、UP / DOWN だけを返す（`show-details: never`）。
- ローカルの profile だけ、`beans`、`conditions`、`env`、`mappings`、`modulith` も公開し、`health` の詳細を返す。
- 独自のメトリクスは、イベントの未完了の件数 `ecsite.events.incomplete` と止めた件数 `ecsite.events.stopped` の 2 つ（`EventPublicationMetrics` の `Gauge`）。値の設定と見方は[バックエンドのアーキテクチャ](backend-architecture.md)の「水平スケール」。`MeterRegistry` を使うコードは `EventPublicationMetrics` だけ。

## トレース

- 分散トレースの送信先は Zipkin。送信の有無、送信先、記録するリクエストの割合の設定と既定値は[環境変数](../06-手順書/environment.md)の「バックエンド」。共通の設定は送信先を書かないので、指定しなければ Spring Boot の既定の送信先に送る。
- ローカルの profile は送信を有効にし、割合を 1.0 にして、compose の Zipkin（URL は[開発環境の起動](../06-手順書/setup.md)の「起動」）で確認する。E2E の profile はローカルの profile と一緒に使うので、設定も同じになる。
- トレース ID は、ログ（「ログ」）とエラーの応答の `traceId` に入る。問い合わせのトレース ID から、ログとトレースを関連づけられる。
