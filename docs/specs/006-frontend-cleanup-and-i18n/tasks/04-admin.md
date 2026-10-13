# 段階 4：管理画面の繰り返し

- 前提：段階 3
- 完了条件：DC-50〜53。admin のテストとビルド、E2E が通る。
- 参照：[plan](../plan.md) 第 3.1〜3.4 節

## 更新の操作（DC-50）

- [x] **T6401** [テスト] `useApiMutation`（成功の知らせ、一覧の取り直し）と `useMutationGroup`（待ち状態、最初のエラー、エラーの消去）の単体テスト。
- [x] **T6402** 2 つの hook を作り、26 か所を機能ごとに置き換える（税、カテゴリ、商品、在庫、注文の順。機能ごとに commit）。T6401 と既存の結合テストが通る。

## 画面の URL（DC-51）

- [x] **T6403** 管理画面の `src/app/paths.ts` を作り、`router.tsx`・`menu.ts`・`<Link to>`・`navigate()` を置き換える。
- [x] **T6404** 店舗画面の `typedRoutes` を試し、使えれば有効にする。使えなければ `src/lib/paths.ts` を作って置き換える。どちらにしたかを `docs/style/frontend.md` に書く。

## 入力の制約（DC-52）

- [ ] **T6405** [テスト] 制約の書き出しの script のテスト（`maxLength`・`minimum`・`maximum`、参照（`$ref`）、入れ子）。
- [ ] **T6406** `frontend/shared/scripts/` に制約の書き出しの script を作り、admin・store の `generate:api` で定数のファイルを作る。画面の入力の属性をこの定数に置き換える。数字を直接書いた所を探す検査を `scripts/ci/` に足し、CI で流す。

## バリアントの行（P2）

- [ ] **T6407** `VariantSection` を見直し、まだ長ければ `VariantRow` に分ける（DC-53）。
