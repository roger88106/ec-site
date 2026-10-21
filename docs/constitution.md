# EC Site Constitution

プロジェクト全体の長期ルール。spec、plan、tasks、コードは全てこれに従う。
ルールを変えるときは、先に本書を更新してから他を変更する。
本書は大綱と文書の地図で、細則はリンク先の各文書にある。細則も本書と同じく守る。

【提案】が付いた項目（細則の文書を含む）は暫定で、運用して確定する。それ以外は確定したルール。

## 1. プロジェクトの位置づけ

- 自社 B2C の EC サイト（多店舗プラットフォームではない）。個人のポートフォリオとして、本番相当のやり方を模擬する。
- コアフローは省略しない：閲覧 → カート → 注文 → 決済 → 出荷 → 配送状況。
  - C 側（消費者）：会員登録・ログイン、商品閲覧、カート、チェックアウト、決済、注文・配送状況の照会
  - B 側（運営者）：商品登録、価格変更、在庫、注文管理、出荷通知、データ収集と簡易レポート
- MVP は「コアフロー全体を動かせる最小版」とする。購入できるのは会員だけ（ゲストの購入は MVP の後に検討する）。
- クラウドネイティブで、水平スケールできる構成にする（第 6 節）。
- 対象市場は日本、通貨は日本円のみ。

## 2. 技術スタック

### 共通・E2E

| 領域 | 採用 |
|------|------|
| API の契約 | OpenAPI |
| 認証 | Keycloak（OIDC） |
| ローカルの環境 | Docker Compose |
| CI | GitHub Actions |
| E2E | Playwright |

### バックエンド

| 領域 | 採用 |
|------|------|
| 言語・フレームワーク | Java 21、Spring Boot 4.1.x |
| モジュール化 | Spring Modulith、jMolecules |
| DB・データアクセス | PostgreSQL 18、jOOQ、Flyway |
| ファイル保存 | Amazon S3 互換オブジェクトストレージ（AWS SDK）、SeaweedFS（ローカル・CI） |
| 画像の変換 | imgproxy |
| 決済 | Stripe（stripe-java。ローカルの既定は mock） |
| メール | SMTP（Spring の mail。ローカルは Mailpit） |
| 定期ジョブの分散ロック | ShedLock |
| ビルド | Gradle Kotlin DSL、version catalog |
| 定型コードの生成 | Lombok |
| テスト | JUnit 5、AssertJ、Testcontainers、MockMvc |
| 可観測性 | Actuator、Micrometer（Prometheus）、分散トレーシング（Zipkin）、構造化ログ |

### フロントエンド

C 側と B 側は別のアプリ。構成は [フロントのアーキテクチャ](design/frontend-architecture.md)。

両方で共通：

| 領域 | 採用 |
|------|------|
| 言語・パッケージ管理 | TypeScript、pnpm（workspace） |
| UI | Tailwind CSS、shadcn/ui、lucide-react |
| API の型と呼び出し | `openapi-typescript` + `openapi-fetch` |
| lint・整形 | Biome |
| テスト | Vitest、MSW（`openapi-msw`） |

アプリごと：

| 領域 | C 側（`frontend/store`） | B 側（`frontend/admin`） |
|------|------|------|
| フレームワーク | Next.js（App Router） | Vite + React（SPA）、React Router |
| サーバー状態 | Next.js の fetch とキャッシュ | TanStack Query + `openapi-react-query` |
| フォーム | サーバーアクション | React Hook Form |
| ログイン | Auth.js（`next-auth`） | `oidc-client-ts` + `react-oidc-context` |
| 表示の文言 | `next-intl` | `use-intl` |
| コンポーネントのテスト | `react-dom/server` | React Testing Library、user-event、jsdom |

### 後から導入する技術

MVP では入れない。導入するときは本節を更新してから spec を書く。

| 技術 | 導入のタイミング |
|------|------------------|
| 検索エンジン | 1 → 10 の段階 |
| Redis | インスタンス間で共有するキャッシュや一時データが必要になったとき |
| メッセージブローカー | モジュールを別プロセスに切り出すとき（Spring Modulith のイベント外部化） |

## 3. リポジトリ構成

モノレポとする。

```
ec-site/
├── .github/workflows/
├── .claude/              # エージェントの skill とプレビューの設定（Git に入れて共有する）
├── .local/               # Git に入れないもの（作業用の script、引き継ぎの文書、マシンに固有の設定）
├── api/openapi/          # API 契約の正本
├── docs/
│   ├── constitution.md   # 大綱と文書の地図
│   ├── todo.md           # TODO 一覧（後回しにする作業と未決事項）
│   ├── rules/            # 規範：開発の進め方、書き方の規則
│   ├── design/           # 今の設計：全体構成、アーキテクチャ、モジュール、画面、用語集
│   ├── guides/           # 手順：開発環境の起動、環境変数、CI、テストの実行方法
│   └── specs/            # 開発記録：spec・plan・tasks（完了した spec は specs/archive/ に移す）
├── backend/ecsite/       # バックエンド
├── frontend/
│   ├── store/            # C 側（店舗画面）
│   ├── admin/            # B 側（管理画面）
│   └── shared/           # 共有のパッケージ
├── e2e/                  # E2E
├── showcase/             # 機能紹介のページ（GitHub Pages で公開する）
├── scripts/              # CI と開発の script
└── docker/               # ローカル開発の compose
```

### 文書の地図

`docs/` は、規範（rules）、今の設計（design）、手順（guides）、開発記録（specs）に分ける。今の振る舞いはコードが正で、design の文書はそれを説明する（[開発の進め方](rules/workflow.md) 第 1.5 節）。

規範（`docs/rules/`）：

| 文書 | 内容 |
|------|------|
| [workflow.md](rules/workflow.md) | 開発の進め方（SDD・TDD）、文書の書き方、コードと文書の食い違い |
| [notation.md](rules/notation.md) | spec・tasks の記号と番号 |
| [review.md](rules/review.md) | 書き方の規則の書き方とレビュー |
| [comments.md](rules/comments.md) | コメントの書き方 |
| [subagents.md](rules/subagents.md) | エージェントの作業フロー（サブエージェントへの任せ方） |
| [backend-coding.md](rules/backend-coding.md) | バックエンドの書き方 |
| [frontend-coding.md](rules/frontend-coding.md) | フロントの書き方 |
| [test-writing.md](rules/test-writing.md) | テストの書き方 |
| [api-conventions.md](rules/api-conventions.md) | API の規約 |

今の設計（`docs/design/`）：

| 文書 | 内容 |
|------|------|
| [overview.md](design/overview.md) | 全体構成（モジュールの依存とイベント、外部システム） |
| [backend-architecture.md](design/backend-architecture.md) | バックエンドのアーキテクチャ |
| [modules/](design/modules) | モジュールごとの設計 |
| [frontend-architecture.md](design/frontend-architecture.md) | フロントのアーキテクチャ |
| [apps/](design/apps) | アプリごとの構成（管理画面、店舗画面） |
| [common.md](design/common.md) | 共通コード・ログ・可観測性 |
| [role-permissions.md](design/role-permissions.md) | ロールと権限の対応表 |
| [acceptance-criteria.md](design/acceptance-criteria.md) | AC 一覧（状態とテスト） |
| [glossary.md](design/glossary.md) | 用語集 |
| [licenses.md](design/licenses.md) | 依存とライセンス |

手順（`docs/guides/`）：

| 文書 | 内容 |
|------|------|
| [setup.md](guides/setup.md) | 開発環境の起動 |
| [environment.md](guides/environment.md) | 環境変数 |
| [ci.md](guides/ci.md) | CI の検査 |
| [running-tests.md](guides/running-tests.md) | テストの実行方法 |

開発記録（`docs/specs/`）：

| 文書 | 内容 |
|------|------|
| [specs/](specs) | spec・plan・tasks。完了した spec は、MVP のまとまりごとに `specs/archive/<まとまり>/` に移す（MVP は `specs/archive/mvp/`。spec 019 で移す） |
| [mvp.md](specs/mvp.md) | MVP の完成までの一覧 |
| [todo.md](todo.md) | TODO 一覧 |

## 4. 開発の進め方

仕様駆動開発（SDD）で何を作るかを先に決め、テスト駆動開発（TDD）で作る。細則は [開発の進め方](rules/workflow.md)。

### 4.1 仕様駆動開発（SDD：Spec-Driven Development）

spec（何を・なぜ）→ plan（どう作るか）→ tasks（作業の手順）→ 実装。

- spec がコードに先行する。誤りに気づいたら、spec から修正する。
- spec は「機能」と「整理」の 2 種類（[文書の記号と番号](rules/notation.md)）。

### 4.2 テスト駆動開発（TDD：Test-Driven Development）

レッド（失敗するテスト）→ グリーン（通る最小の実装）→ リファクタリング。

- テストのタスクを実装より先に置く。
- 全ての AC に、AC 番号付きのテストを対応させる。

## 5. バックエンドのアーキテクチャ

Spring Modulith のモジュラーモノリス。デプロイ単位は 1 つ。細則は [バックエンドのアーキテクチャ](design/backend-architecture.md)。

- モジュールが外に公開するのは `api/`（同期の呼び出し）と `event/`（非同期の通知）だけ。他のモジュールのテーブルは読み書きしない。
- モジュールごとに、複雑さに応じて構成のレベル（フル・簡略・イベントストア）を選ぶ。
- C 側と B 側は同じモジュールを共有し、API のエンドポイント（`/api/store/**`、`/api/admin/**`）で分ける。

| モジュール | 責務 |
|------------|------|
| `tax` | 税区分と税率 |
| `catalog` | 商品、価格、公開、カテゴリ、商品検索 |
| `inventory` | 在庫数、引当と減算 |
| `media` | 画像の受け付け、保存、配信 |
| `storefront` | C 側 API（複数モジュールの結果を合成する参照専用の層） |
| `cart` | カート |
| `member` | 会員 |
| `order` | 注文と状態遷移 |
| `payment` | 決済 |
| `shipping` | 出荷と配送状況 |
| `notification` | 消費者へのメール |
| `reporting` | データ収集と簡易レポート |
| `region` | 都道府県・市区町村・郵便番号のマスターデータ |

## 6. 水平スケール

同じアプリを複数のインスタンスで並行して動かせるように作る。細則は [バックエンドのアーキテクチャ](design/backend-architecture.md) 第 5 節。

- リクエストをまたぐ状態をメモリやローカルディスクに持たない。
- 業務上の排他は DB で制御する（JVM の中のロックを使わない）。
- 時刻で切り替わるデータは、定期ジョブで書き換えず、参照時に判定する。

## 7. データとファイル

細則は [バックエンドのアーキテクチャ](design/backend-architecture.md) 第 6 節。

- PostgreSQL と jOOQ。スキーマの正本は Flyway のマイグレーション。
- 品番・注文番号など人が使う番号と、識別子（UUID）を分ける。金額は円単位の整数（`Money`）。
- ファイルは S3 互換のオブジェクトストレージに置き、DB にはキーだけを持つ。

## 8. API

細則は [API の規約](rules/api-conventions.md)。

- 契約先行：OpenAPI（`api/openapi/`）を plan で書き、バックエンドもフロントもそこから生成した型を使う。
- RESTful JSON。エラーは Problem Details（RFC 9457）に、分岐用のコードと画面に表示できる文を加える。

## 9. 認証・認可

細則は [バックエンドのアーキテクチャ](design/backend-architecture.md) 第 7 節。

- 認証は IdP（Keycloak）に任せ、自前で作らない。ログインは OIDC、API は OAuth 2.0 の Resource Server として token を検証するだけ。
- 消費者と運営者は IdP の別の realm に分ける。
- 業務のコードは認証の仕組み（Spring Security）に依存しない。
- 公開ネットワークへのデプロイは、その前提（第 16 節）を決めてから行う。

## 10. 外部連携（決済・物流）

細則は [バックエンドのアーキテクチャ](design/backend-architecture.md) 第 8 節。

- 外部サービスは port 経由で呼び、外部の型を業務のコードに持ち込まない。
- sandbox があれば使い、なければ内部の mock にする。設定で切り替える。

## 11. テスト

細則は [テストの書き方](rules/test-writing.md)、実行方法は [テストの実行方法](guides/running-tests.md)。

- 全ての AC にテストを対応させ、テスト名に AC 番号を入れる。
- バックエンドは単体テストと統合テスト（Testcontainers）、フロントは単体テストと結合テスト、つないだ流れは E2E（Playwright）。
- 画面のテストは、画面に見えるもの（文字、ラベル、ボタンの名前）で確認する。見た目は目視で確認する。

## 12. CI/CD

- CI：push と pull request で、バックエンド、フロント、E2E を実行する。文書だけを変えた push では実行しない（文書の参照の検査だけは実行する）。
- CI が失敗している状態で、次の spec の実装に進まない。
- CD：まだない（デプロイ先は未定。第 16 節）。

## 13. 書く言語（日本語と英語の使い分け）

細則は [開発の進め方](rules/workflow.md) 第 3 節。

- Git に入るものは全て日本語で書く（文書、コメント、テスト名、コミットメッセージ、エラーメッセージなど）。
- 識別子（クラス名、API のパス、テーブル名など）は英語。
- 文書の本文は業務の用語（論理名）で書き、識別子（物理名）との対応は [用語集](design/glossary.md) に置く。

## 14. コードの書き方

書き方は [バックエンド](rules/backend-coding.md)、[フロント](rules/frontend-coding.md)、[テスト](rules/test-writing.md)、[コメント](rules/comments.md) の各文書にある。コードを書く前と、[レビュー](rules/review.md)のときに読む。

## 15. 依存とライセンス

細則は [依存とライセンス](design/licenses.md)。

- プロジェクトのライセンスは MIT。
- 依存は、無料で商用に使えて、コピーレフトでない（使う側のコードの公開を求めない）ものだけを使う。CI で確認する。

| 区分 | 当てはまるもの |
|------|----------------|
| 許可 | 無料で商用に使え、コピーレフトでない。改変を禁じるものも、無料で商用に使えれば許可（改変しない） |
| 条件付き | 規模（売上、利用者数など）が一定以内なら無料。改変しなければコピーレフトの義務が生じない |
| 禁止 | 有料、コピーレフト、非商用に限る、ライセンスが分からない |

- 有料版がある製品は、無料版の範囲で使う。

## 16. 未定事項

[TODO 一覧](todo.md) にまとめる。後回しにする作業と、まだ決めていない事項を 1 か所で管理するため。
