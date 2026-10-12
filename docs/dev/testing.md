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
- `./gradlew check`（`build`）の後に、単体と統合を合わせたカバレッジの報告が `build/reports/jacoco/coverageReport/` にできる（`./gradlew coverageReport` だけでも作れる。統合テストも流れる）。

## 管理画面・店舗画面

`frontend/admin/`・`frontend/store/` で実行する。依存の入れ方は [setup.md](setup.md)。

```bash
# CI と同じ確認（lint と整形、型チェック、単体・結合テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- `pnpm test` はカバレッジの報告を `coverage/` に作る（`index.html`）。
- 管理画面の結合テスト（画面の操作の流れ）はバックエンドなしで動く。API の応答は MSW で差し替える。書き方は [管理画面の構成](../frontend/admin.md) 第 10 節。
- 要約だけを出す script `scripts/dev/check-frontend.sh` でも流せる（skill `local-dev`）。

## E2E（`e2e/`）

バックエンド、管理画面、店舗画面を起動して、ブラウザ（Chromium）で操作する（Playwright）。

```bash
# リポジトリのルートで、PostgreSQL、SeaweedFS、Keycloak、Mailpit などを起動する
docker compose up -d

# 管理画面と店舗画面の依存を入れておく（それぞれのディレクトリで pnpm install）

# e2e/ で、依存とブラウザを入れる
pnpm install
pnpm exec playwright install chromium

# E2E。バックエンド（local profile）、管理画面（:4173）、店舗画面（:3100）を起動してから動かす
pnpm test
```

- バックエンドの起動に JDK 21 を使うので、`JAVA_HOME` を JDK 21 に向けておく。
- すでに起動しているサーバー（:8080、:4173、:3100）があれば、それを使う。管理画面・店舗画面を直したときは、止めてからやり直す（ビルドし直すため）。
- テストのデータは管理 API で作り、品番・名称に実行ごとの印（`E2E-<印>-…`）を付ける。消さないので、ローカルの DB に残る。
- 店舗画面の API のキャッシュは、E2E では 1 秒にする（`STORE_API_REVALIDATE_SECONDS`）。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を見る。

