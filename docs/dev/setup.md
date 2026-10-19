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
| http://localhost:8080/mock-carrier | mock の物流サービス（出荷の配送状態を、配送中・配達完了・返送に進める） |
| http://localhost:9080/actuator/health | ヘルスチェック（`/liveness`、`/readiness`）。運用ポート（9080）で出し、業務ポート（8080）では出さない |
| http://localhost:9411 | Zipkin（トレース。local では全リクエストを記録） |
| http://localhost:8333 | SeaweedFS（S3 互換。バケットは `seaweedfs-init` が作る） |
| http://localhost:8081 | imgproxy（画像の WebP への変換。署名のない URL は拒否） |
| http://localhost:8180 | Keycloak（管理画面は `/admin`、`admin` / `admin`） |
| http://localhost:8025 | Mailpit（Keycloak とバックエンドが送ったメール。外には送らない） |

- `docker compose` はルートで実行する（ルートの `.env` の `COMPOSE_FILE` が [docker/compose.yaml](../../docker/compose.yaml) を指す）。DB は `localhost:5432/ec_site`。
- Mailpit の SMTP はホストの 2525 で受ける（1025 は Windows のシステムのプログラムが使うことがあるので使わない）。2525 を他のプログラムが使っていると、バックエンドのメールが Mailpit に届かない。環境変数 `MAILPIT_SMTP_PORT` に空いたポートを入れて、compose とバックエンドを起動する（両方が同じ値を読む）。
- compose のポートは、このマシン（127.0.0.1）でだけ待ち受ける（ローカル用の管理のパスワードと DB のパスワードは公開されているため）。同じネットワークの他のマシン（スマートフォンなど）からつなぐときは、`docker/.env`（Git に入らない）に `LOCAL_BIND_ADDRESS=0.0.0.0` と書くか、同じ名前の環境変数を付けて、`docker compose up -d` で作り直す。終わったら消して作り直す。バックエンドと画面の開発サーバーは、この設定と別に待ち受ける。
- 止めるときは `docker compose stop`。データを作り直すときは第 4 節。
- 店舗画面は API の結果を最大 60 秒キャッシュするので、管理画面での変更は 1 分以内に反映される。
- フロントの API の型は、`dev`・`typecheck`・`build` の前に OpenAPI から生成される（コミットしない）。整形と自動で直せる lint は `pnpm lint:fix`。
- commit の前に CI の検査の script（[CI の検査](ci.md)）を流すには、`.git/hooks/pre-commit` に次の 1 行を書く（ファイルがなければ、1 行目に `#!/bin/sh` を書いて作る）。`.git/hooks/` に置いたほかの hook と並べられるよう、`core.hooksPath` は変えない。

  ```sh
  "$(git rev-parse --show-toplevel)/scripts/dev/hooks/pre-commit" || exit 1
  ```

## 3. アカウント

realm の設定は [docker/keycloak/realms/](../../docker/keycloak/realms) の JSON が正本で、Keycloak の起動時に取り込まれる。ローカルと CI で使うアカウント（パスワードはローカル専用）：

| realm | 画面 | アカウント | パスワード | 用途 |
|-------|------|------------|------------|------|
| `ec-store`（消費者） | 店舗 | `member@example.com` | `member-pass-1` | seed の注文を持つ会員。生年月日と性別を入れてある |
| `ec-store`（消費者） | 店舗 | `member2@example.com` | `member2-pass-1` | 他人のデータが見えないことの確認。生年月日と性別を入れていない（レポートの「不明」の確認） |
| `ec-admin`（運営者） | 管理画面 | `admin@example.com` | `admin-pass-1` | 管理者 |
| `ec-admin`（運営者） | 管理画面 | `viewer@example.com` | `viewer-pass-1` | 閲覧者 |
| `ec-admin`（運営者） | 管理画面 | `manager@example.com` | `manager-pass-1` | 管理職（すべてのレポート） |
| `ec-admin`（運営者） | 管理画面 | `sales@example.com` | `sales-pass-1` | 営業（売上の推移を除くレポート） |

- 手動確認はこの表のアカウントを使う（誰の環境にもある）。足りなければ realm の JSON に足し、表にも書く。画面から登録した会員は、その人の環境にしかない。
- 会員のアカウント ID は realm の JSON で固定している（`member@example.com` は `00000000-0000-0000-0000-000000009001`、`member2@example.com` は `…9002`）。ローカル用の seed の注文が、この会員のものになるため。
- 会員の生年月日と性別（任意の属性）は、会員登録とアカウントの画面（`http://localhost:8180/realms/ec-store/account`）で入れる・直す。表示の文言は realm の JSON の `localizationTexts` に置く。
- 会員登録の確認とパスワードの再設定のメール（Keycloak）、出荷通知メール（バックエンド）は Mailpit に届く。宛先のアドレスは実在しなくてよい。
- token の中身は `scripts/dev/keycloak.sh token <realm> <アカウント> <パスワード>` で見る。

## 4. データの作り直し

画面で変えたデータは、seed を流し直しても戻らない。`scripts/dev/reset.sh` で、Git の正本から作り直す。

| 戻したいもの | コマンド（ルートで） | 触れないもの |
|--------------|----------------------|--------------|
| DB だけ | `scripts/dev/reset.sh`（`db` と同じ）。バックエンドを止めてから流す。次の起動で seed が流れる（レポート用の注文 2 万件を含め、15 秒ほど） | E2E の DB、Keycloak、画像 |
| Keycloak（realm の JSON を変えたとき） | `scripts/dev/reset.sh keycloak`（ローカルで登録した会員も消える） | DB、E2E の Keycloak |
| すべて | `scripts/dev/reset.sh all`（`docker compose down -v` と `up -d`） | なし |

正本は、DB の seed が `backend/ecsite/src/main/resources/db/seed/`、画像が `docker/seaweedfs/seed/`、Keycloak が `docker/keycloak/realms/`。seed の商品の画像の ID と、seed の注文の会員の ID は、それぞれ画像と realm の正本に合わせてある。

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
- 支払の画面に出る支払の方法は、ダッシュボードの「設定 → 決済手段」で決まる。カードだけを有効にする。カード以外の方法（コンビニ、銀行振込など）は与信だけを取れない。カードで払うウォレット（Apple Pay、Google Pay、Link）は MVP の後に見直す。
- 期限切れを早く確かめるときは、`--ecsite.payment.deadline=PT3M --ecsite.payment.expiry-interval=PT20S` を足す。Stripe の支払の画面は作ってから 31 分で切れるが、支払の期限で期限切れのジョブが閉じる。
- seed の注文（`db/seed/local/`）の与信は mock のもので、Stripe には無い。Stripe で動かすときは、seed の注文を出荷すると売上確定で失敗する。出荷は、Stripe で支払った注文で確かめる。

## 6. 機能紹介のページのスクリーンショット

機能紹介のページ（`showcase/`）の画像を、ローカルの環境の画面で撮り直す。機能を足して画面が変わったときに使う。

1. バックエンドを止め、DB を作り直す（第 4 節。画面で変えたデータと、前に撮ったときの注文を消すため）。
2. compose、バックエンド（profile `local`。Stripe の profile は付けない）、店舗画面（:3000）、管理画面（:5173）を起動する（第 2 節）。
3. `e2e/` で `pnpm capture`。`showcase/img/` の 14 枚を上書きする。

- 撮影の中で、会員として注文して支払い、もう 1 つ注文して支払わずに戻る（支払待ちの画面のため）。白のマグカップの在庫を 2 つ使うので、続けて撮るときも DB を作り直す。
- 撮影は E2E ではなく、CI では流さない。撮った画像は目で確かめてから commit する。
- ページの数字（条件・テスト・仕様書の数）は、`./gradlew check` の後にルートで `node scripts/dev/showcase-counts.mjs` を流して照らし、変わっていれば直す。テストの数は、Java を実行した件数（パラメーターの組ごと）で数える。

## 7. ポートが使えないとき（Windows）

Windows は、WinNAT（Docker や WSL が使う）などのために、ポートの範囲を動的に予約する。ローカルの環境のポートがその範囲に入ると使えなくなる。

- 症状：待ち受けているプロセスがないのに、起動が `EACCES`、`bind: An attempt was made to access a socket in a way forbidden by its access permissions`、`ports are not available` で失敗する。
- 確かめ方：`netsh interface ipv4 show excludedportrange protocol=tcp` の範囲に、そのポートが入っているか。
- 直し方：compose・バックエンド・画面を止めてから、管理者の PowerShell で `scripts\dev\windows\exclude-ports.ps1` を流す。ローカルの環境のポートを予約の範囲と照らして示し、確かめてから WinNAT を止め、ポートを 1 つずつ予約して WinNAT を始め直す。予約は再起動の後も残る。`-WhatIf` を付けると、照らした結果だけを示す。
- その場の対処：管理者の権限で `net stop winnat` と `net start winnat` を流す。動的な範囲が取り直されるので空くことが多いが、再起動などでまた入ることがある。
