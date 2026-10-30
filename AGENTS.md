# AGENTS.md

AI エージェント（Claude Code など）がこのリポジトリで作業するときの手順。
プロジェクトのルールは [constitution](docs/constitution.md) とその細則にあり、本書はそれを守って作業する手順だけを書く。一致しないときは constitution を正とする。

## 最初に読むもの

1. [constitution](docs/constitution.md)：大綱と文書の地図
2. [開発の進め方](docs/05-開発規則/workflow.md)：SDD と TDD、起票と規模、利用者の確認と中断、完了の定義
3. 着手する段階の tasks と、冒頭の「参照」の文書（`docs/08-spec/<番号>-*/`。規模 L は `tasks/`、規模 M は spec.md の中の tasks、規模 S は issue）
4. コードを書く・レビューする前に、関わる書き方の文書（constitution の「コードの書き方」の一覧。バックエンド、フロント、テスト、コメント）
5. 文書（docs/、README、AGENTS.md、spec、plan、tasks）を書く・レビューする前に、[文書の書き方](docs/05-開発規則/writing.md)

マシン固有の環境と利用者個人の習慣（返答の言語など）は、`/.local/agent/environment.md` があれば始めに読んで従う。ないときは、本書と `docs/` だけで進める。

## skill

`.claude/skills/` にある。追加する・削除するときは、この表も修正する（skill `writing-for-agents`）。

| skill | いつ使うか |
|-------|------------|
| `local-dev` | スロット（作業ツリーごとの環境）の割り当てと解除、compose・バックエンド・フロントの起動、テストと E2E、commit の前の CI の検査、画面・DB・token の確認、壊しての確認（break-check）、手元の hook の設定 |
| `new-screen` | 店舗画面・管理画面に画面や操作を追加する、修正する |
| `handoff` | spec 担当のセッションを途中で別のセッションに替えるときに、引き継ぎの文書を作る（利用者が `/handoff` で呼ぶ） |
| `retro` | セッションを振り返り、環境の改善点を挙げる（利用者が `/retro` で呼ぶ） |
| `agent-retro` | 後片付けと、ステップ・フェーズの終わりの振り返り |
| `docs` | 文書（docs/、README、AGENTS.md）を書く・修正する・移す |
| `writing-for-agents` | skill や AGENTS.md を作る、修正する |
| `review` | 段階・spec の最後のレビュー、plan・tasks の照合、文書のレビュー、全体のレビュー（受け入れ確認） |
| `sessions` | issue 担当と spec 担当の運用（起動、開始の条件、資源の配分）と、セッションへのメッセージ・遠隔操作、Dependabot の pull request のマージ |
| `zh-mixing` | 中国語で指示する環境で、中国語の字・用語の混入を調べる、設定する |

skill の正本は `.claude/skills/` である。Claude Code、GitHub Copilot、Cursor は `.claude/skills/` を読み、Codex と Gemini CLI は読まない（`.agents/skills/` を読む）。Codex か Gemini CLI を使うときに、`.agents/skills/` を追加する。

## 作業の進め方

SDD と TDD の流れは [開発の進め方](docs/05-開発規則/workflow.md)。エージェントは次も守る。

- Dependabot の pull request を除き、全ての変更は issue から始める。規模（S・M・L）を提案し、利用者が承認してから進める（開発の進め方の「起票と規模」）。
- 急ぐ作業（不具合、マージの失敗など）は、すぐに起票する（開発の進め方の「起票」）。
- spec 担当のセッションは、コードを書かず（数行の文書の修正を除く）、実装を実装役に任せ、合否を判定する（[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「役割と作業の流れ」）。
- spec 担当のセッションは、最初のタスクの前に、最新の `develop` を取り込み、plan の前提、範囲、依存、規則、並行する spec との重なりを照合し、差があれば文書を先に直す（開発の進め方の「実装を始める前の照合」）。
- 作業の単位は tasks の 1 タスク。tasks にない作業は始めず、先に tasks への追加を提案する。ただし、実装の前の照合、`develop` の取り込み、pull request の作成、後片付けはタスクにしない（開発の進め方の「規模」、開発の進め方の「実装を始める前の照合」と開発の進め方の「マージの許可と後片付け」）。
- spec・plan・設計書・OpenAPI の誤りや不足に気づいたら、コードで回避せずに作業を止めて報告する。修正するときは文書が先。
- 文書で決まっていない設計上の判断は推測せず、選択肢と推奨を示して確認する。
- 利用者が確認する対象（開発の進め方の「利用者が確認する対象」）で止まり、確認を受けてから進む。段階とタスクの間では止まらない（開発の進め方の「待たないこと」）。作業を中断して質問するのは、開発の進め方の「中断して質問する条件」に当たるときだけ。
- 確認事項は、plan と tasks の確認でまとめて質問する（開発の進め方の「まとめて質問する」）。
- `[テスト]` のタスクは、失敗する（レッド）ことを確認してから実装に進む。テストを通すためにテストを弱めない。仕様と合わないと思ったら、変えずに報告する。
- 後回しにする作業と未決事項は、spec に記録する。spec の最後に、起票のまとめ方の案を pull request に付ける。利用者が承認したら、issue 担当のセッションが起票する（開発の進め方の「起票」）。

### 完了の基準

- tasks の「完了」の条件を満たし、対応する AC のテストが通り、既存のテストを壊していない（`./gradlew check` など）。
- 文書を更新するタスク（用語集への追加など）を終え、チェックボックスを `[x]` にする。
- 段階の最後のタスクの後は、段階の完了条件を確認して報告する。
- spec をアーカイブに移すタスクは tasks の最後にし、同じ pull request に含める。pull request は draft で先に作る（開発の進め方の「規模」）。
- 段階の最後のレビューで、変えたコードを [書き方の規則とレビュー](docs/05-開発規則/review.md) と照合する。

### 規則と指示の食い違い

- 利用者の指示が規則（constitution、AGENTS.md、skill など）と食い違うときは、従う前に伝え、その場限りの特例か、規則を修正するかを確認する。
- 作業の習慣は記憶でなく文書に書く。共有するものはリポジトリの文書に、マシンや個人に固有のものは `/.local/agent/environment.md` に書く。

### セッションの始めと終わり

- 始めに直近の CI を確認する（`gh run list -b develop -L 1`）。`develop` の CI が失敗していれば、すぐに起票し（開発の進め方の「起票」）、spec の作業の前に修正する。
- 1 つの spec は 1 つの spec 担当のセッションで進め、長くなったら文脈の圧縮で続ける。issue 担当のセッションとのやり取りは [エージェントの作業フロー](docs/05-開発規則/subagents.md)の「役割と作業の流れ」。
- 引き継ぎの文書は、spec 担当のセッションを替えるときに作り、始めに読む（置き場所と読み方は skill `handoff`）。
- マージの後は、開発の進め方の「マージの許可と後片付け」に従って後片付けを行う。

## モデルの区分

サブエージェントに使うモデルは、区分（最上位・高性能・汎用・軽量）で決める。使う場面と、サービスやモデルを替えるときの手順は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「任せる作業とモデル」と[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「モデルや設定を替えるとき」と、レビューの profile の表。そこでの effort の低・中・高は、順に `low`・`medium`・`high`。指定がなければ、下の表の既定の effort を使う。実際のモデルと effort はサービスごとに表で決め、替えるときは表と、`.claude/agents/` の定義の `model` を修正する。

今使う AI のサービスは Claude Code（Anthropic）。

| 区分 | 使う場面の要点 | モデル | モデルの ID | 既定の effort |
|------|----------------|--------|-------------|---------------|
| 最上位 | 調査役が原因を特定できないとき、利用者の指定（上限あり） | Claude Fable 5.1 | `claude-fable-5-1` | `high` |
| 高性能 | `[リスク]` のタスクの実装、原因の調査、重要な観点のレビュー | Claude Opus 5.5 | `claude-opus-5-5` | `medium` |
| 汎用 | 実装、判断が必要な作業、一般の観点のレビュー | Claude Sonnet 5.5 | `claude-sonnet-5-5` | `medium` |
| 軽量 | 機械的な作業（固定の分を含めて 10 万 token を超えないもの）※1 | Claude Haiku 5.5 | `claude-haiku-5-5` | `medium` |
| 組み込みのコードレビュー | pull request の前。level は毎回指定する（厚め high、標準 medium、節約 low） | Claude Code の `/code-review` | | |
| セキュリティのレビュー | 認証・支払・権限の経路を変える変更の、pull request の前 | Claude Code の `/security-review` | | |

最上位を使えるプランは Max で、Pro は使えない（2026-10-28）。出典と軽量の読める量は[実績](docs/07-記録/subagents-results.md)の「モデルの区分の出典と軽量の読める量」。

※1：Claude Haiku 5.5 は、1 回の要求が 10 万 token を超えると、入力・出力・キャッシュの全ての単価が 5 倍になる（Anthropic の料金の文書、2026-10-20 確認）。固定の分（定義なしで約 4 万、`.claude/agents/` の定義ありで約 0.6〜1.9 万 token）を含む。

## Git

| 項目 | ルール |
|------|--------|
| ブランチ | `develop` から作業ブランチ `<issue の番号>-<短い名前>` を切って作業し、pull request で `develop` に入れる。`develop` と `main` には直接コミットしない ※2 |
| メッセージ | 日本語。1 行目は `<対象>: <要約>`（例：`tax: 税率の一覧の置き換えを実装する`）、決定の理由は本文 |
| 単位 | いつコミットしてもよいが、1 つのコミットに複数のタスクを混ぜない（例外は [開発の進め方](docs/05-開発規則/workflow.md)の「循環」） |
| 修正 | push していないコミットへの小さな修正は、amend・fixup で入れる。push 済みは書き換えない |
| push・pull request | push と pull request の作成は、許可を求めない |
| マージ・tag | 利用者の許可が必要なのは、危険度が高い変更、機能の spec、`release-`・`hotfix-` の pull request、`hotfix-` の後の `main` から `develop` への取り込み、tag。操作のたびに求め、head の commit を指定してマージする ※3。それ以外は、CI が全て通り未解決の重要度「高」「必須」の指摘が 0 なら、issue 担当のセッションがマージする。マージの担当は [エージェントの作業フロー](docs/05-開発規則/subagents.md)の「役割と作業の流れ」。Claude Code の hook は、条件を満たさないマージ、tag、ruleset の変更で確認の画面を表示する（条件を満たす、危険度が低い変更のマージは通す）。担当のセッション、未解決の指摘、`hotfix-` の後の `main` から `develop` への取り込み（ブランチの名前で見分けられない）は hook が見ないので、文章の規則として守る |
| 入れないもの | 作業用の script、テストデータ、引き継ぎの文書、マシン固有のパス、個人の習慣は `/.local/` に置く（項目ごとの中身、使う者、保存の期間は [開発環境の起動](docs/06-手順書/setup.md)の「手元の置き場所」、`.gitignore` は [ブランチとリリース](docs/06-手順書/branches.md)の「切り方と pull request」） |

※2：ブランチの構成、作業ブランチ、保護、リリースは [ブランチとリリース](docs/06-手順書/branches.md)。

※3：マージは `gh pr merge --match-head-commit <SHA>` で行う。

## 環境

- JDK 21。統合テストと jOOQ のコード生成には Docker が必要。
- Gradle のプロジェクトは `backend/ecsite/` にある（`./gradlew` もそこで実行する）。リポジトリのルートと `backend/` には Gradle の設定がない。
- 起動・テストの手順は [開発環境の起動](docs/06-手順書/setup.md) と [テストの実行方法](docs/06-手順書/running-tests.md)。エージェントは skill `local-dev` を使う。
- Claude Code の hook と permission の書き先は、2 つに分ける。チームの例外のない規則は `.claude/settings.json`（Git に入る）、環境・利用者に固有のもの（zh-mixing の hook、利用者の SessionStart など）は `.claude/settings.local.json`（Git に入らない）である。hook の script は `scripts/dev/hooks/` に置く（Claude Code の `agent-guard.mjs` と、git の `pre-commit`・`commit-msg`、手元の hook の `session-start.mjs`・`backslash-guard.mjs`。手元の hook は `settings.local.json` に置き、設定は skill `local-dev` の「手元の hook の設定」）。作業ツリーのセッションが読む設定・定義・skill と、変更が反映される時点は、skill `sessions` のツールの補足の「作業ツリーのセッションが読むもの」の表。作業ツリーで起動した `claude -p` は、作業ツリーのブランチに commit された `settings.json` を読むので、`develop` を取り込んでいない作業ブランチでは適用されない。その `claude -p` では、動いているセッションに反映されるのは `settings.json` の hook の変更と skill の追加で、`settings.local.json` と `.claude/agents/` の定義の変更は、新しいセッションを起動してから読まれる。アプリのセッションは、作業ツリーで動くときも、メインの作業ツリーの（commit していない変更を含む）設定・定義・skill を読み、作業ツリーのものを読まない。作業ブランチの変更を実際に実行して確認するときは、その作業ツリーで `claude -p` を起動する。反映が確認できないときは、新しいセッションを起動する。
