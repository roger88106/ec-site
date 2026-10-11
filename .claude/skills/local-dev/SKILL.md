---
name: local-dev
description: ec-site をローカルで起動・確認する手順。Node・JDK のパス、DB（compose）の起動と接続、バックエンドの local profile での起動と待ち方、フロント（store・admin）の lint・型チェック・テスト・ビルドを要約だけで流すスクリプト。バックエンドやフロントを動かす、テストを流す、DB を見るときに使う。
---

# ローカルでの起動と確認

README の「起動とテスト」を、このマシンでそのまま実行できる形にしたもの。調べ直さずにこの通りに実行する。

## 環境（Bash ツール）

シェルの既定では Node と JDK 21 が見つからない。コマンドごとに次を前に付ける。

```bash
export PATH="/c/Program Files/nodejs:/c/Users/roger/AppData/Roaming/npm:$PATH"   # node、pnpm
export JAVA_HOME=~/.jdks/ms-21.0.12.1                                              # Gradle（既定は JVM 8）
```

## ミドルウェア（compose）

```bash
docker compose up -d          # リポジトリのルートで。PostgreSQL、Zipkin、SeaweedFS、imgproxy
docker compose stop           # 止める（データは残る）
```

- DB：`localhost:5432`、DB `ec_site`、ユーザー `ec_site`、パスワード `ec_site`。
- SQL を見るときはコンテナの psql を使う（ローカルに psql はない）。スキーマはモジュールごと（`\dn`、`\dt <スキーマ>.*` で引く。注文は `ordering`）。

  ```bash
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "select order_number, status from ordering.sales_order order by ordered_at desc limit 5"
  ```

## バックエンド（`backend/`）

起動は `run_in_background` で行い、出力はスクラッチパッドのファイルに捨てる（ログが多いため）。

```bash
cd backend && JAVA_HOME=~/.jdks/ms-21.0.12.1 ./gradlew bootRun --args='--spring.profiles.active=local' > <スクラッチパッド>/bootrun.log 2>&1
```

起動を待つのは sleep ではなく curl の再試行で行う（初回のコンパイル込みで 1〜2 分）。

```bash
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health
```

- 200 が出たら起動済み。すでに起動しているかは同じ curl を `--retry` なしで確かめる。
- 起動に失敗したら `bootrun.log` を `grep -n "ERROR\|Caused by" ` で見る。全体を読まない。
- local profile では、起動時にマイグレーションとローカル用の seed（`db/seed/local`）が適用される。
- 止める（PowerShell）：`Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess`

テスト（`backend/` で。Docker が要る）：

```bash
JAVA_HOME=~/.jdks/ms-21.0.12.1 ./gradlew check -q --console=plain 2>&1 | tail -40
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

- PATH はスクリプトの中で通す。末尾の行数は `TAIL_LINES`（既定 30）、ログの置き場所は `LOG_DIR`（既定は一時ディレクトリ）。
- 整形と自動で直せる lint は、各アプリで `pnpm lint:fix`。
- 型（`src/api/generated/`）は `typecheck`・`build`・`dev` の前に OpenAPI から生成される。

開発サーバーは Bash ではなく、プレビューのツール（`preview_start`）で `.claude/launch.json` の名前を指定して起動する。

| 名前 | URL | 前提 |
|------|-----|------|
| `admin` | http://localhost:5173 | バックエンド（`/api` を :8080 に転送） |
| `store` | http://localhost:3000 | バックエンド（API はサーバーから呼ぶ） |

- 店舗画面の一覧は API の結果を最大 60 秒キャッシュする。管理画面での変更がすぐ出ないのは正常。

## E2E（`frontend/e2e`）

README の「E2E」の通り。バックエンド（:8080）、管理画面（:4173）、店舗画面（:3100）は、起動していなければ Playwright が起動する。

```bash
cd frontend/e2e && pnpm test 2>&1 | tail -30
```
