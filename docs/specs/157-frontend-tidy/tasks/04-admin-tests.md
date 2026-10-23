# 段階 4：管理画面のテストの修正

- 前提：段階 3
- 完了条件：管理画面のテストが DC-157-12、15、18〜27、31、32 を満たす。`git grep -n "as never" -- 'frontend/admin/*.test.ts*'` が 0 件。テストの関数が `test` にそろっている。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-157-4、5、[テストの書き方](../../../rules/test-writing.md)、レビューの結果（`/.local/review/202610231200-157-frontend-tests/admin-1.md`、`admin-2.md`）

## タスク

実装は実装役のサブエージェントに任せる。実装役には、範囲のファイルのレビューの指摘を渡す。修正したテストは、一時的に実装か期待値を変更して、失敗しうることを確認する。テスト名の AC は、AC の文と照合してから書き、照合の結果を commit の本文に書く。

- [x] **T157-04-01** [P] 注文のテストを修正する。DOM の位置で探す所を補助に、`as never` を型付きの応答に、出荷の登録の応答を契約の型にする。コメントだけにある AC-142 をテストの名前に入れる。AC-268、AC-186 の同じ構成のテストを `test.each` にまとめる。一覧の行は補助で探す（DC-157-12、15、18、21、27）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/order/pages/OrderDetailPage.test.tsx`、`OrderListPage.test.tsx`、`frontend/admin/src/features/order/labels.test.ts`（名前、DC-157-19）
- [x] **T157-04-02** [P] 商品のテストを修正する。`as never` を型付きの応答に、商品の更新の応答を契約の 204 にする。送った本文に契約の型を付ける。在庫数のハンドラーは補助を使う。並びのインデックスと行の探し方を補助に、部分一致の確認を全体の一致にする。商品の一覧の項目と 409 の応答は補助を使う。関数名だけの describe と名詞だけの名前を振る舞いの文にする（DC-157-12、15、18、19、24、31、32）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/product/pages/ProductCreatePage.test.tsx`、`ProductEditPage.test.tsx`、`ProductListPage.test.tsx`、`frontend/admin/src/features/product/imageUpload.test.ts`、`frontend/admin/src/features/product/product.test.ts`
- [x] **T157-04-03** [P] カテゴリと在庫のテストを修正する。HTML の要素の種類と DOM の位置で探す所を、役割と名前か補助に（名前のない要素は画面に支援技術向けの名前を追加する）、`as never` を型付きの応答にする。AC-9・270 と AC-12 の重複したテストをまとめる。在庫の調整の履歴の「空」を確認する。カテゴリの節と 409 の応答は補助を使い、行の要素の型の変換を除く（DC-157-11、12、15、18、22、27、31、32）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/category/pages/CategoryPage.test.tsx`、`frontend/admin/src/features/inventory/pages/StockPage.test.tsx`、名前を追加する画面のコンポーネント（`features/category/`、`features/inventory/`）
- [x] **T157-04-04** [P] 税のテストを修正する。`as never` を型付きの応答に、税区分の再定義と 409 の応答を補助にする（DC-157-15、31、32）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/tax/pages/TaxCategoryListPage.test.tsx`、`TaxRatesPage.test.tsx`
- [x] **T157-04-05** [P] レポートのテストを修正する。行と要求を並びのインデックスで選ぶ所を補助と件数の確認に、重ねた `expect` と同じハンドラーの重複をまとめる。名前を振る舞いの文にする（DC-157-12、19、24、32）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/report/pages/SalesTrendPage.test.tsx`、`VariantSalesPage.test.tsx`、`CustomerBreakdownPage.test.tsx`、`ReportPermission.test.tsx`、`frontend/admin/src/features/report/report.test.ts`、`frontend/admin/src/features/report/labels.test.ts`
- [x] **T157-04-06** [P] その他のテストを修正する。吹き出しの件数の確認と、ログインの利用者の型の変換を修正する（DC-157-18、24）。
  - 依存：なし
  - 範囲：`frontend/admin/src/components/ChartValueTooltip.test.tsx`、`frontend/admin/src/auth/AuthGate.test.tsx`
- [x] **T157-04-07** 管理画面のテストの関数を `test` にそろえる（24 ファイル）。他の変更を含めない。`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DC-157-28、21）。
  - 依存：T157-04-01〜06（同じファイルを変えるため）
  - 範囲：`frontend/admin/src/**/*.test.ts(x)`、`docs/design/acceptance-criteria.md`
- [x] **T157-04-08** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T157-04-01〜07
  - 範囲：レビューの結果、`/.local/review/`

## 段階のレビューの結果

2026-10-23 に行った。対象は段階 4 の差分（T157-04-01〜07）。レビューの前に `develop`（spec 158 のマージを含む）を取り込み、共有の金額の整形の衝突を解消した。

| レビュー役 | モデル | 高 | 必須 |
|------------|--------|----|------|
| 一般の観点（ファイルをまたぐ影響を含む）1 回目 | 汎用 | 0 | 2 |
| 一般の観点（ファイルをまたぐ影響を含む）2 回目 | 汎用 | 0 | 3 |
| ファイルをまたぐ影響 | 高性能 | 0 | 1 |

修正したもの（重複を除いて 4 件）：

- AC-268 の each の表に関数の呼び出しがあり、AC 一覧の script がテスト名を読めず、AC 一覧のテストの列から注文の詳細のテストが消えていた。表を定数に出した。script の読み方の改善は、起票の案に入れる（DN-157-6）。
- 在庫数の取得の差し替えが 2 つのファイルにあった（DC-157-32）。
- 注文の一覧のテストが、行の最後のセルを位置で選んでいた（DC-157-12）。列の見出しからセルを選ぶ補助を作り、3 つのファイルで使う。
- 注文の詳細のテストの最上位の型に TSDoc がなかった。

全体の検査：`scripts/dev/check-frontend.sh all`、カバレッジの基準（3 つとも基準以上）、`CI=true scripts/ci/check-all.sh`（spec 158 が加えた検査を含む）が通った。
