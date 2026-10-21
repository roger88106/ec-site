# Plan 008：機能紹介のページ

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 007 が完了している（決済の画面がある）。原稿は `/.local/sales/index.html`（Git の外）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 置き場所 | 第 1 節 | DC-70 |
| スクリーンショットの撮影 | 第 2 節 | DC-67、72 |
| ページの中身 | 第 3 節 | DC-67、68、69、DN-9 |
| 公開 | 第 4 節 | DC-70 |
| README とリポジトリの構成 | 第 5 節 | DC-71 |
| 確認方法 | 第 6 節 | 全て |

## 1. 置き場所

| パス | 中身 |
|------|------|
| `showcase/index.html` | ページ（HTML 1 枚。CSS はページの中に書く。フォントは Google Fonts） |
| `showcase/img/` | スクリーンショット（PNG） |

- `docs/` には置かない（`docs/` は開発者とエージェント向けの文書）。
- 原稿の見た目（構成、明暗の両テーマ）はそのまま残す。Markdown には書き換えない。

## 2. スクリーンショットの撮影（DC-72）

Playwright で、手元で起動しているローカルの環境の画面を撮る。E2E とは設定を分ける。

| 項目 | 決め |
|------|------|
| 置き場所 | `e2e/showcase/capture.spec.ts`、設定は `e2e/playwright.showcase.config.ts` |
| 実行方法 | `e2e/` で `pnpm capture`。出力は `showcase/img/` に上書きする |
| 前提 | ローカルの環境（compose、バックエンド :8080 の profile `local`、店舗画面 :3000、管理画面 :5173）が起動している。サーバーは起動しない |
| データ | local の seed（`db/seed/local/`）。支払待ちの注文は、撮影の中で会員として注文し、mock の支払画面で「やめる」として作る |
| 画面の大きさ | 1280 × 800、明るいテーマ、日本語、日本時間 |
| ログイン | local の seed のアカウント（会員 `member@example.com`、運営者 `admin@example.com`） |

撮る画面：

| ファイル | 画面 |
|----------|------|
| `store-top.png` | 店舗画面のトップ |
| `store-product.png` | 商品の詳細（色・サイズの選択） |
| `store-order.png` | 注文の詳細（受付。税率ごとの内訳） |
| `store-payment.png` | 注文の詳細（支払待ち。支払の期限と「支払う」） |
| `admin-products.png` | 商品の一覧 |
| `admin-product.png` | 商品の詳細 |
| `admin-stock.png` | 在庫の履歴 |
| `admin-tax.png` | 税率の一覧 |
| `admin-orders.png` | 注文の一覧（支払の状態の列） |
| `admin-order.png` | 注文の詳細（支払の状態） |

- 画面で変えたデータは seed を再適用しても戻らない。撮る前にローカルの DB を再作成する手順を、撮影の手順に書く（`docs/dev/setup.md`）。
- 撮影は自動のテストではない（CI では流さない）。画面の見た目を確認するものではなく、撮った画像は人が見て確認する。
- 決済サービスの画面（Stripe）は撮らない（外部の画面で、sandbox の鍵が必要なため）。

## 3. ページの中身（DC-67、68、69、DN-9）

- 原稿を `showcase/` に移し、画像のパスを `img/` に合わせる。
- 決済：「開発中」の表記を外し、カードで支払えること（カード番号は決済サービスの画面で入れ、EC は預からない）を、`store-payment.png` と `admin-order.png` と一緒に示す。
- 開発の流れの表：「開発中」と「これから」を、README の spec の進捗と合わせる（出荷・配送は将来）。
- 個人の情報：リポジトリへのリンク（`https://github.com/roger88106/ec-site`）と、開発の期間（最初の commit の月から。「2026 年 10 月〜」）だけを載せる。

数字の数え方（DC-68）：

| 数字 | 数え方 |
|------|--------|
| 自動のテスト | CI で実行されるテストの数の合計。バックエンドは `./gradlew check` の JUnit の XML の `testcase` の数、フロントは Vitest の結果の件数（admin、store、shared）、E2E は Playwright の結果の件数 |
| 受け入れ条件 | 機能の spec の AC の数（重複を除く） |

- 数えた日と内訳を、tasks の結果に残す。ページには「約」を付けず、百の位で切り捨てて「N 件以上」と書く（テストが増えても嘘にならないため）。

## 4. 公開（DC-70）

| 項目 | 決め |
|------|------|
| 方法 | GitHub Pages。GitHub Actions で `showcase/` をそのまま公開する（ビルドしない） |
| ワークフロー | `.github/workflows/pages.yml`。`develop` への push で `showcase/**` が変わったときと、手動で起動したときに実行する |
| URL | `https://roger88106.github.io/ec-site/` |
| 利用者の手作業 | Settings → Pages の Source を「GitHub Actions」にする。Settings → Environments → `github-pages` の Deployment branches に `develop` を追加する |

- `ci.yml` の `paths-ignore` に `showcase/**` を追加する（ページだけの変更で CI を流さない）。

## 5. README とリポジトリの構成（DC-71）

- README の先頭（概要の下）に、ページへのリンクと `showcase/img/store-top.png` を置く。
- constitution のリポジトリの構成の図に `showcase/` を追加する。
- 撮影の手順を、[テストの実行方法](../../testing/running.md) ではなく [開発環境の起動](../../dev/setup.md) に追加する（テストではないため）。skill `local-dev` に 1 行で案内する。

## 6. 確認方法

| 条件 | 確認方法 |
|------|----------|
| DC-67 | ページの機能の節と画像を読み、画面と食い違いがない。「開発中」が README の進捗と合う |
| DC-68 | 第 3 節の数え方で数えた数と、ページの数字が合う |
| DC-69 | プレビューで、明暗の両テーマと幅 375 px で見て、横に崩れない |
| DC-70 | `develop` に push して、Pages の URL でページが開く |
| DC-71 | README の先頭のリンクと画像が、GitHub で表示される |
| DC-72 | ローカルの DB を再作成してから `pnpm capture` を実行し、10 枚が撮れる |
| DN-9 | ページに名前・連絡先・鍵がない（`grep` と目視） |
