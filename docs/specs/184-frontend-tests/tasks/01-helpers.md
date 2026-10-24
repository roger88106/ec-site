# 段階 1：依存、補助、フォームの分割

- 前提：spec 184 の plan と tasks の確認、実装を始める前の照合（[開発の進め方](../../../rules/workflow.md) 第 5.6 節）
- 完了条件：店舗画面と共有のパッケージで jest-dom を使える（DC-184-6）。DOM に読み込む補助があり、補助の単体テストは先に書き、レッドを確認した（DC-184-1、2 の土台）。2 つのフォームが表示のコンポーネントに分かれ、テストに `vi.mock('react')` がない（DC-184-8〜10）。店舗画面の構成とテストの書き方が新しい書き方を案内する（DC-184-5）。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 2 節、[spec](../spec.md) の DS-184-1〜3、[テストの書き方](../../../rules/test-writing.md)、[店舗画面の構成](../../../design/apps/store.md) 第 7 節

## タスク

実装は実装役のサブエージェントに任せる。補助は本段階では作るだけにし、HTML に描画するテストの書き換えは段階 2 で行う。例外は 2 つのフォームのテストで、分割と同じタスクで全て書き換える。書き換えたテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-184-4）。

- [x] **T184-01-01** 店舗画面と共有のパッケージに `@testing-library/jest-dom` を加え（版は管理画面と同じ）、店舗画面は `src/test/setup.ts`、共有のパッケージは `vitest.config.ts` の setupFiles で読み込む。共有のパッケージには `jsdom` と `@testing-library/dom` も加える（DC-184-6）。
  - 依存：なし
  - 範囲：`frontend/store/package.json`、`frontend/shared/package.json`、`pnpm-lock.yaml`、`frontend/store/src/test/setup.ts`、`frontend/shared/vitest.config.ts`
- [x] **T184-01-02** [テスト] DOM に読み込む補助 `renderStatic` の単体テストを書き、レッドを確認する。Node の環境で、役割と名前の問い合わせと jest-dom の照合の関数が動くこと、効果が実行されないことを確認する（DC-184-1、2）。
  - 依存：T184-01-01
  - 範囲：`frontend/store/src/test/render.test.tsx`（新規）
- [x] **T184-01-03** T184-01-02 のテストが通る補助を書く。Node の環境で動かなければ、作業を止めて報告する（plan 第 1 節 ※1）。
  - 依存：T184-01-02
  - 範囲：`frontend/store/src/test/render.tsx`
- [x] **T184-01-04** [P] 注文のフォームを、`useActionState` を呼ぶコンポーネントと、結果を props で受ける `OrderFormView` に分ける。テストは `OrderFormView` に結果を渡して確認し、`vi.mock('react')` を削除する。描画の内容は変えない。テストの HTML の確認も `renderStatic` で書き換える（DC-184-1、8〜10）。
  - 依存：T184-01-03
  - 範囲：`frontend/store/src/features/checkout/components/OrderForm.tsx`、`OrderForm.test.tsx`
- [x] **T184-01-05** [P] カートに入れるフォームを、`useActionState` を呼ぶコンポーネントと、結果を props で受ける `AddToCartFormView` に分ける。テストは `AddToCartFormView` に結果を渡して確認し、`vi.mock('react')` を削除する。テストの HTML の確認も `renderStatic` で書き換える（DC-184-1、8〜10）。
  - 依存：T184-01-03
  - 範囲：`frontend/store/src/features/catalog/components/AddToCartForm.tsx`、`AddToCartForm.test.tsx`
- [x] **T184-01-06** 文書を新しい書き方にする。店舗画面の構成 第 7 節のテストの表（表示のコンポーネント、サーバーアクションの結果の表示、ページ）と補助の表（`renderStatic` の追加、jest-dom）、テストの書き方 第 1.1 節の「HTML に描画し」の文、skill `new-screen` のフォームの例の 1 行（plan 第 2 節）。用語集に「jest-dom」を追加する（DC-184-5）。`markup.ts` の行の削除は T184-02-06 で行う。`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DN-184-3）。
  - 依存：T184-01-03〜05
  - 範囲：`docs/design/apps/store.md`、`docs/rules/test-writing.md`、`.claude/skills/new-screen/SKILL.md`、`docs/design/glossary.md`、`docs/design/acceptance-criteria.md`
- [x] **T184-01-07** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T184-01-01〜06
  - 範囲：レビューの結果、`/.local/review/`

## 段階のレビューの結果

2026-10-24 に行った。対象は段階 1 の差分（T184-01-01〜06）。金額・セキュリティ・`[リスク]` の変更と、値や制約をまたぐ変更がないので、一般の観点だけを行った。

| レビュー役 | モデル | 高 | 必須 |
|------------|--------|----|------|
| 一般の観点 1 回目 | 汎用 | 1 | 1 |
| 一般の観点 2 回目 | 汎用 | 2 | 0 |

修正したもの（重複を除いて 2 件。どちらも規則との食い違いなので必須として扱った）：

- 注文のフォームのテストに、欄の並びの確認で `getAttribute` を新しく使っていた（DC-184-7）。
- 最後の欄（電話番号）の誤りの文の位置の上限を確認していなかったので、元の確認より弱かった（DN-184-4）。注文のボタンを境界にした。

実装の途中で、issue 担当のセッションと次を決めた（spec 第 7 節の ※1）。注文のフォームの `aria-describedby` と、カートに入れるフォームの `role="status"` は読み上げを変えるので加えず、#152 に残す。注文のフォームの初期表示の確認は、文言の一覧の完全な一致にして、元と同じ強さにした。表示のコンポーネントに分けたことで呼ぶ側のコードがテストで実行されなくなり、店舗画面の行のカバレッジが基準を下回ったので、呼ぶ側の初期表示と送信のテストを加えた。

全体の検査：`scripts/dev/check-frontend.sh all`、カバレッジの基準（3 つとも基準以上）、`CI=true scripts/ci/check-all.sh` が通った。
