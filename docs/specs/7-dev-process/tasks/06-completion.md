# 段階 6：完了

- 前提：段階 1〜5
- 完了条件：spec の最後のレビューの採否が決まり反映した。本 spec を `docs/specs/archive/1to10/` に移した。テンプレートで書いた `develop` への pull request がある。
- 参照：[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-7-5 と DC-7-31、7-91〜94

## tasks の外の作業

pull request を作った後の作業は、tasks に書かない（plan 第 5 節）。この作業は issue 担当のセッションが行い、#7 のコメントに記録する。対象は次のとおり。

- マージ
- #7 への完了のコメント
- release の前のレビュー
- `main` の元のブランチの検査を必須にする設定
- `release-0.2.0`、`main` への pull request、マージ、tag `v0.2.0`、GitHub の Release

理由：マージの後に tasks を更新するには、もう 1 つの pull request が必要になるため。

## タスク

- [ ] **T7-06-01** spec の最後のレビュー：profile「厚め」で行い、組み合わせは plan 第 4 節の表に従う。ファイルをまたぐ影響の観点を必ず含める。推奨は、自動の検査に移せるかを付けてまとめる。
  - 依存：段階 1〜5
- [ ] **T7-06-02** 本 spec の途中で見つけた進め方の不備の扱い（修正したもの、起票したもの）を表にする（DC-7-94）。
  - 依存：T7-06-01
- [ ] **T7-06-03** 本 spec（spec、plan、tasks）を `docs/specs/archive/1to10/` に移し、参照を修正する。移した後に `check-all.sh` が通る（DC-7-31）。
  - 依存：T7-06-01、T7-06-02
- [ ] **T7-06-04** pull request の前のレビュー（Opus と `/code-review`）。
  - 依存：T7-06-03
- [ ] **T7-06-05** 作業ブランチを push し、テンプレートで `develop` への pull request を作る。本文には、受け入れの確認、推奨の扱い、起票する issue の案を書く（DC-7-91）。
  - 依存：T7-06-04
