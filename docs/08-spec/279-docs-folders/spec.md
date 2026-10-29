# Spec 279：docs の下のフォルダーを、使う場面が分かる日本語の名前と番号に変える

ステータス：確定
危険度：高

## 1. 背景と目的

milestone「整理の段階」の第 2 段（文書）の最初の spec である。追跡の issue は [#279](https://github.com/roger88106/ec-site/issues/279) で、作業ブランチは `279-docs-folders` である。整理の spec なので、AC、API、データベースは変えない。規模は M で、2026-10-29 に利用者が承認した。

第 2 段では、設計書を機能ごとではなく、読者と粒度ごとに再分類し、大半の文書を書き換える。書き換えの前に、`docs/` の下のフォルダーを、使う場面が分かる日本語の名前と番号に変える。後の spec は、新しいフォルダーの構成を前提に進める。方針は 2026-10-29 に利用者が決めた（issue 担当のセッションの棚卸しの備忘録。Git に入らない）。

危険度を高にする理由：検査の script（`scripts/ci/`）、hook（`scripts/dev/hooks/`）、`.claude/`、AGENTS.md、変更の危険度の一覧（`scripts/ci/risk.json`）を変えるため。

### 本 spec の用語

| 用語 | 意味 |
|------|------|
| 古いパス | `docs/design/`、`docs/rules/`、`docs/guides/`、`docs/specs/` と、その下のパス |
| 新しいパス | 第 2 節の構成のパス |
| アーカイブ | 完了した spec の置き場所。今は `docs/specs/archive/`、移した後は `docs/08-spec/archive/` |
| パスの文字列 | コード、コメント、script、設定、文書の本文に書いたパス（Markdown のリンクでないもの） |

### 新しい構成

issue の本文のとおりにする。ファイル名は英語のまま変えない。

| パス | 中身 |
|------|------|
| `constitution.md` | 入口。`docs/` の直下に残す |
| `01-概要/` | 専門知識がなくても読める全体像（システムの概要、業務の流れ、画面の操作の流れ）。本 spec では骨組みだけを置く |
| `02-設計/` | エンジニア向けの構成（全体の構成、アーキテクチャ、アプリ、権限） |
| `03-機能仕様/` | 主にエージェント向けの、モジュールごとの業務の規則、状態遷移、データ構造 |
| `04-決定記録/` | 決定の理由を、1 つの決定につき 1 ファイルで書く。本 spec では骨組みだけを置く |
| `05-開発規則/` | 今の `docs/rules/` |
| `06-手順書/` | 今の `docs/guides/` |
| `07-記録/` | 実績と測定 |
| `08-spec/` | 今の `docs/specs/`（進行中とアーカイブ） |

### 各ファイルの移動先

次の表は、2026-10-29 の `develop`（57ed2120）の `docs/` の全てのファイルを対象にする。※ の行は issue の本文に書いていないもので、第 6 節の決めたこと 1 で決めた（案 A）。

| 今のパス | 移動先 | 根拠 |
|----------|--------|------|
| `docs/constitution.md` | `docs/constitution.md`（移さない） | issue |
| `docs/design/overview.md` | `docs/02-設計/overview.md` | issue（全体の構成） |
| `docs/design/backend-architecture.md` | `docs/02-設計/backend-architecture.md` | issue（全体の構成） |
| `docs/design/frontend-architecture.md` | `docs/02-設計/frontend-architecture.md` | issue（全体の構成） |
| `docs/design/apps/admin.md`、`store.md` | `docs/02-設計/apps/` | issue（アプリ） |
| `docs/design/role-permissions.md` | `docs/02-設計/role-permissions.md` | issue（権限） |
| `docs/design/common.md` | `docs/02-設計/common.md` | ※ 共通コード、ログ、可観測性の構成で、エンジニア向け |
| `docs/design/modules/*.md`（13 ファイル） | `docs/03-機能仕様/*.md`（`modules/` の階層を作らない） | issue |
| `docs/design/acceptance-criteria.md` | `docs/03-機能仕様/acceptance-criteria.md` | ※ 業務の規則の受け入れ条件で、主にエージェントが読む |
| `docs/design/glossary.md` | `docs/03-機能仕様/glossary.md` | ※ 業務の用語と識別子の対応で、主にエージェントが文書とコードを書くときに引く |
| `docs/design/licenses.md` | `docs/05-開発規則/licenses.md` | ※ 依存を選ぶときの規則（許可・条件付き・禁止） |
| `docs/rules/*.md`（`subagents-results.md` を除く 10 ファイル） | `docs/05-開発規則/` | issue |
| `docs/rules/subagents-results.md` | `docs/07-記録/subagents-results.md` | issue |
| `docs/guides/*.md`（5 ファイル） | `docs/06-手順書/` | issue |
| `docs/specs/` の全て（`archive/mvp/`、`archive/1to10/`、進行中の spec） | `docs/08-spec/`（下の構成は変えない） | issue |
| なし | `docs/01-概要/README.md`、`docs/04-決定記録/README.md`（骨組み） | issue。形は決めたこと 2 |

### パスを直接書いている箇所

2026-10-29 の `develop`（57ed2120）で、アーカイブと本 spec を除いて数えた。PR #278 のマージの後（255d76b8）に再集計し、件数は変わらなかった。調べ方は表の下のコマンドである。

| 種類 | 調べ方 | 件数 |
|------|--------|------|
| パスの文字列 | コマンド 1 | 474 行、269 ファイル |
| 同上の内訳：バックエンドのコメント（`src/main/java`） | コマンド 1 | 178 行、172 ファイル（多くはモジュールの設計書を指す Javadoc） |
| 同上の内訳：バックエンドのテスト・ツール（`src/test` など）とビルド（`build.gradle.kts` など） | コマンド 1 | 49 行、38 ファイル |
| 同上の内訳：`scripts/ci/` と `scripts/dev/` | コマンド 1 | 96 行、27 ファイル（そのうちテストが 52 行、8 ファイル） |
| 同上の内訳：`.claude/`、`.github/`、AGENTS.md、README.md、`docs/`、E2E、compose | コマンド 1 | 151 行、32 ファイル |
| 正規表現で書いたパス（`docs\/specs\/archive\/` など） | コマンド 2 | 11 行。6 つの script（`doc-refs.mjs`・`ja-text.mjs`・`risk.mjs`・`related-specs.mjs`・`zh-mixing.cjs`・`replace-paths.cjs`）の 7 行と、2 つのテスト（`design-migrations.test.mjs`・`agent-guard.test.mjs`）の 4 行 |
| 古いフォルダーを指す Markdown の相対リンク | コマンド 3 | 557 行、45 ファイル |
| 同じフォルダーの中の相対リンク（`(glossary.md)` など） | `node scripts/ci/doc-refs.mjs`（移した後に切れたものが出る） | 移した後に数える |

```bash
# コマンド 1：パスの文字列
git grep -n -E 'docs/(design|rules|guides|specs)' -- ':!docs/specs/archive' ':!docs/specs/279-docs-folders'
# コマンド 2：正規表現で書いたパス
git grep -n -F -e 'docs\/design' -e 'docs\/rules' -e 'docs\/guides' -e 'docs\/specs' -- ':!docs/specs/archive' ':!docs/specs/279-docs-folders'
# コマンド 3：Markdown の相対リンク
git grep -n -E '\]\((\.\./)*(docs/)?(design|rules|guides|specs)/' -- '*.md' ':!docs/specs/archive' ':!docs/specs/279-docs-folders'
```

移した後は、除くパスを `docs/08-spec/archive` と `docs/08-spec/279-docs-folders` に替えて実行する（DC-279-6）。

- パスの文字列のうち、コメントでなく処理に使うもの（パスの組み立て、正規表現、定数）は次のとおりである。コメントと違い、置き換えた後に動かして確認する。
  - `scripts/ci/` の検査：`ac-check`・`ci-docs`・`design-migrations`・`doc-refs`・`env-defaults`・`glossary-ids`・`ja-text`・`license-policy`・`local-ports`・`progress`・`related-specs`・`risk`・`role-permissions`・`lib/spec-dirs`・`test-helpers`
  - `scripts/dev/`：`showcase-counts.mjs`
  - skill の script：`docs` の `replace-paths.cjs`・`suffixes.cjs`、`review` の `stats.cjs`、`zh-mixing` の `zh-mixing.cjs`
  - `build.gradle.kts` の system property と inputs
  - `.github/workflows/ci.yml` の paths
  - `scripts/ci/risk.json` の経路
- 置き換えのツール `replace-paths.cjs` は、テスト（`*.test.*`）、`backend/ecsite/src/main/resources/db/` の下、アーカイブを除く。テストの 71 行と `db/seed/local/README.md` の 1 行は、ツールの対象の外なので手動で修正する。
- アーカイブから外を指す相対リンクは、227 ファイルの 874 行ある。issue のとおり修正しない（第 5 節）。`doc-refs.mjs` は、アーカイブのうち `archive/mvp/mvp.md` だけを検査する。`mvp.md` の外へのリンクは README の 1 つで、階層の深さが変わらないので切れない。

### 並行する spec との重なり（PR #278）

spec 276 の PR [#278](https://github.com/roger88106/ec-site/pull/278)（ブランチ `276-retro-rules`、2026-10-29 にマージ。`develop` 255d76b8）は、次の 12 ファイルを変える（`gh pr diff 278 --name-only`）。

| ファイル | 本 spec が変えるもの |
|----------|----------------------|
| `AGENTS.md` | パスの文字列とリンク（13 行） |
| `docs/rules/workflow.md` | 移す。リンク、置き場所の表（開発の進め方 第 1.3 節）、アーカイブのリンクの規則（開発の進め方 第 1.4 節） |
| `docs/rules/subagents.md` | 移す。リンク |
| `docs/rules/subagents-results.md` | `07-記録/` に移す。リンク |
| `.claude/skills/local-dev/SKILL.md`、`review/SKILL.md`、`review/convergence.md`、`review/docs-review.md`、`review/prompts.md`、`sessions/SKILL.md`、`zh-mixing/SKILL.md` | リンクとパスの文字列（あるもの） |
| `docs/specs/archive/1to10/276-retro-rules/spec.md`（アーカイブ） | アーカイブと一緒に `08-spec/archive/1to10/` に移す。中身は変えない |

- 重なるのは、spec 276 が書く本文と、本 spec が修正するパスの同じ行または近くの行である。spec 276 のマージの後に実装を始め、spec 276 が追加した行のパスも修正する（第 7 節）。
- spec 276 の T276-01-09 で、アプリのセッションは、作業ツリーで動くときもメインの作業ツリーの設定・定義・skill を読むと分かった。そのため、本 spec が作業ツリーで変える `.claude/` の skill と hook は、マージするまで、アプリのセッションには効かない。本 spec の実装と検査は、作業ツリーの script を直接実行し、git の hook（作業ツリーの `scripts/dev/hooks/`）で確認するので、影響しない。

## 2. スコープ

| # | 目的 | 対象 | 優先度 |
|---|------|------|--------|
| DS-279-1 | `docs/` を新しい構成にする | D | P1 |
| DS-279-2 | アーカイブの外の、古いパスを指す参照を全て修正する | D | P1 |
| DS-279-3 | `docs/` の直下のフォルダー名を、許可する一覧のものだけに制限する検査を追加する | D | P1 |

## 3. 目的と完了条件

完了条件は、第 6 節の決めた案（全て案 A）で書いた。

### DS-279-1 `docs/` を新しい構成にする（D / P1）

- **DC-279-1** `docs/` の直下が、`constitution.md` と第 1 節の 8 つのフォルダーだけである。
- **DC-279-2** 各ファイルが、第 1 節の「各ファイルの移動先」の表の場所にある。ファイル名は変えていない。
- **DC-279-3** 移したファイルが、Git の履歴で移動として追える（`git log --follow` で移す前の履歴が出る）。理由：後の spec で書き換えるときに、元の文書と比べるため。例外は `03-機能仕様/acceptance-criteria.md` で、`git log --follow -M40%` で追える（第 9 節）。
- **DC-279-4** `01-概要/` と `04-決定記録/` に、骨組みの `README.md` がある。README は、そのフォルダーに置く文書（第 1 節の表の「中身」）と、中身を後の spec で書くことだけを書く。
- **DC-279-5** `08-spec/` の下の構成（直下に進行中の spec、`archive/mvp/`、`archive/1to10/`）が、今の `docs/specs/` と同じである。

### DS-279-2 アーカイブの外の、古いパスを指す参照を全て修正する（D / P1）

- **DC-279-6** アーカイブの外に、古いパスの文字列が残っていない。第 1 節の「パスを直接書いている箇所」の調べ方（パスの文字列、正規表現、Markdown の相対リンク）の件数が全て 0 である。例外は 2 つだけである。1 つは本 spec の spec.md、もう 1 つは `.claude/skills/docs/replace-paths.test.cjs` のうち、置き換えの入力の例として古いパスを使う行である（行の一覧は第 9 節に書く）。
- **DC-279-7** `node scripts/ci/doc-refs.mjs` が通る（Markdown のリンクと、文書の名前と節の参照が切れていない）。
- **DC-279-8** 検査の script とテストの、パスの定数と正規表現が新しいパスを指す。`bash scripts/ci/check-all.sh` が通る。アーカイブを除く検査（`doc-refs`・`ja-text`・`zh-mixing`・`replace-paths`・`suffixes`）は、`docs/08-spec/archive/` を除く。
- **DC-279-9** `build.gradle.kts` の inputs が新しいパスを指し、`./gradlew check` が通る。system property では、リポジトリのルート（ASCII のパス）だけを渡し、テストが Java のソースに書いた相対パスでパスを組み立てる（`Contracts.path` と `ContractsTest`）。理由：Windows では、system property の日本語が文字化けするため（T279-01-01、第 9 節）。`ci.yml` の paths が、`docs/02-設計/overview.md`・`docs/03-機能仕様/*.md`・`docs/05-開発規則/backend-coding.md` を含む。理由：これらの文書を変えた push で、文書と照合するバックエンドの単体テストを実行するため。
- **DC-279-10** `risk.json` の高の経路が、`docs/rules/` の代わりに `docs/05-開発規則/` を、`docs/design/role-permissions.md` の代わりに `docs/02-設計/role-permissions.md` を含む。`docs/07-記録/` は含めない（決めたこと 4）。
- **DC-279-11** 置き場所を書いた表が、新しい構成に合っている。対象は、constitution 第 3 節（リポジトリ構成の図と文書の地図）、skill `docs` 第 1 節、[開発の進め方](../../05-開発規則/workflow.md) 第 1.3 節である。変えるのは、パス、フォルダーの説明、新しいフォルダー 3 つ（`01-概要/`・`04-決定記録/`・`07-記録/`）の行だけである。
- **DC-279-12** アーカイブのリンクの規則を、アーカイブの中のリンクを修正しない規則に替えている（決めたこと 3）。対象は、[開発の進め方](../../05-開発規則/workflow.md) 第 1.4 節（完了した spec はリンクのパスだけを修正する）と、skill `docs` 第 4 節（アーカイブはリンクのパスだけを修正する）である。
- **DC-279-13** [開発環境の起動](../../06-手順書/setup.md) に、`git config core.quotepath false` を設定する手順と理由（`git status` などで日本語のパスを読める形で表示するため）がある（決めたこと 5）。
- **DC-279-14** push の後に、GitHub の画面で次を開いて確認している。新しい構成のフォルダーの一覧、constitution の文書の地図のリンク、日本語のパスを含むリンク（`02-設計/` と `03-機能仕様/` の文書から 1 つずつ）。結果を第 9 節に書き、スクリーンショットを利用者に送る。

### DS-279-3 `docs/` の直下のフォルダー名を、許可する一覧のものだけに制限する検査を追加する（D / P1）

- **DC-279-15** 新しい検査の script が `scripts/ci/` の直下にある。検査は、Git が追跡する `docs/` の下の各ファイルについて、パスの最初の要素が `constitution.md` か、許可するフォルダー名のどれかであることを確認する。違うときは、ファイルのパスと許可する一覧を出力して失敗する。理由：「開発」「記録」は、繁体字の「開發」「記錄」と取り違えやすいため。
- **DC-279-16** 許可するフォルダー名の一覧は、script の中の 1 か所に置く。script の冒頭のコメントと skill `docs` 第 1 節が、フォルダーを追加・変更するときに一覧と constitution の文書の地図を一緒に修正することを書いている。
- **DC-279-17** 検査のテストがあり、次の例を確認する。許可する名前と `constitution.md` が通る。繁体字の `05-開發規則/`・`07-記錄/` が失敗する。一覧にない名前（`design/`）と、`constitution.md` 以外の直下のファイルが失敗する。
- **DC-279-18** [CI の検査](../../06-手順書/ci.md) 第 3 節の表に、新しい検査の行がある（`ci-docs.mjs` が通る）。

## 4. 守る制約

- **DN-279-1** 振る舞い、API、データベース、AC を変えない。
- **DN-279-2** 文書の中身を変えない。変えるのは次だけである。
  - パスとリンク
  - 置き場所の表（DC-279-11）
  - アーカイブのリンクの規則（DC-279-12）
  - `core.quotepath` の手順（DC-279-13）
  - 新しい検査の説明（DC-279-16、18）
- **DN-279-3** アーカイブのファイルは場所だけを移し、中身を変えない。
- **DN-279-4** 適用済みのマイグレーションと seed の SQL を変えない。
- **DN-279-5** `bash scripts/ci/check-all.sh`、`./gradlew check`、フロントの検査と E2E を含む CI が通る。依存のパッケージを加えない。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| 文書の書き換え、分割、統合 | 後の spec（第 2 段の spec 1〜7）で行う |
| `01-概要/` と `04-決定記録/` の中身 | 書き方の規則を決めた後に、後の spec で書く |
| アーカイブの中のリンクを修正する | 利用者の決定（2026-10-29）。アーカイブは当時の記録で、当時の状態は Git の履歴で読める |
| AC 一覧の生成物への置き換え、`ac-check` がアーカイブを読むのをやめる | 後の spec（第 2 段の spec 5）で行う |
| 設計書の「関連 spec」の行とその検査（`related-specs`）の削除 | 後の spec（第 2 段の spec 5）で行う。本 spec では、検査をパスの変更に合わせるだけである |
| 参照の書き方を「文書の名前と節の番号」から「文書の名前と節の見出し」に変える | 後の spec（第 2 段の spec 2）で行う |
| ファイル名を日本語にする | 利用者の決定（2026-10-29）。フォルダー名だけを変える |

## 6. 決めたこと

2026-10-29 に、利用者が全て案 A に決めた。

### 決めたこと 1：issue の本文に移動先がない 4 つのファイル

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | `common.md` は `02-設計/`、`acceptance-criteria.md` と `glossary.md` は `03-機能仕様/`、`licenses.md` は `05-開発規則/` | 読者（エンジニアかエージェントか）と、規則か設計かで分かれる。AC 一覧と用語集は、後の spec で `03-機能仕様/` の文書と一緒に再作成する | `licenses.md` が設計のフォルダーから離れる | なし |
| 案 B | 4 つとも `02-設計/` | 今の `docs/design/` と同じ構成で、移動先を判断する必要がない | `03-機能仕様/` の文書が引く用語集と AC 一覧が、別のフォルダーになる | 第 1 節の表を変える |

### 決めたこと 2：骨組みの形

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | 各フォルダーに `README.md` を置き、置く文書と、中身を後の spec で書くことだけを書く | GitHub でフォルダーを開くと説明が出る。文書の地図からリンクできる | 後の spec で README の扱い（残すか、索引にするか）を決める必要がある | なし |
| 案 B | 空のファイル（`.gitkeep`）だけを置く | 文書が増えない | フォルダーの目的が、constitution の文書の地図にしか書かれない | DC-279-4 を変える |

### 決めたこと 3：アーカイブのリンクの規則を、本 spec で修正するか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | 本 spec で、開発の進め方 第 1.4 節と skill `docs` 第 4 節を「アーカイブの中のリンクは修正しない」に替える | 本 spec がアーカイブのリンクを修正しないことと、規則が一致したままマージされる | 毎回読む文書を、spec 3 の前に少し変える | なし |
| 案 B | 後の spec（第 2 段の spec 3）で修正する | 毎回読む文書の変更を 1 つの spec にまとめられる | spec 3 まで、規則と実際が食い違う | DC-279-12 を削る |

### 決めたこと 4：`07-記録/` を変更の危険度の高の経路に含めるか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | 含めない | 記録は規則でないので、記録の追加で利用者のマージの許可が必要にならない | 今は `docs/rules/` の下で高の経路に入る `subagents-results.md` が、低の経路になる | なし |
| 案 B | 含める | 今と同じく、`subagents-results.md` の変更は高になる | 記録を追加するだけの変更でも、利用者のマージの許可が必要になる | DC-279-10 を変える |

### 決めたこと 5：`core.quotepath` をどこに書くか

| 案 | 内容 | 利点 | 欠点 | 変わる DC |
|----|------|------|------|-----------|
| 案 A（推奨） | [開発環境の起動](../../06-手順書/setup.md) に、全ての開発者への手順として書く | このマシンだけでなく、clone した全ての環境で日本語のパスが読める | なし | なし |
| 案 B | このマシンの `/.local/agent/environment.md` にだけ書く | Git の文書が増えない | 他の環境では、`git status` のパスが `"docs/\346\251\237..."` の形で出る | DC-279-13 を削り、issue 担当のセッションに知らせる |

## 7. tasks

### 順序と重なり

- 本 spec は、spec 276（PR #278）のマージの後に実装を始める。理由：spec 276 が変える skill と規則の同じ箇所を、本 spec のパスの変更も変えるため。spec の確定は、マージを待たない。
- 実装の開始の条件：利用者が spec を承認し、PR #278 がマージされ、issue 担当のセッションがそれを知らせ、5 時間の使用量が 90% 未満、週の使用量が 80% 未満のとき。
- 実装を始める前の照合（[開発の進め方](../../05-開発規則/workflow.md) 第 5.6 節）では、最新の `develop` を取り込み、次を照合する。差があれば、本 spec を先に修正する。
  - 第 1 節の「各ファイルの移動先」の表に、`docs/` の全てのファイルがある（spec 276 のアーカイブを含む）。
  - 第 1 節の「パスを直接書いている箇所」の件数を再集計し、表を更新する。
  - spec 276 が追加・変更した行（第 1 節の 12 ファイル）にある古いパスを、T279-01-02 の範囲に含める。
  - 並行する他の spec（`docs/specs/` の直下）がないか。あれば、その spec の変えるファイルとの重なりを第 1 節に加え、実装の順序を issue 担当のセッションと決める。
- commit の前の hook は、ステージした内容で CI の検査の全てを実行する。ファイルを移すだけの commit は、リンクが切れて検査が失敗し、hook で止まる。そのため、移動とパスの修正を 1 つの commit（T279-01-02）にする。各ファイルの変更は数行なので、Git の類似度の判定で移動として検出される（DC-279-3 で確認する）。

### 段階 1：移動と検査

- 前提：spec の確定、spec 276 のマージ、`develop` の取り込み、実装を始める前の照合
- 完了条件：DC-279-1〜13、15〜18 を満たし、DN-279-1〜5 を守る。DC-279-14 は push の後に段階 2 で確認する。
- 参照：第 1 節、第 3 節、第 6 節、skill `docs` 第 4 節、[テストの書き方](../../05-開発規則/test-writing.md)、[コメントの書き方](../../05-開発規則/comments.md)
- `[テスト]` のタスクは手元でレッドを確認し、続く実装のタスクと同じ commit に入れる。commit の本文に、レッドを確認したことを書く（[開発の進め方](../../05-開発規則/workflow.md) 第 2.1 節）。

- [x] **T279-01-01** 日本語のパスを Gradle のテストに渡せるかを、このマシン（Windows）で確認する。
  - 依存：なし
  - 範囲：なし（作業ツリーの中で試し、commit しない）
  - 理由：`build.gradle.kts` は、設計書のパスを system property でテストに渡す。Windows では、コマンドラインの引数がシステムの文字コードに変換され、日本語が文字化けするおそれがある。
  - 手順：3 つの文書（`overview.md`、`modules/`、`backend-coding.md`）を新しいパスに仮に移し、`build.gradle.kts` の 3 つのパスを替えて試す。試した後に全て戻す。
  - 確認方法：仮に移した状態で `./gradlew test --tests '*ProductionClassesTest*' --tests '*ModuleDiagramTest*' --tests '*ModuleStructureTest*'` が通る。文字化けするときは、リポジトリのルートを渡してテストの側でパスを組み立てる案を、spec に追加してから進める。結果を第 9 節に書く。
- [x] **T279-01-02** ファイルを移し、古いパスを指す参照を全て修正する（DC-279-1〜13）。
  - 依存：T279-01-01
  - 範囲：`docs/` の全て、第 1 節の「パスを直接書いている箇所」のファイル、`Contracts.java` と `ContractsTest.java`（`backend/ecsite/src/testFixtures/`、テスト）
  - 手順：`git mv` で移し、骨組みの README を置く。リンクは `fix-links.cjs`、パスの文字列は `replace-paths.cjs` で修正する（skill `docs` 第 4 節）。`docs/design/modules` は `docs/design` より先に置き換える（長いパスを先にする）。ツールの対象の外（テスト、`db/seed/local/README.md`）と、正規表現で書いたパスは手動で修正する。
  - 確認方法：DC-279-6 の件数が 0。`bash scripts/ci/check-all.sh`、`./gradlew check`、`node scripts/ci/ja-text.mjs` が通る。`git diff --stat -M` で移したファイルが移動として出る。
- [x] **T279-01-03** [テスト] フォルダー名の検査のテストを書き、レッドを確認する（DC-279-17）。
  - 依存：T279-01-02
  - 範囲：`scripts/ci/docs-folders.test.mjs`
  - 確認方法：DC-279-17 の例がレッドになる。
- [x] **T279-01-04** フォルダー名の検査を書き、CI の検査の文書に加える（DC-279-15、16、18）。
  - 依存：T279-01-03
  - 範囲：`scripts/ci/docs-folders.mjs`、`docs/06-手順書/ci.md`、skill `docs` 第 1 節
  - 確認方法：T279-01-03 のテストが通る。`bash scripts/ci/check-all.sh` が通る。`docs/` の下に `05-開發規則/` のファイルを置くと失敗する（確認した後に削除する）。

### 段階 2：完了

- 前提：段階 1
- 完了条件：次を全て終えている。
  - spec の最後のレビューと、pull request の前のレビュー
  - DC-279-14 の画面の確認
  - spec のアーカイブへの移動

- [ ] **T279-02-01** spec の最後のレビューを行う（skill `review`）。
  - 依存：段階 1
  - 結果：
- [ ] **T279-02-02** pull request の前のレビューを、draft の pull request で行う（skill `review`）。本文に `Closes #279` を書く。push の後に DC-279-14 の画面の確認を行う。
  - 依存：T279-02-01
  - 結果：
- [ ] **T279-02-03** spec をアーカイブ（`docs/08-spec/archive/1to10/`）に移す。
  - 依存：T279-02-02

## 8. 後回しにする作業と起票の案

作業の中で見つけたものは、この節に加える。

| # | 項目 | 起票の案 |
|---|------|----------|
| 1 | `03-機能仕様/` の直下に、モジュールの文書と、AC 一覧・用語集が並ぶ。`design-migrations.mjs` と `ProductionClassesTest` は、直下の全ての `.md` をモジュールの文書として読む（今は、レベルの行とデータモデルの節がないので通る）。モジュールでない文書を加えると、誤って検査の対象になる | 第 2 段の、`03-機能仕様/` を書き換える spec の範囲に入れる（新しく起票しない） |

## 9. 作業の記録

T279-01-01 の結果、DC-279-6 の例外の一覧、DC-279-14 の画面の確認の結果を、ここに書く。

### T279-01-01 の結果（2026-10-29）

- 3 つの文書を新しいパスに仮に移し、system property に日本語のパスを渡すと、3 つのテストの 41 件のうち 38 件が失敗した。原因は `InvalidPathException: Illegal char <?>` で、システムの文字コード（CP950）にない字（「様」「発」）が `?` に変わった。
- 代替案を試し、41 件全てが通った。代替案では、system property にリポジトリのルート（`file("../..").absolutePath`）だけを渡し、`Contracts.path` が Java のソースに書いた日本語の相対パスをつなぐ。Java のソースは UTF-8 でコンパイルされる（`options.encoding`）。Gradle の inputs は、日本語のパスのままで動いた。
- 新しいパスを読んでいることは、`03-機能仕様/tax.md` を外し、`overview.md` と `backend-coding.md` を空にすると、対応する 4 件が失敗することで確認した。試した変更は全て戻した。
- DC-279-9 と T279-01-02 の範囲に、この形を加えた。

### T279-01-02 の結果（2026-10-29、commit 80f87732）

- 611 ファイルを変え、そのうち 331 が移動（rename）として出る。アーカイブは全て中身の差がない移動である。
- DC-279-3 の例外：`03-機能仕様/acceptance-criteria.md` は、全ての行の spec へのリンク（`../specs/` → `../08-spec/`）が変わり、類似度が 40% になるため、Git の既定の閾値（50%）では移動として出ない。`git log --follow -M40% -- docs/03-機能仕様/acceptance-criteria.md` で移す前の履歴が出る。移動だけの commit は hook で止まるので、commit を分けずに例外にした。
- DC-279-6 の例外：`.claude/skills/docs/replace-paths.test.cjs` の 29、30、33、38、39、42、43、44、53 行（置き換えの入力の例）。
- 調べ方で見つからず、手動で修正したもの：`ac-check.mjs` が生成するリンク（`../specs/`）、`ac-check.test.mjs` の正規表現（`\.\.\/specs\/`）、`review/stats.cjs` の `path.join(..., 'specs', ...)`。
- 調べ方に当たるが、変えなかったテストの入力：`ac-check.test.mjs` の `../specs/016-master-data`（中身を読まずに渡すだけのデータ）。
- 完了条件のために加えたもの：
  - `scripts/ci/related-specs-exclusions.json` に `T279-01-02` を加えた。移した設計書が全て関連 spec の検査の対象になり、commit-msg の hook で止まるため（前例は T018-06）。
  - git の出力からパスを読む処理（`related-specs.mjs` の `--staged`、skill `docs` の 3 つの script、`review/checklist.cjs`、`scripts/ci/review/test-diff.mjs`）に、`-c core.quotepath=false` を付けた。付けないと日本語のパスが `"docs/\346..."` の形で出て、ファイルを読めないため。
- `core.quotepath` の手順は、[開発環境の起動](../../06-手順書/setup.md) 第 1 節に置いた。
- `./gradlew check`、`bash scripts/ci/check-all.sh`、`node scripts/ci/doc-refs.mjs`、フロントの検査（`check-frontend.sh e2e lint typecheck`）が通った。
