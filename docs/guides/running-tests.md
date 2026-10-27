# テストの実行方法

テストの書き方は [テストの書き方](../rules/test-writing.md)、起動の準備は [開発環境の起動](setup.md)。エージェントは skill `local-dev` を使う。

## 1. バックエンド

`backend/ecsite/` で実行する。

| コマンド | 内容 | Docker |
|----------|------|--------|
| `./gradlew test` | 単体テスト（Spring を起動しない） | 不要 ※1 |
| `./gradlew integrationTest` | 統合テスト（Spring + Testcontainers の PostgreSQL など） | 必要 |
| `./gradlew check` | 単体・統合テスト、OpenAPI の構文の検証（CI はジョブに分けて同じものを実行する） | 必要 |
| `./gradlew integrationTest -PintegrationTestShard=<番号>/<総数>` | 統合テストの一部だけ（クラスの名前の hash で分ける。例 `1/2`）。CI が 2 つのジョブに分けるために使う。手元では通常は使わない | 必要 |
| `./gradlew integrationTest -PintegrationTestForks=<数>` | 統合テストを実行するフォークの数（既定 1。CI も 1）。手元では通常は使わない | 必要 |
| `./gradlew integrationTestShards` | 統合テストのジョブ（1/2・2/2）に入るクラスの一覧（`-PintegrationTestShardCount=<数>` で分ける数を変える。既定 2） | 不要 |
| `./gradlew coverageReport` | 単体テストと統合テストを合算したカバレッジ（`build/reports/jacoco/coverageReport/`。`check` の後にも実行できる）。`-PcoverageExecDir=<ディレクトリ>` と `-PcoverageClassesDir=<ディレクトリ>` で、別のディレクトリの実行データ（`*.exec`）とクラスから作る（CI が、ジョブごとに保存したものを集めるために使う。手元では通常は使わない） | 必要 |
| `./gradlew pitest` | domain（`com.ecsite.module.*.domain.*`）の mutation testing（PIT）。単体テスト（`src/test`）だけを使う。報告は `build/reports/pitest/`（`index.html` と `mutations.xml`）。`check` と `build` には含まれない。時間は ※2 | 不要 ※1 |

※1：jOOQ のコード生成は Testcontainers の PostgreSQL にマイグレーションを適用して行うため、マイグレーションか生成の設定が変わったときだけ Docker が必要（変わらなければ `generateJooq UP-TO-DATE` でスキップされる）。

※2：PIT は変異の前に全ての単体テストを 1 回実行してカバレッジを取るので、時間の多くはその実行に使われる。手元はコンパイル済みで約 2 分（実測 1 分 57 秒）。CI の手順は 2 分 1 秒から 2 分 28 秒（[CI の検査](ci.md) 第 3.1 節）。

単体テストには、構成と契約の検査も含まれる：モジュールの境界（`ModulithTest`）、アーキテクチャの規則（`architecture/` の ArchUnit）、契約の制約（`contract/`）、エラーの定義とメッセージの対応（`MessageContractTest`）。

コンパイルが Error Prone や NullAway で失敗したときは、エラーの行の `[検査の名前]`（例：`[NullAway]`）で検査が分かる。修正方法は、null の報告なら [バックエンドの書き方](../rules/backend-coding.md) 第 7.1 節、検査を抑えるときは [バックエンドの書き方](../rules/backend-coding.md) 第 7.2 節。`-Werror` を付けているので、警告でもコンパイルが失敗する。

Checkstyle（`checkstyleMain`、`checkstyleTest`、`checkstyleTools` など）が失敗したときは、エラーの行の `[検査の名前]` で検査が分かる。`UnusedImports` は使われていない import を、`CustomImportOrder` は import の並びが決まった順（`java`・`javax` 以外、`java`・`javax`、static）でないものを報告する。`AvoidStarImport` は `*` の import を報告する。個々の import に書き換える（IntelliJ の設定は[バックエンドの書き方](../rules/backend-coding.md) 第 5.4 節）。Checkstyle は import を修正しないので、報告された import は手動で削除するか、並び替える。`MissingJavadocType`・`MissingJavadocMethod`・`JavadocVariable` は、本番のコード（`src/main`）の Javadoc の書き漏れなので、Javadoc を書く。修正方法と import の並びは [バックエンドの書き方](../rules/backend-coding.md) 第 5.4 節。test、integrationTest、testFixtures、tools では、Javadoc の 3 つの検査を `backend/ecsite/config/checkstyle/suppressions.xml` で抑えている。

テストのコードの重複（`cpdCheck`）が失敗したときは、報告（`backend/ecsite/build/reports/cpd/cpdCheck.xml`）に重複の場所（ファイルと行）が出る。補助に移すか、移せない理由があるときだけ `// CPD-OFF 理由` と `// CPD-ON` で囲む（[テストの書き方](../rules/test-writing.md) 第 3.5 節）。

## 2. 管理画面・店舗画面

`frontend/admin/`・`frontend/store/` で実行する（ルートで実行すると、全てのプロジェクトをまとめて実行する）。

```bash
# CI と同じ確認（lint と整形、型チェック、テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- 管理画面の `pnpm build`（本番ビルド）は、環境変数 `VITE_OIDC_AUTHORITY` と `VITE_OIDC_CLIENT_ID` がないと失敗する（[環境変数](environment.md)）。ローカルの値で確認するときは、環境変数の文書の第 2 節の既定値を付けて実行する（`VITE_OIDC_AUTHORITY=<既定値> VITE_OIDC_CLIENT_ID=<既定値> pnpm build`）。
- `pnpm test` はカバレッジのレポートを `coverage/index.html` に生成する。共有パッケージ（`frontend/shared`）の `pnpm test` も、上と同じ Vitest のカバレッジの設定（v8）でカバレッジを取り、`coverage/coverage-summary.json` に出力する。
- 管理画面の結合テストはバックエンドなしで実行できる（API の応答は MSW）。
- 要約だけを出力する `scripts/dev/check-frontend.sh` でも実行できる（管理画面のビルドの環境変数は、未設定ならローカルの値を使う）。

## 3. E2E（`e2e/`）

バックエンド、管理画面、店舗画面を起動して、Chromium で操作する（Playwright）。専用の DB（`ec_site_e2e`）、IdP（Keycloak、:8181）、画像のバケットを使い、ローカルのデータを変えない。

```bash
# ルートで：compose を起動し、依存を入れる（E2E 用の Keycloak は準備の script が起動する）
docker compose up -d
pnpm install

# e2e/ で：初回だけブラウザーを入れ、E2E を実行する
pnpm exec playwright install chromium
pnpm test
```

| 段階 | 内容 |
|------|------|
| 準備（`e2e/scripts/prepare.ts`） | E2E のサーバーのポート（:8090、:4173、:3100）で待ち受けられるかを確認する（Windows の予約範囲に含まれるポートは EACCES で失敗する）。E2E 用の DB と Keycloak の database を再作成し、E2E 用の Keycloak（compose の profile `e2e`）を再起動する。毎回、マイグレーションと税のデータ（`db/seed/base`）だけの DB、realm の JSON の状態の IdP から始まる |
| 起動 | バックエンド（:8090、profile `local,e2e`）、管理画面（:4173）、店舗画面（:3100）を毎回起動する。画面のサーバーは手元でも再利用しないので、ポートが使われていれば Playwright が停止する |
| データ | 管理 API で作り、品番・名称に実行ごとの識別子（`E2E-<印>-…`）を付ける |

- バックエンドの起動に JDK 21 を使うため、`JAVA_HOME` に JDK 21 のパスを設定しておく。
- 手元の開発のバックエンド（:8080）と、スロットのバックエンド（[開発環境の起動](setup.md) 第 9 節）と並行して実行できる。
- メインの作業ツリーでも、作業ツリーでも、`pnpm test`（`e2e/scripts/run.ts`）を実行できる。E2E は DB（`ec_site_e2e`）、IdP、ポートを共有するので、全ての作業ツリーで 1 つずつ実行する。実行中は git の共通のディレクトリに `ec-site-e2e.lock`（作業ツリーのパス、PID、開始の時刻）を置き、他の作業ツリーで実行中なら、所有者の作業ツリーのパスと開始の時刻を示して終了コード 1 で終了する。所有者のプロセスがなければ、古いロックとして再取得する。停止のシグナル（Ctrl+C など）の 1 回目で、実行中の子プロセスを停止し、終了するのを待ってからロックを削除して終了する。2 回目は待たずに終了する。PID が別のプロセスに再利用されて終了し続けるときは、示されたロックのファイルを削除してよい。E2E の実行中の印はこのロックだけで、`scripts/dev/check-frontend.sh` もこのロックを見て、所有者がその作業ツリーで動いているあいだは画面のビルド（E2E が配る `dist` の入れ替え）を止める（`scripts/dev/e2e.sh` でも `e2e/` の `pnpm test` でも同じ）。
- E2E は常にスロット 0 の固定の環境で実行する。スロットの環境変数（`node scripts/dev/slot.mjs env` の出力など）を shell に設定していても、E2E のエントリポイントが取り除いてから実行する。
- 管理画面・店舗画面を、E2E のポート（:4173、:3100）で起動したままにしない。E2E は起動済みのサーバーを使わず、ポートが使われていれば停止する。別の作業ツリーのコードに対して実行することを防ぐため。
- 店舗画面の API のキャッシュは、E2E では 1 秒（`STORE_API_REVALIDATE_SECONDS`）。
- 決済は mock で、外部の決済サービスに接続しない。支払の期限は、期限切れを待つテストのため 12 秒（`application-e2e.yml`）。注文から mock の画面で支払うまでの操作は、この期限内に完了する（CI で 2 秒ほど）。
- compose の設定を変えたコミットを取り込んだら、メインの作業ツリーで [開発環境の起動](setup.md) 第 9 節の手順（共有の基盤の設定を変える変更をマージしたとき）で反映する。作業ツリーでは `docker compose up -d` を実行しない。
- 結果の要約だけを見るときは、ルートで `scripts/dev/e2e.sh [Playwright の引数]`（テストごとの ok / x と失敗の要点だけを出力し、全体はログのファイルに残す）。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を確認する。

## 4. 既存のテストを変えていないことの確認

振る舞いを変えない整理（書き方の統一など）では、既存のテストの変更が、本番のクラスの移動と名前の変更に伴うものと、中身を変えずにテストを別のファイルへ移したものだけであることを確認する。

```bash
# ルートで：元の commit から今の作業ツリーまでを比べる
node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]
```

- 対象は、テストのファイル（バックエンドの `src/test`・`src/integrationTest`、画面の `*.test.ts(x)`、`e2e/`）の差分。
- `import`・`package` の行（複数行の import の中の行を含む）を除き、既存のファイル（削除したファイルを含む）の削除した行に名前の対応を適用した結果を、全てのテストのファイルの追加した行と、前後の空白を除いて照合する。照合できない行を出力して失敗する。
- 既存のファイルの追加した行で、照合して残ったものも出力する。新しく追加したファイルの、照合して残った行は対象外（移した先の import と枠の行、新しいテスト）。
- 名前の対応のファイルは、1 行に `旧=新`（クラスの単純名か完全修飾名、画面はモジュールのパスも）。名前は語の境界で置き換える。

## 5. カバレッジと mutation score の基準の検査

CI と同じ検査を、手元で実行できる（[テストの書き方](../rules/test-writing.md) 第 1.2 節、[CI の検査](ci.md) 第 3.1 節）。先に、対象のテストを実行して報告を作る。

```bash
# ルートで：node scripts/ci/report/coverage-baseline.mjs <対象> <報告のパス> [--write]
node scripts/ci/report/coverage-baseline.mjs backend backend/ecsite/build/reports/jacoco/coverageReport/coverageReport.xml
node scripts/ci/report/coverage-baseline.mjs admin frontend/admin/coverage/coverage-summary.json
node scripts/ci/report/coverage-baseline.mjs store frontend/store/coverage/coverage-summary.json
node scripts/ci/report/coverage-baseline.mjs shared frontend/shared/coverage/coverage-summary.json
node scripts/ci/report/coverage-baseline.mjs backend-domain backend/ecsite/build/reports/pitest/mutations.xml
```

| 対象 | 報告を作るコマンド | 報告 |
|------|--------------------|------|
| `backend` | `backend/ecsite/` で `./gradlew check`（統合テストを含む。`coverageReport` を実行する） | JaCoCo の XML |
| `admin`、`store`、`shared` | 各ディレクトリで `pnpm test` | Vitest の `coverage-summary.json` |
| `backend-domain` | `backend/ecsite/` で `./gradlew pitest` | PIT の `mutations.xml` |

- 基準は `scripts/ci/report/quality-baseline.json`。報告の値が、基準から許容の幅を引いた値を下回ると、対象、基準、今回の値、差を出力して終了コード 1 で終了する。上回ったときは成功にして、基準の更新を促す警告を表示する。
- `--write` は、基準のファイルのその対象の値を、報告の値で書き換える（許容の幅は変えない）。基準を下げるときと、上回った値に更新するときに使い、書き換えたファイルを同じ pull request に含める。下げるときは、pull request に理由を書く。
- 手元でバックエンドの一部のテストだけを実行すると、報告の値が基準より低くなり、検査が失敗する。基準の検査には、CI と同じ範囲（`./gradlew check`）の報告を使う。
- バックエンドの `--write` は、手元のビルドのキャッシュから読んだテストの報告ではなく、CI の summary の値、または `./gradlew check --rerun-tasks` などでテストを実際に実行した報告の値で行う。キャッシュから読んだ報告は、実行した報告と分岐が 1 つ違うことがある（実測：キャッシュから読んだ値は分岐 85.26%、実行した値は 85.20%）。
