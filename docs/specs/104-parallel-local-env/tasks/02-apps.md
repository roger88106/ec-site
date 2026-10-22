# 段階 2：画面と IdP

- 前提：段階 1
- 完了条件：店舗画面の Cookie の接頭辞のテストが通り、Keycloak の統合テストが通る。プレビューの設定とポートの予約の script にスロットの項目がある。`check-all.sh` が通る。テストは先に書き、レッドを確認した。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-104-1・DS-104-3 と DC-104-10〜12、104-22、[開発の進め方](../../../rules/workflow.md) 第 2.1 節

## タスク

`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。

- [x] **T104-02-01** [P] [テスト] 店舗画面の Cookie の接頭辞：設定の読み込みと Cookie の名前のテストを書き、レッドを確認する（DC-104-12）。
  - 依存：なし
  - 範囲：`frontend/store/src/auth/*.test.ts`、`frontend/store/src/api/cartCookie` のテスト
- [x] **T104-02-02** T104-02-01 のテストが通る実装を書く。Auth.js の現在の版の文書（context7）で、Cookie の名前の設定方法を確認する。
  - 依存：T104-02-01
  - 範囲：`frontend/store/src/auth/`、`frontend/store/src/api/cartCookie.ts`
- [x] **T104-02-03** [P] realm の定義にスロットのリダイレクト先を加え、Keycloak の統合テストを流す（DC-104-11）。
  - 依存：なし
  - 範囲：`docker/keycloak/realms/*.json`
- [x] **T104-02-04** [P] プレビューの設定にスロットの項目を加える（DC-104-22）。
  - 依存：T104-01-04
  - 範囲：`.claude/launch.json`
- [x] **T104-02-05** [P] Windows のポートの予約の script にスロットのポートを加える（DC-104-10）。
  - 依存：なし
  - 範囲：`scripts/dev/windows/exclude-ports.ps1`
- [ ] **T104-02-06** 段階のレビュー（profile「厚め」：一般の観点は汎用を 2 回（和集合）、重要な観点（セキュリティ、ファイルをまたぐ影響）は高性能と汎用（和集合）、判定は spec 担当のセッション）と完了の確認：`check-all.sh` が通る。
  - 依存：T104-02-01〜05

T104-02-01 と 02 の組、T104-02-03、T104-02-04、T104-02-05 は互いに `[P]` である。
