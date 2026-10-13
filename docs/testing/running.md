# テストの流し方

テストの書き方は [テストの書き方](writing.md)、起動の準備は [開発環境の起動](../dev/setup.md)。エージェントは skill `local-dev` を使う。

## 1. バックエンド

`backend/ecsite/` で実行する。

| コマンド | 内容 | Docker |
|----------|------|--------|
| `./gradlew test` | 単体テスト（Spring を起動しない） | 不要 ※1 |
| `./gradlew integrationTest` | 統合テスト（Spring + Testcontainers の PostgreSQL など） | 必要 |
| `./gradlew build`（`check`） | 単体・統合テスト、OpenAPI の構文の検証（CI と同じ） | 必要 |
| `./gradlew coverageReport` | 単体と統合を合わせたカバレッジ（`build/reports/jacoco/coverageReport/`。`check` の後にもできる） | 必要 |

※1：jOOQ のコード生成は Testcontainers の PostgreSQL にマイグレーションを流して行うので、マイグレーションか生成の設定が変わったときだけ Docker が必要（変わらなければ `generateJooq UP-TO-DATE` で省かれる）。

単体テストには、構成と契約の検査も入る：モジュールの境界（`ModulithTest`）、標準の構成（`architecture/` の ArchUnit）、契約の制約（`contract/`）、エラーの定義とメッセージの対応（`MessageContractTest`）。

## 2. 管理画面・店舗画面

`frontend/admin/`・`frontend/store/` で実行する（ルートで流すと、すべてのプロジェクトをまとめて流す）。

```bash
# CI と同じ確認（lint と整形、型チェック、テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- `pnpm test` はカバレッジの報告を `coverage/index.html` に作る。
- 管理画面の結合テストはバックエンドなしで動く（API の応答は MSW）。
- 要約だけを出す `scripts/dev/check-frontend.sh` でも流せる。

## 3. E2E（`e2e/`）

バックエンド、管理画面、店舗画面を起動して、Chromium で操作する（Playwright）。専用の DB（`ec_site_e2e`）、IdP（Keycloak、:8181）、画像のバケットを使い、ローカルのデータを変えない。

```bash
# ルートで：compose を起動し、依存を入れる（E2E 用の Keycloak は準備の script が起動する）
docker compose up -d
pnpm install

# e2e/ で：初回だけブラウザを入れ、E2E を流す
pnpm exec playwright install chromium
pnpm test
```

| 段階 | 内容 |
|------|------|
| 準備（`e2e/scripts/prepare.ts`） | E2E 用の DB と Keycloak の database を作り直し、E2E 用の Keycloak（compose の profile `e2e`）を起動し直す。毎回、マイグレーションと税のデータ（`db/seed/base`）だけの DB、realm の JSON の状態の IdP から始まる |
| 起動 | バックエンド（:8090、profile `local,e2e`）を毎回起動する。管理画面（:4173）と店舗画面（:3100）は、起動していなければ起動する |
| データ | 管理 API で作り、品番・名称に実行ごとの印（`E2E-<印>-…`）を付ける |

- バックエンドの起動に JDK 21 を使うので、`JAVA_HOME` を JDK 21 に向けておく。
- 手元の開発のバックエンド（:8080）と並べて動かせる。
- 起動済みの管理画面・店舗画面を直したときは、止めてからやり直す（ビルドし直すため）。
- 店舗画面の API のキャッシュは、E2E では 1 秒（`STORE_API_REVALIDATE_SECONDS`）。
- 決済は mock で、外部の決済サービスに接続しない。支払の期限は、期限切れを待つテストのため 30 秒（`application-e2e.yml`）。
- compose の設定を変えたコミットを取り込んだら、`docker compose up -d` で反映し、`docker compose run --rm seaweedfs-init` でバケットを作る（`docker/seaweedfs/s3.json` を変えたときは `docker compose restart seaweedfs` も必要）。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を見る。
