# 段階 2：店舗画面と共有のパッケージの HTML のテストの書き換え

- 前提：段階 1
- 完了条件：店舗画面と共有のパッケージのテストが、描画した HTML の文字を確認せず、DOM に読み込んで役割と名前で探す（DC-184-1、3）。jsdom のテストの状態と属性の確認が jest-dom の照合の関数である（DC-184-7）。開始タグの補助を削除した（DC-184-4）。spec 第 6 節の違反（`toBeDefined` 3 か所、HTML の正規表現・文字の一致 14 か所）が 0 件。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 3 節、[spec](../spec.md) の DS-184-1、2、[spec](../spec.md) 第 6 節、[テストの書き方](../../../../../rules/test-writing.md) 第 4.1 節、レビューの結果（`/.local/review/202610231200-157-frontend-tests/store-1.md`、`store-2.md`）

## タスク

実装は実装役のサブエージェントに任せる。書き換えたテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-184-4）。探す名前がない要素に支援技術向けの名前を追加したときは、`e2e/` の探し方と衝突しないかを確認する（plan 第 3 節）。

- [x] **T184-02-01** [P] 共通の部品のテストを書き換える（DC-184-1、3）。
  - 依存：なし
  - 範囲：`frontend/store/src/components/ActionError.test.tsx`、`LoginLinks.test.tsx`、`PageLinks.test.tsx`、`QuoteItemSummary.test.tsx`、`SiteHeader.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [x] **T184-02-02** [P] カートのテストを書き換える。購入できない品目の小計のテストは、購入できる品目との違いを確認する名前と確認にする（DC-184-1、3、22）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/cart/components/CartItemControls.test.tsx`、`CartMergeNoticeView.test.tsx`、`StartCheckoutForm.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [x] **T184-02-03** [P] 商品のテストを書き換える。jsdom のテストの `.disabled`、`getAttribute` の比較を jest-dom にする（DC-184-1、3、7）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/catalog/components/CategoryList.test.tsx`、`ListFilterForm.test.tsx`、`ProductCard.test.tsx`、`ProductShowcase.test.tsx`、`VariantPicker.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [x] **T184-02-04** [P] 注文のテストを書き換える。jsdom のテストの `getAttribute` の比較を jest-dom にする（DC-184-1、3、7）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/order/components/CancellationRequestForm.test.tsx`、`CancellationSection.test.tsx`、`OrderStatusBadge.test.tsx`、`PaymentSection.test.tsx`、`ShippingAddressDetails.test.tsx`、`frontend/store/src/app/orders/[orderId]/page.test.tsx`。名前を追加するときは、範囲のテストが対象とする部品（plan 第 7 節）
- [x] **T184-02-05** [P] 共有のパッケージの金額の内訳のテストを、DOM に読み込んで確認する形にする（plan 第 1 節 ※2）。2 つの見た目の種類のテストの名前に、種類ごとに見える違いを書き、`expectInOrder` を見つからない文言が分かる確認にする（DC-184-1、22）。
  - 依存：なし
  - 範囲：`frontend/shared/src/components/AmountsSummary.test.tsx`
- [x] **T184-02-06** 開始タグの補助とそのテストを削除し、店舗画面の構成 第 7 節の補助の表から `markup.ts` の行を削除する（DC-184-4、5）。
  - 依存：T184-02-01〜05
  - 範囲：`frontend/store/src/test/markup.ts`、`markup.test.ts`、`docs/design/apps/store.md`
- [x] **T184-02-07** `node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DN-184-3）。
  - 依存：T184-02-01〜06
  - 範囲：`docs/design/acceptance-criteria.md`
- [x] **T184-02-08** 段階のレビュー（profile「厚め」）と全体の検査を行う。spec 第 6 節の違反が 0 件であることを `git grep` で確認する。結果をこのファイルの末尾に書く。
  - 依存：T184-02-01〜07
  - 範囲：レビューの結果、`/.local/review/`

## 段階のレビューの結果

2026-10-24 に行った。対象は段階 2 の差分（T184-02-01〜07）。値や制約をまたぐ変更がないので、一般の観点だけを行った。

| レビュー役 | モデル | 高 | 必須 |
|------------|--------|----|------|
| 一般の観点 1 回目（テストと規則） | 汎用 | 0 | 4 |
| 一般の観点 2 回目（一貫性、重複、文書） | 汎用 | 1 | 2 |

修正したもの（重複を除いて 5 件）：

- カテゴリの一覧の「何も出さない」の確認が、元（HTML が空）より弱かった（DN-184-4）。`renderMarkup` の結果が空であることの確認に戻し、店舗画面の構成 第 7 節に例外として書いた。共通の部品とカートの通知の同じ確認は、段階の中で先に戻した。
- カートの通知の 2 つの一覧を、並びのインデックスで区別していた（テストの書き方 第 4.1 節）。中の項目の文言で特定する形にした。
- 注文のフォームの順の確認が `toBeTruthy` だった。他のファイルと同じ `toBe` にした。
- 共有のパッケージに setup のファイルを新しく置いていた（plan 第 1 節、テストの書き方 第 4.5 節）。setupFiles で jest-dom を直接読み込む形に戻し、型は tsconfig の types で解決した。

修正しなかったもの：

- 購入できない品目の小計の取り消し線を確認していない（高）：取り消し線は見た目なので目視で確認する（spec 157 の DC-157-14）。合計に含めないことは API のテストが確認する。
- 絞り込みのフォームを、入力欄の `form`（フォームの所有者）から取っている（必須）：DOM の位置でなく、入力欄が属するフォームを返すプロパティなので残し、理由をコメントに書いた。フォームに名前を付けると読み上げ（ランドマーク）が変わるので付けない（spec 第 7 節 ※1）。

実装の途中で、並行の実装役の commit に別のタスクのファイルが入ったので、T184-02-03 と 02-04 の commit に分け直した。AC 一覧の再生成（T184-02-07）は、T184-02-02 の commit で行い、その後の差分はない。spec 第 6 節の違反は、型を回避する変換のうち、理由をコメントに書いた 1 か所を除いて 0 件になった。

全体の検査：`scripts/dev/check-frontend.sh all`、カバレッジの基準（3 つとも基準以上）、`CI=true scripts/ci/check-all.sh` が通った。
