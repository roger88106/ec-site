# 段階 5：店舗画面と共有のパッケージのテストの修正

- 前提：段階 3（段階 4 とは依存がない。スロットとテストの実行が重ならないよう、段階 4 の後に行う）
- 完了条件：店舗画面と共有のパッケージのテストが DC-157-14〜16、18〜27、31〜33 を満たす。AC 一覧を再生成した。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-157-4、5、[テストの書き方](../../../rules/test-writing.md) 第 1.1 節（店舗画面のテストの種類）、レビューの結果（`/.local/review/202610231200-157-frontend-tests/store-1.md`、`store-2.md`）

## タスク

実装は実装役のサブエージェントに任せる。実装役には、範囲のファイルのレビューの指摘を渡す。修正したテストは、一時的に実装か期待値を変更して、失敗しうることを確認する。HTML に描画するテストを DOM で探す形に全面で書き換えることと、フォームのテストの React の差し替えは、本 spec で扱わない（plan 第 7 節）。

- [x] **T157-05-01** [P] 共有のパッケージのテストを修正する。整形のテストの重複を関数ごとの describe にまとめ、注文の表示名のテストの名前を振る舞いの文にする（DC-157-19、27）。
  - 依存：なし
  - 範囲：`frontend/shared/src/lib/format.test.ts`、`frontend/shared/src/lib/orderLabels.test.ts`
- [x] **T157-05-02** [P] 商品の表示のテストを修正する。代替画像の確認を削除し、`toBeTruthy` と要素の型の変換を修正する。バリアントと軸は補助を使う。カテゴリの一覧のテストをカテゴリの一覧のファイルに移す。関数名だけの describe と名詞だけの名前を振る舞いの文にする（DC-157-14、18、19、24、26、31）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/catalog/components/VariantPicker.test.tsx`、`ProductCard.test.tsx`、`ProductShowcase.test.tsx`、`ListFilterForm.test.tsx`、`CategoryList.test.tsx`、`frontend/store/src/features/catalog/variantSelection.test.ts`
- [x] **T157-05-03** [P] カートと注文手続きのテストを修正する。購入できない品目の小計の取り消し線の確認を削除する。AC-256 は結果の処理を待ってから確認する。数量を表示しないことのテスト名と確認を一致させる。まとまりの名前だけにある AC-84 をテストの名前に移す。`toBeTruthy` と要素の型の変換を修正する（DC-157-14、18、21、22、24、25）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/cart/components/CartItemControls.test.tsx`、`frontend/store/src/features/checkout/components/OrderForm.test.tsx`、`RemainingTime.test.tsx`、`frontend/store/src/features/checkout/remainingTime.test.ts`、`frontend/store/src/components/QuoteItemSummary.test.tsx`、`frontend/store/src/lib/labels.test.ts`
- [ ] **T157-05-04** [P] 注文のテストを修正する。名前を振る舞いの文にし、まとまりの名前だけにある AC の番号をテストの名前に移す。注文の状態のバッジのテストを自分のファイルに移す。注文とお届け先は補助を使う。電話番号とメールアドレスは項目名と合わせて確認する。画面の props の型の変換を修正する（DC-157-18、19、21、24、26、31）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/order/orderState.test.ts`、`orderListQuery.test.ts`、`labels.test.ts`、`frontend/store/src/features/order/components/CancellationRequestForm.test.tsx`、`OrderStatusBadge.test.tsx`（新規）、`CancellationSection.test.tsx`、`PaymentSection.test.tsx`、`ShippingAddressDetails.test.tsx`、`frontend/store/src/app/orders/[orderId]/page.test.tsx`
- [ ] **T157-05-05** [P] サーバーアクションと API の接続のテストを修正する。引数のない例外の確認を補助と内容の確認に、標準エラー出力の確認を補助に、通信の失敗を補助にする。注文の操作のサーバーアクションのテストを自分のファイルに移す。契約にある応答を型付きのハンドラーにし、契約にない応答に理由を書く。要求の本文の型の変換を修正する。結果の包みのテストの重複をまとめる（DC-157-15、16、18、23、26、27、32、33）。
  - 依存：なし
  - 範囲：`frontend/store/src/actions/withActionResult.test.ts`、`checkout.test.ts`、`order.test.ts`（新規）、`cart.test.ts`、`region.test.ts`、`frontend/store/src/api/client.test.ts`、`cart.test.ts`、`result.test.ts`、`frontend/store/src/app/checkout/[checkoutId]/page.test.tsx`
- [ ] **T157-05-06** [P] 店舗画面の lib とサーバーの処理のテストを修正する。追跡の ID のテストを自分のファイルに移し、ログの確認を補助と内容の確認にする。カートの ID の例外の確認に内容を加え、カートの ID は補助を使う。ログインの戻り先のテストの重複を削除する（DC-157-23、24、26、27、31、33）。
  - 依存：なし
  - 範囲：`frontend/store/src/lib/server/log.test.ts`、`trace.test.ts`（新規）、`timeout.test.ts`、`frontend/store/src/lib/cartId.test.ts`、`frontend/store/src/lib/cartChoice.test.ts`、`frontend/store/src/lib/login.test.ts`、`frontend/store/src/instrumentation.test.ts`
- [ ] **T157-05-07** [P] 認証と proxy のテストを修正する。Auth.js の引数と proxy の型の変換を、型に合う値を作る補助にする。環境変数の一式は補助を使う。重複したテスト（公開の URL と設定、ログインと Cookie の接頭辞）をまとめる。ヘッダーのテストは、役割と名前で要素を探して確認する（DC-157-18、24、27、31）。
  - 依存：なし
  - 範囲：`frontend/store/src/auth/index.test.ts`、`cookiePrefix.test.ts`、`publicUrl.test.ts`、`config.test.ts`、`loginRedirect.test.ts`、`frontend/store/src/proxy.test.ts`、`frontend/store/src/components/SiteHeader.test.tsx`
- [ ] **T157-05-08** `node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する（DN-157-3）。
  - 依存：T157-05-01〜07
  - 範囲：`docs/design/acceptance-criteria.md`
- [ ] **T157-05-09** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T157-05-01〜08
  - 範囲：レビューの結果、`/.local/review/`
