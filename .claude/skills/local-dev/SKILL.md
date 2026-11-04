---
name: local-dev
description: ec-site をローカルで起動・確認する手順。スロット（作業ツリーごとの環境）、compose・バックエンド・フロントの起動、テストと E2E。commit の前の CI の検査、画面・DB・token の確認、壊しての確認（break-check）、手元の hook の設定のときに使う。
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
scripts/dev/check-frontend.sh                       # 全部（CI と同じ）
scripts/dev/check-frontend.sh admin test            # 管理画面のテストだけ
scripts/dev/check-frontend.sh store lint typecheck  # 対象と手順を選ぶ
```

- 手順の一覧と、対象ごとに実行する手順は、`scripts/dev/check-frontend.sh` の冒頭のコメントにある。
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
```

- 起動を待つ curl は「バックエンド」の節と同じで、待つ先は自分のスロットの運用ポート（スロット 1 は `localhost:19080`）。`localhost:9080` はメインのバックエンドなので、待ってはいけない。
- 店舗画面と管理画面のプレビューは、`.claude/launch.json` の `store-<n>`・`admin-<n>`（`store-1`、`admin-2` など。`<n>` は自分のスロット）を `preview_start` で指定する。`admin`・`store` はスロット 0。プレビューのツールがないときは、`node scripts/dev/slot.mjs run store <n>`・`run admin <n>` をバックグラウンドで実行する。番号が自分のスロットと違うと起動しない。
- `run backend` がポートの使用で終了コード 1 で終わったら、表示された停止のコマンドで停止してから再実行する。
- 割り当てとデータを残したまま停止するときは、[開発環境の起動](../../../docs/06-手順書/setup.md)の「スロット」のコマンドを使う。
- DB を見るときは、`docker exec ec-site-postgres psql -U ec_site -d ec_site_<n> -c "..."`（`<n>` は自分のスロット）。

## CI の検査の script

`./gradlew check` と `check-frontend.sh` に入っていない CI の検査。commit の前にルートで実行する（hook を置いていれば commit のときに実行される）。各検査の目的は [CI の検査の script](../../../docs/06-手順書/ci-checks.md)の表にある。中身と失敗したときの修正の手順と除外の書き方は、各 script の先頭のコメントにある。

```bash
scripts/ci/check-all.sh
```

手元では、テスト名に出ない AC は警告だけで通る。spec の最後（push の前）は、CI と同じに確認する。

```bash
CI=true scripts/ci/check-all.sh
```

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

- 置き換える文字列が 1 か所だけに一致しなければ、変えずに止まる。テストの後は元に戻す。
- 最後の行が `→ 失敗した（壊すと検出できる）` なら終了コード 0、`→ 通った（壊しても検出できない）` なら 1。置き換える文字列が一致しない・コマンドが実行できないときは 2（理由は標準エラー出力）。詳しい終了コードと、元に戻す条件は `scripts/dev/break-check.mjs` の冒頭のコメントにある。
- Gradle のテストでは、出力に `FAILED` か `BUILD` の行があることを確認する。ない失敗は、テスト以外の原因（起動の失敗など）を疑う。

## シェルと Git の落とし穴

hook や検査に止められてからコマンドを書き換えないよう、初めから次の手順で行う。

- 複数のファイルの置き換えと、`\` や正規表現を含む書き換えは、初めから Edit で行う（新しいファイルは Write）。script が必要なときは、Write でスクラッチパッドに `.cjs` を書いて node で実行する。heredoc（`<<'EOF'` も）と `node -e` の文字列に `\` を入れない。理由：Windows の Git Bash では `\` が落ちて、正規表現が黙って壊れるため。hook `backslash-guard` も、heredoc と `node -e` の中の `\` を拒否する。Markdown の表のセルの `\|` も同じで、`\|` を含む表の追記も Edit で書く。
- コミットメッセージは、スクラッチパッドのファイルに書き、`node scripts/ci/ja-text.mjs --message <ファイル>` で検査してから、`git commit -F <ファイル>` で commit する。理由：hook `commit-msg` が、`scripts/ci/ja-text.json` の error の語を含むメッセージを拒否するため。文書で語を言い換えたときの、言い換える前の語も error の語なので、メッセージに書くと止まる（語の一覧は skill `zh-mixing` の「書くときの注意」）。
- CI を待つときは、バックグラウンドで実行し、ワークフローのファイル名・`event`・commit で絞る。フォアグラウンドで長い `sleep` を実行しない（Claude Code の Bash ツールが拒否する）。絞らないと、同じ commit の別のワークフロー（Dependabot の更新など）の完了で待ちが終わる。

  ```bash
  gh run list -c <SHA> -w docs.yml -e push --json databaseId -q '.[0].databaseId'   # 実行の ID。push の直後は空のことがある
  gh run watch <ID> --exit-status
  ```

  - push で待つワークフローは、変えたファイルで決まる。`.md` と `.claude/` の下のファイルだけなら、ワークフロー「CI」（`ci.yml`）は push で実行されないので、ワークフロー「文書」（`docs.yml`）だけを待つ。理由：`ci.yml` の push の `paths` がそれらを除くため。ただし、モジュールの設計書など一部の `.md` は、`paths` が個別に指定しているので、push でも「CI」が実行される。pull request では、「CI」は変えたファイルによらず実行される。
- commit の前の hook（`pre-commit`）は、作業ツリーでなく index（ステージした内容）を検査する。ステージしていない変更は検査に入らない。そのため、前の段階の変更を参照するファイルを先に commit すると、参照先が index にないので、hook が切れた参照として commit を失敗にする。段階の順に commit する。
  - `git add -A && git commit` が hook で止まると、ステージしたものが残る。続けて一部のファイルだけを `git add` して commit すると、残っていたものも入る。止まったら `git reset -q` でステージを外してから、入れるものだけを `git add` する。
- 最初の 2 つの箇条のほかに、次の書き方は、拒否されるか、意図しない結果になる。表の「避け方」に替える。

| 書き方 | 拒否するもの | 避け方 |
|--------|------------|--------|
| `gh pr edit` と `gh pr ready` など、GitHub の状態を変える `gh` の操作を `&&` や `;` で 1 つにつないだコマンド | auto mode の判定 | pull request の編集・ready・マージ、issue の編集は、1 つのコマンドに操作を 1 つだけ書き、別の呼び出しで実行する |
| `claude -p` の `--permission-mode bypassPermissions` | auto mode の判定 | `--permission-mode default` と `--allowedTools` を使う（skill `sessions` の「ツールの補足」） |
| spec やコミットメッセージに写した、利用者の中国語の言葉 | git の hook `commit-msg`・`pre-commit`（中国語の混入の検査） | 日本語に訳して書く（skill `zh-mixing` の「書くときの注意」） |
| `git -C` と `cd` のパスに変数やコマンドの置き換え（`git -C "$D" commit`、`cd "$(...)"` など） | hook `agent-guard`（`git commit` の対象のリポジトリを決められないとき） | 変数を使わず、絶対パスを直接書く |
| `cd` で `/.local/` などに移る | 拒否されない。以後のコマンドの作業ディレクトリが意図しない場所になる | `cd` で作業ディレクトリを移さず、`git -C <絶対パス>` と絶対パスを使う |

- 検査を `| tail` に通すと、終了コードが `tail` のものになり、`&&` で続く commit が検査の失敗でも実行される。検査と commit は別の呼び出しにするか、`set -o pipefail` を付ける。

## 場面ごとに読むファイル

- [画面の確認のデータ](screen-data.md)：決済（mock と Stripe）の確認、機能紹介のページのスクリーンショット、画面で確認する seed のデータを見るとき。
- [手元の hook の設定](hooks.md)：hook を `.claude/settings.local.json` に置く、修正するとき。
