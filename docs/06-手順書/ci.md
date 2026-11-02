# CI の検査

CI のジョブと、CI の検査の script の一覧。CI の決まり（失敗したまま次の spec に進まない、など）は [constitution](../constitution.md)の「CI/CD」。

## 1. 手元での実行

| 検査 | コマンド（ルートで） | CI のジョブ |
|------|----------------------|-------------|
| バックエンド | `cd backend/ecsite && ./gradlew check` | バックエンド（ビルドと単体テスト）、バックエンド（統合テスト 1/2・2/2）、バックエンド |
| 画面（共有パッケージ・管理画面・店舗画面・E2E のコード） | `scripts/dev/check-frontend.sh`（ルートで 1 回の検査として、依存の再インストール、ライセンス、knip、組み込みの名前の一覧の確認も実行する） | 管理画面、店舗画面（共有パッケージを含む）、E2E（E2E のコードの lint・型チェック） |
| CI の検査の script | `scripts/ci/check-all.sh` | 文書と CI の検査の script |
| E2E | `scripts/dev/e2e.sh` | E2E |

- CI で失敗したら、まず個別のジョブ（バックエンド（ビルドと単体テスト）、バックエンド（統合テスト 1/2・2/2）、管理画面、店舗画面、文書と CI の検査の script）の結果を確認する。「バックエンド」は、他の 3 つのジョブ（ビルドと単体テスト、統合テスト 1/2・2/2）の結果を確認するジョブなので、先に他の 3 つを確認する。E2E は他のジョブを待たずに始まるため、個別のジョブで分かる失敗（コンパイルの誤りなど）でも E2E が失敗する。
- 手元のバックエンドのコマンドは、CI のようにジョブに分けず、単体テストと統合テストの全てを実行する。
- commit の hook が実行するのは、次の 3 つだけである。ステージした内容に対する `check-all.sh`（`ja-text` の error を含む）、追加行の `ja-text` の warning の表示、メッセージの `ja-text` の検査（[開発環境の起動](setup.md)の「起動」）。テストは時間がかかるため、変更した箇所に応じて手動で実行する（[テストの実行方法](running-tests.md)）。

## 2. CI のジョブ

| ワークフロー | ジョブ | 実行するもの | 実行する時 |
|--------------|--------|----------|----------|
| `ci.yml` | バックエンド（ビルドと単体テスト） | 依存のライセンスの検査、ビルド、Error Prone と NullAway（コンパイル時の静的解析）、Checkstyle（Javadoc の記載漏れは main だけ、private を含む。使われていない import、`*` の import、import の並びは全てのソースセット）、テストのコードの重複の検出（PMD の CPD。[テストの書き方](../05-開発規則/test-writing.md)の「テストのコードの重複」）、tools のソースのコンパイル、単体テスト、OpenAPI の構文の検証、domain の mutation testing（PIT）と mutation score の基準の検査 | push（下の除外のファイルだけの push を除く）と pull request |
| `ci.yml` | バックエンド（統合テスト 1/2・2/2） | 統合テストの半分ずつ（2 つのジョブ。統合テストのコンパイルでも Error Prone が動く） | 同上 |
| `ci.yml` | バックエンド | 上の 3 つのジョブの結果の確認、カバレッジの報告（要約）、カバレッジの基準の検査 | 同上 |
| `ci.yml` | 管理画面 | lint、型チェック、テスト、ビルド、カバレッジの要約、カバレッジの基準の検査 | 同上 |
| `ci.yml` | 店舗画面 | 画面の依存のライセンスの検査、使われていないファイル・export・依存の検査（knip。管理画面・店舗画面・共有パッケージ・E2E のコード全体を、ルートで 1 回）、組み込みの名前の一覧の確認（`scripts/ci/globals/lib-globals.mjs`。TypeScript の lib の名前と一致するか）、共有パッケージ（lint、型チェック、テスト、カバレッジの要約）、店舗画面（lint、型チェック、テスト、ビルド、カバレッジの要約）、店舗画面と共有パッケージのカバレッジの基準の検査 | 同上 |
| `ci.yml` | E2E | E2E のコードの lint・型チェック、バックエンド・管理画面・店舗画面を起動してブラウザーで操作する | 同上 |
| `docs.yml` | 文書と CI の検査の script | `scripts/ci/check-all.sh`（「CI の検査の script」）、Mermaid の図の描画の検査（`scripts/ci/mermaid/mermaid-check.mjs`。「Mermaid の図の描画の検査」）。後者は、前の step（`check-all.sh`）が失敗しても実行する | 全ての push と pull request（文書だけの push でも実行する） |
| `main-source.yml` | main への元のブランチ | `main` への pull request の元のブランチが `release-` か `hotfix-` で始まる（判定は `scripts/ci/workflow/main-source.mjs`。「CI の検査の script」）。`main` の ruleset の必須の検査（[ブランチとリリース](branches.md)の「`main` の元のブランチの制限」） | `main` への pull request |
| `risk.yml` | 危険度の照合 | pull request の本文の「## 危険度」の節の「判定：」の値を、`scripts/ci/risk.mjs` の判定（「CI の検査の script」）と比べ、本文が判定より低いとき、値が空・節がないときに失敗にする。判定は pull request の側と基準（`origin/develop`）の側の両方の `risk.mjs`・`risk.json` で行い、どちらかが失敗すれば失敗にする。Dependabot の pull request（作成者で見分ける）は比べず、題から読む更新の種類を入れた判定をジョブの要約に出すだけにする。本文を書き換えたときにも流すので、`ci.yml` とは別のワークフローにする（`edited` で全体の CI を流し直さないため） | `develop` への draft でない pull request（開いた、本文を書き換えた、commit を足した、開き直した、draft を外したとき）。draft の間はジョブを skip する |
| `pages.yml` | 機能紹介のページ | `showcase/` を GitHub Pages に公開する | `main` で `showcase/` が変わったとき、手動で起動したとき |

バックエンドのジョブの構成：

- ruleset の必須の検査は「バックエンド」の名前のままにする。
- 前のジョブ（ビルドと単体テスト、統合テスト 1/2・2/2 のジョブ）のどれかが失敗すると、「バックエンド」はスキップされる。必須の検査では、スキップが成功と同じに扱われる。
- そのため、前のジョブが失敗・スキップしても「バックエンド」を実行し、最初の手順で前のジョブの結果を確認して失敗にする。ワークフローが中止されたときは、「バックエンド」も中止になる（古い commit に失敗を残さない。中止も成功ではないので、必須の検査としては通らない）。
- 3 つのジョブ（ビルドと単体テスト、統合テスト 1/2・2/2）は同時に始まり、それぞれコンパイルする。コンパイルの結果を渡すと、統合テストのジョブがビルドを待ち、全体が長くなる。変わっていない部分は、Gradle のキャッシュから読む。
- 統合テストの分け方は、テストクラスの名前の hash で決まる（実行時間では決まらない）。クラスを追加しても設定を変えなくてよい。`./gradlew integrationTestShards` は、各ジョブに入るクラスの一覧を出す（Docker は不要）。一覧が示すのはクラスの数だけで、時間ではない。
- 統合テストのジョブの時間が偏ったときの目安は、2 つのジョブの差が 1 分 30 秒以下。差が大きいときは、`backend/ecsite/build.gradle.kts` の `shardOf` の salt を変えて分け直す。一覧で重複と漏れがないことを確認し、CI のジョブの時間で差を測る。
- 分ける数は、`ci.yml` の matrix の `shard` の一覧の長さだけで決まる（ジョブの名前と `-PintegrationTestShard=<番号>/<総数>` の総数は `strategy.job-total` から読む）。変えるときは、次を直す。
  - `ci.yml` の `shard` の一覧。
  - この文書の、ジョブの名前の「1/2・2/2」と「2 つのジョブ」「3 つのジョブ」の書き方（「手元での実行」の表、「CI のジョブ」）。
  - `backend/ecsite/build.gradle.kts` の `integrationTestShards` の既定の数（`integrationTestShardCount` の `orElse`）。
- 統合テストのフォークの数（`maxParallelForks`）は 1。CI で測って 10% 以上短くなれば、`-PintegrationTestForks=2` にする。根拠の実測は [CI の実測](../07-記録/ci-results.md)の「統合テストのフォークの数」。
- 「バックエンド」は、各ジョブが保存したカバレッジの実行データとクラスを集めて、1 つの報告にする（コンパイルもテストもしない）。各ジョブが別々にコンパイルしたクラスの実行データを合わせるので、クラスが一致しないと JaCoCo が警告を出し、「バックエンド」が注釈（warning）を出す。
- E2E は、ブラウザーの OS の依存を apt で入れない。ランナーのイメージに依存が入っているため。失敗時の画面の撮影では、日本語のフォントがなく文字が四角になることがある。読めないときは、手元の `scripts/dev/e2e.sh` で再現する。
- Node のバージョンは `.node-version`、JDK のバージョンは `ci.yml` の `JAVA_VERSION` と `libs.versions.toml` の `java` に書く。1 か所にできない組は、検査 `runtime-versions`（「CI の検査の script」）が一致を確認する。

CI の検査の script は、文書だけの変更でも結果が変わる ※1。そのため、文書だけの push でも実行する `docs.yml` で実行する。

※1：文書の参照と用語、AC 一覧、ロールと権限の対応表、進捗の表、用語集の識別子など。

`ci.yml` は、次のファイルだけを変えた push では実行しない：Markdown、`.claude/`、`scripts/dev/`、`showcase/`、`pages.yml`、`.github/ISSUE_TEMPLATE/`、`.github/dependabot.yml`、`.gitignore`、`LICENSE`。ただし、全体構成の文書（`docs/02-設計/overview.md`）は、バックエンドの単体テストが図と照合するので、Markdown でも実行する。モジュールの設計書（`docs/03-機能仕様/*.md`。同じフォルダーの AC 一覧と用語集だけを変えた push でも実行する）と、バックエンドの書き方（`docs/05-開発規則/backend-coding.md`）も、Markdown でも実行する。`ProductionClassesTest` が設計書のレベルをレベル A の一覧と照合し、`ModuleStructureTest` がパッケージの大きさの表を照合するため。また、`scripts/dev/lib/` は E2E の script から使う script の置き場所で、`scripts/dev/ports.json` はその `lib/slot.mjs` が読むポートの一覧なので、`scripts/dev/` の下でも実行する。pull request はいつも実行する。

## 3. CI の検査の script

`scripts/ci/check-all.sh` は、`scripts/ci/` と `scripts/dev/` の下の script のテスト（`*.test.mjs`）、skill の script のテスト（`.claude/skills/` の下の `*.test.cjs`）と、`scripts/ci/` の直下の検査（`*.test.mjs` 以外の `*.mjs`）を全て実行する。1 つでも失敗すれば、全てを実行したあとで終了コード 1 を返す。検査は、終了コードが 0 でなければ失敗で、終了コードが 0 でも、最後の行が要約の行（`<何>：<件数>`。「：」の後に数字がある行）でなければ失敗にする（判定は `lib/check-result.mjs`。終了コードが 0 でなければ、`check-all.sh` が node を呼ばずに直接失敗にする）。script のテストは `node --test` の出力なので、要約の行を求めず、終了コードと、出力のテストの数（`tests N`）が 1 件以上であることで判定する（glob が 1 件も一致しないと、`node --test` は 0 件で終了コード 0 になるため）。
直下の検査の他に、`ja-text` を `--changed` で実行する（表の `ja-text`（`--changed`）の行）。分岐点を求めるので、shallow clone では失敗する。

- 検査を追加するときは、次の手順で作る。
  - 引数なしで実行できる `.mjs` を直下に置き、テストを `*.test.mjs` に書く。`check-all.sh`、CI、hook は修正しなくてよい。
  - 処理は `if (isMain(import.meta.url)) { … }`（`lib/is-main.mjs`）の中に書き、テストが import しても動かないようにする。
  - 違反は標準出力に出し、最後の行に要約の行（`<何>：<件数>`。例：`切れた参照：0`）を出して、違反があれば終了コード 1 にする。
  - 先頭のコメント（1 行目から `//`）に、確認すること、失敗したとき、除外を書く。
  - この節の表に行を加える（`ci-docs` の検査が、表と直下の script の対応を確認する）。
  - Git の一覧は `listGitFiles`（`lib/git-files.mjs`）で求め、`git ls-files` を直接呼ばない。対象が 0 件のとき `exitIfNoFiles`（文は `emptyListReason`）で失敗にする。0 件で失敗にすることは、`lib/git-files.test.mjs` が `scripts/ci/` の直下の検査を走査して確認する。
  - 対応していない書き方の行は、読み飛ばさずに `describeUnreadableLine`（`lib/unreadable-line.mjs`）で `ファイル:行 内容（理由）` の形にして出し、失敗にする。
  - 除外の一覧を持つなら、パスを `exclusionsPath`、誤りの文を `checkExclusions`（`lib/exclusions.mjs`）で作る。
  - 画面のソースを読むなら `lib/screen-sources.mjs` を使う。行番号が必要なら `lib/line-number.mjs` を使う。
- 次の補助の script は、各ディレクトリに置く。`check-all.sh` は、これらの script のテストだけを実行し、script 自体は実行しない。
  - `report/` の下の `coverage-summary.mjs`、`coverage-baseline.mjs`、`mutation-report.mjs`：引数が必要（「カバレッジと mutation score の基準の検査」）。
  - `licenses/`：画面の依存が必要な検査。
  - `globals/` の下の `lib-globals.mjs`：TypeScript の依存が必要な、組み込みの名前の一覧の生成と確認（一覧の `lib-globals.json` を `frontend-rules` が読む。`frontend-rules.mjs` の先頭のコメント）。
  - `workflow/` の下の `main-source.mjs`：workflow から呼ぶ判定（元のブランチの名前が引数）。
  - `review/` の下の `test-diff.mjs`：レビューで手動で使う補助（[テストの実行方法](running-tests.md)の「既存のテストを変えていないことの確認」）。
  - `mermaid/` の下の `mermaid-check.mjs`：Chrome とネットワークが必要な Mermaid の図の描画の検査（「Mermaid の図の描画の検査」）。
- 複数の検査が使う関数は `lib/` に置き（テストは `*.test.mjs`）、役割は各ファイルの先頭のコメントに書く。画面のソースを読む検査は `screen-sources.mjs` の定義を使い、違う対象を読む検査は理由を書く。

各検査の詳しい説明（確認すること、失敗したときの修正の手順、除外の書き方）は、script の先頭のコメントにある。

| script | 目的 |
|--------|------|
| `ac-check` | 進行中の spec の AC が AC 一覧にあり、③ の「今有効な AC」の表が AC 一覧から作った表と一致し、有効・変更の AC がテスト名（@DisplayName、test・it の第 1 引数）に出ることを確認する（テスト名の確認は CI でだけ失敗）。アーカイブの spec は読まない |
| `ci-docs` | この表と検査の 1 対 1 の対応、表の 2 列、検査と `lib/` の 1 行目のコメント、検査の先頭のコメントの「失敗したとき：」と「除外：」の行を確認する |
| `compose-ports` | compose の全ての ports が、既定でこのマシンだけで待ち受ける `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` で始まることを確認する |
| `dependabot-actions` | 複合の action を置いたディレクトリが、Dependabot の github-actions の directories に入っていることを確認する |
| `doc-refs` | Markdown のリンクと見出しの参照の先が存在し、`docs/` の下の文書へのリンクテキストが文書の名前で、節の番号の参照（第 N 節）が残っていないことを確認する |
| `docs-folders` | docs/ の下の全てのファイルが、constitution.md か許可したフォルダーの下にあることを確認する |
| `ja-text` | 日本語の文に使わない語（error）がないことを確認し、文脈で決める語と長い文などの文体の型を warning として示す。 |
| `ja-text`（`--changed`） | `develop` から分けた後に追加した行のうち、閾値以上の文（字数・読点の数）と表のセル（字数の合計）を、注釈の形で示す（警告だけで失敗にしない）。 |
| `env-defaults` | 環境変数の文書の既定値が、設定の型の @DefaultValue と一致し、管理画面の IdP の既定値が置き場所の間でそろうことを確認する |
| `frontend-rules` | フロントとテストの書き方の規則のうち、ソースの文字で確認できるもの（機能間の import、ロケール、重複、E2E の選び方など）を確認する |
| `glossary-ids` | 用語集の識別子の列の名前が、コードかファイルのパスにあることを確認する |
| `gradle-plugins` | Gradle のプラグインの id が、policy.json の gradlePlugins と過不足なく一致することを確認する |
| `hardcoded-text` | 画面のコードに、表示の文言（日本語の文字）を直接書いていないことを確認する。 |
| `image-versions` | compose と、Gradle・統合テストのイメージのバージョンが一致し、patch まで固定されていることを確認する。 |
| `input-constraints` | 画面の入力の制約（長さ・範囲）に、数字を直接書かず、API の契約から書き出した定数を使っていることを確認する。 |
| `integration-shards` | 統合テストを分ける数が、手元の既定（`build.gradle.kts`）と CI の matrix の `shard` の一覧で一致することを確認する。 |
| `java-rules` | バックエンドのソースで、Lombok の使用、列挙型の `name()` 変換と文字列からの変換、JSpecify 以外の `Nullable`、理由のない `@SuppressWarnings` がないことを確認する。 |
| `license-policy` | 許可するライセンスの一覧が、`licenses.md` の許可の行と `policy.json` の `allowed` で過不足なく一致することを確認する。 |
| `local-ports` | ローカルのポートの一覧（`ports.json`）と、compose・Windows の予約の script・文書・設定・realm のポートが食い違わないことを確認する。 |
| `main-entry` | script が、直接実行されたかの判定を共通の関数 `isMain(import.meta.url)` だけで行うことを確認する。 |
| `message-comments` | 画面のソースで、文言をキーで引く呼び出し（`t('…')`、`translate('…')`）に、表示する内容のコメントがあることを確認する。 |
| `message-numbers` | バックエンドのエラーの文に、差し込み以外の数字を直接書いていないことを確認する。 |
| `migrations` | 適用済みのマイグレーションを追加後に変えていないことと、新しいマイグレーションが前のリリースのアプリを停止させる変更を含まないことを確認する。 |
| `note-numbers` | 文書の注の番号が、1 つの文書の中で重複せず、出る順に並ぶことを確認する。 |
| `progress` | 1→10 のアーカイブの spec の tasks が、全て済んでいることを確認する。 |
| `risk` | 変更の危険度（高か低）を、危険度が高い経路と spec の宣言から判定し、pull request の本文の判定と比べる。 |
| `role-permissions` | ロールと権限の対応表が、API の契約と運営者の realm の設定から再作成した表と一致することを確認する。表を Git に置くのは、エンジニアが読む設計の文書で、契約と realm の変更でしか変わらないため。 |
| `runtime-versions` | JDK と Node のバージョンが置き場所の間で一致し、workflow が直接書かずにそれを参照していることを確認する。 |
| `service-names` | 規則の本文に、AI のサービス固有の名前がない（コードスパンのパス、コードブロック、リンク先を除く）ことを確認する |
| `test-helpers` | 統合テストが、テストの補助だけが呼んでよい操作を、補助の外で呼んでいないことを確認する。 |
| `spec-numbers` | 本番のコードと、テストと設定のファイルと `scripts/` の下の script の、それぞれのコメントに、spec・plan・tasks の番号が書かれていないことを確認する。 |
| `spec-structure` | spec・plan・tasks と機能仕様について、構造（節の名前と順）と ID の定義・参照を確認する。失敗したら、文書を雛形 `docs/08-spec/templates/` に合わせる。判定の区分は誤りの防止（誤って書いたものを失敗にする）。 |
| `test-names` | バックエンドの全てのテストに、表示の名前（`@DisplayName`）があることを確認する。 |
| `time-waits` | テストが、決まった時間だけ待つ書き方をしていないことを確認する。 |

### 3.1 カバレッジと mutation score の基準の検査

テストの量と強さが下がったときに CI を失敗にする検査（[テストの書き方](../05-開発規則/test-writing.md)の「カバレッジと mutation score の基準」）。`scripts/ci/report/` の下の script と基準のファイルで行う。

| ファイル | 役割 |
|----------|------|
| `coverage-summary.mjs` | カバレッジの合計と、行のカバレッジが低い順の 10 件を、job の summary に書く。失敗にはしない |
| `coverage-baseline.mjs` | 報告の値を基準の値と比べ、下回ると終了コード 1 で失敗にする。上回ったときは、基準の更新を促す警告を表示する。`--write` で基準のファイルを書き換える |
| `mutation-report.mjs` | PIT の報告（`mutations.xml`）から mutation score と、生き残った変異が多いクラスを読む（`coverage-baseline.mjs` が使う） |
| `quality-baseline.json` | 基準の値と許容の幅（`tolerance`）。対象は、行と分岐を持つ `backend`、`admin`、`store`、`shared` と、mutation score を持つ `backend-domain` |

- 検査の手順は、報告を保存した後に置く（失敗しても報告が残る）。位置は、管理画面、店舗画面、「バックエンド」のジョブでは最後。「バックエンド（ビルドと単体テスト）」のジョブでは、失敗時のテストレポートの保存の前。店舗画面のジョブは、店舗画面と共有パッケージの 2 つの手順で検査する。
- 基準は対象ごとの合計で、ファイルごとの基準はない。
- 失敗したときは、ログと summary に出る対象、基準、今回の値、差を確認し、テストを追加して値を戻す。正当な理由で下げるときは、手元で `--write` を実行して基準のファイルを更新する。更新したファイルは同じ pull request に含め、pull request に理由を書く。手元の実行は [テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」。
- バックエンドのカバレッジは、統合テストの同時実行の競合があるときだけ実行される分岐があり、実行ごとにばらつく。そのため `backend` の許容の幅は、行 0.10、分岐 0.30 にしている。根拠の実測は [CI の実測](../07-記録/ci-results.md)の「バックエンドのカバレッジのばらつき」。
- PIT は、ビルドと単体テストのジョブの、単体テストの後で実行する（`./gradlew pitest`。`check` には含まれない）。変異の前に全ての単体テストを 1 回実行してカバレッジを取るので、そのジョブの時間が増える。報告は artifact `pitest-backend` に保存する。
- このジョブは、統合テストの遅い方のジョブより早く終わるので、`ci.yml` の全体の時間は遅い方のジョブで決まる。根拠の実測は [CI の実測](../07-記録/ci-results.md)の「PIT とジョブの時間」。

### 3.2 Mermaid の図の描画の検査

文書の Mermaid の図が描画できることを確認する検査。`scripts/ci/mermaid/mermaid-check.mjs` が、Markdown のコードブロックから図を取り出し、mermaid-cli で描画する。描画できない図があれば失敗にする。取り出した図が 0 件のときも、検査が何も見ずに成功するのを防ぐため、失敗にする。

- 対象の文書は、Git で扱う全ての `.md` である（アーカイブ `docs/08-spec/archive/` を除く）。GitHub は、置き場所によらず図を描画するため。
- 次の図は描画しないが、見つけたら失敗にする。
  - 箇条書き・引用・4 個以上の字下げの中の図（対象にしない書き方の図）
  - 他のコードブロックの中の、図の開きの形の行（前のコードブロックが閉じていない疑い）。`markdown`・`md` のコードブロックの中は、囲みの字が外側と違うか外側より短い行（雛形の骨組み）を除く
- 実行するのは、CI の `docs.yml` だけである。描画に Chrome とネットワーク（mermaid-cli の取得）が必要で遅いため、`check-all.sh` と commit の前の hook では実行しない。
- script は `scripts/ci/` の直下に置かない（直下の検査は `check-all.sh` が全て実行する）。script のテストは、他の script のテストと同じく `check-all.sh` が実行する。
- 手元では、ルートで `node scripts/ci/mermaid/mermaid-check.mjs` を実行する。Chrome の場所は、環境変数 `MERMAID_CHROME` で指定する。指定しないときは、既定の場所（Windows の Chrome、macOS の Chrome、`/usr/bin/google-chrome`、chromium）を順に探す。
- CI のランナー（ubuntu-latest）には Google Chrome が `/usr/bin/google-chrome` に入っているので、`MERMAID_CHROME` は指定しない。
- 失敗したときは、出力の図の位置（文書と行）と描画のエラーを確認し、図の構文を修正する。対象にしない書き方の図は、字下げしないコードブロックに移す。閉じていない疑いは、前のコードブロックの閉じの行を加える。

## 4. GitHub のセキュリティの機能

リポジトリの設定（Settings の Code security）で有効にする。CI のジョブではないため、CI の実行時間に含まれない。

| 機能 | 見るもの | 結果の場所 | 対応 |
|------|----------|------------|------|
| Dependabot alerts | 依存ライブラリ（Gradle、pnpm、GitHub Actions）の既知の脆弱性 | Security のタブの Dependabot | 直ったバージョンに更新する（バックエンドは `libs.versions.toml`、画面は `pnpm`）。更新できないときは、影響がない理由を書いて閉じる |
| secret scanning、push protection | リポジトリに入った秘密情報（API キー、token など）。push protection は、秘密情報を含む push を拒否する | Security のタブの Secret scanning | 秘密情報を無効にして再発行し、履歴から削除する。テスト用の値など誤検出なら、理由を付けて閉じる |
| CodeQL（default setup） | Java と JavaScript・TypeScript のコードの、セキュリティの問題（注入、パスの操作など） | Security のタブの Code scanning | 修正するか、誤検出・影響がない理由を付けて閉じる |

- Dependabot の設定は [ブランチとリリース](branches.md)の「Dependabot」。
