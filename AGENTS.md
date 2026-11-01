# AGENTS.md

AI エージェント（Claude Code など）がこのリポジトリで作業するときの手順。
プロジェクトのルールは [constitution](docs/constitution.md) とその細則にあり、本書はそれを守って作業する手順だけを書く。一致しないときは constitution を正とする。

## 最初に読むもの

1. [constitution](docs/constitution.md)：大綱と文書の地図
2. [開発の進め方](docs/05-開発規則/workflow.md)：SDD と TDD、起票と規模、利用者の確認と中断、完了の定義
3. 着手する段階の tasks と、冒頭の「参照」の文書（`docs/08-spec/<番号>-*/`。規模 L は `tasks/`、規模 M は spec.md の中の tasks、規模 S は issue）
4. コードを書く・レビューする前に、関わる書き方の文書（constitution の「コードの書き方」の一覧。バックエンド、フロント、テスト、コメント）
5. 文書（docs/、README、AGENTS.md、spec、plan、tasks、issue の本文とコメント）を書く・レビューする前に、[文書の書き方](docs/05-開発規則/writing.md)。書く前に、skill `docs` の「進め方」も読む（本文は、日本語の要点から、日本語だけの文脈のサブエージェントに書かせる）

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

SDD と TDD の流れは [開発の進め方](docs/05-開発規則/workflow.md)、役割とサブエージェントへの任せ方は [エージェントの作業フロー](docs/05-開発規則/subagents.md)。場面ごとに、次の節を読む。

| 場面 | 読む節 |
|------|--------|
| 起票と規模の提案 | [開発の進め方](docs/05-開発規則/workflow.md)の「起票」と「規模」 |
| 急ぐ作業（不具合、マージの失敗など） | [開発の進め方](docs/05-開発規則/workflow.md)の「起票」 |
| spec 担当の役割（コードを書かない、合否の判定） | [エージェントの作業フロー](docs/05-開発規則/subagents.md)の「役割と作業の流れ」 |
| 実装を始める前 | [開発の進め方](docs/05-開発規則/workflow.md)の「実装を始める前の照合」 |
| 利用者の確認、止まる所、質問する条件 | [開発の進め方](docs/05-開発規則/workflow.md)の「利用者の確認と中断」 |
| 後回しの作業と未決事項 | [開発の進め方](docs/05-開発規則/workflow.md)の「起票」 |
| 完了の条件、アーカイブ、pull request | [開発の進め方](docs/05-開発規則/workflow.md)の「規模」と「完了の定義」 |
| マージと後片付け | [開発の進め方](docs/05-開発規則/workflow.md)の「マージの許可と後片付け」 |

次は、本書にだけある規則である。

- 作業の単位は tasks の 1 タスク。tasks にない作業は始めず、先に tasks への追加を提案する。ただし、実装の前の照合、`develop` の取り込み、pull request の作成、後片付けはタスクにしない。
- spec・plan・設計書・OpenAPI の誤りや不足に気づいたら、コードで回避せずに作業を止めて報告する。修正するときは文書が先。
- 文書で決まっていない設計上の判断は推測せず、選択肢と推奨を示して確認する。
- `[テスト]` のタスクは、失敗する（レッド）ことを確認してから実装に進む。テストを通すためにテストを弱めない。仕様と合わないと思ったら、変えずに報告する。

### 完了の基準

- tasks の「完了」の条件を満たし、対応する AC のテストが通り、既存のテストを壊していない（`./gradlew check` など）。文書を更新するタスクを終え、チェックボックスを `[x]` にする。
- 段階の最後のタスクの後は、段階の完了条件を確認して報告する。段階の最後のレビューで、変えたコードを [書き方の規則とレビュー](docs/05-開発規則/review.md) と照合する。
### 規則と指示の食い違い

- 利用者の指示が規則（constitution、AGENTS.md、skill など）と食い違うときは、従う前に伝え、その場限りの特例か、規則を修正するかを確認する。
- 作業の習慣は記憶でなく文書に書く。共有するものはリポジトリの文書に、マシンや個人に固有のものは `/.local/agent/environment.md` に書く。

### セッションの始めと終わり

- 始めに直近の CI を確認する（`gh run list -b develop -L 1`）。`develop` の CI が失敗していれば、すぐに起票し（開発の進め方の「起票」）、spec の作業の前に修正する。
- 1 つの spec は 1 つの spec 担当のセッションで進め、長くなったら文脈の圧縮で続ける。issue 担当のセッションとのやり取りは [エージェントの作業フロー](docs/05-開発規則/subagents.md)の「役割と作業の流れ」。
- 引き継ぎの文書は、spec 担当のセッションを替えるときに作り、始めに読む（置き場所と読み方は skill `handoff`）。
- マージの後は、開発の進め方の「マージの許可と後片付け」に従って後片付けを行う。

## モデルの区分

サブエージェントに使うモデルは、区分（最上位・高性能・汎用・軽量）で決める。区分ごとの使う場面と、作業ごとの区分と effort は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「任せる作業とモデル」にある。サービスやモデルを替えるときの手順は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「モデルや設定を替えるとき」。

そこでの effort の低・中・高は、順に `low`・`medium`・`high`。指定がなければ、下の表の既定の effort を使う。実際のモデルと effort はサービスごとに表で決め、替えるときは表を修正する。

今使う AI のサービスは Claude Code（Anthropic）。

| 区分 | モデル | モデルの ID | 既定の effort |
|------|--------|-------------|---------------|
| 最上位 | Claude Fable 5.1 | `claude-fable-5-1` | `high` |
| 高性能 | Claude Opus 5.5 | `claude-opus-5-5` | `medium` |
| 汎用 | Claude Sonnet 5.5 | `claude-sonnet-5-5` | `medium` |
| 軽量 | Claude Haiku 5.5 ※1 | `claude-haiku-5-5` | `medium` |

最上位を使えるプランは Max で、Pro は使えない（2026-10-28）。出典と軽量の読める量は[実績](docs/07-記録/subagents-results.md)の「モデルの区分の出典と軽量の読める量」。

※1：Claude Haiku 5.5 は、1 回の要求が 10 万 token を超えると、入力・出力・キャッシュの全ての単価が 5 倍になる（Anthropic の料金の文書、2026-10-20 確認）。固定の分（定義なしで約 4 万、`.claude/agents/` の定義ありで約 0.6〜1.9 万 token）を含む。

### レビューのツール

使う時点は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「レビューの時点と profile」。

| ツール | 名前 | level の指定 |
|--------|------|--------------|
| 組み込みのコードレビュー | Claude Code の `/code-review` | 毎回指定する（厚め high、標準 medium、節約 low） |
| セキュリティのレビュー | Claude Code の `/security-review` | |

## Git

| 項目 | ルール |
|------|--------|
| ブランチ | `develop` から作業ブランチ `<issue の番号>-<短い名前>` を切って作業し、pull request で `develop` に入れる。`develop` と `main` には直接コミットしない ※2 |
| メッセージ | 日本語。1 行目は `<対象>: <要約>`（例：`tax: 税率の一覧の置き換えを実装する`）、決定の理由は本文 |
| 単位 | いつコミットしてもよいが、1 つのコミットに複数のタスクを混ぜない（例外は [開発の進め方](docs/05-開発規則/workflow.md)の「循環」） |
| 修正 | push していないコミットへの小さな修正は、amend・fixup で入れる。push 済みは書き換えない |
| push・pull request | push と pull request の作成は、許可を求めない ※3。push は段階の最後にする（例外は [開発の進め方](docs/05-開発規則/workflow.md)の「push のタイミング」） |
| マージ・tag | 許可と担当は [開発の進め方](docs/05-開発規則/workflow.md)の「マージの許可と後片付け」。マージは head の commit を指定する ※4。hook の扱いは ※5 |
| 入れないもの | 作業用の script、テストデータ、引き継ぎの文書、マシン固有のパス、個人の習慣は `/.local/` に置く（項目ごとの中身、使う者、保存の期間は [開発環境の起動](docs/06-手順書/setup.md)の「手元の置き場所」、`.gitignore` は [ブランチとリリース](docs/06-手順書/branches.md)の「切り方と pull request」） |

※2：ブランチの構成、作業ブランチ、保護、リリースは [ブランチとリリース](docs/06-手順書/branches.md)。

※3：draft の pull request を ready にする操作（`gh pr ready`）も、作成の一部として許可を求めない。`.claude/settings.json` の permissions で許可してあり、auto mode の判定を受けない。draft に戻す `--undo` は、確認を求める。

※4：マージは `gh pr merge --match-head-commit <SHA>` で行う。

※5：Claude Code の hook は、条件を満たさないマージ、tag、ruleset の変更で確認の画面を表示する（条件を満たす、危険度が低い変更のマージは通す）。担当のセッション、未解決の指摘、`hotfix-` の後の `main` から `develop` への取り込み（ブランチの名前で見分けられない）は hook が見ないので、文章の規則として守る。

## 環境

- JDK 21。統合テストと jOOQ のコード生成には Docker が必要。
- Gradle のプロジェクトは `backend/ecsite/` にある（`./gradlew` もそこで実行する）。リポジトリのルートと `backend/` には Gradle の設定がない。
- 起動・テストの手順は [開発環境の起動](docs/06-手順書/setup.md) と [テストの実行方法](docs/06-手順書/running-tests.md)。エージェントは skill `local-dev` を使う。
- Claude Code の hook と permission の書き先は、2 つに分ける。チームの例外のない規則は `.claude/settings.json`（Git に入る）、環境・利用者に固有のもの（zh-mixing の hook、利用者の SessionStart など）は `.claude/settings.local.json`（Git に入らない）である。hook の script は `scripts/dev/hooks/` に置く（Claude Code の `agent-guard.mjs` と、git の `pre-commit`・`commit-msg`、手元の hook の `session-start.mjs`・`backslash-guard.mjs`。手元の hook は `settings.local.json` に置き、設定は skill `local-dev` の「手元の hook の設定」）。作業ツリーのセッションが読む設定・定義・skill と、変更が反映される時点は、skill `sessions` の「作業ツリーのセッションが読むもの」。
