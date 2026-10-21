# 段階 3：共有のパッケージ

- 前提：段階 2
- 完了条件：DC-48、49。admin・store のテストとビルド、E2E が通る。画面を目視で見て、見た目が変わっていない。
- 参照：[plan](../plan.md) 第 2 節

## タスク

- [x] **T6301** `frontend/shared/`（`@ec-site/shared`）を workspace に追加する（ソースのまま使う。store の `transpilePackages`、tsconfig と biome の土台を読む）。
- [x] **T6302** `lib` を移す：カテゴリの木、ログインの戻り先、`cn`、UUID の判定。テストも移す。admin・store の import を修正し、元のファイルを削除する。
- [x] **T6303** shadcn/ui の同じ部品（7 つ）の `'use client'` の付け方を比べ、`src/ui/` に移す。`select.tsx` の違いを見て、揃えられれば共有する。`components.json` で、部品を追加するときの置き場所を決める。
- [x] **T6304** テーマ（CSS 変数、`@theme`）を `src/theme.css` に移し、admin の `index.css`・store の `globals.css` から読む。
- [x] **T6305** admin・store に同じ内容のファイルが残っていないかを比べて報告する（DC-48）。

## 比べた結果（T6305）

2026-10-13 に、admin と store の `src`（生成物を除く）のファイルの中身を比べた。

- 中身が同じものは `lib/utils.ts` だけ。shadcn/ui の部品が読む import 先（`@/lib/utils`。`components.json` の決まり）で、共有の `cn` を返す 1 行なので残す。
- 数・日付・金額の整形（admin の `lib/format.ts`・`components/Price.tsx`、store の `lib/price.ts`・`components/PriceText.tsx`）は、形が少しずつ違う。ロケールを 1 か所にするタスク（T6606）で、共有の関数にまとめる。
- 管理画面だけにある部品（`QueryState`、`PageHeader`、`Pagination` など）は、店舗画面で使っていないので共有にしない。
