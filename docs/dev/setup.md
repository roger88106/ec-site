# 開発環境の起動

ローカルで compose・バックエンド・管理画面・店舗画面を起動する手順と、使うアカウント。テストは [テストの流し方](../testing/running.md)、環境変数は [環境変数](environment.md)。エージェントは skill `local-dev` を使う。

## 1. 前提

JDK 21、Docker、Node.js 24 以上、pnpm（版は `package.json` の `packageManager`）。

IntelliJ IDEA でルートを開いたら、`backend/ecsite/build.gradle.kts` を Gradle プロジェクトとしてリンクし、Gradle JVM を 21 にする。

## 2. 起動

```bash
# ルートで：PostgreSQL、Zipkin、SeaweedFS、imgproxy、Keycloak、Mailpit を起動する
docker compose up -d

# ルートで：フロントの依存を入れる（管理画面・店舗画面・E2E をまとめて）
pnpm install

# backend/ecsite/ で：local profile で起動する（マイグレーションとローカル用の seed も流れる）
./gradlew bootRun --args='--spring.profiles.active=local'

# frontend/admin/ と frontend/store/ で：開発サーバー
pnpm dev
```

| URL | 内容 |
|-----|------|
| http://localhost:5173 | 管理画面（`/api` をバックエンドに転送する） |
| http://localhost:3000 | 店舗画面（API はサーバーから呼ぶ） |
| http://localhost:8080/api/admin | 管理 API（[admin.yaml](../../api/openapi/admin.yaml)） |
| http://localhost:8080/api/store | 店舗 API（[store.yaml](../../api/openapi/store.yaml)） |
| http://localhost:8080/actuator/health | ヘルスチェック（`/liveness`、`/readiness`） |
| http://localhost:9411 | Zipkin（トレース。local では全リクエストを記録） |
| http://localhost:8333 | SeaweedFS（S3 互換。バケットは `seaweedfs-init` が作る） |
| http://localhost:8081 | imgproxy（画像の WebP への変換。署名のない URL は拒否） |
| http://localhost:8180 | Keycloak（管理画面は `/admin`、`admin` / `admin`） |
| http://localhost:8025 | Mailpit（Keycloak が送ったメール。外には送らない） |

- `docker compose` はルートで実行する（ルートの `.env` の `COMPOSE_FILE` が [docker/compose.yaml](../../docker/compose.yaml) を指す）。DB は `localhost:5432/ec_site`。
- 止めるときは `docker compose stop`、データを消して作り直すときは `docker compose down -v`。
- 店舗画面は API の結果を最大 60 秒キャッシュするので、管理画面での変更は 1 分以内に反映される。
- フロントの API の型は、`dev`・`typecheck`・`build` の前に OpenAPI から生成される（コミットしない）。整形と自動で直せる lint は `pnpm lint:fix`。

## 3. アカウント

realm の設定は [docker/keycloak/realms/](../../docker/keycloak/realms) の JSON が正本で、Keycloak の起動時に取り込まれる。ローカルと CI で使うアカウント（パスワードはローカル専用）：

| realm | 画面 | アカウント | パスワード | 用途 |
|-------|------|------------|------------|------|
| `ec-store`（消費者） | 店舗 | `member@example.com` | `member-pass-1` | seed の注文を持つ会員 |
| `ec-store`（消費者） | 店舗 | `member2@example.com` | `member2-pass-1` | 他人のデータが見えないことの確認 |
| `ec-admin`（運営者） | 管理画面 | `admin@example.com` | `admin-pass-1` | 管理者 |
| `ec-admin`（運営者） | 管理画面 | `viewer@example.com` | `viewer-pass-1` | 閲覧者 |

- 手動確認はこの表のアカウントを使う（誰の環境にもある）。足りなければ realm の JSON に足し、表にも書く。画面から登録した会員は、その人の環境にしかない。
- 会員のアカウント ID は realm の JSON で固定している（`member@example.com` は `00000000-0000-0000-0000-000000009001`、`member2@example.com` は `…9002`）。ローカル用の seed の注文が、この会員のものになるため。
- 会員登録の確認とパスワードの再設定のメールは Mailpit に届く。
- token の中身は `scripts/dev/keycloak.sh token <realm> <アカウント> <パスワード>` で見る。

## 4. データの作り直し

画面で変えたデータは、seed を流し直しても戻らない。

| 戻したいもの | 手順 |
|--------------|------|
| DB だけ（Keycloak と画像はそのまま） | バックエンドを止めてから、下の 1 つ目のコマンド。次の起動で seed が流れる |
| Keycloak（realm の JSON を変えたとき） | 下の 2 つ目のコマンド（ローカルで登録した会員も消える） |
| すべて | `docker compose down -v` |

```bash
# DB を作り直す
docker exec ec-site-postgres psql -U ec_site -d postgres -c "drop database ec_site with (force)" -c "create database ec_site owner ec_site"

# Keycloak の database を作り直して、realm を取り込み直す
docker compose stop keycloak
docker exec ec-site-postgres psql -U ec_site -d ec_site -c "drop database keycloak" -c "create database keycloak owner keycloak"
docker compose up -d keycloak
```

Keycloak の database（`keycloak`）は、PostgreSQL の volume が空のとき（初回）にだけ作られる。それより前からある volume では、一度だけ `docker exec -i ec-site-postgres psql -U ec_site -d ec_site < docker/postgres/init-keycloak.sql` を流してから `docker compose up -d` する。

## 5. Stripe で動かす

既定の起動（profile `local`）は、決済サービスに mock を使い、外部に接続しない。Stripe の sandbox で確かめるときだけ、次の手順で切り替える。決済の設計は [決済モジュール](../backend/modules/payment.md)。

1. Stripe のアカウントと sandbox を作り、ダッシュボードの「開発者 → API キー」のシークレットキー（`sk_test_` で始まる）を、`/.local/stripe.env` に書く。このファイルは Git に入らない。鍵を会話やコミットに貼らない。

   ```bash
   STRIPE_SECRET_KEY=sk_test_...
   STRIPE_WEBHOOK_SECRET=
   ```

2. 通知の署名の鍵（`whsec_` で始まる）を取り、同じファイルの `STRIPE_WEBHOOK_SECRET` に書く。API キーが変わらなければ、鍵も変わらないので 1 回だけでよい。

   ```bash
   docker compose --profile stripe run --rm stripe-cli 'stripe listen --api-key "$STRIPE_SECRET_KEY" --print-secret'
   ```

3. 通知の転送と、バックエンドを起動する。

   ```bash
   # ルートで：Stripe の通知を http://localhost:8080/api/webhooks/stripe に転送する
   docker compose --profile stripe up -d stripe-cli

   # backend/ecsite/ で：profile stripe を重ねる（/.local/stripe.env を読み、決済サービスを Stripe にする）
   ./gradlew bootRun --args='--spring.profiles.active=local,stripe'
   ```

- 転送した通知と応答は `docker logs -f ec-site-stripe-cli` で見る。止めるときは `docker compose --profile stripe stop stripe-cli`。
- テストのカード番号は Stripe の文書のもの（成功は `4242 4242 4242 4242`、拒否は `4000 0000 0000 0002`。有効期限は未来の日付、セキュリティコードは任意の 3 桁）。
- 支払の画面に出る支払の方法は、ダッシュボードの「設定 → 決済手段」で決まる。カードと、カードで払うウォレット（Apple Pay、Google Pay、Link）だけを有効にする。カード以外の方法は与信だけを取れない。
- 期限切れを早く確かめるときは、`--ecsite.payment.deadline=PT3M --ecsite.payment.expiry-interval=PT20S` を足す。Stripe の支払の画面は作ってから 31 分で切れるが、支払の期限で期限切れのジョブが閉じる。
