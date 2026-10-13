# EC Site

[![CI](https://github.com/roger88106/ec-site/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/roger88106/ec-site/actions/workflows/ci.yml)

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

> MVP の 3 つ目の機能（spec 003：認証）まで完了し、コードと文書の整理（spec 004〜006）も完了した。

## 技術スタック

| 領域 | 採用 |
|------|------|
| バックエンド | Java 21、Spring Boot 4.1 |
| DB | PostgreSQL 18 |
| フロント（店舗） | Next.js |
| フロント（管理画面） | Vite + React |
| API | OpenAPI（契約先行） |
| CI | GitHub Actions |

詳細は [constitution](docs/constitution.md) 第 2 節。

## 文書の地図

| 文書 | 内容 |
|------|------|
| [docs/constitution.md](docs/constitution.md) | プロジェクト全体の長期ルール。まずここを読む |
| [docs/glossary.md](docs/glossary.md) | 用語集。業務の用語と、コード・DB の識別子の対応 |
| [docs/notation.md](docs/notation.md) | 文書の記号と番号。spec の種類（機能・整理）、AC・DC などの番号、対象、優先度、タスクの番号と記号の意味 |
| [docs/modules/](docs/modules) | モジュールごとの現在の設計 |
| [docs/frontend/](docs/frontend) | フロントのアプリごとの構成（ディレクトリ、API の呼び出し方、画面の追加手順） |
| [docs/specs/](docs/specs) | spec（機能・整理。何を・なぜ）、plan（どう作るか）、tasks（作業の手順） |
| [docs/style/](docs/style) | コードの書き方とプロジェクトの習慣、見直しで読む規則 |
| [docs/dev/](docs/dev) | 起動・テスト・環境変数の手順 |
| [api/openapi/](api/openapi) | API 契約の正本（`admin.yaml`：管理画面、`store.yaml`：店舗） |
| [AGENTS.md](AGENTS.md) | AI エージェントが作業するときの手順（SDD + TDD の進め方、Git） |

## 進め方

### 仕様駆動開発（SDD：Spec-Driven Development）

1. **spec**：何を、なぜ作るかと、受け入れ条件（AC）を書く。
2. **plan**：どう作るかを決め、OpenAPI を書く。
3. **tasks**：作業を段階ごとに分け、テストのタスクを実装より先に置く。
4. **実装**：tasks に沿って進める。

spec がコードに先行する。仕様の誤りに気づいたら、spec を直してからコードを直す。

### テスト駆動開発（TDD：Test-Driven Development）

1. **レッド**：失敗するテストを書き、失敗することを確かめる。
2. **グリーン**：テストが通る最小の実装を書く。
3. **リファクタリング**：テストが通ったまま整える。

すべての AC に、AC 番号付きのテストを対応させる。テストを通すためにテストを弱めない。

## 進捗

| spec | 種類 | 内容 | 状態 |
|------|------|------|------|
| [001](docs/specs/001-catalog-and-inventory) | 機能 | 商品カタログと在庫 | 完了 |
| [002](docs/specs/002-cart-and-order) | 機能 | カートと注文 | 完了 |
| [003](docs/specs/003-auth) | 機能 | 認証 | 完了 |
| [004](docs/specs/004-foundation) | 整理 | 開発の基盤の整理 | 完了 |
| [005](docs/specs/005-backend-cleanup) | 整理 | バックエンドの整理 | 完了 |
| [006](docs/specs/006-frontend-cleanup-and-i18n) | 整理 | フロントの整理と i18n | 完了 |

## 起動とテスト

前提は JDK 21、Docker、Node.js 24 以上、pnpm。手順は [docs/dev/](docs/dev) にある。

| 文書 | 内容 |
|------|------|
| [setup.md](docs/dev/setup.md) | compose・バックエンド・管理画面・店舗画面の起動、ローカルと CI で使うアカウント |
| [testing.md](docs/dev/testing.md) | 単体・統合テスト、フロントの確認、E2E の流し方 |
| [environment.md](docs/dev/environment.md) | 環境変数 |
