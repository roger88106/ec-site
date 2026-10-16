# CI の検査

CI のジョブと、CI の検査の script の一覧。CI の決まり（失敗したまま次の spec に進まない、など）は [constitution](../constitution.md) 第 12 節。

## 1. 手元で流す

| 検査 | コマンド（ルートで） | CI のジョブ |
|------|----------------------|-------------|
| バックエンド | `cd backend/ecsite && ./gradlew checkLicense build` | バックエンド |
| 画面（管理画面・店舗画面・E2E のコード） | `scripts/dev/check-frontend.sh` | 管理画面、店舗画面、E2E の lint・型チェック |
| CI の検査の script | `scripts/ci/check-all.sh` | CI の検査の script、文書 |
| E2E | `scripts/dev/e2e.sh` | E2E |

- CI で失敗したら、まず各部分のジョブ（バックエンド、管理画面、店舗画面、CI の検査の script）の結果を見る。E2E はほかのジョブを待たずに始まるので、部分のジョブで分かる失敗（コンパイルの誤りなど）でも E2E が失敗する。
- commit の前の hook は `check-all.sh` だけを流す（[開発環境の起動](setup.md)）。テストは時間がかかるので、変えた所に応じて手で流す（[テストの流し方](../testing/running.md)）。

## 2. CI のジョブ

| ワークフロー | ジョブ | 流すもの | 流れる時 |
|--------------|--------|----------|----------|
| `ci.yml` | バックエンド | 依存のライセンスの検査、ビルド、Javadoc の書き漏れの検査（Checkstyle）、単体・統合テスト、OpenAPI の構文の検証、カバレッジの要約 | push（下の除外のファイルだけの push を除く）と pull request |
| `ci.yml` | 管理画面 | lint、型チェック、テスト、ビルド、カバレッジの要約 | 同上 |
| `ci.yml` | 店舗画面 | 画面の依存のライセンスの検査、共有のパッケージの lint・型チェック・テスト、店舗画面の lint、型チェック、テスト、ビルド、カバレッジの要約 | 同上 |
| `ci.yml` | CI の検査の script | `scripts/ci/check-all.sh`（第 3 節） | 同上 |
| `ci.yml` | E2E | E2E のコードの lint・型チェック、バックエンド・管理画面・店舗画面を起動してブラウザで操作する | 同上 |
| `docs.yml` | 文書とコメントの検査 | `doc-refs`、`spec-numbers`、`doc-terms` | すべての push と pull request（文書だけの push でも流す） |
| `pages.yml` | 機能紹介のページ | `showcase/` を GitHub Pages に出す | `develop` で `showcase/` が変わったとき、手で起動したとき |

`ci.yml` は、次のファイルだけを変えた push では流さない：Markdown、`.claude/`、`scripts/dev/`、`showcase/`、`pages.yml`、`.gitignore`、`LICENSE`。pull request はいつも流す。

## 3. CI の検査の script

`scripts/ci/check-all.sh` は、`scripts/ci/` の下の script のテスト（`*.test.mjs`）と、`scripts/ci/` の直下の検査（`*.test.mjs` 以外の `*.mjs`）をすべて流す。

- 検査を足すときは、引数なしで流せる `.mjs` を直下に置き、テストを `*.test.mjs` に書く。`check-all.sh`、CI、hook は直さなくてよい。
- 引数の要る補助（`report/coverage-summary.mjs`）と、画面の依存が要る検査（`licenses/`）は、下のディレクトリに置く。

| script | 確かめること | 失敗したとき | 除外の書き方 |
|--------|--------------|--------------|--------------|
| `ac-check` | 機能の spec のすべての AC が、テスト名（メソッドの `@DisplayName`、`test`・`it` の第 1 引数）に 1 つずつ出る（[テストの書き方](../testing/writing.md) 第 2.1 節） | テストを足すか、テスト名に AC を書く。範囲（`AC-8〜10`）でなく 1 つずつ書く | `ac-exclusions.json` に AC と理由 |
| `doc-refs` | Markdown のリンクと、`<文書の名前> 第 N 節` の参照が切れていない | リンクか節の番号を直す（skill `docs`） | なし |
| `doc-terms` | 文書に使わない語（`doc-terms.json` の ng）がない | 言い換える。`--caution` で文脈で決める語も出る | `doc-terms.json` |
| `hardcoded-text` | 画面のコードに表示の文言を直接書いていない（[フロントの書き方](../frontend/coding.md) 第 7.1 節） | 文言を `messages/ja.json` に置き、キーで引く。`--count` で数だけを出す | `hardcoded-text-exclusions.json` にファイルと理由 |
| `input-constraints` | 画面の入力の制約に数字を直接書いていない（[フロントの書き方](../frontend/coding.md) 第 2.3 節） | API の契約から書き出した定数を使う | なし |
| `spec-numbers` | 本番のコードのコメントに spec・plan・tasks の番号がない（[コメントの書き方](../process/comments.md) 第 1 節） | 番号を消し、規則を言葉で書く。spec との対応はテスト名に書く | なし |
| `time-waits` | テストが時間で待っていない（[テストの書き方](../testing/writing.md) 第 5 節） | 条件で待つ | なし |
