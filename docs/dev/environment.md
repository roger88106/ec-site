# 環境変数

バックエンドの環境変数。本番相当の環境では profile を付けずに起動し、環境に依存する値を環境変数で与える（[constitution](../constitution.md) 第 5 節）。

| 環境変数 | 内容 |
|----------|------|
| `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | DB の接続先 |
| `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT` | トレースの送信先（既定 `http://localhost:9411/api/v2/spans`） |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | 記録するリクエストの割合（既定 0.1） |
| `ECSITE_SECURITY_ADMIN_ISSUER_URI`、`ECSITE_SECURITY_ADMIN_AUDIENCE` | 管理 API が受け付ける token の issuer（運営者の realm）と audience（必須。[plan 003](../specs/003-auth/plan.md) 第 3.1 節） |
| `ECSITE_SECURITY_STORE_ISSUER_URI`、`ECSITE_SECURITY_STORE_AUDIENCE` | 店舗 API が受け付ける token の issuer（消費者の realm）と audience（必須） |
| `ECSITE_MEDIA_STORAGE_INCOMING_BUCKET`、`ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET` | 画像の受付（非公開）・配信（公開）のバケット（[plan 001](../specs/001-catalog-and-inventory/plan.md) 第 7 節） |
| `ECSITE_MEDIA_STORAGE_REGION`、`ECSITE_MEDIA_STORAGE_PUBLIC_BASE_URL` | バケットのリージョン、画像の配信の基準 URL（CDN） |
| `ECSITE_MEDIA_STORAGE_ENDPOINT`、`ECSITE_MEDIA_STORAGE_PATH_STYLE_ACCESS` | S3 互換のストレージを使うときだけ。AWS では設定しない（資格情報は IAM ロール） |
| `ECSITE_MEDIA_STORAGE_UPLOAD_URL_TTL` | 画像のアップロードの署名付き URL の有効期間（既定 5m） |
| `ECSITE_ORDER_SHIPPING_TAX_CATEGORY_ID` | 送料に適用する税区分の ID（必須。標準税率の税区分。[plan 002](../specs/002-cart-and-order/plan.md) 第 4 節） |
| `ECSITE_ORDER_SHIPPING_FEE`、`ECSITE_ORDER_RESERVATION_TTL` | 送料（税込、円。既定 1000）、引当の期限（既定 15m） |
| `ECSITE_SHOP_CONTACT_EMAIL` | 問い合わせ先のメールアドレス（必須。キャンセルの申請が却下された注文に示す。[plan 002](../specs/002-cart-and-order/plan.md) 第 4 節） |
| `ECSITE_MEDIA_CONVERTER_BASE_URL`、`ECSITE_MEDIA_CONVERTER_KEY`、`ECSITE_MEDIA_CONVERTER_SALT` | 画像の変換（imgproxy）の URL と、URL の署名の鍵・ソルト（16 進。imgproxy の `IMGPROXY_KEY`・`IMGPROXY_SALT` と同じ値。plan 001 第 7.5 節） |
| `ECSITE_MEDIA_CONVERTER_QUALITY`、`ECSITE_MEDIA_CONVERTER_TIMEOUT` | WebP の品質（既定 82）、変換を待つ時間の上限（既定 30s） |

- マイグレーションは起動時には適用しない。デプロイごとに 1 回だけ実行する（constitution 5-10）。
- ログは標準出力に JSON（Elastic Common Schema）で出る。
- **公開ネットワークへのデプロイは、IdP の本番の構成・secret の管理・HTTPS などの前提を決めてから行う**（constitution 10、16）。

管理画面・店舗画面の環境変数（IdP の接続先、API の接続先、secret）は [setup.md](setup.md) の各画面の節。
