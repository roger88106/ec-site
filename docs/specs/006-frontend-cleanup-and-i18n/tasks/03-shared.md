# 段階 3：共有のパッケージ

- 前提：段階 2
- 完了条件：DC-48、49。admin・store のテストとビルド、E2E が通る。画面を手で見て、見た目が変わっていない。
- 参照：[plan](../plan.md) 第 2 節

## タスク

- [x] **T6301** `frontend/shared/`（`@ec-site/shared`）を workspace に足す（ソースのまま使う。store の `transpilePackages`、tsconfig と biome の土台を読む）。
- [x] **T6302** `lib` を移す：カテゴリの木、ログインの戻り先、`cn`、UUID の判定。テストも移す。admin・store の import を直し、元のファイルを消す。
- [ ] **T6303** shadcn/ui の同じ部品（7 つ）の `'use client'` の付け方を比べ、`src/ui/` に移す。`select.tsx` の違いを見て、揃えられれば共有する。`components.json` で、部品を足すときの置き場所を決める。
- [ ] **T6304** テーマ（CSS 変数、`@theme`）を `src/theme.css` に移し、admin の `index.css`・store の `globals.css` から読む。
- [ ] **T6305** admin・store に同じ内容のファイルが残っていないかを比べて報告する（DC-48）。
