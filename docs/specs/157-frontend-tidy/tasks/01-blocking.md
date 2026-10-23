# 段階 1：spec 158 が待つ違反の修正

- 前提：plan と tasks の承認、実装を始める前の照合（[開発の進め方](../../../rules/workflow.md) 第 5.6 節）
- 完了条件：テストのファイルに、内部の属性（`data-slot`）と `querySelector` で探す箇所がない。テストの名前に、空白で区切った AC の番号がない。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 11 節、[spec](../spec.md) の DC-157-10、11、14、20、[テストの書き方](../../../rules/test-writing.md) 第 2.1 節、[テストの書き方](../../../rules/test-writing.md) 第 4.1 節

## タスク

実装は実装役のサブエージェントに任せる。修正したテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（plan の共通の決定）。

- [ ] **T157-01-01** [P] 管理画面の注文の詳細と商品の登録のテストで、内部の属性で探す 5 か所を、役割と名前で探す形にする。名前のない状態のバッジと出荷のカードには、画面に支援技術向けの名前を追加する。入力欄のエラーは、エラーの文を入力欄に関連付けて、入力欄の説明で確認する。見える文言は変えない。あわせて、注文の詳細のテストの、空白で区切った AC の番号 1 か所を読点で区切る（DC-157-10、11、20、DN-157-1）。
  - 依存：なし
  - 範囲：`frontend/admin/src/features/order/pages/OrderDetailPage.test.tsx`、`frontend/admin/src/features/product/pages/ProductCreatePage.test.tsx`、注文の詳細の状態のバッジと出荷のカードのコンポーネント（`frontend/admin/src/features/order/`）、商品の登録の入力欄（`frontend/admin/src/features/product/`）。shadcn/ui の生成物（`components/ui/`）は変えない
- [ ] **T157-01-02** [P] 共有の金額の内訳のテストの、Tailwind のクラスと区切り線の属性の確認を、見た目の種類ごとに見える文言と順の確認にする。店舗画面のバリアントの選択のテストの、取り消し線の要素を `querySelector` で調べる確認を削除する（選べないことは、既存の無効の確認で確認している）。カバレッジの基準を下回らないことを確認する（DC-157-14）。
  - 依存：なし
  - 範囲：`frontend/shared/src/components/AmountsSummary.test.tsx`、`frontend/store/src/features/catalog/components/VariantPicker.test.tsx`
- [ ] **T157-01-03** 空白で区切った AC の番号（4 か所）を読点で区切る。`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DC-157-20）。
  - 依存：T157-01-01（AC 一覧の再生成に T157-01-01 の変更を含めるため）
  - 範囲：`frontend/admin/src/features/order/orderFilter.test.ts`、`frontend/admin/src/features/order/pages/OrderListPage.test.tsx`、`frontend/store/src/features/order/labels.test.ts`、`docs/design/acceptance-criteria.md`。`OrderDetailPage.test.tsx` の 1 か所は、同じファイルを変える T157-01-01 で行う
- [ ] **T157-01-04** 段階のレビュー（profile「厚め」）と全体の検査を行う。管理画面を起動し、注文の詳細と商品の登録の画面の見た目が変わらないことを目視する。結果をこのファイルの末尾に書く。
  - 依存：T157-01-01〜03
  - 範囲：レビューの結果、`/.local/review/`
