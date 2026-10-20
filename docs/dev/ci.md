# CI の検査

CI のジョブと、CI の検査の script の一覧。CI の決まり（失敗したまま次の spec に進まない、など）は [constitution](../constitution.md) 第 12 節。

## 1. 手元で流す

| 検査 | コマンド（ルートで） | CI のジョブ |
|------|----------------------|-------------|
| バックエンド | `cd backend/ecsite && ./gradlew checkLicense build` | バックエンド |
| 画面（共有パッケージ・管理画面・店舗画面・E2E のコード） | `scripts/dev/check-frontend.sh` | 共有パッケージ、管理画面、店舗画面、E2E の lint・型チェック |
| CI の検査の script | `scripts/ci/check-all.sh` | CI の検査の script、文書 |
| E2E | `scripts/dev/e2e.sh` | E2E |

- CI で失敗したら、まず各部分のジョブ（バックエンド、管理画面、店舗画面、CI の検査の script）の結果を見る。E2E はほかのジョブを待たずに始まるので、部分のジョブで分かる失敗（コンパイルの誤りなど）でも E2E が失敗する。
- commit の前の hook は `check-all.sh` だけを流す（[開発環境の起動](setup.md)）。テストは時間がかかるので、変えた所に応じて手で流す（[テストの流し方](../testing/running.md)）。

## 2. CI のジョブ

| ワークフロー | ジョブ | 流すもの | 流れる時 |
|--------------|--------|----------|----------|
| `ci.yml` | バックエンド | 依存のライセンスの検査、ビルド、Javadoc の書き漏れの検査（Checkstyle。private を含む）、単体・統合テスト、OpenAPI の構文の検証、カバレッジの要約 | push（下の除外のファイルだけの push を除く）と pull request |
| `ci.yml` | 管理画面 | lint、型チェック、テスト、ビルド、カバレッジの要約 | 同上 |
| `ci.yml` | 店舗画面 | 画面の依存のライセンスの検査、共有のパッケージの lint・型チェック・テスト、店舗画面の lint、型チェック、テスト、ビルド、カバレッジの要約 | 同上 |
| `ci.yml` | CI の検査の script | `scripts/ci/check-all.sh`（第 3 節） | 同上 |
| `ci.yml` | E2E | E2E のコードの lint・型チェック、バックエンド・管理画面・店舗画面を起動してブラウザで操作する | 同上 |
| `docs.yml` | 文書とコメントの検査 | `doc-refs`、`spec-numbers`、`doc-terms` | すべての push と pull request（文書だけの push でも流す） |
| `pages.yml` | 機能紹介のページ | `showcase/` を GitHub Pages に出す | `develop` で `showcase/` が変わったとき、手で起動したとき |

文書の検査（`doc-refs`、`spec-numbers`、`doc-terms`）は、`docs.yml` と `ci.yml` の CI の検査の script の両方で流れる。`docs.yml` は文書だけの push でも流すため、`check-all.sh` は手元と CI で同じものを流すために持つ。

`ci.yml` は、次のファイルだけを変えた push では流さない：Markdown、`.claude/`、`scripts/dev/`、`showcase/`、`pages.yml`、`.gitignore`、`LICENSE`。pull request はいつも流す。

## 3. CI の検査の script

`scripts/ci/check-all.sh` は、`scripts/ci/` の下の script のテスト（`*.test.mjs`）と、`scripts/ci/` の直下の検査（`*.test.mjs` 以外の `*.mjs`）をすべて流す。

- 検査を足すときは、引数なしで流せる `.mjs` を直下に置き、テストを `*.test.mjs` に書く。`check-all.sh`、CI、hook は直さなくてよい。
- 引数の要る補助（`report/coverage-summary.mjs`）と、画面の依存が要る検査（`licenses/`）は、下のディレクトリに置く。

| script | 確かめること | 失敗したとき | 除外の書き方 |
|--------|--------------|--------------|--------------|
| `ac-check` | 機能の spec のすべての AC が、テスト名（メソッドの `@DisplayName`、`test`・`it` の第 1 引数）に 1 つずつ出る（[テストの書き方](../testing/writing.md) 第 2.1 節）※1。[AC 一覧](../design/acceptance-criteria.md)を読み、廃止の AC はテストを求めず、テストが参照すると失敗にする。spec と一覧の AC の食い違い、決まっていない状態、古い表も失敗にする | テストを足すか、テスト名に AC を書く。範囲（`AC-8〜10`）でなく 1 つずつ書く。表が古いときは `node scripts/ci/ac-check.mjs --write` で書き直す | `ac-exclusions.json` に AC と理由 |
| `compose-ports` | compose のすべての `ports` が、待ち受けるアドレスを `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` で始める（既定でこのマシンだけで待ち受け、1 つの変数で替えられる。[開発環境の起動](setup.md) 第 2 節） | ポートの前に `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` を書く | なし |
| `doc-refs` | Markdown のリンクと、`<文書の名前> 第 N 節` の参照が切れていない | リンクか節の番号を直す（skill `docs`） | なし |
| `doc-terms` | 文書に使わない語（`doc-terms.json` の ng）がない | 言い換える。`--caution` で文脈で決める語も出る | `doc-terms.json` |
| `env-defaults` | [環境変数](environment.md) の表の「既定」が、設定の型（`@ConfigurationProperties` の record）の `@DefaultValue` と一致する。コードの既定値は文書にあり、文書に書いた既定値（時間、数、真偽）はコードにもある（[バックエンドの書き方](../backend/coding.md) 第 6.1 節）。時間は書き方によらず長さで比べる | 文書の表の行か値を直す。省略形（`_EXPIRY_INTERVAL`）は、前の名前の後ろを置き換えた名前として読む | なし |
| `frontend-rules` | 機能（`features/<機能>/`）が、ほかの機能を import しない（[フロントの書き方](../frontend/coding.md) 第 1.2 節）。ロケールとタイムゾーンを `frontend/shared/src/lib/locale.ts` のほかに直接書かない（[フロントの書き方](../frontend/coding.md) 第 7.3 節）。店舗画面と管理画面に、同じ相対パスで同じ内容のファイルがない（[フロントの書き方](../frontend/coding.md) 第 5.1 節） | 2 つの機能で使うものは `components/` か `lib/` に置く。ロケールは `LOCALE`・`TIME_ZONE` を使う。同じファイルは共有パッケージに移す | `frontend-rules-exclusions.json` に src からの相対パスと理由 |
| `glossary-ids` | [用語集](../reference/glossary.md) の識別子の列の名前が、コード（`backend/`、`frontend/`、`api/`、`docker/`、`e2e/`、`scripts/`）かファイルのパスにある。kebab-case の設定の名前は camelCase の語でもよい。名前の形の例（`Xxx`、`<…>`、`*`）は見ない | 用語集の識別子を、今のコードの名前に直す | `glossary-ids-exclusions.json` に名前と理由 |
| `hardcoded-text` | 画面のコードに表示の文言を直接書いていない（[フロントの書き方](../frontend/coding.md) 第 7.1 節） | 文言を `messages/ja.json` に置き、キーで引く。`--count` で数だけを出す | `hardcoded-text-exclusions.json` にファイルと理由 |
| `input-constraints` | 画面の入力の制約に数字を直接書いていない（[フロントの書き方](../frontend/coding.md) 第 2.3 節） | API の契約から書き出した定数を使う | なし |
| `java-rules` | テストのソース（`test`・`integrationTest`・`testFixtures`）に Lombok がない（[バックエンドの書き方](../backend/coding.md) 第 5.1 節）。本番のソースで、列挙型の変換に `name()` を渡していない（[バックエンドの書き方](../backend/coding.md) 第 1.3 節） | テストでは定型コードを手で書く。列挙型は `switch` 式で写す | なし |
| `message-comments` | 画面のコードで、キーを文字で書いた文言の呼び出し（`t('…')`）に、表示する内容のコメントがある（[コメントの書き方](../process/comments.md) 第 6.1 節）。キーを組み立てる呼び出しは見ない | 呼び出しの後に `/* 表示する内容 */` か `// 表示する内容` を書く | なし |
| `message-numbers` | バックエンドのエラーの文（`messages.properties`。メールの文は除く）に、差し込み（`{max}` など）のほかの数字がない（[バックエンドの書き方](../backend/coding.md) 第 3.2 節）。除外の一覧の、要らなくなったキーも失敗にする | 値をドメインの定数から `{名前}` で差し込む | `message-numbers-exclusions.json` にキーと理由（「1 つだけ」など数え方の言葉で、規則の値でないもの） |
| `progress` | spec 011 以降の進捗が、README の進捗の表、MVP の完成までの一覧、tasks のチェックボックスで一致する（tasks がすべて済んだ spec だけが「完了」。2 つの表の状態が同じ） | 最後のタスクを済ませる commit で、2 つの表の状態を「完了」にする | なし |
| `related-specs` | 設計の文書（概要の表に「関連 spec」の行を持つもの）を変えた commit の spec（1 行目の `（T0NN-…）`）が、その行にある。commit の履歴を読むので、shallow clone では失敗する | 概要の表の「関連 spec」に、その spec のリンクを足す | `related-specs-exclusions.json` に、設計を変えない段階のタスク番号の前置き（`T018-05` など）と理由 |
| `role-permissions` | [ロールと権限の対応表](../design/role-permissions.md)が、管理 API の契約の `x-required-permission`、運営者の realm のロール、店舗 API の契約の `security` から作り直した表と一致する。管理 API の操作に権限がない、realm にない権限を使う、も失敗にする | ルートで `node scripts/ci/role-permissions.mjs --write` を流して表を書き直し、差分を確かめる | なし |
| `spec-numbers` | 本番のコードのコメントに spec・plan・tasks の番号がない（[コメントの書き方](../process/comments.md) 第 1 節） | 番号を消し、規則を言葉で書く。spec との対応はテスト名に書く | なし |
| `test-names` | バックエンドのすべての `@Test`・`@ParameterizedTest` に `@DisplayName` がある（[テストの書き方](../testing/writing.md) 第 2.2 節） | 振る舞いを書いた名前を付ける | なし |
| `time-waits` | テストが時間で待っていない。決まった時間だけ見る書き方（Awaitility の `during`・`pollDelay`、Promise で包んだ `setTimeout`）も見る（[テストの書き方](../testing/writing.md) 第 5 節） | 条件で待つ。バックエンドの「起きないこと」は `AsyncEvents` で待つ | なし |

※1：テスト名に出ない AC は、CI（環境変数 `CI=true`）でだけ失敗にし、手元（hook を含む）では警告にする。`check-all.sh` は、通った検査の警告（`警告：` で始まる行）も出す。spec を書いてからテストを書くまでの間も commit できるようにし、テストの漏れは CI で止めるため。手元で CI と同じに確かめるときは `CI=true scripts/ci/check-all.sh`。他の誤り（範囲の表記、spec にない AC 番号、除外の一覧の誤り）は、手元でも失敗にする。

## 4. GitHub のセキュリティの機能

リポジトリの設定（Settings の Code security）で有効にする。CI のジョブではないので、CI の時間に入らない。

| 機能 | 見るもの | 結果の場所 | 対応 |
|------|----------|------------|------|
| Dependabot alerts | 依存ライブラリ（Gradle、pnpm、GitHub Actions）の既知の脆弱性 | Security のタブの Dependabot | 直った版に上げる（バックエンドは `libs.versions.toml`、画面は `pnpm`）。上げられないときは、影響がない理由を書いて閉じる |
| secret scanning、push protection | リポジトリに入った秘密情報（API キー、token など）。push protection は、秘密情報を含む push を拒否する | Security のタブの Secret scanning | 秘密情報を無効にして作り直し、履歴から消す。テスト用の値など誤検出なら、理由を付けて閉じる |
| CodeQL（default setup） | Java と JavaScript・TypeScript のコードの、セキュリティの問題（注入、パスの操作など） | Security のタブの Code scanning | 直すか、誤検出・影響がない理由を付けて閉じる |

- 依存の版を自動で上げる pull request（Dependabot の version updates と security updates）は使っていない。ブランチの運用と CD を決めるときに合わせて決める（[TODO 一覧](../todo.md)）。
