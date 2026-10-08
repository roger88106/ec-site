# EC Site

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

> 現在は MVP の設計が終わった段階で、実装はまだない。仕様駆動開発（SDD）で進めている。

## 技術スタック

| 領域 | 採用 |
|------|------|
| バックエンド | Java 21、Spring Boot 4、Spring Modulith（モジュラーモノリス）、jOOQ、Flyway |
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

## 進め方（SDD）

1. **spec**：何を、なぜ作るかと、受け入れ条件（AC）を書く。
2. **plan**：どう作るかを決め、OpenAPI を書く。
3. **tasks**：作業を段階ごとに分け、テストのタスクを実装より先に置く。
4. **実装**：tasks に沿って進める。すべての AC に、AC 番号付きのテストを対応させる。

spec がコードに先行する。仕様の誤りに気づいたら、spec を直してからコードを直す。

## 進捗

| spec | 内容 | 状態 |
|------|------|------|
| [001](docs/specs/001-catalog-and-inventory) | 商品カタログと在庫 | 設計完了。実装は [段階 0（基盤）](docs/specs/001-catalog-and-inventory/tasks/00-foundation.md) から |

## 起動とテスト

段階 0 の実装（T021）で追記する。
