# Spec 246：例外のない規則を hook と permission で強制する

ステータス：確定

## 1. 背景と目的

milestone「整理の段階」の第 1 段の spec の 1 つである。追跡の issue は [#246](https://github.com/roger88106/ec-site/issues/246) で、sub-issue はない。作業ブランチは `246-enforce-hooks` である。整理の spec なので、AC、API、データベースは変えない。規模は M で、2026-10-28 に利用者が確認した（第 6 節の決めたこと 1）。

「`develop` と `main` に直接 commit しない」「許可なしにマージしない」「push 済みの履歴を書き換えない」などは、文章の規則でエージェントに守らせている。一方、機械で強制している検査は、導入の後に規則に反した commit やコマンドが残っていない。例は、git の hook による中国語の混入の検査と、Claude Code の hook による heredoc の `\` の検査である。そこで、例外のない規則を洗い出し、hook か permission で強制できるものを強制する。あわせて、Git に入れる Claude Code の設定（`.claude/settings.json`）と、環境ごとの設定（`.claude/settings.local.json`）の分け方を決める。

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
| commit の前の検査を通す | [開発環境の起動](../../guides/setup.md) | `.git/hooks/` に置いた git の pre-commit hook と commit-msg hook | hook は clone ごとに手動で置く。置き忘れると、検査が実行されないまま commit できる。`--no-verify` で検査を省略できる |
| マージは利用者の許可を得てから | AGENTS.md の Git の表 | なし（ruleset の必須の承認数は 0） | `gh pr merge` を許可なしに実行できる |
| tag は利用者の許可を得てから | AGENTS.md の Git の表 | なし | `git tag`・`gh release create` を実行できる |
| ruleset を一時的に無効にするのは利用者の許可を得てから | [ブランチとリリース](../../guides/branches.md) 第 5 節 | なし | `gh api` で ruleset を変えられる |
| `/.local/`・秘密の値を Git に入れない | AGENTS.md の Git の表 | `.gitignore` の許可リスト | なし（強制済み） |
| `main` には `release-`・`hotfix-` からだけ入れる | [ブランチとリリース](../../guides/branches.md) 第 3.2 節 | CI の必須の検査 | なし（強制済み） |

- ローカルの Claude Code の hook は、全て Git に入らない `.claude/settings.local.json` にある。heredoc の `\` を拒否する PreToolUse、中国語の混入を調べる PostToolUse、`environment.md` を読み込む SessionStart の 3 つである。Git に入る `.claude/settings.json` はない。
- 「取り込みは merge で行い、rebase しない」（[開発の進め方](../../rules/workflow.md) 第 5.6 節）は、例外のない規則でない。push していない commit の fixup に rebase を使うためである。この規則が守ろうとする push 済みの履歴は、force push を拒否すれば書き換えられないので、rebase は hook の対象にしない。
- 判断を伴う規則（issue から始める、規模の承認、文書が先、利用者が確認する所で止まる）は、hook で判定できない。
- release と hotfix の手順（[ブランチとリリース](../../guides/branches.md) 第 4、5 節）は、全て作業ブランチと pull request で行い、`develop`・`main` でローカルの commit を作らない。

### Claude Code の hook と permission の働き

2026-10-28 に、Claude Code の公式の文書（hooks、permissions、permission modes、settings、作業ツリー）で確認した。実際に動かしての確認は、T246-01-07 で行う。

- PreToolUse の hook が `ask` を返すと、auto mode でも確認の画面が表示される。`deny` を返すと、コマンドは実行されず、理由がエージェントに渡る。
- permission の `ask`・`deny` の規則も auto mode で効く。ただし Bash の規則はコマンドの先頭の語と一致するかで判定し、`gh` をフルパスで呼ぶ形とは一致しない。そのため、Bash のコマンドは hook で全文を読んで判定する。
- hook と permission の規則は、`settings.json` と `settings.local.json` の両方のものが合わせて使われる。片方が他方を上書きしない。
- `settings.json` は、セッションの作業ディレクトリのものが読まれる。作業ツリーのセッションでは、その作業ツリーのブランチに commit されたものである。
- 文書は、Windows の作業ツリーのセッションでは、作業ツリーの `settings.local.json` を読むと書いている（メインの作業ツリーのものを読まない）。`settings.local.json` は Git に入らないので、新しい作業ツリーにはない。そのため、全ての作業ツリーで効かせたい規則は `settings.json` に置く。
- `$CLAUDE_PROJECT_DIR` は、セッションを始めたディレクトリを指す。デスクトップアプリの作業ツリーのセッションでは、メインの作業ツリーを指す。
- `.claude/` の下の書き換えは、auto mode では利用者に確認せず、auto mode の分類器（操作を許すかを自動で判定する仕組み）が判定する。確認の画面を必ず表示するには、permission の `ask` の規則を書く。

### spec 245 との関係

spec 245（作業の流れとレビュー。issue #245、pull request [#253](https://github.com/roger88106/ec-site/pull/253)、ブランチ `245-workflow-review`、マージ前）は、マージの規則を次のように変える。

- 危険度が低く、機能の spec でない変更は、CI が全て通り、未解決の指摘のうち重要度が高と必須のものが 0 なら、issue 担当のセッションが利用者の許可なしにマージする（DC-245-26）。
- 危険度は `scripts/ci/risk.mjs` が、差分の経路と spec の「危険度：高」の行から判定する。pull request の本文の「## 危険度」の節に「判定：低」か「判定：高」を書く。本文の判定が `risk.mjs` の判定より低いと、CI の必須の検査「危険度の照合」が失敗する。Dependabot の pull request は、本文と比べず、経路と題で判定する。
- マージは `gh pr merge --match-head-commit <SHA>` で、承認を求めたときの head の commit を指定して行う（DC-245-24、25）。

マージの hook は、この例外（低の変更を利用者の許可なしにマージすること）を通し、`--match-head-commit` を求める必要がある。そのため、本 spec は spec 245 のマージの後に実装を始める（第 7 節）。spec 245 の規則は、spec 245 がマージされるまで本 spec の進め方には当てない。

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
- **DC-246-2** `.gitignore` の `.claude/` の許可リストに `settings.json` があり、許可リストのコメントと [ブランチとリリース](../../guides/branches.md) 第 2 節の `.claude/` の箇条が、`settings.json` を含む形になっている。`settings.local.json` は Git に入らない。
- **DC-246-3** 分け方（チームの規則は `settings.json`、環境・利用者に固有のものは `settings.local.json`）と、hook の script の置き場所が、AGENTS.md に書いてある。skill `zh-mixing` の設定の手順（`settings.local.json` に書く）と食い違わない。
- **DC-246-4** hook の script は Git に入り、`node --test` のテストを持ち、`scripts/ci/check-all.sh` の script のテスト（`scripts/dev/**/*.test.mjs`）に含まれる。
- **DC-246-5** hook の script は、対象のリポジトリとブランチを、hook の入力の `cwd` と、コマンドの中の `cd`・`git -C` で決める。`$CLAUDE_PROJECT_DIR` に頼らない。`settings.json` の hook のコマンドは、セッションの作業ツリーにある script を呼ぶ。
- **DC-246-6** `settings.json` の hook に時間切れ（`timeout`）を書く。script の中の `gh` の呼び出しには、それより短い時間切れを付ける。

### DS-246-2 `develop`・`main` への直接の commit を失敗にする（D / P1）

- **DC-246-7** git の pre-commit hook（`scripts/dev/hooks/pre-commit`）が、今のブランチが `develop` か `main` なら、CI の検査の前に commit を失敗にし、作業ブランチを作る手順を表示する。人の commit も失敗にする。detached HEAD（rebase の途中を含む）は通す。
- **DC-246-8** 判定と表示のテストがある。既存の、一時リポジトリで実際に commit するテストは、作業ブランチで commit する形にして、既定のブランチ名によらず通る。
- **DC-246-9** [開発環境の起動](../../guides/setup.md) の hook の置き方が、DC-246-7 の判定を説明する。

### DS-246-3 push 済みの履歴の書き換えと、commit の前の検査の省略を拒否する（D / P1）

- **DC-246-10** Claude Code の PreToolUse の hook が、Bash のコマンドのうち次を拒否し、理由と代わりの手順をエージェントに返す。
  - `git push` の force（`-f`、`--force`、`--force-with-lease`、`--force-if-includes`、`+` で始まる refspec）
  - `git commit` と `git push` の `--no-verify`（`git commit -n` を含む）
  - `git -c core.hooksPath=…` で hook の置き場所を替えるコマンド
- **DC-246-11** 同じ hook が、`git commit` のときに git の pre-commit hook が置かれていなければ拒否し、置き方（[開発環境の起動](../../guides/setup.md)）を示す。置かれているとは、`git rev-parse --git-path hooks/pre-commit` のファイルがあり、`scripts/dev/hooks/pre-commit` を含むことをいう。
- **DC-246-12** 判定は、`&&`・`;`・`|` でつないだコマンド、`cd` を前に付けたコマンド、`git -C <パス>` を含むコマンドにも働く。テストが、拒否する例と通す例（通常の `git push`、`git commit --amend`、作業ツリーの中の `git commit` など）を持つ。

### DS-246-4 利用者の許可が必要な操作で、確認を求める（D / P1）

- **DC-246-13** 同じ hook が、マージのコマンドを次の順に判定する。`gh` をフルパスで呼ぶ形にも働く。
  1. `gh api` によるマージ（`…/pulls/<番号>/merge`）と、`gh pr merge --admin` は拒否し、`gh pr merge --match-head-commit` を使うよう返す。
  2. `gh pr merge` に `--match-head-commit` がなければ拒否する。
  3. `--auto` があれば確認を求める（予約のマージは、マージの時点の条件を判定できないため）。
  4. DC-246-14 の条件を全て満たせば通す。
  5. それ以外と、pull request の情報を読めないとき（`gh` の失敗、時間切れ）は、確認を求める。
- **DC-246-14** 通す条件は、DC-245-26 の条件のうち、未解決の指摘が 0 であること（第 5 節）を除いたものである。
  - pull request が番号か URL で指定されている（番号のない形は、今のブランチの pull request を指すので、確認を求める）。
  - 向き先が `develop` である。
  - `--match-head-commit` の commit が、pull request の今の head の commit と一致する。
  - CI の検査が全て終わり、全て成功（skipped を含む）している。実行中のものがあれば満たさない。
  - 危険度が低い。（a）Dependabot（作成者が `dependabot[bot]`）以外は、本文の「判定：」が「低」で、検査「危険度の照合」が成功している。（b）Dependabot は、変更のファイルと題を `scripts/ci/risk.mjs` の判定の関数に渡した結果が低である。
  - 機能の spec でない。Dependabot 以外は、本文の `Closes` の issue が 1 つ以上あり、どれにもラベル「種類:機能」がない。
- **DC-246-15** 同じ hook が、tag の作成（`git tag <名前>`）、tag の push（`git push` の `--tags` と `refs/tags/`、tag の名前の push）、`gh release create` で確認を求める。tag の一覧を読むコマンド（`git tag`、`git tag -l`）は通す。
- **DC-246-16** 同じ hook が、ruleset（ブランチの保護を含む）を変える `gh api`（`-X`・`--method` が `GET` 以外で、パスが `rulesets` か `branches/<名前>/protection`）で確認を求める。`gh api graphql` は対象にしない。
- **DC-246-17** `.claude/settings.json` の permission で、自動マージを有効にする MCP のツール（`mcp__ccd_pr__set_auto_merge`）と、`.claude/settings.json`・`.claude/settings.local.json` の Edit・Write で、確認を求める。
- **DC-246-18** DC-246-13〜16 の判定に、テストがある。`gh` の呼び出しはテストの中の偽物に差し替え、DC-246-14 の各条件が欠けた例、`Closes` がない例、実行中の検査がある例、時間切れの例を持つ。`risk.mjs` は差し替えず、実物を使う。
- **DC-246-19** AGENTS.md の Git の表の「マージ・tag」の行が、hook が確認を求めることと、hook が見ない条件（担当のセッション、未解決の指摘）は文章の規則であることを書いている。[ブランチとリリース](../../guides/branches.md) 第 5 節の ruleset の箇条が、確認の画面が表示されることを書いている。

## 4. 守る制約

- **DN-246-1** 振る舞い、API、データベース、AC を変えない。変えるのは、hook の script とテスト、`.claude/settings.json`、`.gitignore`、文書だけである。
- **DN-246-2** 規則そのもの（何が許可を必要とするか）を変えない。本 spec は、今の規則（spec 245 のマージの後は、spec 245 の規則）を機械で強制するだけにする。マージには spec 245 の例外があるので、例外を通す条件を DC-246-14 で明示する。
- **DN-246-3** 拒否と確認の対象は、第 3 節に挙げた形だけにする。読むだけの `gh`、通常の `git push`、push していない commit の `--amend`・fixup は通す。
- **DN-246-4** `scripts/ci/check-all.sh`、変えた script のテスト、CI が通る。
- **DN-246-5** 依存のパッケージを加えない。hook は Node.js の標準の機能と、リポジトリの `scripts/ci/risk.mjs` だけで書く。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| 判断を伴う規則（issue から始める、規模の承認、文書が先、確認の所で止まる）の強制 | hook で判定できない。文章の規則のまま残す |
| 担当のセッションの区別（低の変更をマージするのは issue 担当のセッション） | hook からセッションの役割を見分けられない。文章の規則のまま残す（DC-246-19） |
| 未解決の指摘のうち、重要度が高と必須のものが 0 であることの確認（DC-245-26 の条件の 1 つ） | レビューの記録は機械で読める形でない。文章の規則のまま残す（第 8 節の 1） |
| Bash のコマンド（`sed` など）による `.claude/settings*.json` の書き換えの確認 | Bash のコマンドの書き込み先を確実には判定できない。Edit・Write だけを対象にする（DC-246-17） |
| セッションのアーカイブの許可 | アプリの機能で、hook の対象の外である |
| ローカルの hook（heredoc の `\`、`environment.md` の読み込み、中国語の混入）を `settings.json` に移す | 環境・利用者に固有のもので、分け方（DC-246-3）のとおり `settings.local.json` に残す |
| `/.local/` の script の整理、skill 化 | #247 が扱う |

## 6. 決めたこと

2026-10-28 に、利用者が次の 4 つとも案 A（推奨）を確認した。Open questions はない。4 つは互いに独立している。

### 決めたこと 1：規模

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | M（spec.md の 1 つのファイル） | DC が 19、タスクが 9 で、1 つのファイルに収まる | なし | なし |
| 案 B | S（issue の本文を spec にする） | 文書が最も少ない | マージの条件の決めごとと判定の例が多く、issue の本文では変更を追いにくい | なし（置き場所だけ変わる） |
| 案 C | L（spec・plan・tasks） | 設計の判断を plan に分けられる | API・データベースを変えず、モジュールにもまたがらないので、[開発の進め方](../../rules/workflow.md) 第 4.2 節の L の基準に当たらない | なし（置き場所だけ変わる） |

### 決めたこと 2：マージの許可をどう強制するか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | hook が、DC-246-14 を満たさないマージで確認を求める。今のとおり会話でも許可を求める（アーカイブの許可と一緒に）。許可を得たらコマンドを実行し、表示された確認の画面で利用者が承認する | 会話の許可（利用者の言葉）と、実行するコマンドの全文（pull request の番号と head の commit）の両方を利用者が見るので、番号の取り違えに気づける | 確認が 1 回増える | なし |
| 案 B | 案 A と同じ hook で、会話では許可を求めず、確認の画面を許可とする。アーカイブの許可だけを会話で求める | 確認が今と同じ回数で済む | 許可した利用者の言葉が会話に残らない（後片付けで issue 担当にアーカイブを頼むとき、添える言葉がない） | DC-246-19（AGENTS.md の表に「確認の画面が許可」と書く） |
| 案 C | hook がマージのコマンドを全て拒否し、利用者が GitHub の画面でマージする | 最も強い | 低の変更も利用者がマージすることになり、spec 245 の DC-245-26 と合わない。マージの後の後片付けと CI の見届けを、エージェントが続けにくい | DC-246-13、14 を削り、拒否だけにする |

- 利用者が離れているとき（Remote Control）に確認の画面へ答えられるかは、未確認である。案 A・B のどちらでも、答えられないときはマージが進まない（実行されない側に倒れる）。

### 決めたこと 3：直接の commit をどこで失敗にするか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | git の pre-commit hook で失敗にする | 人の commit も失敗にでき、エージェントの使うツールによらない。pre-commit hook を置いていない clone では、DC-246-11 が Claude Code の `git commit` を拒否するので、エージェントの commit も止まる | 人が pre-commit hook を置いていない clone では、人の commit は止まらない | なし |
| 案 B | Claude Code の hook で、`develop`・`main` での `git commit` を拒否する | git の hook に手を入れない | 人の commit は止まらない。案 A と DC-246-11 の組み合わせで、エージェントについては同じ効果がある | DC-246-7〜9 を Claude Code の hook の DC に置き換える |
| 案 C | 案 A と案 B の両方 | 二重に止まる | 同じ判定を 2 か所で持ち、直すときに食い違いうる | DC-246-10 に `develop`・`main` の commit を加える |

### 決めたこと 4：hook の script の置き場所

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | `scripts/dev/hooks/` に置く（例：`agent-guard.mjs` とテスト）。git の hook（`pre-commit`・`commit-msg`）と同じ階層になる | `check-all.sh` の script のテストの glob（`scripts/dev/**/*.test.mjs`）に既に含まれる。`scripts/dev/hooks/` は spec 245 の危険度が高の経路なので、hook を変える pull request は高と判定され、利用者がマージを許可する | git の hook と Claude Code の hook が同じ階層に並ぶので、ファイルの冒頭のコメントで区別する | なし |
| 案 B | `.claude/hooks/` を作って置く | Claude Code の設定と近い | `.gitignore` の許可リストと `check-all.sh` のテストの glob（`.cjs` だけを含む `.claude/skills/` と別）に追加が必要である。`.claude/` は高の経路なので、危険度は案 A と同じ | DC-246-2、4 に追加の許可リストと glob を加える |

## 7. tasks

### 順序と重なり

- 本 spec は spec 245 のマージの後に実装を始める。理由：マージの hook は、spec 245 の危険度の判定（`scripts/ci/risk.mjs`）、本文の「## 危険度」の節、`--match-head-commit` を使うため。spec の確定は、spec 245 のマージを待たない。
- 実装を始める前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）では、spec 245 がマージした内容を第 3 節と照合し、差があれば本 spec を先に修正する。照合するものは次のとおりである。
  - マージの条件（DC-245-24〜26）と、AGENTS.md の Git の表の行
  - `scripts/ci/risk.mjs` の、hook が使う関数の名前と引数（今のブランチでは `judgeRisk`、`judgeDependabotTitle`、`applyDependabotTitle`、`readBodyRisk`、`isDependabot`）
  - 必須の検査の名前「危険度の照合」と、ruleset に戻したか
- 本 spec が変えるファイルと、他の spec との重なり（2026-10-28）：

| まとまり | ファイル | 重なる spec |
|----------|----------|-------------|
| git の hook | `scripts/dev/lib/pre-commit.mjs`・`pre-commit-main.mjs`・`pre-commit.test.mjs` | なし（spec 249 はマージ済み） |
| Claude Code の hook | `scripts/dev/hooks/agent-guard.mjs`・`agent-guard.test.mjs`（決めたこと 4） | なし。spec 245 の `scripts/ci/risk.mjs` を読むだけで変えない |
| 設定 | `.claude/settings.json`（新規）、`.gitignore`（許可リストとコメント） | spec 247（`.claude/` と `/.local/` の扱い） |
| 文書 | `AGENTS.md`（Git の表、環境の節）、`docs/guides/setup.md`（hook の置き方）、`docs/guides/branches.md`（第 2 節の `.claude/` の箇条、第 5 節の ruleset の箇条）、skill `zh-mixing`（設定の手順の参照） | spec 245（AGENTS.md の Git の表と ※3、`branches.md` の第 2、3 節）、spec 247（AGENTS.md の環境の節） |

- spec 245 とは AGENTS.md と `branches.md` が重なる。本 spec は spec 245 のマージの後に `develop` を取り込んでから変えるので、衝突を避けられる。
- spec 247 は本 spec の後に始める見込み（issue 担当のセッション）。spec 247 が `.claude/settings.local.json` や `/.local/agent/` の hook を整理するときは、DC-246-3 の分け方に従う。DC-246-17 により、spec 247 の `.claude/settings*.json` の Edit・Write でも確認の画面が表示される。
- `settings.json` は作業ツリーのブランチに commit されたものが読まれる。本 spec のマージの後も、`develop` を取り込んでいない作業ブランチのセッションでは強制されない。後片付けで、issue 担当のセッションにこのことを伝える。

### 段階 1：hook と設定

- 前提：spec の確定、spec 245 のマージ、実装を始める前の照合
- 完了条件：DC-246-1〜19 を満たし、DN-246-1〜5 を守る。
- 参照：第 1 節、第 3 節、第 6 節、[テストの書き方](../../rules/test-writing.md)、[コメントの書き方](../../rules/comments.md)
- `[テスト]` のタスクは手元でレッドを確認し、続く実装のタスクと同じ commit に入れる。commit の本文に、レッドを確認したことを書く（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。

- [ ] **T246-01-01** [テスト] git の pre-commit hook の、ブランチの判定と表示のテストを書き、レッドを確認する（DC-246-7、8）。
  - 依存：なし
  - 範囲：`scripts/dev/lib/pre-commit.test.mjs`
  - 既存の、一時リポジトリで実際に commit するテストを、作業ブランチで commit する形にする。
  - 確認方法：`develop`・`main` で失敗する例がレッドになる。作業ブランチと detached HEAD で通る例を含む。
- [ ] **T246-01-02** [リスク] git の pre-commit hook に、ブランチの判定を加える（DC-246-7、8）。
  - 依存：T246-01-01
  - 範囲：`scripts/dev/lib/pre-commit.mjs`・`pre-commit-main.mjs`
  - 理由（リスク）：誤ると全ての commit が失敗する。
  - 確認方法：T246-01-01 のテストが通る。この作業ブランチで commit できる。
- [ ] **T246-01-03** [テスト] Claude Code の hook の、拒否の判定（DC-246-10〜12）のテストを書き、レッドを確認する。
  - 依存：なし
  - 範囲：`scripts/dev/hooks/agent-guard.test.mjs`（決めたこと 4）
  - 確認方法：DC-246-10 の各形（`+` の refspec、`git commit -n`、`core.hooksPath` を含む）、DC-246-11（作業ツリーの例を含む）、DC-246-12 の拒否する例と通す例がレッドになる。
- [ ] **T246-01-04** [リスク] Claude Code の hook の、拒否の判定を実装する（DC-246-4、5、10〜12）。
  - 依存：T246-01-03
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`
  - 確認方法：T246-01-03 のテストが通る。`check-all.sh` の script のテストに `agent-guard.test.mjs` が含まれ、通る。
- [ ] **T246-01-05** [テスト] Claude Code の hook の、確認の判定（DC-246-13〜16）のテストを書き、レッドを確認する（DC-246-18）。
  - 依存：T246-01-04
  - 範囲：`scripts/dev/hooks/agent-guard.test.mjs`
  - 確認方法：DC-246-18 の例と、フルパスの `gh`、`gh api graphql` を通す例がレッドになる。
- [ ] **T246-01-06** [リスク] Claude Code の hook の、確認の判定を実装する（DC-246-6、13〜16、18）。
  - 依存：T246-01-05
  - 範囲：`scripts/dev/hooks/agent-guard.mjs`
  - 確認方法：T246-01-05 のテストが通る。`check-all.sh` が通る。
- [ ] **T246-01-07** [リスク] `.claude/settings.json` を作り、hook と permission を書き、`.gitignore` の許可リストとコメントに加える（DC-246-1、2、5、6、17）。
  - 依存：T246-01-06
  - 範囲：`.claude/settings.json`、`.gitignore`
  - hook のコマンドは、セッションの作業ディレクトリから作業ツリーの根を求めて script を呼ぶ形にする（例：`node "$(git rev-parse --show-toplevel)/scripts/dev/hooks/agent-guard.mjs"`）。実際に動く形を、この作業ツリーで動かして確認してから決める。
  - 確認方法：`git check-ignore` で `settings.json` が無視されないことを確認する。hook の設定はセッションの開始のときに読まれるので、この作業ツリーで新しいセッションを起動し、次の表の各行を試す。結果を本 spec の第 9 節に記録する。

| DC | 試すコマンド・操作 | 期待 |
|----|--------------------|------|
| DC-246-10 | `git push --force --dry-run origin 246-enforce-hooks` | 拒否 |
| DC-246-10 | `git commit --no-verify --allow-empty -m 試し` | 拒否 |
| DC-246-11 | pre-commit hook を一時的に別名にして `git commit --allow-empty -m 試し`（試した後に戻す） | 拒否 |
| DC-246-13 | `gh pr merge <閉じた pull request の番号>` | 拒否（`--match-head-commit` がない） |
| DC-246-13 | フルパスの `gh` で `pr merge <閉じた pull request の番号> --match-head-commit <SHA>` | 確認の画面（利用者が拒否する） |
| DC-246-15 | `git tag 試し` | 確認の画面（利用者が拒否する） |
| DC-246-16 | `gh api -X PUT repos/roger88106/ec-site/rulesets/<id>`（本文なし） | 確認の画面（利用者が拒否する） |
| DC-246-17 | `.claude/settings.json` を Edit で書き換える | 確認の画面（利用者が拒否する） |

- [ ] **T246-01-08** 文書を更新する（DC-246-2、3、9、19）。
  - 依存：T246-01-07
  - 範囲：`AGENTS.md`（分け方と hook の置き場所、Git の表のマージ・tag の行）、`docs/guides/setup.md`（DC-246-9、hook を置かないと Claude Code の hook が commit を拒否すること）、`docs/guides/branches.md`（第 2 節の `.claude/` の箇条、第 5 節の ruleset の箇条）、skill `zh-mixing`（設定の手順が分け方に合うこと）
  - 確認方法：DC-246-2、3、9、19 の各文書の該当箇所を、skill `review` の文書のレビューで読む。

### 段階 2：完了

- 前提：段階 1
- 完了条件：spec の最後のレビューと、pull request の前のレビューを終え、spec をアーカイブに移している。

- [ ] **T246-02-01** spec の最後のレビューと、pull request の前のレビューを行う（skill `review`）。
  - 依存：段階 1
- [ ] **T246-02-02** spec をアーカイブ（`docs/specs/archive/1to10/`）に移す。
  - 依存：T246-02-01

## 8. 後回しにする作業と起票の案

作業の中で見つけたものは、この節に加える。

| # | 項目 | 起票の案 |
|---|------|----------|
| 1 | 未解決の指摘（重要度が高と必須）が 0 であることを、機械で読める形にして hook の条件に加える | spec 245 のレビューの記録の形が決まってから、issue 担当のセッションの候補の一覧に残す |

## 9. 動作の確認の記録

T246-01-07 の確認の結果を、ここに書く。
