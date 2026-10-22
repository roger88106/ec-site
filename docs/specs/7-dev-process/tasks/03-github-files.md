# 段階 3：GitHub のファイル

- 前提：段階 1
- 完了条件：issue のテンプレート 3 つと設定、pull request のテンプレート、Dependabot の設定、`main` の元のブランチの検査の workflow が、リポジトリにある。`check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 4 節、[spec](../spec.md) の DS-7-1、DS-7-3、DS-7-4 と DC-7-1、7-2、7-39、7-40、7-51〜57、7-82、7-83

## タスク

- [ ] **T7-03-01** [P] issue のテンプレート（`feature.yml`、`cleanup.yml`、`bug.yml`）と `config.yml` を作る（DC-7-1、7-2）。
  - 依存：なし
  - 範囲：`.github/ISSUE_TEMPLATE/`
- [ ] **T7-03-02** [P] pull request のテンプレートを作る（DC-7-82、7-83）。
  - 依存：なし
  - 範囲：`.github/pull_request_template.md`
- [ ] **T7-03-03** [P] Dependabot の設定を、plan 第 3 節の表のとおりに作る（DC-7-51〜57）。
  - 依存：なし
  - 範囲：`.github/dependabot.yml`
- [ ] **T7-03-04** [P] `main` の元のブランチの検査の workflow を作る。`release-` と `hotfix-` 以外のブランチで失敗する（DC-7-39、7-40）。
  - 依存：なし
  - 範囲：`.github/workflows/main-source.yml`
- [ ] **T7-03-05** 段階の完了の確認：`check-all.sh` が通る。
  - 依存：T7-03-01〜04
