# 段階 1：スクリーンショットの撮影

- 前提：spec 007 の完了
- 完了条件：ローカルの DB を再作成してから `pnpm capture` を実行すると、`showcase/img/` に 10 枚が撮れる（DC-72）。
- 参照：[plan](../plan.md) 第 2 節、[開発環境の起動](../../../../../guides/setup.md)、skill `local-dev`

## タスク

- [x] **T8101** 撮影の設定（`e2e/playwright.showcase.config.ts`）と script（`e2e/showcase/capture.spec.ts`）を追加する。`e2e/package.json` に `capture` を追加する。E2E の `pnpm test` が撮影を流さないことを確認する。
- [x] **T8102** ローカルの DB を再作成して撮り、10 枚を利用者に見せる。
- [x] **T8103** 撮影の手順を [開発環境の起動](../../../../../guides/setup.md) に追加し、skill `local-dev` から案内する。
