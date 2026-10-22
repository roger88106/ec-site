# 段階 1：今の振る舞いを固定するテストの追加

- 前提：なし（spec 134 が確定し、plan が承認された）
- 完了条件：DC-134-27〜36 のテストがあり、今のコードで通る。各テストについて、一時的にコードか期待値を変えて、失敗することを確認した。AC 一覧のテストの列が再生成されている。`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-134-6 と DC-134-27〜36、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[テストの書き方](../../../rules/test-writing.md) 第 2 節

## タスク

実装は実装役のサブエージェントに任せる。この段階のテストは、今のコードで通る。各タスクは、テストを書き、通ることを確認し、一時的に実装か期待値を変えて失敗することを確認する。確認した後に、変更を元に戻す。

商品の編集のテスト（T134-01-01〜06）は同じファイルを変えるので、順に行う。T134-01-01 と、他のファイルのテスト（T134-01-07〜09）は、互いに `[P]` である。T134-01-10 は、バリアントの追加のフォームのテストで、同じファイルを変えるので T134-01-06 の後に行う。

- [x] **T134-01-01** [P] [テスト] 公開する・非公開にするを押すと、その要求を送り、状態の表示と知らせが変わるテストを書く。テスト名に AC-29、AC-30 を入れる（DC-134-27）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T134-01-02** [テスト] 税区分を変えて基本情報を保存すると、入力した商品名・説明・税区分・税抜価格・カテゴリとバージョンを送り、保存を知らせるテストを書く。テスト名に AC-18、AC-19 を入れる（DC-134-28）。
  - 依存：T134-01-01
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T134-01-03** [テスト] 軸が 2 つの商品にバリアントを追加すると、軸の順に値を送るテストを書く。テスト名に AC-20、AC-22 を入れる（DC-134-29）。
  - 依存：T134-01-02
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`、`frontend/admin/src/test/fixtures.ts`（軸が 2 つの商品を作る補助が足りないときだけ）
- [x] **T134-01-04** [テスト] 今の AC-24 のテストで、送った本文の品番が大文字であることも確認する。テスト名に AC-21 を追加する（DC-134-30）。
  - 依存：T134-01-03
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T134-01-05** [テスト] バリアントの並べ替えと削除が、商品のバージョンを添えて送ることを確認するテストを書く。テスト名に AC-27、AC-71 を入れる（DC-134-31）。
  - 依存：T134-01-04
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T134-01-06** [テスト] 画像の並べ替えと削除が、商品のバージョンを添えて送ることを確認するテストを書く。並べ替えのテスト名に AC-37 を入れる。削除に当たる AC はないので、AC の番号を付けない（DC-134-32）。
  - 依存：T134-01-05
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T134-01-07** [P] [テスト] カテゴリの名称の変更で 409 になったら、再読み込みでダイアログを閉じて最新の一覧を表示するテストを書く。AC の番号は付けない（DC-134-33）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/category/pages/CategoryPage.test.tsx`
- [x] **T134-01-08** [P] [テスト] バリアントの売れ行きと、地域・年齢層・性別の CSV（3 つの表を 1 つにまとめたもの）の見出しと行を確認するテストを書く。テスト名に AC-218 を入れる。表示名は今の形で書き、段階 4 で修正する（DC-134-34）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/report/pages/VariantSalesPage.test.tsx`、`frontend/admin/src/features/report/pages/CustomerBreakdownPage.test.tsx`
- [x] **T134-01-09** [P] [テスト] 税率の表示の整形と、URL のページ番号の読み取り（0、1.5、空、負の数、文字）の単体テストを、実装の横に置く。AC の番号は付けない（DC-134-35）。
  - 依存：なし
  - 範囲：`frontend/admin/src/lib/rate.test.ts`、`frontend/admin/src/lib/searchParams.test.ts`（どちらも新規。対象は `frontend/admin/src/lib/rate.ts`、`searchParams.ts`）
- [x] **T134-01-10** [テスト] バリアントの追加のフォームで 409 になった後の再読み込みで、最新の値にフォームを再作成し、入力が消えず、送るバージョンが変わらないことを確認するテストを書く。今の振る舞いを固定するテストで、段階 5 の整理を守るために追加する。spec の DC にはなく、AC の番号は付けない。
  - 依存：T134-01-06
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
  - 結果：再読み込みの前（他の人の更新を再取得した後）は入力が消えず、送るバージョンも変わらない。再読み込みの後は、空の値でフォームを再作成し、送るバージョンは最新になる。両方を固定した。
- [x] **T134-01-11** AC 一覧のテストの列を `node scripts/ci/ac-check.mjs --write` で再生成し、`ac-check.mjs` が通ることを確認する（DC-134-36）。
  - 依存：T134-01-01〜11
  - 範囲：`docs/design/acceptance-criteria.md`
- [x] **T134-01-12** 段階のレビュー（profile「厚め」：一般の観点は汎用 2 回、重要な観点（ファイルをまたぐ影響）は高性能と汎用、判定は spec 担当のセッション）と完了の確認：`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
  - 依存：T134-01-01〜11
  - 結果：4 つのレビュー役（汎用 3、高性能 1）で、高 0、必須 8（重複を含む）。採用 5：基本情報と公開期間の 409 のテストが更新の状態のリセットを確認しない、3 つの CSV のテストがファイル名を確認しない（以上、段階 5 を守るため。高性能）、CSV の BOM が見えない文字のまま、テスト名 `AC-21、AC-24` の書き方、状態の表示「公開」を画面全体から探す。T134-01-13、14 で直す。不採用 3：AC-21 の文は形式が AC-15 と同じことを含む。AC-19・29・30 は画面の側で確認する範囲を plan 第 6 節で決めた。`check-frontend.sh all` と `check-all.sh` は通った。
- [x] **T134-01-13** [テスト] 段階のレビューの指摘で、商品の編集のテストを強める。基本情報と公開期間の 409 のテストに、再読み込みの後に 409 の文が消えたことを加え、公開期間は再読み込みの後に保存して送るバージョンが新しくなることも確認する。テスト名 `AC-21、AC-24` を `AC-21、24` にする。公開・非公開のテストの状態の表示は、状態のカードの中に絞って探す。
  - 依存：T134-01-12
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [x] **T134-01-14** [テスト] 段階のレビューの指摘で、レポートの CSV のテストを強める。3 つの画面の CSV のテストで、ダウンロードのファイル名も確認する。期待する CSV の先頭の BOM は `﻿` と書く（`csv.test.ts` と同じ）。
  - 依存：T134-01-12
  - 範囲：`frontend/admin/src/features/report/pages/SalesTrendPage.test.tsx`、`VariantSalesPage.test.tsx`、`CustomerBreakdownPage.test.tsx`
