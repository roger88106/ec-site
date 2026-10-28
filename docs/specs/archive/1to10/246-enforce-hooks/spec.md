# Spec 246：例外のない規則を hook と permission で強制する

ステータス：確定

## 1. 背景と目的

milestone「整理の段階」の第 1 段の spec の 1 つである。追跡の issue は [#246](https://github.com/roger88106/ec-site/issues/246) で、sub-issue はない。作業ブランチは `246-enforce-hooks` である。整理の spec なので、AC、API、データベースは変えない。規模は M で、2026-10-28 に利用者が確認した（第 6 節の決めたこと 1）。

「`develop` と `main` に直接 commit しない」「許可なしにマージしない」「push 済みの履歴を書き換えない」などは、文章の規則でエージェントに守らせている。一方、機械で強制している検査では、導入の後に、規則に反した commit やコマンドが残っていない。例は、git の hook による中国語の混入の検査と、Claude Code の hook による heredoc の `\` の検査である。そこで、例外のない規則を洗い出し、hook か permission で強制できるものを強制する。あわせて、Git に入れる Claude Code の設定（`.claude/settings.json`）と、環境ごとの設定（`.claude/settings.local.json`）の分け方を決める。

候補の一覧（R- の番号）は、issue 担当のセッションの作業用の記録（Git に入らない）である。本 spec は R-094 を扱う。必要な内容は本 spec に書いた。

### 本 spec の用語

| 用語 | 意味 |
|------|------|
| 拒否する | Claude Code の hook が `permissionDecision: "deny"` を返し、コマンドを実行させない。理由はエージェントに渡る |
| 確認を求める | Claude Code の hook か permission の規則が `ask` を返し、利用者に確認の画面を表示する。利用者が承認したときだけ実行される |
| 通す | hook が判定を返さず、いつもの permission の判定に任せる |
| 失敗にする | git の hook が 0 以外で終わり、commit を作らない |
| メインの作業ツリー | clone した元のディレクトリ（`git worktree add` で作った作業ツリーでないもの） |

### 今の強制

2026-10-28 に、規則の文書（constitution、AGENTS.md、`docs/rules/`、`docs/guides/`、skill）と、ローカルの設定を照合した。

| 規則 | 出典 | 今の強制 | 抜け道 |
|------|------|----------|--------|
| `develop` と `main` に直接 commit しない | AGENTS.md の Git の表 | GitHub の ruleset が、pull request を経ない push を拒否する | ローカルの commit はできる。メインの作業ツリーは複数のセッションが使うので、`develop` への commit が他のセッションの取り込みや作業ブランチの作成に混ざる |
| push 済みの履歴を書き換えない | AGENTS.md の Git の表 | ruleset が `develop` と `main` への force push を拒否する | 作業ブランチへの force push はできる |
| commit の前の検査を通す | [開発環境の起動](../../../../guides/setup.md) | `.git/hooks/` に置いた git の pre-commit hook と commit-msg hook | hook は clone ごとに手動で置く。置き忘れると、検査が実行されないまま commit できる。`--no-verify` で検査を省略できる |
| マージは利用者の許可を得てから | AGENTS.md の Git の表 | なし（ruleset の必須の承認数は 0） | `gh pr merge` を許可なしに実行できる |
| tag は利用者の許可を得てから | AGENTS.md の Git の表 | なし | `git tag`・`gh release create` を実行できる |
| ruleset を一時的に無効にするのは利用者の許可を得てから | [ブランチとリリース](../../../../guides/branches.md) 第 5 節 | なし | `gh api` で ruleset を変えられる |
| `/.local/`・秘密の値を Git に入れない | AGENTS.md の Git の表 | `.gitignore` の許可リスト | なし（強制済み） |
| `main` には `release-`・`hotfix-` からだけ入れる | [ブランチとリリース](../../../../guides/branches.md) 第 3.2 節 | CI の必須の検査 | なし（強制済み） |

- ローカルの Claude Code の hook は、全て Git に入らない `.claude/settings.local.json` にある。heredoc の `\` を拒否する PreToolUse、中国語の混入を調べる PostToolUse、`environment.md` を読み込む SessionStart の 3 つである。Git に入る `.claude/settings.json` はない。
- 「取り込みは merge で行い、rebase しない」（[開発の進め方](../../../../rules/workflow.md) 第 5.6 節）は、例外のある規則である。push していない commit の fixup に rebase を使うためである。この規則が守ろうとする push 済みの履歴は、force push を拒否すれば書き換えられないので、rebase は hook の対象にしない。
- 判断を伴う規則（issue から始める、規模の承認、文書が先、利用者が確認する所で止まる）は、hook で判定できない。
- release と hotfix の手順（[ブランチとリリース](../../../../guides/branches.md) 第 4、5 節）は、全て作業ブランチと pull request で行い、`develop`・`main` でローカルの commit を作らない。

### Claude Code の hook と permission の働き

2026-10-28 に、Claude Code の公式の文書（hooks、permissions、permission modes、settings、作業ツリー）で確認した。実際に動かしての確認は、T246-01-07 で行う。

- PreToolUse の hook が `ask` を返すと、auto mode でも確認の画面が表示される。`deny` を返すと、コマンドは実行されず、理由がエージェントに渡る。
- permission の `ask`・`deny` の規則も auto mode で適用される。ただし Bash の規則はコマンドの先頭の語と一致するかで判定し、`gh` をフルパスで呼ぶ形とは一致しない。そのため、Bash のコマンドは hook で全文を読んで判定する。
- hook と permission の規則は、`settings.json` と `settings.local.json` の両方のものが合わせて使われる。片方が他方を上書きしない。
- `settings.json` は、セッションの作業ディレクトリのものが読まれる。作業ツリーのセッションでは、その作業ツリーのブランチに commit されたものである。
- 文書は、Windows の作業ツリーのセッションでは、作業ツリーの `settings.local.json` を読むと書いている（メインの作業ツリーのものを読まない）。`settings.local.json` は Git に入らないので、新しい作業ツリーにはない。そのため、全ての作業ツリーで適用したい規則は `settings.json` に置く。
- `$CLAUDE_PROJECT_DIR` は、セッションを始めたディレクトリを指す。デスクトップアプリの作業ツリーのセッションでは、メインの作業ツリーを指す。
- `.claude/` の下の書き換えは、auto mode では利用者に確認せず、auto mode の分類器（操作を許すかを自動で判定する仕組み）が判定する。確認の画面を必ず表示するには、permission の `ask` の規則を書く。

### spec 245 との関係

spec 245（作業の流れとレビュー。issue #245、pull request [#253](https://github.com/roger88106/ec-site/pull/253)、2026-10-28 に `develop` にマージ済み。アーカイブは `docs/specs/archive/1to10/245-workflow-review/`）は、マージの規則を次のように変えた。

- 危険度が低く、機能の spec でない変更は、CI が全て通り、未解決の指摘のうち重要度が高と必須のものが 0 なら、issue 担当のセッションが利用者の許可なしにマージする（DC-245-26）。
- 危険度は `scripts/ci/risk.mjs` が、差分の経路と spec の「危険度：高」の行から判定する。pull request の本文の「## 危険度」の節に「判定：低」か「判定：高」を書く。本文の判定が `risk.mjs` の判定より低いと、CI の必須の検査「危険度の照合」が失敗する。Dependabot の pull request は、本文と比べず、経路と題で判定する。
- マージは `gh pr merge --match-head-commit <SHA>` で、承認を求めたときの head の commit を指定して行う（DC-245-24、25）。

マージの hook は、この例外（低の変更を利用者の許可なしにマージすること）を通し、`--match-head-commit` を求める必要がある。そのため、本 spec は spec 245 のマージの後に実装を始める（第 7 節）。spec 245 の規則は、本 spec の実装の進め方にも適用する。

## 2. スコープ

| # | 目的 | 対象 | 優先度 |
|---|------|------|--------|
| DS-246-1 | Git に入れる設定と環境ごとの設定の分け方を決め、文書にする | D | P1 |
| DS-246-2 | `develop`・`main` への直接の commit を失敗にする | D | P1 |
| DS-246-3 | push 済みの履歴の書き換えと、commit の前の検査の省略を拒否する | D | P1 |
| DS-246-4 | 利用者の許可が必要な操作（マージ、tag、ruleset の変更）で、確認を求める | D | P1 |

## 3. 目的と完了条件

完了条件は、第 6 節で選んだ案（全て推奨の案 A）で書いた。

### DS-246-1 Git に入れる設定と環境ごとの設定の分け方を決め、文書にする（D / P1）

- **DC-246-1** Git に入る `.claude/settings.json` があり、チームの例外のない規則を強制する hook と permission だけを書く。マシン固有のパスを書かない。
- **DC-246-2** `.gitignore` の `.claude/` の許可リストに `settings.json` があり、許可リストのコメントと [ブランチとリリース](../../../../guides/branches.md) 第 2 節の `.claude/` の箇条と、[constitution](../../../../constitution.md) の文書の地図の `.claude/` の行が、`settings.json` を含む形になっている。`settings.local.json` は Git に入らない。
- **DC-246-3** 分け方（チームの規則は `settings.json`、環境・利用者に固有のものは `settings.local.json`）と、hook の script の置き場所が、AGENTS.md に書いてある。skill `zh-mixing` の設定の手順（`settings.local.json` に書く）と食い違わない。
- **DC-246-4** hook の script は Git に入り、`node --test` のテストを持ち、`scripts/ci/check-all.sh` の script のテスト（`scripts/dev/**/*.test.mjs`）に含まれる。
- **DC-246-5** hook の script は、対象のリポジトリとブランチを、hook の入力の `cwd` と、コマンドの中の `cd`・`git -C` で決め、`$CLAUDE_PROJECT_DIR` を使わない。`settings.json` の hook のコマンドは、hook の作業ディレクトリの作業ツリーにある script を呼ぶ。そこに script がなければ（Bash のツールで前に Git の外や別の作業ツリーに `cd` したとき）、`$CLAUDE_PROJECT_DIR` の下の script を呼ぶ。どちらにもないとき、または script が判定の前に異常終了したとき（構文の誤り、import の失敗、node がない）は、`git` か `gh` を含むコマンドを拒否する。hook のコマンドは Git Bash で動く形で書く。Windows の hook は Git Bash で実行されるためである（T246-01-07 と段階 1 のレビュー）。
- **DC-246-6** `settings.json` の hook にタイムアウト（`timeout`）を書く。script の中の `gh` の呼び出しには、それより短いタイムアウトを付ける。

### DS-246-2 `develop`・`main` への直接の commit を失敗にする（D / P1）

- **DC-246-7** git の pre-commit hook（`scripts/dev/hooks/pre-commit`）が、今のブランチが `develop` か `main` なら、CI の検査の前に commit を失敗にし、作業ブランチを作る手順を表示する。人の commit も失敗にする。detached HEAD（rebase の途中を含む）は通す。
- **DC-246-8** 判定と表示のテストがある。既存の、一時リポジトリで実際に commit するテストは、作業ブランチで commit する形にして、既定のブランチ名によらず通る。
- **DC-246-9** [開発環境の起動](../../../../guides/setup.md) の hook の置き方が、DC-246-7 の判定を説明する。

### DS-246-3 push 済みの履歴の書き換えと、commit の前の検査の省略を拒否する（D / P1）

- **DC-246-10** Claude Code の PreToolUse の hook が、Bash のコマンドのうち次を拒否し、理由と代わりの手順をエージェントに返す。
  - `git push` の force（`-f`、`--force`、`--force-with-lease`、`--force-if-includes`、`+` で始まる refspec、リモートの参照を force で更新する `--mirror`）
  - `git push` によるリモートのブランチの削除（`--delete`・`-d`、`:` で始まる refspec が tag 以外を指すもの）。理由：消して push し直すと、push 済みの履歴を書き換えるのと同じ結果になり、開いた pull request も閉じる。マージした作業ブランチはリモートで自動で削除されるので、通常の作業では消さない（pull request #268 の審査）
  - `git commit` と `git push` の `--no-verify`（`git commit -n` を含む）
  - `git -c core.hooksPath=…` で hook の置き場所を替えるコマンド
- **DC-246-11** 同じ hook が、`git commit` のときに git の pre-commit hook が置かれていなければ拒否し、置き方（[開発環境の起動](../../../../guides/setup.md)）を示す。置かれているとは、`git rev-parse --git-path hooks/pre-commit` のファイルがあり、`scripts/dev/hooks/pre-commit` を含むことをいう。
- **DC-246-12** 判定は、`&&`・`;`・`|` でつないだコマンド、`cd` を前に付けたコマンド、`git -C <パス>` を含むコマンドにも適用される。テストが、拒否する例と通す例（通常の `git push`、`git commit --amend`、作業ツリーの中の `git commit` など）を持つ。

### DS-246-4 利用者の許可が必要な操作で、確認を求める（D / P1）

- **DC-246-13** 同じ hook が、マージのコマンドを次の順に判定する。`gh` をフルパスで呼ぶ形も対象にする。
  1. `gh api` によるマージ（REST の `…/pulls/<番号>/merge` と、GraphQL の mutation `mergePullRequest`）と、`gh pr merge --admin` は拒否し、`gh pr merge --match-head-commit` を使うよう返す。
  2. `gh pr merge` に `--match-head-commit` がなければ拒否する。自動のマージを解除するだけの `--disable-auto` は、マージでないので通す。
  3. `--auto` と、GraphQL の mutation `enablePullRequestAutoMerge` は確認を求める（予約のマージは、マージの時点の条件を判定できないため）。GraphQL の本文を読めないとき（ファイルから読む形、`$(…)` で作る形）も確認を求める。GraphQL の判定は、エンドポイントが `graphql`、`/graphql`、`https://<ホスト>/graphql` のどれでも行う。
  4. DC-246-14 の条件を全て満たせば通す。
  5. それ以外と、pull request の情報を読めないとき（`gh` の失敗、タイムアウト）は、確認を求める。
- **DC-246-14** 通す条件は、DC-245-26 の条件のうち、未解決の指摘が 0 であること（第 5 節）を除いたものである。
  - pull request が番号か URL で指定されている（番号のない形は、今のブランチの pull request を指すので、確認を求める）。
  - 向き先が `develop` である。
  - `--match-head-commit` の commit が、pull request の今の head の commit と一致する。
  - pull request が開いている。
  - CI の検査が全て終わっている（実行中のものがない）。
  - 終わった検査が全て、成功、skipped、neutral のいずれかである。neutral を含めるのは、GitHub の必須の検査も neutral を成功と同じに扱い、Dependabot の pull request では CodeQL が neutral を返すためである。
  - 危険度が低い。（a）Dependabot（作成者が `dependabot[bot]`）以外は、本文の「判定：」が「低」で、検査「危険度の照合」が成功している。照合の最も新しい実行は、本文を最後に編集した時刻の後に始まったものである（本文を低に書き換えた直後、書き換える前の本文で成功した照合で通さないため）。（b）Dependabot は、変更のファイルと題を `scripts/ci/risk.mjs` の判定の関数に渡した結果が低である。
  - 機能の spec でない。Dependabot 以外は、本文の `Closes` の issue が 1 つ以上あり、どれにもラベル「種類:機能」がない。
- **DC-246-15** 同じ hook が、tag の作成（`git tag <名前>`）、tag の push（`git push` の `--tags` と `refs/tags/`、tag の名前の push）、`gh release create`、GET 以外の `gh api` で Release か tag の参照を作る・変える形（パスが `releases` か `git/refs`・`git/tags` のもの）で確認を求める。tag の一覧を読むコマンド（`git tag`、`git tag -l`）は通す。
- **DC-246-16** 同じ hook が、ruleset（ブランチの保護を含む）を変える `gh api`（`-X`・`--method` が `GET` 以外で、パスが `rulesets` か `branches/<名前>/protection`）で確認を求める。`gh api graphql` は対象にしない。
- **DC-246-17** 次の 2 つで確認を求める。（a）自動マージを有効にする MCP のツール（`mcp__ccd_pr__set_auto_merge`）、（b）`.claude/settings.json`・`.claude/settings.local.json` の Edit・Write。どちらも `.claude/settings.json` の permission の `ask` で書く。
- **DC-246-18** DC-246-13〜16 の判定に、テストがある。`gh` の呼び出しはテストの中のスタブに差し替え、DC-246-14 の各条件が欠けた例、`Closes` がない例、実行中の検査がある例、タイムアウトの例を持つ。`risk.mjs` は差し替えず、実物を使う。
- **DC-246-19** AGENTS.md の Git の表の「マージ・tag」の行が、hook が確認を求めることと、hook が見ない条件（担当のセッション、未解決の指摘、`hotfix-` の後の `main` から `develop` への取り込み）は文章の規則であることを書いている。[ブランチとリリース](../../../../guides/branches.md) 第 5 節の ruleset の箇条が、確認の画面が表示されることを書いている。
- **DC-246-20** 同じ hook が、PowerShell のツールのコマンドのうち、`git` か `gh` を含み、操作の語（`push`、`commit`、`tag`、`merge`、`api`、`release`、`hooksPath`）を含むものを拒否し、Bash のツールで実行するよう返す。それ以外の PowerShell のコマンドは通す。テストが、拒否する例と通す例（`git status`、`git log`、`gh pr view`）を持つ（決めたこと 5）。

## 4. 守る制約

- **DN-246-1** 振る舞い、API、データベース、AC を変えない。変えるのは、hook の script とテスト、`.claude/settings.json`、`.gitignore`、文書だけである。
- **DN-246-2** 規則そのもの（何が許可を必要とするか）を変えない。本 spec は、今の規則（spec 245 のマージの後は、spec 245 の規則）を機械で強制するだけにする。マージには spec 245 の例外があるので、例外を通す条件を DC-246-14 で明示する。
- **DN-246-3** 拒否と確認の対象は、第 3 節に挙げた形だけにする。読むだけの `gh`、通常の `git push`、push していない commit の `--amend`・fixup は通す。
- **DN-246-4** `scripts/ci/check-all.sh`、変えた script のテスト、CI が通る。
- **DN-246-5** 依存のパッケージを加えない。hook は Node.js の標準の機能と、リポジトリの `scripts/ci/` の script（危険度の判定の `risk.mjs`、直接の実行の判定の `lib/is-main.mjs`）だけで書く。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| 判断を伴う規則（issue から始める、規模の承認、文書が先、確認の所で止まる）の強制 | hook で判定できない。文章の規則のまま残す |
| 担当のセッションの区別（低の変更をマージするのは issue 担当のセッション） | hook からセッションの役割を見分けられない。文章の規則のまま残す（DC-246-19） |
| 未解決の指摘のうち、重要度が高と必須のものが 0 であることの確認（DC-245-26 の条件の 1 つ） | レビューの記録は機械で読める形でない。文章の規則のまま残す（第 8 節の 1） |
| `hotfix-` の後の `main` から `develop` への取り込みで、利用者の許可を求めること（[ブランチとリリース](../../../../guides/branches.md) 第 5 節） | 取り込みの pull request は `<issue の番号>-<短い名前>` のブランチから `develop` に向かうので、ブランチの名前で見分けられない。文章の規則のまま残す（DC-246-19） |
| Bash のコマンド（`sed` など）による `.claude/settings*.json` の書き換えの確認 | Bash のコマンドの書き込み先を確実には判定できない。Edit・Write だけを対象にする（DC-246-17） |
| セッションのアーカイブの許可 | アプリの機能で、hook の対象の外である |
| ローカルの hook（heredoc の `\`、`environment.md` の読み込み、中国語の混入）を `settings.json` に移す | 環境・利用者に固有のもので、分け方（DC-246-3）のとおり `settings.local.json` に残す |
| `/.local/` の script の整理、skill 化 | #247 が扱う |

## 6. 決めたこと

2026-10-28 に、利用者が決めたこと 1〜4 の全てで案 A（推奨）を確認した。決めたこと 5 は、実装の途中で 2026-10-29 に確認した。Open questions はない。5 つは互いに独立している。

### 決めたこと 1：規模

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | M（spec.md の 1 つのファイル） | 決めた時点で DC が 19、タスクが 9 で、1 つのファイルに収まる | なし | なし |
| 案 B | S（issue の本文を spec にする） | 文書が最も少ない | マージの条件の決めごとと判定の例が多く、issue の本文では変更を追いにくい | なし（置き場所だけ変わる） |
| 案 C | L（spec・plan・tasks） | 設計の判断を plan に分けられる | API・データベースを変えず、モジュールにもまたがらないので、[開発の進め方](../../../../rules/workflow.md) 第 4.2 節の L の基準に当たらない | なし（置き場所だけ変わる） |

### 決めたこと 2：マージの許可をどう強制するか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | hook が、DC-246-14 を満たさないマージで確認を求める。今のとおり会話でも許可を求める（アーカイブの許可と一緒に）。許可を得たらコマンドを実行し、表示された確認の画面で利用者が承認する | 会話の許可（利用者の言葉）と、実行するコマンドの全文（pull request の番号と head の commit）の両方を利用者が見るので、番号の取り違えに気づける | 確認が 1 回増える | なし |
| 案 B | 案 A と同じ hook で、会話では許可を求めず、確認の画面を許可とする。アーカイブの許可だけを会話で求める | 確認が今と同じ回数で済む | 許可した利用者の言葉が会話に残らない（後片付けで issue 担当にアーカイブを頼むとき、添える言葉がない） | DC-246-19（AGENTS.md の表に「確認の画面が許可」と書く） |
| 案 C | hook がマージのコマンドを全て拒否し、利用者が GitHub の画面でマージする | 許可のないマージを確実に防げる | 低の変更も利用者がマージすることになり、spec 245 の DC-245-26 と合わない。マージの後の後片付けと CI の見届けを、エージェントが続けにくい | DC-246-13、14 を削り、拒否だけにする |

- 利用者が離れているとき（Remote Control）に確認の画面へ答えられるかは、未確認である。案 A・B のどちらでも、答えられないときはマージが実行されない。

### 決めたこと 3：直接の commit をどこで失敗にするか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | git の pre-commit hook で失敗にする | 人の commit も失敗にでき、エージェントの使うツールによらない。pre-commit hook を置いていない clone では、DC-246-11 が Claude Code の `git commit` を拒否するので、エージェントの commit も止まる | 人が pre-commit hook を置いていない clone では、人の commit は止まらない | なし |
| 案 B | Claude Code の hook で、`develop`・`main` での `git commit` を拒否する | git の hook に手を入れない | 人の commit は止まらない。案 A と DC-246-11 の組み合わせで、エージェントについては同じ効果がある | DC-246-7〜9 を Claude Code の hook の DC に置き換える |
| 案 C | 案 A と案 B の両方 | 二重に止まる | 同じ判定を 2 か所で持ち、修正するときに食い違うことがある | DC-246-10 に `develop`・`main` の commit を加える |

### 決めたこと 4：hook の script の置き場所

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | `scripts/dev/hooks/` に置く（例：`agent-guard.mjs` とテスト）。git の hook（`pre-commit`・`commit-msg`）と同じ階層になる | `check-all.sh` の script のテストの glob（`scripts/dev/**/*.test.mjs`）に既に含まれる。`scripts/dev/hooks/` は spec 245 の危険度が高の経路なので、hook を変える pull request は高と判定され、利用者がマージを許可する | git の hook と Claude Code の hook が同じ階層に並ぶので、ファイルの冒頭のコメントで区別する | なし |
| 案 B | `.claude/hooks/` を作って置く | Claude Code の設定と近い | `.gitignore` の許可リストへの追加が必要である。`check-all.sh` には `.claude/skills/` 用のテストの glob があるが、`.cjs` だけを含むので使えない。`.claude/hooks/` 用の glob を追加する必要がある。`.claude/` は高の経路なので、危険度は案 A と同じ | DC-246-2、4 に追加の許可リストと glob を加える |

### 決めたこと 5：Bash 以外のシェルのツール

T246-01-06 のレビューで、hook が Bash のツールだけを判定し、PowerShell のツールで実行したマージ・tag・force push を通すことが分かった。2026-10-29 に、利用者が案 A を確認した。

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | PowerShell のコマンドが `git` か `gh` を含み、hook が判定する操作の語（`push`、`commit`、`tag`、`merge`、`api`、`release`、`hooksPath`）を含めば拒否し、Bash で実行するよう返す。それ以外（`git status` など）は通す | 判定は Bash の 1 か所だけで、PowerShell の書き方を読まなくてよい。エージェントは Bash で実行するだけで済む | 読むだけの `gh api` なども拒否する | DC-246-20 を加える |
| 案 B | PowerShell のコマンドが `git` か `gh` を含めば、全て拒否する | 最も単純 | `git status` などの読むだけの操作も拒否する | DC-246-20 を加える |
| 案 C | PowerShell のコマンドが `git` か `gh` を含めば、確認を求める | エージェントを止めない | 確認の画面が増える | DC-246-20 を加える |
| 案 D | 扱わず、第 8 節に残す | 今回の作業が増えない | マージ・tag・ruleset の変更を確認なしに実行できる道が残る | なし |

## 7. tasks

### 順序と重なり

- 本 spec は spec 245 のマージの後に実装を始める。理由：マージの hook は、spec 245 の危険度の判定（`scripts/ci/risk.mjs`）、本文の「## 危険度」の節、`--match-head-commit` を使うため。spec の確定は、spec 245 のマージを待たない。
- 実装を始める前の照合（[開発の進め方](../../../../rules/workflow.md) 第 5.6 節）では、spec 245 がマージした内容を第 3 節と照合し、差があれば本 spec を先に修正する。照合するものは次のとおりである。
  - マージの条件（DC-245-24〜26）と、AGENTS.md の Git の表の行
  - `scripts/ci/risk.mjs` の、hook が使う関数の名前と引数（今のブランチでは `judgeRisk`、`judgeDependabotTitle`、`applyDependabotTitle`、`readBodyRisk`、`isDependabot`）
  - 必須の検査の名前「危険度の照合」と、ruleset に戻したか
- 照合の結果（2026-10-28、`develop` の af66e826）：設計の差はない。マージの条件は DC-245-26 と AGENTS.md の Git の表のとおりで、DC-246-14 の条件と合う。関数の名前は上のとおりで、`judgeRisk` は高の経路の一覧（`scripts/ci/risk.json` を `parseRiskConfig` で読んだもの）とファイルの中身を読む関数を引数に取る。Dependabot の pull request は spec.md を変えないので、中身を読む関数は常に null を返すものでよい。検査の名前は「危険度の照合」（`.github/workflows/risk.yml`）で、[ブランチとリリース](../../../../guides/branches.md) 第 3.1 節の必須の検査に載っている（ruleset に戻す作業は issue 担当のセッション）。`branches.md` 第 2 節には spec 245 が `.gitignore` の許可リストの箇条を加えたので、DC-246-2 はこの箇条を修正する。
- 本 spec が変えるファイルと、他の spec との重なり（2026-10-28）：

| まとまり | ファイル | 重なる spec |
|----------|----------|-------------|
| git の hook | `scripts/dev/lib/pre-commit.mjs`・`pre-commit-main.mjs`・`pre-commit.test.mjs` | なし（spec 249 はマージ済み） |
| Claude Code の hook | `scripts/dev/hooks/agent-guard.mjs`・`agent-guard.test.mjs`（決めたこと 4） | なし。spec 245 の `scripts/ci/risk.mjs` を読むだけで変えない |
| 設定 | `.claude/settings.json`（新規）、`.gitignore`（許可リストとコメント） | spec 247（`.claude/` と `/.local/` の扱い） |
| 文書 | `AGENTS.md`（Git の表、環境の節）、`docs/guides/setup.md`（hook の置き方）、`docs/guides/branches.md`（第 2 節の `.claude/` の箇条、第 5 節の ruleset の箇条）、skill `zh-mixing`（設定の手順の参照） | spec 245（AGENTS.md の Git の表と ※3、`branches.md` の第 2、3 節）、spec 247（AGENTS.md の環境の節） |

- spec 245 とは AGENTS.md と `branches.md` が重なる。本 spec は spec 245 のマージの後に `develop` を取り込んでから変えるので、衝突を避けられる。
- spec 247 は本 spec の後に始める見込みである。起票と spec 担当のセッションの起動は、issue 担当のセッションが行う。spec 247 が `.claude/settings.local.json` や `/.local/agent/` の hook を整理するときは、DC-246-3 の分け方に従う。DC-246-17 により、spec 247 の `.claude/settings*.json` の Edit・Write でも確認の画面が表示される。
- `settings.json` は作業ツリーのブランチに commit されたものが読まれる。本 spec のマージの後も、`develop` を取り込んでいない作業ブランチのセッションでは強制されない。後片付けで、issue 担当のセッションにこのことを伝える。

### 段階 1：hook と設定

- 前提：spec の確定、spec 245 のマージ、実装を始める前の照合
- 完了条件：DC-246-1〜20 を満たし、DN-246-1〜5 を守る。
- 参照：第 1 節、第 3 節、第 6 節、[テストの書き方](../../../../rules/test-writing.md)、[コメントの書き方](../../../../rules/comments.md)
- `[テスト]` のタスクは手元でレッドを確認し、続く実装のタスクと同じ commit に入れる。commit の本文に、レッドを確認したことを書く（[開発の進め方](../../../../rules/workflow.md) 第 2.1 節）。

- [x] **T246-01-01** [テスト] git の pre-commit hook の、ブランチの判定と表示のテストを書き、レッドを確認する（DC-246-7、8）。
  - 依存：なし
  - 範囲：`scripts/dev/lib/pre-commit.test.mjs`
  - 既存の、一時リポジトリで実際に commit するテストを、作業ブランチで commit する形にする。
  - 確認方法：`develop`・`main` で失敗する例がレッドになる。作業ブランチと detached HEAD で通る例を含む。
- [x] **T246-01-02** [リスク] git の pre-commit hook に、ブランチの判定を加える（DC-246-7、8）。
  - 依存：T246-01-01
  - 範囲：`scripts/dev/lib/pre-commit.mjs`・`pre-commit-main.mjs`
  - 理由（リスク）：誤ると全ての commit が失敗する。
  - 確認方法：T246-01-01 のテストが通る。この作業ブランチで commit できる。
- [x] **T246-01-03** [テスト] Claude Code の hook の、拒否の判定（DC-246-10〜12）のテストを書き、レッドを確認する。
  - 依存：なし
  - 範囲：`scripts/dev/hooks/agent-guard.test.mjs`（決めたこと 4）
  - 確認方法：DC-246-10 の各形（`+` の refspec、`git commit -n`、`core.hooksPath` を含む）、DC-246-11（作業ツリーの例を含む）、DC-246-12 の拒否する例と通す例がレッドになる。
- [x] **T246-01-04** [リスク] Claude Code の hook の、拒否の判定を実装する（DC-246-4、5、10〜12）。
  - 依存：T246-01-03
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`
  - 確認方法：T246-01-03 のテストが通る。`check-all.sh` の script のテストに `agent-guard.test.mjs` が含まれ、通る。
- [x] **T246-01-05** [テスト] Claude Code の hook の、確認の判定（DC-246-13〜16）のテストを書き、レッドを確認する（DC-246-18）。
  - 依存：T246-01-04
  - 範囲：`scripts/dev/hooks/agent-guard.test.mjs`
  - 確認方法：DC-246-18 の拒否と確認の例がレッドになる。通す例（フルパスの `gh` で条件を満たす例、`gh api graphql`）も持つ。
- [x] **T246-01-06** [リスク] Claude Code の hook の、確認の判定を実装する（DC-246-6、13〜16、18）。
  - 依存：T246-01-05
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`
  - 確認方法：T246-01-05 のテストが通る。`check-all.sh` が通る。
- [x] **T246-01-09** [テスト] Claude Code の hook の、PowerShell のツールの判定（DC-246-20）のテストを書き、レッドを確認する。T246-01-06 のレビューで加えた（決めたこと 5）。
  - 依存：T246-01-06
  - 範囲：`scripts/dev/hooks/agent-guard.test.mjs`
  - 確認方法：DC-246-20 の拒否する例がレッドになる。通す例も持つ。
- [x] **T246-01-10** [リスク] Claude Code の hook の、PowerShell のツールの判定を実装する（DC-246-20）。
  - 依存：T246-01-09
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`
  - 確認方法：T246-01-09 のテストが通る。`check-all.sh` が通る。
- [x] **T246-01-07** [リスク] `.claude/settings.json` を作り、hook と permission を書き、`.gitignore` の許可リストとコメントに加える（DC-246-1、2、5、6、17、20）。
  - 依存：T246-01-10
  - hook の matcher は、Bash と PowerShell のツールを対象にする（DC-246-20）。
  - 範囲：`.claude/settings.json`、`.gitignore`
  - hook のコマンドは、セッションの作業ディレクトリから作業ツリーの根を求めて script を呼ぶ形にする（初期案：`node "$(git rev-parse --show-toplevel)/scripts/dev/hooks/agent-guard.mjs"`）。実際に動く形を、この作業ツリーで動かして確認してから決める。決めた形は DC-246-5 のとおり。
  - 確認方法：`git check-ignore` で `settings.json` が無視されないことを確認する。hook の設定はセッションの開始のときに読まれるので、この作業ツリーで新しいセッションを起動し、次の表の各行を試す。結果を本 spec の第 9 節に記録する。

| DC | 試すコマンド・操作 | 期待 |
|----|--------------------|------|
| DC-246-10 | `git push --force --dry-run origin 246-enforce-hooks` | 拒否 |
| DC-246-10 | `git commit --no-verify --allow-empty -m 試し` | 拒否 |
| DC-246-11 | スクラッチパッドに作った hook のない一時リポジトリで `git commit --allow-empty -m 試し`（理由：作業ツリーの hook はメインの作業ツリーの `.git/hooks/` にあり、別名にすると他のセッションの commit にも影響するため） | 拒否 |
| DC-246-13 | `gh pr merge <閉じた pull request の番号>` | 拒否（`--match-head-commit` がない） |
| DC-246-13 | フルパスの `gh` で `pr merge <閉じた pull request の番号> --match-head-commit <SHA>` | 確認の画面（利用者が拒否する） |
| DC-246-15 | `git tag 試し` | 確認の画面（利用者が拒否する） |
| DC-246-16 | `gh api -X PUT repos/roger88106/ec-site/rulesets/<id>`（本文なし） | 確認の画面（利用者が拒否する） |
| DC-246-5 | 1 回目の Bash で Git の外（スクラッチパッド）に `cd` し、2 回目の Bash で `git -C <この作業ツリー> push --force --dry-run origin 246-enforce-hooks` | 拒否 |
| DC-246-17 | `.claude/settings.json` を Edit で書き換える | 確認の画面（利用者が拒否する） |
| DC-246-17 | メインの作業ツリーの `.claude/settings.local.json` を Edit で書き換える | 確認の画面（利用者が拒否する） |
| DC-246-20 | PowerShell のツールで `git tag 試し` | 拒否（Bash で実行するよう返す） |

- [x] **T246-01-08** 文書を更新する（DC-246-2、3、9、19）。
  - 依存：T246-01-07
  - 範囲：`AGENTS.md`（分け方と hook の置き場所、Git の表のマージ・tag の行）、`docs/guides/setup.md`（DC-246-9、hook を置かないと Claude Code の hook が commit を拒否すること）、`docs/guides/branches.md`（第 2 節の `.claude/` の箇条、第 5 節の ruleset の箇条）、skill `zh-mixing`（設定の手順が分け方に合うこと）
  - 確認方法：DC-246-2、3、9、19 の各文書の該当箇所を、skill `review` の文書のレビューで読む。
- [x] **T246-01-11** [リスク] 段階 1 の最後のレビューの指摘（必須 7 件。重複を除いて 6 件）を直す（DC-246-2、5、6、12、17、20）。
  - 依存：T246-01-08
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`・`agent-guard.test.mjs`、`scripts/dev/lib/pre-commit.test.mjs`（Git Bash を求める補助を共通にする）、`.claude/settings.json`、`docs/constitution.md`、`docs/guides/setup.md`
  - 修正するもの：
    - シェルの予約語（`then`、`do`、`{`、`!` など）の後の git・gh の判定
    - script の異常終了のときに git・gh を拒否する（DC-246-5）
    - settings.json の中身（matcher、timeout、ask）のテスト
    - テストで Git Bash を使う
    - constitution の `.claude/` の行（DC-246-2）
    - setup.md に pre-commit が止めない形（merge など。第 8 節の 3）を書く
  - 確認方法：加えたテストが修正の前に失敗し、修正の後に通る。`check-all.sh` が通る。

### 段階 2：完了

- 前提：段階 1
- 完了条件：spec の最後のレビューと、pull request の前のレビューを終え、spec をアーカイブに移している。

- [x] **T246-02-01** spec の最後のレビューを行う（skill `review`）。
  - 依存：段階 1
- [x] **T246-02-02** pull request の前のレビューを、draft の pull request で行う（skill `review`）。
  - 依存：T246-02-01
- [x] **T246-02-03** spec をアーカイブ（`docs/specs/archive/1to10/`）に移す。
  - 依存：T246-02-02
- [x] **T246-02-04** [リスク] pull request #268 の issue 担当のセッションの審査の指摘（高 4 件）を直す（DC-246-10、13〜15）。
  - 依存：T246-02-03
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`・`agent-guard.test.mjs`
  - 確認方法：加えたテストが修正の前に失敗し、修正の後に通る。`check-all.sh` が通る。
- [x] **T246-02-05** [リスク] pull request #268 の再審査の指摘（高 2 件、必須 1 件）を直す（DC-246-10、13）。
  - 依存：T246-02-04
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`・`agent-guard.test.mjs`
  - 確認方法：加えたテストが修正の前に失敗し、修正の後に通る。`check-all.sh` が通る。

## 8. 後回しにする作業と起票の案

作業の中で見つけたものは、この節に加える。

| # | 項目 | 起票の案 |
|---|------|----------|
| 1 | 未解決の指摘（重要度が高と必須）が 0 であることを、機械で読める形にして hook の条件に加える | spec 245 のレビューの記録の形が決まってから、issue 担当のセッションの候補の一覧に残す |
| 2 | `bash -c "…"` に入れたコマンドと、環境変数（`GIT_CONFIG_COUNT` など）や `git config` で hook の置き場所を替えてから commit する形も、hook で判定する。設定 `push.followTags` による tag の push、セッションで先に `export` した `GH_REPO`、エンドポイントを変数で書く `gh api $EP …`も、hook で判定する。どれも今は通す。なお、ローカルの tag を先に消してからの `git push --delete <名前>` は、ブランチの削除として拒否する（`refs/tags/<名前>` で指定すれば確認を求める）（T246-01-04、06） | エージェントが意図して使う形ではないので、見逃しが起きたときに起票する。issue 担当のセッションの候補の一覧に残す |
| 3 | `develop`・`main` の上の `git merge`・`git cherry-pick`・`git revert`・`git am` も失敗にする。git はこれらで pre-commit hook を実行しない。`develop` の上の `git pull`（merge）を止めないように、判定の条件を決める必要がある（段階 1 のレビュー） | 見逃しが起きたときに起票する。issue 担当のセッションの候補の一覧に残す |
| 4 | `$(…)`・バッククォート・括弧（サブシェル）の中の `cd` が、外の後ろのコマンドのディレクトリに持ち越される。拒否する側の誤りで、通すべきでないものを通す誤りではない（spec の最後のレビュー） | 見逃しや誤った拒否が起きたときに起票する。issue 担当のセッションの候補の一覧に残す |

## 9. 動作の確認の記録

T246-01-07 の確認の結果を、ここに書く。

2026-10-29、commit 17f4dcdb。利用者が不在だったので、確認の画面への回答は試せない。代わりに、この作業ツリーで非対話の新しいセッション（`claude -p`、permission mode は default、Bash・Edit・Read・PowerShell を許可）を起動して試した。非対話では、確認を求めた操作は確認の画面の代わりに拒否として記録され、hook の理由の文が残る。セッションの記録の permission の拒否の一覧で、各行を確認した。

| 確認の表の行 | 結果 |
|--------------|------|
| DC-246-10 force push | 拒否（理由の文が force の各形と代わりの手順を示す） |
| DC-246-10 `--no-verify` | 拒否 |
| DC-246-11 hook のない一時リポジトリの commit | 拒否（`.git/hooks/pre-commit` がないことと置き方を示す） |
| DC-246-13 `gh pr merge 253` | 拒否（`--match-head-commit` がない） |
| DC-246-13 フルパスの `gh` で `--match-head-commit 0000000` | 確認（満たさない条件として、開いていない、head の不一致、本文の危険度が高の 3 つを示す） |
| DC-246-15 `git tag 試し` | 確認 |
| DC-246-16 `gh api -X PUT …/rulesets/999999999` | 確認 |
| DC-246-17 作業ツリーの `.claude/settings.json` の Edit | 書き込みの許可を求めて止まった。ただし、非対話の default mode では規則がなくても許可を求めるので、ask の規則が効いたことは示さない。規則があることは、settings.json を読むテストで確認する |
| DC-246-17 メインの作業ツリーの `.claude/settings.local.json` の Edit | 未確認（試した Edit が「変更なし」のエラーで終わり、規則の判定まで進まなかった） |
| DC-246-20 PowerShell の `git tag 試し` | 拒否（Bash で実行するよう返す） |
| DC-246-5 Git の外に `cd` してからの `git -C … push --force` | 拒否。ただし、Claude Code が作業ツリーの外への `cd` を作業ツリーに戻したので、script を見つけられないときの経路は通っていない（その経路はテストで確認した） |
| 読むだけの `git status`・`git log` | 通った |

- 試した後、作業ツリーに変更はなく、tag も作られていない。
- 確認の画面の表示（利用者が答える形）と、DC-246-17 の 2 行は、利用者が対話のセッションで確認する。利用者が不在の間に pull request を作るので、T246-01-07 は機械で確認できた範囲で完了とし、残りを pull request の本文の「利用者の確認待ち」に書く。これは、2026-10-29 に issue 担当のセッションが伝えた利用者の特例（その場限り）のもとでの、spec 担当の判断である。
