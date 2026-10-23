# 段階 6：完了

- 前提：段階 1〜5
- 完了条件：規則と設計書がコードと一致する（DC-155-44〜46）。spec の最後のレビューの採否を決め、反映した。E2E が通る。起票のまとめの案がある。本 spec を `docs/specs/archive/1to10/155-backend-structure/` に移した。
- 参照：[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 8 節、[plan](../plan.md) 第 9 節、[spec](../spec.md)、[開発の進め方](../../../rules/workflow.md) 第 4.1 節、[開発の進め方](../../../rules/workflow.md) 第 4.2 節、[開発の進め方](../../../rules/workflow.md) 第 6 節、[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節

## タスク

- [ ] **T155-06-01** 各モジュールの設計書の「構成」の節を、全てのコードと照合して修正する（skill `review` の plan・tasks の照合の手順に準じる）。対象は 13 モジュールである。片付けのジョブ、設定の型と設定のクラス、決済の view のパッケージ、カートと税の公開の API の利用者の記述が、コードと一致することを確認する（DC-155-44、45）。
  - 依存：段階 1〜5
  - 範囲：`docs/design/modules/{cart,catalog,inventory,media,member,notification,order,payment,region,reporting,shipping,storefront,tax}.md`
- [ ] **T155-06-02** [バックエンドの書き方](../../../rules/backend-coding.md) 第 1 節（標準の構成、パッケージの大きさの表）と[バックエンドの書き方](../../../rules/backend-coding.md) 第 6 節（設定の値の型）を、本 spec の決定と照合して修正する。バックエンドのアーキテクチャと共通コードの設計書の、構成とテスト専用の操作の記述も照合する。パッケージの大きさの表は、現在の 10 を超えるパッケージの一覧と一致させる（DC-155-46）。
  - 依存：段階 1〜5
  - 範囲：`docs/rules/backend-coding.md`、`docs/design/backend-architecture.md`、`docs/design/common.md`
- [ ] **T155-06-03** 全体の検査：`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` を実行する。mutation とカバレッジが基準（line 98.09、branch 85.26、domain の mutation 90.2）を下回らないことを確認する。
  - 依存：T155-06-01、T155-06-02
- [ ] **T155-06-04** spec の最後のレビュー：profile「厚め」で行い、組み合わせは[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節の表に従う。ファイルをまたぐ影響の観点を必ず含める。DC-155-1〜48 と DN-155-1〜5 を満たすことを確認する。推奨ごとに、自動の検査に移せるかどうかを併記する。DN-155-2 のため、spec の最初の commit からの差分に `api/openapi/` と `db/migration/` のファイルがないことも確認する。
  - 依存：T155-06-03
- [ ] **T155-06-05** E2E を実行し（`scripts/dev/e2e.sh`）、通ることを確認する。API の応答を変えないことを、注文、出荷、レポート、店舗の画面のシナリオで確認するため。
  - 依存：T155-06-04
- [ ] **T155-06-06** 文書のレビュー（skill `review` の文書のレビューの手順）：本 spec で修正した文書を、skill `docs` の言い回しと論理の規則と照合する。
  - 依存：T155-06-04
  - 範囲：`docs/rules/backend-coding.md`、`docs/design/common.md`、`docs/design/backend-architecture.md`、`docs/design/modules/`（本 spec で修正したもの）
- [ ] **T155-06-07** 起票のまとめの案を作る。後回しにする作業、未決事項、spec の最後のレビューの推奨のうち採用しなかったものを、既存の issue と重複しないようにまとめ、案を pull request の本文に付ける。利用者が承認したら、issue 担当のセッションが起票する（[開発の進め方](../../../rules/workflow.md) 第 4.1 節）。
  - 依存：T155-06-04〜06
- [ ] **T155-06-08** pull request の前のレビュー：profile「厚め」のとおり、高性能のレビュー役が spec との一致と完了の定義を確認し、組み込みのコードレビュー（`/code-review`）も行う。指摘の扱いを決め、修正する（[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節）。
  - 依存：T155-06-07
- [ ] **T155-06-09** 本 spec（spec、plan、tasks）を `docs/specs/archive/1to10/155-backend-structure/` に移し、参照を修正する。移した後に `scripts/ci/check-all.sh` が通る（[開発の進め方](../../../rules/workflow.md) 第 4.2 節）。そのタスク自身を含む全てのチェックボックスを `[x]` にする。
  - 依存：T155-06-08

T155-06-09 は tasks の最後に行うタスクで、pull request を作る直前の commit で行う。理由：アーカイブした spec は、進捗の検査が全てのタスクの済みを求めるため。

## tasks の外の作業

pull request の作成と、その後の `develop` へのマージと後片付けは、tasks に書かない（[開発の進め方](../../../rules/workflow.md) 第 4.2 節、[開発の進め方](../../../rules/workflow.md) 第 5.7 節）。pull request はテンプレートで作り、本文に受け入れの確認、推奨の扱い、起票する issue の案を書き、#155 にコメントする。マージは spec 担当のセッションが利用者の許可を得てから行う。
