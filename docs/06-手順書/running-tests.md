# テストの実行方法

テストの書き方は [テストの書き方](../05-開発規則/test-writing.md)、起動の準備は [開発環境の起動](setup.md)。エージェントは skill `local-dev` を使う。

## 1. テストの種類

| 種類 | 対象 | ツール | 置き場所 |
|------|------|------|----------|
| バックエンドの単体テスト | Spring を起動しないもの | JUnit、AssertJ | `src/test` |
| バックエンドの統合テスト | Spring と DB などをつないだもの | `@SpringBootTest`、Testcontainers | `src/integrationTest` |
| フロントの単体テスト | 画面に依存しない関数（判定、変換、表示の整形） | Vitest | 対象の横（`*.test.ts`） |
| フロントの結合テスト | コンポーネントを描画し、利用者の操作で振る舞いを確認する（API は MSW） | Vitest、React Testing Library | 対象の横（`*.test.tsx`） |
| E2E | 管理画面・店舗画面・バックエンドをつないだ、コアフローの少数のシナリオ | Playwright | `e2e/` |

E2E は専用の DB・IdP・画像のバケットで、毎回再作成した状態から実行する（ローカルの開発のデータを変えない）。

## 2. バックエンド

`backend/ecsite/` で実行する。

| コマンド | 内容 | Docker |
|----------|------|--------|
| `./gradlew test` | 単体テスト（Spring を起動しない） | 不要 ※1 |
| `./gradlew integrationTest` | 統合テスト（Spring + Testcontainers の PostgreSQL など） | 必要 |
| `./gradlew check` | 単体・統合テスト、OpenAPI の構文の検証（CI はジョブに分けて同じものを実行する） | 必要 |
| `./gradlew integrationTest -PintegrationTestShard=<番号>/<総数>` | 統合テストの一部だけ（クラスの名前の hash で分ける。例 `1/2`）。CI が 2 つのジョブに分けるために使う。手元では通常は使わない | 必要 |
| `./gradlew integrationTest -PintegrationTestForks=<数>` | 統合テストを実行するフォークの数（既定 1。CI も 1）。手元では通常は使わない | 必要 |
| `./gradlew integrationTestShards` | 統合テストのジョブ（1/2・2/2）に入るクラスの一覧（`-PintegrationTestShardCount=<数>` で分ける数を変える。既定 2） | 不要 |
| `./gradlew coverageReport` | 単体テストと統合テストを合算したカバレッジ（`build/reports/jacoco/coverageReport/`）。`-PcoverageExecDir`・`-PcoverageClassesDir` は CI が使う。手元では通常は使わない | 必要 |
| `./gradlew pitest` | domain（`com.ecsite.module.*.domain.*`）の mutation testing（PIT）。単体テスト（`src/test`）だけを使う。報告は `build/reports/pitest/`。`check` と `build` には含まれない。時間は ※2 | 不要 ※1 |

※1：jOOQ のコード生成は Testcontainers の PostgreSQL にマイグレーションを適用して行うため、マイグレーションか生成の設定が変わったときだけ Docker が必要（変わらなければ `generateJooq UP-TO-DATE` でスキップされる）。

※2：PIT は変異の前に全ての単体テストを 1 回実行するので、手元（コンパイル済み）で約 2 分かかる（CI の実測は [CI の実測](../07-記録/ci-results.md)の「PIT とジョブの時間」）。

単体テストには、構成と契約の検査も含まれる：モジュールの境界（`ModulithTest`）、アーキテクチャの規則（`architecture/` の ArchUnit）、契約の制約（`contract/`）、エラーの定義とメッセージの対応（`MessageContractTest`）。

コンパイルが Error Prone や NullAway で失敗したときは、エラーの行の `[検査の名前]` で検査が分かる。`-Werror` を付けているので、警告でも失敗する。修正は [バックエンドの書き方](../05-開発規則/backend-coding.md)の「null の扱い」と「検査を抑えるとき」。

Checkstyle（`checkstyleMain`、`checkstyleTest` など）が失敗したときも、`[検査の名前]` で分かる。import の違反（`UnusedImports`、`CustomImportOrder`、`AvoidStarImport`）の修正方法は [バックエンドの書き方](../05-開発規則/backend-coding.md)の「書式」。`MissingJavadocType`・`MissingJavadocMethod`・`JavadocVariable` は `src/main` の Javadoc の記載漏れなので、Javadoc を書く（他のソースセットでは `config/checkstyle/suppressions.xml` で抑えている）。

テストのコードの重複（`cpdCheck`）が失敗したときは、報告 `backend/ecsite/build/reports/cpd/cpdCheck.xml` の場所を見る。修正方法は [バックエンドのテストの書き方](../05-開発規則/test-writing-backend.md)の「テストのコードの重複」。

## 3. 管理画面・店舗画面

`frontend/admin/`・`frontend/store/` で実行する（ルートで実行すると、全てのプロジェクトをまとめて実行する）。

```bash
# CI と同じ確認（lint と整形、型チェック、テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- 管理画面の `pnpm build`（本番ビルド）は、環境変数 `VITE_OIDC_AUTHORITY` と `VITE_OIDC_CLIENT_ID` がないと失敗する。ローカルの値で確認するときは、[環境変数](environment.md)の「管理画面」の既定値を付ける。
- `pnpm test` はカバレッジのレポートを `coverage/index.html` と `coverage/coverage-summary.json` に出力する（共有パッケージ `frontend/shared` も同じ）。
- 管理画面の結合テストはバックエンドなしで実行できる（API の応答は MSW）。
- 要約だけを出力する `scripts/dev/check-frontend.sh` でも実行できる。

### knip が失敗したとき

使われていないものの検査（規則は[フロントの書き方](../05-開発規則/frontend-coding.md)の「使われていないものを残さない」）は、knip（設定は `knip.ts`）が `frontend/*` と `e2e` を調べる。ルートで `pnpm knip` を実行する（`check-frontend.sh` と CI も実行する）。先に `generate:api` で生成物を作る。

報告は次の 3 種類で、種類ごとに修正する。

| 報告 | 修正の方法 |
|------|--------|
| どこからも使われていないファイル | 削除する。実行時に import されずに使われるファイル（例：`i18n/types.ts`、`e2e/scripts/prepare.ts`）は誤検出なので、`knip.ts` の `entry` に書く。意味のない参照を追加しない |
| どこからも import されていない export と型 | 削除する。定義したファイルの中だけで使う export も報告するので、export を外す |
| `package.json` にあって使われていない依存、`package.json` にない依存の import | 依存を削除する、または `package.json` に追加する |

生成物の export と型の報告は、`knip.ts` の `ignoreIssues` に、ファイルの範囲と `exports`・`types` だけを書いて外す。外す理由はコメントに書く。

## 4. E2E（`e2e/`）

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
| 準備（`e2e/scripts/prepare.ts`） | ポートを確認し、E2E 用の DB と Keycloak を再作成する。DB は毎回、マイグレーションと税のデータ（`db/seed/base`）だけから始まる |
| 起動 | バックエンド（:8090、profile `local,e2e`）、管理画面（:4173）、店舗画面（:3100）を毎回起動する |
| データ | 管理 API で作り、品番・名称に実行ごとの識別子（`E2E-<印>-…`）を付ける |

- バックエンドの起動に JDK 21 を使うため、`JAVA_HOME` に JDK 21 のパスを設定しておく。
- 手元の開発のバックエンド（:8080）と、スロットのバックエンド（[開発環境の起動](setup.md)の「スロット」）と並行して実行できる。
- メインの作業ツリーでも作業ツリーでも `pnpm test`（`e2e/scripts/run.ts`）を実行できる。E2E は DB、IdP、ポートを共有するので、全ての作業ツリーで 1 つずつ実行する。他の作業ツリーが実行中なら、ロックの所有者を示して終了コード 1 で終了する。ロックの仕組みは `e2e/scripts/run.ts` の冒頭のコメント。示されたロックのファイルは、所有者のプロセスがないのに終了し続けるときだけ削除してよい。
- E2E は常にスロット 0 の固定の環境で実行する（スロットの環境変数は取り除く）。
- 管理画面・店舗画面を、E2E のポート（:4173、:3100）で起動したままにしない。別の作業ツリーのコードに対して実行することを防ぐため。
- 店舗画面の API のキャッシュは、E2E では 1 秒（`STORE_API_REVALIDATE_SECONDS`）。
- 決済は mock で、外部に接続しない。支払の期限は、期限切れを待つテストのため 12 秒（`application-e2e.yml`）。
- compose の設定を変えたコミットを取り込んだら、[開発環境の起動](setup.md)の「共有の基盤の設定を変える変更をマージしたとき」の手順で反映する。
- 結果の要約だけを見るときは、ルートで `scripts/dev/e2e.sh [Playwright の引数]`。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を確認する。

## 5. 既存のテストを変えていないことの確認

振る舞いを変えない整理（書き方の統一など）では、既存のテストの変更が、本番のクラスの移動と名前の変更に伴うものと、中身を変えずにテストを別のファイルへ移したものだけであることを確認する。

```bash
# ルートで：元の commit から今の作業ツリーまでを比べる
node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]
```

判定と名前の対応のファイルの書き方は、`scripts/ci/review/test-diff.mjs` の冒頭のコメントにある。

## 6. カバレッジと mutation score の基準の検査

カバレッジ（行・分岐）と、domain の mutation score は、基準の値（許容の幅を含む）を下回らない。テストの量や強さが下がったことに気づくため、下回ると CI が失敗する。

- 行と分岐の対象は、バックエンド（`backend`）、管理画面（`admin`）、店舗画面（`store`）、共有パッケージ（`shared`）。mutation score の対象は、バックエンドの domain（`backend-domain`）。mutation score は、PIT で測る、テストが検出できた変異の割合。
- 基準は `scripts/ci/report/quality-baseline.json` にある、対象ごとの合計の値と許容の幅（`tolerance`）。今回の値が、基準の値から許容の幅を引いた値を下回ると失敗にする。ファイルごとの基準はない。
- `backend` の許容の幅は、行 0.10、分岐 0.30。統合テストの同時実行の競合があるときだけ実行される分岐があり、実行ごとにばらつくため。根拠は [CI の実測](../07-記録/ci-results.md)の「バックエンドのカバレッジのばらつき」。

検査は `scripts/ci/report/` の下の script で行う。`coverage-baseline.mjs` が基準と比べて失敗にし、`coverage-summary.mjs` と `mutation-report.mjs` が低い箇所の一覧を job の summary に書く。

### 6.1 CI での手順の位置

- 検査の手順は、報告を保存した後に置く（失敗しても報告が残る）。店舗画面のジョブは、店舗画面と共有パッケージの 2 つの手順で検査する。
- PIT は、ビルドと単体テストのジョブの、単体テストの後で実行する。報告は artifact `pitest-backend` に保存する。ジョブの時間が約 2 分増える根拠は [CI の実測](../07-記録/ci-results.md)の「PIT とジョブの時間」。

### 6.2 手元での実行

CI と同じ検査を、手元で実行できる。先に、対象のテストを実行して報告を作る。

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

- 手元でバックエンドの一部のテストだけを実行すると、報告の値が基準より低くなり、検査が失敗する。基準の検査には、CI と同じ範囲（`./gradlew check`）の報告を使う。
- 今回の値が基準を上回ったときは、成功にして、基準の更新を促す警告を表示する。

### 6.3 失敗したとき

ログと summary の対象・基準・今回の値・差を見る。低い箇所は、`coverage-summary.mjs` と `mutation-report.mjs` が summary に書く一覧で見つける。テストを追加して、値を基準以上に戻す。正当な理由で下げるときは、次の手順で基準のファイルを更新する。

### 6.4 基準を変える手順

- `--write` は、基準のファイルのその対象の値を、報告の値で書き換える（許容の幅は変えない）。基準を下げるときと、上回った値に更新するときに使い、書き換えたファイルを同じ pull request に含める。下げるときは、pull request に理由を書く。
- バックエンドの `--write` は、CI の summary の値、または `./gradlew check --rerun-tasks` などでテストを実際に実行した報告の値で行う。ビルドのキャッシュから読んだ報告は、実行した報告と分岐が 1 つ違うことがある（[CI の実測](../07-記録/ci-results.md)の「キャッシュから読んだ値と実行した値」）。
