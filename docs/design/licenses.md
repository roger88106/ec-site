# 依存とライセンス

[constitution](../constitution.md) 第 15 節の細則。

## 1. プロジェクトのライセンス

MIT。リポジトリ直下の `LICENSE` が正本で、フロントの各プロジェクトの `package.json` の `license` にも同じ識別子を書く。

## 2. 依存のライセンスの方針

実行時・ビルド時・テスト時の全ての依存に当てはめる。無料で商用に使えて、コピーレフトでない（使う側のコードの公開を求めない）ものだけを使う。ライブラリごとの一覧は `scripts/ci/licenses/` に置き、CI で照合する（新しいライブラリが方針に合わなければ CI が失敗する）。

| 区分 | ライセンス | 扱い |
|------|------------|------|
| 許可 | MIT、MIT-0、Apache-2.0、BSD-2-Clause、BSD-3-Clause、ISC、0BSD、BlueOak-1.0.0、CC0-1.0、Zlib、PostgreSQL、Unlicense、Python-2.0。他に、改変を禁じるが無料で商用に使えるもの | そのまま使える。改変を禁じるものは改変しない |
| 条件付き | EPL-1.0・2.0、MPL-2.0、LGPL-2.1・3.0、CDDL、CC-BY-4.0（データ）、OFL-1.1（フォント）、GPL-2.0 with Classpath-exception。他に、規模（売上、利用者数など）が一定以内なら無料のもの | 改変せずに依存として使う場合だけ。規模で無料のものは、その規模以内で使う。ライブラリごとに理由を書いて一覧に載せる |
| 禁止 | GPL（例外なし）、AGPL、SSPL、BUSL、Commons Clause、非商用限定、有料のもの、ライセンスが分からないもの | 使わない |

- 複数のライセンスから選べるもの（`A OR B`）は、許可のライセンスが 1 つ含まれればよい。全てに従うもの（`A AND B`）は、全てが許可か、条件付きの一覧に載っていること。
- 依存を追加するときに、方針に合うかを確認する。合わないライブラリが必要なときは、先に本節を修正する。

## 3. 使っている依存とライセンス

主な依存だけを書く。間接の依存を含む全てのライブラリは、CI のライセンス検査で照合する。

### 3.1 バックエンド

| タイプ | ライブラリ | ライセンス |
|------|------|------------|
| フレームワーク | Spring Boot、Spring Modulith、Spring Security、jMolecules | Apache-2.0 |
| DB | jOOQ、Flyway | Apache-2.0（有料版あり。第 4 節） |
| DB | PostgreSQL の JDBC ドライバー | BSD-2-Clause |
| API・ストレージ・可観測性 | OpenAPI Generator、AWS SDK、Micrometer | Apache-2.0 |
| ユーティリティ | Lombok | MIT |
| 定期ジョブ | ShedLock | Apache-2.0 |
| 決済サービス | stripe-java | MIT |
| メール | Spring Boot のメール（Jakarta Mail、Eclipse Angus Mail） | Apache-2.0。Jakarta Mail と Angus Mail は条件付き（EDL-1.0 を選ぶ。改変しない） |
| ログ | Logback | 条件付き（EPL-2.0 を選ぶ。改変しない） |
| テスト | AssertJ | Apache-2.0 |
| テスト | Testcontainers | MIT |
| テスト | JUnit | 条件付き（EPL-2.0。テストでだけ使い、配布しない） |

### 3.2 フロント

| タイプ | ライブラリ | ライセンス |
|------|------|------------|
| フレームワーク | React、Next.js、Vite、React Router | MIT |
| サーバー状態・フォーム | TanStack Query、React Hook Form | MIT |
| API | openapi-typescript、openapi-fetch、openapi-react-query | MIT |
| ログイン | Auth.js（next-auth） | ISC |
| ログイン | oidc-client-ts | Apache-2.0 |
| ログイン | react-oidc-context | MIT |
| 文言 | next-intl、use-intl | MIT |
| UI | Tailwind CSS、shadcn/ui、Base UI、sonner | MIT |
| グラフ | Recharts | MIT |
| UI | lucide-react | ISC |
| UI | class-variance-authority | Apache-2.0 |
| UI | Geist（管理画面のフォント） | 条件付き（OFL-1.1。フォント単体で販売しない） |
| 開発ツール | Biome | MIT か Apache-2.0 |
| テスト | Vitest、React Testing Library、MSW、jsdom | MIT |
| テスト | Playwright | Apache-2.0 |

ビルドツールの依存のうち、配布物に入らない条件付きのもの（lightningcss、caniuse-lite）は、ライブラリごとの理由を `scripts/ci/licenses/policy.json` に書く。Next.js の画像の最適化の依存（sharp の libvips、LGPL-3.0。動的リンクで使い、改変しない）も同じ。

### 3.3 ミドルウェア（Docker イメージ）

CI のライセンス検査の対象外なので、追加するときに手動で確認する。

| ソフトウェア | ライセンス |
|------|------------|
| PostgreSQL | PostgreSQL License |
| Keycloak | Apache-2.0（コミュニティ版） |
| SeaweedFS、Zipkin、AWS CLI、Stripe CLI（profile `stripe` だけ） | Apache-2.0 |
| imgproxy | MIT（OSS 版。第 4 節） |
| Mailpit | MIT |

### 3.4 データ

CI のライセンス検査の対象外なので、追加するときに手動で確認する。

| データ | 出典 | 利用の条件 |
|--------|------|------------|
| 都道府県・市区町村・郵便番号（地域の最初のデータ） | 日本郵便の「住所の郵便番号（1 レコード 1 行、UTF-8 形式）」 | 日本郵便は郵便番号のデータの著作権を主張せず、自由に配布してよい（日本郵便の郵便番号データの説明）。ダウンロードしたファイルはコミットせず、そこから作成したマイグレーションをコミットする |

## 4. 有料版がある製品

無料で商用に使える版の範囲で使う。有料版の機能が必要になったら、先に本節を修正する。

- **jOOQ**：オープンソース版。PostgreSQL などオープンソースの DB でだけ無料。
- **Flyway**：Community 版の機能だけ。
- **imgproxy**：OSS 版の機能だけを使う。
- **Keycloak**：コミュニティ版（商用サポートは使わない）。
