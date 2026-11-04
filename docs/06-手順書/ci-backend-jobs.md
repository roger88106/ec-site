# バックエンドの CI のジョブ

バックエンドのジョブの構成と、`docs.yml` を分ける理由、`ci.yml` を実行しないファイル。`.github/workflows/` を変えるとき、ジョブの時間を調べるときに読む。

## 1. バックエンドのジョブの構成

- ジョブの分け方、同時に始める理由、「バックエンド」の実行の条件、カバレッジの報告の合わせ方は、`.github/workflows/ci.yml` のコメントにある。
- 「ビルドと単体テスト」のジョブは、ビルドと単体テストのほかに、静的解析（Error Prone など）と PIT を実行する。統合テストのジョブのコンパイルでも、Error Prone が動く。
- ruleset の必須の検査は「バックエンド」の名前のままにする。前のジョブが失敗・スキップしても「バックエンド」を実行し、最初の手順で結果を確認して失敗にする。
- 統合テストの分け方は `backend/ecsite/build.gradle.kts` のコメントにある。`./gradlew integrationTestShards` の一覧が示すのはクラスの数だけで、時間ではない。
- 2 つのジョブの差は 1 分 30 秒以下を目安にする。差が大きいときは、`backend/ecsite/build.gradle.kts` の `shardOf` の salt を変えて再分割し、一覧で重複と漏れがないことと、CI の時間の差を確認する。
- 分ける数は、`ci.yml` の matrix の `shard` の一覧の長さだけで決まる（ジョブの名前と `-PintegrationTestShard=<番号>/<総数>` の総数は `strategy.job-total` から読む）。変えるときは、次を直す。
  - `ci.yml` の `shard` の一覧。
  - この文書の「バックエンドのジョブの構成」の「2 つのジョブ」。
  - [CI の検査](ci.md)の表の、「1/2・2/2」「半分ずつ」「上の 3 つ」。
  - [テストの実行方法](running-tests.md)の「バックエンド」の、「1/2・2/2」「2 つのジョブ」。
  - [ブランチとリリース](branches.md)の ruleset の注の、「1/2・2/2」。
  - `backend/ecsite/build.gradle.kts` の `integrationTestShards` の既定の数（`integrationTestShardCount` の `orElse`）。
- 統合テストのフォークの数（`maxParallelForks`）は 1。CI で測って 10% 以上短くなれば、`-PintegrationTestForks=2` にする。根拠の実測は [CI の実測](../07-記録/ci-results.md)の「統合テストのフォークの数」。
- E2E は、ブラウザーの OS の依存を apt で入れない（理由は `ci.yml` のコメント）。失敗時の画面の撮影の文字が四角になって読めないときは、手元の `scripts/dev/e2e.sh` で再現する。
- Node のバージョンは `.node-version`、JDK のバージョンは `ci.yml` の `JAVA_VERSION` と `libs.versions.toml` の `java` に書く。1 か所にできない組は、検査 `runtime-versions`（[CI の検査の script](ci-checks.md)）が一致を確認する。

## 2. docs.yml を分ける理由

`.github/workflows/docs.yml` の冒頭のコメントにある。

## 3. ci.yml を実行しないファイル

変えても `ci.yml` が実行されないファイルは、`.github/workflows/ci.yml` の `on.push.paths` のコメントにある。pull request はいつも実行する。
