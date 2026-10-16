# 環境変数

本番相当の環境では profile を付けずに起動し、環境に依存する値を環境変数で渡す。ローカルの既定値は `application-local.yml` にある。

## 1. バックエンド

| 環境変数 | 内容 | 既定 |
|----------|------|------|
| `SPRING_DATASOURCE_URL`、`_USERNAME`、`_PASSWORD` | DB の接続先 | — |
| `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT` | トレースの送信先 | `http://localhost:9411/api/v2/spans` |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | 記録するリクエストの割合 | 0.1 |
| `ECSITE_SECURITY_ADMIN_ISSUER_URI`、`_AUDIENCE` | 管理 API が受け付ける token の issuer（運営者の realm）と audience | 必須 |
| `ECSITE_SECURITY_STORE_ISSUER_URI`、`_AUDIENCE` | 店舗 API が受け付ける token の issuer（消費者の realm）と audience | 必須 |
| `ECSITE_MEDIA_STORAGE_INCOMING_BUCKET`、`_PUBLIC_BUCKET` | 画像の受付（非公開）・配信（公開）のバケット | 必須 |
| `ECSITE_MEDIA_STORAGE_REGION`、`_PUBLIC_BASE_URL` | バケットのリージョン、配信の基準 URL（CDN） | 必須 |
| `ECSITE_MEDIA_STORAGE_ENDPOINT`、`_PATH_STYLE_ACCESS` | S3 互換ストレージを使うときだけ（AWS では設定せず、資格情報は IAM ロール） | — |
| `ECSITE_MEDIA_STORAGE_UPLOAD_URL_TTL` | アップロードの署名付き URL の有効期間 | 5m |
| `ECSITE_MEDIA_CONVERTER_BASE_URL`、`_KEY`、`_SALT` | imgproxy の URL と、URL の署名の鍵・ソルト（16 進。imgproxy の `IMGPROXY_KEY`・`IMGPROXY_SALT` と同じ値） | 必須 |
| `ECSITE_MEDIA_CONVERTER_QUALITY`、`_TIMEOUT` | WebP の品質、変換を待つ時間の上限 | 82、30s |
| `ECSITE_ORDER_SHIPPING_TAX_CATEGORY_ID` | 送料に適用する税区分の ID（標準税率） | 必須 |
| `ECSITE_ORDER_SHIPPING_FEE`、`_RESERVATION_TTL` | 送料（税込、円）、引当の期限 | 1000、15m |
| `ECSITE_STOREFRONT_SHOP_CONTACT_EMAIL` | 問い合わせ先のメールアドレス（却下された注文に示す） | 必須 |
| `ECSITE_PAYMENT_RETURN_URL` | 支払の画面から戻る店舗の注文の画面の URL。`{orderId}` に注文 ID が入る | 必須 |
| `ECSITE_PAYMENT_GATEWAY`、`_DEADLINE`、`_EXPIRY_INTERVAL` | 決済サービス（`mock`・`stripe`）、支払の期限（注文日時からの時間）、期限切れのジョブの間隔 | mock、30m、PT1M |
| `ECSITE_PAYMENT_AUTHORIZATION_VALIDITY`、`_AUTHORIZATION_WARNING` | 与信の有効期間（与信の時刻からの時間）、与信期限のどれだけ前から「期限が近い」とするか | 7d、2d |
| `ECSITE_PAYMENT_MOCK_BASE_URL` | mock の支払の画面を出すバックエンドの URL（ブラウザーから開ける URL） | 決済サービスが mock なら必須 |
| `STRIPE_SECRET_KEY`、`STRIPE_WEBHOOK_SECRET` | Stripe の API の秘密の鍵、通知の署名の鍵。Git に入れない。ローカルは `/.local/stripe.env`（[開発環境の起動](setup.md) 第 5 節） | 決済サービスが stripe なら必須 |
| `ECSITE_SHIPPING_CARRIER` | 物流サービス（今は `mock` だけ） | mock |
| `CARRIER_WEBHOOK_SECRET` | 物流サービスの通知の署名の鍵。Git に入れない。ローカルと統合テストは試験用の値を設定ファイルに持つ | 必須 |
| `ECSITE_NOTIFICATION_FROM` | 消費者へのメール（出荷通知メール）の送信元のアドレス | 必須 |
| `SPRING_MAIL_HOST`、`_PORT`、`_USERNAME`、`_PASSWORD` | メールを送る SMTP サーバーの接続先と認証（Spring Boot の設定）。ローカルは compose の Mailpit（`localhost:2525`。ポートは `MAILPIT_SMTP_PORT` で変える） | host は必須、port は 25 |
| `ECSITE_SCHEDULING_ENABLED` | 定期ジョブを自動で動かすか | true |
| `ECSITE_SCHEDULING_EVENT_RESUBMISSION_INTERVAL`、`_MIN_AGE` | 未完了のイベントを再発行する間隔、再発行するイベントの発行からの時間の下限 | PT1M、5m |

- `_USERNAME` などは、同じ行の 1 つ目の名前の接頭辞を省いたもの（`SPRING_DATASOURCE_USERNAME` など）。
- マイグレーションは起動時には流さず、デプロイごとに 1 回だけ実行する。
- ログは標準出力に JSON（Elastic Common Schema）で出る。
- 公開ネットワークへのデプロイは、その前提（[バックエンドのアーキテクチャ](../backend/architecture.md) 第 7.3 節）を決めてから行う。

## 2. 管理画面

ビルド時に埋め込む。

| 環境変数 | 内容 | 既定 |
|----------|------|------|
| `VITE_OIDC_AUTHORITY` | IdP（運営者の realm） | `http://localhost:8180/realms/ec-admin` |
| `VITE_OIDC_CLIENT_ID` | client の ID | `ec-admin-web` |
| `ADMIN_API_PROXY_TARGET` | 開発サーバーと `vite preview` の `/api` の転送先（E2E は 8090） | `http://localhost:8080` |

## 3. 店舗画面

| 環境変数 | 内容 | 既定 ※1 |
|----------|------|---------|
| `STORE_API_BASE_URL` | 店舗 API の接続先 | `http://localhost:8080/api/store` |
| `STORE_API_REVALIDATE_SECONDS` | API の結果のキャッシュの秒数（E2E で短くする。本番相当では設定しない） | 60 |
| `AUTH_KEYCLOAK_ISSUER` | IdP（消費者の realm） | `http://localhost:8180/realms/ec-store` |
| `AUTH_KEYCLOAK_ID`、`AUTH_KEYCLOAK_SECRET` | client の ID と secret | `ec-store-web`、ローカル用の値 |
| `AUTH_SECRET` | セッションの Cookie の暗号化の鍵 | ローカル用の値 |

※1：既定値はローカルと CI だけで使う値で、開発モードとテストでだけ使う。本番モードでは `STORE_API_REVALIDATE_SECONDS` 以外がなければ起動に失敗する。
