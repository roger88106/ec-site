---
name: local-dev
description: ec-site をローカルで起動・確認する手順。DB・Keycloak（compose）の起動と接続、token の中身の確認、バックエンドの local profile での起動と待ち方、スロット（作業ツリーごとの環境）、フロント（store・admin）と E2E を要約だけで実行するスクリプト、commit の前の CI の検査、プレビューでの画面の確認方法、壊しての確認（break-check）、手元の hook の設定、シェルと Git の落とし穴。バックエンドやフロントを動かす、テストや E2E を実行する、commit する、画面を確認する、DB や token を見るときに使う。
---

# ローカルでの起動と確認

エージェントだけが行う手順（待ち方、出力の読み方、避けるコマンドの書き方）を書く。人も行う手順は、各節が指す `docs/06-手順書/` の節にある。この skill と指された節のとおりに実行する。

前提：JDK 21、Node.js と pnpm、Docker が使えること。マシンに固有のこと（JDK の場所、PATH、OS の癖）は、`/.local/agent/environment.md` があればそれに従う（AGENTS.md「環境」）。

以下の 8080、9080、3000、5173 と DB `ec_site` などの固定の値は、メインの作業ツリー（スロット 0）の値。追加の作業ツリーは、[開発環境の起動](../../../docs/06-手順書/setup.md)の「スロット」の表の値を使う。

## ミドルウェア（compose）

```bash
docker compose up -d          # メインの作業ツリーのルートで。PostgreSQL、Zipkin、SeaweedFS、imgproxy、Keycloak、Mailpit
docker compose stop           # 止める（データは残る）
```

- Keycloak は起動に 20 秒ほどかかる。`docker inspect -f '{{.State.Health.Status}}' ec-site-keycloak` が `healthy` になるまで待つ。
- Keycloak の起動に失敗し、ログに database `keycloak` がないと出たら、`scripts/dev/reset.sh keycloak` で再作成する（[開発環境の起動](../../../docs/06-手順書/setup.md)の「データの再作成」。database は compose の PostgreSQL を初めて作ったときだけ自動で作られる）。

- DB：`localhost:5432`、DB `ec_site`、ユーザー `ec_site`、パスワード `ec_site`。
- SQL を見るときはコンテナの psql を使う（ローカルに psql はない）。スキーマはモジュールごと（`\dn`、`\dt <スキーマ>.*` で一覧にする。注文は `ordering`）。

  ```bash
  docker exec ec-site-postgres psql -U ec_site -d ec_site -c "select order_number, status from ordering.sales_order order by ordered_at desc limit 5"
  ```

## 認証（Keycloak、Mailpit）

realm の設定は `docker/keycloak/realms/` の JSON。アカウントと、JSON を変えたときの再インポートは、[開発環境の起動](../../../docs/06-手順書/setup.md)の「アカウント」と [開発環境の起動](../../../docs/06-手順書/setup.md)の「データの再作成」。

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
- 依存を追加した・上げたときは、ルートで `pnpm install --frozen-lockfile` とライセンスの検査を再実行する（[フロントの書き方](../../../docs/05-開発規則/frontend-coding.md)の「依存を追加したら再インストールして確認する」）。手元は依存が入った後なので、テストが通っても CI のインストールで失敗することがある。

開発サーバーは Bash ではなく、プレビューのツール（`preview_start`）で `.claude/launch.json` の名前を指定して起動する。プレビューのツールがないとき（コマンドラインの実行など）は、各アプリのディレクトリで `pnpm dev` をバックグラウンドで実行し、URL に curl して 200 を確認する。

| 名前 | URL | 前提 |
|------|-----|------|
| `admin` | http://localhost:5173 | バックエンド（`/api` を :8080 に転送） |
| `store` | http://localhost:3000 | バックエンド（API はサーバーから呼ぶ） |

- 店舗画面の一覧は API の結果を最大 60 秒キャッシュする。管理画面での変更がすぐ表示されないのは正常。

### プレビューで画面を確認する

エージェントが実装の途中で表示を確認するときの、ツールの使い方。利用者が画面を確認する手順は、skill `new-screen` の「画面を追加し終えたら」にある。

- IdP のログインの画面は、描画の前だとキーの入力やクリックが失敗する。`find` で入力欄を探し、`form_input` で local の seed のアカウント（`docs/06-手順書/setup.md`）を入れ、`javascript_tool` で `document.querySelector('form').submit()` を呼ぶ。
- 表示の中身は `get_page_text` で読む。スクリーンショットは描画を待ってタイムアウトしやすいため、見た目を見るときだけ使う。
- 開く画面の ID は DB から取得する（例：注文と支払は `ordering.sales_order` と `payment.payment` を結ぶ）。

## スロット（作業ツリーごとの環境）

追加の作業ツリーは、スロット 1〜3 を 1 つ割り当てて、環境を分ける。割り当て、ポートの表、コマンド、使えないもの、環境変数の扱いは[開発環境の起動](../../../docs/06-手順書/setup.md)の「スロット」。自分のスロットは `node scripts/dev/slot.mjs show` で見分ける。

- `release` せずに作業ツリーを削除しても、残った DB とバケットは次の `assign` が再作成するので、手動で削除しない。
- メインの作業ツリーでは、`release`・`reset` も状態を変えない。
- 基盤が動いていないと、slot.mjs は終了コード 2 で終わる。追加の作業ツリーで `docker compose up` を実行しない。slot.mjs は作業ツリーのどこから実行してもよい。
- 自分が動いている作業ツリーは、メインの作業ツリーから `git worktree remove` で削除する。使うツールがセッションのアーカイブで作業ツリーを片付けるときは、それに任せる。作業ツリーのセッションが動いていると、ロックで削除できないので、先にセッションをアーカイブする。

バックエンドは、`run backend` で起動する（出力先をファイルにして、バックグラウンドで実行する）。

```bash
node scripts/dev/slot.mjs run backend > <作業用のディレクトリ>/bootrun.log 2>&1
curl -s --retry 60 --retry-delay 3 --retry-all-errors -m 5 -o /dev/null -w '%{http_code}\n' localhost:19080/actuator/health   # スロット 1 の運用ポート
```

- 待つ先は自分のスロットの運用ポート。`localhost:9080` はメインのバックエンドなので、待ってはいけない。
- 店舗画面と管理画面のプレビューは、`.claude/launch.json` の `store-<n>`・`admin-<n>`（`store-1`、`admin-2` など。`<n>` は自分のスロット）を `preview_start` で指定する。`admin`・`store` はスロット 0。プレビューのツールがないときは、`node scripts/dev/slot.mjs run store <n>`・`run admin <n>` をバックグラウンドで実行する。番号が自分のスロットと違うと起動しない。
- `run backend` がポートの使用で終了コード 1 で終わったら、表示された停止のコマンドで停止してから再実行する。
- 割り当てとデータを残したまま停止するときは、自分のスロットのポートで待ち受けているプロセスだけを停止する。Windows の PowerShell では `Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -State Listen).OwningProcess`（スロット 1 のバックエンド）。他のスロットのポートのプロセスを停止しない。
- DB を見るときは、`docker exec ec-site-postgres psql -U ec_site -d ec_site_<n> -c "..."`（`<n>` は自分のスロット）。

## CI の検査の script

`./gradlew check` と `check-frontend.sh` に入っていない CI の検査。commit の前にルートで実行する（hook を置いていれば commit のときに実行される）。各検査の目的は [CI の検査](../../../docs/06-手順書/ci.md)の表にある。中身と失敗したときの修正の手順と除外の書き方は、各 script の先頭のコメントにある。

```bash
scripts/ci/check-all.sh
```

手元では、テスト名に出ない AC は警告だけで通る。spec の最後（push の前）は、CI と同じに確認する。

```bash
CI=true scripts/ci/check-all.sh
```

## 決済（mock と Stripe）

- profile `local` は mock で、注文すると mock の支払画面に移る（[決済の設計](../../../docs/02-設計/payment.md)の「mock」）。期限切れを早く確認する引数は、mock でも[開発環境の起動](../../../docs/06-手順書/setup.md)の「Stripe で動かす」のものを使う。
- Stripe の sandbox は[開発環境の起動](../../../docs/06-手順書/setup.md)の「Stripe で動かす」の通り。鍵は `/.local/stripe.env` にある（なければ利用者に頼む。鍵を会話に貼らせない）。
- 転送した通知は、鍵の行を出さずに読む：`docker logs ec-site-stripe-cli 2>&1 | grep -- '-->' | tail`（ログ全体は署名の鍵を含むので出さない）。
- 支払は `payment.payment`（`status`、`page_id`、`authorization_ref`）、注文は `ordering.sales_order`。
- Stripe の Checkout の画面は描画が遅く、入力が遅れて二重に入ることがある。入力の後に拡大して確認してから支払う。

## 機能紹介のページのスクリーンショット

[開発環境の起動](../../../docs/06-手順書/setup.md)の「機能紹介のページのスクリーンショット」の通り。

## ローカルの画面のデータ

local profile の seed（`db/seed/local/`）が、画面を確認するためのデータを作る。注文を作るスクリプトは必要ない。レポート用の注文の中身は `db/seed/local/README.md`「レポート用の注文」。

- 商品 15（公開 13、非公開 1、公開の予約 1）、カテゴリ 13、商品画像 31。在庫切れ（`TEE-001` の L、`PANTS-001-M`、`SENBEI-001`）と残りわずか（`TEE-002-M`、`JUICE-001`、`MUG-001-GY` など）がある。
- 会員 `member@example.com` の注文 4 件：受付、キャンセルの申請中、申請の却下（受付のまま）、キャンセル済み。どれも状態に合う支払（mock の与信）を持ち、受付の注文は管理画面から出荷できる（Stripe の profile では売上確定で失敗する）。承認・却下・運営者のキャンセルは、申請中の注文で管理画面か管理 API（`PUT /api/admin/orders/{orderId}/cancellation-request/approve` など）から試す。
- ID は固定（`00000000-0000-0000-0000-00000000` + 4 桁）：カテゴリ 1xxx、商品 2xxx、バリアント 3xxx、在庫 4xxx、会員 6001、注文 61xx、支払 64xx、アセット 7xxx、商品画像 8xxx。レポート用は `00000000-0000-0000-<種類 4 桁>-<番号 12 桁>`（会員 0001、注文 0010、支払 0012、出荷 0013）。
- 画面で変えたデータを最初の状態に戻す手順は、[開発環境の起動](../../../docs/06-手順書/setup.md)の「データの再作成」。

## E2E（`e2e/`）

手順と構成は[テストの実行方法](../../../docs/06-手順書/running-tests.md)の「E2E」。ルートで `scripts/dev/e2e.sh` を実行する。

```bash
scripts/dev/e2e.sh                                   # テストごとの ok / x と失敗の要点だけを出す。前提のコンテナが止まっていれば、名前を出して終了コード 2
scripts/dev/e2e.sh tests/payment.spec.ts -g AC-148   # 引数は Playwright にそのまま渡す
```

- 他の作業ツリーが実行中なら、所有者を示して終了コード 1 で終わる。待ってから再実行する（ロックは[テストの実行方法](../../../docs/06-手順書/running-tests.md)の「E2E」）。
- 準備でポートの EACCES が出たら、[開発環境の起動](../../../docs/06-手順書/setup.md)の「ポートが使えないとき」の手順で外す。

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

hook や検査に止められてからコマンドを書き換えないよう、初めから次の手順で行う。

- 複数のファイルの置き換えと、`\` や正規表現を含む書き換えは、初めから Edit で行う（新しいファイルは Write）。script が必要なときは、Write でスクラッチパッドに `.cjs` を書いて node で実行する。heredoc（`<<'EOF'` も）と `node -e` の文字列に `\` を入れない。理由：Windows の Git Bash では `\` が落ちて、正規表現が黙って壊れるため。hook `backslash-guard` も、heredoc と `node -e` の中の `\` を拒否する。Markdown の表のセルの `\|` も同じで、`\|` を含む表の追記も Edit で書く。
- コミットメッセージは、スクラッチパッドのファイルに書き、`node scripts/ci/ja-text.mjs --message <ファイル>` で検査してから、`git commit -F <ファイル>` で commit する。理由：hook `commit-msg` が、`scripts/ci/ja-text.json` の error の語を含むメッセージを拒否するため。文書で語を言い換えたときの、言い換える前の語も error の語なので、メッセージに書くと止まる。
- CI を待つときは、バックグラウンドで実行し、ワークフローのファイル名・`event`・commit で絞る。フォアグラウンドで長い `sleep` を実行しない（Claude Code の Bash ツールが拒否する）。絞らないと、同じ commit の別のワークフロー（Dependabot の更新など）の完了で待ちが終わる。

  ```bash
  gh run list -c <SHA> -w docs.yml -e push --json databaseId -q '.[0].databaseId'   # 実行の ID。push の直後は空のことがある
  gh run watch <ID> --exit-status
  ```

  - push で待つワークフローは、変えたファイルで決まる。`.md` と `.claude/` の下のファイルだけなら、ワークフロー「CI」（`ci.yml`）は push で実行されないので、ワークフロー「文書」（`docs.yml`）だけを待つ。理由：`ci.yml` の push の `paths` がそれらを除くため。ただし、モジュールの設計書など一部の `.md` は、`paths` が個別に指定しているので、push でも「CI」が実行される。pull request では、「CI」は変えたファイルによらず実行される。
- commit の前の hook（`pre-commit`）は、作業ツリーでなく index（ステージした内容）を検査する。ステージしていない変更は検査に入らない。そのため、前の段階の変更を参照するファイルを先に commit すると、参照先が index にないので、hook が切れた参照として commit を失敗にする。段階の順に commit する。
  - `git add -A && git commit` が hook で止まると、ステージしたものが残る。続けて一部のファイルだけを `git add` して commit すると、残っていたものも入る。止まったら `git reset -q` でステージを外してから、入れるものだけを `git add` する。
- 次の書き方は、拒否されるか、意図しない結果になる。表の「避け方」に替える。

| 書き方 | 拒否するもの | 避け方 |
|--------|------------|--------|
| heredoc・`node -e` の中の `\`（Markdown の表の `\|` を含む） | hook `backslash-guard`（次の節） | 上の箇条のとおり、`\|` を含む表の追記も Edit で書く |
| `gh pr edit` と `gh pr ready` など、GitHub の状態を変える `gh` の操作を `&&` や `;` で 1 つにつないだコマンド | auto mode の判定 | pull request の編集・ready・マージ、issue の編集は、1 つのコマンドに操作を 1 つだけ書き、別の呼び出しで実行する |
| `claude -p` の `--permission-mode bypassPermissions` | auto mode の判定 | `--permission-mode default` と `--allowedTools` を使う（skill `sessions` の「ツールの補足」） |
| コミットメッセージの、層 1 の使わない語（`scripts/ci/ja-text.json` の error の語） | git の hook `commit-msg`（日本語の検査） | 上の箇条のとおり、commit の前に `--message` で検査する。語の一覧は skill `zh-mixing` の「書くときの注意」 |
| spec やコミットメッセージに写した、利用者の中国語の言葉 | git の hook `commit-msg`・`pre-commit`（中国語の混入の検査） | 日本語に訳して書く（skill `zh-mixing` の「書くときの注意」） |
| `git -C` と `cd` のパスに変数やコマンドの置き換え（`git -C "$D" commit`、`cd "$(...)"` など） | hook `agent-guard`（`git commit` の対象のリポジトリを決められないとき） | 変数を使わず、絶対パスを直接書く |
| `cd` で `/.local/` などに移る | 拒否されない。以後のコマンドの作業ディレクトリが意図しない場所になる | `cd` で作業ディレクトリを移さず、`git -C <絶対パス>` と絶対パスを使う |

- 検査を `| tail` に通すと、終了コードが `tail` のものになり、`&&` で続く commit が検査の失敗でも実行される。検査と commit は別の呼び出しにするか、`set -o pipefail` を付ける。

## 手元の hook の設定

hook と permission の書き先は 2 つに分ける。チームの例外のない規則は `.claude/settings.json`（Git に入る）、環境・利用者に固有のものは `.claude/settings.local.json`（Git に入らない）に置く。hook の script は `scripts/dev/hooks/` に置く（Claude Code の `agent-guard.mjs`、git の `pre-commit`・`commit-msg`、手元の hook の `session-start.mjs`・`backslash-guard.mjs`）。例外は zh-mixing の手元の hook で、script は `.claude/skills/zh-mixing/` に置く。

利用者の環境（`.claude/settings.local.json`）に置く 3 つの hook は次のとおり。読む場所と、変更が反映される時点は、skill `sessions` の「作業ツリーのセッションが読むもの」の表。設定はメインの作業ツリーに置く。

| hook | イベントと matcher | 働き |
|------|--------------------|------|
| `session-start.mjs` | SessionStart、`startup\|resume\|compact` | メインの作業ツリーの `/.local/agent/environment.md` を文脈に入れる。hook が動くセッションでは、どの作業ツリーでも同じファイルを読む。ファイルがなければ何も出さない |
| `backslash-guard.mjs` | PreToolUse、`Bash` | heredoc と `node -e` の中の `\` を拒否する。コマンドの他の箇所の `\`（grep の `\|` など）は通す |
| `post-tool-use.cjs` | PostToolUse、`Write\|Edit\|MultiEdit\|NotebookEdit` | 書いたファイル（`tool_input.file_path`）を層 2 の検査にかけ、見つかれば終了コード 2 で返す（詳細は表の下の箇条） |

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
    ],
    "PostToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit|NotebookEdit",
        "hooks": [
          { "type": "command", "command": "f=; for d in \"$(git rev-parse --show-toplevel 2>/dev/null)\" \"$CLAUDE_PROJECT_DIR\"; do if [ -n \"$d\" ] && [ -f \"$d/.claude/skills/zh-mixing/post-tool-use.cjs\" ]; then f=\"$d/.claude/skills/zh-mixing/post-tool-use.cjs\"; break; fi; done; [ -z \"$f\" ] || node \"$f\"" }
        ]
      }
    ]
  }
}
```

- 既に `hooks` があれば、イベントごとの配列に項目を追加する。設定を変えたら、新しいセッションを起動する。
- PostToolUse の項目は、中国語で指示する環境だけに置く。環境の印と git の hook は skill `zh-mixing`。
- `post-tool-use.cjs` は、書いたファイルのうち、リポジトリのファイルと memory のファイル（`~/.claude/projects/<プロジェクト>/memory/` の下）を調べる。`/.local/` の下とリポジトリの外の他のファイルは調べない。見つかれば終了コード 2 と標準エラー出力（ファイル、行、語、直し方）で返し、Claude Code がエージェントに渡す。script は `.claude/skills/zh-mixing/` にある。
- 他の置き方として、同じ項目を利用者の設定（`~/.claude/settings.json`）に置く方法もある。その場合は他のリポジトリでも呼ばれ、script がないリポジトリでは node が失敗するので、PostToolUse の command を `f="$CLAUDE_PROJECT_DIR/.claude/skills/zh-mixing/post-tool-use.cjs"; [ ! -f "$f" ] || node "$f"` にする。script があるリポジトリでも、環境の印がなければ、入口は何も出さず終了コード 0 で終わり、リポジトリの外で呼ばれても同じである（テストが調べる）。
