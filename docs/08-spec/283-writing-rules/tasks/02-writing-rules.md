# 段階 2：書き方の規則の正本と、語を選ぶ規則

- 前提：段階 1 が完了した（語の検査がインラインコードの中を見ない）。
- 完了条件：DC-283-1〜11 を満たす。DC-283-26 の対象の warning が 0。`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-283-1〜3、6

## タスク

実装は実装役のサブエージェント（汎用）に任せる。規則の文書の本文は、spec 担当のセッションが日本語の要点を書き、実装役が本文を書く（[エージェントの作業フロー](../../../05-開発規則/subagents.md) 第 2 節）。

- 順序：T283-02-01〜04 は同じ文書を変えるので、番号の順に行う。

- [ ] **T283-02-01** 正本 `docs/05-開発規則/writing.md` を作る。移す元は、skill `docs` の「書き方」、`tech-writing.md`、開発の進め方の「spec と plan の書き方」と「文書の書き方」である。内容を変えずに、[plan](../plan.md) 第 1 節の節の構成へ移す。避ける語の例はインラインコードにし、出典の表示を冒頭に置く。移す前と後の項目の対照表を、結果に書く（DC-283-1、4）。
  - 依存：段階 1
  - 範囲：`docs/05-開発規則/writing.md`（新規）
  - 確認方法：対照表で、移す元の全ての項目に移した先がある。`node scripts/ci/doc-refs.mjs`。
- [ ] **T283-02-02** 移す元の文書を、正本を指す形にする（DC-283-2、3、5、6）。
  - skill `docs` は手順だけにする。「置き場所」の表は、constitution の文書の地図を指す 1 行にする。
  - 開発の進め方の 2 つの節は、見出しを残して正本を指す。
  - `tech-writing.md` を削除する。参照する所を正本に向け、`ja-text.mjs` の `EXCLUDED` から外す。
  - constitution の文書の地図と、AGENTS.md の最初に読むものに、正本を加える。
  - `coding.md` の例を、今ある書き方の規則の文書に替える。
  - 依存：T283-02-01
  - 範囲：`.claude/skills/docs/SKILL.md`、`.claude/skills/docs/tech-writing.md`（削除）、`docs/05-開発規則/workflow.md`、`.claude/skills/review/docs-review.md`、`.claude/agents/ja-proofreader.md`、`scripts/ci/ja-text.mjs`、`scripts/ci/ja-text.test.mjs`、`docs/constitution.md`、`AGENTS.md`、`docs/05-開発規則/comments.md`
  - 確認方法：`git grep tech-writing` がアーカイブの外で 0。正本を検査の対象にしても、`node scripts/ci/ja-text.mjs` の error が 0。`scripts/ci/check-all.sh`。
- [ ] **T283-02-03** 正本の「語の選び方」を、[plan](../plan.md) 第 2 節の規則に書き換える。「日本語だけで書く」は、英字を識別子と固有名だけに使う意味だと明確にする。造語の手順が、起票の時点にも当てはまることを書く。正本の「参照」に、[plan](../plan.md) 第 3 節の規則を書く。既存の参照の置き換えは段階 3 で行う（DC-283-7〜11）。
  - 依存：T283-02-02
  - 範囲：`docs/05-開発規則/writing.md`、`docs/05-開発規則/workflow.md`（「起票」の校閲役の行に、造語の手順を指す句を加えるだけ）
  - 確認方法：文書を読む。規則どうしが矛盾しない。今の「和語より技術文書の語」が残る。
- [ ] **T283-02-04** DC-283-26 の対象の `ja-text` の warning を 0 にする。対象は、正本、skill `docs` の `SKILL.md`、開発の進め方の 2 つの節である（DC-283-26）。
  - 依存：T283-02-03
  - 範囲：同上
  - 確認方法：`node scripts/ci/ja-text.mjs --warning` の出力を、対象のファイルと 2 つの節の行の範囲で絞り、行がない。
- [ ] **T283-02-05** 段階の最後のレビュー。観点は、移した内容の漏れと重複（重要な観点として高性能）、規則どうしの矛盾、文書の言い回しである。中国語で指示する環境なので、校閲役も読む。指摘を修正する。
  - 依存：T283-02-04
  - 確認する項目：
    - 対照表で、移す元の全ての項目が正本にあり、2 か所に残っていない（DC-283-1、2）。
    - skill `docs` に規則が残っていない（DC-283-3）。
    - 変えたコードを[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。
