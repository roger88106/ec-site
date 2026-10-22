# 段階 3：権限の取得の失敗

- 前提：段階 2
- 完了条件：DC-134-9〜13 を満たす。結合テストは先に書き、レッドを確認した。500 の応答と通信の失敗の両方で確認する。関係する文書を更新した。`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 3 節、[spec](../spec.md) の DS-134-2 と DC-134-9〜13、DN-134-1、2、[開発の進め方](../../../rules/workflow.md) 第 2.1 節

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。

- [ ] **T134-03-01** [テスト] 自分の権限の一覧の取得の失敗の結合テストを書き、レッドを確認する。テストの描画の補助に、自分の権限の一覧の応答（500 の応答と、通信の失敗）を差し替えるオプション `me` を追加する。結合テストのキャッシュの再試行の間隔を 0 にする（回数は本番と同じ 3 回。間隔を引数で受ける）。テストは 2 つで、どちらも 500 の応答と通信の失敗の両方で確認する（`it.each` でよい）。AC の番号は付けない。
  - 自分の権限の一覧の取得に失敗すると、レポートの画面は取得の失敗を示し、再試行して開ける（DC-134-9、10、13）
  - 自分の権限の一覧の取得に失敗すると、メニューにレポートの入口を全て表示する（DC-134-11、13）
  - 依存：なし
  - 範囲：`frontend/admin/src/test/renderApp.tsx`、`frontend/admin/src/app/queryClient.ts`、`frontend/admin/src/features/report/pages/ReportPermission.test.tsx`。`queryClient.ts` の呼び出し元の `frontend/admin/src/main.tsx`、`frontend/admin/src/api/useApiMutation.test.tsx` は、変更せず、テストが通ることを確認する。
- [ ] **T134-03-02** T134-03-01 のテストが通る実装を書く。自分の権限の一覧の取得の状態を「取得中・失敗・取得済み」の 3 つで返し、失敗のときは再試行する関数も返す。権限が必要な画面は、失敗のときにエラーの文と「再試行」のボタンを示す。メニューは、失敗のときにレポートの 3 つの入口を全て表示する。取得中の挙動は変更しない（画面は何も示さず、メニューにレポートを表示しない）。文言「再試行」を管理画面の文言ファイルに追加する（DC-134-9〜12）。
  - 依存：T134-03-01
  - 範囲：`frontend/admin/src/auth/permissions.ts`、`frontend/admin/src/auth/RequirePermission.tsx`、`frontend/admin/src/app/Layout.tsx`、`frontend/admin/messages/ja.json`。`RequirePermission` を使う `frontend/admin/src/app/router.tsx` は、変更せず、テストが通ることを確認する。
- [ ] **T134-03-03** [管理画面の構成](../../../design/apps/admin.md) 第 10 節の「権限による出し分け」の行に失敗のときの扱いを追加し、[管理画面の構成](../../../design/apps/admin.md) 第 9 節の補助の表の `renderApp` に `me` のオプションを追加する。
  - 依存：T134-03-02
  - 範囲：`docs/design/apps/admin.md`
- [ ] **T134-03-04** 段階のレビュー（profile「厚め」：一般の観点は汎用 2 回、重要な観点（ファイルをまたぐ影響）は高性能と汎用、合否の判定は spec 担当のセッションが行う）と完了の確認：`scripts/dev/check-frontend.sh all` と `scripts/ci/check-all.sh` が通る。
  - 依存：T134-03-01〜03
