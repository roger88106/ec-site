# Spec 247：`/.local/` と作業ツリーの置き場所を整理し、ツールを Git に移す

ステータス：確定
危険度：高

## 1. 背景と目的

milestone「整理の段階」の第 1 段の spec の 1 つである。追跡の issue は [#247](https://github.com/roger88106/ec-site/issues/247) で、sub-issue は [#22](https://github.com/roger88106/ec-site/issues/22)（skill を、Claude Code 以外のエージェントも読める置き場所に移す）である。作業ブランチは `247-local-tidy` である。整理の spec なので、AC、API、データベースは変えない。規模は M で、2026-10-29 に利用者が確認した（第 6 節の決めたこと 1）。

`/.local/` は Git に入らない手元の置き場所で、18 の項目と約 33MB になった。エージェントが使うもの、利用者が使うもの、両方が使うものが混ざり、終わった spec の記録や古い script も残る。`/.local/agent/environment.md`（約 1.2 万バイト）は、SessionStart の hook でセッションの始めと文脈の圧縮の後に毎回文脈に入る。そのうち、このマシンに固有でない内容（Windows や Claude Code に共通の落とし穴）と、経緯の記述（注意された日と回数）が多くを占める。`/.local/agent/` のツール（break-check、backslash-guard など）は Git に入らないので、他のマシンや clone し直した環境で使えず、テストもない。作業ツリーの置き場所は、issue 担当のセッションの備忘録にだけ書いてあり、Git の文書にない。

候補の一覧（R- の番号）に本 spec の項目はない。範囲は issue の本文による。

### 本 spec の用語

| 用語 | 意味 |
|------|------|
| 手元の置き場所 | Git に入らない `/.local/`。作業ツリーごとにある（スロットの `.local/slot` など）が、本 spec が扱うのはメインの作業ツリーのもの |
| メインの作業ツリー | clone した元のディレクトリ（`git worktree add` で作った作業ツリーでないもの） |
| ツール | `/.local/agent/` などにある、エージェントが使う script |

### 今の `/.local/`

2026-10-28 に、メインの作業ツリーの `/.local/` を調べた。

| 項目 | 大きさ | 中身 | 使う者 | 参照する Git の文書 |
|------|--------|------|--------|---------------------|
| `agent/` | 40K | `environment.md`、`session-start.mjs`、`backslash-guard.mjs`、`break-check.mjs`、`merge-deps.sh`、`usage-log.md`（spec 003 の使用量の記録） | エージェント | AGENTS.md、skill `local-dev`・`zh-mixing`、`docs/rules/resources.md` |
| `handoff/` | 32K | 引き継ぎの文書 3 つ | 両方 | skill `handoff`、用語集 |
| `retro/` | 4K | 振り返り | エージェント | skill `agent-retro`・`sessions` |
| `review/` | 12M | レビューの記録（約 90 のフォルダー。spec 7 から） | エージェント | skill `review`、`docs/rules/subagents.md` |
| `impl/` | 577K | 実装役の報告（spec 155、225） | エージェント | `docs/rules/subagents.md` |
| `memo/` | 304K | issue 担当のセッションの備忘録（第 3・6 弾、整理の段階） | エージェント | なし |
| `refactor/` | 181K | 整理の段階の目的、候補の一覧、基準 | エージェント | skill `sessions` |
| `issues/` | 152K | 起票に使った script と JSON | エージェント | なし |
| `pr/` | 16K | spec 157 の pull request の本文の下書き | エージェント | なし |
| `agent-runs/` | 68K | モデルの比較の記録（結果は `docs/rules/subagents-results.md` に移した） | エージェント | なし |
| `e2e-flow/` | 33K（`node_modules` を除く） | 全画面を撮る作業用の Playwright の設定 | 利用者 | なし |
| `masterdata/` | 20M | 日本郵便の郵便番号データ（zip と展開した CSV） | 両方 | `docs/design/modules/region.md` |
| `seed-images/` | 20K | 商品画像のプロンプトの一覧 | 利用者 | なし |
| `stripe.env` | 1K | Stripe の sandbox の鍵 | 両方 | `docs/guides/setup.md`・`environment.md`、skill `local-dev` |
| `WinNAT再起動.ps1` | 1K | WinNAT を再起動する script | 利用者 | なし（同じ手順が `docs/guides/setup.md` 第 7 節にある） |
| `ca.txt` | 1K | `check-all.sh` の出力の写し | なし | なし |
| `articles/` | 0 | 空 | なし | なし |
| `trash/` | 308K | spec 248 の後片付けで退けたもの、spec 107 の残り | なし | なし |

- 保存の期間の決まりがないので、終わった spec の記録（`review/`、`impl/`、`pr/`、`memo/batch3`・`batch6`）が残り続ける。`review/` だけで 12M ある。
- 使う者は名前から分からない。たとえば `e2e-flow/` と `seed-images/` は利用者が手動で使い、`masterdata/` は利用者が置いてエージェントが読む。

### 今の `environment.md`

2026-10-28 の時点のファイル（11,536 バイト）を節ごとに分けた。

| 節 | 内容 | このマシンと利用者に固有か |
|----|------|----------------------------|
| 利用者とのやり取り | 確認の所で止まる、返答の言語、push の許可、中国語の混入の検査 | 返答の言語と検査の設定は固有。確認の所と push は AGENTS.md・`docs/rules/workflow.md` の写し |
| 使用量の上限 | 並行の数の上限、使用率の読み方、圧縮の習慣 | 固有 |
| JDK・Node・gh | パス、Antigravity の CLI | 固有 |
| IntelliJ IDEA の MCP | 接続先、起動のパス | 固有 |
| バックエンドの起動と停止 | `bootRun` の出力の捨て方、8080 の止め方 | Windows に共通（skill `local-dev` に置ける） |
| ポートが使えないとき | WinNAT の再起動 | Windows に共通。`docs/guides/setup.md` 第 7 節と重なる |
| シェルと Git の落とし穴 | heredoc の `\`、break-check、`python` がない、`\| tail` の終了コード、hook で止まったときのステージ、作業ツリーの削除 | `python` がないことだけ固有。他は Windows の Git Bash か Claude Code に共通 |
| Stripe | アカウント、鍵の置き場所、ログの鍵 | アカウントは固有。鍵とログは `docs/guides/setup.md` 第 5 節と重なる |

- 多くの箇条が、決めた日と、注意された回数（「2 回」「3 回目」）を持つ。規則を守らせるための強調だが、毎回の文脈を増やす。

### 今のツール

| ツール | 働き | 呼び出し | テスト |
|------|------|----------|--------|
| `session-start.mjs` | SessionStart の hook。`environment.md` を文脈に入れる | `.claude/settings.local.json` | なし |
| `backslash-guard.mjs` | PreToolUse の hook。heredoc と `node -e` の中の `\` を拒否する | `.claude/settings.local.json` | なし |
| `break-check.mjs` | コードの 1 か所を書き換えてテストを実行し、失敗するかを確認して戻す | エージェントが手動で | なし。コマンドを cmd.exe で実行するので `./gradlew` が起動できない（`environment.md`） |
| `merge-deps.sh` | Dependabot の pull request を、最新にして CI を待ち、1 つずつマージする | issue 担当のセッションが手動で | なし |

中国語の混入の検査（`check-zh.mjs`）は、spec 249 が skill `zh-mixing` に置き換えて削除した。skill `zh-mixing` の SKILL.md に、置き換えの手順の段落（spec 249 第 8 節の 8）が残る。

### 作業ツリーの置き場所

- issue 担当のセッションの備忘録は、spec 担当のセッションが `git worktree add` で `.claude/worktrees/<番号>-<短い名前>` に作業ツリーを作ると決めている。デスクトップアプリが作る作業ツリーも `.claude/worktrees/` に置かれる。
- Git の文書には置き場所の決まりがない。`.gitignore` のコメント（`.claude/` の下の作業ツリーはツールが置き場所を決める）と、`docs/rules/backend-coding.md` の IntelliJ の注意だけが触れる。
- spec 246 の調べ（第 1 節）では、Claude Code の文書は、作業ツリーのセッションがその作業ツリーの `.claude/settings.local.json` を読むと書いている。`$CLAUDE_PROJECT_DIR` はメインの作業ツリーを指す。`git worktree add` で作業ツリーを作ってそこへ移ったセッションは、始めたディレクトリ（メインの作業ツリー）の設定を読み続ける。
- 2026-10-29 に本 spec の作業ツリー（デスクトップアプリが作ったもの）で動かして確認すると、文書と違った。この作業ツリーに `.claude/settings.local.json` はないが、メインの作業ツリーの `settings.local.json` の hook（中国語の混入の PostToolUse と SessionStart）が動いた。デスクトップアプリの作業ツリーのセッションは、メインの作業ツリーの `settings.local.json` を読む。

### skill の置き場所（#22）

2026-10-28 に、各エージェントの公式の文書で確認した。

| エージェント | プロジェクトの skill を読む場所 | 出典 |
|--------------|----------------------------------|------|
| Claude Code | `.claude/skills/`（`--add-dir` で足した場所の `.claude/skills/` を含む）。skill 単位のシンボリックリンクを辿る。`.agents/skills/` を読むという記述は見つからない | [Claude Code の skill](https://code.claude.com/docs/en/skills) |
| GitHub Copilot | `.github/skills/`、`.claude/skills/`、`.agents/skills/` | [About agent skills](https://docs.github.com/en/copilot/concepts/agents/about-agent-skills) |
| Cursor | `.agents/skills/`、`.cursor/skills/`、互換として `.claude/skills/` | [Cursor の skill](https://cursor.com/docs/context/skills) |
| OpenAI Codex | `.agents/skills/` | [Codex の skill](https://learn.chatgpt.com/docs/build-skills) |
| Gemini CLI | `.gemini/skills/`、`.agents/skills/` | [Gemini CLI の skill](https://geminicli.com/docs/cli/skills/) |

- 規格（[Agent Skills](https://agentskills.io/specification)）は置き場所を決めていない。実装の手引きは `.agents/skills/` を慣例として勧める。
- `.agents/skills/` を正本にすると、Claude Code のために `.claude/skills/<名前>` からのシンボリックリンクが必要になる。Windows の Git でリンクを checkout するには、開発者モードと `core.symlinks` の設定が必要になる。設定がない clone では、リンクがパスを書いた 1 行のファイルになり、skill が壊れる。

## 2. スコープ

| # | 目的 | 対象 | 優先度 |
|---|------|------|--------|
| DS-247-1 | `/.local/` の項目ごとの用途と保存の期間を決めて文書にし、要らないものを削除する | D | P1 |
| DS-247-2 | `environment.md` を縮め、固有でない内容を Git の文書に移す | D | P1 |
| DS-247-3 | ツールを Git に移し、テストを付ける | D | P1 |
| DS-247-4 | 作業ツリーの置き場所を決めて文書にする | D | P2 |
| DS-247-5 | skill の置き場所を決めて文書にする（#22） | D | P2 |

## 3. 目的と完了条件

完了条件は、第 6 節で選んだ案（全て推奨の案 A）で書いた。

### DS-247-1 `/.local/` の項目ごとの用途と保存の期間を決めて文書にし、要らないものを削除する（D / P1）

- **DC-247-1** [開発環境の起動](../../../../guides/setup.md) に `/.local/` の節があり、項目ごとに、中身、使う者（エージェント・利用者・両方）、保存の期間（いつ削除するか）を表で書いている。フォルダーの名前と場所は変えない（決めたこと 2）。
- **DC-247-2** 保存の期間は、少なくとも次を決めている。spec ごとの記録（`review/`、`impl/`、`pr/`）は spec のマージの後の後片付けで削除する。引き継ぎの文書は引き継いだ後に削除する。issue 担当の備忘録（`memo/`）は段・弾の終わりに削除する。振り返り（`retro/`）は skill `agent-retro` が読むので残す。
- **DC-247-3** [開発の進め方](../../../../rules/workflow.md) 第 5.7 節の後片付けの手順が、DC-247-2 の spec ごとの記録を削除することを含む。
- **DC-247-4** 削除するものの一覧（第 1 節の表のうち、使う者が「なし」のものと、終わった spec の記録）を利用者に示し、許可を得たものだけを削除している。作業中の他のセッションが使うもの（`memo/refactor1/`、`refactor/`、進行中の spec の `review/`）は削除しない。削除した結果を第 9 節に書く。
- **DC-247-5** AGENTS.md の Git の表の「入れないもの」の行が、DC-247-1 の節を指す。

### DS-247-2 `environment.md` を縮め、固有でない内容を Git の文書に移す（D / P1）

- **DC-247-6** `environment.md` が 5,000 バイト以下で、このマシンと利用者に固有の内容だけを書いている。固有の内容は、返答の言語、プラン・使用量・並行の数の上限、ツールのパス、IntelliJ の MCP、ないコマンド、アカウントである。経緯（日付と注意された回数）は、理由として必要な箇条だけに残す。
- **DC-247-7** 固有でない内容は、次の行き先に移っている。移す前にない記述だけを足し、重なる記述は既存の文書を正とする。
  - バックエンドの起動と停止、break-check の使い方、シェルと Git の落とし穴（`\| tail` の終了コード、hook で止まったときのステージ、`node_modules` のある作業ツリーの削除）→ skill `local-dev`
  - ポートが使えないとき → [開発環境の起動](../../../../guides/setup.md) 第 7 節（既存）
  - Stripe のログに出る鍵の扱い → [開発環境の起動](../../../../guides/setup.md) 第 5 節
  - 確認の所で止まる、push の許可 → AGENTS.md と [開発の進め方](../../../../rules/workflow.md)（既存。`environment.md` から削除する）
- **DC-247-8** `environment.md` を変える前に、issue 担当のセッションに変える内容を知らせている（`/.local/` は Git に入らず、issue 担当も修正するため）。

### DS-247-3 ツールを Git に移し、テストを付ける（D / P1）

- **DC-247-9** `session-start.mjs` と `backslash-guard.mjs` が `scripts/dev/hooks/` にあり（spec 246 の決めたこと 4 と同じ階層）、`node --test` のテストを持つ。hook の設定は `.claude/settings.local.json` に残し（spec 246 の DC-246-3 の分け方）、設定の手順が skill `local-dev` にある。
  - `session-start.mjs` は、`environment.md` をメインの作業ツリーの `/.local/agent/` から読む。メインの作業ツリーは、hook の入力の `cwd` で `git rev-parse --git-common-dir` を実行して求め、`$CLAUDE_PROJECT_DIR` に頼らない（spec 246 の DC-246-5 と同じ方針）。どの作業ツリーのセッションでも同じファイルを読むためである。
  - 移した script に、マシン固有のパスを書かない（DN-247-4）。
- **DC-247-10** `break-check.mjs` が `scripts/dev/break-check.mjs` にあり、テストを持ち、テストのコマンドをシェル（Git Bash）で実行して `./gradlew` も起動できる。Git Bash のパスは、spec 246 が置いた `scripts/dev/lib/git-bash.mjs` の `gitBash()` で求める（名前だけの `bash` は WSL の bash を指すことがあるため）。使い方が skill `local-dev` にある。
- **DC-247-11** `merge-deps.sh` が `scripts/dev/merge-deps.sh` にあり、使い方が skill `sessions` にある。script はマージを実行せず、最新にして CI を待ち、マージできる pull request ごとに `gh pr merge <番号> --merge --match-head-commit <SHA>` のコマンドを出力する。エージェントがそのコマンドを 1 つずつ実行する。理由：spec 246 の hook は Bash のコマンドの文字列を読むので、script の中の `gh pr merge` を判定できない。
- **DC-247-12** 本 spec のマージの後の後片付け（[開発の進め方](../../../../rules/workflow.md) 第 5.7 節）で、メインの作業ツリーの `.claude/settings.local.json` の hook を新しい置き場所の script に替え、`/.local/agent/` の古いツールを削除する。マージの前に替えると、新しい script がメインの作業ツリーになく、メインの作業ツリーを使う他のセッションの hook が動かなくなるためである。替える前に issue 担当に知らせる（DC-247-8 と同じ）。
  - 替えた後の 3 つの hook（backslash-guard、session-start、中国語の混入の PostToolUse）のコマンドは、spec 246 の `settings.json` と同じ書き方にする。script のパスを、まず `git rev-parse --show-toplevel` の作業ツリーから求め、そこになければ `$CLAUDE_PROJECT_DIR`（メインの作業ツリー）の下を使う（DC-246-5）。理由：メインの作業ツリーの `settings.local.json` は、全ての作業ツリーで使われる（第 1 節）。本 spec の script を含まないブランチでも、メインの作業ツリーの script で動かすためである。
- **DC-247-13** skill `zh-mixing` の SKILL.md から、「手元の古い検査（`.local/agent/check-zh.mjs`）からの置き換え」の段落を削除している。設定の手順にある Claude Code の hook の例が、DC-247-12 の書き方になっている。
- **DC-247-14** 移した script のテストが `scripts/ci/check-all.sh` の script のテスト（`scripts/dev/**/*.test.mjs`）に含まれ、通る。

### DS-247-4 作業ツリーの置き場所を決めて文書にする（D / P2）

- **DC-247-15** [エージェントの作業フロー](../../../../rules/subagents.md) の並行の単位の節（第 4 節）か skill `sessions` が、作業ツリーの置き場所を `.claude/worktrees/<issue の番号>-<短い名前>` と書いている。あわせて、手動で作る方法（`git worktree add`）と、デスクトップアプリが作る作業ツリーも同じ場所に置かれることを書いている。
- **DC-247-16** 同じ箇所が、作業ツリーのセッションで読まれる設定（`.claude/settings.local.json` と `$CLAUDE_PROJECT_DIR`）の違いを書いている。内容は、第 1 節の 2026-10-29 の確認にする（デスクトップアプリの作業ツリーのセッションは、メインの作業ツリーの `settings.local.json` を読む）。spec 246 の第 1 節の調べ（文書の記述）と違うことも書く。

### DS-247-5 skill の置き場所を決めて文書にする（#22）（D / P2）

- **DC-247-17** skill の正本は `.claude/skills/` のままにする（決めたこと 6）。AGENTS.md の skill の節が、読むエージェント（Claude Code、GitHub Copilot、Cursor）、読まないエージェント（Codex、Gemini CLI）、読まないエージェントを使うときに `.agents/skills/` を追加する方針を書いている。
- **DC-247-18** #22 を本 spec の pull request で閉じる（`Closes #22`）。

## 4. 守る制約

- **DN-247-1** 振る舞い、API、データベース、AC を変えない。変えるのは、文書、skill、`scripts/dev/` の script とテストだけである。
- **DN-247-2** `/.local/` の未追跡のファイルは、利用者の許可を得てから削除する。削除する前に中身の一覧を示す。
- **DN-247-3** `/.local/` と `.claude/settings.local.json` は Git に入らず、issue 担当のセッションと他のセッションも使う。変える前に issue 担当のセッションに知らせる。
- **DN-247-4** Git に入れる script に、マシン固有のパスと個人の習慣を書かない。
- **DN-247-5** `scripts/ci/check-all.sh`、移した script のテスト、CI が通る。依存のパッケージを加えない。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| `/.local/` のフォルダーの名前と場所を変える | 決めたこと 2。Git の文書と skill の参照が多く、並行するセッションが今のパスを使う |
| 手元の hook（heredoc の `\`、`environment.md` の読み込み、中国語の混入）を `settings.json` に移す | spec 246 の Non-goals と DC-246-3。環境・利用者に固有なので `settings.local.json` に残す |
| `.agents/skills/` を作る | 決めたこと 6。読むエージェントを使う予定がない |
| 中国語の混入の検査の置き換え | spec 249 が済ませた。残りの段落の削除だけを行う（DC-247-13） |
| `masterdata/` の郵便番号データの置き場所 | 地域の設計（`docs/design/modules/region.md`）の手順で、整理の対象でない |

## 6. 決めたこと

2026-10-29 に、利用者が次の 6 つとも案 A（推奨）を確認した。Open questions はない。6 つは互いに独立している。

### 決めたこと 1：規模

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | M（spec.md の 1 つのファイル） | DC が 18、タスクが 12 で、1 つのファイルに収まる | なし | なし |
| 案 B | S（issue の本文を spec にする） | 文書が最も少ない | 決めることが 6 つあり、issue の本文では変更を追いにくい | なし（置き場所だけ変わる） |
| 案 C | L（spec・plan・tasks） | 設計の判断を plan に分けられる | API・データベースを変えず、[開発の進め方](../../../../rules/workflow.md) 第 4.2 節の L の基準に当たらない | なし（置き場所だけ変わる） |

### 決めたこと 2：`/.local/` をどう分けるか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | フォルダーは移さず、項目ごとの使う者と保存の期間を表で文書にする。要らないものを削除する | Git の文書と skill の参照（`stripe.env`、`masterdata/`、`handoff/`、`retro/`、`review/`）を変えずに済む。並行するセッションが使うパスが変わらない | 使う者がフォルダーの名前から分からない（表を読む） | なし |
| 案 B | `agent/`・`user/`・`shared/` の 3 つの親フォルダーに移す | 名前で使う者が分かる | 参照の修正が 10 以上の文書と skill に及ぶ。移す間、他のセッションの参照が切れる | DC-247-1 を移す形に、DC-247-5 と参照の修正のタスクを追加する |

### 決めたこと 3：`environment.md` をどう縮めるか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | 固有でない内容を Git の文書に移し、経緯の記述を削る。毎回入るファイルは 1 つのまま | 他のマシンと他の人も、移した落とし穴を読める。ファイルが 1 つで迷わない | 経緯（注意された回数）を削ると、強調が弱まる。理由として必要なものは残す | なし |
| 案 B | 案 A に加え、固有の内容も「毎回入る要点」と「必要なときに読む詳細」の 2 つのファイルに分ける | 毎回の文脈が最も小さい | 詳細を読み忘れる。案 A で 5,000 バイト以下になれば、分ける効果が小さい | DC-247-6 に詳細のファイルを追加する |

### 決めたこと 4：ツールをどこに置くか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | script は `scripts/dev/`（hook は `scripts/dev/hooks/`）に置き、使い方は既存の skill（`local-dev`、`sessions`）に書く | テストが `check-all.sh` に既に含まれる。spec 246 の hook の置き場所と同じ階層。skill が増えない | `scripts/dev/hooks/` は危険度が高い経路なので、変える pull request は利用者がマージを許可する | なし |
| 案 B | 新しい skill（例：`agent-tools`）を作り、script をその中に置く | ツールが 1 か所にまとまる | `.claude/skills/` の下の script をテストする glob は `.cjs` だけなので、`.mjs` を追加する必要がある。skill の一覧が増え、説明が毎回の文脈に入る | DC-247-9〜11、14 の置き場所を変える |
| 案 C | 手元（`/.local/agent/`）に残し、文書だけ修正する | 変更が最も少ない | 他のマシンで使えず、テストもないまま | DC-247-9〜12、14 を削る |

### 決めたこと 5：作業ツリーの置き場所

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | `.claude/worktrees/` のまま、文書に書く | デスクトップアプリが作る作業ツリーと同じ場所になる。`.gitignore` で無視され、検索の対象にならない | IntelliJ の `reformat_file` が使えない（既存の注意のまま） | なし |
| 案 B | リポジトリの外（例：clone の隣のディレクトリ）に置く | リポジトリの中が軽くなる | アプリが作る作業ツリーと場所が分かれる。セッションに外のディレクトリの許可が必要になる | DC-247-15 の置き場所を変え、許可の手順を追加する |

### 決めたこと 6：skill の置き場所（#22）

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | `.claude/skills/` を正本のままにし、読めるエージェントと方針を AGENTS.md に書いて #22 を閉じる | Claude Code、GitHub Copilot、Cursor が読める。シンボリックリンクが要らない | Codex と Gemini CLI は読めない（使う予定がない） | なし |
| 案 B | `.agents/skills/` に移し、`.claude/skills/<名前>` をシンボリックリンクにする | 5 つのエージェントが読める | Windows で開発者モードと `core.symlinks` が要り、設定のない clone で skill が壊れる。リンクの検査が必要になる | DC-247-17 を移す形にし、リンクの検査と setup.md の手順を追加する |

## 7. tasks

### 順序と重なり

- 本 spec は spec 246 のマージの後に実装を始める。理由：spec 246 は次を変え、本 spec はそれらに合わせてツールを移すため。spec の確定は、spec 246 のマージを待たない。
  - `.claude/settings.json` の新設と、`settings.json` と `settings.local.json` の分け方（DC-246-3）
  - hook の script の置き場所 `scripts/dev/hooks/`（spec 246 の決めたこと 4）
  - AGENTS.md の環境の節、`docs/guides/setup.md`、skill `zh-mixing` の設定の手順
- 実装を始める前に、最新の `develop`（spec 246 のマージを含む）を作業ブランチに取り込む。取り込まないと、spec 246 の `settings.json` の hook がこの作業ツリーのセッションで適用されない（spec 246 第 7 節）。
- 実装を始める前の照合（[開発の進め方](../../../../rules/workflow.md) 第 5.6 節）では、spec 246 がマージした内容を第 3 節と照合し、差があれば本 spec を先に修正する。照合するものは、`scripts/dev/hooks/` の置き場所とテストの glob、`settings.json` と `settings.local.json` の分け方の文言、AGENTS.md の環境の節、作業ツリーの設定の読み方の記述、マージの hook が Bash のコマンドの文字列だけを読むこと（DC-247-11）である。
  - hook のコマンドの書き方：spec 246 は、hook の script のパスを `$CLAUDE_PROJECT_DIR` でなく `git rev-parse --show-toplevel` から求める（issue 担当のセッション、2026-10-29）。DC-247-12 で差し替える `settings.local.json` の 3 つの hook（backslash-guard、session-start、zh-mixing の PostToolUse）と、skill `zh-mixing` の設定の手順の例を、この書き方に合わせるかを照合する。`session-start.mjs` が読む `environment.md` の場所は、hook のコマンドの書き方と別に DC-247-9 で決める。
- 照合の結果（2026-10-29、`develop` の d57301f7 を取り込んだ後）。設計（第 6 節の決めたこと）は変わらない。
  - `scripts/dev/hooks/` の置き場所、テストの glob（`scripts/dev/**/*.test.mjs`）、AGENTS.md の環境の節の分け方、マージの hook が Bash のコマンドの文字列だけを読むことは、第 3 節と合う。
  - `settings.json` の PreToolUse（`agent-guard.mjs`、Bash と PowerShell）と、`settings.local.json` の backslash-guard（Bash）は、別の hook として両方が動く。判定するもの（git・gh のコマンドと、heredoc・`node -e` の `\`）は重ならない。
  - 作業ツリーのセッションが読む `settings.local.json` が、spec 246 の第 1 節の記述と違った（第 1 節）。DC-247-16 の内容を、動かして確認したものに修正した。
  - hook のコマンドの書き方は、spec 246 に合わせる（DC-247-12、13 に追加した）。
  - spec 246 が置いた `scripts/dev/lib/git-bash.mjs` を break-check で使う（DC-247-10 に追加した）。
  - 並行する spec はない（`docs/specs/` の下は本 spec だけ）。
- 実装の並行の数の上限は 3 に決まった（issue 担当のセッション、2026-10-29）。利用者の確認を受けたら、issue 担当のセッションに聞かずに始めてよい。
- 本 spec が変えるファイルと、他の spec との重なり（2026-10-28）：

| まとまり | ファイル | 重なる spec |
|----------|----------|-------------|
| ツール | `scripts/dev/hooks/session-start.mjs`・`backslash-guard.mjs` とテスト、`scripts/dev/break-check.mjs` とテスト、`scripts/dev/merge-deps.sh` | spec 246（`scripts/dev/hooks/agent-guard.mjs` を同じ階層に置く。ファイルは重ならない） |
| 文書 | `AGENTS.md`（skill の節、環境の節、Git の表の「入れないもの」）、`docs/guides/setup.md`（`/.local/` の節、Stripe とポートの節）、`docs/rules/workflow.md`（後片付けの節）、`docs/rules/subagents.md`（並行の単位の節） | spec 246（AGENTS.md の Git の表と環境の節、`setup.md` の hook の置き方） |
| skill | `local-dev`、`sessions`、`zh-mixing` | spec 246（`zh-mixing` の設定の手順） |
| 手元 | `/.local/agent/environment.md`、`.claude/settings.local.json`、`/.local/` の削除するもの | spec 246（DC-246-17 により、`settings*.json` の Edit・Write で確認の画面が出る）。issue 担当のセッション（`environment.md` を修正する） |

- 手元の設定の切り替え（DC-247-12）と `environment.md` の差し替え（DC-247-6）は、本 spec のマージの後の後片付けで行う。マージの前は、新しい script と移した記述がメインの作業ツリーになく、メインの作業ツリーを使う他のセッションの hook が動かなくなるためである。段階 1 では、差し替える内容を用意して確認するところまでを行う。

### 段階 1：ツールと文書

- 前提：spec の確定、spec 246 のマージ、`develop` の取り込み、実装を始める前の照合
- 完了条件：DC-247-1〜11、13〜17 を満たし、DC-247-6・12 の差し替えの内容を用意している。DN-247-1〜5 を守る。
- 参照：第 1 節、第 3 節、第 6 節、[テストの書き方](../../../../rules/test-writing.md)、[コメントの書き方](../../../../rules/comments.md)
- `[テスト]` のタスクは手元でレッドを確認し、続く実装のタスクと同じ commit に入れる。commit の本文に、レッドを確認したことを書く（[開発の進め方](../../../../rules/workflow.md) 第 2.1 節）。

- [x] **T247-01-01** [テスト] hook の script（`session-start`、`backslash-guard`）のテストを書き、レッドを確認する（DC-247-9）。
  - 依存：なし
  - 範囲：`scripts/dev/hooks/session-start.test.mjs`・`backslash-guard.test.mjs`
  - 確認方法：次の例がレッドになる。メインの作業ツリーと、`git worktree add` で作った作業ツリーの両方の `cwd` から、メインの作業ツリーの `/.local/agent/environment.md` を読む例。そのファイルがないときに何も出さない例。heredoc・`node -e` の `\` を拒否する例と、コマンドの他の箇所の `\` を通す例。
- [x] **T247-01-02** hook の script を `scripts/dev/hooks/` に移す（DC-247-9、14）。
  - 依存：T247-01-01
  - 範囲：`scripts/dev/hooks/session-start.mjs`・`backslash-guard.mjs`
  - 確認方法：T247-01-01 のテストが通る。`check-all.sh` の script のテストに含まれる。マシン固有のパスがない。
- [x] **T247-01-03** [テスト] break-check のテストを書き、レッドを確認する（DC-247-10）。
  - 依存：なし
  - 範囲：`scripts/dev/break-check.test.mjs`
  - 確認方法：当たらない・2 か所に当たる置き換えで止まる例、テストの後に元に戻る例、シェルのコマンド（`./` で始まるもの）を実行できる例がレッドになる。
- [x] **T247-01-04** break-check を `scripts/dev/` に移し、コマンドを Git Bash で実行する（DC-247-10、14）。
  - 依存：T247-01-03
  - 範囲：`scripts/dev/break-check.mjs`
  - 確認方法：T247-01-03 のテストが通る。`./gradlew` を含むコマンドで、Gradle の出力の行が出る。マシン固有のパスがない。
- [x] **T247-01-05** merge-deps を `scripts/dev/` に移し、マージのコマンドを出力する形にする（DC-247-11）。
  - 依存：なし
  - 範囲：`scripts/dev/merge-deps.sh`
  - 確認方法：script の中に `gh pr merge` の実行がなく、出力するコマンドが `--match-head-commit` を含む。`gh` のパスがマシン固有でない（PATH の `gh` を使い、なければ止まる）。`bash -n` が通る。
- [x] **T247-01-06** skill と文書を更新する（DC-247-1、2、3、5、7、9〜11、13、15〜17）。
  - 依存：T247-01-02、04、05
  - 範囲：`AGENTS.md`、`docs/guides/setup.md`、`docs/rules/workflow.md`、`docs/rules/subagents.md`、skill `local-dev`・`sessions`・`zh-mixing`
  - DC-247-3 の後片付けの手順に、DC-247-12 と DC-247-6 の差し替えは含めない（本 spec だけの作業のため、第 9 節に書く）。
  - 確認方法：各 DC の該当箇所を、skill `review` の文書のレビューで読む。`check-all.sh` の文書の検査が通る。
- [x] **T247-01-07** 手元の設定の差し替えを用意し、この作業ツリーで確認する（DC-247-12）。
  - 依存：T247-01-02
  - 範囲：第 9 節（差し替える `settings.local.json` の hook の行）
  - 確認方法：差し替える hook のコマンド（DC-247-12 の書き方）を、この作業ツリーで実行する。hook の入力の形の JSON を渡し、次を確認する。`environment.md` が出力される。heredoc の `\` が拒否される。中国語の混入が見つかる。結果を第 9 節に書く。メインの作業ツリーの設定と `/.local/agent/` は変えない。
- [x] **T247-01-08** 縮めた `environment.md` を用意する（DC-247-6〜8）。
  - 依存：T247-01-06
  - 範囲：`/.local/memo/refactor1/247-environment.md`（差し替える内容。Git に入らない）
  - 先に issue 担当のセッションに、変える内容を知らせる。
  - 確認方法：5,000 バイト以下。削除した箇条ごとに、行き先（Git の文書か削除）を第 9 節に書く。
- [x] **T247-01-09** `/.local/` の要らないものを削除する（DC-247-4）。
  - 依存：T247-01-06
  - 範囲：`/.local/`
  - 先に issue 担当のセッションに、削除するものの一覧を知らせる（DN-247-3）。そのうえで一覧を利用者に示し、許可を得たものだけを削除する。
  - 確認方法：削除したものと残したものを第 9 節に書く。

### 段階 2：完了

- 前提：段階 1
- 完了条件：spec の最後のレビューと、pull request の前のレビューを終え、spec をアーカイブに移している。pull request の本文に `Closes #22` がある（DC-247-18）。

- [x] **T247-02-01** spec の最後のレビューを行う（skill `review`）。
  - 依存：段階 1
  - 結果：

    | 時点 | 観点 | モデル | 回数 | token | 時間 | 高 | 必須 | 推奨 | 採用 |
    |------|------|--------|------|-------|------|----|------|------|------|
    | spec の最後 | spec との一致 | 高性能 | 1 | 81181 | 1m31s | 0 | 1 | 3 | 4 |
    | spec の最後 | ファイルをまたぐ影響・段階をまたぐ影響 | 汎用 | 1 | 69467 | 1m39s | 1 | 1 | 3 | 5 |
    | spec の最後 | 日本語の校閲 | 汎用 | 1 | 63301 | 0m44s | 0 | 0 | 9 | 9 |

    - 修正したもの：break-check が bash を起動できないときと対象のファイルが読めないときに終了コード 2 にする（高。テストを追加した）。setup.md 第 10 節の `agent/` に `zh-allow.txt` を加える（必須。2 つの観点で重複）。AGENTS.md の hook の script の一覧、hook のコマンドの「`settings.json` と同じ」の書き分け、作業ツリーのセッションが読む設定の主語（デスクトップアプリの作業ツリーに限る）、merge-deps の 2 つ目以降の失敗の扱い、第 9 節の T247-01-07 の記録、校閲の 9 件。
    - 残したもの：なし。
    - 機械の検査に移したもの：なし。break-check の終了コードはテストに加えた。他は文書の内容の照合で、検査に移せない。
- [x] **T247-02-02** pull request の前のレビューを、draft の pull request で行う（skill `review`）。本文に `Closes #247` と `Closes #22` を書く（DC-247-18）。
  - 依存：T247-02-01
  - 結果：組み込みのコードレビュー（high）で 8 件。6 件を修正した（70639bc5）：break-check の終了コード 126・127 とシグナル、backslash-guard の node の引数と heredoc の終わりの印、session-start の git の環境、merge-deps の待ちの上限。2 件は修正しない：merge-deps のまとめての処理（DC-247-11 の形。skill `sessions` にやり直しを書いた）、`--separate-git-dir` の clone（このリポジトリで使わない）。
- [x] **T247-02-03** spec をアーカイブ（`docs/specs/archive/1to10/`）に移す。
  - 依存：T247-02-02

マージの後の後片付けで、DC-247-6 と DC-247-12 の差し替えを、issue 担当のセッションに知らせてから行う。手順は第 9 節に書いたとおりにする。

## 8. 後回しにする作業と起票の案

作業の中で見つけたものは、この節に加える。

| # | 項目 | 起票の案 |
|---|------|----------|
| 1 | Codex か Gemini CLI を使うときに、`.agents/skills/` を追加する（決めたこと 6） | 使う予定ができたときに起票する。今は起票しない |

## 9. 作業の記録

T247-01-07〜09 の結果と、マージの後に行う差し替え（DC-247-6、12）の手順を、ここに書く。

### T247-01-07 手元の hook の差し替えの確認（2026-10-29）

差し替える 3 つの hook のコマンドは、skill `local-dev` の「手元の hook の設定」と skill `zh-mixing` の設定の手順の例のとおり（script のパスを `git rev-parse --show-toplevel` → `$CLAUDE_PROJECT_DIR` の順に探す）。本 spec の作業ツリーで、hook の入力の形の JSON を渡して実行した。

| hook | 入力 | 結果 |
|------|------|------|
| session-start | `cwd` が本 spec の作業ツリー | メインの作業ツリーの `environment.md` を前置きの文とともに出力した。終了コード 0 |
| session-start | シェルの作業ディレクトリが Git の外、入力の `cwd` はメインの作業ツリー、`$CLAUDE_PROJECT_DIR` が本 spec の作業ツリー | `$CLAUDE_PROJECT_DIR` の下の script で同じ出力になった |
| backslash-guard | heredoc の本文に `\` | 拒否した。終了コード 2、理由を標準エラー出力に出した |
| backslash-guard | `ls` | 通した。終了コード 0 |
| 中国語の混入の PostToolUse | 中国語の用語を含むファイル | 混入を見つけた。終了コード 2 |

メインの作業ツリーの設定と `/.local/agent/` は変えていない。

### T247-01-08 縮めた `environment.md`（2026-10-29）

差し替える内容は `/.local/memo/refactor1/247-environment.md`（4,985 バイト。今は 11,549 バイト）。削除した箇条と行き先：

| 削除した箇条 | 行き先 |
|--------------|--------|
| 確認の所で止まる、段階とタスクの間で止まらない | [開発の進め方](../../../../rules/workflow.md)（既存）。別の件だけに答えたときに聞く 1 文は残した |
| push と pull request の作成は許可を求めない | AGENTS.md の Git の表（既存） |
| バックエンドの起動と停止 | skill `local-dev`（停止の手順は既存、java が残る注意を追加した） |
| WinNAT の手順とスクリプト | [開発環境の起動](../../../../guides/setup.md) 第 7 節（既存）。`netstat` の中国語の表示と 1025 の注意は残した |
| heredoc の `\`、break-check、`tail` の終了コード、hook で止まったときのステージ、作業ツリーの削除、テストを同時に実行しない | skill `local-dev`（「シェルと Git の落とし穴」「壊しての確認」、スロットの節） |
| Stripe のログの鍵 | [開発環境の起動](../../../../guides/setup.md) 第 5 節 |
| 各箇条の日付と注意された回数、試した経緯 | 削除（返答の言語の回数だけ理由として残した） |

### T247-01-09 `/.local/` の削除（2026-10-29）

issue 担当のセッションに一覧を知らせ、利用者の許可を得て削除した。全て削除できた。削除できないものは `trash/` に移す指示だったが、移すものがなかったので `trash/` も削除した。

| 区分 | 項目 |
|------|------|
| 削除した | `review/` のうち本 spec のもの以外（約 13MB、マージ済みの spec の記録）、`impl/155`・`impl/225`、`pr/157-*`、`handoff/` の 3 つ（issue 担当の古い引き継ぎ）、`issues/`、`ca.txt`、空の `articles/`・`trash/`。空になった `impl/`・`pr/`・`handoff/` も削除した |
| 残した | `agent/`、`memo/`（issue 担当の判断に任せる）、`retro/`、`refactor/`、`agent-runs/`（結果を [調べた仕組みと実績](../../../../rules/subagents-results.md) に移したか確認できない）、`masterdata/`、`seed-images/`、`stripe.env`、`e2e-flow/`、`WinNAT再起動.ps1`、`review/202610290000-247-spec` |

[開発環境の起動](../../../../guides/setup.md) 第 10 節の保存の期間のうち、DC-247-2 で決めていないもの（`refactor/`、`issues/`、`agent-runs/`、`e2e-flow/`、`WinNAT再起動.ps1`）も、利用者が同じ確認で承認した。

### マージの後の差し替えの手順（DC-247-6、12）

issue 担当のセッションに知らせてから、メインの作業ツリーで行う。`settings.local.json` の Edit は確認の画面が出る（DC-246-17）。

1. メインの作業ツリーの `develop` に本 spec のマージが入っていることを確認する。
2. `.claude/settings.local.json` の 3 つの hook のコマンドを、skill `local-dev` と `zh-mixing` の例の形に替える。
3. `/.local/agent/environment.md` を `/.local/memo/refactor1/247-environment.md` で置き換える。マージまでに issue 担当のセッションが `environment.md` を変えていれば、その変更を先に写す。
4. `/.local/agent/` の `session-start.mjs`、`backslash-guard.mjs`、`break-check.mjs`、`merge-deps.sh` を削除する。
5. 新しいセッションを起動し、始めに `environment.md` が文脈に入ることを確認する。
