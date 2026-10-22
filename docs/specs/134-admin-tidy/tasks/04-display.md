# 段階 4：表示と文言

- 前提：段階 3
- 完了条件：DC-134-17〜22 を満たす。DN-134-1 の例外 4 件のうち、この段階の 2 件（バリアントの売れ行きの表示名、グラフの吹き出しの値）は、テストを先に書き、レッドを確認した。`scripts/dev/check-frontend.sh all`（店舗画面のテストとビルドを含む）と `scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 4 節、[spec](../spec.md) の DS-134-4 と DC-134-17〜22、DN-134-1、2、6、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[フロントの書き方](../../../rules/frontend-coding.md) 第 7 節

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。shadcn/ui の生成物（`frontend/admin/src/components/ui/chart.tsx`）は修正しない（DN-134-6）。

- [x] **T134-04-01** [P] [テスト] バリアントの売れ行きの表示名のテストを書き、レッドを確認する。軸があれば「名前：値」を区切りの文言で並べた形（例：「色：白、サイズ：M」）、軸がなければ品番になることを、表示名を組み立てる関数の単体テストで確認する。段階 1 の画面のテスト（表、グラフ、CSV の表示名）も、新しい形に修正する（DC-134-17〜19）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/report/labels.test.ts`、`frontend/admin/src/features/report/pages/VariantSalesPage.test.tsx`
- [x] **T134-04-02** T134-04-01 のテストが通る実装を書く。表示名を組み立てる関数をレポートの表示名のファイルに置き、共有の軸の値の表示を、管理画面の区切りの文言で並べる。表、グラフ、CSV は、この 1 つの値を使う（DC-134-17〜19）。
  - 依存：T134-04-01
  - 範囲：`frontend/admin/src/features/report/labels.ts`、`frontend/admin/src/features/report/pages/VariantSalesPage.tsx`
- [x] **T134-04-03** [P] レポートの条件のフォームの期間の選択肢の文言を、コンポーネントの中で使う hook（`useTranslations`）で引く。コンポーネントの外で使う翻訳の関数の import を削除する（DC-134-20）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/report/components/ReportConditionForm.tsx`。このコンポーネントを使う `frontend/admin/src/features/report/pages/SalesTrendPage.tsx`、`VariantSalesPage.tsx`、`CustomerBreakdownPage.tsx` のテストが通ることを確認する。
- [x] **T134-04-04** [P] [テスト] グラフの吹き出しの値の整形のテストを書き、レッドを確認する。数の整形の単体テストと、ラッパーのコンポーネントの結合テスト（値を渡して描画し、`1,234 円` と `1,234` を確認する）である。jsdom で描画できないときは、作業を中断して報告する（DC-134-21）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/format.test.ts`、`frontend/admin/src/components/ChartValueTooltip.test.tsx`
- [x] **T134-04-05** T134-04-04 のテストが通る実装を書く。共有に数の整形を追加し、グラフの吹き出しで値だけを整形するラッパーのコンポーネントを置く。3 つのレポートの画面の 4 か所の吹き出しを変更する。金額は共有の円の整形、数量は共有の数の整形を使う（DC-134-21）。
  - 依存：T134-04-02、T134-04-04（バリアントの売れ行きの画面を同じ段階で変えるため）
  - 範囲：`frontend/shared/src/lib/format.ts`、`frontend/admin/src/components/ChartValueTooltip.tsx`、`frontend/admin/src/features/report/pages/SalesTrendPage.tsx`、`frontend/admin/src/features/report/pages/VariantSalesPage.tsx`、`frontend/admin/src/features/report/pages/CustomerBreakdownPage.tsx`
- [x] **T134-04-06** [P] 商品の項目のフォームが、入力の制約を props で受け取る。登録は登録の要求のスキーマ、編集は更新の要求のスキーマから引く（DC-134-22）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/product/components/ProductFields.tsx`、`frontend/admin/src/features/product/components/BasicInfoCard.tsx`、`frontend/admin/src/features/product/pages/ProductCreatePage.tsx`。`ProductCreatePage.test.tsx`、`ProductEditPage.test.tsx` が通ることを確認する。
- [x] **T134-04-07** [P] カテゴリの名称のダイアログが、名称の最大の長さを props で受け取る。登録は登録の要求、名称の変更は名称の変更の要求のスキーマから引く（DC-134-22）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/category/components/CategoryNameDialog.tsx`、`frontend/admin/src/features/category/pages/CategoryPage.tsx`。`CategoryPage.test.tsx` が通ることを確認する。
- [ ] **T134-04-08** 段階のレビュー（profile「厚め」：一般の観点は汎用 2 回、重要な観点（ファイルをまたぐ影響）は高性能と汎用、合否の判定は spec 担当のセッションが行う）と完了の確認：`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
  - 依存：T134-04-01〜07

組（T134-04-01・02、04・05）と、T134-04-03、06、07 は、互いに `[P]` である。ただし、T134-04-05 は T134-04-02 と同じ画面のファイルを変えるので、T134-04-02 の後に行う。
