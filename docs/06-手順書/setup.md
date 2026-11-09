# 開発環境の起動

ローカルで compose・バックエンド・管理画面・店舗画面を起動する手順と、使うアカウント。テストは [テストの実行方法](running-tests.md)、環境変数は [環境変数](environment.md)で、エージェントは skill `local-dev` を使う。

## 前提

JDK 21、Docker（Compose 2.20 以上。ルートの `compose.yaml` が `include` を使うため）、Node.js 24 以上、pnpm（バージョンは `package.json` の `packageManager`）。

- `./gradlew` は、環境変数 `JAVA_HOME`（なければ PATH の `java`）の JDK で Gradle を起動する。Gradle の起動には JDK 17 以上が必要なので、PATH の `java` が古いマシンでは、`JAVA_HOME` に JDK 21 のパスを設定する。コンパイルとテストの JDK 21 は、Gradle の toolchain が探す。
- clone したら、リポジトリのルートで `git config core.quotepath false` を実行する。日本語を含むパスを、`git status` などで読める形で表示するため。

IntelliJ IDEA でルートを開いたら、`backend/ecsite/build.gradle.kts` を Gradle プロジェクトとしてリンクし、Gradle JVM を 21 にする。

## 起動

```bash
# ルートで：PostgreSQL、Zipkin、SeaweedFS、imgproxy、Keycloak、Mailpit を起動する
docker compose up -d

# ルートで：フロントの依存をインストールする（管理画面・店舗画面・E2E をまとめて）
pnpm install

# backend/ecsite/ で：local profile で起動する（マイグレーションとローカル用の seed も適用される）
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
| http://localhost:8080/mock-carrier | mock の物流サービス（出荷の配送状態を、配送中・配達完了・返送に進める） |
| http://localhost:9080/actuator/health | ヘルスチェック（`/liveness`、`/readiness`）。運用ポート（9080）で出し、業務ポート（8080）では出さない |
| http://localhost:9411 | Zipkin（トレース。local では全リクエストを記録） |
| http://localhost:8333 | SeaweedFS（S3 互換。バケットは `seaweedfs-init` が作る） |
| http://localhost:8081 | imgproxy（画像の WebP への変換。署名のない URL は拒否） |
| http://localhost:8180 | Keycloak（管理画面は `/admin`、`admin` / `admin`） |
| http://localhost:8025 | Mailpit（Keycloak とバックエンドが送ったメール。外部には送信しない） |

- `docker compose` はメインの作業ツリーのルートで実行する（作業ツリーでは実行しない。「スロット」。ルートの `compose.yaml` が [docker/compose.yaml](../../docker/compose.yaml) を読む）。DB は `localhost:5432/ec_site`。
- Mailpit の SMTP はホストの 2525 番ポートで待ち受ける。他のプログラムが 2525 を使っているときは、環境変数 `MAILPIT_SMTP_PORT` に空いているポートを設定して、compose とバックエンドを起動する（両方が同じ値を読む）。
- compose のポートは、このマシン（127.0.0.1）でだけ待ち受ける（ローカル用のパスワードは公開されているため）。他のマシン（スマートフォンなど）から接続するときは、`docker/.env`（Git に入らない）に `LOCAL_BIND_ADDRESS=0.0.0.0` と書くか、同じ名前の環境変数を設定して、`docker compose up -d` で再作成する。終わったら設定を削除して再作成する。バックエンドと画面の開発サーバーは対象外である。
- 停止するときは `docker compose stop`。E2E を実行した後は、E2E 用の Keycloak も停止するため `docker compose --profile e2e stop` にする。
- 店舗画面の開発サーバーのログに、Edge Runtime で使えない API（`instrumentation.ts` の `process.exit`）の警告が出る。Node.js の実行環境でだけ呼ぶので、影響しない。
- compose のプロジェクトの名前（`ec-site`）とコンテナの名前は固定なので、2 つ目の clone は volume（DB と画像のデータ）を共有する。別のデータで動かすときは、1 つ目の compose を `docker compose down` で停止し、環境変数 `COMPOSE_PROJECT_NAME` に別の名前を設定してから起動する。
- E2E 用の Keycloak（:8181、profile `e2e`）はこの一覧に含まれない。E2E の準備の script が起動する（[テストの実行方法](running-tests.md)）。
- 店舗画面は API の結果を最大 60 秒キャッシュするので、管理画面での変更は 1 分以内に反映される。
- フロントの API の型は、`dev`・`typecheck`・`build` の前に OpenAPI から生成される（コミットしない）。整形と自動で修正できる lint は `pnpm lint:fix`。
- commit のときに CI の検査の script（[CI の検査の script](ci-checks.md)）を実行するには、次の 2 つの hook を置く（ファイルがなければ、1 行目に `#!/bin/sh` を書いて作る）。`core.hooksPath` は変えない。

  | hook | 書く 1 行 | 実行するもの |
  |------|-----------|--------------|
  | `.git/hooks/pre-commit` | `"$(git rev-parse --show-toplevel)/scripts/dev/hooks/pre-commit" \|\| exit 1` | `check-all.sh`。ステージした内容（次の commit）だけを、一時ディレクトリに取り出して検査する。ステージしていない変更と未追跡のファイルは検査しない。日本語の文の検査（`ja-text`）の error（使わない語）があれば commit は失敗する。検査が通ったら、ステージした追加行の warning（文体の型など）だけを表示する。warning は commit を止めないので、読んで修正するかを決める。今のブランチが `develop` か `main` なら、検査の前に commit を失敗にし、作業ブランチの作り方を示す（detached HEAD は通す）※1。pre-commit hook を置いていないときは ※2 |
  | `.git/hooks/commit-msg` | `"$(git rev-parse --show-toplevel)/scripts/dev/hooks/commit-msg" "$1" \|\| exit 1` | `ja-text --message` で、メッセージの error（使わない語）を調べ、あれば commit は失敗する |

  ※1：git が pre-commit hook を実行しない `git merge`・`git cherry-pick`・`git revert`・`git am` は、`develop`・`main` の上でも失敗にならない。

  ※2：pre-commit hook を置いていないと、Claude Code の hook（`scripts/dev/hooks/agent-guard.mjs`）が `git commit` を拒否する。

## アカウント

realm の設定は [docker/keycloak/realms/](../../docker/keycloak/realms) の JSON が正本で、Keycloak の起動時に取り込まれる。ローカルと CI で使うアカウント（パスワードはローカル専用）：

| realm | 画面 | アカウント | パスワード | 用途 |
|-------|------|------------|------------|------|
| `ec-store`（消費者） | 店舗 | `member@example.com` | `member-pass-1` | seed の注文を持つ会員。生年月日と性別を設定してある |
| `ec-store`（消費者） | 店舗 | `member2@example.com` | `member2-pass-1` | 他人のデータが見えないことの確認。生年月日と性別を設定していない（レポートの「不明」の確認） |
| `ec-admin`（運営者） | 管理画面 | `admin@example.com` | `admin-pass-1` | 管理者 |
| `ec-admin`（運営者） | 管理画面 | `viewer@example.com` | `viewer-pass-1` | 閲覧者 |
| `ec-admin`（運営者） | 管理画面 | `manager@example.com` | `manager-pass-1` | 管理職（全てのレポート） |
| `ec-admin`（運営者） | 管理画面 | `sales@example.com` | `sales-pass-1` | 営業（売上の推移を除くレポート） |

- 手動確認はこの表のアカウントを使う（誰の環境にもある）。足りなければ realm の JSON に追加し、表にも書く。画面から登録した会員は、その人の環境にしかない。
- 会員のアカウント ID は realm の JSON で固定している（`member@example.com` は `00000000-0000-0000-0000-000000009001`、`member2@example.com` は `…9002`）。ローカル用の seed の注文が、この会員のものになるため。
- 会員の生年月日と性別（任意の属性）は、会員登録とアカウントの画面（`http://localhost:8180/realms/ec-store/account`）で入力・修正する。表示の文言は realm の JSON の `localizationTexts` に置く。
- 会員登録の確認とパスワードの再設定のメール（Keycloak）、出荷通知メール（バックエンド）は Mailpit で受信される。宛先のアドレスは実在しなくてよい。
- token の内容は `scripts/dev/keycloak.sh token <realm> <アカウント> <パスワード>` で確認する。

## データの再作成

画面で変えたデータは、seed を再適用しても戻らない。`scripts/dev/reset.sh` で、Git の正本から再作成する。

| 戻したいもの | コマンド（ルートで） | 触れないもの |
|--------------|----------------------|--------------|
| DB だけ | `scripts/dev/reset.sh`（`db` と同じ）。バックエンドを停止してから実行する。次の起動で seed が適用される（レポート用の注文 2 万件を含め、15 秒ほど） | E2E の DB、Keycloak、画像 |
| Keycloak（realm の JSON を変えたとき） | `scripts/dev/reset.sh keycloak`（ローカルで登録した会員も削除される） | DB、E2E の Keycloak |
| 全て | `scripts/dev/reset.sh all`（`docker compose down -v` と `up -d`）。全スロットのデータベースとバケットも削除される | なし |

作業ツリー（「スロット」）で実行すると、`db`（既定）はそのスロットのデータベースと 2 つのバケットだけを再作成する（`node scripts/dev/slot.mjs reset` と同じ）。`keycloak` と `all` は共有のコンテナを再作成するので、メインの作業ツリーで実行するよう示して終了する。メインの作業ツリーで `all` を実行した後は、各作業ツリーで `node scripts/dev/slot.mjs reset` を実行する。

正本は、DB の seed が `backend/ecsite/src/main/resources/db/seed/`、画像が `docker/seaweedfs/seed/`、Keycloak が `docker/keycloak/realms/`。seed の商品画像の ID と注文の会員 ID は、画像と realm の正本に合わせてある。

## Stripe で動かす

既定の起動（profile `local`）は、決済サービスに mock を使い、外部に接続しない。Stripe の sandbox で確認するときだけ、次の手順で切り替える。決済サービスの接続の設計は [決済の設計](../02-設計/payment.md)の「外部のサービス」。

1. Stripe のアカウントと sandbox を作り、ダッシュボードの「開発者 → API キー」のシークレットキー（`sk_test_` で始まる）を、`/.local/stripe.env` に書く。このファイルは Git に入らない。鍵を会話やコミットに含めない。

   ```bash
   STRIPE_SECRET_KEY=sk_test_...
   STRIPE_WEBHOOK_SECRET=
   ```

2. 通知の署名鍵（`whsec_` で始まる）を取得し、同じファイルの `STRIPE_WEBHOOK_SECRET` に書く。API キーが変わらなければ、鍵も変わらないため 1 回だけでよい。

   ```bash
   docker compose --profile stripe run --rm stripe-cli 'stripe listen --api-key "$STRIPE_SECRET_KEY" --print-secret'
   ```

3. 通知の転送を始め、バックエンドを起動する。

   ```bash
   # ルートで：Stripe の通知を http://localhost:8080/api/webhooks/stripe に転送する
   docker compose --profile stripe up -d stripe-cli

   # backend/ecsite/ で：profile stripe を追加で有効にする（/.local/stripe.env を読み、決済サービスを Stripe にする）
   ./gradlew bootRun --args='--spring.profiles.active=local,stripe'
   ```

- 転送した通知と応答は `docker logs -f ec-site-stripe-cli` で確認する。起動時のログに署名鍵（`whsec_`）が出るので、エージェントは `docker logs ec-site-stripe-cli 2>&1 | grep -v whsec_` で鍵の行を除き、鍵を会話に出さない。停止は `docker compose --profile stripe stop stripe-cli`。
- テストのカード番号は Stripe の文書のもの（成功は `4242 4242 4242 4242`、拒否は `4000 0000 0000 0002`。有効期限は未来の日付、セキュリティコードは任意の 3 桁）。
- 支払方法は、ダッシュボードの「設定 → 決済手段」で決まる。カードだけを有効にする（カード以外は与信だけを行えない）。ウォレット（Apple Pay、Google Pay、Link）は MVP の後に再検討する（issue [#44](https://github.com/roger88106/ec-site/issues/44)）。
- 期限切れを早く確認するときは、`--ecsite.payment.deadline=PT3M --ecsite.payment.expiry-interval=PT20S` を追加する（Stripe の支払画面の期限は長い。[決済の設計](../02-設計/payment.md)の「Stripe」）。
- seed の注文（`db/seed/local/`）の与信は mock のもので、Stripe には存在しない。Stripe で動かすときは、seed の注文を出荷すると売上確定で失敗するので、出荷は Stripe で支払った注文で確認する。

## 機能紹介のページのスクリーンショット

機能紹介のページ（`showcase/`）の画像を、ローカルの環境の画面で再撮影する。機能を追加して画面が変わったときに使う。

1. バックエンドを停止し、DB を再作成する（「データの再作成」。画面で変えたデータと、前に撮ったときの注文を削除するため）。
2. compose、バックエンド（profile `local`。Stripe の profile は付けない）、店舗画面（:3000）、管理画面（:5173）を起動する（「起動」）。
3. `e2e/` で `pnpm capture`。`showcase/img/` の 14 枚を上書きする。

- 撮影では、会員として注文して支払い、もう 1 つ注文して支払わずに戻る（支払待ちの画面を撮るため）。白のマグカップの在庫を 2 つ使うため、続けて撮るときも DB を再作成する。
- 設計書の画像（`docs/01-概要/img/`）は `pnpm capture:docs` で撮る。前提は上の手順 1、2 と同じ。続けて撮るときは DB を再作成する（品番が重複するため）。
- 撮影は E2E とは別で、CI では実行しない。撮った画像は目視で確認してから commit する。
- ページの数字（条件・テスト・仕様書の数）は、`./gradlew check` の後にルートで `node scripts/dev/showcase-counts.mjs` を実行して照合し、変わっていれば修正する。

## ポートが使えないとき（Windows）

Windows は、WinNAT（Docker や WSL が使う）などのために、ポートの範囲を動的に予約する。ローカル環境のポートが予約範囲に含まれると使えなくなる。

- 症状：待ち受けているプロセスがないのに、起動が `EACCES`、`bind: An attempt was made to access a socket in a way forbidden by its access permissions`、`ports are not available` で失敗する。
- 確認方法：`netsh interface ipv4 show excludedportrange protocol=tcp` の範囲に、そのポートが含まれているか。
- 解決方法：compose・バックエンド・画面を停止してから、管理者の PowerShell で `scripts\dev\windows\exclude-ports.ps1` を実行する（スロット 1〜3 のポートも対象）。動作は script の冒頭のコメントにある。`-WhatIf` で、照合した結果だけを表示する。
- 暫定的な対処：管理者の権限で `net stop winnat` と `net start winnat` を実行する。再起動などでまた含まれることがある。

## エージェントの MCP サーバー（任意）

なくても同じ作業ができる。使える環境では、次の場面での使用を推奨する。

| MCP サーバー | 使う場面 | 準備 |
|--------------|----------|------|
| IntelliJ IDEA の MCP サーバー | Java のメソッドの呼び出し元を調べる（同じ名前のメソッドが複数あるとき、移動・削除の前）。依存ライブラリのソースの中のクラスやメソッドを検索する | IntelliJ IDEA でこのプロジェクトを開き、MCP サーバーの機能を有効にして、エージェントに登録する（手順は IntelliJ IDEA の文書） |
| Context7 | ライブラリの最新の文書を参照する（Spring Boot 4、Spring Modulith 2 など、エージェントの知識より新しいバージョン） | Context7 の手順（`npx ctx7 setup`）で、MCP サーバーとして利用者の全体の設定に登録する。API キーは任意（使える回数が増える） |

理由：同じ名前のメソッドが複数あると、文字列の検索では呼び出しを区別できない。IDE の呼び出しの階層は、メソッドの型まで区別する。

## スロット（作業ツリーごとの環境）

複数の作業ツリーで並行して開発するとき、作業ツリーごとにデータベース、画像のバケット、ポートを分ける。分けた環境を「スロット」と呼ぶ。compose の共有の基盤（PostgreSQL、SeaweedFS、imgproxy、Keycloak など）は 1 つで、全てのスロットが使う。

- メインの作業ツリー（`git worktree list` の最初の項目）はいつもスロット 0 で、「起動」の手順と値のまま動く。割り当ては要らない。
- 作業ツリーには、スロット 1〜3 のどれかを割り当てる。割り当ては `<作業ツリー>/.local/slot` に番号だけで書かれる（Git に入らない）。
- 共有の基盤は、メインの作業ツリーで `docker compose up -d` を実行して起動する。作業ツリーの script は compose を起動しない。止まっていると、script はメインの作業ツリーのパスと `docker compose up -d` を示して終了する。

```bash
# 新しい作業ツリーのルートで：始めに依存を入れる
pnpm install --frozen-lockfile

# 作業ツリーのルートで：空いている最小のスロットを割り当てる（データベースと 2 つのバケットを作り、seed の画像をコピーする）
node scripts/dev/slot.mjs assign

# スロットの環境で起動する（それぞれ別のターミナルで）
node scripts/dev/slot.mjs run backend
node scripts/dev/slot.mjs run store
node scripts/dev/slot.mjs run admin

# 割り当てとデータを残したまま、自分のスロットのポートで待ち受けるプロセスを停止する（対象を省略すると全て）
node scripts/dev/slot.mjs stop [backend|store|admin]

# スロットのポート・URL・名前と、全ての作業ツリーのスロットの一覧を示す
node scripts/dev/slot.mjs show

# 作業ツリーを削除する前に：データベースとバケットを削除し、割り当てを解除する
node scripts/dev/slot.mjs release
```

各コマンドの振る舞いと終了コードは、`scripts/dev/slot.mjs` の冒頭のコメントにある。`release` は、ポートの停止を確認できないときと、停止の後もバックエンドが応答するときは、データを削除せず終了コード 1 で終了する（動いているバックエンドの DB を消さないため）。

Windows で `run backend` の node を強制終了すると、Gradle のクライアントとバックエンドが残る。`stop` で停止できる（データも削除するときは `release`）。`stop` は、停止の後もポートで待ち受けるプロセスが残るときと、ポートを問い合わせられないときに、手動で停止するコマンドを示して終了コード 1 で終了する。

ポートは「スロット 0 の値 + 10000 × スロットの番号」。データベースとバケットは、名前の末尾にスロットの番号を付ける。ポートの一覧は `scripts/dev/ports.json` にある。ポートを加えるときはそこに加え、`node scripts/ci/local-ports.mjs` で他の置き場所との食い違いを確認する。

| スロット | バックエンド | 運用ポート | 店舗画面 | 管理画面 | データベース | 配信のバケット | 受付のバケット | 店舗画面の Cookie の接頭辞 |
|---|---|---|---|---|---|---|---|---|
| 0（メインの作業ツリー） | 8080 | 9080 | 3000 | 5173 | `ec_site` | `ec-site-images` | `ec-site-images-incoming` | なし |
| 1 | 18080 | 19080 | 13000 | 15173 | `ec_site_1` | `ec-site-images-1` | `ec-site-images-incoming-1` | `s1-` |
| 2 | 28080 | 29080 | 23000 | 25173 | `ec_site_2` | `ec-site-images-2` | `ec-site-images-incoming-2` | `s2-` |
| 3 | 38080 | 39080 | 33000 | 35173 | `ec_site_3` | `ec-site-images-3` | `ec-site-images-incoming-3` | `s3-` |

- 作業ツリーを `release` せずに消しても、そのスロットは次の `assign` で空きになる。データベースとバケットは共有の基盤に残るが、`assign` が再作成する。
- `node_modules` のある作業ツリーを Windows で削除すると、パスが長すぎて失敗する。`git config core.longpaths true` を設定してから `git worktree remove <パス>` で削除する。失敗が続くときは、PowerShell で `\\?\` を付けたパスを指定して削除する。
- 店舗画面の Cookie はポートで分かれないので、スロットごとに環境変数 `STORE_COOKIE_PREFIX` で接頭辞を変える（[環境変数](environment.md)の「店舗画面」）。Keycloak のログインの状態は共有される。
- `env` の出力は、`bootRun` などを手動で実行するときだけ subshell の中で使う（`(eval "$(node scripts/dev/slot.mjs env)"; ./gradlew bootRun ...)`）。export したままの shell で `./gradlew check` を実行すると、スロットの値が統合テストの設定を上書きして失敗しうる。
- E2E のポート（8090、9090、4173、3100）、データベース、バケットは、どのスロットとも重ならない。E2E は作業ツリーからも流せる（[テストの実行方法](running-tests.md)の「E2E」）。

### スロットで使えないもの

| 機能 | 扱い |
|------|------|
| Stripe の通知の転送（`stripe-cli`）、機能紹介のスクリーンショット（`pnpm capture`） | スロット 0（メインの作業ツリー）だけで対応する |
| Mailpit、Zipkin | 全スロットで共有する（スロットごとには分けない） |

### 共有の基盤の設定を変える変更をマージしたとき

共有の基盤の設定を変える変更を取り込んだら、メインの作業ツリーで次を実行して反映する。

| 変更 | 実行するもの |
|------|--------------|
| imgproxy の許可、SeaweedFS の権限（`docker/compose.yaml`、`docker/seaweedfs/s3.json`） | `docker compose up -d --force-recreate seaweedfs imgproxy`（bind mount した設定ファイルの中身だけの変更では、compose がコンテナを再作成しないため） |
| realm の JSON（`docker/keycloak/realms/`） | `scripts/dev/reset.sh keycloak` |
| ポートの追加（`scripts/dev/windows/exclude-ports.ps1`） | 管理者の PowerShell で予約の script を実行する（「ポートが使えないとき」） |

realm を再度取り込む前は、スロットの画面のログインがリダイレクト先のエラー（Invalid redirect uri）で失敗する。

## 手元の置き場所（`/.local/`）

`/.local/` は Git に入らない手元の置き場所である。作業用の script、テストデータ、引き継ぎの文書、マシン固有のパス、個人の習慣を置く。ここで扱うのはメインの作業ツリーの `/.local/` である（作業ツリーの `.local/slot` は「スロット」。作業ツリーのレビューの記録は、その作業ツリーの `.local/review/` に置き、作業ツリーの削除で消える。skill `review`。作業ツリーの振り返りは、その作業ツリーの `.local/retro/` に書き、削除の前にメインの作業ツリーにコピーする。skill `agent-retro`）。フォルダーの名前と場所は、文書と skill が参照しているので変えない。

| 項目 | 中身 | 使う者 | 保存の期間 |
|------|------|--------|------------|
| `agent/` | `environment.md`（このマシンと利用者に固有の環境と習慣）、`usage-log.md`（使用量の記録）、`zh-allow.txt`（skill `zh-mixing` の手元だけの除外） | エージェント | 残す。script は置かない（`scripts/dev/` に置く） |
| `handoff/` | 引き継ぎの文書（skill `handoff`） | 両方 | 引き継いだ後に削除する |
| `retro/` | 振り返り（skill `agent-retro` が読む） | エージェント | 残す |
| `review/` | レビューの記録（skill `review`） | エージェント | その spec のマージ後に、後片付けで削除する |
| `impl/` | 実装役の報告 | エージェント | その spec のマージ後に、後片付けで削除する |
| `pr/` | pull request の本文の下書き | エージェント | その spec のマージ後に、後片付けで削除する |
| `memo/` | issue 担当のセッションの備忘録 | エージェント | フェーズ・ステップの終わりに削除する |
| `refactor/` | 整理のフェーズの目的、候補の一覧、基準 | エージェント | 整理のフェーズが終わったら削除する |
| `issues/` | 起票に使った script と JSON | エージェント | 起票が済んだら削除する |
| `agent-runs/` | モデルの比較の記録 | エージェント | 結果を [エージェントの作業フローの調べた仕組みと実績](../07-記録/subagents-results.md) に移した後に削除する |
| `e2e-flow/` | 全画面を撮る作業用の Playwright の設定 | 利用者 | 利用者が使う間は残す |
| `masterdata/` | 日本郵便の郵便番号データ（[地域モジュール](../03-機能仕様/region.md)） | 両方 | 残す |
| `seed-images/` | 商品画像のプロンプトの一覧 | 利用者 | 残す |
| `stripe.env` | Stripe の sandbox の鍵（「Stripe で動かす」） | 両方 | 残す |
| `WinNAT再起動.ps1` | WinNAT を再起動する script（「ポートが使えないとき」に同じ手順がある） | 利用者 | 利用者が使う間は残す |

- 上の表にないものは、置いたまま忘れた作業用のファイルである。見つけたら、中身の一覧を利用者に示し、許可を得て削除する。
- spec ごとの記録（`review/`、`impl/`、`pr/`）の削除は、[マージと後片付け](../05-開発規則/merging.md)の「マージの許可と後片付け」で行う。

## IDE の設定

IntelliJ IDEA で、Java の import の並びを Checkstyle の検査に合わせる設定と、エージェントが整形するときの注意である。import の並びの規則は、[バックエンドの書き方](../05-開発規則/backend-coding.md)の「書式」にある。

### import の並び

Settings → Editor → Code Style → Java → Imports の「Import Layout」を、次の順にする（既定から `import javax.*` と `import java.*` の 2 行を入れ替える）。

1. `import all other imports`
2. 空行
3. `import java.*`
4. `import javax.*`
5. 空行
6. `import static all other imports`

同じ画面の「Class count to use import with *」と「Names count to use static import with *」は、999 にする。

### エージェントによる整形

- エージェントは、IntelliJ の MCP（`reformat_file`）を使えるとき、変えた Java のファイルを整形してよい。
- `reformat_file` が整形できるのは、IntelliJ で開いているプロジェクトのファイルだけ。`.claude/worktrees/` の下の作業ツリーのファイルは、その作業ツリーを IntelliJ で開いていなければ、MCP が「ok」を返しても整形しない。整形の後に `git diff` で変わったかを確認する。
