# 段階 2：共有のパッケージとログインの戻り先

- 前提：段階 1
- 完了条件：DC-134-1〜8、14〜16 を満たす。関係する文書と用語集を更新した。店舗画面のテストとビルドが通る（DN-134-5）。ログインの戻り先の判定のテストは先に書き、レッドを確認した。`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 8 節、[spec](../spec.md) の DS-134-1、DS-134-3 と DC-134-1〜8、14〜16、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[フロントの書き方](../../../rules/frontend-coding.md) 第 5.1 節

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。`[リスク]` のタスク（T134-02-10、11）の実装役は高性能にする。

共有のコンポーネントの置き場所（`frontend/shared/src/components/`）は、ハードコードの文言の検査と入力の制約の検査の対象になる。文言は共有の翻訳の関数で引き、React の hook は使わない（plan の共通の決定）。共有に移した関数のファイルは `@/` を import しない。ロケールは共有の `locale.ts` の定数で書く。

- [x] **T134-02-01** [P] 日付の形の判定（`isDateString`・`DATE`）とそのテストを、共有から管理画面の `lib/date.ts`、`date.test.ts` に移し、呼び出し元の import 先を変更する。共有に `isUuid` は残す（DC-134-1）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/patterns.ts`、`frontend/shared/src/lib/patterns.test.ts`、`frontend/admin/src/lib/date.ts`、`frontend/admin/src/lib/date.test.ts`、`frontend/admin/src/features/order/orderFilter.ts`、`frontend/admin/src/features/report/period.ts`
- [x] **T134-02-02** [P] [テスト] 注文の状態・配送状態・配送業者の表示名の共有のテストを書き、レッドを確認する。管理画面と店舗画面の今のテスト（`labels.test.ts`）の該当するケースを移す（DC-134-2）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/orderLabels.test.ts`
- [x] **T134-02-03** T134-02-02 のテストが通る実装を書く。値の型は共有の文言のキーから作る。管理画面と店舗画面の `features/order/labels.ts` から 3 つの関数を削除する。呼び出し元の import 先を共有に変更する。Badge の見た目と、各画面だけの表示名は各画面に残す。画面の `labels.test.ts` から、移した関数のケースを削除する。
  - 依存：T134-02-02
  - 範囲：`frontend/shared/src/lib/orderLabels.ts`、`frontend/admin/src/features/order/labels.ts`、`frontend/admin/src/features/order/labels.test.ts`、`frontend/admin/src/features/order/components/OrderFilterForm.tsx`、`frontend/admin/src/features/order/components/OrderStatusBadge.tsx`、`frontend/admin/src/features/order/components/ShipmentCard.tsx`、`frontend/admin/src/features/order/pages/OrderListPage.tsx`、`frontend/store/src/features/order/labels.ts`、`frontend/store/src/features/order/labels.test.ts`、`frontend/store/src/app/orders/[orderId]/page.tsx`。E2E の `e2e/tests/shipping.spec.ts` は文言を確認するだけなので変更せず、段階 6 の E2E で通ることを確認する。
- [x] **T134-02-04** [P] [テスト] 金額の内訳の共有のテストを書き、レッドを確認する。店舗画面の今のテスト（AC-77）を共有に移し、見た目の種類（管理画面と店舗画面）ごとに描画できることも確認する。描画は静的な HTML で行い、共有のパッケージの開発用の依存に `react-dom` を追加する（plan 第 1 節。DC-134-3、4）。
  - 依存：なし
  - 範囲：`frontend/shared/src/components/AmountsSummary.test.tsx`、`frontend/store/src/components/AmountsSummary.test.tsx`（削除）、`frontend/shared/package.json`（開発用の依存）、`pnpm-lock.yaml`
- [x] **T134-02-05** T134-02-04 のテストが通る実装を書く。共有の `package.json` の exports に `./components/*` を追加する。店舗画面の `src/components/AmountsSummary.tsx` を削除し、金額の内訳を使う 3 つの画面と、管理画面の注文の明細のカードの内訳の import 先を共有に変更する。`"use client"` を付けるかどうかを確認して決め、理由をコメントに書く。
  - 依存：T134-02-04、T134-02-03（`frontend/store/src/app/orders/[orderId]/page.tsx` を両方が変更するため）
  - 範囲：`frontend/shared/src/components/AmountsSummary.tsx`、`frontend/shared/package.json`、`frontend/store/src/components/AmountsSummary.tsx`（削除）、`frontend/store/src/app/cart/page.tsx`、`frontend/store/src/app/checkout/[checkoutId]/page.tsx`、`frontend/store/src/app/orders/[orderId]/page.tsx`、`frontend/admin/src/features/order/components/OrderLinesCard.tsx`。管理画面の注文の詳細の結合テスト（`OrderDetailPage.test.tsx`、AC-100、252）が通ることを確認する。
- [x] **T134-02-06** [P] 日本時間の年・月・日・時・分の書式を共有の `lib/format.ts` の 1 つの定数にして export し、管理画面の `lib/format.ts` の書式の定義を削除して、この定数を使う（DC-134-5）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/format.ts`、`frontend/shared/src/lib/format.test.ts`、`frontend/admin/src/lib/format.ts`、`frontend/admin/src/lib/format.test.ts`。呼び出し元の `frontend/admin/src/components/DateTimeInput.tsx`、`frontend/admin/src/features/report/period.ts` は、変更せず、テストが通ることを確認する。
- [x] **T134-02-07** リンクの見た目のボタンを共有のコンポーネントにする。`button` の既定の振る舞いを無効にする指定とそのコメントは、このコンポーネントの中だけに置く。リンクの要素は呼ぶ側が渡す。管理画面の 8 か所と店舗画面の 2 か所を置き換える。管理画面の結合テストで、役割 `button` で探すテストが通ることを確認する（DC-134-6）。
  - 依存：T134-02-05（T134-02-05 が追加する exports を使い、共有の `package.json` の変更が衝突しないよう後に行うため）
  - 範囲：`frontend/shared/src/components/LinkButton.tsx`、`frontend/admin/src/features/inventory/pages/StockPage.tsx`、`frontend/admin/src/features/order/pages/OrderDetailPage.tsx`、`frontend/admin/src/features/product/components/StockLink.tsx`、`frontend/admin/src/features/product/pages/ProductCreatePage.tsx`、`frontend/admin/src/features/product/pages/ProductEditPage.tsx`、`frontend/admin/src/features/product/pages/ProductListPage.tsx`、`frontend/admin/src/features/tax/pages/TaxCategoryListPage.tsx`、`frontend/admin/src/features/tax/pages/TaxRatesPage.tsx`、`frontend/store/src/components/PageLinks.tsx`。店舗画面の `PageLinks.test.tsx` が通ることを確認する。
- [x] **T134-02-08** [P] カテゴリの選択肢のテストを、カテゴリの木のテストのファイルに合わせる。`categoryOptions.test.ts` の足りないケースを `categoryTree.test.ts` に移し、`categoryOptions.test.ts` を削除する（DC-134-8）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/categoryOptions.test.ts`（削除）、`frontend/shared/src/lib/categoryTree.test.ts`
- [x] **T134-02-09** 共有のパッケージのテストのコマンドから、テストがなくても成功にする指定を外し、`scripts/dev/check-frontend.sh shared` が通ることを確認する（DC-134-7）。
  - 依存：T134-02-07
  - 範囲：`frontend/shared/package.json`
- [x] **T134-02-10** [P] [テスト] [リスク] ログインの戻り先の判定のテストを追加し、レッドを確認する。受け付ける入力（今のもの）と、受け付けない入力（`/\t/evil.example`、`/\n/evil.example`、`/\r/evil.example`、途中に TAB を含む `/a\tb`）を確認する（DC-134-15）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/login.test.ts`
- [x] **T134-02-11** [リスク] T134-02-10 のテストが通る実装を書く。制御文字（U+0000〜U+001F と U+007F）を 1 つでも含む入力を受け付けない。受け付けないときは、各画面が既定のトップに戻る（DC-134-14）。
  - 依存：T134-02-10
  - 範囲：`frontend/shared/src/lib/login.ts`。呼び出し元の `frontend/admin/src/lib/login.ts`、`frontend/store/src/lib/login.ts`、店舗画面のログインの route（`frontend/store/src/app/login/route.ts`）と認証の設定（`frontend/store/src/auth/index.ts`）は、変更せず、各画面の `lib/login.test.ts`（管理画面と店舗画面）と `frontend/store/src/auth/loginRedirect.test.ts` が通ることを確認する。
- [x] **T134-02-12** [リスク] ログインの戻り先の判定のレビュー（高性能、セキュリティの観点。合否の判定は spec 担当のセッションが行う）。
  - 依存：T134-02-11
- [x] **T134-02-13** ログインの戻り先の条件の記述に「制御文字を含まない」を追加し、判定のコードと照合する（DC-134-16）。
  - 依存：T134-02-11
  - 範囲：`docs/design/apps/admin.md`（[管理画面の構成](../../../design/apps/admin.md) 第 10.1 節）、`docs/design/apps/store.md`（[店舗画面の構成](../../../design/apps/store.md) 第 9.2 節）
- [x] **T134-02-14** 用語集と設計の文書を、共有に移した内容に合わせる。[plan](../plan.md) 第 7 節の表のとおり、用語集に「リンクの見た目のボタン」「金額の内訳」「注文の表示名」の行を追加し、「表示名」の行を修正する。[フロントの書き方](../../../rules/frontend-coding.md) 第 1.1 節と[フロントの書き方](../../../rules/frontend-coding.md) 第 5.1 節、[フロントのアーキテクチャ](../../../design/frontend-architecture.md) 第 4 節（選択肢の「すべて」は段階 5 で書く）、[管理画面の構成](../../../design/apps/admin.md) 第 2 節を修正する。`glossary-ids.mjs` が通ることを確認する。
  - 依存：T134-02-01、03、05、06、07、09、13（管理画面の構成を T134-02-13 と同じファイルで変えるため）
  - 範囲：`docs/design/glossary.md`、`docs/rules/frontend-coding.md`、`docs/design/frontend-architecture.md`、`docs/design/apps/admin.md`
- [ ] **T134-02-15** 段階のレビュー（profile「厚め」：一般の観点は汎用 2 回、重要な観点（ファイルをまたぐ影響、セキュリティ）は高性能と汎用、合否の判定は spec 担当のセッションが行う）と完了の確認：店舗画面のテストとビルドを含めて `scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
  - 依存：T134-02-01〜14

組（T134-02-02・03、04・05、10・11）と、T134-02-01、06、08 は、互いに `[P]` である。ただし、組 T134-02-02・03 と組 T134-02-04・05 は、実装の T134-02-03 と T134-02-05 を順に行う。T134-02-05、07、09 は、共有の `package.json` を変えるので、この順に行う。T134-02-03 と T134-02-05 は、同じファイルを変えるので、この順に行う。
