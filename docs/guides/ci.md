# CI の検査

CI のジョブと、CI の検査の script の一覧。CI の決まり（失敗したまま次の spec に進まない、など）は [constitution](../constitution.md) 第 12 節。

## 1. 手元での実行

| 検査 | コマンド（ルートで） | CI のジョブ |
|------|----------------------|-------------|
| バックエンド | `cd backend/ecsite && ./gradlew check` | バックエンド（ビルドと単体テスト）、バックエンド（統合テスト 1/2・2/2）、バックエンド |
| 画面（共有パッケージ・管理画面・店舗画面・E2E のコード） | `scripts/dev/check-frontend.sh` | 管理画面、店舗画面（共有パッケージを含む）、E2E（E2E のコードの lint・型チェック） |
| CI の検査の script | `scripts/ci/check-all.sh` | 文書と CI の検査の script |
| E2E | `scripts/dev/e2e.sh` | E2E |

- CI で失敗したら、まず個別のジョブ（バックエンド（ビルドと単体テスト）、バックエンド（統合テスト 1/2・2/2）、管理画面、店舗画面、文書と CI の検査の script）の結果を確認する。「バックエンド」は、他の 3 つのジョブ（ビルドと単体テスト、統合テスト 1/2・2/2）の結果を確認するジョブなので、先に他の 3 つを確認する。E2E は他のジョブを待たずに始まるため、個別のジョブで分かる失敗（コンパイルの誤りなど）でも E2E が失敗する。
- 手元のバックエンドのコマンドは、CI のようにジョブに分けず、単体テストと統合テストの全てを実行する。
- commit の hook は、ステージした内容に対する `check-all.sh` と、次の commit の関連 spec の照合だけを実行する（[開発環境の起動](setup.md) 第 2 節）。テストは時間がかかるため、変更した箇所に応じて手動で実行する（[テストの実行方法](running-tests.md)）。

## 2. CI のジョブ

| ワークフロー | ジョブ | 実行するもの | 実行する時 |
|--------------|--------|----------|----------|
| `ci.yml` | バックエンド（ビルドと単体テスト） | 依存のライセンスの検査、ビルド、Error Prone と NullAway（コンパイルの時の静的解析）、Checkstyle（Javadoc の記述漏れの検査は main だけで private を含む。使われていない import と import の並びの検査は全てのソースセット）、tools のソースのコンパイル、単体テスト、OpenAPI の構文の検証、domain の mutation testing（PIT）と mutation score の基準の検査 | push（下の除外のファイルだけの push を除く）と pull request |
| `ci.yml` | バックエンド（統合テスト 1/2・2/2） | 統合テストの半分ずつ（2 つのジョブ。統合テストのコンパイルでも Error Prone が動く） | 同上 |
| `ci.yml` | バックエンド | 上の 3 つのジョブの結果の確認、カバレッジの報告（要約）、カバレッジの基準の検査 | 同上 |
| `ci.yml` | 管理画面 | lint、型チェック、テスト、ビルド、カバレッジの要約、カバレッジの基準の検査 | 同上 |
| `ci.yml` | 店舗画面 | 画面の依存のライセンスの検査、共有パッケージ（lint、型チェック、テスト、カバレッジの要約）、店舗画面（lint、型チェック、テスト、ビルド、カバレッジの要約）、店舗画面と共有パッケージのカバレッジの基準の検査 | 同上 |
| `ci.yml` | E2E | E2E のコードの lint・型チェック、バックエンド・管理画面・店舗画面を起動してブラウザーで操作する | 同上 |
| `docs.yml` | 文書と CI の検査の script | `scripts/ci/check-all.sh`（第 3 節） | 全ての push と pull request（文書だけの push でも実行する） |
| `main-source.yml` | main への元のブランチ | `main` への pull request の元のブランチが `release-` か `hotfix-` で始まる（判定は `scripts/ci/workflow/main-source.mjs`。第 3 節）。`main` の ruleset の必須の検査（[ブランチとリリース](branches.md) 第 3.2 節） | `main` への pull request |
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
  - この文書の、ジョブの名前の「1/2・2/2」と「2 つのジョブ」「3 つのジョブ」の書き方（第 1 節の表、第 2 節）。
  - `backend/ecsite/build.gradle.kts` の `integrationTestShards` の既定の数（`integrationTestShardCount` の `orElse`）。
- 統合テストのフォークの数（`maxParallelForks`）は 1。2 にしても 10% 以上は短くならなかった（遅い方のジョブで約 2%）。CI で測って 10% 以上短くなれば、`-PintegrationTestForks=2` にする。
- 「バックエンド」は、各ジョブが保存したカバレッジの実行データとクラスを集めて、1 つの報告にする（コンパイルもテストもしない）。各ジョブが別々にコンパイルしたクラスの実行データを合わせるので、クラスが一致しないと JaCoCo が警告を出し、「バックエンド」が注釈（warning）を出す。
- E2E は、ブラウザーの OS の依存を apt で入れない。ランナーのイメージに依存が入っていて、apt の取得に数分から 30 分かかることがあったため。失敗時の画面の撮影では、日本語のフォントがなく文字が四角になることがある。読めないときは、手元の `scripts/dev/e2e.sh` で再現する。
- Node のバージョンは `.node-version`、JDK のバージョンは `ci.yml` の `JAVA_VERSION` と `libs.versions.toml` の `java` に書く。1 か所にできない組は、検査 `runtime-versions`（第 3 節）が一致を確認する。

CI の検査の script は、文書だけの変更でも結果が変わる（文書の参照・用語、AC 一覧、ロールと権限の対応表、進捗の表、用語集の識別子、設計の文書の関連 spec など）ので、文書だけの push でも実行する `docs.yml` で実行する。

`ci.yml` は、次のファイルだけを変えた push では実行しない：Markdown、`.claude/`、`scripts/dev/`、`showcase/`、`pages.yml`、`.github/ISSUE_TEMPLATE/`、`.github/dependabot.yml`、`.gitignore`、`LICENSE`。ただし、全体構成の文書（`docs/design/overview.md`）は、バックエンドの単体テストが図と照合するので、Markdown でも実行する。モジュールの設計書（`docs/design/modules/*.md`）と、バックエンドの書き方（`docs/rules/backend-coding.md`）も、Markdown でも実行する。バックエンドの単体テスト（`ProductionClassesTest`）が、設計書のレベルをレベル A のモジュールの一覧と照合し、`ModuleStructureTest` が、バックエンドの書き方のパッケージの大きさの表を照合するため。また、`scripts/dev/lib/` は E2E の script から使う script の置き場所で、`scripts/dev/ports.json` はその `lib/slot.mjs` が読むポートの一覧なので、`scripts/dev/` の下でも実行する。pull request はいつも実行する。

## 3. CI の検査の script

`scripts/ci/check-all.sh` は、`scripts/ci/` と `scripts/dev/` の下の script のテスト（`*.test.mjs`）、skill の script のテスト（`.claude/skills/` の下の `*.test.cjs`）と、`scripts/ci/` の直下の検査（`*.test.mjs` 以外の `*.mjs`）を全て実行する。1 つでも失敗すれば、全てを実行したあとで終了コード 1 を返す。検査は、終了コードが 0 でなければ失敗で、終了コードが 0 でも、最後の行が要約の行（`<何>：<件数>`。「：」の後に数字がある行）でなければ失敗にする（判定は `lib/check-result.mjs`。終了コードが 0 でなければ、`check-all.sh` が node を呼ばずに直接失敗にする）。script のテストは `node --test` の出力なので、要約の行を求めず、終了コードと、出力のテストの数（`tests N`）が 1 件以上であることで判定する（glob が 1 件も一致しないと、`node --test` は 0 件で終了コード 0 になるため）。

- 検査を追加するときは、引数なしで実行できる `.mjs` を直下に置き、テストを `*.test.mjs` に書く。違反は標準出力に出し、最後の行に要約の行（例：`切れた参照：0`）を出して、違反があれば終了コード 1 にする。処理は `if (isMain(import.meta.url)) { … }`（`lib/is-main.mjs`）の中に書き、テストが import しても動かないようにする。`check-all.sh`、CI、hook は修正しなくてよい。この節の表に行を加える（`ci-docs` の検査が、表と直下の script の対応を確認する）。Git の一覧を読むなら、対象が 0 件のとき `exitIfNoFiles` で失敗にする。対応していない書き方の行は、読み飛ばさずに `ファイル:行` を出して失敗にする。除外の一覧を持つなら `lib/exclusions.mjs` を使い、画面のソースを読むなら `lib/screen-sources.mjs` を使う。
- 引数が必要な補助の script（`report/` の下の `coverage-summary.mjs`、`coverage-baseline.mjs`、`mutation-report.mjs`。第 3.1 節）と、画面の依存が必要な検査（`licenses/`）、workflow から呼ぶ判定（`workflow/` の下の `main-source.mjs`。元のブランチの名前が引数）、レビューで手動で使う補助（`review/` の下の `test-diff.mjs`。[テストの実行方法](running-tests.md) 第 4 節）は、下のディレクトリに置く。`check-all.sh` は、これらの script のテストだけを実行し、script 自体は実行しない。
- 複数の script が使う関数は `lib/` に置く（テストは `*.test.mjs`）。画面のソースを読む検査は `screen-sources.mjs` の定義を使い、違う対象を読む検査は理由を書く。
  - `spec-dirs.mjs`：spec のディレクトリ（`docs/specs/` の直下とアーカイブの下）を探す。
  - `git-files.mjs`：Git の対象のファイル（追跡しているファイルと、追跡していない無視されないファイル）の一覧を返す。Git の一覧を読む検査は、対象が 0 件のとき（`exitIfNoFiles`）失敗にする。
  - `screen-sources.mjs`：画面の本番のソース（admin・store・shared の `src` の `.ts`・`.tsx` から、テスト・生成物・ui を除く）の定義。
  - `exclusions.mjs`：除外の一覧（`{キー: 理由}`）を読み、理由のない除外と不要になった除外を見つける。
  - `strip-comments.mjs`：ソースのコメントを空白に置き換える。TypeScript は `stripComments`、Java は `stripJavaComments`（文字列とテキストブロックの中の `//` はコメントにしない）。
  - `closing-paren.mjs`：開き括弧に対応する閉じ括弧の位置を求める（文字列とコメントの中は数えない）。TypeScript は正規表現のリテラルの中も数えない。Java には正規表現のリテラルがないので、呼び出し元が `regex: false` を渡して `/` を割り算と読む。
  - `is-main.mjs`：script が直接実行されたか（import されたのでないか）を判定する。シンボリックリンク経由でも同じファイルなら直接実行とみなす。
  - `shallow-clone.mjs`：shallow clone（commit の履歴が途中までのリポジトリ）かを確認する。履歴を読む検査が、shallow clone では失敗にするために使う。
  - `line-number.mjs`：文字列の中の位置から行番号を求める。
  - `test-files.mjs`：テストのファイルの探し方と、テストの関数の読み方（AC の照合と機能紹介の数え方が使う）。`each` の表は括弧の対応で読み、表の中の関数の呼び出しでテスト名が切れない。
  - `check-result.mjs`：検査の成功・失敗の判定（`check-all.sh` が使う）。終了コードが 0 でも、最後の行が要約の行でなければ（script のテストは、テストが 0 件なら）失敗にする。成功は `OK` の 1 行で示す。

| script | 確認すること | 失敗したとき | 除外の書き方 |
|--------|--------------|--------------|--------------|
| `ac-check` | 機能の spec の全ての AC（MVP の `AC-n` と、1→10 の spec の `AC-n-m`。番号は [文書の記号と番号](../rules/notation.md) 「spec の番号」）が、テスト名（メソッドの `@DisplayName`、`test`・`it` の第 1 引数。`skip`・`fixme` のテストと、`skip`・`fixme` のまとまりの中のテストと、コメント・文字列の中の呼び出しは数えない。Git の対象のファイルだけを読む。テストのファイルが 0 件のときも失敗にする）に 1 つずつ含まれる（[テストの書き方](../rules/test-writing.md) 第 2.1 節）※1。[AC 一覧](../design/acceptance-criteria.md)を読み、廃止の AC はテストを求めず、テストが参照すると失敗にする。spec と一覧の AC の食い違い、決まっていない状態、古い表も失敗にする | テストを追加するか、テスト名に AC を書く。範囲（`AC-8〜10`）や空白で区切った並び（`AC-1 AC-2`）でなく、読点で 1 つずつ書く（`AC-1、AC-2`）。テスト名が文字列でもテンプレートでもない `test(`・`it(`、括弧が閉じない `each`、対応していない修飾（`test.shuffle` など）の付いた呼び出しは、読めないので失敗にする（`ファイル:行` を出す。テスト名を文字列で書く）。表が古いときは `node scripts/ci/ac-check.mjs --write` で再生成する | `ac-exclusions.json` に `{キー: 理由}`。キーは AC（`AC-n`）。テストがある AC の除外は失敗 |
| `ci-docs` | この表の 1 列目の名前が、`scripts/ci/` の直下の検査（`*.test.mjs` 以外の `*.mjs`）と 1 対 1 に対応する。表にない検査も、検査のない表の行も、同じ名前の行が 2 つあるのも失敗にする | 表に行を加えるか、不要な行を消す | なし |
| `compose-ports` | compose の全ての `ports` が、待ち受けるアドレスを `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` で始める（既定でこのマシンだけで待ち受け、1 つの変数で変更できる。[開発環境の起動](setup.md) 第 2 節）。`ports:` の節の中に、対応している書き方（`ports:` の行と、字下げのそろった `- ` の項目）に当たらない行（`ports: [...]` のフロー形式、字下げのそろわない行など）があっても失敗にする（読み飛ばして成功にしない。YAML のライブラリは使わない）。ホスト側のポートを読めない項目（既定値のない `${変数}`、`9000-9001` の範囲）も失敗にする | ポートの前に `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` を書く。対応していない書き方の行は、`ports:` の下に 1 行ずつ `- "…"` で書くか、検査の読み方を追加する。ホスト側のポートは、数字か `${変数:-数字}` で 1 つずつ書く | なし |
| `dependabot-actions` | 複合の action（`action.yml`）を置いたディレクトリが、`.github/dependabot.yml` の `github-actions` の `directories` に入っている（`/` はルートだけで、`.github/actions/<名前>/` は含まない。`*` は 1 つの階層として読む）。入っていない action の `uses:` は、Dependabot が更新しない | 新しい複合の action のディレクトリを `directories` に加える | なし |
| `doc-refs` | Markdown のリンク先と、`<文書の名前> 第 N 節` の参照先が存在する | リンクか節の番号を修正する（skill `docs`） | なし |
| `doc-terms` | 文書（`.github/` の yml と Markdown を含む）に使わない語（`doc-terms.json` の ng）がない | 言い換える。`--caution` で文脈で決める語も表示する | `doc-terms.json` の項目に `allow: ["許す語"]`（使わない語を含む正しい語。その中の一致は数えず、使わない語を含まない許す語は設定の誤りで失敗） |
| `env-defaults` | [環境変数](environment.md) の表の「既定」が、設定の型（`@ConfigurationProperties` の record）の `@DefaultValue` と一致する。コードの既定値は文書にあり、文書に書いた既定値（時間、数、真偽）はコードにもある（[バックエンドの書き方](../rules/backend-coding.md) 第 6.1 節）。時間は書き方によらず長さで比較する。設定の値のクラス（record）を 1 つも読めないとき、読めないクラス（record でない、引数やプロパティが 0 件など）があるときは失敗にする（コメントの中の注釈は読まない） | 文書の表の行か値を修正する。省略形（`_EXPIRY_INTERVAL`）は、前の名前の後ろを置き換えた名前として読む | なし |
| `frontend-rules` | 機能（`features/<機能>/`）が、他の機能を import しない（`@/features/` のほか、相対パス、`export … from`、`import()` の書き方も見る）（[フロントの書き方](../rules/frontend-coding.md) 第 1.2 節）。ロケールとタイムゾーンを `frontend/shared/src/lib/locale.ts` 以外に直接書かない（[フロントの書き方](../rules/frontend-coding.md) 第 7.3 節）。店舗画面と管理画面に、同じ相対パスで同じ内容のファイルがない。画面の本番のソースに、共有のパッケージ（`frontend/shared/package.json` の `exports` の先）が公開する名前と同じ名前の関数・定数・クラスの定義がない（[フロントの書き方](../rules/frontend-coding.md) 第 5.1 節）。画面の本番のソースに、クエリ付きの URL の文字列の組み立て（[フロントの書き方](../rules/frontend-coding.md) 第 6 節）と `line-through` の直書き（[フロントの書き方](../rules/frontend-coding.md) 第 10.1 節）がない。`actions/`・`lib/`・`api/` が `features/` を import しない（[フロントの書き方](../rules/frontend-coding.md) 第 1.2 節）。画面のテストと E2E が CSS のクラスを確認しない。画面のテストが、コンポーネントの内部の属性（`data-slot`）と `querySelector`・`querySelectorAll` で要素を探さない（[テストの書き方](../rules/test-writing.md) 第 4.1 節） | 2 つの機能で使うものは `components/` か `lib/` に置く。ロケールは `LOCALE`・`TIME_ZONE` を使う。同じファイルは共有パッケージに移す。クエリは `URLSearchParams` で組み立てる。テストは役割と名前で確認する（CSS のクラス、`data-slot`、`querySelector` を使わない）。取り消し線は `<s>` で示す。`actions/`・`lib/`・`api/` が使うものは `features/` の外の共通の置き場所に移す。共有のパッケージにあるものは、同じ名前で定義せず共有のものを使う | `frontend-rules-exclusions.json` に `{キー: 理由}`。キーは src からの相対パス（同じ内容のファイル）か `<画面>:<名前>`（共有と同じ名前の定義。例：`store:translate`）。不要になった除外は失敗 |
| `glossary-ids` | [用語集](../design/glossary.md) の識別子の列の名前が、コード（`backend/`、`frontend/`、`api/`、`docker/`、`e2e/`、`scripts/`）かファイルのパスにある（`scripts/ci/` のテスト、`testdata/`、`doc-terms.json`、除外の一覧は根拠にしない）。kebab-case の設定の名前は camelCase の語でもよい。名前の形の例（`Xxx`、`<…>`、`*`）は検査の対象外 | 用語集の識別子を、今のコードの名前に修正する | `glossary-ids-exclusions.json` に `{キー: 理由}`。キーは識別子。用語集にない識別子の除外は失敗（コードに偶然の一致があっても除外は残す） |
| `gradle-plugins` | Gradle のプラグインの id（`libs.versions.toml` の `[plugins]` と、`build.gradle.kts` の `plugins {}` に直接書いた `id("…")`・`kotlin("…")`。読めない書き方（`[plugins.名前]` の表、複数行の表、`plugins {}` の中の対応していない行、`apply(plugin = "…")`）は、ファイル:行を出して失敗にする）と、`scripts/ci/licenses/policy.json` の `gradlePlugins`（ライセンスの手書きの一覧）が過不足なく一致する。`java`、`jacoco` のように `.` のない id は Gradle の組み込みで、別の成果物として入らないので対象にしない | プラグインを加える・外すときは、`gradlePlugins` にライセンスを確認して加える・削除する | なし |
| `hardcoded-text` | 画面のコードに表示の文言を直接書いていない（[フロントの書き方](../rules/frontend-coding.md) 第 7.1 節） | 文言を `messages/ja.json` に置き、キーで取得する。`--count` で数だけを出力する | `hardcoded-text-exclusions.json` に `{キー: 理由}`。キーはリポジトリのルートからのファイルのパス。文言を直接書いていないファイルの除外は失敗 |
| `image-versions` | compose の `image:` の `名前:バージョン` を正とし、Gradle（`build.gradle.kts`）と統合テストのコードに書いた同じイメージのバージョンが一致する（手元の compose と CI の Testcontainers で別のバージョンを動かさないため）。compose の全てのイメージのバージョンが patch まで固定されている（`latest`、バージョンなし、`3` や `3.6` は失敗。patch のないバージョンを使う `postgres`（major だけ）と `chrislusf/seaweedfs`（`major.minor` だけ）は、バージョンの付け方がそうなので許す）。compose にないイメージを統合テストが使うときは、照合する相手がないので「警告：」で報告するだけにする | 統合テストと Gradle のバージョンを compose のバージョンにそろえる。compose のバージョンは patch まで書く | なし |
| `input-constraints` | 画面の入力の制約に数字を直接書いていない（[フロントの書き方](../rules/frontend-coding.md) 第 2.3 節） | API の契約から書き出した定数を使う | なし |
| `java-rules` | コメント（行、ブロック、Javadoc）の中は、どの規則でも読まない。テストのソース（`test`・`integrationTest`・`testFixtures`）に Lombok がない（[バックエンドの書き方](../rules/backend-coding.md) 第 5.1 節）。本番のソースで、列挙型の変換に `name()` を渡していない（改行やコメントを挟んだ書き方も検査する）（[バックエンドの書き方](../rules/backend-coding.md) 第 1.3 節）。本番のソースのうち `domain` と `infrastructure/persistence` 以外で、列挙型を名前の文字列から変換していない（文字列リテラル以外を `valueOf`・`fromValue` に渡していない）。本番・テスト・tools のソースで、JSpecify 以外の `Nullable` を使っていない（`jakarta.annotation.*` などのワイルドカード import も含む）（[バックエンドの書き方](../rules/backend-coding.md) 第 7.1 節）。全てのソースセット（`main`・`test`・`integrationTest`・`testFixtures`・tools）で、`@SuppressWarnings` の直前の行（間にある他の注釈は飛ばす）に、理由の `//` のコメントがある（Javadoc だけでは違反）（[バックエンドの書き方](../rules/backend-coding.md) 第 7.2 節） | テストでは定型コードを手動で書く。列挙型は `switch` 式で変換する。`Nullable` は `org.jspecify.annotations.Nullable` に替える。`@SuppressWarnings` の直前の行に、なぜ抑えてよいかを `//` で書く | なし |
| `local-ports` | ローカルのポートの一覧（`scripts/dev/ports.json`。スロットの script `scripts/dev/lib/slot.mjs` が読む）と、一覧を読めない所のポートが一致する。compose のホスト側のポート（共有の基盤）、Windows の予約の script（`scripts/dev/windows/exclude-ports.ps1`）の表（スロットごとにずらした分を含む）、開発環境の起動の文書と、テストの実行方法の文書（`:ポート` の書き方と、スロットの表の行）、E2E の設定（`e2e/src/urls.ts`、`e2e/playwright.config.ts` の E2E のポート。E2E のポートと、E2E が使う共有の IdP・Mailpit のポートだけを許し、開発の画面のポートなどに替えると失敗にする）、ポートを直接書く設定とスクリプト（バックエンドの `application*.yml` と local の seed、`.claude/launch.json`、`ci.yml`、画面の認証の設定、`scripts/dev/` の補助の script。スロットでずらした値も一覧から決まる値であること）、Keycloak の realm（redirect URI に、画面の一覧のポートがスロットごとの分と E2E を含めて全てあること）。テストコードの中のポートは対象にしない。一覧にないポートが書かれているとき、一覧のポートが書かれていないとき、一覧の中でポートが重なるとき、compose のホスト側のポートを読めない項目（既定値のない変数、範囲）があるとき（ファイルと行を出す）も失敗にする。compose の待ち受けるアドレスは `compose-ports` が確認する | 一覧と、食い違う所のどちらかを修正する。ポートを加えるときは、一覧、compose、Windows の script、文書、設定とスクリプト、realm の redirect URI を同時に直す | なし |
| `message-comments` | 画面の本番のソース（店舗画面・管理画面・共有パッケージの `.ts`・`.tsx`）と、店舗画面・管理画面の `src/test/` のテストの補助と `generated/` の `.tsx` で、キーを文字列リテラルで書いた文言の呼び出し（`t('…')`、`translate('…')`）に、表示する内容のコメントがある（[コメントの書き方](../rules/comments.md) 第 6.1 節）。キーを組み立てる呼び出しは検査の対象外 | 呼び出しの後に `/* 表示する内容 */` か `// 表示する内容` を書く | なし |
| `message-numbers` | バックエンドのエラーの文（`messages.properties`。メールの文は除く）に、差し込み（`{max}` など）以外の数字がない（[バックエンドの書き方](../rules/backend-coding.md) 第 3.2 節）。除外の一覧の、不要になったキーも失敗にする | 値をドメインの定数から `{名前}` で差し込む | `message-numbers-exclusions.json` に `{キー: 理由}`。キーはメッセージのキーで、理由は「1 つだけ」など数え方の言葉で、規則の値でないもの |
| `migrations` | バージョン付きのマイグレーション（`V*.sql`）が、追加した後に変わっていない（コメントだけの変更も含む。Flyway が適用済みの DB で checksum の不一致にする）。改名と削除、マイグレーションのファイルが 0 件のときも失敗にする。まだ `develop` にも `main` にも入っていない自分のブランチで追加したものは、番号の振り替え（改名・削除）と中身の変更を許す（HEAD と `develop`・`main`（`origin/` の方を先に見る）のそれぞれとの分岐点の tree を基準にし、基準にあるものが今の index（commit の前の hook では次の commit）にないか、中身が違えば失敗にする。履歴の commit でなく tree を比べるので、自分のブランチの追加と改名を merge commit でマージした後も失敗にならない。hotfix のように `main` にだけあるものも検査の対象にする。分岐点が HEAD 自身（`develop` の上、push の CI）のときは、HEAD と、HEAD の 1 つ目の親（マージの前）を基準にする。片方の ref だけがあるときはある方で判定し、どちらもなければ HEAD の 1 つ目の親を基準にして警告を出す）。分岐点を求めるので、shallow clone では失敗する | 元に戻し、変更は新しいマイグレーションで行う | なし |
| `progress` | MVP の spec 011 以降（0 埋めの 3 桁）の進捗が、README の進捗の表、MVP の完成までの一覧、tasks のチェックボックスで一致する（tasks が全て完了した spec だけが「完了」。2 つの表の状態が同じ）。アーカイブした 1→10 の spec（`docs/specs/archive/1to10/`）は、tasks が全て済んでいる | 最後のタスクを完了する commit で、2 つの表の状態を「完了」にする。アーカイブに移すタスクを tasks の最後にし、その commit で全てのタスクのチェックボックスを `[x]` にする。マージの前にアーカイブの後に追加するタスクは、アーカイブのタスクの後に、済ませる commit で `[x]` の行として追加する（[開発の進め方](../rules/workflow.md) 第 4.2 節） | なし |
| `related-specs` | 設計の文書（概要の表に「関連 spec」の行を持つもの）を変えた commit の spec（1 行目の `（T0NN-…）` か `（T7-…）`。MVP と 1→10 の spec を番号の形で区別する）が、その行にある。commit の履歴を読むので、shallow clone では失敗する | 概要の表の「関連 spec」に、その spec のリンクを追加する | `related-specs-exclusions.json` に `{キー: 理由}`。キーは設計を変えない段階のタスク番号の前置き（`T018-05` など）。当たる commit がない除外は、CI でだけ失敗にし、手元では警告にする（※1） |
| `role-permissions` | [ロールと権限の対応表](../design/role-permissions.md)が、管理 API の契約の `x-required-permission`、運営者の realm のロール、店舗 API の契約の `security` から再生成した表と一致する。管理 API の操作に権限がない、realm にない権限を使う、も失敗にする。2 つの契約の `paths:` の下（パス、メソッド、操作の直下のキー、`security` の項目）に、対応している書き方（2 字ずつの字下げ、1 行 1 項目）に当たらない行（フロー形式、字下げの違い、引用符で囲んだパス、ブロックの `summary`、字下げ 8 でない `security` の項目、項目のない `security:` など）があっても失敗にする（読み飛ばして成功にしない。YAML のライブラリは使わない） | ルートで `node scripts/ci/role-permissions.mjs --write` を実行して表を再生成し、差分を確認する。対応していない書き方の行（ファイル:行を出す）は、契約を今の書き方にそろえるか、検査の読み方を追加する | なし |
| `runtime-versions` | JDK のバージョン（`ci.yml` の `env.JAVA_VERSION` と `backend/ecsite/gradle/libs.versions.toml` の `java`）が一致する。Node のバージョン（`.node-version`）が、ルートの `package.json` の `engines.node`（`>=N` の形だけを扱う）の範囲を満たす。1 か所から読めないバージョンは、この検査で一致を確認する。`.github/workflows/` と `.github/actions/` の yml は、`java-version` が `${{ env.JAVA_VERSION }}`、Node が `node-version-file: .node-version` の形でなければ失敗にする（新しい job の直書きを見つける。フロー形式の `with: { … }` の中も読む）。`JAVA_VERSION` を、`ci.yml` の最上位の `env` 以外（job の `env`、別のファイル）で定義しても失敗にする。yml が 1 件もなければ失敗にする。3 つの `package.json`（管理画面、店舗画面、共有）の `dependencies` と `devDependencies` の `react`、`react-dom`、`@types/react`、`@types/react-dom` の指定は、全て `^1.2.3` の範囲の書き方でなければ失敗にする（`peerDependencies` は使う側に許す範囲を書くので対象にしない） | 置き場所のバージョンをそろえる。新しい job は、バージョンを直接書かずこの形で参照する。Node のバージョンを上げるときは `.node-version` と `engines.node` を合わせる | なし |
| `spec-numbers` | 本番のコードのコメントと、バックエンドのテスト（`src/test`、`src/integrationTest`、`src/testFixtures`）のコメントに spec・plan・tasks の番号（MVP の `AC-n` や `T011-…`、1→10 の `AC-7-1` や `T7-…`）がない（[コメントの書き方](../rules/comments.md) 第 1 節） | 番号を削除し、規則を言葉で書く。spec との対応はテストの名前（`@DisplayName`）に書く | なし |
| `test-names` | バックエンドの全ての `@Test`・`@ParameterizedTest` に `@DisplayName` がある（[テストの書き方](../rules/test-writing.md) 第 2.2 節） | 振る舞いを書いた名前を付ける | なし |
| `time-waits` | テストが時間で待っていない。決まった時間だけ待つ書き方（Awaitility の `during`・`pollDelay`、Promise でラップした `setTimeout`）も検出する。バックエンド・画面・E2E のテストに加え、E2E の実行の script、Playwright・Vitest・Vite の設定、画面のテストの補助（`src/test/`）も読む（[テストの書き方](../rules/test-writing.md) 第 5 節） | 条件で待つ。バックエンドの「起きないこと」は `AsyncEvents` で待つ | `time-waits-exclusions.json` に `{キー: 理由}`。キーはリポジトリのルートからのファイルのパス。時間で待つ行がないファイルの除外は失敗 |

※1：テスト名に含まれない AC と、当たる commit がない `related-specs` の除外は、CI（環境変数 `CI=true`）でだけ失敗にし、手元（hook を含む）では警告にする。`check-all.sh` は、成功した検査の警告（`警告：` で始まる行）も出力する。spec を書いてからテストを書くまでの間も commit できるようにし、テストの漏れは CI で防ぐため。手元で CI と同じに確認するときは `CI=true scripts/ci/check-all.sh`。ほかの誤り（範囲の表記、spec にない AC 番号、テストがある AC の除外など、`related-specs` の当たる commit がない除外を除く除外の一覧の誤り）は、手元でも失敗にする。

### 3.1 カバレッジと mutation score の基準の検査

テストの量と強さが下がったときに CI を失敗にする検査（[テストの書き方](../rules/test-writing.md) 第 1.2 節）。`scripts/ci/report/` の下の script と基準のファイルで行う。

| ファイル | 役割 |
|----------|------|
| `coverage-summary.mjs` | カバレッジの合計と、行のカバレッジが低い順の 10 件を、job の summary に書く。失敗にはしない |
| `coverage-baseline.mjs` | 報告の値を基準の値と比べ、下回ると終了コード 1 で失敗にする。上回ったときは、基準の更新を促す警告を表示する。`--write` で基準のファイルを書き換える |
| `mutation-report.mjs` | PIT の報告（`mutations.xml`）から mutation score と、生き残った変異が多いクラスを読む（`coverage-baseline.mjs` が使う） |
| `quality-baseline.json` | 基準の値と許容の幅（`tolerance`）。対象は、行と分岐を持つ `backend`、`admin`、`store`、`shared` と、mutation score を持つ `backend-domain` |

- 検査の手順は、報告を保存した後に置く（失敗しても報告が残る）。位置は、管理画面、店舗画面、「バックエンド」のジョブでは最後。「バックエンド（ビルドと単体テスト）」のジョブでは、失敗時のテストレポートの保存の前。店舗画面のジョブは、店舗画面と共有パッケージの 2 つの手順で検査する。
- 基準は対象ごとの合計で、ファイルごとの基準はない。
- 失敗したときは、ログと summary に出る対象、基準、今回の値、差を確認し、テストを追加して値を戻す。正当な理由で下げるときは、手元で `--write` を実行して基準のファイルを更新する。更新したファイルは同じ pull request に含め、pull request に理由を書く。手元の実行は [テストの実行方法](running-tests.md) 第 5 節。
- バックエンドのカバレッジは、統合テストの同時実行の競合があるときだけ実行される分岐があり、実行ごとにばらつく。観測した最大の欠けは、行 0.02 ポイント、分岐 0.11 ポイント（分岐 2 つ分）。分岐 1 つは約 0.05 ポイントで、同じ競合の分岐が他にもありうるため、`backend` の許容の幅は、行 0.10、分岐 0.30（どちらも約 6 つ分）にしている。
- PIT は、ビルドと単体テストのジョブの、単体テストの後で実行する（`./gradlew pitest`。`check` には含まれない）。変異の前に全ての単体テストを 1 回実行してカバレッジを取るので、毎回そのジョブの時間が 2 分ほど増える（手順の実測は 2 分 1 秒から 2 分 28 秒）。報告は artifact `pitest-backend` に保存する。
- このジョブ（実測 3 分 41 秒から 4 分 10 秒）は、統合テストの遅い方のジョブ（実測 5 分 41 秒から 6 分 15 秒）より早く終わるので、`ci.yml` の全体の時間は遅い方のジョブで決まる。同じ commit の 4 回の実測の全体は 6 分 22 秒から 7 分 10 秒で、8 分以下だった。

## 4. GitHub のセキュリティの機能

リポジトリの設定（Settings の Code security）で有効にする。CI のジョブではないため、CI の実行時間に含まれない。

| 機能 | 見るもの | 結果の場所 | 対応 |
|------|----------|------------|------|
| Dependabot alerts | 依存ライブラリ（Gradle、pnpm、GitHub Actions）の既知の脆弱性 | Security のタブの Dependabot | 直ったバージョンに更新する（バックエンドは `libs.versions.toml`、画面は `pnpm`）。更新できないときは、影響がない理由を書いて閉じる |
| secret scanning、push protection | リポジトリに入った秘密情報（API キー、token など）。push protection は、秘密情報を含む push を拒否する | Security のタブの Secret scanning | 秘密情報を無効にして再発行し、履歴から削除する。テスト用の値など誤検出なら、理由を付けて閉じる |
| CodeQL（default setup） | Java と JavaScript・TypeScript のコードの、セキュリティの問題（注入、パスの操作など） | Security のタブの Code scanning | 修正するか、誤検出・影響がない理由を付けて閉じる |

- Dependabot の設定は [ブランチとリリース](branches.md) 第 6 節。
