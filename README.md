# EC Site

[![CI](https://github.com/roger88106/ec-site/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/roger88106/ec-site/actions/workflows/ci.yml)

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

> 現在は MVP の 2 つ目の機能（spec 002）を実装している。仕様駆動開発（SDD）で進めている。

## 技術スタック

| 領域 | 採用 |
|------|------|
| バックエンド | Java 21、Spring Boot 4.1、Spring Modulith（モジュラーモノリス）、jOOQ、Flyway |
| DB | PostgreSQL 18 |
| フロント（店舗） | Next.js |
| フロント（管理画面） | Vite + React |
| API | OpenAPI（契約先行） |
| CI | GitHub Actions |

詳細は [constitution](docs/constitution.md) 第 2 節。

## 文書の地図

| 文書 | 内容 |
|------|------|
| [constitution](docs/constitution.md) | プロジェクト全体の長期ルール。まずここを読む |
| [用語集](docs/glossary.md) | 業務の用語と、コード・DB の識別子の対応 |
| [modules/](docs/modules) | モジュールごとの現在の設計 |
| [frontend/](docs/frontend) | フロントのアプリごとの構成（ディレクトリ、API の呼び出し方、画面の追加手順） |
| [specs/](docs/specs) | 機能ごとの spec（何を・なぜ）、plan（どう作るか）、tasks（作業の手順） |
| [api/openapi/](api/openapi) | API 契約の正本（`admin.yaml`：管理画面、`store.yaml`：店舗） |
| [AGENTS.md](AGENTS.md) | AI エージェントが作業するときの手順（SDD + TDD の進め方、Git） |

## 進め方（SDD）

1. **spec**：何を、なぜ作るかと、受け入れ条件（AC）を書く。
2. **plan**：どう作るかを決め、OpenAPI を書く。
3. **tasks**：作業を段階ごとに分け、テストのタスクを実装より先に置く。
4. **実装**：tasks に沿って進める。すべての AC に、AC 番号付きのテストを対応させる。

spec がコードに先行する。仕様の誤りに気づいたら、spec を直してからコードを直す。

## 進捗

| spec | 内容 | 状態 |
|------|------|------|
| [001](docs/specs/001-catalog-and-inventory) | 商品カタログと在庫 | 完了。[段階 0（基盤）](docs/specs/001-catalog-and-inventory/tasks/00-foundation.md)、[段階 1（税）](docs/specs/001-catalog-and-inventory/tasks/01-tax.md)、[段階 2（カテゴリ）](docs/specs/001-catalog-and-inventory/tasks/02-category.md)、[段階 3（商品）](docs/specs/001-catalog-and-inventory/tasks/03-product.md)、[段階 4（在庫）](docs/specs/001-catalog-and-inventory/tasks/04-inventory.md)、[段階 5（B 側の商品一覧）](docs/specs/001-catalog-and-inventory/tasks/05-admin-product-list.md)、[段階 6（店舗）](docs/specs/001-catalog-and-inventory/tasks/06-storefront.md)、[段階 7（管理画面）](docs/specs/001-catalog-and-inventory/tasks/07-admin-ui.md)、[段階 8（店舗画面）](docs/specs/001-catalog-and-inventory/tasks/08-store-ui.md)、[段階 9（フロントのテスト）](docs/specs/001-catalog-and-inventory/tasks/09-frontend-tests.md) 完了（P1 はすべて完了）。[段階 10（P2）](docs/specs/001-catalog-and-inventory/tasks/10-p2.md) は、バリアントの販売停止、公開日時の予約、商品画像（US-7）、メディアモジュールへの切り出し、画像形式の受け付けと WebP 変換（US-13）が完了（P2 はすべて完了）。[段階 11（見直し）](docs/specs/001-catalog-and-inventory/tasks/11-review.md) 完了 |
| [002](docs/specs/002-cart-and-order) | カートと注文 | 実装中。[段階 1（税と在庫の土台）](docs/specs/002-cart-and-order/tasks/01-foundation.md)〜[段階 4（キャンセルと B 側の注文）](docs/specs/002-cart-and-order/tasks/04-cancellation-and-admin.md) 完了（バックエンド）。[段階 5（店舗画面）](docs/specs/002-cart-and-order/tasks/05-store-ui.md)、[段階 6（管理画面）](docs/specs/002-cart-and-order/tasks/06-admin-ui.md)、[段階 7（フロントのテスト）](docs/specs/002-cart-and-order/tasks/07-frontend-tests.md) 完了。次は [段階 8（見直し）](docs/specs/002-cart-and-order/tasks/08-review.md) |

## 起動とテスト

前提：JDK 21、Docker（Docker Desktop など）

バックエンドの Gradle プロジェクトは `backend/` にある。以下のコマンドは `backend/` で実行する。
IntelliJ IDEA でリポジトリのルートを開いた場合は、`backend/build.gradle.kts` を Gradle プロジェクトとしてリンクし、Gradle JVM を 21 にする。

### ローカルで起動する

```bash
# リポジトリのルートで、PostgreSQL、Zipkin、SeaweedFS（画像の置き場所）、imgproxy（画像の変換）を起動する
docker compose up -d

# backend/ で、local profile で起動する（マイグレーションとローカル用の seed は起動時に適用される）
./gradlew bootRun --args='--spring.profiles.active=local'
```

| URL | 内容 |
|-----|------|
| http://localhost:8080/api/admin | 管理 API（[admin.yaml](api/openapi/admin.yaml)） |
| http://localhost:8080/api/store | 店舗 API（[store.yaml](api/openapi/store.yaml)） |
| http://localhost:8080/actuator/health | ヘルスチェック（`/liveness`、`/readiness`） |
| http://localhost:9411 | Zipkin（トレースの表示。local では全リクエストを記録する） |
| http://localhost:8333 | SeaweedFS（S3 互換。画像のバケットは `seaweedfs-init` が作る。[plan 001](docs/specs/001-catalog-and-inventory/plan.md) 第 7 節） |
| http://localhost:8081 | imgproxy（画像の WebP への変換。署名のない URL は拒否する。[plan 001](docs/specs/001-catalog-and-inventory/plan.md) 第 7.5 節） |

- DB の接続先は [compose.yaml](compose.yaml) と `application-local.yml` で揃えてある（`localhost:5432/ec_site`）。
- 止めるときは `docker compose stop`。データを消して作り直すときは `docker compose down -v`。

### テスト

```bash
# 単体テスト（Spring を起動しない。Docker 不要）
./gradlew test

# 統合テスト（Spring + Testcontainers の PostgreSQL。Docker が必要）
./gradlew integrationTest

# ビルド（単体・統合テスト、OpenAPI の構文の検証を含む。CI と同じ）
./gradlew build
```

- テストの構成と方針は [constitution](docs/constitution.md) 第 12 節。
- jOOQ のコードと OpenAPI のインターフェースは、コンパイル時に生成される（コミットしない）。
  jOOQ の生成は Testcontainers の PostgreSQL にマイグレーションを適用して行うので、Docker が必要（compose の DB は不要）。

### 管理画面（`frontend/admin`）

前提：Node.js 24 以上、pnpm（版は `package.json` の `packageManager`）

```bash
# frontend/admin/ で、依存を入れる
pnpm install

# 開発サーバー（http://localhost:5173）。/api はバックエンド（localhost:8080）に転送する
pnpm dev

# CI と同じ確認（lint と整形、型チェック、単体・結合テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build

# 整形と、自動で直せる lint の指摘を直す
pnpm lint:fix
```

- 先にバックエンドを local profile で起動しておく。
- API の型は [admin.yaml](api/openapi/admin.yaml) から `dev`・`typecheck`・`build` の前に生成する（コミットしない）。
- 結合テスト（画面の操作の流れ）はバックエンドなしで動く。API の応答は MSW で差し替える。書き方は [管理画面の構成](docs/frontend/admin.md) 第 10 節。
- ディレクトリと画面の追加手順は [管理画面の構成](docs/frontend/admin.md)。

### 店舗画面（`frontend/store`）

前提は管理画面と同じ。

```bash
# frontend/store/ で、依存を入れる
pnpm install

# 開発サーバー（http://localhost:3000）。API はサーバーから呼ぶ（既定は localhost:8080）
pnpm dev

# CI と同じ確認（lint と整形、型チェック、テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- 先にバックエンドを local profile で起動しておく。接続先を変えるときは環境変数 `STORE_API_BASE_URL`（例：`http://localhost:8080/api/store`）。
- キャンセルの申請が却下された注文に示す問い合わせ先は、環境変数 `STORE_CONTACT_EMAIL`（既定 `support@example.com`）。
- API の結果は最大 60 秒キャッシュする（NFR-1）。管理画面での変更は、1 分以内に店舗画面に反映される。
- ディレクトリと画面の追加手順は [店舗画面の構成](docs/frontend/store.md)。

### E2E（`frontend/e2e`）

バックエンド、管理画面、店舗画面を起動して、ブラウザ（Chromium）で操作する（Playwright）。

```bash
# リポジトリのルートで、PostgreSQL と SeaweedFS を起動する
docker compose up -d

# 管理画面と店舗画面の依存を入れておく（それぞれのディレクトリで pnpm install）

# frontend/e2e/ で、依存とブラウザを入れる
pnpm install
pnpm exec playwright install chromium

# E2E。バックエンド（local profile）、管理画面（:4173）、店舗画面（:3100）を起動してから動かす
pnpm test
```

- バックエンドの起動に JDK 21 を使うので、`JAVA_HOME` を JDK 21 に向けておく。
- すでに起動しているサーバー（:8080、:4173、:3100）があれば、それを使う。管理画面・店舗画面を直したときは、止めてからやり直す（ビルドし直すため）。
- テストのデータは管理 API で作り、品番・名称に実行ごとの印（`E2E-<印>-…`）を付ける。消さないので、ローカルの DB に残る。
- 店舗画面の API のキャッシュは、E2E では 1 秒にする（`STORE_API_REVALIDATE_SECONDS`）。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を見る。

### 環境変数

本番相当の環境では profile を付けずに起動し、環境に依存する値を環境変数で与える（[constitution](docs/constitution.md) 第 5 節）。

| 環境変数 | 内容 |
|----------|------|
| `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | DB の接続先 |
| `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT` | トレースの送信先（既定 `http://localhost:9411/api/v2/spans`） |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | 記録するリクエストの割合（既定 0.1） |
| `ECSITE_MEDIA_STORAGE_INCOMING_BUCKET`、`ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET` | 画像の受付（非公開）・配信（公開）のバケット（[plan 001](docs/specs/001-catalog-and-inventory/plan.md) 第 7 節） |
| `ECSITE_MEDIA_STORAGE_REGION`、`ECSITE_MEDIA_STORAGE_PUBLIC_BASE_URL` | バケットのリージョン、画像の配信の基準 URL（CDN） |
| `ECSITE_MEDIA_STORAGE_ENDPOINT`、`ECSITE_MEDIA_STORAGE_PATH_STYLE_ACCESS` | S3 互換のストレージを使うときだけ。AWS では設定しない（資格情報は IAM ロール） |
| `ECSITE_MEDIA_STORAGE_UPLOAD_URL_TTL` | 画像のアップロードの署名付き URL の有効期間（既定 5m） |
| `ECSITE_ORDER_SHIPPING_TAX_CATEGORY_ID` | 送料に適用する税区分の ID（必須。標準税率の税区分。[plan 002](docs/specs/002-cart-and-order/plan.md) 第 4 節） |
| `ECSITE_ORDER_SHIPPING_FEE`、`ECSITE_ORDER_RESERVATION_TTL` | 送料（税込、円。既定 1000）、引当の期限（既定 15m） |
| `ECSITE_MEDIA_CONVERTER_BASE_URL`、`ECSITE_MEDIA_CONVERTER_KEY`、`ECSITE_MEDIA_CONVERTER_SALT` | 画像の変換（imgproxy）の URL と、URL の署名の鍵・ソルト（16 進。imgproxy の `IMGPROXY_KEY`・`IMGPROXY_SALT` と同じ値。plan 001 第 7.5 節） |
| `ECSITE_MEDIA_CONVERTER_QUALITY`、`ECSITE_MEDIA_CONVERTER_TIMEOUT` | WebP の品質（既定 82）、変換を待つ時間の上限（既定 30s） |

- マイグレーションは起動時には適用しない。デプロイごとに 1 回だけ実行する（constitution 5-10）。
- ログは標準出力に JSON（Elastic Common Schema）で出る。
- **MVP は認証がないため、公開ネットワークにデプロイしてはならない**（constitution 10）。
