# 段階 3：公開

- 前提：段階 2
- 完了条件：`develop` に push すると、`https://roger88106.github.io/ec-site/` でページが開く。README の先頭からページに辿れる（DC-70、71）。
- 参照：[plan](../plan.md) 第 4、5 節

## タスク

- [x] **T8301** `.github/workflows/pages.yml` を足し、`ci.yml` の `paths-ignore` に `showcase/**` を足す。
- [x] **T8302** README の先頭にページへのリンクと店舗画面のトップの画像を足す。constitution のリポジトリの構成の図に `showcase/` を足す。
- [x] **T8303** 利用者が Settings の Pages と Environments を設定する（plan 第 4 節）。push の許可を得て push し、Pages の URL でページが開くことを確かめる。
