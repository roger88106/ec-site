# 段階 4：文書と手動の確認

- 前提：段階 1〜3 の文書以外のタスク。T104-04-04 は段階 1〜3 の全てと T104-04-01〜03
- 完了条件：文書の検査（`doc-refs.mjs`、`glossary-ids.mjs`、環境変数の検査）が通る。plan 第 7 節の手動の確認を行い、結果を本ファイルに書いた。`check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 9 節、[spec](../spec.md) の DS-104-1〜3 と DC-104-2〜19、104-21〜25、DN-104-4、[開発の進め方](../../../rules/workflow.md) 第 2.1 節

## タスク

文書の書き方は skill `docs`（[SKILL.md](../../../../.claude/skills/docs/SKILL.md)）に従う。

- [ ] **T104-04-01** [P] skill `local-dev` に、スロットの節を加え、負荷の制限の文を修正する（DC-104-21、104-23）。
  - 依存：なし
  - 範囲：`.claude/skills/local-dev/SKILL.md`
- [ ] **T104-04-02** [P] 開発環境の起動、テストの実行方法、環境変数の文書に、スロットの使い方と追加した環境変数を書く（DC-104-24、104-25）。
  - 依存：なし
  - 範囲：`docs/guides/setup.md`、`docs/guides/running-tests.md`、`docs/guides/environment.md`
- [ ] **T104-04-03** [P] 用語集に語と識別子を加え、AGENTS.md の skill の表の `local-dev` の説明を修正する。
  - 依存：なし
  - 範囲：`docs/design/glossary.md`、`AGENTS.md`
- [ ] **T104-04-04** plan 第 7 節の手動の確認（DC-104-2〜19、DN-104-4）を行い、結果を本ファイルに書く。手動の確認の前に、共有の Keycloak の client に、スロットのリダイレクト先を管理 API で一時的に加える（plan 第 3 節、plan 第 9 節）。実施の前に、plan 第 9 節に従って、並行する spec 131 のセッションに compose と E2E を使わないよう知らせる。確認の後に、メインの作業ツリーで `docker compose up -d` を実行して元の状態に戻す。
  - 依存：段階 1〜3、T104-04-01〜03
  - 範囲：本ファイル（結果の記録）
- [ ] **T104-04-05** 段階のレビュー（profile「厚め」：一般の観点は汎用を 2 回（和集合）、重要な観点（セキュリティ、ファイルをまたぐ影響）は高性能と汎用（和集合）、判定は spec 担当のセッション）と完了の確認：`check-all.sh` が通る。
  - 依存：T104-04-01〜04

T104-04-01〜03 は互いに `[P]` である。
