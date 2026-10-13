---
name: local-dev
description: ec-site をローカルで起動・確認する手順。DB・Keycloak（compose）の起動と接続、token の中身の確認、バックエンドの local profile での起動と待ち方、フロント（store・admin）の lint・型チェック・テスト・ビルドを要約だけで流すスクリプト。バックエンドやフロントを動かす、テストを流す、DB や token を見るときに使う。
---

# ローカルでの起動と確認

`docs/dev/`（起動とテストの手順）を、エージェントがそのまま実行できる形にしたもの。調べ直さずにこの通りに実行する。

前提：JDK 21、Node.js と pnpm、Docker が使えること。マシンに固有のこと（JDK の場所、PATH、OS の癖）は、`/.local/agent/environment.md` があればそれに従う（AGENTS.md「環境」）。

## ミドルウェア（compose）

```bash
docker compose up -d          # リポジトリのルートで。PostgreSQL、Zipkin、SeaweedFS、imgproxy、Keycloak、Mailpit
docker compose stop           # 止める（データは残る）
```

- Keycloak は起動に 20 秒ほどかかる。`docker inspect -f '{{.State.Health.Status}}' ec-site-keycloak` が `healthy` になるまで待つ。
- Keycloak の起動に失敗し、ログに database `keycloak` がないと出たら、`docs/dev/setup.md`「ローカルで起動する」の手順で一度だけ作る。

- DB：`localhost:5432`、DB `ec_site`、ユーザー `ec_site`、パスワード `ec_site`。
- SQL を見るときはコンテナの psql を使う（ローカルに psql はない）。スキーマはモジュールごと（`\dn`、`\dt <スキーマ>.*` で引く。注文は `ordering`）。

  ```bash
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "select order_number, status from ordering.sales_order order by ordered_at desc limit 5"
  ```

## 認証（Keycloak、Mailpit）

realm の設定は `docker/keycloak/realms/` の JSON。アカウントと、JSON を変えたときの取り込み直しは `docs/dev/setup.md`「認証（Keycloak）」。

```bash
scripts/dev/keycloak.sh token ec-admin admin@example.com admin-pass-1   # access token の payload を出す（権限・audience の確認）
scripts/dev/keycloak.sh token ec-store member@example.com member-pass-1
scripts/dev/keycloak.sh access-token ec-store member@example.com member-pass-1  # token そのもの（curl で API を呼ぶとき）
scripts/dev/keycloak.sh admin GET /admin/realms/ec-store                # 管理 API（realm の設定を読む）
curl -s http://localhost:8025/api/v1/messages | head -c 500              # Mailpit に届いたメールの一覧
```

- `token`・`access-token` は、web の client のパスワードによる取得（Direct Access Grant）を一時的に有効にし、終わったら無効に戻す。途中で止めたら、`admin` で `directAccessGrantsEnabled` が `false` か確かめる。
- 管理 API で設定を変えても、realm の JSON は変わらない。残す設定は JSON を直し、取り込み直して確かめる。

## バックエンド（`backend/ecsite/`）

起動はバックグラウンドで行い、出力はファイルに捨てる（ログが多いため）。

```bash
cd backend/ecsite && ./gradlew bootRun --args='--spring.profiles.active=local' > <作業用のディレクトリ>/bootrun.log 2>&1
```

起動を待つのは sleep ではなく curl の再試行で行う（初回のコンパイル込みで 1〜2 分）。

```bash
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health
```

- 200 が出たら起動済み。すでに起動しているかは同じ curl を `--retry` なしで確かめる。
- 起動に失敗したら `bootrun.log` を `grep -n "ERROR\|Caused by" ` で見る。全体を読まない。
- local profile では、起動時にマイグレーションと seed（`db/seed/base`、`db/seed/local`）が適用される。使い分けは `db/seed/local/README.md`。
- 止めるときは、ポート 8080 で待ち受けているプロセスを止める。

テスト（`backend/ecsite/` で。Docker が要る）：

```bash
./gradlew check -q --console=plain 2>&1 | tail -40
```

- `check` は単体・統合テストと OpenAPI の検証を含む（AGENTS.md の完了の基準）。一部だけなら `test`（単体）、`integrationTest`（統合）、`--tests '<クラス名>'`。
- 失敗の詳細は `build/test-results/**/TEST-*.xml` を、失敗したクラスだけ grep する。

## フロント（`frontend/store`、`frontend/admin`）

lint・型チェック・テスト・ビルドは、手順ごとに 1 行の要約だけを出すスクリプトで流す。失敗した手順だけ、出力の末尾とログのパスが出る。

```bash
scripts/dev/check-frontend.sh                       # 両方のアプリで lint typecheck test build（CI と同じ）
scripts/dev/check-frontend.sh admin test            # 管理画面のテストだけ
scripts/dev/check-frontend.sh store lint typecheck  # 対象と手順を選ぶ
```

- 末尾の行数は `TAIL_LINES`（既定 30）、ログの置き場所は `LOG_DIR`（既定は一時ディレクトリ）。
- 整形と自動で直せる lint は、各アプリで `pnpm lint:fix`。
- 型（`src/api/generated/`）は `typecheck`・`build`・`dev` の前に OpenAPI から生成される。
- 依存を足した・上げたときは、ルートで `pnpm install --frozen-lockfile` とライセンスの検査を流し直す（`docs/frontend/coding.md` 第 9.1 節）。手元は依存が入った後なので、テストが通っても CI のインストールで失敗することがある。

開発サーバーは Bash ではなく、プレビューのツール（`preview_start`）で `.claude/launch.json` の名前を指定して起動する。

| 名前 | URL | 前提 |
|------|-----|------|
| `admin` | http://localhost:5173 | バックエンド（`/api` を :8080 に転送） |
| `store` | http://localhost:3000 | バックエンド（API はサーバーから呼ぶ） |

- 店舗画面の一覧は API の結果を最大 60 秒キャッシュする。管理画面での変更がすぐ出ないのは正常。

## ローカルの画面のデータ

local profile の seed（`db/seed/local/`）が、画面を確かめるためのデータを作る。注文を作るスクリプトは要らない。

- 商品 15（公開 13、非公開 1、公開の予約 1）、カテゴリ 13、商品画像 31。在庫切れ（`TEE-001` の L、`PANTS-001-M`、`SENBEI-001`）と残りわずか（`TEE-002-M`、`JUICE-001`、`MUG-001-GY` など）がある。
- 会員 `member@example.com` の注文 4 件：受付、キャンセルの申請中、申請の却下（受付のまま）、キャンセル済み。承認・却下・運営者のキャンセルは、申請中の注文で管理画面か管理 API（`PUT /api/admin/orders/{orderId}/cancellation-request/approve` など）から試す。
- ID は固定（`00000000-0000-0000-0000-00000000` + 4 桁）：カテゴリ 1xxx、商品 2xxx、バリアント 3xxx、在庫 4xxx、会員 6001、注文 61xx、アセット 7xxx、商品画像 8xxx。
- 画面で変えたデータは、seed を流し直しても戻らない。最初の状態に戻すときは、ローカルの DB を作り直す（`docs/dev/setup.md`）。

## E2E（`e2e/`）

`docs/testing/running.md` の「E2E」の通り。準備の script が E2E 用の DB と Keycloak（:8181）を作り直し、Playwright がバックエンド（:8090、profile `local,e2e`）を毎回起動する。管理画面（:4173）、店舗画面（:3100）は、起動していなければ起動する。ローカルのデータ（:8080 の DB、:8180 の Keycloak）は変わらない。

```bash
cd e2e && pnpm test 2>&1 | tail -30
```
