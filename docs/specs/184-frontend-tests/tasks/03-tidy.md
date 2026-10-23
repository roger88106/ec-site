# 段階 3：テストの構成、書き方、補助とデータ

- 前提：段階 2（同じテストのファイルを触るため）
- 完了条件：管理画面と店舗画面のテストが DC-184-11〜21 を満たす。補助の単体テストは先に書き、レッドを確認した。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-184-4、5、[テストの書き方](../../../rules/test-writing.md) 第 4.5 節、[管理画面の構成](../../../design/apps/admin.md) 第 9 節、レビューの結果（`/.local/review/202610231200-157-frontend-tests/admin-1.md`、`admin-2.md`、`/.local/review/202610232100-157-final/general-2.md`）

## タスク

実装は実装役のサブエージェントに任せる。書き換えたテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-184-4）。

- [ ] **T184-03-01** 偽の時計の後始末を店舗画面と管理画面の setup に置き、各ファイルから削除する。店舗画面の環境変数の直接の代入を `vi.stubEnv` にする（DC-184-16、17）。
  - 依存：なし
  - 範囲：`frontend/store/src/test/setup.ts`、`frontend/admin/src/test/setup.ts`、`frontend/admin/src/features/report/pages/SalesTrendPage.test.tsx`、`frontend/store/src/features/checkout/components/RemainingTime.test.tsx`、`frontend/store/src/lib/server/timeout.test.ts`
- [ ] **T184-03-02** [テスト] 管理画面の日時の入力の補助と、店舗画面の現在の秒の補助のテストを書き、レッドを確認する。管理画面の一覧のセルの補助（行の見出しのセル、入れ子の表）と、ダウンロードの記録の補助（`download` の属性のないリンク）の想定外の使い方のテストを加え、レッドを確認する（DC-184-15、16、20）。
  - 依存：T184-03-01
  - 範囲：`frontend/admin/src/test/inputs.test.ts`（新規）、`queries.test.tsx`、`download.test.ts`、`frontend/store/src/test/auth.test.ts`（新規）
- [ ] **T184-03-03** T184-03-02 のテストが通る補助を書く。店舗画面の現在の秒の計算を、補助を使う形にする（DC-184-15、16、20）。
  - 依存：T184-03-02
  - 範囲：`frontend/admin/src/test/inputs.ts`（新規）、`queries.ts`、`download.ts`、`frontend/store/src/test/auth.ts`、`frontend/store/src/auth/index.test.ts`、`frontend/store/src/lib/server/timeout.test.ts`
- [ ] **T184-03-04** [P] 商品の編集のテストを整える。409 の流れのテストを振る舞いごとに分け、`const URL` の名前を変え、日時の入力とファイルの選択を補助と `user.upload` にし、テストの間に空行を入れ、補助を包むだけの関数を削除する（DC-184-11〜13、15、18）。
  - 依存：T184-03-03
  - 範囲：`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`
- [ ] **T184-03-05** [P] 税区分、税率、在庫、注文の詳細のテストを整える。税率の 409 の流れのテストを分け、`const URL` の名前を変え、税率の日時の入力の補助を共通の補助に置き換え、テストの間に空行を入れる（DC-184-11〜13、15、18）。
  - 依存：T184-03-03
  - 範囲：`frontend/admin/src/features/tax/pages/TaxRatesPage.test.tsx`、`TaxCategoryListPage.test.tsx`、`frontend/admin/src/features/inventory/pages/StockPage.test.tsx`、`frontend/admin/src/features/order/pages/OrderDetailPage.test.tsx`
- [ ] **T184-03-06** [P] 管理画面の hook、ログイン、レポートの条件のテストを整える。変更をまとめる hook を `renderHook` で呼び、ログインの戻り先のテストから共有のテストとの重複を削除し、レポートの条件の商品 ID は補助を使う（DC-184-14、18、19）。
  - 依存：T184-03-03
  - 範囲：`frontend/admin/src/api/useMutationGroup.test.ts`、`frontend/admin/src/lib/login.test.ts`、`frontend/shared/src/lib/login.test.ts`（網羅に漏れがあるときだけ）、`frontend/admin/src/features/report/reportCondition.test.ts`、`frontend/admin/src/test/fixtures.ts`、`reportServer.ts`
- [ ] **T184-03-07** [P] 店舗画面の地域のデータを補助に移す。地域のサーバーアクションと注文のフォームのテストは、補助を使う（DC-184-18）。
  - 依存：T184-03-03
  - 範囲：`frontend/store/src/test/fixtures.ts`、`frontend/store/src/actions/region.test.ts`、`frontend/store/src/features/checkout/components/OrderForm.test.tsx`
- [ ] **T184-03-08** 管理画面のカテゴリの木と商品の画像の、重ねた支援技術向けの名前を 1 つにする。テストと、探し方が当たる E2E を合わせる（DC-184-21）。
  - 依存：T184-03-04（商品の編集のテストが重なるため）
  - 範囲：`frontend/admin/src/features/category/components/CategoryTreeList.tsx`、`frontend/admin/src/features/product/components/ProductImagesCard.tsx`、`frontend/admin/src/features/category/pages/CategoryPage.test.tsx`、`frontend/admin/src/features/product/pages/ProductEditPage.test.tsx`（商品の画像のテストだけ）、`e2e/`（当たる所だけ）
- [ ] **T184-03-09** 管理画面の構成 第 9 節と店舗画面の構成 第 7 節の補助の表に、追加・移動した補助を書く。`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DN-184-3）。
  - 依存：T184-03-01〜08
  - 範囲：`docs/design/apps/admin.md`、`docs/design/apps/store.md`、`docs/design/acceptance-criteria.md`
- [ ] **T184-03-10** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T184-03-01〜09
  - 範囲：レビューの結果、`/.local/review/`
