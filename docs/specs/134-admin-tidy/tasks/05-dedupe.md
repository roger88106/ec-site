# 段階 5：管理画面の複製

- 前提：段階 4
- 完了条件：DC-134-23〜26 を満たす。段階 1 のテストと今の結合テストが、変更前と同じ振る舞いで通る。店舗画面のテストとビルドを含む。関係する文書を更新した。`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-134-5 と DC-134-23〜26、[開発の進め方](../../../rules/workflow.md) 第 2.1 節

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。

- [ ] **T134-05-01** [P] 409 の後の再読み込みの処理を、`useVersionedForm` の 1 つの関数にまとめる。バージョン付きのフォームの hook に、更新の状態をリセットしてから、最新の値でフォームを再作成する関数を追加し、4 か所の呼び出し元がその関数を再読み込みの操作に渡す。段階 1 のテスト（T134-01-10 を含む）と今の 409 の結合テストが通ることを確認する（DC-134-23）。
  - 依存：T134-01-10（段階 1）
  - 範囲：`frontend/admin/src/hooks/useVersionedForm.ts`、`frontend/admin/src/features/product/components/AddVariantForm.tsx`、`frontend/admin/src/features/product/components/BasicInfoCard.tsx`、`frontend/admin/src/features/product/components/PublicationPeriodCard.tsx`、`frontend/admin/src/features/tax/components/TaxRatesEditor.tsx`。`ProductEditPage.test.tsx`、`TaxRatesPage.test.tsx` が通ることを確認する。
- [ ] **T134-05-02** [P] [テスト] 「すべて」を先頭に付けた選択肢を作る共有の関数のテストを書き、レッドを確認する（DC-134-24）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/options.test.ts`
- [ ] **T134-05-03** T134-05-02 のテストが通る実装を書く。カテゴリの選択肢もこの関数を使う。管理画面の注文の絞り込みと商品の絞り込みのフォームも、この関数を使う。店舗画面の `ListFilterForm.tsx` は、結果が同じなので変えない（DC-134-24）。
  - 依存：T134-05-02
  - 範囲：`frontend/shared/src/lib/options.ts`、`frontend/shared/src/lib/categoryTree.ts`、`frontend/shared/src/lib/categoryTree.test.ts`、`frontend/admin/src/features/order/components/OrderFilterForm.tsx`、`frontend/admin/src/features/product/components/ProductFilterForm.tsx`。呼び出し元の `frontend/admin/src/components/CategorySelect.tsx`、`frontend/store/src/features/catalog/components/ListFilterForm.tsx` は、変更せず、テストが通ることを確認する。
- [ ] **T134-05-04** [P] レポートの 3 つの画面の共通の処理（見出し、レポートの設定の取得、条件を URL から読む・URL に書く処理、条件のフォームの再作成）を 1 つのコンポーネントにする。画面ごとに違うもの（既定の期間、選択肢、粒度、送料の除外、商品の必須、取得と表示）は props で渡す。3 つの画面を置き換える（DC-134-25）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/report/components/ReportPage.tsx`、`frontend/admin/src/features/report/pages/SalesTrendPage.tsx`、`frontend/admin/src/features/report/pages/VariantSalesPage.tsx`、`frontend/admin/src/features/report/pages/CustomerBreakdownPage.tsx`。`SalesTrendPage.test.tsx`、`VariantSalesPage.test.tsx`（段階 1 の CSV のテストを含む）、`CustomerBreakdownPage.test.tsx`、`ReportPermission.test.tsx` が通ることを確認する。
- [ ] **T134-05-05** [P] メニューの項目の表示を 1 つのコンポーネントにし、機能の群とレポートの群がそれを使う（DC-134-26）。
  - 依存：なし
  - 範囲：`frontend/admin/src/app/Layout.tsx`。`ReportPermission.test.tsx` が通ることを確認する。
- [ ] **T134-05-06** 文書を更新する。[フロントのアーキテクチャ](../../../design/frontend-architecture.md) 第 4 節の共有に今置いているものに、選択肢の「すべて」を追加する。[管理画面の構成](../../../design/apps/admin.md) 第 5.1 節に、409 の後の再読み込みは hook の関数を使うことを追加する。
  - 依存：T134-05-01、T134-05-03
  - 範囲：`docs/design/frontend-architecture.md`、`docs/design/apps/admin.md`
- [ ] **T134-05-07** 段階のレビュー（profile「厚め」：一般の観点は汎用 2 回、重要な観点（ファイルをまたぐ影響）は高性能と汎用、合否の判定は spec 担当のセッションが行う）と完了の確認：`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
  - 依存：T134-05-01〜06

組（T134-05-02・03）と、T134-05-01、04、05 は、互いに `[P]` である。T134-05-06 は、それらの後に行う。
