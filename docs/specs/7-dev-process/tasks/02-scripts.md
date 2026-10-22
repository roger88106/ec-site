# 段階 2：検査の script

- 前提：段階 1
- 完了条件：script のテストが、新しい番号（0 埋めなしの spec、`AC-n-m`、`T7-01-01`）と MVP の番号（0 埋めの 3 桁）の両方で通る。`check-all.sh` が通る。テストは先に書き、レッドを確認した。
- 参照：[plan](../plan.md) 第 2 節、[spec](../spec.md) の DS-7-2 と DC-7-14〜21、7-28、7-29、[開発の進め方](../../../rules/workflow.md) 第 2.1 節

## タスク

script は、実装役のサブエージェントに書かせる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。

- [ ] **T7-02-01** [P] [テスト] spec のディレクトリと番号：`lib/spec-dirs.mjs`（0 埋めなしのディレクトリを読む）、`spec-numbers.mjs`（`AC-n-m`、`T7-01-01`、`spec 7` を検出する）、`related-specs.mjs`（`T<番号>-` を 0 埋めなしで読み、0 で始まる 3 桁を MVP として区別する）のテストを書き、レッドを確認する（DC-7-14〜19）。
  - 依存：なし
  - 範囲：`scripts/ci/lib/spec-dirs.test.mjs`、`scripts/ci/spec-numbers.test.mjs`、`scripts/ci/related-specs.test.mjs`
- [ ] **T7-02-02** T7-02-01 のテストが通る実装を書く。
  - 依存：T7-02-01
  - 範囲：`scripts/ci/lib/spec-dirs.mjs`、`scripts/ci/spec-numbers.mjs`、`scripts/ci/related-specs.mjs`
- [ ] **T7-02-03** [P] [テスト] AC の照合（`ac-check.mjs`）：`AC-n` と `AC-n-m` の両方の定義とテスト名、範囲の書き方の拒否のテストを書き、レッドを確認する（DC-7-20、7-21）。
  - 依存：なし
  - 範囲：`scripts/ci/ac-check.test.mjs`
- [ ] **T7-02-04** T7-02-03 のテストが通る実装を書く。
  - 依存：T7-02-03
  - 範囲：`scripts/ci/ac-check.mjs`
- [ ] **T7-02-05** [P] [テスト] 進捗（`progress.mjs`）：MVP の表と「MVP の完成までの一覧」の照合を続け、`docs/specs/archive/1to10/` の spec の tasks が全て済んでいることを確認するテストを書き、レッドを確認する（DC-7-28、7-29）。
  - 依存：なし
  - 範囲：`scripts/ci/progress.test.mjs`
- [ ] **T7-02-06** T7-02-05 のテストが通る実装を書く。
  - 依存：T7-02-05
  - 範囲：`scripts/ci/progress.mjs`
- [ ] **T7-02-07** CI の検査の文書（`docs/guides/ci.md`）に、変えた検査の説明を反映する。
  - 依存：T7-02-02、T7-02-04、T7-02-06
  - 範囲：`docs/guides/ci.md`
- [ ] **T7-02-08** 段階のレビュー（profile「厚め」：一般の観点は Sonnet 2 回、判定は spec 担当のセッション）と完了の確認：`check-all.sh` が通る。
  - 依存：T7-02-01〜07

組（T7-02-01・02、03・04、05・06）は互いに `[P]` である。
