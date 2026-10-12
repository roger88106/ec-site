# テストの流し方

テストの書き方は [規則の文書](../style/testing.md)、テストの構成と方針は [constitution](../constitution.md) 第 12 節。

## バックエンド

`backend/ecsite/` で実行する。

```bash
# 単体テスト（Spring を起動しない。Docker 不要）
./gradlew test

# 統合テスト（Spring + Testcontainers の PostgreSQL。Docker が必要）
./gradlew integrationTest

# ビルド（単体・統合テスト、OpenAPI の構文の検証を含む。CI と同じ）
./gradlew build
```

- jOOQ のコードと OpenAPI のインターフェースは、コンパイル時に生成される（コミットしない）。
  jOOQ の生成は Testcontainers の PostgreSQL にマイグレーションを適用して行うので、Docker が必要（compose の DB は不要）。
  マイグレーションと生成の設定が変わらなければ、生成は省かれる（`generateJooq UP-TO-DATE`）。そのときは単体テストに Docker は要らない。
- 単体テストには、構成と契約の検査も入る：モジュールの境界（`ModulithTest`）、標準の構成（`architecture/` の ArchUnit）、API の契約の制約の漏れと上限の一致（`contract/`）、エラーの定義とメッセージの対応（`MessageContractTest`）。規則は [バックエンドの書き方](../style/backend.md)。
- `./gradlew check`（`build`）の後に、単体と統合を合わせたカバレッジの報告が `build/reports/jacoco/coverageReport/` にできる（`./gradlew coverageReport` だけでも作れる。統合テストも流れる）。

## 管理画面・店舗画面

`frontend/admin/`・`frontend/store/` で実行する。依存の入れ方は [setup.md](setup.md)。リポジトリのルートの `pnpm lint`・`pnpm typecheck`・`pnpm test`・`pnpm build` は、すべてのプロジェクトでまとめて流す。

```bash
# CI と同じ確認（lint と整形、型チェック、単体・結合テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- `pnpm test` はカバレッジの報告を `coverage/` に作る（`index.html`）。
- 管理画面の結合テスト（画面の操作の流れ）はバックエンドなしで動く。API の応答は MSW で差し替える。書き方は [管理画面の構成](../frontend/admin.md) 第 10 節。
- 要約だけを出す script `scripts/dev/check-frontend.sh` でも流せる（skill `local-dev`）。

## E2E（`e2e/`）

バックエンド、管理画面、店舗画面を起動して、ブラウザ（Chromium）で操作する（Playwright）。
E2E は専用の DB（`ec_site_e2e`）、IdP（Keycloak、:8181）、画像のバケットを使い、ローカルのデータを変えない（spec 004 plan 第 6.1 節）。

```bash
# リポジトリのルートで、PostgreSQL、SeaweedFS、imgproxy、Mailpit などを起動する（E2E 用の Keycloak は準備の script が起動する）
docker compose up -d

# リポジトリのルートで、依存を入れる（管理画面・店舗画面・E2E をまとめて）
pnpm install

# e2e/ で、ブラウザを入れる
pnpm exec playwright install chromium

# E2E。準備（DB と IdP の作り直し）の後に、バックエンド（:8090）、管理画面（:4173）、店舗画面（:3100）を起動してから動かす
pnpm test
```

- バックエンドの起動に JDK 21 を使うので、`JAVA_HOME` を JDK 21 に向けておく。
- 準備の script（`e2e/scripts/prepare.ts`）が、E2E 用の DB と Keycloak の database を作り直し、E2E 用の Keycloak（compose の profile `e2e`）を起動し直す。毎回、マイグレーションと税のデータ（`db/seed/base`）だけの DB と、realm の JSON の状態の IdP から始まる。
- バックエンドは profile `local,e2e`（`application-e2e.yml`）で毎回起動する。手元の開発のバックエンド（:8080）と並べて動かせる。
- すでに起動している管理画面・店舗画面（:4173、:3100）があれば、それを使う。直したときは、止めてからやり直す（ビルドし直すため）。
- テストのデータは管理 API で作り、品番・名称に実行ごとの印（`E2E-<印>-…`）を付ける（同じ実行の中のテストどうしで混ざらないため）。
- compose の設定を変えたコミットを取り込んだ後は、`docker compose up -d` で compose の設定の変更（E2E 用のバケットなど）を反映し、`docker compose run --rm seaweedfs-init` でバケットを作る。`docker/seaweedfs/s3.json` を変えたときは `docker compose restart seaweedfs` も要る。
- 店舗画面の API のキャッシュは、E2E では 1 秒にする（`STORE_API_REVALIDATE_SECONDS`）。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を見る。

