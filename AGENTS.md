# AGENTS.md

AI エージェント（Claude Code など）がこのリポジトリで作業するときの手順。
プロジェクトのルールは [constitution](docs/constitution.md) とその細則にあり、本書はそれを守って作業する手順だけを書く。一致しないときは constitution を正とする。

## 最初に読むもの

1. [constitution](docs/constitution.md)：大綱と文書の地図
2. [開発の進め方](docs/rules/workflow.md)：SDD と TDD、起票と規模、利用者の確認と中断、完了の定義
3. 着手する段階の tasks ファイル（`docs/specs/NNN-*/tasks/`）と、冒頭の「参照」の文書
4. コードを書く・レビューする前に、関わる書き方の文書（constitution 第 14 節）。バックエンドは [バックエンドの書き方](docs/rules/backend-coding.md)、フロントは [フロントの書き方](docs/rules/frontend-coding.md)、どちらも [テストの書き方](docs/rules/test-writing.md) と [コメントの書き方](docs/rules/comments.md)

マシン固有の環境と利用者個人の習慣（返答の言語など）は、`/.local/agent/environment.md` があれば始めに読んで従う。Git に入らないので、新しい clone にはない。ないときは、本書と `docs/` だけで進める。

## skill

`.claude/skills/` にある。追加する・削除するときは、この表も修正する（skill `writing-for-agents`）。

| skill | いつ使うか |
|-------|------------|
| `local-dev` | compose・バックエンド・フロントを起動する、テストや E2E を実行する、commit の前に CI の検査を実行する、画面を確認する、DB や token を見る |
| `new-screen` | 店舗画面・管理画面に画面や操作を追加する、修正する |
| `handoff` | spec 担当のセッションを途中で別のセッションに替えるときに、引き継ぎの文書を作る（利用者が `/handoff` で呼ぶ） |
| `retro` | セッションを振り返り、エージェントの環境の改善点を挙げる（利用者が `/retro` で呼ぶ） |
| `docs` | 文書（docs/、README、AGENTS.md）を書く・修正する・移す |
| `writing-for-agents` | skill や AGENTS.md を作る、修正する |
| `review` | 段階・spec の最後のレビュー、plan・tasks の照合、文書のレビュー、全体のレビュー（受け入れ確認） |

## 作業の進め方

SDD と TDD の流れは [開発の進め方](docs/rules/workflow.md)。エージェントは次も守る。

- Dependabot の pull request を除き、全ての変更は issue から始める。規模（S・M・L）を提案し、利用者が承認してから進める（開発の進め方 第 4 節）。
- 急ぐ作業（不具合、マージの失敗など）は、すぐに起票する（開発の進め方 第 4.1 節）。
- spec 担当のセッションは、コードを書かず（数行の文書の修正を除く）、実装を実装役に任せ、合否を判定する（[エージェントの作業フロー](docs/rules/subagents.md) 第 4 節）。
- 作業の単位は tasks の 1 タスク。tasks にない作業は始めず、先に tasks への追加を提案する。
- spec・plan・設計書・OpenAPI の誤りや不足に気づいたら、コードで回避せずに作業を止めて報告する。修正するときは文書が先。
- 文書で決まっていない設計上の判断は推測せず、選択肢と推奨を示して確認する。
- 利用者が確認する対象（開発の進め方 第 5.1 節）で止まり、確認を受けてから進む。段階とタスクの間では止まらない（開発の進め方 第 5.2 節）。作業を中断して質問するのは、開発の進め方 第 5.3 節の条件のときだけ。
- 確認事項は、plan と tasks の確認でまとめて質問する（開発の進め方 第 5.4 節）。
- `[テスト]` のタスクは、失敗する（レッド）ことを確認してから実装に進む。テストを通すためにテストを弱めない。仕様と合わないと思ったら、変えずに報告する。
- 後回しにする作業と未決事項は、spec に記録する。spec の最後に、起票のまとめ方の案を pull request に付ける。利用者が承認したら、issue 担当のセッションが起票する（開発の進め方 第 4.1 節）。

### 完了の基準

- tasks の「完了」の条件を満たし、対応する AC のテストが通り、既存のテストを壊していない（`./gradlew check` など）。
- 文書を更新するタスク（用語集への追加など）を終え、チェックボックスを `[x]` にする。
- 段階の最後のタスクの後は、段階の完了条件を確認して報告する。MVP の後の進捗は、追跡の issue と milestone で見る（README の 1→10 の節が issue を指す）。
- spec の最後のタスクで、spec をアーカイブに移し、同じ pull request に含める（開発の進め方 第 4.2 節）。
- spec の最後のレビューの段階では、変えたコードを [書き方の規則とレビュー](docs/rules/review.md) と照合する。

### 規則と指示の食い違い

- 利用者の指示が規則（constitution、AGENTS.md、skill など）と食い違うときは、従う前に伝え、その場限りの特例か、規則を修正するかを確認する。
- 作業の習慣は記憶でなく文書に書く。チームで共有するものはリポジトリの文書に、マシンや利用者個人に固有のものは `/.local/agent/environment.md` に書く。

### セッションの始めと終わり

- 始めに直近の CI を確認する（`gh run list -b develop -L 1`）。失敗していれば tasks に追加して先に修正する。
- 1 つの spec は 1 つの spec 担当のセッションで進め、長くなったら文脈の圧縮で続ける。issue 担当のセッションとのやり取りは [エージェントの作業フロー](docs/rules/subagents.md) 第 4 節。
- 引き継ぎの文書（skill `handoff`）は、spec 担当のセッションを替えるときに作る。置き場所は `/.local/handoff/yyyyMMddHHmmss-<名前>/` に置く。指示がなければ最も新しいものを読む。古いものは利用者が削除する。

## Git

| 項目 | ルール |
|------|--------|
| ブランチ | `develop` から作業ブランチ `<issue の番号>-<短い名前>` を切って作業し、pull request で `develop` に入れる。`develop` と `main` には直接コミットしない ※2 |
| メッセージ | 日本語。1 行目は `<対象>: <要約>`（例：`tax: 税率の一覧の置き換えを実装する`）、決定の理由は本文 |
| 単位 | いつコミットしてもよいが、1 つのコミットに複数のタスクを混ぜない（例外は [開発の進め方](docs/rules/workflow.md) 第 2.1 節） |
| 修正 | push していないコミットへの小さな修正は、amend・fixup で入れる。push 済みは書き換えない |
| push・pull request | push と pull request の作成は、許可を求めない |
| マージ・tag | マージと tag は、利用者の許可を得てから。許可は操作のたびに求める。エージェントは、自分が作った pull request を利用者の許可なしにマージしない。担当：作業ブランチ → `develop` は spec 担当のセッション、`release-` のブランチと `hotfix-` のブランチ → `main`、および `hotfix-` の後の `main` → `develop` は issue 担当のセッション |
| 入れないもの | 作業用の script、テストデータ、引き継ぎの文書、マシン固有のパス、個人の習慣は `/.local/` に置く ※1 |

※1：`.claude` の skill とプレビューの設定は共有するので Git に入れる（マシン固有のパスは書かない）。ツールが置き場所を決めていて `/.local/` に置けないものだけを、`.gitignore` に例外として書く。

※2：ブランチの構成、作業ブランチ、保護、release は [ブランチと release](docs/guides/branches.md)。

## 環境

- JDK 21。統合テストと jOOQ のコード生成には Docker が必要。
- Gradle のプロジェクトは `backend/ecsite/` にある（`./gradlew` もそこで実行する）。リポジトリのルートと `backend/` には Gradle の設定がない。
- 起動・テストの手順は [開発環境の起動](docs/guides/setup.md) と [テストの実行方法](docs/guides/running-tests.md)。エージェントは skill `local-dev` を使う。
