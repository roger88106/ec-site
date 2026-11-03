# Spec 331：skill から規則と手順書の写しを除き、skill と規則の境界を決める

種類：整理　規模：M　ステータス：確定

## 背景と目的

整理の段階の第 2 フェーズの spec 7。対象は、`.claude/skills/` の local-dev、sessions（`rounds.md` を含む）、new-screen、review（`prompts.md`、`convergence.md`、`docs-review.md` を含む）、docs、zh-mixing と、`.claude/agents/` の定義である。

棚卸し（2026-10-29）で、次の 5 つの問題が見つかった。

- skill に、手順書や規則の転記がある。
- 規則が skill にあり、規則の文書に手順の説明がある。
- 同じ説明が複数の skill にある。
- 日付つきの経緯や、一度きりの測定の結果が本文に残っている。
- agents の定義が、AGENTS.md の「モデルの区分」と合っていない。

skill には、エージェントが実行する手順と雛形だけを残す。規則は `docs/05-開発規則/` に、手順書の内容は `docs/06-手順書/` に置き、skill はそれを指す。

消費者・運営者に見える振る舞いも、コードも変えない。全体の字数の圧縮は spec 8 で行う。追跡の issue は #331。

## スコープ

| 含める | 含めない |
|--------|----------|
| レビューの重要度の定義と、収束の規則を、規則の文書に移す | 書き方の規則の 4 つの文書（spec 6） |
| 手元の hook の設定と、作業ツリーのセッションが読むものについて、説明の重複をなくす | 全体の字数の圧縮（spec 8） |
| 「本文は日本語だけの文脈のサブエージェントに書かせる」の規則の重複の解消 | 英語の原文のまま取り込んだ skill（writing-for-agents、retro、handoff）の本文。存在しない文書名の参照などの誤りの修正は含める |
| skill から、手順書と規則の転記を削除し、正本を指す | |
| skill の間で重複した説明をなくし、日付つきの経緯と一度きりの測定の結果を移すか削除する | |
| `.claude/agents/` の定義の修正 | |

決定（利用者の承認、2026-11-03）：

- 残すものの基準：skill に残すのは、エージェントだけが行う手順（ツールの使い方、待ち方、出力の読み方、避ける書き方）と雛形である。人も行う手順は `docs/06-手順書/` に置き、skill はその節を指す。規則（何をするか、なぜか）は `docs/05-開発規則/` に置く。
- 手順書に同じ内容がある記述は、skill から削除する。skill にだけあり、人にも必要な手順は、手順書に移す。
- 理由の 1 行は残してよい。出来事の説明（いつ、どの spec・issue で何が起きたか）と、一度きりの測定の結果は、本文から削除するか `docs/07-記録/` に移す。
- 重要度と収束の規則は、[書き方の規則とレビュー](../../05-開発規則/review.md)に節を加えて移す。`convergence.md` は削除する。`prompts.md` の「重要度」の節は削除し、review.md を指す。
- 「日本語だけの文脈のサブエージェントに書かせる」の規則の正本は、[文書の書き方](../../05-開発規則/writing.md)の冒頭とする。手順（要点の作り方、渡すもの、指示に入れる文）は、skill `docs` の「進め方」に残す。AGENTS.md、[エージェントの作業フロー](../../05-開発規則/subagents.md)、[開発の進め方](../../05-開発規則/workflow.md)、skill `sessions` は、言い換えを書かない。規則は writing.md の冒頭を、手順は skill `docs` の「進め方」を指す。
- 手元の hook の設定（SessionStart、backslash-guard、zh-mixing の PostToolUse）の JSON と、その前の説明文は、skill `local-dev` の「手元の hook の設定」にだけ置く。skill `zh-mixing` は、環境の印（git の設定 `ecsite.instructionLanguage`）と git の hook の行だけを書く。Claude Code の hook は、local-dev のその節を指す。
- 作業ツリーのセッションが読むものの表は、skill `sessions` の「ツールの補足」に残す。測定の出典と日付、公式の文書との比較の段落は、[エージェントの作業フローの調べた仕組みと実績](../../07-記録/subagents-results.md)に移す。
- skill `new-screen` の共有の部品の表は削除し、`frontend/shared/src/` の `lib`・`components`・`ui` の export を読んで探す手順に替える。
- 層 1・層 2 の分け方の正本は、skill `review` の `docs-review.md` の手順 4 とする。skill `zh-mixing` は、層 2 の一覧の修正の方法だけを書き、分け方はそこを指す。
- ステップの終わりの手順は、skill `sessions` の `rounds.md` にだけ書く。SKILL.md の「ステップの終わり」は、手順の名前と rounds.md の節への参照だけにする。
- 並行の可否：spec 6（#330）と並行で進める（利用者の決定、2026-11-03）。重なるファイルと順序は、tasks の「並行する spec との重なり」に書く。

## 目的と完了条件

### DS-331-1 規則を、規則の文書の 1 か所で読めるようにする（D / P1）

- **DC-331-1** 重要度（高・必須・推奨）の定義と、時点ごとに返す重要度が、`docs/05-開発規則/review.md` にだけ書かれている。skill `review`、`workflow.md`、`subagents.md` は、review.md の節を指す。
- **DC-331-2** 収束の規則（由来、既存の指摘の扱い、2 回目以降の対象、繰り返しの上限、同じ種類の指摘、同じ判定の別の書き方の指摘、採らなかった指摘）が、`docs/05-開発規則/review.md` にだけ書かれている。`.claude/skills/review/convergence.md` がない。
- **DC-331-3** 「文書の本文は、日本語の要点から、日本語だけの文脈のサブエージェントに書かせる」の規則が、`docs/05-開発規則/writing.md` にだけ書かれている。AGENTS.md、`subagents.md`、`workflow.md`、skill `docs`、skill `sessions` には、言い換えた文がない。skill `docs` の手順と、skill `sessions` の雛形の参照は残る。
- **DC-331-4** 層 1・層 2 の分け方（何をどちらの一覧に加えるか）が、skill `review` の `docs-review.md` の手順 4 にだけ書かれている。

### DS-331-2 skill に、手順と雛形だけを残す（D / P1）

- **DC-331-5** 手元の hook の設定の JSON と、その前の説明文が、skill `local-dev` の「手元の hook の設定」にだけある。作業ツリーのセッションが読むものの表が、skill `sessions` にだけある。skill `local-dev`、skill `zh-mixing`、`subagents.md`、AGENTS.md は、表を言い換えずに指す。
- **DC-331-6** skill `local-dev` のスロット・決済・E2E・データの節に、`docs/06-手順書/setup.md`・`running-tests.md` と同じ内容（コマンドの一覧、ポートの表、同じ注意の文）がない。残るのは、エージェントだけが行う手順と、手順書の節への参照である。
- **DC-331-7** skill `new-screen` に、`frontend/shared` の部品の一覧の転記がない。
- **DC-331-8** ステップの終わりの手順の説明が、skill `sessions` の `rounds.md` にだけある。
- **DC-331-9** 対象の 6 つの skill の本文に、出来事の説明（日付、spec・issue の番号で、いつ何が起きたかを書く文）と、一度きりの測定の結果がない。残す価値のあるものは `docs/07-記録/` にある。
- **DC-331-10** `subagents.md` に、作業ツリーとスロットの操作の手順（コマンド）がない。skill を指す。

### DS-331-3 サブエージェントの定義を、AGENTS.md と合わせる（D / P1）

- **DC-331-11** `reviewer` の description に、レビューの時点の一覧がない。
- **DC-331-12** `implementer` に、AGENTS.md の規則の転記がない。`.claude/agents/` のどの定義にも `model:` の指定がない。

### DS-331-4 検査と校閲を通す（D / P1）

- **DC-331-13** `node scripts/ci/doc-refs.mjs` と `scripts/ci/check-all.sh` が成功する。
- **DC-331-14** 変えたファイルの、変えた行の `ja-text` の warning が 0 である（`node scripts/ci/ja-text.mjs --changed`）。
- **DC-331-15** AGENTS.md の skill の表が、`.claude/skills/` の skill と一致する。
- **DC-331-16** 変えた日本語が、校閲（検出役、確認役の順）を通っている。本文は、日本語だけの文脈のサブエージェントが書いている。
- **DC-331-17** 作業ツリーで起動した `claude -p` で、変えた skill と定義が読まれる（skill `sessions` の「作業ツリーのセッションが読むもの」の確認の手順）。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 規則 | `docs/05-開発規則/review.md`、`writing.md`、`subagents.md`、`workflow.md` |
| 手順書 | `docs/06-手順書/setup.md`、`running-tests.md`（skill にだけある、人にも必要な手順を移すとき） |
| 記録 | `docs/07-記録/subagents-results.md` |
| skill | `.claude/skills/local-dev/SKILL.md`、`sessions/SKILL.md`、`sessions/rounds.md`、`new-screen/SKILL.md`、`review/SKILL.md`、`review/prompts.md`、`review/convergence.md`（削除）、`review/docs-review.md`、`docs/SKILL.md`、`zh-mixing/SKILL.md` |
| サブエージェントの定義 | `.claude/agents/implementer.md`、`reviewer.md` |
| エージェントの手順 | `AGENTS.md` |
| spec | `docs/08-spec/331-skills/spec.md`（アーカイブで `docs/08-spec/archive/1to10/331-skills/` に移す） |

## 非機能要件

- **DN-331-1** 消費者・運営者に見える振る舞いを変えない。コード（`scripts/` を含む）を変えない。
- **DN-331-2** 規則の意味を変えない。移すときは文の中身を保ち、置き場所と参照だけを変える。
- **DN-331-3** アーカイブした spec を変えない。
- **DN-331-4** 英語の原文のまま取り込んだ skill（writing-for-agents、retro、handoff）の本文を変えない。誤りの修正だけはしてよい。
- **DN-331-5** skill の変更は作業ツリーで行い、メインの作業ツリーの `.claude/skills/` を変えない。
- **DN-331-6** 全体の字数の圧縮はしない（spec 8）。この spec は、置き場所の整理と重複の削除だけを行う。

## 依存

| 対象 | 関係 |
|------|------|
| spec 6（#330） | 並行で進める。重なりうるファイルは ※1、順序と知らせ方は tasks の「並行する spec との重なり」 |
| spec 8 | 全体の字数の圧縮は spec 8 で行う。本 spec の後に始める |
| spec 309 | 確認 5 の案 A で、重要度と収束の移動を本 spec に回した |

※1：重なるもの。`docs/05-開発規則/review.md`（spec 6 は冒頭の「規則の一覧」の形、本 spec は重要度と収束の節の追加）。`docs/06-手順書/setup.md`（spec 6 は IDE の設定の節の追加、本 spec は local-dev との重複の解消）。`docs/06-手順書/running-tests.md`（spec 6 はテストの種類などの移動、本 spec は local-dev の E2E などとの重複の解消）。書き方の規則の節（spec 6 は見出しや置き場所を変える、本 spec は skill `new-screen`・`local-dev` がその節を指す参照）。

## Non-goals

| 項目 | 理由 |
|------|------|
| 書き方の規則の 4 つの文書（バックエンド、フロント、テスト、コメント）の整理 | spec 6 で扱う |
| 全体の字数の圧縮 | spec 8 で扱う（利用者の決定、2026-11-03） |
| 英語の原文のまま取り込んだ skill の本文 | 原文を保つ（棚卸しの決定 4） |
| 共有の部品の一覧を検査する script | 一覧の転記を削除するので、照合する対象がなくなる |
| skill の追加・削除・改名 | 目的に必要ない。行うことになったら AGENTS.md の skill の表を修正する |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。文書の本文は、日本語の要点から、日本語だけの文脈のサブエージェント（汎用）が書く。

並行する spec との重なり：

- spec 6（#330）と重なりうるファイルは、`docs/05-開発規則/review.md`、`docs/06-手順書/setup.md`、`running-tests.md`、skill `new-screen`・`local-dev` が指す書き方の規則の節である。spec 6 は、`review.md` では「規則の書き方」だけを、`setup.md` では新しい節の追加だけを行う（spec 6 の plan）。
- 順序：後にマージする spec が develop を取り込み、競合を解消する。本 spec が先なら、spec 6 が取り込む。本 spec が後なら、本 spec が取り込み、`node scripts/ci/doc-refs.mjs` で参照を確認する。
- 同じ節を書き換える前に、issue 担当のセッションに知らせる。

- [x] **T331-01-01** 重要度と収束の規則を `review.md` に移し、`convergence.md` を削除し、`prompts.md` の「重要度」の節を削除する。参照（skill `review` の SKILL.md、`workflow.md`、`subagents.md`）を、review.md の節を指すように修正する（DC-331-1、DC-331-2）。
  - 依存：なし
  - 範囲：`docs/05-開発規則/review.md`、`workflow.md`、`subagents.md`、`.claude/skills/review/SKILL.md`、`prompts.md`、`convergence.md`
  - 確認方法：移す前と後の文を並べて読む（`git show`）。`node scripts/ci/doc-refs.mjs`。`git grep convergence.md` が、アーカイブと評価の一覧の例文の外に残らない
- [x] **T331-01-02** 「日本語だけの文脈のサブエージェントに書かせる」の規則を `writing.md` の冒頭だけに置き、他の 5 か所を参照に替える（DC-331-3）。
  - 依存：なし
  - 範囲：`writing.md`、`subagents.md`、`workflow.md`、AGENTS.md、`.claude/skills/docs/SKILL.md`、`.claude/skills/sessions/SKILL.md`
  - 確認方法：`git grep -n "日本語だけの文脈"` の結果を読む（用語集の定義は残る）
- [x] **T331-01-03** 層 1・層 2 の分け方を `docs-review.md` の手順 4 だけに置く。skill `zh-mixing` の「層 1 と層 2 のどちらに加えるか」と冒頭の表は、参照に替える（DC-331-4）。
  - 依存：なし
  - 範囲：`.claude/skills/review/docs-review.md`、`.claude/skills/zh-mixing/SKILL.md`
  - 確認方法：`git grep -n "層 1"` の結果を読む
- [x] **T331-01-04** 手元の hook の設定を skill `local-dev` の「手元の hook の設定」にまとめ、zh-mixing の PostToolUse の項目をそこに移す。作業ツリーのセッションが読むものの表から、測定の出典・日付と、公式の文書との比較を取り出し、`docs/07-記録/subagents-results.md` に移す。`subagents.md` の作業ツリーとスロットの手順を skill への参照に替える（DC-331-5、DC-331-10、DC-331-9 の一部）。
  - 依存：T331-01-03（同じ zh-mixing の SKILL.md を変えるため）
  - 範囲：`.claude/skills/local-dev/SKILL.md`、`zh-mixing/SKILL.md`、`sessions/SKILL.md`、`docs/05-開発規則/subagents.md`、`docs/07-記録/subagents-results.md`、AGENTS.md
  - 確認方法：文書を読む。hook の JSON の項目が、移す前と同じ文字列である（`git show` と比べる）
- [x] **T331-01-05** skill `local-dev` のスロット・決済・E2E・データ・機能紹介の節から、`setup.md`・`running-tests.md`・`db/seed/local/README.md` と同じ内容を削除し、節を指す。skill にだけあり人にも必要な手順は手順書に移す（DC-331-6）。
  - 依存：T331-01-04（同じファイル）
  - 範囲：`.claude/skills/local-dev/SKILL.md`、`docs/06-手順書/setup.md`、`running-tests.md`
  - 確認方法：削除する文ごとに、手順書の同じ内容の場所を、作業の記録（このタスクの下）に書く。setup.md を変える前に、issue 担当のセッションに知らせる
  - 結果：スロットのポートの表、`slot.mjs` のコマンドの一覧、使えないもの、環境変数の注意、Stripe の起動のコマンド、E2E の構成の説明を削除し、setup.md の「スロット」「Stripe で動かす」と running-tests.md の「E2E」を指した。Windows で `node_modules` のある作業ツリーを削除する手順は、人にも必要なので setup.md の「スロット」に移した。seed の商品・注文・固定の ID の説明は `db/seed/local/README.md` にないので、skill に残した
- [x] **T331-01-06** skill `new-screen` の共有の部品の表を削除し、`frontend/shared/src/` の export を読んで探す手順に替える（DC-331-7）。
  - 依存：なし
  - 範囲：`.claude/skills/new-screen/SKILL.md`
  - 確認方法：文書を読む。手順のとおりに、表にあった部品（例：`formatYen`、`AmountsSummary`）が見つかることを確認する
- [x] **T331-01-07** skill `sessions` の「ステップの終わり」を rounds.md への参照に替える。対象の 6 つの skill の本文から、出来事の説明と一度きりの測定の結果を削除するか、`docs/07-記録/` に移す（DC-331-8、DC-331-9）。
  - 依存：T331-01-01〜06（同じファイルを変えるため、最後に行う）
  - 範囲：`.claude/skills/sessions/SKILL.md`、`rounds.md`、`review/SKILL.md`、他の対象の skill、`docs/07-記録/` の該当の文書
  - 確認方法：対象の skill を `20[0-9][0-9]-`、`spec [0-9]`、`#[0-9]` で検索し、残るものが理由の 1 行か出典だけであることを読む
- [x] **T331-01-08** `reviewer` の description から、レビューの時点の一覧を削除する。`implementer` から、AGENTS.md の規則の転記を削除する（DC-331-11、DC-331-12）。
  - 依存：なし
  - 範囲：`.claude/agents/reviewer.md`、`implementer.md`
  - 確認方法：文書を読む。`git grep -n "^model:" .claude/agents` が空
- [ ] **T331-01-09** 検査を通し、変えた skill と定義が読まれることを確認する（DC-331-13〜15、DC-331-17）。
  - 依存：T331-01-01〜08
  - 範囲：変えたファイル
  - 確認方法：`node scripts/ci/doc-refs.mjs`、`scripts/ci/check-all.sh`、`node scripts/ci/ja-text.mjs --changed`、AGENTS.md の skill の表と `ls .claude/skills` の照合、作業ツリーで起動した `claude -p` で skill と定義の名前が示されること
- [ ] **T331-01-10** spec の最後のレビュー。指摘を修正し、draft の pull request を作る（DC-331-16 の校閲を含む）。
  - 依存：T331-01-09
- [ ] **T331-01-11** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T331-01-10
- [ ] **T331-01-12** spec をアーカイブに移す。
  - 依存：T331-01-11
