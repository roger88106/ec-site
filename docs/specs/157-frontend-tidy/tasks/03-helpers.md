# 段階 3：テストの規則とテストの補助

- 前提：段階 2
- 完了条件：テストの書き方に本 spec の規則がある（DC-157-28）。plan 第 4.2 節の補助があり（DC-157-13、29〜33 の補助）、補助の単体テストは先に書き、レッドを確認した。全てのテストの後のクリーンアップを各ファイルで繰り返さない（DC-157-34）。`scripts/dev/check-frontend.sh all`、カバレッジの基準、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 4 節、[spec](../spec.md) の DS-157-6、DC-157-13、28、[テストの書き方](../../../rules/test-writing.md)、[コメントの書き方](../../../rules/comments.md)

## タスク

実装は実装役のサブエージェントに任せる。補助は本段階では作るだけにし、テストのファイルの置き換えは段階 4・5 で行う。例外は、補助に移す重複したテストデータの元の定義である。移した後は、元のファイルが補助を使う形にする。

- [x] **T157-03-01** テストの書き方に規則を追加する。テストの関数は `test` にそろえる。複数のテストで使うデータ、API の差し替え、操作、確認は各画面のテストの補助の置き場所に置く。テストは型を回避する変換を使わない（避けられない所は理由を書く）。詳細の項目の値と一覧の行を探す書き方は補助だけに置く。規則の一覧の表に行を追加する（DC-157-28、13）。
  - 依存：なし
  - 範囲：`docs/rules/test-writing.md`
- [x] **T157-03-02** [P] [テスト] 管理画面の詳細の項目の値と一覧の行を探す補助の単体テストを書き、レッドを確認する（DC-157-13）。
  - 依存：T157-03-01
  - 範囲：`frontend/admin/src/test/queries.test.tsx`
- [x] **T157-03-03** T157-03-02 のテストが通る補助を書く。
  - 依存：T157-03-02
  - 範囲：`frontend/admin/src/test/queries.ts`
- [x] **T157-03-04** [P] 管理画面のダウンロードの記録の補助を書く。URL の作成とリンクのクリックを `vi.spyOn` で差し替え、保存名と本文を返す。管理画面の setup に `restoreAllMocks` を追加し、同じクリーンアップを持つファイルの `afterEach` を削除する。レポートの 3 画面の CSV のテストを、この補助に置き換える（DC-157-29、30）。
  - 依存：T157-03-01
  - 範囲：`frontend/admin/src/test/download.ts`、`frontend/admin/src/test/setup.ts`、`frontend/admin/src/features/report/pages/SalesTrendPage.test.tsx`、`VariantSalesPage.test.tsx`、`CustomerBreakdownPage.test.tsx`（CSV のテストと、そのための `afterEach` だけ）、`frontend/admin/src/api/useApiMutation.test.tsx`（`afterEach` だけ）
- [x] **T157-03-05** [P] 管理画面の API の差し替えの補助に、409 の応答と、条件で応答を変えるハンドラーが返す型付きの応答を追加する。テストデータの補助に、商品の一覧の項目、カテゴリの節、在庫数の応答を追加する。`api/errors.test.ts` の問題の応答と、`App.test.tsx`・税率のテストの税区分の再定義を、補助を使う形にする（DC-157-15、31、32）。
  - 依存：T157-03-01
  - 範囲：`frontend/admin/src/test/server.ts`、`frontend/admin/src/test/fixtures.ts`、`frontend/admin/src/api/errors.test.ts`、`frontend/admin/src/app/App.test.tsx`。税率のテストの置き換えは T157-04-04 で行う
- [x] **T157-03-06** [P] 店舗画面のテストデータの補助に、注文、お届け先、バリアントと軸、カートの ID を追加し、本番の環境変数の一式の補助を作る。補助と同じ定義を持つファイル（フォームの値、カートの ID）を、補助を使う形にする（DC-157-31）。
  - 依存：T157-03-01
  - 範囲：`frontend/store/src/test/fixtures.ts`、`frontend/store/src/test/env.ts`、`frontend/store/src/lib/server/formData.test.ts`。注文・バリアント・環境変数を使うファイルの置き換えは段階 5 で行う
- [x] **T157-03-07** [P] [テスト] 店舗画面の標準エラー出力の記録、通信の失敗の応答、見つからないことの確認の補助のテストを書き、レッドを確認する。見つからないことの確認の補助は、今の型の変換を型の絞り込みにする（DC-157-16、18、23、33）。
  - 依存：T157-03-01
  - 範囲：`frontend/store/src/test/stderr.test.ts`、`frontend/store/src/test/next.test.ts`、`frontend/store/src/test/server.test.ts`
- [x] **T157-03-08** T157-03-07 のテストが通る補助を書く。店舗画面の 4 ファイルの `restoreAllMocks` の `afterEach` を削除する。理由：setup で実行するため（DC-157-34）。
  - 依存：T157-03-07
  - 範囲：`frontend/store/src/test/stderr.ts`、`frontend/store/src/test/next.ts`、`frontend/store/src/test/server.ts`、`frontend/store/src/actions/withActionResult.test.ts`、`frontend/store/src/api/client.test.ts`、`frontend/store/src/lib/server/log.test.ts`、`frontend/store/src/instrumentation.test.ts`（`afterEach` だけ）
- [x] **T157-03-09** 管理画面の構成の結合テストの補助の表に、追加した補助を書く。
  - 依存：T157-03-03〜05
  - 範囲：`docs/design/apps/admin.md` 第 9 節
- [ ] **T157-03-10** 段階のレビュー（profile「厚め」）と全体の検査を行う。結果をこのファイルの末尾に書く。
  - 依存：T157-03-01〜09
  - 範囲：レビューの結果、`/.local/review/`
