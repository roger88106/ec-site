# テストの実行方法

テストの書き方は [テストの書き方](../rules/test-writing.md)、起動の準備は [開発環境の起動](setup.md)。エージェントは skill `local-dev` を使う。

## 1. バックエンド

`backend/ecsite/` で実行する。

| コマンド | 内容 | Docker |
|----------|------|--------|
| `./gradlew test` | 単体テスト（Spring を起動しない） | 不要 ※1 |
| `./gradlew integrationTest` | 統合テスト（Spring + Testcontainers の PostgreSQL など） | 必要 |
| `./gradlew build`（`check`） | 単体・統合テスト、OpenAPI の構文の検証（CI と同じ） | 必要 |
| `./gradlew coverageReport` | 単体テストと統合テストを合算したカバレッジ（`build/reports/jacoco/coverageReport/`。`check` の後にも実行できる） | 必要 |

※1：jOOQ のコード生成は Testcontainers の PostgreSQL にマイグレーションを適用して行うため、マイグレーションか生成の設定が変わったときだけ Docker が必要（変わらなければ `generateJooq UP-TO-DATE` でスキップされる）。

単体テストには、構成と契約の検査も含まれる：モジュールの境界（`ModulithTest`）、アーキテクチャの規則（`architecture/` の ArchUnit）、契約の制約（`contract/`）、エラーの定義とメッセージの対応（`MessageContractTest`）。

## 2. 管理画面・店舗画面

`frontend/admin/`・`frontend/store/` で実行する（ルートで実行すると、全てのプロジェクトをまとめて実行する）。

```bash
# CI と同じ確認（lint と整形、型チェック、テスト、ビルド）
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

- 管理画面の `pnpm build`（本番ビルド）は、環境変数 `VITE_OIDC_AUTHORITY` と `VITE_OIDC_CLIENT_ID` がないと失敗する（[環境変数](environment.md)）。ローカルの値で確認するときは、`VITE_OIDC_AUTHORITY=http://localhost:8180/realms/ec-admin VITE_OIDC_CLIENT_ID=ec-admin-web pnpm build`。
- `pnpm test` はカバレッジのレポートを `coverage/index.html` に生成する。
- 管理画面の結合テストはバックエンドなしで実行できる（API の応答は MSW）。
- 要約だけを出力する `scripts/dev/check-frontend.sh` でも実行できる（管理画面のビルドの環境変数は、未設定ならローカルの値を使う）。

## 3. E2E（`e2e/`）

バックエンド、管理画面、店舗画面を起動して、Chromium で操作する（Playwright）。専用の DB（`ec_site_e2e`）、IdP（Keycloak、:8181）、画像のバケットを使い、ローカルのデータを変えない。

```bash
# ルートで：compose を起動し、依存を入れる（E2E 用の Keycloak は準備の script が起動する）
docker compose up -d
pnpm install

# e2e/ で：初回だけブラウザを入れ、E2E を実行する
pnpm exec playwright install chromium
pnpm test
```

| 段階 | 内容 |
|------|------|
| 準備（`e2e/scripts/prepare.ts`） | E2E のサーバーのポート（:8090、:4173、:3100）で待ち受けられるかを確認する（Windows の予約範囲に含まれるポートは EACCES で失敗する）。E2E 用の DB と Keycloak の database を再作成し、E2E 用の Keycloak（compose の profile `e2e`）を再起動する。毎回、マイグレーションと税のデータ（`db/seed/base`）だけの DB、realm の JSON の状態の IdP から始まる |
| 起動 | バックエンド（:8090、profile `local,e2e`）、管理画面（:4173）、店舗画面（:3100）を毎回起動する。画面のサーバーは手元でも再利用しないので、ポートが使われていれば Playwright が停止する |
| データ | 管理 API で作り、品番・名称に実行ごとの識別子（`E2E-<印>-…`）を付ける |

- バックエンドの起動に JDK 21 を使うため、`JAVA_HOME` に JDK 21 のパスを設定しておく。
- 手元の開発のバックエンド（:8080）と、スロットのバックエンド（[開発環境の起動](setup.md) 第 9 節）と並行して実行できる。
- メインの作業ツリーでも、作業ツリーでも、`pnpm test`（`e2e/scripts/run.ts`）を実行できる。E2E は DB（`ec_site_e2e`）、IdP、ポートを共有するので、全ての作業ツリーで 1 つずつ実行する。実行中は git の共通のディレクトリに `ec-site-e2e.lock`（作業ツリーのパス、PID、開始の時刻）を置き、他の作業ツリーで実行中なら、持ち主の作業ツリーのパスと開始の時刻を示して終了コード 1 で終了する。持ち主のプロセスがなければ、古いロックとして取り直す。
- E2E は常にスロット 0 の固定の環境で実行する。スロットの環境変数（`node scripts/dev/slot.mjs env` の出力など）を shell に設定していても、E2E の入口が除いてから実行する。
- 管理画面・店舗画面を、E2E のポート（:4173、:3100）で起動したままにしない。E2E は起動済みのサーバーを使わず、ポートが使われていれば停止する。別の作業ツリーのコードを試すことを避けるためである。
- 店舗画面の API のキャッシュは、E2E では 1 秒（`STORE_API_REVALIDATE_SECONDS`）。
- 決済は mock で、外部の決済サービスに接続しない。支払の期限は、期限切れを待つテストのため 12 秒（`application-e2e.yml`）。注文から mock の画面で支払うまでの操作は、この期限内に完了する（CI で 2 秒ほど）。
- compose の設定を変えたコミットを取り込んだら、`docker compose up -d` で反映し、`docker compose run --rm seaweedfs-init` でバケットを作る（`docker/seaweedfs/s3.json` を変えたときは `docker compose restart seaweedfs` も必要）。
- 結果の要約だけを見るときは、ルートで `scripts/dev/e2e.sh [Playwright の引数]`（テストごとの ok / x と失敗の要点だけを出力し、全体はログのファイルに残す）。
- 失敗したときは `test-results/` のスクリーンショットとトレース（`pnpm exec playwright show-trace <trace.zip>`）を確認する。

## 4. 既存のテストを変えていないことの確認

振る舞いを変えない整理（書き方の統一など）では、既存のテストの変更が、本番のクラスの移動と名前の変更に伴うものと、中身を変えずにテストを別のファイルへ移したものだけであることを確認する。

```bash
# ルートで：元の commit から今の作業ツリーまでを比べる
node scripts/ci/review/test-diff.mjs <元の commit> [名前の対応のファイル]
```

- 対象は、テストのファイル（バックエンドの `src/test`・`src/integrationTest`、画面の `*.test.ts(x)`、`e2e/`）の差分。
- `import`・`package` の行（複数行の import の中の行を含む）を除き、既存のファイル（削除したファイルを含む）の削除した行に名前の対応を適用した結果を、全てのテストのファイルの追加した行と、前後の空白を除いて照合する。照合できない行を出力して失敗する。
- 既存のファイルの追加した行で、照合して残ったものも出力する。新しく追加したファイルの、照合して残った行は対象外（移した先の import と枠の行、新しいテスト）。
- 名前の対応のファイルは、1 行に `旧=新`（クラスの単純名か完全修飾名、画面はモジュールのパスも）。名前は語の境界で置き換える。
