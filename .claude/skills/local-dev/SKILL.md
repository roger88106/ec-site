---
name: local-dev
description: ec-site をローカルで起動・確認する手順。DB・Keycloak（compose）の起動と接続、token の中身の確認、バックエンドの local profile での起動と待ち方、フロント（store・admin）と E2E を要約だけで流すスクリプト、commit の前の CI の検査、プレビューでの画面の確かめ方。バックエンドやフロントを動かす、テストや E2E を流す、commit する、画面を確かめる、DB や token を見るときに使う。
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
- Keycloak の起動に失敗し、ログに database `keycloak` がないと出たら、`scripts/dev/reset.sh keycloak` で作り直す（`docs/dev/setup.md` 第 4 節。database は compose の PostgreSQL を初めて作ったときだけ自動で作られる）。

- DB：`localhost:5432`、DB `ec_site`、ユーザー `ec_site`、パスワード `ec_site`。
- SQL を見るときはコンテナの psql を使う（ローカルに psql はない）。スキーマはモジュールごと（`\dn`、`\dt <スキーマ>.*` で引く。注文は `ordering`）。

  ```bash
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "select order_number, status from ordering.sales_order order by ordered_at desc limit 5"
  ```

## 認証（Keycloak、Mailpit）

realm の設定は `docker/keycloak/realms/` の JSON。アカウントと、JSON を変えたときの取り込み直しは、`docs/dev/setup.md` 第 3 節（アカウント）と `docs/dev/setup.md` 第 4 節（取り込み直し）。

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
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:9080/actuator/health
```

- 死活監視は運用ポート（9080）で出す。業務ポート（8080）では出さない。
- 200 が出たら起動済み。すでに起動しているかは同じ curl を `--retry` なしで確かめる。
- 起動に失敗したら `bootrun.log` を `grep -n "ERROR\|Caused by" ` で見る。全体を読まない。
- local profile では、起動時にマイグレーションと seed（`db/seed/base`、`db/seed/local`）が適用される。使い分けは `db/seed/local/README.md`。
- 止めるときは、ポート 8080 で待ち受けているプロセスを止める。

テスト（`backend/ecsite/` で。Docker が要る）：

```bash
./gradlew check -q --console=plain 2>&1 | tail -40
```

- `check` は単体・統合テストと OpenAPI の検証を含む（AGENTS.md の完了の基準）。一部だけなら `test`（単体）、`integrationTest`（統合）、`--tests '<クラス名>'`。
- `build.gradle.kts` を変えたら、テストの前に `./gradlew help -q` でビルドの設定を評価する。書き誤りが、テストや E2E の途中まで見えないため。
- 長い検査は、最初からバックグラウンドで実行する（Bash の上限 10 分を超えて途中で背景に回ると、結果の読み方が変わるため）。目安：`./gradlew check` 約 10 分、`check-frontend.sh` 約 1.5 分、`e2e.sh` 約 2 分、push の後の CI 約 8 分（`gh run watch --exit-status`）。`./gradlew check` と `check-frontend.sh` は続けて実行し、同時に実行しない（CPU が足りずに管理画面のテストが時間切れになる）。
- 失敗の詳細は `build/test-results/**/TEST-*.xml` を、失敗したクラスだけ grep する。

## フロント（`frontend/store`、`frontend/admin`）

lint・型チェック・テスト・ビルドは、手順ごとに 1 行の要約だけを出すスクリプトで流す。失敗した手順だけ、出力の末尾とログのパスが出る。

```bash
scripts/dev/check-frontend.sh                       # 依存の入れ直しとライセンスの後に、共有パッケージで lint typecheck test、両方のアプリで lint typecheck test build、E2E のコードで lint typecheck（CI と同じ）
scripts/dev/check-frontend.sh admin test            # 管理画面のテストだけ
scripts/dev/check-frontend.sh store lint typecheck  # 対象と手順を選ぶ
```

- 末尾の行数は `TAIL_LINES`（既定 30）、ログの置き場所は `LOG_DIR`（既定は一時ディレクトリ）。
- 整形と自動で直せる lint は、各アプリで `pnpm lint:fix`。1 つのファイルだけなら、そのアプリのディレクトリで `npx biome check --write <ファイル>`（ルートから流すと、設定が入れ子だというエラーになる）。
- 1 つのテストのファイルを書いている間は、アプリのディレクトリで `npx tsc --noEmit` を先に流してから `npx vitest run <ファイル>` を流す。Vitest は型を確かめないので、テストデータの形の誤りが、テストの失敗として遠回りに見える。出力は `| grep -E "^\s+×|Tests|AssertionError" | head` で要約だけを読む（失敗した toContain は HTML の全体を出す）。
- 型（`src/api/generated/`）は `typecheck`・`build`・`dev` の前に OpenAPI から生成される。
- 依存を足した・上げたときは、ルートで `pnpm install --frozen-lockfile` とライセンスの検査を流し直す（`docs/frontend/coding.md` 第 9.1 節）。手元は依存が入った後なので、テストが通っても CI のインストールで失敗することがある。

開発サーバーは Bash ではなく、プレビューのツール（`preview_start`）で `.claude/launch.json` の名前を指定して起動する。

| 名前 | URL | 前提 |
|------|-----|------|
| `admin` | http://localhost:5173 | バックエンド（`/api` を :8080 に転送） |
| `store` | http://localhost:3000 | バックエンド（API はサーバーから呼ぶ） |

- 店舗画面の一覧は API の結果を最大 60 秒キャッシュする。管理画面での変更がすぐ出ないのは正常。

### プレビューで画面を確かめる

エージェントが実装の途中で表示を確かめるときの、ツールの使い方。利用者が普段の流れで画面を確かめる段階（skill `new-screen` の「画面を足し終えたら」）は別に残す。

- IdP のログインの画面は、描画の前だとキーの入力やクリックが失敗する。`find` で入力欄を探し、`form_input` で local の seed のアカウント（`docs/dev/setup.md`）を入れ、`javascript_tool` で `document.querySelector('form').submit()` を呼ぶ。
- 表示の中身は `get_page_text` で読む。スクリーンショットは描画を待って時間切れになりやすいので、見た目を見るときだけ使う。
- 開く画面の ID は DB から引く（例：注文と支払は `ordering.sales_order` と `payment.payment` を結ぶ）。

## CI の検査の script

`./gradlew check` と `check-frontend.sh` に入っていない CI の検査。commit の前にルートで流す（hook を置いていれば commit のときに流れる）。各検査の中身、失敗したときの直し方、除外の書き方は [CI の検査](../../../docs/dev/ci.md)。

```bash
scripts/ci/check-all.sh
```

手元では、テスト名に出ない AC は警告だけで通る。spec の最後（push の前）は、CI と同じに確かめる。

```bash
CI=true scripts/ci/check-all.sh
```

## 決済（mock と Stripe）

- profile `local` は mock（`application-local.yml` の mock フラグ。物流も mock）。注文すると `http://localhost:8080/mock-payment/{ID}` の支払の画面に移る（「支払う」「カードを拒否させる」「やめる」）。
- Stripe の sandbox は `docs/dev/setup.md`「Stripe で動かす」の通り。鍵は `/.local/stripe.env`（なければ利用者に頼む。鍵を会話に貼らせない）。

  ```bash
  docker compose --profile stripe up -d stripe-cli                   # ルートで。通知を :8080 に転送する
  ./gradlew bootRun --args='--spring.profiles.active=local,stripe'     # backend/ecsite/ で
  docker logs ec-site-stripe-cli 2>&1 | grep -- '-->' | tail           # 転送した通知（ログ全体は署名の鍵を含むので出さない）
  ```

- 期限切れを早く確かめるときは、`--args` に `--ecsite.payment.deadline=PT3M --ecsite.payment.expiry-interval=PT20S` を足す。
- 支払は `payment.payment`（`status`、`page_id`、`authorization_ref`）、注文は `ordering.sales_order`。
- Stripe の Checkout の画面は描くのが遅く、入力が遅れて二重に入ることがある。入力の後に拡大して確かめてから支払う。

## 機能紹介のページのスクリーンショット

`docs/dev/setup.md`「機能紹介のページのスクリーンショット」の通り。DB を作り直してから、`e2e/` で `pnpm capture`。

## ローカルの画面のデータ

local profile の seed（`db/seed/local/`）が、画面を確かめるためのデータを作る。注文を作るスクリプトは要らない。

- 商品 15（公開 13、非公開 1、公開の予約 1）、カテゴリ 13、商品画像 31。在庫切れ（`TEE-001` の L、`PANTS-001-M`、`SENBEI-001`）と残りわずか（`TEE-002-M`、`JUICE-001`、`MUG-001-GY` など）がある。
- レポート用の注文 2 万件（2024-10-01〜2026-09-30、会員 300 人、出荷済みが多い）と、その支払・出荷・レポートの写し。中身は `db/seed/local/README.md`「レポート用の注文」。
- 会員 `member@example.com` の注文 4 件：受付、キャンセルの申請中、申請の却下（受付のまま）、キャンセル済み。どれも状態に合う支払（mock の与信）を持ち、受付の注文は管理画面から出荷できる（Stripe の profile では売上確定で失敗する）。承認・却下・運営者のキャンセルは、申請中の注文で管理画面か管理 API（`PUT /api/admin/orders/{orderId}/cancellation-request/approve` など）から試す。
- ID は固定（`00000000-0000-0000-0000-00000000` + 4 桁）：カテゴリ 1xxx、商品 2xxx、バリアント 3xxx、在庫 4xxx、会員 6001、注文 61xx、支払 64xx、アセット 7xxx、商品画像 8xxx。レポート用は `00000000-0000-0000-<種類 4 桁>-<番号 12 桁>`（会員 0001、注文 0010、支払 0012、出荷 0013）。
- 画面で変えたデータは、seed を流し直しても戻らない。最初の状態に戻すときは、バックエンドを止めて `scripts/dev/reset.sh` で DB を作り直す（Keycloak は `keycloak`、すべては `all`。`docs/dev/setup.md` 第 4 節）。

## E2E（`e2e/`）

`docs/testing/running.md` の「E2E」の通り。準備の script が E2E 用の DB と Keycloak（:8181）を作り直し、Playwright がバックエンド（:8090、profile `local,e2e`）を毎回起動する。管理画面（:4173）、店舗画面（:3100）は、起動していなければ起動する。ローカルのデータ（:8080 の DB、:8180 の Keycloak）は変わらない。

```bash
scripts/dev/e2e.sh                                   # ルートで。テストごとの ok / x と失敗の要点だけを出す。前提のコンテナが止まっていれば、名前を出して終了コード 2
scripts/dev/e2e.sh tests/payment.spec.ts -g AC-148   # 引数は Playwright にそのまま渡す
```

- 準備でポートの EACCES が出たら、Windows の予約範囲（WinNAT）に入っている。`/.local/agent/environment.md` の手順で外す。
