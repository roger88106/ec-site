# CI の検査

手元で CI と同じ検査を実行するコマンドと、CI のジョブの一覧。commit の前と、CI が失敗したときに読む。

## 1. 手元での実行

| 検査 | コマンド（ルートで） | CI のジョブ |
|------|----------------------|-------------|
| バックエンド | `cd backend/ecsite && ./gradlew check` | バックエンド（ビルドと単体テスト）、バックエンド（統合テスト 1/2・2/2）、バックエンド |
| 画面（共有パッケージ・管理画面・店舗画面・E2E のコード） | `scripts/dev/check-frontend.sh`（ルートで 1 回の検査として、依存の再インストール、ライセンス、knip、組み込みの名前の一覧の確認も実行する） | 管理画面、店舗画面（共有パッケージを含む）、E2E（E2E のコードの lint・型チェック） |
| CI の検査の script | `scripts/ci/check-all.sh` | 文書と CI の検査の script |
| E2E | `scripts/dev/e2e.sh` | E2E |

- CI で失敗したら、まず個別のジョブを確認する。「バックエンド」は、ビルドと単体テスト・統合テストの結果を確認するジョブなので後に見る。E2E は他のジョブを待たずに始まるため、コンパイルの誤りなどでも E2E が失敗する。
- 手元のバックエンドのコマンドは、ジョブに分けず、単体テストと統合テストの全てを実行する。
- commit の hook が実行するのは、ステージした内容に対する `check-all.sh`、追加行の `ja-text` の warning の表示、メッセージの `ja-text` の検査だけである（[開発環境の起動](setup.md)の「起動」）。テストは、変更した箇所に応じて手動で実行する（[テストの実行方法](running-tests.md)）。

## 2. CI のジョブ

ジョブの手順は、各 workflow のコメントと手順の名前にある。

| ワークフロー | ジョブ | 実行するもの | 実行する時 |
|--------------|--------|--------------|------------|
| `ci.yml` | バックエンド（ビルドと単体テスト） | ライセンス、ビルド、静的解析、単体テスト、PIT の検査を実行する（[バックエンドの CI のジョブ](ci-backend-jobs.md)）。 | push（[バックエンドの CI のジョブ](ci-backend-jobs.md)の「ci.yml を実行しないファイル」を除く）と pull request |
| `ci.yml` | バックエンド（統合テスト 1/2・2/2） | 統合テストの半分ずつを実行する。 | 同上 |
| `ci.yml` | バックエンド | 上の 3 つの結果を確認し、カバレッジの報告と基準の検査を行う。 | 同上 |
| `ci.yml` | 管理画面 | lint、型チェック、テスト、ビルド、カバレッジの基準の検査を行う。 | 同上 |
| `ci.yml` | 店舗画面 | 画面全体の検査（ライセンス、knip など）と、共有パッケージ・店舗画面のテストとビルドを行う。 | 同上 |
| `ci.yml` | E2E | E2E のコードの lint・型チェックの後、ブラウザーで操作する。 | 同上 |
| `docs.yml` | 文書と CI の検査の script | `check-all.sh` と Mermaid の図の描画の検査を実行する（[CI の検査の script](ci-checks.md)）。 | 全ての push と pull request |
| `main-source.yml` | main への元のブランチ | `main` への pull request の元のブランチ名が `release-` か `hotfix-` で始まることを確認する（[ブランチとリリース](branches.md)の「`main` の元のブランチの制限」）。 | `main` への pull request |
| `risk.yml` | 危険度の照合 | pull request の本文の危険度が、`scripts/ci/risk.mjs` の判定より低くないことを確認する。 | `develop` への draft でない pull request |
| `pages.yml` | 機能紹介のページ | `showcase/` を GitHub Pages に公開する。 | `main` で `showcase/` が変わったとき、手動 |

## 3. 他の文書

- CI の決まり（失敗したまま次の spec に進まない、など）は [constitution](../constitution.md)の「CI/CD」。
- CI の検査の script の一覧と、検査が失敗したときの対応は、[CI の検査の script](ci-checks.md)。
- 検査を加える手順は、[CI の検査の追加](ci-adding-checks.md)。
- バックエンドのジョブの構成と、`ci.yml` を実行しないファイルは、[バックエンドの CI のジョブ](ci-backend-jobs.md)。
- GitHub のセキュリティの機能は、[ブランチとリリース](branches.md)の「GitHub のセキュリティの機能」。
