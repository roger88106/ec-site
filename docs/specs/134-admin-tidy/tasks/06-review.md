# 段階 6：完了

- 前提：段階 1〜5
- 完了条件：spec の最後のレビューの採否を決め、反映した。E2E が通る。画面の目視を終えた。起票のまとめの案がある。本 spec を `docs/specs/archive/1to10/134-admin-tidy/` に移した。`develop` への pull request がある。
- 参照：[plan](../plan.md) 第 9 節、[plan](../plan.md) 第 10 節、[spec](../spec.md)、[開発の進め方](../../../rules/workflow.md) 第 4.1 節、[開発の進め方](../../../rules/workflow.md) 第 4.2 節、[開発の進め方](../../../rules/workflow.md) 第 6 節、[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節

## タスク

- [ ] **T134-06-01** spec の最後のレビュー：profile「厚め」で行い、組み合わせは[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節の表に従う。ファイルをまたぐ影響の観点を必ず含める。推奨は、自動の検査に移せるかを付けてまとめる。
  - 依存：段階 1〜5
- [ ] **T134-06-02** E2E を実行し（`scripts/dev/e2e.sh`）、通ることを確認する。注文、出荷、レポート、権限のシナリオが、共有の変更の影響を受けるため。
  - 依存：T134-06-01
- [ ] **T134-06-03** 画面を目視で確認する（skill `local-dev`）。管理画面と店舗画面を起動し、注文の詳細（金額の内訳）、商品の一覧（リンクの見た目のボタン）、レポート（表示名、吹き出し）、権限の取得を失敗させたとき（失敗の表示）を確認する。
  - 依存：T134-06-01
- [ ] **T134-06-04** 文書のレビュー（skill `review` の文書のレビューの手順）：本 spec で修正した文書（用語集、フロントの書き方、フロントのアーキテクチャ、管理画面の構成、店舗画面の構成）を、skill `docs` の言い回しと論理の規則に照合する。
  - 依存：T134-06-01
  - 範囲：`docs/design/glossary.md`、`docs/rules/frontend-coding.md`、`docs/design/frontend-architecture.md`、`docs/design/apps/admin.md`、`docs/design/apps/store.md`
- [ ] **T134-06-05** 起票のまとめの案を作る。後回しにする作業、未決事項、spec の最後のレビューの推奨のうち採用しなかったものを、既存の issue と重複しないようにまとめ、案を pull request の本文に付ける。利用者が承認したら、issue 担当のセッションが起票する（[開発の進め方](../../../rules/workflow.md) 第 4.1 節）。
  - 依存：T134-06-01〜04
- [ ] **T134-06-06** pull request の前のレビュー：profile「厚め」のとおり、高性能のレビュー役が spec との一致と完了の定義を確認し、組み込みのコードレビュー（`/code-review`）も行う。指摘の扱いを決め、修正する（[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節）。
  - 依存：T134-06-05
- [ ] **T134-06-07** 本 spec（spec、plan、tasks）を `docs/specs/archive/1to10/134-admin-tidy/` に移し、参照を修正する。移した後に `scripts/ci/check-all.sh` が通る（[開発の進め方](../../../rules/workflow.md) 第 4.2 節）。
  - 依存：T134-06-06
- [ ] **T134-06-08** 作業ブランチを push し、テンプレートで `develop` への pull request を作る。本文には、受け入れの確認、推奨の扱い、起票する issue の案を書く。追跡の issue（#134）に pull request の作成をコメントする。
  - 依存：T134-06-07

T134-06-07 は、T134-06-08 の直前の commit で行う。理由：アーカイブした spec は、進捗の検査が全てのタスクの済みを求めるため（[spec 7 の段階 6](../../archive/1to10/7-dev-process/tasks/06-completion.md) の結果と同じ）。

## tasks の外の作業

pull request を作った後の作業（`develop` へのマージ）は、tasks に書かない。マージは spec 担当のセッションが利用者の許可を得てから行い、#134 のコメントに記録する（[AGENTS.md](../../../../AGENTS.md) の Git の節）。
