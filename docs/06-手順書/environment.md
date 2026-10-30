# 環境変数

本番相当の環境では profile を付けずに起動し、環境に依存する値を環境変数で渡す。ローカルの既定値は `application-local.yml` にある。

profile の設定ファイルは本番の jar に含まれない（[バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)の「外部連携」）。

| profile | 使う場面 | 指定する profile |
|---------|----------|--------|
| `local` | ローカルの開発。compose のサービスに接続し、決済と物流は mock、起動のときに seed を適用する | 単独（`local`） |
| `e2e` | E2E。DB・IdP・画像のバケット・ポートを E2E 用に分離し、ローカルのデータを変えない（[テストの実行方法](running-tests.md)） | `local,e2e` |
| `stripe` | Stripe の sandbox での確認。決済サービスを Stripe にし、秘密情報を `/.local/stripe.env` から読む（[開発環境の起動](setup.md)の「Stripe で動かす」） | `local,stripe` |

## 1. バックエンド

| 環境変数 | 内容 | 既定 |
|----------|------|------|
| `SPRING_DATASOURCE_URL`、`_USERNAME`、`_PASSWORD` | DB の接続先 | — |
| `SERVER_PORT` | 業務ポート（Spring Boot の `server.port`）。`slot.mjs run` が設定する（[開発環境の起動](setup.md)の「スロット」） | 8080（E2E は 8090） |
| `MANAGEMENT_SERVER_PORT` | 運用ポート（死活監視とメトリクス）。業務ポートと分け、外部に公開しない | 9080（E2E は 9090） |
| `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENABLED` | トレースの送信を有効にする | false（local と E2E は true） |
| `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT` | 送信を有効にしたときの送信先 | `http://localhost:9411/api/v2/spans`（Spring Boot の既定） |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | 記録するリクエストの割合 | 0.1 |
| `ECSITE_SECURITY_ADMIN_ISSUER_URI`、`_AUDIENCE` | 管理 API が受け付ける token の issuer（運営者の realm）と audience | 必須 |
| `ECSITE_SECURITY_STORE_ISSUER_URI`、`_AUDIENCE` | 店舗 API が受け付ける token の issuer（消費者の realm）と audience | 必須 |
| `ECSITE_MEDIA_STORAGE_INCOMING_BUCKET`、`_PUBLIC_BUCKET` | 画像のアップロード（非公開）・配信（公開）のバケット | 必須 |
| `ECSITE_MEDIA_STORAGE_REGION`、`_PUBLIC_BASE_URL` | バケットのリージョン、配信の基準 URL（CDN） | 必須 |
| `ECSITE_MEDIA_STORAGE_ENDPOINT`、`_PATH_STYLE_ACCESS` | S3 互換ストレージを使うときだけ（AWS では設定せず、資格情報は IAM ロール） | —、false |
| `ECSITE_MEDIA_STORAGE_ACCESS_KEY_ID`、`_SECRET_ACCESS_KEY` | ストレージの資格情報（アクセスキー ID とシークレット）。ローカル専用で、2 つを一緒に指定する。本番は空にして IAM ロールを使う | — |
| `ECSITE_MEDIA_STORAGE_UPLOAD_URL_TTL` | アップロードの署名付き URL の有効期間 | 5m |
| `ECSITE_MEDIA_STORAGE_ATTEMPT_TIMEOUT`、`_CALL_TIMEOUT` | ストレージの呼び出しのタイムアウト（1 回の試行、SDK の再試行を含めた全体） | 10s、30s |
| `ECSITE_MEDIA_CONVERTER_BASE_URL`、`_KEY`、`_SALT` | imgproxy の URL と、URL の署名鍵・ソルト（16 進。imgproxy の `IMGPROXY_KEY`・`IMGPROXY_SALT` と同じ値） | 必須 |
| `ECSITE_MEDIA_CONVERTER_QUALITY`、`_TIMEOUT` | WebP の品質、変換のタイムアウト | 82、30s |
| `ECSITE_ORDER_SHIPPING_TAX_CATEGORY_ID` | 送料に適用する税区分の ID（標準税率） | 必須 |
| `ECSITE_ORDER_SHIPPING_FEE`、`_RESERVATION_TTL` | 送料（税込、円）、引当の期限 | 1000、15m |
| `ECSITE_STOREFRONT_SHOP_CONTACT_EMAIL` | 問い合わせ先のメールアドレス（却下された注文に表示する） | 必須 |
| `ECSITE_REPORTING_FISCAL_YEAR_START_MONTH` | 会計年度の始まりの月（1〜12）。売上の推移の四半期と年の区切りに使う | 4 |
| `ECSITE_PAYMENT_RETURN_URL` | 支払画面からの戻り先（店舗画面の注文の画面）の URL。`{orderId}` に注文 ID が入る | 必須 |
| `ECSITE_PAYMENT_MOCK_ENABLED` | 決済の mock フラグ。`true` なら mock、それ以外なら Stripe を使う。本番では有効にしない | false |
| `ECSITE_PAYMENT_DEADLINE`、`_EXPIRY_INTERVAL` | 支払の期限（注文日時からの時間）、期限切れのジョブの間隔。支払の期限は 30 分を超えると起動に失敗する | 30m、1m |
| `ECSITE_PAYMENT_AWAITING_AUTHORIZATION_WARNING` | 通知を待つ支払のうち、待ち始めてからこの時間を過ぎたものの件数を、warn のログに出力する | 1h |
| `ECSITE_PAYMENT_AUTHORIZATION_VALIDITY`、`_AUTHORIZATION_WARNING` | 与信の有効期間（与信の時刻からの時間）、「期限が近い」とする期間（与信期限までの残り時間） | 7d、2d |
| `ECSITE_PAYMENT_MOCK_BASE_URL` | mock の支払画面を表示するバックエンドの URL（ブラウザーから開ける URL） | 決済の mock フラグを有効にしたら必須 |
| `STRIPE_SECRET_KEY`、`STRIPE_WEBHOOK_SECRET` | Stripe の API のシークレットキー、通知の署名鍵。Git にコミットしない。ローカルは `/.local/stripe.env`（[開発環境の起動](setup.md)の「Stripe で動かす」） | 決済の mock フラグを有効にしなければ必須 |
| `ECSITE_PAYMENT_STRIPE_CONNECT_TIMEOUT`、`_READ_TIMEOUT` | Stripe の API への接続、応答のタイムアウト（応答は 1 回の要求ごと。SDK の自動の再試行は別に数える） | 5s、20s |
| `ECSITE_SHIPPING_MOCK_ENABLED` | 物流の mock フラグ。本番の実装がまだないため、`true` でなければ起動に失敗する。本番では有効にしない | false |
| `CARRIER_WEBHOOK_SECRET` | 物流サービスの通知の署名鍵。Git にコミットしない。ローカルと統合テストでは、テスト用の値を設定ファイルに書く | 必須 |
| `ECSITE_NOTIFICATION_FROM` | 消費者へのメール（出荷通知メール）の送信元のアドレス | 必須 |
| `SPRING_MAIL_HOST`、`_PORT`、`_USERNAME`、`_PASSWORD` | メールを送る SMTP サーバーの接続先と認証（Spring Boot の設定）。ローカルは compose の Mailpit（`localhost:2525`。ポートは `MAILPIT_SMTP_PORT` で変える） | host は必須、port は 25 |
| `ECSITE_SCHEDULING_ENABLED` | 定期ジョブを自動で実行するか | true |
| `ECSITE_SCHEDULING_EVENT_RESUBMISSION_INTERVAL`、`_MIN_AGE`、`_MAX_ATTEMPTS` | 未完了のイベントを再発行する間隔、再発行の対象にする経過時間（発行から）の下限、処理の回数（発行時の 1 回と再発行の回数）の上限 | 1m、5m、60 |
| `ECSITE_SCHEDULING_EVENT_CLEANUP_INTERVAL`、`_RETENTION` | 処理の済んだイベントを削除する間隔、保存期間（処理を終えた時刻から） | 1h、7d |
| `ECSITE_INVENTORY_CLEANUP_INTERVAL`、`_RETENTION`、`_BATCH_SIZE` | 期限切れの引当を削除する間隔、保存期間（期限から）、1 回に削除する件数の上限 | 1h、1d、1000 |
| `ECSITE_ORDER_CLEANUP_INTERVAL`、`_RETENTION`、`_BATCH_SIZE` | 期限切れの注文手続きを削除する間隔、保存期間（期限から）、1 回に削除する件数の上限 | 1h、1d、1000 |
| `ECSITE_CART_CLEANUP_INTERVAL`、`_RETENTION`、`_BATCH_SIZE` | 期限切れのカートを削除する間隔、保存期間（期限から）、1 回に削除する件数の上限 | 1h、0d、1000 |

- `_USERNAME` などは、同じ行の 1 つ目の名前の接頭辞を省略したもの（`SPRING_DATASOURCE_USERNAME` など）。
- マイグレーションは起動時には適用せず、デプロイごとに 1 回だけ適用する。
- ログは標準出力に JSON（Elastic Common Schema）で出力される。
- 公開ネットワークへのデプロイは、その前提（[バックエンドのアーキテクチャ](../02-設計/backend-architecture.md)の「公開ネットワークへのデプロイの前提」）を決めてから行う。

## 2. 管理画面

ビルド時に埋め込む。本番ビルドでは、`VITE_OIDC_AUTHORITY` と `VITE_OIDC_CLIENT_ID` がなければビルドに失敗する（既定値は開発サーバーとテストでだけ使う）。

| 環境変数 | 内容 | 既定 |
|----------|------|------|
| `VITE_OIDC_AUTHORITY` | IdP（運営者の realm） | `http://localhost:8180/realms/ec-admin` |
| `VITE_OIDC_CLIENT_ID` | client ID | `ec-admin-web` |
| `ADMIN_API_PROXY_TARGET` | 開発サーバーと `vite preview` の `/api` の転送先（E2E は 8090） | `http://localhost:8080` |

## 3. 店舗画面

| 環境変数 | 内容 | 既定 ※1 |
|----------|------|---------|
| `STORE_API_BASE_URL` | 店舗 API の接続先 | `http://localhost:8080/api/store` |
| `STORE_API_REVALIDATE_SECONDS` | API の結果をキャッシュする秒数（E2E で短くする。本番相当では設定しない） ※2 | 60 |
| `STORE_API_TIMEOUT_SECONDS` | 店舗 API の応答のタイムアウト（秒）。正の数でなければ起動に失敗する | 30 |
| `AUTH_KEYCLOAK_TIMEOUT_SECONDS` | IdP（ログイン、token の更新）の応答のタイムアウト（秒）。正の数でなければ起動に失敗する | 10 |
| `AUTH_KEYCLOAK_ISSUER` | IdP（消費者の realm） | `http://localhost:8180/realms/ec-store` |
| `AUTH_KEYCLOAK_ID`、`AUTH_KEYCLOAK_SECRET` | client ID と secret | `ec-store-web`、ローカル用の値 |
| `AUTH_SECRET` | セッションの Cookie の暗号化キー | ローカル用の値 |
| `AUTH_URL` | 店舗画面を公開する URL（origin）。ログイン・ログアウトの戻り先を生成する | `http://localhost:3000` |
| `STORE_COOKIE_PREFIX` | 店舗画面の Cookie（Auth.js のセッション・CSRF・PKCE など、カート）の名前の前に付ける接頭辞。HTTPS の `__Secure-`・`__Host-` があれば、その後ろに付ける。英数字と `-`、`_` だけ（それ以外は起動に失敗する）。任意。`slot.mjs run` が設定する（[開発環境の起動](setup.md)の「スロット」） | 空（今の名前のまま） |

※1：既定値はローカルと CI 向けの値で、開発モードとテストでだけ使う。本番モードでは、`STORE_API_REVALIDATE_SECONDS`、`STORE_API_TIMEOUT_SECONDS`、`AUTH_KEYCLOAK_TIMEOUT_SECONDS` 以外がなければ起動に失敗する。

※2：0 以上の整数。0 はキャッシュしない設定。小数、負の数、数でない値は起動に失敗する。
