# CI の検査

手元で CI と同じ検査を実行するコマンドと、CI のジョブの一覧。commit の前と、CI が失敗したときに読む。

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
| `ci.yml` | バックエンド（ビルドと単体テスト） | 依存のライセンスの検査、ビルド、Error Prone と NullAway（コンパイル時の静的解析）、Checkstyle（Javadoc の記載漏れは main だけ、private を含む。使われていない import、`*` の import、import の並びは全てのソースセット）、テストのコードの重複の検出（PMD の CPD。[バックエンドのテストの書き方](../05-開発規則/test-writing-backend.md)の「テストのコードの重複」）、tools のソースのコンパイル、単体テスト、OpenAPI の構文の検証、domain の mutation testing（PIT）と mutation score の基準の検査 | push（[バックエンドの CI のジョブ](ci-backend-jobs.md)の「ci.yml を実行しないファイル」だけの push を除く）と pull request |
| `ci.yml` | バックエンド（統合テスト 1/2・2/2） | 統合テストの半分ずつ（2 つのジョブ。統合テストのコンパイルでも Error Prone が動く） | 同上 |
| `ci.yml` | バックエンド | 上の 3 つのジョブの結果の確認、カバレッジの報告（要約）、カバレッジの基準の検査 | 同上 |
| `ci.yml` | 管理画面 | lint、型チェック、テスト、ビルド、カバレッジの要約、カバレッジの基準の検査 | 同上 |
| `ci.yml` | 店舗画面 | 画面の依存のライセンスの検査、使われていないファイル・export・依存の検査（knip。管理画面・店舗画面・共有パッケージ・E2E のコード全体を、ルートで 1 回）、組み込みの名前の一覧の確認（`scripts/ci/globals/lib-globals.mjs`。TypeScript の lib の名前と一致するか）、共有パッケージ（lint、型チェック、テスト、カバレッジの要約）、店舗画面（lint、型チェック、テスト、ビルド、カバレッジの要約）、店舗画面と共有パッケージのカバレッジの基準の検査 | 同上 |
| `ci.yml` | E2E | E2E のコードの lint・型チェック、バックエンド・管理画面・店舗画面を起動してブラウザーで操作する | 同上 |
| `docs.yml` | 文書と CI の検査の script | `scripts/ci/check-all.sh`（[CI の検査の script](ci-checks.md)）、Mermaid の図の描画の検査（`scripts/ci/mermaid/mermaid-check.mjs`。[CI の検査の script](ci-checks.md)の「Mermaid の図の描画の検査」）。後者は、前の step（`check-all.sh`）が失敗しても実行する | 全ての push と pull request（文書だけの push でも実行する） |
| `main-source.yml` | main への元のブランチ | `main` への pull request の元のブランチが `release-` か `hotfix-` で始まる（判定は `scripts/ci/workflow/main-source.mjs`。[CI の検査の script](ci-checks.md)）。`main` の ruleset の必須の検査（[ブランチとリリース](branches.md)の「`main` の元のブランチの制限」） | `main` への pull request |
| `risk.yml` | 危険度の照合 | pull request の本文の「## 危険度」の節の「判定：」の値を、`scripts/ci/risk.mjs` の判定（[CI の検査の script](ci-checks.md)）と比べ、本文が判定より低いとき、値が空・節がないときに失敗にする。判定は pull request の側と基準（`origin/develop`）の側の両方の `risk.mjs`・`risk.json` で行い、どちらかが失敗すれば失敗にする。Dependabot の pull request（作成者で見分ける）は比べず、題から読む更新の種類を入れた判定をジョブの要約に出すだけにする。本文を書き換えたときにも流すので、`ci.yml` とは別のワークフローにする（`edited` で全体の CI を流し直さないため） | `develop` への draft でない pull request（開いた、本文を書き換えた、commit を足した、開き直した、draft を外したとき）。draft の間はジョブを skip する |
| `pages.yml` | 機能紹介のページ | `showcase/` を GitHub Pages に公開する | `main` で `showcase/` が変わったとき、手動で起動したとき |

## 3. 他の文書

- CI の決まり（失敗したまま次の spec に進まない、など）は [constitution](../constitution.md)の「CI/CD」。
- CI の検査の script の一覧と、検査が失敗したときの対応は、[CI の検査の script](ci-checks.md)。
- 検査を加える手順は、[CI の検査の追加](ci-adding-checks.md)。
- バックエンドのジョブの構成と、`ci.yml` を実行しないファイルは、[バックエンドの CI のジョブ](ci-backend-jobs.md)。
- GitHub のセキュリティの機能は、[ブランチとリリース](branches.md)の「GitHub のセキュリティの機能」。
