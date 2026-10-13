# 段階 1：スクリーンショットの撮影

- 前提：spec 007 の完了
- 完了条件：ローカルの DB を作り直してから `pnpm capture` を流すと、`showcase/img/` に 10 枚が撮れる（DC-72）。
- 参照：[plan](../plan.md) 第 2 節、[開発環境の起動](../../../dev/setup.md)、skill `local-dev`

## タスク

- [x] **T8101** 撮影の設定（`e2e/playwright.showcase.config.ts`）と script（`e2e/showcase/capture.spec.ts`）を足す。`e2e/package.json` に `capture` を足す。E2E の `pnpm test` が撮影を流さないことを確かめる。
- [x] **T8102** ローカルの DB を作り直して撮り、10 枚を利用者に見せる。
- [ ] **T8103** 撮影の手順を [開発環境の起動](../../../dev/setup.md) に足し、skill `local-dev` から案内する。
