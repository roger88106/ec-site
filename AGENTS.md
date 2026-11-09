# AGENTS.md

AI エージェント（Claude Code など）がこのリポジトリで作業するときの手順。
プロジェクトのルールは [constitution](docs/constitution.md) とその細則にあり、本書はそれを守って作業する手順だけを書く。一致しないときは constitution を正とする。

## 最初に読むもの

本節はセッションの手順である。サブエージェントは、呼び出しの指示と定義に従う。

1. [constitution](docs/constitution.md)：大綱と文書の地図
2. [開発の進め方](docs/05-開発規則/workflow.md)：SDD と TDD、規模と push、利用者の確認と中断、完了の定義
3. 着手する段階の tasks と、冒頭の「参照」の文書（`docs/08-spec/<番号>-*/`）
4. コードを書く・レビューする前に、関わる書き方の文書（constitution の「コードの書き方」の一覧）
5. 文書（docs/・README・AGENTS.md・spec・plan・tasks・issue の本文とコメント）を書く・レビューする前に、場面で分けて読む。書く前に、skill `docs` の「進め方」も読む
   - 言い回し（用語、文の書き方）：[文書の書き方](docs/05-開発規則/writing.md)
   - 置き場所、参照、spec と plan の書き方、文書の長さ、文書に内容を追加するときの基準：[文書の構成](docs/05-開発規則/doc-structure.md)

マシン固有の環境と利用者個人の習慣（返答の言語など）は、`/.local/agent/environment.md` があれば始めに読んで従う。ないときは、本書と `docs/` だけで進める。

## skill

skill は `.claude/skills/` にある。追加する・削除する・役割を変えるときは、skill `writing-for-agents` に従う。Codex と Gemini CLI は `.claude/skills/` を読まず `.agents/skills/` を読むので、使うときに追加する。

## 作業の進め方

場面ごとの規則は、[開発の進め方](docs/05-開発規則/workflow.md)、[起票と記録の操作](docs/05-開発規則/issues.md)、[マージと後片付け](docs/05-開発規則/merging.md)、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の見出しで探す。spec 担当のセッションの役割（コードを書かない、合否を判定する）は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「役割」。次は、本書にだけある規則である。

- 作業の単位は tasks の 1 タスク。tasks にない作業は始めず、先に tasks への追加を提案する。ただし、実装の前の照合、`develop` の取り込み、pull request の作成、後片付けはタスクにしない。利用者が同意した試作（[起票と記録の操作](docs/05-開発規則/issues.md)の「構成が決まっていないときの試作」）も例外である。
- spec・plan・設計書・OpenAPI の誤りや不足に気づいたら、コードで回避せずに作業を止めて報告する。修正するときは文書が先。
- 文書で決まっていない設計上の判断は推測せず、選択肢と推奨を示して確認する。
- `[テスト]` のタスクは、失敗する（レッド）ことを確認してから実装に進む。テストを通すためにテストを弱めない。仕様と合わないと思ったら、変えずに報告する。
- 利用者の指示が規則と食い違うときは、従う前に伝え、その場限りの特例か、規則を修正するかを確認する。作業の習慣は、共有するものはリポジトリの文書に、マシンや個人に固有のものは `/.local/agent/environment.md` に書く。

### 完了の基準

- tasks の「完了」の条件を満たし、対応する AC のテストが通り、既存のテストを壊していない（`./gradlew check` など）。文書を更新するタスクがあるときは、それも終えてから、チェックボックスを `[x]` にする。
- 段階の最後のタスクの後は、段階の完了条件を確認して報告する。段階の最後のレビューで、変えたコードを [書き方の規則とレビュー](docs/05-開発規則/review.md) と照合する。

### セッションの始めと終わり

- セッションの始めに直近の CI を確認し（`gh run list -b develop -L 1`）、`develop` の CI が失敗していれば、すぐに起票し、spec の作業の前に修正する。
- 1 つの spec は 1 つの spec 担当のセッションで進め、長くなったら文脈の圧縮で続ける。セッションを替えるときは引き継ぎの文書を作り、新しいセッションは始めにそれを読む（置き場所と読み方は skill `handoff`）。

## モデルの区分

サブエージェントのモデルは、区分（最上位・高性能・汎用・軽量）で決める。区分ごとの使う場面と、作業ごとの区分と effort は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「任せる作業とモデル」、替えるときの手順は[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「モデルや設定を替えるとき」。その表の effort の低・中・高は、順に `low`・`medium`・`high`。指定がなければ、下の表の既定の effort を使う。

今使う AI のサービスは Claude Code（Anthropic）。

| 区分 | モデル | モデルの ID | 既定の effort |
|------|--------|-------------|---------------|
| 最上位 | Claude Fable 5.1 | `claude-fable-5-1` | `high` |
| 高性能 | Claude Opus 5.5 | `claude-opus-5-5` | `medium` |
| 汎用 | Claude Sonnet 5.5 | `claude-sonnet-5-5` | `medium` |
| 軽量 | Claude Haiku 5.5 ※1 | `claude-haiku-5-5` | `medium` |

最上位を使えるプランは Max で、Pro は使えない（2026-10-28）。出典と軽量の読める量は[エージェントの作業フローの調べた仕組みと実績](docs/07-記録/subagents-results.md)の「モデルの区分の出典と軽量の読める量」。

※1：Claude Haiku 5.5 は、1 回の要求が 10 万 token を超えると、全ての単価が 5 倍になる（Anthropic の料金の文書、2026-10-20 確認）。固定の分（定義なしで約 4 万、`.claude/agents/` の定義ありで約 0.6〜1.9 万 token）を含む。

### レビューのツール

使う時点は、[エージェントの作業フロー](docs/05-開発規則/subagents.md)の「レビューの時点と profile」。

| ツール | 名前 | level の指定 |
|--------|------|--------------|
| 組み込みのコードレビュー | Claude Code の `/code-review` | 毎回指定する（厚め high、標準 medium、節約 low） |
| セキュリティのレビュー | Claude Code の `/security-review` | |

## Git

| 項目 | ルール |
|------|--------|
| ブランチ | `develop` から作業ブランチ `agent/<issue の番号>-<短い名前>` を切って作業し、pull request で `develop` に入れる。`develop` と `main` には直接コミットしない（構成と保護は [ブランチとリリース](docs/06-手順書/branches.md)） |
| メッセージ | 日本語。1 行目は `<対象>: <要約>`（例：`tax: 税率の一覧の置き換えを実装する`）、決定の理由は本文 |
| 単位 | セッションは、いつコミットしてもよいが、1 つのコミットに複数のタスクを混ぜない（例外は [開発の進め方](docs/05-開発規則/workflow.md)の「循環」） |
| 修正 | push していないコミットへの小さな修正は、amend・fixup で入れる。push 済みは書き換えない |
| push・pull request | セッションは、push と pull request の作成で許可を求めない ※2。push の時点は [開発の進め方](docs/05-開発規則/workflow.md)の「push のタイミング」 |
| マージ・tag | 許可と担当は [マージと後片付け](docs/05-開発規則/merging.md)の「マージの許可と後片付け」。マージは `gh pr merge --match-head-commit <SHA>` で head の commit を指定する。hook の扱いは ※3 |
| 入れないもの | 作業用の script、テストデータ、引き継ぎの文書、マシン固有のパス、個人の習慣は `/.local/` に置く（[開発環境の起動](docs/06-手順書/setup.md)の「手元の置き場所」） |

※2：`gh pr ready` も許可を求めない（`.claude/settings.json` の permissions で許可してある）。draft に戻す `--undo` は、確認を求める。

※3：hook は、条件を満たさないマージ、tag、ruleset の変更で確認の画面を表示する。担当のセッション、未解決の指摘、`hotfix/` の後の `main` から `develop` への取り込みは hook が見ないので、[マージと後片付け](docs/05-開発規則/merging.md)の規則として守る。

## 環境

- JDK 21。統合テストと jOOQ のコード生成には Docker が必要。
- Gradle のプロジェクトは `backend/ecsite/` にある（`./gradlew` もそこで実行する）。リポジトリのルートと `backend/` には Gradle の設定がない。
- 起動・テストの手順は [開発環境の起動](docs/06-手順書/setup.md) と [テストの実行方法](docs/06-手順書/running-tests.md)。エージェントは skill `local-dev` を使う。
- hook と permission の書き先と script の置き場所は、skill `local-dev` の[手元の hook の設定](.claude/skills/local-dev/hooks.md)。作業ツリーのセッションが読むものは、skill `sessions` の[作業ツリーのセッションが読むもの](.claude/skills/sessions/session-reads.md)。
