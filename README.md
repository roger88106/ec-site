# EC Site

[![CI](https://github.com/roger88106/ec-site/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/roger88106/ec-site/actions/workflows/ci.yml)

自社 B2C の EC サイト。個人のポートフォリオとして、本番相当のやり方を模擬する。
閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況のコアフローを、省略せずに作る。

Java・Spring Boot のモジュラーモノリスと、Next.js（店舗画面）・React（管理画面）で作り、仕様駆動開発（SDD）とテスト駆動開発（TDD）で進める。

できることと作り方は、[機能紹介のページ](https://roger88106.github.io/ec-site/)（エンジニアでない方向け）にまとめている。

![店舗画面のトップ。カテゴリと新着の商品が写真付きで並ぶ](showcase/img/store-top.png)

## 文書

[constitution](docs/constitution.md) から読む。プロジェクトの大綱と、すべての文書の地図がある。
AI エージェントの作業の手順は [AGENTS.md](AGENTS.md)。

## 起動とテスト

前提は JDK 21、Docker、Node.js 24 以上、pnpm。起動は [開発環境の起動](docs/dev/setup.md)、テストは [テストの流し方](docs/testing/running.md)。

## 進捗

| spec | 種類 | 内容 | 状態 |
|------|------|------|------|
| [001](docs/specs/001-catalog-and-inventory) | 機能 | 商品カタログと在庫 | 完了 |
| [002](docs/specs/002-cart-and-order) | 機能 | カートと注文 | 完了 |
| [003](docs/specs/003-auth) | 機能 | 認証 | 完了 |
| [004](docs/specs/004-foundation) | 整理 | 開発基盤の整理 | 完了 |
| [005](docs/specs/005-backend-cleanup) | 整理 | バックエンドの整理 | 完了 |
| [006](docs/specs/006-frontend-cleanup-and-i18n) | 整理 | フロントの整理と i18n | 完了 |
| [007](docs/specs/007-payment) | 機能 | 決済 | 完了。[段階 1〜8](docs/specs/007-payment/tasks/) を終えた。直さずに残したことは [見直しの結果](docs/specs/007-payment/tasks/08-review.md) |
| [008](docs/specs/008-showcase) | 整理 | 機能紹介のページ | 完了。[ページ](https://roger88106.github.io/ec-site/)を GitHub Pages で公開した。直さずに残したことは [見直しの結果](docs/specs/008-showcase/tasks/04-review.md) |
| [009](docs/specs/009-shipping) | 機能 | 出荷と配送状況 | 段階 1〜8 を終えた。次は [段階 9](docs/specs/009-shipping/tasks/09-review.md) |
