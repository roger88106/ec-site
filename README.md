# EC Site

[![CI](https://github.com/roger88106/ec-site/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/roger88106/ec-site/actions/workflows/ci.yml)

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

> 現在は MVP の最初の機能（spec 001）を実装している。仕様駆動開発（SDD）で進めている。

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
| [001](docs/specs/001-catalog-and-inventory) | 商品カタログと在庫 | 実装中。[段階 0（基盤）](docs/specs/001-catalog-and-inventory/tasks/00-foundation.md)、[段階 1（税）](docs/specs/001-catalog-and-inventory/tasks/01-tax.md)、[段階 2（カテゴリ）](docs/specs/001-catalog-and-inventory/tasks/02-category.md) 完了 |

## 起動とテスト

前提：JDK 21、Docker（Docker Desktop など）

バックエンドの Gradle プロジェクトは `backend/` にある。以下のコマンドは `backend/` で実行する。
IntelliJ IDEA でリポジトリのルートを開いた場合は、`backend/build.gradle.kts` を Gradle プロジェクトとしてリンクし、Gradle JVM を 21 にする。

### ローカルで起動する

```bash
# リポジトリのルートで、PostgreSQL と Zipkin を起動する
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

### 環境変数

本番相当の環境では profile を付けずに起動し、環境に依存する値を環境変数で与える（[constitution](docs/constitution.md) 第 5 節）。

| 環境変数 | 内容 |
|----------|------|
| `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | DB の接続先 |
| `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT` | トレースの送信先（既定 `http://localhost:9411/api/v2/spans`） |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | 記録するリクエストの割合（既定 0.1） |

- マイグレーションは起動時には適用しない。デプロイごとに 1 回だけ実行する（constitution 5-10）。
- ログは標準出力に JSON（Elastic Common Schema）で出る。
- **MVP は認証がないため、公開ネットワークにデプロイしてはならない**（constitution 10）。
