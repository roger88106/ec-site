# 段階 6：文書の構成と AC 一覧

- 前提：段階 5
- 完了条件：`docs/` が plan 第 7.1 節の構成になり、リンクと検査が通る。AC 一覧があり、`ac-check` がそれを読む。`./gradlew check`、`check-frontend.sh`、`e2e.sh`、`check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 7 節、spec の DS-52、[constitution](../../../constitution.md)、[開発の進め方](../../../process/workflow.md)、skill `docs`（[SKILL.md](../../../../.claude/skills/docs/SKILL.md)）

## タスク

- [ ] **T018-06-01** 文書を `docs/rules/`、`docs/design/`、`docs/guides/` に移す（DC-196）。
  - リンクを直す script で、文書の中のリンクを直す。
  - コード、skill、AGENTS.md、README、CI の workflow のパスも直す。
  - `doc-refs.mjs` が通る。
- [ ] **T018-06-02** constitution の文書の地図とリポジトリ構成の図を、新しい構成に合わせて書き直す。アーカイブの置き場所（`docs/specs/archive/mvp/`）を書く（DC-196）。
- [ ] **T018-06-03** spec 001〜017 の AC を抜き出す script で、AC 一覧の雛形を作り、テストの所在を埋める（DC-197）。
- [ ] **T018-06-04** 後の spec にある前の AC 番号の参照を候補にして、各 AC の状態を決める。利用者が確認する（DC-197）。
- [ ] **T018-06-05** [テスト] `ac-check` が AC 一覧を読む検査のテストを書く（DC-198）。次のそれぞれで失敗すること。
  - 有効・変更の AC に、テストも除外もない。
  - 廃止の AC を、テストが参照している。
  - spec にあって AC 一覧にない AC がある。
- [ ] **T018-06-06** `ac-check` を直して、T018-06-05 を通す。
- [ ] **T018-06-07** 開発の進め方に、コードを唯一の正とすること、食い違いの扱い、AC 一覧の更新の手順を書く（DC-199）。
- [ ] **T018-06-08** 段階の完了の確認：`./gradlew check`、`check-frontend.sh`、`e2e.sh`、`check-all.sh` が通る（パスを直したため）。
