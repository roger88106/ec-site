# Plan 7：1→10 の開発の進め方への切り替え

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 7 が確定した。spec 019 が完了し、tag `v0.1.0` がある。文書の書き方は skill `docs`（[SKILL.md](../../../../../.claude/skills/docs/SKILL.md)）と[開発の進め方](../../../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../../../rules/notation.md)。レビューは skill `review`（[SKILL.md](../../../../../.claude/skills/review/SKILL.md)）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 起票と TODO 一覧 | 第 1 節 | DS-7-1（DC-7-1〜13） |
| 番号と規模 | 第 2 節 | DS-7-2（DC-7-14〜31） |
| ブランチと release | 第 3 節 | DS-7-3（DC-7-32〜62） |
| エージェントの作業の流れ | 第 4 節 | DS-7-4（DC-7-63〜90） |
| 本 spec の進め方 | 第 5 節 | DS-7-5（DC-7-91〜94） |
| 用語集 | 第 6 節 | 全て |
| 段階と確認の方法 | 第 7 節、第 8 節 | 全て |

DC の列は「7-1」の形で書く。spec の番号 7 を付けた DC-7-1 の略である。

共通の決定：

- 規則の文書（段階 1）を先に書き、検査の script（段階 2）と GitHub のファイル（段階 3）を後にする。理由：script とファイルは、文書に書いた規則を自動で検査し、実行するものであるため。
- GitHub の設定（段階 4）は、`develop` を push してから行う。
- 設定の変更は、plan と tasks の確認で、利用者の許可を一度にまとめて得た（2026-10-22）。変える設定の一覧は第 3 節に書く。
- TODO 一覧の移行（段階 5）は、label と milestone を作った後に行う。
- 本 spec は特例（DN-7-3）として、新しい進め方を、確定した規則から適用する。
- plan と tasks は一緒に利用者が確認する。
- 段階の間では、確認のために中断しない。
- 本 spec の作業は、このセッションが spec 担当のセッションとして行う。このセッションは、issue 担当のセッションを兼ねる。
- 文書の本文は、日本語の要点を渡して、文書を書くサブエージェントに書かせる。
- script は実装役のサブエージェントに書かせる。
- 理由：上の 2 つは、DC-7-64 を本 spec から適用するため。
- 1 タスクを 1 commit にする。
- 例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../../../rules/workflow.md) 第 2.1 節）。

## 1. 起票と TODO 一覧（DS-7-1：DC-7-1〜13）

| DC | 決定 | 確認方法 |
|----|------|----------|
| 7-1、7-2 | issue のテンプレートを `.github/ISSUE_TEMPLATE/` に YAML の issue forms で 3 つ置く（`feature.yml`、`cleanup.yml`、`bug.yml`） | GitHub の画面で、新しい issue の選択肢が 3 つだけであることを確認する |
| 7-1、7-2 | テンプレートごとに `種類:` の label を自動で付ける | GitHub の画面で確認する |
| 7-1、7-2 | `config.yml` で `blank_issues_enabled: false` にする | GitHub の画面で確認する |
| 7-3 | テンプレートの項目（下の表）と同じ節を、エージェントの起票の本文に使うと、開発の進め方に書く | 差分を読む |
| 7-4〜7-6 | label と milestone を `gh` で作る（下の表）。既定の label を削除する | `gh label list`、`gh api` で milestone を読む |
| 7-7〜7-9 | TODO 一覧の項目を issue に集約する案を作り、利用者が承認してから起票する | tasks の結果の表（TODO の項目、issue の番号または移さない理由） |
| 7-7〜7-9 | 案には、どの項目をどの issue にまとめるか、題名、label を書く | tasks の結果の表 |
| 7-7〜7-9 | 案は `/.local/` に置く | tasks の結果の表 |
| 7-7〜7-9 | 起票は script で行う | tasks の結果の表 |
| 7-7〜7-9 | 起票する issue の本文は、「整理」か「機能」のテンプレートの節に合わせ、出典を書く | tasks の結果の表 |
| 7-7〜7-9 | 移さない項目（完了済み、不要）は、理由を tasks の結果に書く | tasks の結果の表 |
| 7-10、7-11 | `docs/todo.md` を削除する | `git grep todo.md`（アーカイブの外で 0） |
| 7-10、7-11 | TODO 一覧を指す箇所を、issue（milestone `1→10` の一覧）を指す形にする。対象は下の箇条書きに示す | `doc-refs.mjs`、`git grep todo.md`（アーカイブの外で 0） |
| 7-10、7-11 | アーカイブした spec の中のリンクは変えない（DN-7-2）。理由：文書の参照の検査がアーカイブを対象にしないため | `doc-refs.mjs` |
| 7-12 | 後回しにする作業の起票の規則を、開発の進め方と AGENTS.md に書く | 差分を読む |
| 7-13 | 全ての変更が issue から始まること（Dependabot を除く）を、開発の進め方と AGENTS.md に書く | 差分を読む |

TODO 一覧を指す箇所の対象（DC-7-10、7-11）：

- README、constitution、AGENTS.md
- `docs/guides/ci.md`、`docs/guides/setup.md`
- `docs/design/glossary.md`
- skill `docs`（`SKILL.md`、`tech-writing.md`）
- `scripts/ci/glossary-ids.mjs`

テンプレートの項目（\* は必須）：

| テンプレート | 項目 |
|--------------|------|
| 機能 | 背景と目的\*、やりたいこと\*、条件付きの条件、出典、関連 |
| 整理 | 背景と目的\*、整えるもの\*、完了の目安、条件付きの条件、出典、関連 |
| 不具合 | 起きたこと\*、再現の手順\*、期待した振る舞い\*、発生した場所、関係する AC、関連 |

label（名前は日本語にし、説明の欄に業務の名前を書く）：

| 群 | label |
|----|-------|
| 種類 | `種類:機能`、`種類:整理`、`種類:不具合` |
| 領域：バックエンドのモジュール | `領域:tax`、`領域:catalog`、`領域:inventory`、`領域:media`、`領域:storefront`、`領域:cart`、`領域:member`、`領域:order`、`領域:payment`、`領域:shipping`、`領域:notification`、`領域:reporting`、`領域:region` |
| 領域：その他 | `領域:common`（共通のコード、設定、セキュリティの設定）、`領域:store`、`領域:admin`、`領域:shared`、`領域:e2e`、`領域:ci`（CI と `scripts/`）、`領域:docs`（文書、規則、AGENTS.md、skill）、`領域:idp`（Keycloak）、`領域:deploy` |
| 保留 | `保留:条件付き`（条件は本文に書く） |
| その他 | `spec`（追跡の issue）、`依存の更新`（Dependabot） |

milestone は `1→10` を作る。対応時期が「未定」の項目には付けない。

## 2. 番号と規模（DS-7-2：DC-7-14〜31）

| DC | 決定 | 確認方法 |
|----|------|----------|
| 7-14〜7-19 | 文書の記号と番号に書く：spec の番号は追跡の issue の番号とする | 差分を読む |
| 7-14〜7-19 | 文書の記号と番号に書く：spec のディレクトリは `<番号>-<短い名前>` とし、0 で埋めない | 差分を読む |
| 7-14〜7-19 | 文書の記号と番号に書く：タスクの番号は `T<spec の番号>-<段階>-<連番>` とする（例：T7-01-01） | 差分を読む |
| 7-14〜7-19 | 文書の記号と番号に書く：spec の中の番号は `AC-<spec の番号>-<連番>` とする | 差分を読む |
| 7-14〜7-19 | 文書の記号と番号に書く：MVP の番号は 3 桁の 0 埋め（001〜019）のまま残し、新しい文書では「MVP 012」と書く | 差分を読む |
| 7-14〜7-19 | 検査の script が spec の番号を区別する規則：0 で始まる 3 桁の番号は MVP、0 で始めない番号は 1→10 の spec とする。理由：spec 7〜19 が MVP の 007〜019 と同じ数になるため | script のテスト |
| 7-20、7-21 | `ac-check.mjs` が、MVP の `AC-n` と新しい `AC-n-m` の両方を読み、テスト名と照合する | script のテスト（先に書き、レッドを確認する） |
| 7-20、7-21 | 範囲の書き方（AC-8〜10）をエラーにする規則は、`AC-n` と `AC-n-m` の両方に適用する | script のテスト（先に書き、レッドを確認する） |
| 7-14〜7-19 | 他の script を新しい番号に対応させる（下の箇条書き） | script のテスト（先に書き、レッドを確認する） |
| 7-22〜7-26 | 規模の区分と判定の基準、文書、利用者の承認を、開発の進め方に書く | 差分を読む |
| 7-22〜7-26 | M の spec は 1 つのファイルにまとめ、`docs/specs/<番号>-<名前>/spec.md` に置く（目的、完了の条件か AC、tasks の節）。理由：検査の script（ac-check、progress、spec-dirs）が、spec のディレクトリの spec.md を読むため。spec の最後のレビューで、`<番号>-<名前>.md` から変えた | 差分を読む |
| 7-27 | 追跡の issue と、扱う issue を sub-issue として関連付ける手順を、開発の進め方か skill に書く（`gh api` の GraphQL `addSubIssue`） | 差分を読む |
| 7-27 | 本 spec では #7 に、移行した issue のうち本 spec で扱うものを関連付ける | `gh api` で #7 の sub-issue を読む |
| 7-28、7-29 | README の進捗を、MVP の節（今の表）と、1→10 の節に分ける | `progress.mjs` のテスト |
| 7-28、7-29 | 1→10 の節は、milestone `1→10` と `spec` の label の issue の一覧へのリンクにする | `progress.mjs` のテスト |
| 7-28、7-29 | `progress.mjs` は、MVP の表と「MVP の完成までの一覧」の照合を続ける | `progress.mjs` のテスト |
| 7-28、7-29 | `progress.mjs` は、1→10 の spec について「アーカイブにある spec の tasks が全て済んでいる」ことを確認する形に変える | `progress.mjs` のテスト |
| 7-30、7-31 | 完了した 1→10 の spec は `docs/specs/archive/1to10/` に置く | 本 spec の最後の段階で、本 spec を移す |
| 7-30、7-31 | M の spec は、ディレクトリごと（`<番号>-<名前>/spec.md`）同じ場所に移す。spec の最後のレビューで、1 つのファイルの形から変えた | 本 spec の最後の段階で、本 spec を移す |
| 7-30、7-31 | spec の最後のタスクで移し、同じ pull request に含めることを、開発の進め方に書く | 差分を読む |

他の script の合わせ方（DC-7-14〜19）：

- `lib/spec-dirs.mjs`：ディレクトリの名前を、0 埋めなしで読む。
- `spec-numbers.mjs`：`AC-n-m`、`T7-01-01`、`spec 7` を検出する。
- `related-specs.mjs`：タスク番号 `T<番号>-` を 0 埋めなしで読み、MVP の 0 埋めの番号と区別する。

## 3. ブランチと release（DS-7-3：DC-7-32〜62）

| DC | 決定 | 確認方法 |
|----|------|----------|
| 7-32〜7-36 | ブランチの規則を新しい手順の文書（`docs/guides/branches.md`、題名「ブランチと release」）に書き、AGENTS.md の Git の表から指す | 差分を読む |
| 7-37、7-38 | GitHub の ruleset を `develop` と `main` に作る | `gh api` で ruleset を読む |
| 7-37、7-38 | pull request を必須にする | `gh api` で ruleset を読む |
| 7-37、7-38 | CI の必須の検査は、CI の全ての job（バックエンド、管理画面、店舗画面、E2E）と、文書の検査にする | `gh api` で ruleset を読む。本 spec の pull request で、検査が必須になっていることを確認する |
| 7-37、7-38 | マージの前に、作業ブランチが最新の `develop` を含むことを必須にする（`develop` だけ） | `gh api` で ruleset を読む |
| 7-37、7-38 | force push と削除を禁止する | `gh api` で ruleset を読む |
| 7-37、7-38 | approve の数は 0 にする。理由：利用者が 1 人のため | `gh api` で ruleset を読む |
| 7-39、7-40 | `main` への pull request の元のブランチを検査する workflow（`.github/workflows/main-source.yml`）を作る | 本 spec の release の pull request で通ること |
| 7-39、7-40 | その検査を `main` の ruleset の必須の検査にする | `gh api` で ruleset を読む |
| 7-39、7-40 | `release-` と `hotfix-` で始まらなければ失敗する | workflow の作成時に、別の名前のブランチで意図的に失敗させ、1 回確認する |
| 7-41〜7-43 | リポジトリの設定で、merge commit だけを許す（squash と rebase は無効） | `gh api repos/...` で設定を読む |
| 7-41〜7-43 | マージしたブランチを自動で削除する | `gh api repos/...` で設定を読む |
| 7-44、7-45 | 完了の定義を、開発の進め方に書く | 差分を読む |
| 7-44、7-45 | 機能を設定のフラグで無効にする規則を、開発の進め方に書く | 差分を読む |
| 7-46〜7-50 | release と緊急の修正の手順を、ブランチの文書に書く | 本 spec の release |
| 7-46〜7-50 | tag は `v<版>`、GitHub の Release は `gh release create` で作る | 本 spec の release |
| 7-51〜7-59 | `.github/dependabot.yml` を作る（内容は下の表） | `develop` に入った後に、Dependabot の画面で設定のエラーがないことを確認する |
| 7-51〜7-59 | Dependabot のセキュリティの更新は、リポジトリの設定で有効にする | Dependabot の画面 |
| 7-51〜7-59 | 例外（issue が不要、英語の commit のメッセージ）を開発の進め方に書く | 差分を読む |
| 7-51〜7-59 | 注意：GitHub の文書（2026-10-22）では、Dependabot の pnpm の対応は v10 まで。このリポジトリは pnpm 12 である。npm の更新が失敗したら、issue を起票し、その issue で Renovate への切り替えを検討する | Dependabot の実行の記録 |
| 7-51〜7-59 | 本 spec では Renovate に切り替えない | Dependabot の実行の記録 |
| 7-60〜7-62 | CD・staging・preview はデプロイの spec で決めることと、それまでの確認と release の扱いを、ブランチの文書に書く | 差分を読む |

`.github/dependabot.yml` の内容（DC-7-51〜59）：

| 項目 | 内容 |
|------|------|
| 対象 | `gradle`（`/backend/ecsite`）、`npm`（`/`。pnpm の workspace）、`github-actions`（`/`）、`docker-compose`（`/docker`） |
| 頻度 | 週 1 回（月曜、日本時間） |
| group | minor と patch を ecosystem ごとにまとめる |
| cooldown | major 30、minor 7、patch 3。GitHub Actions と Docker Compose は SemVer の日数に対応しないので、7 日（default-days）とする |
| label | `種類:整理`、`領域:ci`、`依存の更新` |
| commit のメッセージの前置き | `deps` |

GitHub の設定で変えるもの（plan と tasks の確認で利用者が許可した）：

| 変えるもの | 内容 |
|------------|------|
| `develop` の push | ローカルの `develop`（`main` と同じ commit）を push する |
| 既定のブランチ | `main` から `develop` に変える |
| マージの方式 | merge commit だけ。squash と rebase を無効にする |
| ブランチの自動の削除 | 有効にする |
| ruleset | `develop`、`main`（上の表の DC-7-37、7-38） |
| label | 作成と、既定の label の削除 |
| milestone | `1→10` を作成する |
| Dependabot のセキュリティの更新 | 有効にする（脆弱性の通知を含む） |

変えないもの：

- `pages.yml`（機能紹介のページ）は、`main` の push のままにする。
- README の CI のバッジは、`main` を指したままにする。

## 4. エージェントの作業の流れ（DS-7-4：DC-7-63〜90）

| DC | 決定 | 確認方法 |
|----|------|----------|
| 7-63〜7-66 | 役割と、判定を spec 担当のセッションが行うことを、エージェントの作業フロー（[subagents.md](../../../../rules/subagents.md)）に書く | 差分を読む |
| 7-63〜7-66 | spec 担当のセッションがコードを書かないことを、エージェントの作業フローに書く | 差分を読む |
| 7-63〜7-66 | AGENTS.md からは、エージェントの作業フローの要点と節を指す | 差分を読む |
| 7-63 | issue 担当のセッションと spec 担当のセッションのやり取りを、エージェントの作業フローに書く（下の箇条書き） | 差分を読む |
| 7-67〜7-72 | `[P]`、依存、変える範囲、`[テスト]` と実装の組、`[リスク]` の基準を、文書の記号と番号に書く | 差分を読む |
| 7-67〜7-72 | tasks の書き方は、タスクの行の下に「依存：」「範囲：」を書く形にする | 本 spec の tasks がその形で書かれている |
| 7-73〜7-76 | レビューの時点と、profile ごとのモデルの組み合わせ（下の表）を、エージェントの作業フローと skill `review` に書く | 差分を読む |
| 7-73〜7-76 | profile を選ぶ場所は `/.local/agent/environment.md` にする | 差分を読む |
| 7-77 | skill `review` の観点の一覧（`prompts.md`）に「ファイルをまたぐ影響」を追加する | 差分を読む |
| 7-78〜7-81 | 利用者が確認する対象、待たないこと、中断する条件、まとめて質問することを、開発の進め方と AGENTS.md に書く | 差分を読む |
| 7-78〜7-81 | AGENTS.md の次の 3 つの記述を置き換える（下の箇条書き） | 差分を読む |
| 7-82、7-83 | pull request のテンプレート（`.github/pull_request_template.md`）を作る | 本 spec の pull request がテンプレートで書かれている |
| 7-82、7-83 | テンプレートの節は、概要、規模、spec、関連の issue、確認したこと、受け入れの確認、文書、推奨の指摘の扱い、起票する issue の案にする。関連の issue には `Closes #番号` を書き、受け入れの確認には AC ごとの根拠を書く | 本 spec の pull request がテンプレートで書かれている |
| 7-84、7-85 | マージの担当と許可を、AGENTS.md の Git の表に書く。担当は、作業ブランチ → `develop` が spec 担当のセッション、`release-` のブランチ → `main` と、`hotfix-` のブランチ → `main`、その後の `main` → `develop` への取り込みが issue 担当のセッション | 差分を読む |
| 7-84、7-85 | push と pull request の作成は、許可を求めずに行う。理由：マージが利用者の確認の対象のため | 差分を読む |
| 7-84、7-85 | エージェントは、自分が作った pull request を、利用者の許可なしにマージしない。tag も利用者の許可を得る | 差分を読む |
| 7-86〜7-89 | 並行の規則を、エージェントの作業フローに書く | 差分を読む |
| 7-90 | 負荷の高い作業を同時に 1 つのセッションだけで行うことを、skill `local-dev` に書く | 差分を読む |
| 7-90 | 並行して作業できるローカル環境ができるまでの規則とする | 差分を読む |

issue 担当のセッションと spec 担当のセッションのやり取り（DC-7-63）：

- issue 担当のセッションが、spec 担当のセッションを起動する。
- 2 つのセッションは親子の関係にあり、ターンの終わりに親へ通知される。
- 記録は、追跡の issue のコメントに残す（開始、pull request、完了）。
- skill `handoff` は、spec 担当のセッションを途中で別のセッションに替えるときだけに使う。
- 利用者がチャットで指示すれば、エージェントはセッションを起動できる。

AGENTS.md で置き換える記述（DC-7-78〜81）：

- 「文書を 1 つ書き終えるごとに見せて止め」
- 「実装の段階も 1 つずつ指示で始める」
- 「返答で予告した次の作業も指示を受けてから」

profile ごとのレビューの組み合わせ（判定は spec 担当のセッションが行う）：

| 時点 | 厚め | 標準 | 節約 |
|------|------|------|------|
| `[リスク]` のタスクの後 | Opus | Sonnet | 行わない |
| 段階の最後：一般の観点 | Sonnet を 2 回（和集合） | Sonnet を 1 回 | Sonnet を 1 回 |
| 段階の最後：重要な観点（金額、セキュリティ、`[リスク]`、ファイルをまたぐ影響） | Opus と Sonnet（和集合） | Opus を 1 回 | Sonnet を 1 回 |
| spec の最後 | 段階の最後と同じ。ファイルをまたぐ影響の観点を必ず含める | 同左 | 同左 |
| pull request の前 | Opus（spec との一致、完了の定義）と組み込みの `/code-review` | Sonnet と `/code-review` | `/code-review` |
| release の前 | Opus（マイグレーション、設定、互換性、release の説明の案） | Sonnet | Sonnet |

- レビュー役は「高」と「必須」だけを返す。
- 推奨は、自動の検査に移せるかを付けて、spec の最後にまとめる。
- レビューごとに token、時間、指摘の数、採用した数を記録し、profile を再検討する材料にする。

## 5. 本 spec の進め方（DS-7-5：DC-7-91〜94）

| DC | 決定 | 確認方法 |
|----|------|----------|
| 7-91 | 本 spec の作業ブランチ `7-dev-process` から、テンプレートで `develop` への pull request を作る | pull request |
| 7-91 | 作業ブランチの pull request は、利用者の許可を得てマージする | pull request |
| 7-92、7-93 | `develop` の確認した commit から `release-0.2.0` を作り、`main` に pull request を作る | pull request |
| 7-92、7-93 | `main` への pull request は、利用者の許可を得てマージし、tag `v0.2.0` と GitHub の Release を作る | tag と Release |
| 7-94 | 本 spec の途中で見つけた進め方の不備は、tasks の末尾にタスクを追加して修正するか、issue として起票する | tasks |
| 7-94 | どちらにしたかを tasks の結果に書く | tasks |

- tasks は、`develop` への pull request を作るところまでにする。理由：マージの後に tasks を更新するには、もう 1 つの pull request が必要になるため。
- マージ、release の前のレビュー、release と tag は、issue 担当のセッションが行い、[#7](https://github.com/roger88106/ec-site/issues/7) のコメントに記録する。
- `main` の元のブランチの検査を `main` の必須の検査にする設定は、workflow が `develop` に入った後に、issue 担当のセッションが release の前に行う。

## 6. 用語集

[用語集](../../../../design/glossary.md)の「開発」の節に、次の語を追加する。

- 追跡の issue
- issue 担当のセッション
- spec 担当のセッション
- 規模（S・M・L）
- 完了の定義
- レビューの profile
- 実装役

識別子の列には、`develop`、`main`、`release-`、`hotfix-`、`[P]`、`[リスク]` を書く。

## 7. 段階

| 段階 | 内容 | DC |
|------|------|----|
| 1 | 規則の文書：文書の記号と番号、開発の進め方、エージェントの作業フロー、ブランチの文書（新規）、AGENTS.md、constitution、skill（`review`、`handoff`、`local-dev`、`docs`）、用語集、文書のレビュー | 7-3、7-12〜19、7-22〜26、7-30〜36、7-44〜50、7-58〜90 |
| 2 | 検査の script：spec のディレクトリ、spec の番号、関連 spec、AC の照合、進捗 | 7-14〜21、7-28、7-29 |
| 3 | GitHub のファイル：issue のテンプレート、pull request のテンプレート、Dependabot、`main` の元のブランチの検査 | 7-1、7-2、7-39、7-40、7-51〜57、7-82、7-83 |
| 4 | GitHub の設定：`develop` の push、既定のブランチ、マージの方式、自動の削除、ruleset、label、milestone | 7-4〜6、7-32、7-37、7-38、7-41〜43 |
| 5 | TODO 一覧の移行：まとめ方の案と承認、起票、sub-issue、TODO 一覧の削除と参照の修正、README の進捗 | 7-7〜11、7-27〜29 |
| 6 | 完了：spec の最後のレビュー、本 spec のアーカイブ、pull request の前のレビュー、pull request の作成。マージと release は tasks の外（第 5 節） | 7-91〜94、7-31 |

- 段階 1 の文書は、ファイルごとに `[P]` にできる。AGENTS.md と constitution は、他の文書の節を指すので最後にする。
- 段階 2 の script は、ファイルが重ならない組ごとに `[P]` にする。
- 各段階の最後に `check-all.sh` を実行する。
- 本 spec は API、画面、DB を変えないので、`./gradlew check`、`check-frontend.sh`、`e2e.sh` は、`develop` への pull request の CI で確認する。
- レビューは profile「厚め」で行う。

## 8. 確認の方法

| DC・DN | 確認の方法 |
|--------|------------|
| 7-1〜6 | GitHub の画面と `gh` の出力（tasks の結果） |
| 7-7〜11 | tasks の結果の表、`doc-refs.mjs`、`git grep` |
| 7-12〜19、7-22〜26、7-30〜36、7-44〜50、7-58〜90 | 差分を読む（段階 1 と spec の最後のレビュー） |
| 7-14〜21、7-28、7-29 | script のテスト |
| 7-37〜43 | `gh api` の出力、本 spec の pull request |
| 7-51〜57 | Dependabot の画面 |
| 7-91〜94 | pull request、tag、Release、tasks |
| DN-7-1 | 差分に API、画面、マイグレーションの変更がないこと |
| DN-7-2 | 差分にアーカイブした MVP の spec の変更がないこと |
| DN-7-4 | `check-all.sh` と CI |
