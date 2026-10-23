# 段階 2：共有の部品、数の整形、管理画面の型と文書

- 前提：段階 1
- 完了条件：DC-157-1〜9、35、36 を満たす。共有の数の整形のテストは先に書き、レッドを確認した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 3 節、[spec](../spec.md) の DS-157-1〜3、[フロントの書き方](../../../rules/frontend-coding.md) 第 5.1 節、[フロントの書き方](../../../rules/frontend-coding.md) 第 7.3 節、skill `writing-for-agents`

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。

- [ ] **T157-02-01** [P] [テスト] 共有の既定の数の整形の単体テストを書き、レッドを確認する。管理画面の数の整形のテスト（3 桁ごとの区切り、0）を移す（DC-157-3）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/format.test.ts`
- [ ] **T157-02-02** T157-02-01 のテストが通る実装を書く。共有の金額の整形と、店舗画面の税込・税抜の金額の文字は、共有の数の整形を使う。管理画面の数の整形とそのテストを削除し、呼び出し元を共有に変更する。管理画面だけの書式（符号付き、率、短縮）は変えない。既定の数の書式を作る箇所が 1 か所であることを `git grep "new Intl.NumberFormat(LOCALE)"` で確認する（DC-157-3〜5）。
  - 依存：T157-02-01
  - 範囲：`frontend/shared/src/lib/format.ts`、`frontend/store/src/lib/format.ts`、`frontend/admin/src/lib/format.ts`、`frontend/admin/src/lib/format.test.ts`、`frontend/admin/src/features/report/pages/VariantSalesPage.tsx`、`frontend/admin/src/components/ChartValueTooltip.tsx`（コメント）。変更しない：`frontend/admin/src/lib/delta.ts`、`frontend/admin/src/lib/rate.ts`、`frontend/admin/src/features/report/report.ts`
- [x] **T157-02-03** [P] 自分の権限の一覧の戻り値を、状態を判別の項目に持つ 3 つの型の合併にする。呼び出し元の 2 つを、状態で絞り込んでから値を読む形にする。振る舞いは変えない（DC-157-6）。
  - 依存：なし
  - 範囲：`frontend/admin/src/auth/permissions.ts`、`frontend/admin/src/app/Layout.tsx`、`frontend/admin/src/auth/RequirePermission.tsx`。`ReportPermission.test.tsx` などの今の結合テストが通ることを確認する
- [x] **T157-02-04** [P] レポートの条件の項目の props の型を、条件のフォームのファイルで 1 つ定義して export し、レポートの共通の画面の 2 つのコンポーネントがそれを使う（DC-157-7）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/report/components/ReportConditionForm.tsx`、`frontend/admin/src/features/report/components/ReportPage.tsx`
- [x] **T157-02-05** [P] 商品の項目の制約の組を、操作のスキーマから作る関数を、商品の項目のフォームのファイルに置く。登録の画面と基本情報のカードは、それぞれのスキーマを渡す（DC-157-8）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/product/components/ProductFields.tsx`、`frontend/admin/src/features/product/components/BasicInfoCard.tsx`、`frontend/admin/src/features/product/pages/ProductCreatePage.tsx`
- [ ] **T157-02-06** 管理画面の構成の「middleware」を「ミドルウェア」にし、ディレクトリの図を今のファイルに合わせる。`lib/format.ts` の説明から数の整形を削除する。フロントのアーキテクチャの共有のパッケージの一覧に、形の判定、shadcn/ui の共有のコンポーネント、API の生成で使う script を追加する（DC-157-9、35、36）。
  - 依存：T157-02-02（数の整形の置き場所を書くため）
  - 範囲：`docs/design/apps/admin.md`（冒頭の関連 spec の表に 157 を追加する）、`docs/design/frontend-architecture.md`
- [ ] **T157-02-07** skill `new-screen` に「共有の部品」の節を追加する。共有のパッケージの全てのコンポーネントと関数の表（置き場所、役割、使う場面）と、画面に書く前に共有の部品を探して使う手順を書く（DC-157-1、2）。
  - 依存：T157-02-02（共有の数の整形を表に含めるため）
  - 範囲：`.claude/skills/new-screen/SKILL.md`
- [ ] **T157-02-08** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T157-02-01〜07
  - 範囲：レビューの結果、`/.local/review/`
