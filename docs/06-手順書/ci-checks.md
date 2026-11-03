# CI の検査の script

CI の検査の script（`scripts/ci/`）の一覧と、検査ごとの注。検査が失敗したときに読む。

`scripts/ci/check-all.sh` は、script のテストと `scripts/ci/` の直下の検査を全て実行し、1 つでも失敗すれば最後に終了コード 1 を返す。判定の規則は `check-all.sh` の冒頭のコメントにある。`ja-text --changed` は分岐点を求めるので、shallow clone では失敗する。

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
| `spec-numbers` | 本番のコード、テストと設定のファイル、`scripts/` の下の script のコメントに、spec・plan・tasks の番号がないことを確認する。 |
| `spec-structure` | spec・plan・tasks と機能仕様について、構造（節の名前と順）と ID の定義・参照を確認する。判定の区分は誤りの防止 ※3 |
| `test-names` | バックエンドの全てのテストに、表示の名前（`@DisplayName`）があることを確認する。 |
| `time-waits` | テストが、決まった時間だけ待つ書き方をしていないことを確認する。 |

※1：確認すること、定義の行の書き方、失敗の条件は `scripts/ci/ac-check.mjs` の冒頭のコメント。

※2：表は、エンジニアが読む設計の文書で、契約と realm の変更でしか変わらないので、Git に置く。

※3：誤りの防止は、誤って書いたものを失敗にする区分である。失敗したら、文書を雛形に合わせる。spec は `docs/08-spec/templates/`、機能仕様は `docs/03-機能仕様/template.md` の骨組みである。

## 1. カバレッジと mutation score の基準の検査

基準の値、手順の位置、失敗したときと基準を変えるときの手順は、[テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」。

## 2. Mermaid の図の描画の検査

文書の Mermaid の図が描画できることを確認する検査。`scripts/ci/mermaid/mermaid-check.mjs` が実行する。確認すること、対象、失敗したときの修正は、script の冒頭のコメントにある。

- 実行するのは、CI の `docs.yml` だけである。Chrome とネットワークが必要で遅いため、`check-all.sh` と commit の前の hook では実行しない（script を直下に置かない理由）。
- 手元では、ルートで `node scripts/ci/mermaid/mermaid-check.mjs` を実行する。Chrome の場所は、環境変数 `MERMAID_CHROME` で指定する（指定しなければ既定の場所を探す。CI は指定しない）。
