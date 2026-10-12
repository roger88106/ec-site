# EC Site

[![CI](https://github.com/roger88106/ec-site/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/roger88106/ec-site/actions/workflows/ci.yml)

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

> MVP の 3 つ目の機能（spec 003：認証）まで完了し、今はコードと文書の整理（spec 004〜006）を進めている（004 は完了）。仕様駆動開発（SDD）で進めている。

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
| [文書の記号と番号](docs/notation.md) | spec の種類（機能・整理）、AC・DC などの番号、対象、優先度、タスクの番号と記号の意味 |
| [modules/](docs/modules) | モジュールごとの現在の設計 |
| [frontend/](docs/frontend) | フロントのアプリごとの構成（ディレクトリ、API の呼び出し方、画面の追加手順） |
| [specs/](docs/specs) | spec（機能・整理。何を・なぜ）、plan（どう作るか）、tasks（作業の手順） |
| [api/openapi/](api/openapi) | API 契約の正本（`admin.yaml`：管理画面、`store.yaml`：店舗） |
| [style/](docs/style) | コードの書き方とプロジェクトの習慣、見直しで読む規則 |
| [dev/](docs/dev) | 起動・テスト・環境変数の手順 |
| [AGENTS.md](AGENTS.md) | AI エージェントが作業するときの手順（SDD + TDD の進め方、Git） |

## 進め方（SDD）

1. **spec**：何を、なぜ作るかと、受け入れ条件（AC）を書く。
2. **plan**：どう作るかを決め、OpenAPI を書く。
3. **tasks**：作業を段階ごとに分け、テストのタスクを実装より先に置く。
4. **実装**：tasks に沿って進める。すべての AC に、AC 番号付きのテストを対応させる。

spec がコードに先行する。仕様の誤りに気づいたら、spec を直してからコードを直す。

## 進捗

| spec | 種類 | 内容 | 状態 |
|------|------|------|------|
| [001](docs/specs/001-catalog-and-inventory) | 機能 | 商品カタログと在庫 | 完了。[段階 0（基盤）](docs/specs/001-catalog-and-inventory/tasks/00-foundation.md)、[段階 1（税）](docs/specs/001-catalog-and-inventory/tasks/01-tax.md)、[段階 2（カテゴリ）](docs/specs/001-catalog-and-inventory/tasks/02-category.md)、[段階 3（商品）](docs/specs/001-catalog-and-inventory/tasks/03-product.md)、[段階 4（在庫）](docs/specs/001-catalog-and-inventory/tasks/04-inventory.md)、[段階 5（B 側の商品一覧）](docs/specs/001-catalog-and-inventory/tasks/05-admin-product-list.md)、[段階 6（店舗）](docs/specs/001-catalog-and-inventory/tasks/06-storefront.md)、[段階 7（管理画面）](docs/specs/001-catalog-and-inventory/tasks/07-admin-ui.md)、[段階 8（店舗画面）](docs/specs/001-catalog-and-inventory/tasks/08-store-ui.md)、[段階 9（フロントのテスト）](docs/specs/001-catalog-and-inventory/tasks/09-frontend-tests.md) 完了（P1 はすべて完了）。[段階 10（P2）](docs/specs/001-catalog-and-inventory/tasks/10-p2.md) は、バリアントの販売停止、公開日時の予約、商品画像（US-7）、メディアモジュールへの切り出し、画像形式の受け付けと WebP 変換（US-13）が完了（P2 はすべて完了）。[段階 11（見直し）](docs/specs/001-catalog-and-inventory/tasks/11-review.md) 完了 |
| [002](docs/specs/002-cart-and-order) | 機能 | カートと注文 | 完了。[段階 1（税と在庫の土台）](docs/specs/002-cart-and-order/tasks/01-foundation.md)〜[段階 4（キャンセルと B 側の注文）](docs/specs/002-cart-and-order/tasks/04-cancellation-and-admin.md) 完了（バックエンド）。[段階 5（店舗画面）](docs/specs/002-cart-and-order/tasks/05-store-ui.md)、[段階 6（管理画面）](docs/specs/002-cart-and-order/tasks/06-admin-ui.md)、[段階 7（フロントのテスト）](docs/specs/002-cart-and-order/tasks/07-frontend-tests.md) 完了。[段階 8（見直し）](docs/specs/002-cart-and-order/tasks/08-review.md) 完了 |
| [003](docs/specs/003-auth) | 機能 | 認証 | 完了。[段階 1（IdP と開発環境）](docs/specs/003-auth/tasks/01-idp.md)、[段階 2（バックエンドの認証の土台）](docs/specs/003-auth/tasks/02-backend-auth.md)、[段階 3（会員とカート・注文）](docs/specs/003-auth/tasks/03-member-cart-order.md) 完了。[段階 4（店舗画面）](docs/specs/003-auth/tasks/04-store-ui.md) 完了。[段階 5（管理画面）](docs/specs/003-auth/tasks/05-admin-ui.md) 完了。[段階 6（E2E と CI）](docs/specs/003-auth/tasks/06-e2e-ci.md) 完了。[段階 7（操作した運営者）](docs/specs/003-auth/tasks/07-operator-record.md)、[段階 8（見直し）](docs/specs/003-auth/tasks/08-review.md) 完了 |
| [004](docs/specs/004-foundation) | 整理 | 開発の基盤の整理（規則の文書、CI、ライセンス、テストの網、リポジトリの構成、E2E のデータ） | 完了。[段階 1〜8](docs/specs/004-foundation/tasks/) を終えた。残りの確かめ（GitHub のライセンスの判定は main に入れた後）と、CI の所要時間を上限より少し超えて受け入れたことは [見直しの結果](docs/specs/004-foundation/tasks/08-review.md) |
| [005](docs/specs/005-backend-cleanup) | 整理 | バックエンドの整理 | tasks 作成済み。004 の完了の後に着手 |
| [006](docs/specs/006-frontend-cleanup-and-i18n) | 整理 | フロントの整理と i18n | tasks 作成済み。004 の完了の後に着手（段階 5 は 005 の段階 2 の後） |

## 起動とテスト

前提は JDK 21、Docker、Node.js 24 以上、pnpm。手順は [docs/dev/](docs/dev) にある。

| 文書 | 内容 |
|------|------|
| [setup.md](docs/dev/setup.md) | compose・バックエンド・管理画面・店舗画面の起動、ローカルと CI で使うアカウント |
| [testing.md](docs/dev/testing.md) | 単体・統合テスト、フロントの確認、E2E の流し方 |
| [environment.md](docs/dev/environment.md) | 環境変数 |
