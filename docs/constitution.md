# EC Site Constitution

プロジェクト全体の長期ルールで、spec・plan・tasks・コードは全てこれに従い、変えるときは先に本書を更新する。
本書は大綱と文書の地図で、細則はリンク先の各文書にあり、本書と同じく守る。

## 1. プロジェクトの位置づけ

日本向けで通貨は日本円のみの、自社 B2C の EC サイト（多店舗プラットフォームではない）で、個人のポートフォリオとして本番相当のやり方を模擬する。

- コアフローは省略しない：閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況。C 側（消費者）と B 側（運営者）の機能は [システムの全体像](01-概要/system.md)。
- MVP は「コアフロー全体を動かせる最小版」とする。購入できるのは会員だけ（ゲストの購入は MVP の後に検討する）。
- クラウドネイティブで、水平スケールできる構成にする（「水平スケール」）。

## 2. 技術スタック

バージョンは設定ファイル（Gradle の version catalog、`package.json`、compose）が正本。フロントの細則は [フロントのアーキテクチャ](02-設計/frontend-architecture.md)。

- 共通・E2E：OpenAPI（API の契約）、Keycloak（OIDC）、Docker Compose、GitHub Actions、Playwright。
- バックエンド：Java、Spring Boot、Spring Modulith、jMolecules、PostgreSQL、jOOQ、Flyway、S3 互換オブジェクトストレージ、imgproxy、Stripe、SMTP、ShedLock、Gradle Kotlin DSL、Error Prone、NullAway、Checkstyle、Lombok、JUnit 5、Testcontainers、Actuator、Micrometer。ローカルでは Stripe は mock、メールは Mailpit、ストレージは SeaweedFS を使う。
- フロントエンド：C 側（`frontend/store`）と B 側（`frontend/admin`）は別のアプリ。TypeScript、pnpm（workspace）、Tailwind CSS、shadcn/ui、`openapi-typescript` + `openapi-fetch`、Biome、Vitest、MSW。C 側は Next.js、B 側は Vite + React。

後から導入する技術は MVP では入れず、導入するときは本節を更新してから spec を書く。検索エンジンは 1 → 10 の段階、Redis はインスタンス間で共有するデータが必要になったとき、メッセージブローカーはモジュールを別プロセスに切り出すときに導入する。

## 3. リポジトリ構成

モノレポとする。

```
ec-site/
├── .github/workflows/
├── .claude/              # エージェントの設定と skill
├── .local/               # Git に入れないもの（作業用の script、マシンに固有の設定など）
├── api/openapi/          # API 契約の正本
├── docs/                 # constitution.md と 8 フォルダー（「文書の地図」）
├── backend/ecsite/       # バックエンド
├── frontend/             # store（C 側）、admin（B 側）、shared（共有）
├── e2e/                  # E2E
├── showcase/             # 機能紹介のページ（GitHub Pages）
├── scripts/              # CI と開発の script
└── docker/               # ローカル開発の compose
```

### 文書の地図

現在の振る舞いはコードを正とし、設計と機能仕様の文書はコードを説明する（[開発の進め方](05-開発規則/workflow.md)の「コードと文書の食い違い」）。フォルダーごとの文書の一覧は、各フォルダーの `README.md` にある。

| フォルダー | 中身 | 一覧の文書 |
|------------|------|------------|
| `01-概要/` | 専門知識がなくても読める全体像 | [README.md](01-概要/README.md) |
| `02-設計/` | エンジニア向けの構成 | [README.md](02-設計/README.md) |
| `03-機能仕様/` | モジュールごとの業務の規則、AC 一覧、用語集 | [README.md](03-機能仕様/README.md) |
| `04-決定記録/` | 決定の理由 | [README.md](04-決定記録/README.md) |
| `05-開発規則/` | 開発の進め方、書き方の規則、レビュー | [README.md](05-開発規則/README.md) |
| `06-手順書/` | 環境の起動、CI、テスト、ブランチ | [README.md](06-手順書/README.md) |
| `07-記録/` | 実績と測定 | [README.md](07-記録/README.md) |
| `08-spec/` | spec・plan・tasks | [README.md](08-spec/README.md) |

## 4. 開発の進め方

仕様駆動開発（SDD）で何を作るかを先に決め、テスト駆動開発（TDD）で作る。細則は [開発の進め方](05-開発規則/workflow.md)。

- 全ての変更は issue から始まり、規模（S・M・L）ごとに書く文書が違う。
- SDD：spec（何を・なぜ）→ plan（どう作るか）→ tasks（作業の手順）→ 実装。spec がコードに先行し、誤りに気づいたら spec から修正する。
- TDD：レッド → グリーン → リファクタリング。テストのタスクを実装より先に置き、全ての AC に AC 番号付きのテストを対応させる。

## 5. バックエンドのアーキテクチャ

Spring Modulith のモジュラーモノリス。デプロイ単位は 1 つ。細則（モジュールの一覧を含む）は [バックエンドのアーキテクチャ](02-設計/backend-architecture.md)の「モジュラーモノリス」。

- モジュールが外に公開するのは `api/`（同期の呼び出し）と `event/`（非同期の通知）だけ。他のモジュールのテーブルは読み書きしない。
- モジュールごとに、複雑さに応じて構成のレベル（フル・簡略・イベントストア）を選ぶ。
- C 側と B 側は同じモジュールを共有し、API のエンドポイント（`/api/store/**`、`/api/admin/**`）で分ける。

## 6. 水平スケール

同じアプリを複数のインスタンスで並行して動かせるように作る。細則は [バックエンドのアーキテクチャ](02-設計/backend-architecture.md)の「水平スケール」。

- リクエストをまたぐ状態をメモリやローカルディスクに持たない。
- 業務上の排他は DB で制御する（JVM の中のロックを使わない）。
- 時刻で切り替わるデータは、定期ジョブで書き換えず、参照時に判定する。

## 7. データとファイル

細則は [バックエンドのアーキテクチャ](02-設計/backend-architecture.md)の「データとファイル」。

- PostgreSQL と jOOQ。スキーマの正本は Flyway のマイグレーション。
- 品番・注文番号など人が使う番号と、識別子（UUID）を分ける。金額は円単位の整数（`Money`）。
- ファイルは S3 互換のオブジェクトストレージに置き、DB にはキーだけを持つ。

## 8. API

細則は [API の規約](05-開発規則/api-conventions.md)。

- 契約先行：OpenAPI（`api/openapi/`）を plan で書き、バックエンドもフロントもそこから生成した型を使う。
- RESTful JSON。エラーは Problem Details（RFC 9457）に、分岐用のコードと画面に表示できる文を加える。

## 9. 認証・認可

細則は [バックエンドのアーキテクチャ](02-設計/backend-architecture.md)の「認証・認可」。

- 認証は IdP（Keycloak）に任せ、自前で作らない。ログインは OIDC、API は OAuth 2.0 の Resource Server として token を検証するだけ。業務のコードは認証の仕組み（Spring Security）に依存しない。
- 消費者と運営者は IdP の別の realm に分ける。
- 公開ネットワークへのデプロイは、その前提（[バックエンドのアーキテクチャ](02-設計/backend-architecture.md)の「公開ネットワークへのデプロイの前提」）を決めてから行う。

## 10. 外部連携（決済・物流）

細則は [バックエンドのアーキテクチャ](02-設計/backend-architecture.md)の「外部連携」。

- 外部サービスは port 経由で呼び、外部の型を業務のコードに持ち込まない。
- sandbox があれば使い、なければ内部の mock にする。設定で切り替える。

## 11. テスト

細則は [テストの書き方](05-開発規則/test-writing.md)、[バックエンドのテストの書き方](05-開発規則/test-writing-backend.md)、[フロントのテストの書き方](05-開発規則/test-writing-frontend.md)、実行方法は [テストの実行方法](06-手順書/running-tests.md)。

- 全ての AC にテストを対応させ、テスト名に AC 番号を入れる。
- バックエンドは単体テストと統合テスト（Testcontainers）、フロントは単体テストと結合テスト、つないだ流れは E2E（Playwright）。
- 画面のテストは、画面に見えるもの（文字、ラベル、ボタンの名前）で確認する。見た目は目視で確認する。

## 12. CI/CD

細則は [CI の検査](06-手順書/ci.md)、[CI の検査の script](06-手順書/ci-checks.md)、[ブランチとリリース](06-手順書/branches.md)。

- CI：push と pull request で、バックエンド、フロント、E2E を実行する。文書などだけを変えた push では、CI の検査の script の全てを実行する。CI が失敗している状態で、次の spec の実装に進まない。
- ブランチ：作業ブランチを `develop` に、リリースを `main` に pull request で入れる。
- CD：まだない（デプロイ先は未定。「未定事項」）。

## 13. 書く言語（日本語と英語の使い分け）

細則は [文書の書き方](05-開発規則/writing.md)の「言語」と「論理名と物理名」。

- Git に入るものは全て日本語で書く（文書、コメント、テスト名、コミットメッセージ、エラーメッセージなど）。
- 識別子（クラス名、API のパス、テーブル名など）は英語。
- 文書の本文は業務の用語（論理名）で書き、識別子（物理名）との対応は [用語集](03-機能仕様/glossary.md) に置く。

## 14. コードの書き方

書き方は [バックエンドの書き方](05-開発規則/backend-coding.md)、[フロントの書き方](05-開発規則/frontend-coding.md)、[テストの書き方](05-開発規則/test-writing.md)、[バックエンドのテストの書き方](05-開発規則/test-writing-backend.md)、[フロントのテストの書き方](05-開発規則/test-writing-frontend.md)、[コメントの書き方](05-開発規則/comments.md) にある。コードを書く前と、レビューのときに読む（[書き方の規則とレビュー](05-開発規則/review.md)）。

## 15. 依存とライセンス

細則（区分の表を含む）は [依存とライセンス](05-開発規則/licenses.md)。

- プロジェクトのライセンスは MIT。
- 依存は、無料で商用に使えて、コピーレフトでない（使う側のコードの公開を求めない）ものだけを使う。CI で確認する。
- 有料版がある製品は、無料版の範囲で使う。

## 16. 未定事項

未定事項と後回しにする作業は、GitHub の issue で管理する。対応時期が 1→10 のものには milestone `1→10` を付ける（[一覧](https://github.com/roger88106/ec-site/milestone/1)）。起票の規則は [起票と記録の操作](05-開発規則/issues.md)の「起票」。
