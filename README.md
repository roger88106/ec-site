# EC Site

[![CI](https://github.com/roger88106/ec-site/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/roger88106/ec-site/actions/workflows/ci.yml)

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

Java・Spring Boot のモジュラーモノリスと、Next.js（店舗画面）・React（管理画面）で作り、仕様駆動開発（SDD）とテスト駆動開発（TDD）で進める。

できることと作り方は、[機能紹介のページ](https://roger88106.github.io/ec-site/)（エンジニアでない方向け）にまとめている。

![店舗画面のトップ。カテゴリと新着の商品が写真付きで並ぶ](showcase/img/store-top.png)

## 文書

[constitution](docs/constitution.md) から読む。プロジェクトの大綱と、全ての文書の地図がある。
AI エージェントの作業の手順は [AGENTS.md](AGENTS.md)。

## 起動とテスト

前提は JDK 21、Docker、Node.js 24 以上、pnpm。起動は [開発環境の起動](docs/guides/setup.md)、テストは [テストの実行方法](docs/guides/running-tests.md)。

## 進捗

| spec | 種類 | 内容 | 状態 |
|------|------|------|------|
| [001](docs/specs/archive/mvp/001-catalog-and-inventory) | 機能 | 商品カタログと在庫 | 完了 |
| [002](docs/specs/archive/mvp/002-cart-and-order) | 機能 | カートと注文 | 完了 |
| [003](docs/specs/archive/mvp/003-auth) | 機能 | 認証 | 完了 |
| [004](docs/specs/archive/mvp/004-foundation) | 整理 | 開発基盤の整理 | 完了 |
| [005](docs/specs/archive/mvp/005-backend-cleanup) | 整理 | バックエンドの整理 | 完了 |
| [006](docs/specs/archive/mvp/006-frontend-cleanup-and-i18n) | 整理 | フロントの整理と i18n | 完了 |
| [007](docs/specs/archive/mvp/007-payment) | 機能 | 決済 | 完了。[段階 1〜8](docs/specs/archive/mvp/007-payment/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/007-payment/tasks/08-review.md) |
| [008](docs/specs/archive/mvp/008-showcase) | 整理 | 機能紹介のページ | 完了。[ページ](https://roger88106.github.io/ec-site/)を GitHub Pages で公開した。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/008-showcase/tasks/04-review.md) |
| [009](docs/specs/archive/mvp/009-shipping) | 機能 | 出荷と配送状況 | 完了。[段階 10](docs/specs/archive/mvp/009-shipping/tasks/10-seed.md) でローカルの seed を整理した。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/009-shipping/tasks/09-review.md) |
| [010](docs/specs/archive/mvp/010-sales-report) | 機能 | 売上の簡易レポート | 完了。[段階 1〜6](docs/specs/archive/mvp/010-sales-report/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/010-sales-report/tasks/06-review.md) |
| [011](docs/specs/archive/mvp/011-dev-foundation) | 整理 | 開発の基盤の整理 | 完了。[段階 1〜7](docs/specs/archive/mvp/011-dev-foundation/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/011-dev-foundation/tasks/07-review.md) |
| [012](docs/specs/archive/mvp/012-test-safety-net) | 整理 | テストの安全網 | 完了。[段階 1〜8](docs/specs/archive/mvp/012-test-safety-net/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/012-test-safety-net/tasks/08-review.md) |
| [013](docs/specs/archive/mvp/013-backend-consistency) | 整理 | 書き方の統一（バックエンド） | 完了。[段階 1〜7](docs/specs/archive/mvp/013-backend-consistency/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/013-backend-consistency/tasks/07-review.md) |
| [014](docs/specs/archive/mvp/014-frontend-consistency) | 整理 | 書き方の統一（フロント） | 完了。[段階 1〜5](docs/specs/archive/mvp/014-frontend-consistency/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/014-frontend-consistency/tasks/05-review.md) |
| [015](docs/specs/archive/mvp/015-security) | 機能 | セキュリティ | 完了。[段階 1〜7](docs/specs/archive/mvp/015-security/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/015-security/tasks/07-review.md) |
| [016](docs/specs/archive/mvp/016-master-data) | 機能 | マスターデータ | 完了。[段階 1〜10](docs/specs/archive/mvp/016-master-data/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/016-master-data/tasks/10-review.md) |
| [017](docs/specs/archive/mvp/017-stability) | 機能 | 安定性 | 完了。[段階 1〜8](docs/specs/archive/mvp/017-stability/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/017-stability/tasks/08-review.md) |
| [018](docs/specs/archive/mvp/018-docs-and-tools) | 整理 | 文書とツールの整理 | 完了。[段階 1〜7](docs/specs/archive/mvp/018-docs-and-tools/tasks) を終えた。修正せずに残した項目は [レビューの結果](docs/specs/archive/mvp/018-docs-and-tools/tasks/07-review.md) |
| [019](docs/specs/019-mvp-completion) | 整理 | 受け入れ確認と MVP の完成 | 段階 1〜4 と、段階 5 の main へのマージの前までを終えた |

MVP を完成させるまでの spec の順は [MVP の完成までの一覧](docs/specs/mvp.md)、MVP の後に行う作業と未決事項は [TODO 一覧](docs/todo.md) にある。
