---
name: local-dev
description: ec-site をローカルで起動・確認する手順。DB・Keycloak（compose）の起動と接続、token の中身の確認、バックエンドの local profile での起動と待ち方、フロント（store・admin）の lint・型チェック・テスト・ビルドを要約だけで流すスクリプト。バックエンドやフロントを動かす、テストを流す、DB や token を見るときに使う。
---

# ローカルでの起動と確認

README の「起動とテスト」を、エージェントがそのまま実行できる形にしたもの。調べ直さずにこの通りに実行する。

前提：JDK 21、Node.js と pnpm、Docker が使えること。マシンに固有のこと（JDK の場所、PATH、OS の癖）は、`/.local/agent/environment.md` があればそれに従う（AGENTS.md「環境」）。

## ミドルウェア（compose）

```bash
docker compose up -d          # リポジトリのルートで。PostgreSQL、Zipkin、SeaweedFS、imgproxy、Keycloak、Mailpit
docker compose stop           # 止める（データは残る）
```

- Keycloak は起動に 20 秒ほどかかる。`docker inspect -f '{{.State.Health.Status}}' ec-site-keycloak` が `healthy` になるまで待つ。
- Keycloak の起動に失敗し、ログに database `keycloak` がないと出たら、README「ローカルで起動する」の手順で一度だけ作る。

- DB：`localhost:5432`、DB `ec_site`、ユーザー `ec_site`、パスワード `ec_site`。
- SQL を見るときはコンテナの psql を使う（ローカルに psql はない）。スキーマはモジュールごと（`\dn`、`\dt <スキーマ>.*` で引く。注文は `ordering`）。

  ```bash
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "select order_number, status from ordering.sales_order order by ordered_at desc limit 5"
  ```

## 認証（Keycloak、Mailpit）

realm の設定は `keycloak/realms/` の JSON。アカウントと、JSON を変えたときの取り込み直しは README「認証（Keycloak）」。

```bash
scripts/dev/keycloak.sh token ec-admin admin@example.com admin-pass-1   # access token の payload を出す（権限・audience の確認）
scripts/dev/keycloak.sh token ec-store member@example.com member-pass-1
scripts/dev/keycloak.sh access-token ec-store member@example.com member-pass-1  # token そのもの（curl で API を呼ぶとき）
scripts/dev/keycloak.sh admin GET /admin/realms/ec-store                # 管理 API（realm の設定を読む）
curl -s http://localhost:8025/api/v1/messages | head -c 500              # Mailpit に届いたメールの一覧
```

- `token`・`access-token` は、web の client のパスワードによる取得（Direct Access Grant）を一時的に有効にし、終わったら無効に戻す。途中で止めたら、`admin` で `directAccessGrantsEnabled` が `false` か確かめる。
- 管理 API で設定を変えても、realm の JSON は変わらない。残す設定は JSON を直し、取り込み直して確かめる。

## バックエンド（`backend/`）

起動はバックグラウンドで行い、出力はファイルに捨てる（ログが多いため）。

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' > <作業用のディレクトリ>/bootrun.log 2>&1
```

起動を待つのは sleep ではなく curl の再試行で行う（初回のコンパイル込みで 1〜2 分）。

```bash
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health
```

- 200 が出たら起動済み。すでに起動しているかは同じ curl を `--retry` なしで確かめる。
- 起動に失敗したら `bootrun.log` を `grep -n "ERROR\|Caused by" ` で見る。全体を読まない。
- local profile では、起動時にマイグレーションとローカル用の seed（`db/seed/local`）が適用される。
- 止めるときは、ポート 8080 で待ち受けているプロセスを止める。

テスト（`backend/` で。Docker が要る）：

```bash
./gradlew check -q --console=plain 2>&1 | tail -40
```

- `check` は単体・統合テストと OpenAPI の検証を含む（AGENTS.md の完了の基準）。一部だけなら `test`（単体）、`integrationTest`（統合）、`--tests '<クラス名>'`。
- 失敗の詳細は `build/test-results/**/TEST-*.xml` を、失敗したクラスだけ grep する。

## フロント（`frontend/store`、`frontend/admin`）

lint・型チェック・テスト・ビルドは、手順ごとに 1 行の要約だけを出すスクリプトで流す。失敗した手順だけ、出力の末尾とログのパスが出る。

```bash
scripts/check-frontend.sh                       # 両方のアプリで lint typecheck test build（CI と同じ）
scripts/check-frontend.sh admin test            # 管理画面のテストだけ
scripts/check-frontend.sh store lint typecheck  # 対象と手順を選ぶ
```

- 末尾の行数は `TAIL_LINES`（既定 30）、ログの置き場所は `LOG_DIR`（既定は一時ディレクトリ）。
- 整形と自動で直せる lint は、各アプリで `pnpm lint:fix`。
- 型（`src/api/generated/`）は `typecheck`・`build`・`dev` の前に OpenAPI から生成される。

開発サーバーは Bash ではなく、プレビューのツール（`preview_start`）で `.claude/launch.json` の名前を指定して起動する。

| 名前 | URL | 前提 |
|------|-----|------|
| `admin` | http://localhost:5173 | バックエンド（`/api` を :8080 に転送） |
| `store` | http://localhost:3000 | バックエンド（API はサーバーから呼ぶ） |

- 店舗画面の一覧は API の結果を最大 60 秒キャッシュする。管理画面での変更がすぐ出ないのは正常。

## 動作確認用の注文を作る

画面を確かめるための注文は、ブラウザで操作せずにスクリプトで作る（店舗 API を直接呼ぶ。バックエンドと Keycloak が要る）。会員（既定は README の `member@example.com`）の token を `keycloak.sh access-token` で取り、会員のカートを空にしてから注文する。

```bash
node scripts/dev/mkorder.js                          # seed のバリアント（在庫 100）を 1 つ注文する
node scripts/dev/mkorder.js --qty 2 --request        # 注文して、キャンセルを申請する（理由は既定の文）
node scripts/dev/mkorder.js --request '色違いと間違えた' --name '佐藤 花子'
node scripts/dev/mkorder.js --variant <バリアント ID>
node scripts/dev/mkorder.js --member member2@example.com member2-pass-1   # 別の会員で注文する
```

- 出力は「注文 ID、注文番号、状態」の 1 行と、店舗画面・管理画面の注文の URL。店舗画面の注文は、注文した会員でログインしないと開けない。
- 承認・却下・運営者のキャンセルは管理画面か管理 API（`PUT /api/admin/orders/{orderId}/cancellation-request/approve` など）で行う。
- seed の在庫は 3021（在庫 100）、3001（10）、3003（5）、3011（3）、3013（8）、3031（20）。3002・3004・3012 は在庫 0（バリアント ID は `00000000-0000-0000-0000-00000000` + 4 桁）。
- 失敗した手順と API のエラーの本文が出る（在庫不足なら「品目の追加に失敗しました（HTTP 400）」）。

## E2E（`frontend/e2e`）

README の「E2E」の通り。バックエンド（:8080）、管理画面（:4173）、店舗画面（:3100）は、起動していなければ Playwright が起動する。

```bash
cd frontend/e2e && pnpm test 2>&1 | tail -30
```
