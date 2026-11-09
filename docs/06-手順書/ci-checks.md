# CI の検査の script

CI の検査の script（`scripts/ci/`）の一覧と、検査ごとの注。検査が失敗したときに読む。

## 検査の一覧

`scripts/ci/check-all.sh` は、script のテストと `scripts/ci/` の直下の検査を全て実行する（判定の規則は冒頭のコメント）。`ja-text --changed` は分岐点を求めるので、shallow clone では失敗する。各検査の確認すること、失敗したときの修正の手順、除外の書き方は、script の先頭のコメントにある。

| script | 目的 |
|--------|------|
| `ac-check` | 進行中の spec の AC、③ の「今有効な AC」の表、有効・変更の AC のテスト名を、AC 一覧と照合する |
| `ci-docs` | この表と検査の 1 対 1 の対応、表の 2 列、検査の先頭のコメントを確認する |
| `compose-ports` | compose の全ての ports が `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` で始まることを確認する |
| `dependabot-actions` | 複合の action のディレクトリが、Dependabot の directories に入っていることを確認する |
| `doc-refs` | リンクと見出しの参照の先、リンクテキスト、節の番号の参照、見出しに番号がないことを確認する |
| `doc-shape` | 表のセルの長さ、設計と機能仕様の観測の語と Stripe の語、Mermaid の sequenceDiagram の大きさを warning として示す（失敗にしない） |
| `docs-folders` | docs/ の下の全てのファイルが、constitution.md か許可したフォルダーの下にあることを確認する |
| `ja-text` | 使わない語（error）がないことを確認し、文脈で決める語と文体の型を warning として示す |
| `ja-text`（`--changed`） | `develop` から分けた後に追加した行の長い文と表のセルを、注釈の形で示す（失敗にしない） |
| `env-defaults` | 環境変数の文書の既定値と設定の型の @DefaultValue の一致と、管理画面の IdP の既定値の一致を確認する |
| `frontend-rules` | フロントとテストの書き方の規則のうち、ソースの文字で確認できるものを確認する |
| `glossary-ids` | 用語集の識別子の列の名前が、コードかファイルのパスにあることを確認する |
| `gradle-plugins` | Gradle のプラグインの id が、policy.json の gradlePlugins と過不足なく一致することを確認する |
| `hardcoded-text` | 画面のコードに、表示の文言（日本語の文字）を直接書いていないことを確認する |
| `image-versions` | compose と、Gradle・統合テストのイメージのバージョンの一致と、patch までの固定を確認する |
| `input-constraints` | 画面の入力の制約に、API の契約から書き出した定数を使っていることを確認する |
| `integration-shards` | 統合テストを分ける数が、`build.gradle.kts` と CI の matrix で一致することを確認する |
| `java-rules` | バックエンドのソースに、禁止した書き方（Lombok、列挙型の変換など）がないことを確認する |
| `license-policy` | 許可するライセンスの一覧が、`licenses.md` と `policy.json` で一致することを確認する |
| `local-ports` | ローカルのポートの一覧（`ports.json`）と、compose・script・文書・設定のポートが食い違わないことを確認する |
| `main-entry` | script の直接実行の判定が、共通の関数 `isMain(import.meta.url)` だけであることを確認する |
| `message-comments` | 文言をキーで引く呼び出しに、表示する内容のコメントがあることを確認する |
| `message-numbers` | バックエンドのエラーの文に、差し込み以外の数字を直接書いていないことを確認する |
| `migrations` | 適用済みのマイグレーションの変更と、前のリリースのアプリを停止させる変更がないことを確認する |
| `note-numbers` | 文書の注の番号が、1 つの文書の中で重複せず、出る順に並ぶことを確認する |
| `progress` | 1→10 のアーカイブの spec の tasks が、全て済んでいることを確認する |
| `risk` | 変更の危険度を判定し、pull request の本文の判定と比べる |
| `role-permissions` | ロールと権限の対応表が、API の契約と運営者の realm の設定から再作成した表と一致することを確認する ※1 |
| `rule-sections` | 書き方の規則の文書の各節に、`確認方法：` と `理由：` の行があることを確認する |
| `runtime-versions` | JDK と Node のバージョンの一致と、workflow が直接書かずに参照していることを確認する |
| `service-names` | 規則の本文に、AI のサービス固有の名前がないことを確認する |
| `test-helpers` | 統合テストが、テストの補助だけが呼んでよい操作を、補助の外で呼んでいないことを確認する |
| `spec-numbers` | 本番のコード（コメントの外を含む）と、テスト・設定のファイル・script のコメントに、spec・plan・tasks の番号がないことを確認する |
| `spec-structure` | spec・plan・tasks と機能仕様の構造と ID を確認する。失敗したら、雛形（`docs/08-spec/templates/`、`docs/03-機能仕様/template.md`）に合わせる |
| `test-names` | バックエンドの全てのテストに、表示の名前（`@DisplayName`）があることを確認する |
| `time-waits` | テストが、決まった時間だけ待つ書き方をしていないことを確認する |

※1：表は、エンジニアが読む設計の文書で、契約と realm の変更でしか変わらないので、Git に置く。

## カバレッジと mutation score の基準の検査

基準の値、手順の位置、失敗したときと基準を変えるときの手順は、[テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」。

## Mermaid の図の描画の検査

文書の Mermaid の図が描画できることを確認する検査。確認すること、対象、失敗したときの修正、Chrome の場所は、`scripts/ci/mermaid/mermaid-check.mjs` の冒頭のコメントにある。

- CI の `docs.yml` だけで実行し、`check-all.sh` と commit の前の hook では実行しない。理由：Chrome とネットワークが必要で遅いため。
- 手元では、ルートで `node scripts/ci/mermaid/mermaid-check.mjs` を実行する。

## 文書の形の検査

文の形から推定する文書の形（長い表のセル、設計と機能仕様の観測の語と Stripe の語、大きい sequenceDiagram）を、warning として示す検査。確認すること、対象、warning の扱い、閾値は、`scripts/ci/doc-shape.mjs` の冒頭のコメントにある。

- `check-all.sh` が実行する。warning では失敗にしない（終了コードは 0）。対象の Markdown が 0 件のときだけ、他の検査と同じく失敗にする。
- 手元では、ルートで `node scripts/ci/doc-shape.mjs`（件数）と `node scripts/ci/doc-shape.mjs --list`（1 件 1 行の一覧）を実行する。
