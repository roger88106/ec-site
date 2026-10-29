---
name: local-dev
description: ec-site をローカルで起動・確認する手順。DB・Keycloak（compose）の起動と接続、token の中身の確認、バックエンドの local profile での起動と待ち方、スロット（作業ツリーごとの環境）、フロント（store・admin）と E2E を要約だけで実行するスクリプト、commit の前の CI の検査、プレビューでの画面の確認方法、壊しての確認（break-check）、手元の hook の設定、シェルと Git の落とし穴。バックエンドやフロントを動かす、テストや E2E を実行する、commit する、画面を確認する、DB や token を見るときに使う。
---

# ローカルでの起動と確認

`docs/guides/`（起動とテストの手順）を、エージェントがそのまま実行できる形にしたもの。改めて調べずにこの通りに実行する。

前提：JDK 21、Node.js と pnpm、Docker が使えること。マシンに固有のこと（JDK の場所、PATH、OS の癖）は、`/.local/agent/environment.md` があればそれに従う（AGENTS.md「環境」）。

以下の 8080、9080、3000、5173 と DB `ec_site` などの固定の値は、メインの作業ツリー（スロット 0）の値。追加の作業ツリーは、「スロット」の節の表の値を使う。

## ミドルウェア（compose）

```bash
docker compose up -d          # メインの作業ツリーのルートで。PostgreSQL、Zipkin、SeaweedFS、imgproxy、Keycloak、Mailpit
docker compose stop           # 止める（データは残る）
```

- Keycloak は起動に 20 秒ほどかかる。`docker inspect -f '{{.State.Health.Status}}' ec-site-keycloak` が `healthy` になるまで待つ。
- Keycloak の起動に失敗し、ログに database `keycloak` がないと出たら、`scripts/dev/reset.sh keycloak` で再作成する（`docs/guides/setup.md` 第 4 節。database は compose の PostgreSQL を初めて作ったときだけ自動で作られる）。

- DB：`localhost:5432`、DB `ec_site`、ユーザー `ec_site`、パスワード `ec_site`。
- SQL を見るときはコンテナの psql を使う（ローカルに psql はない）。スキーマはモジュールごと（`\dn`、`\dt <スキーマ>.*` で一覧にする。注文は `ordering`）。

  ```bash
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "select order_number, status from ordering.sales_order order by ordered_at desc limit 5"
  ```

## 認証（Keycloak、Mailpit）

realm の設定は `docker/keycloak/realms/` の JSON。アカウントと、JSON を変えたときの再インポートは、`docs/guides/setup.md` 第 3 節（アカウント）と `docs/guides/setup.md` 第 4 節（再インポート）。

```bash
scripts/dev/keycloak.sh token ec-admin admin@example.com admin-pass-1   # access token の payload を出す（権限・audience の確認）
scripts/dev/keycloak.sh token ec-store member@example.com member-pass-1
scripts/dev/keycloak.sh access-token ec-store member@example.com member-pass-1  # token そのもの（curl で API を呼ぶとき）
scripts/dev/keycloak.sh admin GET /admin/realms/ec-store                # 管理 API（realm の設定を読む）
curl -s http://localhost:8025/api/v1/messages | head -c 500              # Mailpit に届いたメールの一覧
```

- `token`・`access-token` は、web の client のパスワードによる取得（Direct Access Grant）を一時的に有効にし、終わったら無効に戻す。途中で停止したら、`admin` で `directAccessGrantsEnabled` が `false` か確認する。
- 管理 API で設定を変えても、realm の JSON は変わらない。残す設定は JSON を修正し、再インポートして確認する。

## バックエンド（`backend/ecsite/`）

起動はバックグラウンドで行い、出力先をファイルにする（ログが多いため）。

```bash
cd backend/ecsite && ./gradlew bootRun --args='--spring.profiles.active=local' > <作業用のディレクトリ>/bootrun.log 2>&1
```

起動を待つのは sleep ではなく curl の再試行で行う（初回のコンパイル込みで 1〜2 分）。

```bash
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:9080/actuator/health
```

- 死活監視は運用ポート（9080）で公開する。業務ポート（8080）では公開しない。
- 200 が返ったら起動済み。すでに起動しているかは同じ curl を `--retry` なしで確認する。バックグラウンドの起動のコマンドが、ツールの時間の上限で「停止」と通知されても、プロセスは動き続けていることがある。起動したかはこの curl で判断する。
- 起動に失敗したら `bootrun.log` を `grep -n "ERROR\|Caused by" ` で見る。全体を読まない。
- local profile では、起動時にマイグレーションと seed（`db/seed/base`、`db/seed/local`）が適用される。使い分けは `db/seed/local/README.md`。
- 停止するときは、ポート 8080 で待ち受けているプロセスを停止する。Windows では PowerShell の `Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess`（Git Bash には `pkill` がない）。画面の開発サーバーも、ポート（3000、5173）で同じく停止する。
- `bootRun` のバックグラウンドのタスクをツールで停止しても、java のプロセスが残って 8080 で待ち受け続ける。必ず上の `Stop-Process` で停止し、8080 が空いたことを確認する。

テスト（`backend/ecsite/` で。Docker が必要）：

```bash
./gradlew check -q --console=plain 2>&1 | tail -40
```

- `check` は単体・統合テストと OpenAPI の検証を含む（AGENTS.md の完了の基準）。一部だけなら `test`（単体）、`integrationTest`（統合）、`--tests '<クラス名>'`。
- `build.gradle.kts` を変えたら、テストの前に `./gradlew help -q` でビルドの設定を評価する。書き誤りが、テストや E2E の途中まで検出されないため。
- 長い検査は、最初からバックグラウンドで実行する（Bash の上限 10 分を超えて途中でバックグラウンドに移ると、結果の読み方が変わるため）。目安：`./gradlew check` 約 10 分、`check-frontend.sh` 約 1.5 分、`e2e.sh` 約 2 分、push の後の CI 約 8 分（`gh run watch --exit-status`）。`./gradlew check`、`check-frontend.sh`、`check-all.sh`、`e2e.sh` は続けて実行し、同時に実行しない（CPU が足りずに、時間の上限のあるテストと管理画面のテストがタイムアウトする）。
- バックエンドと画面は、スロットごとに同時に起動してよい（「スロット」の節）。E2E はロックで 1 つずつ実行される。バックエンドの全テスト、フロントの検査の script、E2E は、同時に実行すると、タイムアウトを設定したテストが失敗しうるので、他のセッションが実行しているときは待つ。
- 失敗の詳細は `build/test-results/**/TEST-*.xml` を、失敗したクラスだけ grep する。

## フロント（`frontend/store`、`frontend/admin`）

lint・型チェック・テスト・ビルドは、手順ごとに 1 行の要約だけを出力する script で実行する。失敗した手順だけ、出力の末尾とログのパスが表示される。

```bash
scripts/dev/check-frontend.sh                       # 依存の再インストール、ライセンスの検査、knip の実行、組み込みの名前の一覧の確認の後に、共有パッケージで lint typecheck test、両方のアプリで lint typecheck test build、E2E のコードで lint typecheck（CI と同じ）
scripts/dev/check-frontend.sh admin test            # 管理画面のテストだけ
scripts/dev/check-frontend.sh store lint typecheck  # 対象と手順を選ぶ
```

- 末尾の行数は `TAIL_LINES`（既定 30）、ログの置き場所は `LOG_DIR`（既定は一時ディレクトリ）。
- 整形と自動で修正できる lint は、各アプリで `pnpm lint:fix`。1 つのファイルだけなら、そのアプリのディレクトリで `npx biome check --write <ファイル>`（ルートから実行すると、設定が入れ子だというエラーになる）。
- 1 つのテストのファイルを書いている間は、アプリのディレクトリで `npx tsc --noEmit` を先に実行してから `npx vitest run <ファイル>` を実行する。Vitest は型を確認しないため、テストデータの形の誤りが、テストの失敗として間接的に表れる。出力は `| grep -E "^\s+×|Tests|AssertionError" | head` で要約だけを読む（失敗した toContain は HTML の全体を出力する）。
- 型（`src/api/generated/`）は `typecheck`・`build`・`dev` の前に OpenAPI から生成される。
- 依存を追加した・上げたときは、ルートで `pnpm install --frozen-lockfile` とライセンスの検査を再実行する（`docs/rules/frontend-coding.md` 第 9.1 節）。手元は依存が入った後なので、テストが通っても CI のインストールで失敗することがある。

開発サーバーは Bash ではなく、プレビューのツール（`preview_start`）で `.claude/launch.json` の名前を指定して起動する。プレビューのツールがないとき（コマンドラインの実行など）は、各アプリのディレクトリで `pnpm dev` をバックグラウンドで実行し、URL に curl して 200 を確認する。

| 名前 | URL | 前提 |
|------|-----|------|
| `admin` | http://localhost:5173 | バックエンド（`/api` を :8080 に転送） |
| `store` | http://localhost:3000 | バックエンド（API はサーバーから呼ぶ） |

- 店舗画面の一覧は API の結果を最大 60 秒キャッシュする。管理画面での変更がすぐ表示されないのは正常。

### プレビューで画面を確認する

エージェントが実装の途中で表示を確認するときの、ツールの使い方。利用者が画面を確認する手順は、skill `new-screen` の「画面を追加し終えたら」にある。

- IdP のログインの画面は、描画の前だとキーの入力やクリックが失敗する。`find` で入力欄を探し、`form_input` で local の seed のアカウント（`docs/guides/setup.md`）を入れ、`javascript_tool` で `document.querySelector('form').submit()` を呼ぶ。
- 表示の中身は `get_page_text` で読む。スクリーンショットは描画を待ってタイムアウトしやすいため、見た目を見るときだけ使う。
- 開く画面の ID は DB から取得する（例：注文と支払は `ordering.sales_order` と `payment.payment` を結ぶ）。

## スロット（作業ツリーごとの環境）

追加の作業ツリー（`git worktree add` で作ったもの）は、スロット 1〜3 を 1 つ割り当てて、バックエンド・画面・DB・画像のバケットを他の作業ツリーと分ける。メインの作業ツリーはいつもスロット 0（上の固定の値）。見分け方は、`node scripts/dev/slot.mjs show` を実行する。

- 共有の基盤（compose）はメインの作業ツリーで起動する。追加の作業ツリーで `docker compose up` を実行しない。基盤が動いていないと slot.mjs は終了コード 2 で終わる。
- slot.mjs は作業ツリーのどこから実行してもよい。
- 新しい作業ツリーは、始めにルートで `pnpm install --frozen-lockfile` を実行する（`run store`・`run admin` は `node_modules` を直接起動する）。
- スロットで使えないもの：Stripe の通知の転送（`stripe-cli`）と機能紹介のスクリーンショット（`pnpm capture`）はスロット 0 だけで対応する。Mailpit と Zipkin は全スロットで共有する。

```bash
node scripts/dev/slot.mjs assign   # 空いている最小のスロットを割り当て、DB と 2 つのバケットを作る（割り当て済みなら番号を示すだけ）
node scripts/dev/slot.mjs show     # 自分のスロットのポート・URL・名前と、全ての作業ツリーのスロットの一覧
node scripts/dev/slot.mjs env      # スロットの環境変数を export の形で出す（Gradle を直接実行するときなど）
node scripts/dev/slot.mjs reset    # 割り当てを変えずに DB とバケットを再作成する（バックエンドを止めてから）
node scripts/dev/slot.mjs release  # ポートで待ち受けるプロセスを停止し、DB とバケットを削除して、割り当てを解除する
```

- 作業ツリーを削除する前に `release` する。`release` せずに削除しても、残った DB とバケットは次の `assign` が再作成するので、手動で削除しない。
- 自分が動いている作業ツリーは、メインの作業ツリーから `git worktree remove` で削除する。使うツールがセッションのアーカイブで作業ツリーを片付けるときは、それに任せる。
- `node_modules` のある作業ツリーを削除すると、Windows ではパスが長すぎて失敗する。`git config core.longpaths true` を設定してから `git worktree remove <パス>` で削除する。失敗が続くときは、PowerShell で `\\?\` を付けたパスを指定して削除する。作業ツリーのセッションが動いていると、ロックで削除できないので、先にセッションをアーカイブする。
- `env` の出力は、`bootRun` などを手動で実行するときだけ使い、`(eval "$(node scripts/dev/slot.mjs env)"; ./gradlew bootRun ...)` のように subshell の中で使う。export したままの shell で `./gradlew check` を実行すると、スロットの値が統合テストの設定を上書きして失敗しうる。
- メインの作業ツリーで `scripts/dev/reset.sh all` を実行すると、全スロットの DB とバケットも削除される。各作業ツリーで `node scripts/dev/slot.mjs reset` を実行して再作成する。
- マージの直後（共有の Keycloak に realm を再度取り込む前）は、スロットの画面のログインがリダイレクト先のエラー（Invalid redirect uri）で失敗する。メインの作業ツリーで `scripts/dev/reset.sh keycloak` を実行する。
- 割り当ては `<作業ツリー>/.local/slot` に番号だけで書く。メインの作業ツリーでは assign・release・reset は何もしない。

| スロット | バックエンド | 運用ポート | 店舗画面 | 管理画面 | DB |
|---|---|---|---|---|---|
| 0（メイン） | 8080 | 9080 | 3000 | 5173 | `ec_site` |
| 1 | 18080 | 19080 | 13000 | 15173 | `ec_site_1` |
| 2 | 28080 | 29080 | 23000 | 25173 | `ec_site_2` |
| 3 | 38080 | 39080 | 33000 | 35173 | `ec_site_3` |

バックエンドは、`run backend` でスロットの環境変数を付けて起動する（出力先をファイルにして、バックグラウンドで実行する）。

```bash
node scripts/dev/slot.mjs run backend > <作業用のディレクトリ>/bootrun.log 2>&1
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:19080/actuator/health   # スロット 1 の運用ポート
```

- 待つ先は自分のスロットの運用ポート。`localhost:9080` はメインのバックエンドなので、待ってはいけない。
- 店舗画面と管理画面のプレビューは、`.claude/launch.json` の `store-<n>`・`admin-<n>`（`store-1`、`admin-2` など。`<n>` は自分のスロット）を `preview_start` で指定する。`admin`・`store` はスロット 0。プレビューのツールがないときは、`node scripts/dev/slot.mjs run store <n>`・`run admin <n>` をバックグラウンドで実行する。番号が自分のスロットと違うと起動しない。
- `node scripts/dev/slot.mjs run` は、Ctrl+C など停止のシグナルの 1 回目で子プロセスも含めて停止する。`run backend` は、バックエンドか運用のポートが使われていれば、プロセスの ID と停止のコマンドを表示して終了コード 1 で終了するので、表示されたコマンドで停止してから再実行する。
- スロットを解除するなら、`node scripts/dev/slot.mjs release` が 4 つのポートのプロセスを停止してから解除する。node を強制終了して残ったバックエンドも停止する。停止できないときは、データを削除せず、停止のコマンドを表示して終了する。
- 割り当てとデータを残したまま停止するときは、自分のスロットのポートで待ち受けているプロセスだけを停止する。Windows の PowerShell では `Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -State Listen).OwningProcess`（スロット 1 のバックエンド）。他のスロットのポートのプロセスを停止しない。
- DB を見るときは、`docker exec ec-site-postgres psql -U ec_site -d ec_site_<n> -c "..."`（`<n>` は自分のスロット）。
- データの再作成は、作業ツリーでは `scripts/dev/reset.sh`（`db`）だけが使え、自分のスロットの DB とバケットだけを再作成する（`slot.mjs reset` と同じ）。`keycloak` と `all` は共有のコンテナを再作成するので、メインの作業ツリーで実行する。
- E2E は、どの作業ツリーからでも実行できる。ロックで 1 つずつ実行され、他が実行中なら所有者を示して終了コード 1 で終わるので、待ってから再実行する。PID が再利用されて終了し続けるときは、示されたロックのファイルを削除してよい。E2E は常にスロット 0 の固定の環境で実行され、E2E のエントリポイント（`e2e/scripts/run.ts`）がスロットの環境変数を削除する。

## CI の検査の script

`./gradlew check` と `check-frontend.sh` に入っていない CI の検査。commit の前にルートで実行する（hook を置いていれば commit のときに実行される）。各検査の中身、失敗したときの修正方法、除外の書き方は [CI の検査](../../../docs/guides/ci.md)。

```bash
scripts/ci/check-all.sh
```

手元では、テスト名に出ない AC は警告だけで通る。spec の最後（push の前）は、CI と同じに確認する。

```bash
CI=true scripts/ci/check-all.sh
```

## 決済（mock と Stripe）

- profile `local` は mock（`application-local.yml` の mock フラグ。物流も mock）。注文すると `http://localhost:8080/mock-payment/{ID}` の支払画面に移る（「支払う」「カードを拒否させる」「やめる」）。
- Stripe の sandbox は `docs/guides/setup.md`「Stripe で動かす」の通り。鍵は `/.local/stripe.env`（なければ利用者に頼む。鍵を会話に貼らせない）。

  ```bash
  docker compose --profile stripe up -d stripe-cli                   # ルートで。通知を :8080 に転送する
  ./gradlew bootRun --args='--spring.profiles.active=local,stripe'     # backend/ecsite/ で
  docker logs ec-site-stripe-cli 2>&1 | grep -- '-->' | tail           # 転送した通知（ログ全体は署名の鍵を含むので出さない）
  ```

- 期限切れを早く確認するときは、`--args` に `--ecsite.payment.deadline=PT3M --ecsite.payment.expiry-interval=PT20S` を追加する。
- 支払は `payment.payment`（`status`、`page_id`、`authorization_ref`）、注文は `ordering.sales_order`。
- Stripe の Checkout の画面は描画が遅く、入力が遅れて二重に入ることがある。入力の後に拡大して確認してから支払う。

## 機能紹介のページのスクリーンショット

`docs/guides/setup.md`「機能紹介のページのスクリーンショット」の通り。DB を再作成してから、`e2e/` で `pnpm capture`。

## ローカルの画面のデータ

local profile の seed（`db/seed/local/`）が、画面を確認するためのデータを作る。注文を作るスクリプトは必要ない。

- 商品 15（公開 13、非公開 1、公開の予約 1）、カテゴリ 13、商品画像 31。在庫切れ（`TEE-001` の L、`PANTS-001-M`、`SENBEI-001`）と残りわずか（`TEE-002-M`、`JUICE-001`、`MUG-001-GY` など）がある。
- レポート用の注文 2 万件（2024-10-01〜2026-09-30、会員 300 人、出荷済みが多い）と、その支払・出荷・レポートの写し。中身は `db/seed/local/README.md`「レポート用の注文」。
- 会員 `member@example.com` の注文 4 件：受付、キャンセルの申請中、申請の却下（受付のまま）、キャンセル済み。どれも状態に合う支払（mock の与信）を持ち、受付の注文は管理画面から出荷できる（Stripe の profile では売上確定で失敗する）。承認・却下・運営者のキャンセルは、申請中の注文で管理画面か管理 API（`PUT /api/admin/orders/{orderId}/cancellation-request/approve` など）から試す。
- ID は固定（`00000000-0000-0000-0000-00000000` + 4 桁）：カテゴリ 1xxx、商品 2xxx、バリアント 3xxx、在庫 4xxx、会員 6001、注文 61xx、支払 64xx、アセット 7xxx、商品画像 8xxx。レポート用は `00000000-0000-0000-<種類 4 桁>-<番号 12 桁>`（会員 0001、注文 0010、支払 0012、出荷 0013）。
- 画面で変えたデータは、seed を再適用しても戻らない。最初の状態に戻すときは、バックエンドを停止して `scripts/dev/reset.sh` で DB を再作成する（Keycloak は `keycloak`、全ては `all`。`docs/guides/setup.md` 第 4 節）。

## E2E（`e2e/`）

`docs/guides/running-tests.md` の「E2E」の通り。準備の script が E2E 用の DB と Keycloak（:8181）を再作成し、Playwright がバックエンド（:8090、profile `local,e2e`）を毎回起動する。管理画面（:4173）、店舗画面（:3100）も毎回起動する（起動済みのサーバーは使い回さず、ポートが使われていれば停止する）。ローカルのデータ（:8080 の DB、:8180 の Keycloak）は変わらない。

```bash
scripts/dev/e2e.sh                                   # ルートで。テストごとの ok / x と失敗の要点だけを出す。前提のコンテナが止まっていれば、名前を出して終了コード 2
scripts/dev/e2e.sh tests/payment.spec.ts -g AC-148   # 引数は Playwright にそのまま渡す
```

- 準備でポートの EACCES が出たら、Windows の予約範囲（WinNAT）に入っている。`docs/guides/setup.md` 第 7 節の手順で外す。

## 壊しての確認（break-check）

コードをわざと壊してテストが失敗することを確認する（DN の確認など）ときに使う。sed は、当たらない式でも黙って通り、壊したつもりで「通った」と読み違えるため、使わない。

```bash
node scripts/dev/break-check.mjs <ファイル> <置き換える文字列> <置き換え後> <ディレクトリ> <テストのコマンド>
node scripts/dev/break-check.mjs frontend/store/src/x.tsx "{!ok && <p" "{false && <p" frontend/store "npx vitest run src/x.test.tsx"
```

- 置き換えは文字列で行い、1 か所に当たらなければ変えずに終了コード 2 で止まる。テストのコマンドは Git Bash で実行するので、`./gradlew` も起動できる。テストの後は、成否によらず、Ctrl+C などの停止のシグナルを受けたときも、元に戻す。
- 出力は、失敗したテストの行（`×`、`FAILED`、`BUILD`）と件数、最後の `→ 失敗した（壊すと検出できる）` か `→ 通った（壊しても検出できない）`。終了コードは、失敗すれば 0（壊すと検出できた）、通れば 1、置き換えが当たらない・ファイルが読めない・bash を起動できない・テストのコマンドが見つからない（bash の終了コード 127）か実行できない（126）ときは 2（理由は標準エラー出力。コマンドの誤りをテストの失敗と読み違えないため、127 と 126 は 0 にしない）。
- Gradle のテストでは、出力に `FAILED` か `BUILD` の行があることを確認する。ない失敗は、テスト以外の原因（起動の失敗など）を疑う。

## シェルと Git の落とし穴

- `\` や正規表現を含む書き換えは、Edit で行う（新しいファイルは Write）。heredoc（`<<'EOF'` も）と `node -e` の文字列に入れると、Windows の Git Bash では `\` が落ちて、正規表現が黙って壊れる。Markdown の表のセルの `\|` も同じで、`\|` を含む表の追記を heredoc で書くと止まる。置き換えの script が必要なときは、Write でスクラッチパッドに `.cjs` を書いて node で実行する。
- 次の書き方は、拒否されるか、意図しない結果になる。表の「避け方」に替える。

| 書き方 | 拒否するもの | 避け方 |
|--------|------------|--------|
| heredoc・`node -e` の中の `\`（Markdown の表の `\|` を含む） | hook `backslash-guard`（次の節） | 上の箇条のとおり、`\|` を含む表の追記も Edit で書く |
| `gh pr edit` と `gh pr ready` など、GitHub の状態を変える `gh` の操作を `&&` や `;` で 1 つにつないだコマンド | auto mode の判定 | pull request の編集・ready・マージ、issue の編集は、1 つのコマンドに操作を 1 つだけ書き、別の呼び出しで実行する |
| `claude -p` の `--permission-mode bypassPermissions` | auto mode の判定 | `--permission-mode default` と `--allowedTools` を使う（skill `sessions` のツールの注） |
| コミットメッセージの、層 1 の使わない語（`scripts/ci/ja-text.json` の error の語） | git の hook `commit-msg`（日本語の検査） | 書く前に語の一覧を見る（skill `zh-mixing` の「書くときの注意」） |
| spec やコミットメッセージに写した、利用者の中国語の言葉 | git の hook `commit-msg`・`pre-commit`（中国語の混入の検査） | 日本語に訳して書く（skill `zh-mixing` の「書くときの注意」） |
| `git -C` と `cd` のパスに変数やコマンドの置き換え（`git -C "$D" commit`、`cd "$(...)"` など） | hook `agent-guard`（`git commit` の対象のリポジトリを決められないとき） | 変数を使わず、絶対パスを直接書く |
| `cd` で `/.local/` などに移る | 拒否されない。以後のコマンドの作業ディレクトリが意図しない場所になる | `cd` で作業ディレクトリを移さず、`git -C <絶対パス>` と絶対パスを使う |

- 検査を `| tail` に通すと、終了コードが `tail` のものになり、`&&` で続く commit が検査の失敗でも実行される。検査と commit は別の呼び出しにするか、`set -o pipefail` を付ける。
- `git add -A && git commit` が hook で止まると、ステージしたものが残る。続けて一部のファイルだけを `git add` して commit すると、残っていたものも入る。止まったら `git reset -q` でステージを外してから、入れるものだけを `git add` する。

## 手元の hook の設定

利用者の環境（Git に入らない `.claude/settings.local.json`）に置く 2 つの hook。script は `scripts/dev/hooks/` にあり、チームの規則の hook（`settings.json`）とは別である（分け方は AGENTS.md の「環境」）。読む場所と効く時点は、skill `sessions` のツールの注の「作業ツリーのセッションが読むもの」の表。デスクトップアプリの作業ツリーのセッションはメインの作業ツリーの `settings.local.json` を読むので、メインの作業ツリーに置く。作業ツリーで起動した `claude -p` は、メインの作業ツリーの `settings.local.json` を読まないので、手元の hook は動かない（表）。

| hook | イベントと matcher | 働き |
|------|--------------------|------|
| `session-start.mjs` | SessionStart、`startup\|resume\|compact` | メインの作業ツリーの `/.local/agent/environment.md` を文脈に入れる。hook が動くセッションでは、どの作業ツリーでも同じファイルを読む。ファイルがなければ何も出さない |
| `backslash-guard.mjs` | PreToolUse、`Bash` | heredoc と `node -e` の中の `\` を拒否する。コマンドの他の箇所の `\`（grep の `\|` など）は通す |

command は、script のパスを、まず `git rev-parse --show-toplevel` の作業ツリーから探し、なければ `$CLAUDE_PROJECT_DIR`（メインの作業ツリー）の下を使う形にする。探し方は `.claude/settings.json` と同じだが、どちらにも見つからないときは、`settings.json` はコマンドを止め、この例は何もせず通す。理由：script を含まないブランチの作業ツリーでも、メインの作業ツリーの script で動かすため。

```json
{
  "hooks": {
    "SessionStart": [
      {
        "matcher": "startup|resume|compact",
        "hooks": [
          { "type": "command", "command": "f=; for d in \"$(git rev-parse --show-toplevel 2>/dev/null)\" \"$CLAUDE_PROJECT_DIR\"; do if [ -n \"$d\" ] && [ -f \"$d/scripts/dev/hooks/session-start.mjs\" ]; then f=\"$d/scripts/dev/hooks/session-start.mjs\"; break; fi; done; [ -z \"$f\" ] || node \"$f\"" }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          { "type": "command", "command": "f=; for d in \"$(git rev-parse --show-toplevel 2>/dev/null)\" \"$CLAUDE_PROJECT_DIR\"; do if [ -n \"$d\" ] && [ -f \"$d/scripts/dev/hooks/backslash-guard.mjs\" ]; then f=\"$d/scripts/dev/hooks/backslash-guard.mjs\"; break; fi; done; [ -z \"$f\" ] || node \"$f\"" }
        ]
      }
    ]
  }
}
```

- 既に `hooks` があれば、イベントごとの配列に項目を追加する。設定を変えたら、新しいセッションを起動する。
- 中国語の混入の PostToolUse の hook は skill `zh-mixing`。
