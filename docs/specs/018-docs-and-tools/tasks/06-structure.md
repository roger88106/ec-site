# 段階 6：文書の構成と AC 一覧

- 前提：段階 5
- 完了条件：`docs/` が plan 第 7.1 節の構成になり、リンクと検査が通る。AC 一覧があり、`ac-check` がそれを読む。`./gradlew check`、`check-frontend.sh`、`e2e.sh`、`check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 7 節、spec の DS-52、[constitution](../../../constitution.md)、[開発の進め方](../../../rules/workflow.md)、skill `docs`（[SKILL.md](../../../../.claude/skills/docs/SKILL.md)）

## タスク

- [x] **T018-06-01** 文書を `docs/rules/`、`docs/design/`、`docs/guides/` に移す（DC-196）。
  - リンクを修正する script で、文書の中のリンクを修正する。
  - コード、skill、AGENTS.md、README、CI の workflow のパスも修正する。
  - `doc-refs.mjs` が通る。
  - 結果：各フォルダーの文書が少ないので、領域のフォルダーを作らず、名前に領域を付けて並べた（利用者と決めた）。`modules/` と `apps/` だけフォルダーにした。
    - `docs/rules/`：workflow、notation、review、comments、subagents、backend-coding、frontend-coding、test-writing、api-conventions
    - `docs/design/`：overview、backend-architecture、frontend-architecture、modules/、apps/、common、role-permissions、acceptance-criteria、glossary、licenses
    - `docs/guides/`：setup、environment、ci、running-tests
  - リンク 554 か所を `fix-links.cjs` で直し、リンクでないパスの文字（コードのコメント、skill、CI、検査の script、文書）を 225 ファイルで置き換えた。完了した spec（001〜017）は、リンクだけを直した。検査の script のテストのデータ（架空のパス）は変えていない。
- [x] **T018-06-02** constitution の文書の地図とリポジトリ構成の図を、新しい構成に合わせて書き換える。アーカイブの置き場所（`docs/specs/archive/mvp/`）を書く（DC-196）。
  - 結果：constitution 第 3 節の構成の図を、rules・design・guides・specs と TODO 一覧に書き換え、`.claude/` と `.local/` を足した（全体レビューの DOC-17）。文書の地図を、規範・今の設計・手順・開発記録の 4 つの表に分け、design の文書はコードを説明するもの（コードが正）と書いた。アーカイブの置き場所は `docs/specs/archive/<まとまり>/`（MVP は `archive/mvp/`）とした。
- [x] **T018-06-03** spec 001〜017 の AC を抜き出す script で、AC 一覧の雛形を作り、テストの所在を埋める（DC-197）。
  - 結果：[AC 一覧](../../../design/acceptance-criteria.md)（312 件）。抜き出しと表の作成は、別の script を作らず `ac-check` の `--write` にした（T018-06-06）。要約・元の spec・テストの列は再生成する列、状態・変えた spec・今の内容は手動で書く列で、再生成しても残る。テストの列は、AC をテスト名に持つテストのファイル（3 つまで）、除外、なし。今は 302 件にテストがあり、10 件が除外。状態は全て有効で、T018-06-04 で決める。
  - 利用者の確認待ち：テストの列を `ac-check` が比べるので、テストを追加する・名前を変えると、AC 一覧の再生成が必要になる（hook と CI で失敗する）。文書が古くならない代わりに、手間が増える。
  - 段階 5 の後に行うと決めた文書の移動（T018-06-01・02）より前に行った（利用者と決めた）。
- [x] **T018-06-04** 後の spec にある前の AC 番号の参照を候補にして、各 AC の状態を決める。利用者が確認する（DC-197）。
  - 結果：後の spec の spec・plan にある前の AC 番号の参照（77 か所、45 個の AC）と、各 spec の変更の表を、サブエージェント（Opus）が読んで候補を出し、利用者が確認した。14 件を「変更」にした（AC-70、73、81、84〜87、90、93〜95、101、103、131）。廃止はない。参照されたが有効のままの 35 件は、テストを追加しただけなどで振る舞いを変えていない。
  - 番号で参照せずに変えた AC は、spec 015〜017 の AC の見出しで照合しただけで、全ては調べていない。気づいたら、AC 一覧の行を修正する（開発の進め方 第 1.5 節）。
- [x] **T018-06-05** [テスト] `ac-check` が AC 一覧を読む検査のテストを書く（DC-198）。次のそれぞれで失敗すること。
  - 有効・変更の AC に、テストも除外もない。
  - 廃止の AC を、テストが参照している。
  - spec にあって AC 一覧にない AC がある。
  - 結果：`ac-check.test.mjs` に、廃止の AC と、AC 一覧の作り方・読み方・照合し方のテストを追加した。ローカルでレッド（関数がない）を確認し、T018-06-06 と同じ commit にした。
- [x] **T018-06-06** `ac-check` を修正して、T018-06-05 を通す。
  - 結果：`ac-check` が AC 一覧を読み、廃止の AC をテストが必要な AC から外し、テストが参照するとローカルでも失敗にする。spec と一覧の AC の食い違い、有効・変更・廃止でない状態、古い表（再生成した表と違う）も失敗にする。`--write` で表を書き換える。テストのファイルは、探す順が OS で違うので、名前の順にした。CI の検査の文書の行を修正した。
- [x] **T018-06-07** 開発の進め方に、コードを唯一の正とすること、食い違いの扱い、AC 一覧の更新の手順を書く（DC-199）。
  - 結果：[開発の進め方](../../../rules/workflow.md)第 1.5 節「コードと文書の食い違い」を追加し、第 1.3 節の置き場所の表に AC 一覧を追加した。
- [ ] **T018-06-08** 段階の完了の確認：`./gradlew check`、`check-frontend.sh`、`e2e.sh`、`check-all.sh` が通る（パスを修正したため）。
