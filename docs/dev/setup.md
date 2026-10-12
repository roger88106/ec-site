# 起動と開発の手順

ローカルで compose・バックエンド・管理画面・店舗画面を起動する手順と、ローカルと CI で使うアカウント。テストの流し方は [testing.md](testing.md)、環境変数は [environment.md](environment.md)。エージェントは skill `local-dev`（[SKILL.md](../../.claude/skills/local-dev/SKILL.md)）の手順を使う。

## 前提

JDK 21、Docker（Docker Desktop など）

バックエンドの Gradle プロジェクトは `backend/ecsite/` にある。バックエンドのコマンドは `backend/ecsite/` で実行する。
IntelliJ IDEA でリポジトリのルートを開いた場合は、`backend/ecsite/build.gradle.kts` を Gradle プロジェクトとしてリンクし、Gradle JVM を 21 にする。
- 前の置き場所（`backend/`）でリンクしていた場合は、Gradle のツールウィンドウで古いプロジェクトのリンクを外し（Unlink Gradle Project）、`backend/ecsite/build.gradle.kts` をリンクし直す。古い `backend/.gradle`・`backend/build` が残っていれば消してよい。

## ローカルで起動する

```bash
# リポジトリのルートで、PostgreSQL、Zipkin、SeaweedFS（画像の置き場所）、imgproxy（画像の変換）、
# Keycloak（認証）、Mailpit（Keycloak が送るメールの受け取り）を起動する
docker compose up -d

# backend/ecsite/ で、local profile で起動する（マイグレーションとローカル用の seed は起動時に適用される）
./gradlew bootRun --args='--spring.profiles.active=local'
```

| URL | 内容 |
|-----|------|
| http://localhost:8080/api/admin | 管理 API（[admin.yaml](../../api/openapi/admin.yaml)） |
| http://localhost:8080/api/store | 店舗 API（[store.yaml](../../api/openapi/store.yaml)） |
| http://localhost:8080/actuator/health | ヘルスチェック（`/liveness`、`/readiness`） |
| http://localhost:9411 | Zipkin（トレースの表示。local では全リクエストを記録する） |
| http://localhost:8333 | SeaweedFS（S3 互換。画像のバケットは `seaweedfs-init` が作る。[plan 001](../specs/001-catalog-and-inventory/plan.md) 第 7 節） |
| http://localhost:8081 | imgproxy（画像の WebP への変換。署名のない URL は拒否する。[plan 001](../specs/001-catalog-and-inventory/plan.md) 第 7.5 節） |
| http://localhost:8180 | Keycloak（認証。管理の画面は `/admin`、`admin` / `admin`。[plan 003](../specs/003-auth/plan.md) 第 2 節） |
| http://localhost:8025 | Mailpit（Keycloak が送ったメール。外には送らない） |

- DB の接続先は [docker/compose.yaml](../../docker/compose.yaml)（ルートの `.env` の `COMPOSE_FILE` が指すので、`docker compose` はリポジトリのルートで実行する） と `application-local.yml` で揃えてある（`localhost:5432/ec_site`）。
- 止めるときは `docker compose stop`。データを消して作り直すときは `docker compose down -v`。
- Keycloak の database（`keycloak`）は、PostgreSQL の volume が空のとき（初回の起動）に作られる。それより前からある volume では作られないので、一度だけ `docker exec -i ec-site-postgres psql -U ec_site -d ec_site < docker/postgres/init-keycloak.sql` を流してから `docker compose up -d` する（`docker compose down -v` で作り直してもよい）。

### 認証（Keycloak）

realm の設定は [docker/keycloak/realms/](../../docker/keycloak/realms) の JSON が正本で、Keycloak の起動時に取り込まれる（[plan 003](../specs/003-auth/plan.md) 第 2 節）。ローカルと CI で使うアカウント（パスワードはローカル専用の値）：

| realm | 画面 | アカウント | パスワード | 職務 |
|-------|------|------------|------------|------|
| `ec-store`（消費者） | 店舗 | `member@example.com` | `member-pass-1` | |
| `ec-store`（消費者） | 店舗 | `member2@example.com` | `member2-pass-1` | （他人のデータが見えないことの確認用） |
| `ec-admin`（運営者） | 管理画面 | `admin@example.com` | `admin-pass-1` | 管理者 |
| `ec-admin`（運営者） | 管理画面 | `viewer@example.com` | `viewer-pass-1` | 閲覧者 |

- 手動確認で使うアカウントは、ここに挙げたもの（realm の JSON にあり、誰の環境にもある）を使う。足りなければ realm の JSON に足し、この表に書く。画面から登録した会員は、その人の環境にしかない。
- 会員登録の確認のメールと、パスワードの再設定のメールは Mailpit（http://localhost:8025）に届く。
- すでにある realm は取り込み直さない。realm の JSON を変えたら、Keycloak の database を作り直して取り込み直す（ローカルで登録した会員も消える）。

  ```bash
  docker compose stop keycloak
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "drop database keycloak" -c "create database keycloak owner keycloak"
  docker compose up -d keycloak
  ```

- token の中身を見るときは `scripts/dev/keycloak.sh token <realm> <アカウント> <パスワード>`（[skill local-dev](../../.claude/skills/local-dev/SKILL.md)）。

## 管理画面（`frontend/admin`）

前提：Node.js 24 以上、pnpm（版は `package.json` の `packageManager`）

```bash
# frontend/admin/ で、依存を入れる
pnpm install

# 開発サーバー（http://localhost:5173）。/api はバックエンド（localhost:8080）に転送する
pnpm dev

# 整形と、自動で直せる lint の指摘を直す
pnpm lint:fix
```

- 先に compose（Keycloak を含む）とバックエンド（local profile）を起動しておく。ログインは「認証（Keycloak）」の運営者のアカウントで行う。
- IdP の接続先は環境変数 `VITE_OIDC_AUTHORITY`（既定 `http://localhost:8180/realms/ec-admin`）、`VITE_OIDC_CLIENT_ID`（既定 `ec-admin-web`）。ビルドのときに埋め込む。
- API の型は [admin.yaml](../../api/openapi/admin.yaml) から `dev`・`typecheck`・`build` の前に生成する（コミットしない）。
- ディレクトリと画面の追加手順は [管理画面の構成](../frontend/admin.md)。

## 店舗画面（`frontend/store`）

前提は管理画面と同じ。

```bash
# frontend/store/ で、依存を入れる
pnpm install

# 開発サーバー（http://localhost:3000）。API はサーバーから呼ぶ（既定は localhost:8080）
pnpm dev
```

- 先に compose（Keycloak を含む）とバックエンド（local profile）を起動しておく。接続先を変えるときは環境変数 `STORE_API_BASE_URL`（例：`http://localhost:8080/api/store`）。
- ログインの接続先と secret は環境変数 `AUTH_KEYCLOAK_ISSUER`、`AUTH_KEYCLOAK_ID`、`AUTH_KEYCLOAK_SECRET`、`AUTH_SECRET`。既定はローカルと CI だけで使う値で、本番相当の環境では必ず設定する（[店舗画面の構成](../frontend/store.md) 第 9 節）。
- API の結果は最大 60 秒キャッシュする（NFR-1）。管理画面での変更は、1 分以内に店舗画面に反映される。
- ディレクトリと画面の追加手順は [店舗画面の構成](../frontend/store.md)。

