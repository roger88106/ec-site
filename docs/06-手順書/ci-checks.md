# CI の検査の script

CI の検査の script（`scripts/ci/`）の一覧と、検査ごとの注。検査が失敗したときに読む。

`scripts/ci/check-all.sh` は、script のテスト（`*.test.mjs`、`*.test.cjs`）と `scripts/ci/` の直下の検査を全て実行し、1 つでも失敗すれば最後に終了コード 1 を返す。検査は、終了コードが 0 でなく、または最後の行が要約の行（`<何>：<件数>`）でなければ失敗で、判定の規則は `check-all.sh` の冒頭のコメントと `lib/check-result.mjs` にある。
直下の検査の他に、`ja-text` を `--changed` で実行する（表の `ja-text`（`--changed`）の行）。分岐点を求めるので、shallow clone では失敗する。

各検査の詳しい説明（確認すること、失敗したときの修正の手順、除外の書き方）は、script の先頭のコメントにある。

| script | 目的 |
|--------|------|
| `ac-check` | 進行中の spec の AC、③ の「今有効な AC」の表、有効・変更の AC のテスト名を、AC 一覧と照合する ※1 |
| `ci-docs` | この表と検査の 1 対 1 の対応、表の 2 列、検査と `lib/` の 1 行目のコメント、検査の先頭のコメントの「失敗したとき：」と「除外：」の行を確認する |
| `compose-ports` | compose の全ての ports が、既定でこのマシンだけで待ち受ける `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` で始まることを確認する |
| `dependabot-actions` | 複合の action を置いたディレクトリが、Dependabot の github-actions の directories に入っていることを確認する |
| `doc-refs` | Markdown のリンクと見出しの参照の先が存在し、`docs/` の下の文書へのリンクテキストが文書の名前で、節の番号の参照（第 N 節）が残っていないことを確認する |
| `docs-folders` | docs/ の下の全てのファイルが、constitution.md か許可したフォルダーの下にあることを確認する |
| `ja-text` | 日本語の文に使わない語（error）がないことを確認し、文脈で決める語と長い文などの文体の型を warning として示す。 |
| `ja-text`（`--changed`） | `develop` から分けた後に追加した行のうち、閾値以上の文（字数・読点の数）と表のセル（字数の合計）を、注釈の形で示す（警告だけで失敗にしない）。 |
| `env-defaults` | 環境変数の文書の既定値が、設定の型の @DefaultValue と一致し、管理画面の IdP の既定値が置き場所の間でそろうことを確認する |
| `frontend-rules` | フロントとテストの書き方の規則のうち、ソースの文字で確認できるもの（機能間の import、ロケール、重複、E2E の選び方など）を確認する |
| `glossary-ids` | 用語集の識別子の列の名前が、コードかファイルのパスにあることを確認する。表の見出しの行は「用語・識別子・…」の順で、識別子を 1 つ以上取り出せることも確認する |
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
| `progress` | 1→10 のアーカイブの spec の tasks が、全て済んでいることを確認する。`archive/mvp/` の下に MVP の形でない名前の spec があれば失敗にする |
| `risk` | 変更の危険度（高か低）を、危険度が高い経路と spec の宣言から判定し、pull request の本文の判定と比べる。 |
| `role-permissions` | ロールと権限の対応表が、API の契約と運営者の realm の設定から再作成した表と一致することを確認する ※2 |
| `runtime-versions` | JDK と Node のバージョンが置き場所の間で一致し、workflow が直接書かずにそれを参照していることを確認する。 |
| `service-names` | 規則の本文に、AI のサービス固有の名前がない（コードスパンのパス、コードブロック、リンク先を除く）ことを確認する |
| `test-helpers` | 統合テストが、テストの補助だけが呼んでよい操作を、補助の外で呼んでいないことを確認する。 |
| `spec-numbers` | 本番のコードと、テストと設定のファイルと `scripts/` の下の script の、それぞれのコメントに、spec・plan・tasks の番号が書かれていないことを確認する。 |
| `spec-structure` | spec・plan・tasks と機能仕様について、構造（節の名前と順）と ID の定義・参照を確認する。判定の区分は誤りの防止 ※3 |
| `test-names` | バックエンドの全てのテストに、表示の名前（`@DisplayName`）があることを確認する。 |
| `time-waits` | テストが、決まった時間だけ待つ書き方をしていないことを確認する。 |

※1：確認することは 3 つ。進行中の spec の AC が AC 一覧にある。定義の行は、`- **AC-<番号>** <文>` の形で書く。`*`・`+`・字下げ・番号付きの箇条は、崩れた行として失敗にする。③ の「今有効な AC」の表が AC 一覧から作った表と一致する（節の中でマーカーの範囲の外に AC の表の行があると失敗にする。バックエンドのモジュールのディレクトリと ③ の文書の両方がある名前の ③ が、構造の検査の対象の一覧にないときも失敗にする）。有効・変更の AC がテスト名（@DisplayName、test・it の第 1 引数）に出る（テスト名の確認は CI でだけ失敗）。アーカイブの spec は読まない。

※2：表は、エンジニアが読む設計の文書で、契約と realm の変更でしか変わらないので、Git に置く。

※3：誤りの防止は、誤って書いたものを失敗にする区分である。失敗したら、文書を雛形に合わせる。spec は `docs/08-spec/templates/`、機能仕様は `docs/03-機能仕様/template.md` の骨組みである。

## 1. カバレッジと mutation score の基準の検査

テストの量と強さが下がったときに CI を失敗にする検査（[テストの書き方](../05-開発規則/test-writing.md)の「カバレッジと mutation score の基準」）。`scripts/ci/report/` の下の script と基準のファイルで行う。

| ファイル | 役割 |
|----------|------|
| `coverage-summary.mjs` | カバレッジの合計と、行のカバレッジが低い順の 10 件を、job の summary に書く。失敗にはしない |
| `coverage-baseline.mjs` | 報告の値を基準の値と比べ、下回ると終了コード 1 で失敗にする。`--write` の動作は[テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」 |
| `mutation-report.mjs` | PIT の報告（`mutations.xml`）から mutation score と、生き残った変異が多いクラスを読む（`coverage-baseline.mjs` が使う） |
| `quality-baseline.json` | 基準の値と許容の幅（`tolerance`）。対象は[テストの書き方](../05-開発規則/test-writing.md)の「カバレッジと mutation score の基準」 |

- 検査の手順は、報告を保存した後に置く（失敗しても報告が残る）。位置は、管理画面、店舗画面、「バックエンド」のジョブでは最後。「バックエンド（ビルドと単体テスト）」のジョブでは、失敗時のテストレポートの保存の前。店舗画面のジョブは、店舗画面と共有パッケージの 2 つの手順で検査する。
- 基準は対象ごとの合計で、ファイルごとの基準はない。低い箇所は、表の `coverage-summary.mjs` と `mutation-report.mjs` が summary に書く一覧で見つける。
- 失敗したときは、ログと summary の対象・基準・今回の値・差を見て、テストを追加して、値を基準以上に戻す。正当な理由で下げるときは、[テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」の手順で基準のファイルを更新する。
- バックエンドのカバレッジは、統合テストの同時実行の競合があるときだけ実行される分岐があり、実行ごとにばらつく。そのため `backend` の許容の幅は、行 0.10、分岐 0.30 にしている。根拠の実測は [CI の実測](../07-記録/ci-results.md)の「バックエンドのカバレッジのばらつき」。
- PIT は、ビルドと単体テストのジョブの、単体テストの後で実行する（`./gradlew pitest`。`check` には含まれない）。変異の前に全ての単体テストを 1 回実行してカバレッジを取るので、そのジョブの時間が増える。報告は artifact `pitest-backend` に保存する。
- このジョブは、統合テストの遅い方のジョブより早く終わるので、`ci.yml` の全体の時間は遅い方のジョブで決まる。根拠の実測は [CI の実測](../07-記録/ci-results.md)の「PIT とジョブの時間」。

## 2. Mermaid の図の描画の検査

文書の Mermaid の図が描画できることを確認する検査。`scripts/ci/mermaid/mermaid-check.mjs` が実行する。確認すること、対象、失敗したときの修正は、script の冒頭のコメントにある。

- 実行するのは、CI の `docs.yml` だけである。Chrome とネットワークが必要で遅いため、`check-all.sh` と commit の前の hook では実行しない。
- script は `scripts/ci/` の直下に置かない（直下の検査は `check-all.sh` が全て実行する）。script のテストは、他の script のテストと同じく `check-all.sh` が実行する。
- 手元では、ルートで `node scripts/ci/mermaid/mermaid-check.mjs` を実行する。Chrome の場所は、環境変数 `MERMAID_CHROME` で指定する。
- CI のランナー（ubuntu-latest）には Google Chrome が `/usr/bin/google-chrome` に入っているので、`MERMAID_CHROME` は指定しない。
