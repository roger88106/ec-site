# 段階 5：アーカイブと 1 回目のマージ

- 前提：段階 4
- 完了条件：spec 001〜018 が `docs/specs/archive/mvp/` にあり、リンクと CI の検査が通る。文書の検査がアーカイブだけを除外する。Git の規則と CI が main 基準になっている。1 回目の pull request が merge commit で main にマージされ、Pages の公開が成功した。
- 参照：[plan](../plan.md) 第 5、6 節、spec の DS-55（DC-215、216、218）

## タスク

`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（検査の script のテストは commit の前の hook が実行するため。[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。

- [x] **T019-05-01** [テスト] `ac-check.mjs`、`progress.mjs`、`showcase-counts.mjs` が、`docs/specs/` の直下とアーカイブの両方の spec を参照するテストを書く（DC-215）。`showcase-counts.mjs` にテストがなければ、関数を分けてテストを書く。
- [x] **T019-05-02** T019-05-01 の script を修正する（DC-215）。
  - 結果：spec のディレクトリを探す関数を `scripts/ci/lib/spec-dirs.mjs` に分け、3 つの script が使う。`showcase-counts.mjs` の spec の数え方は、この関数のテストで確認する。MVP の完成までの一覧は、直下になければアーカイブから探す（段階 6 で移すため）。
- [x] **T019-05-03** spec 001〜018 を `git mv` でアーカイブに移す。リンクは `fix-links.cjs`、リンクでないパスは段階 1 の script で修正する。旧パスが残らないことを `git grep` で確認する。適用済みのマイグレーションが変わっていないことを確認する（DC-215）。移動だけの commit が関連 spec の検査に当たるときは、除外の一覧（`related-specs-exclusions.json`）にタスク番号の前置きと理由を追加する。
  - 結果：179 ファイルを移し、リンクを 843 か所修正した。リンクでないパスの置き換えは、script の使い方の例だけに当たったので戻した（例として旧パスを書く）。旧パスが残るのは、script のテストのデータだけ。マイグレーションと seed は変わっていない。
- [ ] **T019-05-04** README の進捗の表、constitution、skill（`handoff`、`review`、`docs`）、用語集の spec のパスの記述を修正する（DC-215）。
- [ ] **T019-05-05** [テスト] `doc-terms.mjs` と `doc-refs.mjs` が、アーカイブだけを除外し、進行中の spec を検査するテストを書く（DC-215）。T019-05-03 の移動の後に行う。理由：移動の前に除外を変えると、完了した spec が検査の対象になるため。
- [ ] **T019-05-06** T019-05-05 の script を修正し、進行中の spec（019）で見つかった語とリンクを修正する（DC-215）。
- [ ] **T019-05-07** Git の規則と CI を main 基準にする：`pages.yml` の公開のブランチ、README の CI のバッジ、CI の検査の文書の `pages.yml` の行、AGENTS.md の Git の表と ※2（skill `writing-for-agents` に従う）、TODO 一覧の `develop` を含む項目（DC-218）。
- [ ] **T019-05-08** 利用者に、GitHub の Environment（`github-pages`）の Deployment branches に `main` を追加するよう依頼し、追加されたことを確認する（DC-218）。
- [ ] **T019-05-09** 段階の確認：`./gradlew check`、`check-frontend.sh`、`e2e.sh`、`check-all.sh` を順に実行し、全て通る。README と一覧の進捗を更新する。
- [ ] **T019-05-10** `develop` を push し、`develop` から main への pull request（1 回目）を作る（それぞれ利用者の許可を得る）。CI が通ったら、merge commit でマージする（commit を書き換えない）（DC-216）。
- [ ] **T019-05-11** マージの後、Pages の公開の実行が成功し、ページが表示されることを確認する（DC-218）。
