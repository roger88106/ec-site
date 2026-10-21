# Plan 006：フロントの整理と i18n

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 004 が完了している（フロントの 3 つは pnpm の workspace、規則の文書は `docs/style/`、店舗画面のサーバーの処理に Node のテストがある）。DC-61 だけはバックエンドを変えるので、spec 005 の段階 2（構成の移動）の後に行う（同じファイルを同時に変更しないため）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 店舗画面のサーバーの処理 | 第 1 節 | DS-14 |
| 共有のパッケージ | 第 2 節 | DS-15 |
| 管理画面の繰り返し、業務の出し分け | 第 3 節 | DS-16 |
| i18n | 第 4 節 | DS-17 |
| コメント | 第 5 節 | DS-18 |

## 1. 店舗画面のサーバーの処理（DS-14）

### 1.1 環境変数の確認（DC-45）

- `src/auth/config.ts`（`auth.ts` から定数の読み込みを移す）で、`AUTH_SECRET`・`AUTH_KEYCLOAK_SECRET`・`AUTH_KEYCLOAK_ISSUER`・`AUTH_KEYCLOAK_ID`・`STORE_API_BASE_URL` を読む。
- **起動のとき**に確認する：Next.js の `instrumentation.ts` の `register()`（サーバーの起動時に 1 回だけ動く）で、本番（`NODE_ENV=production`）なのに値がなければ、足りない名前を列挙して例外にする。モジュールを読み込むときには確認しない（`next build` も本番のモードで動き、CI のビルドには secret がないため）。
- 既定の値（ローカル用）は、開発のモード（`next dev`）とテストでだけ使う。E2E（`next start`）と CI の E2E は、`playwright.config.ts` の `env` で値を渡す。
- `docs/frontend/store.md` 第 9 節、`docs/dev/environment.md` を修正する。

### 1.2 サーバーアクションの部品（DC-46）

- `src/lib/server/formData.ts`：`readString(formData, name)`、`readInt(formData, name)`（整数でなければ `null`）。単体テストを付ける。
- `src/actions/withActionResult.ts`：処理を受け取り、`unstable_rethrow`（リダイレクト・not found の例外を通す）、接続の失敗（`CONNECTION_FAILED`）、予期しない例外を結果の型に変える高階関数。今の 8 か所の try/catch を置き換える。
- テストは spec 004 の店舗画面のサーバーの処理のテストを使う（置き換えの前後で通る）。

### 1.3 認証の処理の分割（DC-47、P2）

`src/auth/`：`config.ts`（第 1.1 節）、`refresh.ts`（token の再取得）、`logout.ts`（IdP のログアウトの URL）、`index.ts`（Auth.js の設定と、今の `auth.ts` が公開している関数）。公開インターフェース（`@/auth` の import）は変えない。

### 1.4 サーバー側のログ（DC-62、P2）

- `src/lib/server/log.ts`：1 行 1 件の JSON（時刻、レベル、メッセージ、トレース ID、項目）。今の `console.error` 2 か所（カートの引き継ぎ）を置き換える。
- トレース ID：バックエンドへの要求に W3C の `traceparent` を付け、同じ ID をログに出力する（バックエンドのログと Zipkin で同じ要求を追跡できる）。道具を追加するか（OpenTelemetry の SDK など）は、段階 2 の最初のタスクで調べて決める。追加しない場合は、`traceparent` を自前で生成する小さな関数にする。
- 規則（形式、レベルの使い分け）は `docs/style/frontend.md` に書く。

## 2. 共有のパッケージ（DS-15）

### 2.1 置き場所と形（DC-48）

- `frontend/shared/`（パッケージ名 `@ec-site/shared`、workspace のパッケージ）。TypeScript のソースのまま使い、ビルドしない。store は `next.config` の `transpilePackages` に入れる。
- 中身：

| 置き場所 | 中身 | 今の場所 |
|----------|------|----------|
| `src/lib/` | カテゴリの木（`categoryTree`）、ログインの戻り先（`login`）、クラス名の結合（`cn`）、UUID の判定、数・日付・金額の整形（第 4.4 節のロケールを使う） | admin・store の `lib/` |
| `src/ui/` | shadcn/ui のコンポーネントのうち、2 つで同じもの（7 つ） | admin・store の `components/ui/` |
| `src/theme.css` | テーマ（CSS 変数、`@theme`） | admin の `index.css`、store の `globals.css` の同じ部分 |
| `src/messages/` | 共通の文言（第 4.2 節で決めたら） | — |

- `select.tsx` は 2 つで違うので共有しない（違いを見て、統一できれば共有する）。
- shadcn/ui のコンポーネントは、店舗画面がサーバーコンポーネントを使う（`rsc: true`）。`'use client'` の付け方が 2 つで同じかを確認してから移す。shadcn の CLI でコンポーネントを追加するときの置き場所（共有か各画面か）を `components.json` で決める。

### 2.2 共有の基準（DC-49）

`docs/style/frontend.md` に書く：

- 入れる：2 つ以上の画面で使い、画面のフレームワーク（Vite・Next.js）と API の接続に依存しないもの。
- 入れない：API の接続（接続先と認証が違う）、認証、画面の機能、サーバーアクション。
- 共有のものは、各画面の機能を import しない。

## 3. 管理画面の繰り返しと、業務の出し分け（DS-16）

### 3.1 更新の操作（DC-50）

- `src/api/useApiMutation.ts`：`$api.useMutation` をラップし、成功の通知（文言のキー）と、再取得する一覧（`ApiPathPrefix`）を引数で受ける。
- `src/api/useMutationGroup.ts`：複数の操作の `pending`・`error`・`resetErrors` をまとめる。
- 今の 26 か所を置き換える。通知の文言は第 4 節でキーになるので、i18n の前に置き換え、文言の引数の形を揃えておく。

### 3.2 画面の URL（DC-51）

- 管理画面：`src/app/paths.ts` に、URL を作る関数（例：`paths.product(id)`）をまとめる。`router.tsx` の定義、`menu.ts`、`<Link to>`、`navigate()` をこれで書く。
- 店舗画面：Next.js の型付きの URL（`typedRoutes`）を使えるか確認し、使えれば有効にする。使えなければ `src/lib/paths.ts` に同じ形でまとめる。
- 規則：画面のコードに URL の文字列を直接書かない（`docs/style/frontend.md`）。レビューで `git grep` で確認する。

### 3.3 入力の制約（DC-52）

- API の型の生成（`generate:api`）と一緒に、`admin.yaml`・`store.yaml` のリクエストの制約（`maxLength`・`minimum`・`maximum`）を定数ファイルに書き出す小さな script を `frontend/shared/scripts/` に置く。画面の入力の属性（`maxLength={…}` など）は、この定数だけを使う。
- 契約を変えると定数が変わり、画面の値も変わるので、食い違いが起きない。数字を直接書いた所がないことを、script（`scripts/ci/` の検査）で確認する。

### 3.4 バリアントの行（DC-53、P2）

`VariantRow` に分け、操作は props の関数で受ける。第 3.1 節の後に、まだ必要かを判断してから行う。

### 3.5 業務の出し分けを API のフラグに（DC-61）

- 最初のタスクで、画面が業務の規則で出し分けている所を洗い出す（今わかっているもの：商品の削除 `isNeverShown`（AC-71）、バリアントの追加 `canAdd`（AC-24））。注文のキャンセルの申請・承認・却下、在庫の調整なども探す。
- API の応答にフラグを追加する（例：商品の詳細に `deletable`、`canAddVariant`）。値はバックエンドの業務の規則（集約）から作り、画面はそれを見るだけにする。
- 順序：`admin.yaml`・`store.yaml` の変更 → バックエンド（フラグの値のテストを先に）→ 画面（判定の関数を削除する）。モジュールの設計書の API の欄も修正する。
- spec 005 の段階 2 の後に行う（前提）。

## 4. i18n（DS-17）

### 4.1 道具の選定（段階 6 の最初のタスク）

- 調べる候補：店舗画面は `next-intl`、管理画面は `use-intl`（`next-intl` の基盤のライブラリ。React だけで使える）か `react-i18next`。
- 基準：サーバーコンポーネントとクライアントコンポーネントの両方で使える（店舗画面）、キーの型の検査ができる（DC-55）、値の差し込みと数・日付の整形がある、2 つの画面で同じ形の文言のファイルを使える、ライセンスが方針に合う（spec 004）。
- 推奨の仮説：`next-intl`（店舗画面）と `use-intl`（管理画面）。同じ書き方と同じ文言の形（ICU）を 2 つの画面で使えるため。調べた結果を利用者に確認してから決める。

### 4.2 文言のファイルとキー（DC-54、DC-55）

- 置き場所：`frontend/<画面>/messages/ja.json`。共通の文言（「保存」「キャンセル」「読み込み中」など）を `frontend/shared/src/messages/ja.json` に置くかは、4.1 の道具で決める（spec の DC-54）。
- キーの形：`<機能>.<画面か部品>.<項目>`（例：`tax.list.title`）。共通は `common.<項目>`。英小文字と `camelCase`。
- 値の差し込み：`{code}を上へ` のように名前で差し込む。コードの中で組み立てている表示名（`画像 ${n}` など）も文言にする。
- 型：文言のファイルからキーの型を作り、存在しないキーは型チェックで失敗する（道具の型の拡張の仕組みを使う）。
- 例外（文言のファイルにしないもの）：テストの中の文字、開発者だけが見るログ、アイコンの代わりの記号。`docs/style/frontend.md` に書く。
- `admin/src/lib/labels.ts`（状態の表示名）は文言のファイルに移す。

### 4.3 テスト（DC-58）

- テストの描画の関数（admin の `src/test/renderApp.tsx`、store のテストの描画）で、日本語の文言で i18n の Provider を包む。テストは今の日本語の文字で要素を探したまま通す。
- E2E は変えない。

### 4.4 ロケール（DC-56）

- 既定のロケール（`ja-JP`）は `frontend/shared/src/locale.ts` の 1 か所。`Intl.*` を直接書いている 5 か所（`delta.ts`、`format.ts`、`rate.ts`、`orderDisplay.ts`、`price.ts`）は、道具の整形の機能か、ロケールを受け取る共有の関数に置き換える。
- ページの言語（admin の `index.html` の `lang`、store の `layout.tsx`）も、ロケールから設定する。

### 4.5 置き換えの段取り（DN-8）

仕組み（道具、Provider、型、ロケール）→ 共通のコンポーネント（レイアウト、`QueryState`、確認、ページ送り）→ 管理画面の機能ごと（税、カテゴリ、商品、在庫、注文）→ 店舗画面の機能ごと（一覧、詳細、カート、注文手続き、注文の照会、ヘッダー）。機能ごとに 1 commit。

## 5. コメント（DS-18）

- `docs/style/comments.md` に追加する（段階 1）：
  - 文言をキーに置き換えた所：何が表示されるかを短く（`{/* 税区分 */}`、`title={t('tax.list.title')} // 税区分`）。全文は転記しない。
  - 型の項目（TSDoc）：1 文目に論理名、続けて特別な値・単位・範囲・理由。名前と型で分かる項目には書かない。
  - 一目で意図が分からない処理（イベントの処理、`render`・`nativeButton` などのコンポーネントの差し替え、`unstable_rethrow` などのフレームワークの決まり）と、複雑な処理（spec 005 と同じ基準）の前に、何のためかを 1 行。
- spec・plan・tasks の番号を、規則の言葉に書き換える（DC-66。`docs/style/comments.md` 第 1 節）。今は約 200 か所（AC 約 150、BR・US・NFR 約 25、タスク番号 約 14、plan の節 約 7）。段階 8 で、コメントだけの commit にする。
- 文言のコメントは、第 4.5 節の置き換えと同じ commit で書く（置き換えた人が意味を知っているため）。型と処理のコメントは段階 8 でまとめて、コメントだけの commit にする。

## 6. 確認方法

| DC | 確認方法 |
|----|----------|
| 45 | `instrumentation` のテスト（値がないと例外）。値のない本番の起動で失敗することを手元で一度確認する。E2E が CI で通る |
| 46、47 | 店舗画面のサーバーの処理のテスト（spec 004）が通る。`unstable_rethrow` を書いている所が部品の中だけ（`git grep`） |
| 48、49 | admin・store に同じ内容のファイルがない（レビューで比べる）。規則の文書がある |
| 50 | `onSuccess` で知らせと再取得を直接書いている所がない（レビューで `git grep`） |
| 51 | URL の文字列を直接書いた所がない（`git grep`） |
| 52 | 第 3.3 節の検査が CI で通る |
| 53 | レビューで読む |
| 54、55 | 型チェックが通る。表示の文言を直接書いた所の検査（日本語の文字を含む JSX の文字・属性を探す script。例外の一覧を除く） |
| 56 | `Intl` のロケールを直接書いた所が 1 か所（`git grep`） |
| 57、59、60 | レビューで読む |
| 66 | spec 005 の DC-65 と同じ `git grep` を admin・store の `src`（テストを除く）で見る |
| 58 | 既存のテストが、探し方を変えずに通る |
| 61 | フラグの値のバックエンドのテストと、画面の結合テストが通る。画面に判定の関数がない |
| 62 | ログの部品の単体テスト。手元でバックエンドのログとトレース ID が揃うことを確認する |
| DN-7 | 全てのテストと E2E が通る。画面を目視で確認して、見た目と文言が変わっていないこと |
| DN-8 | `git log` |

## 7. 実装の順序

tasks は段階ごとに `tasks/` の 1 ファイルにする（[01](tasks/01-docs.md)、[02](tasks/02-store-server.md)、[03](tasks/03-shared.md)、[04](tasks/04-admin.md)、[05](tasks/05-api-flags.md)、[06](tasks/06-i18n-base.md)、[07](tasks/07-i18n-messages.md)、[08](tasks/08-comments.md)、[09](tasks/09-review.md)）。タスク番号は `T6` ＋ 段階 ＋ 連番（例：T6101）。

| # | 段階 | 完了条件 |
|---|------|----------|
| 1 | 規則の文書（`docs/style/frontend.md`：共有の基準、i18n、URL、環境変数、ログ。`comments.md` の第 5 節） | 文書がある |
| 2 | 店舗画面のサーバーの処理（第 1 節） | DC-45〜47、62 |
| 3 | 共有のパッケージ（第 2 節） | DC-48、49。admin・store のテストとビルドが通る |
| 4 | 管理画面の繰り返し（第 3.1〜3.4 節） | DC-50〜53 |
| 5 | 業務の出し分けを API のフラグに（第 3.5 節。spec 005 の段階 2 の後） | DC-61 |
| 6 | i18n の仕組み（第 4.1、4.3、4.4 節、道具の選定を利用者に確認する） | DC-55、56、58 |
| 7 | 文言の置き換え（第 4.2、4.5 節） | DC-54、57 |
| 8 | コメント（第 5 節） | DC-59、60、66 |
| 9 | レビュー | `docs/style/review.md` で確認し、`check-zh.mjs --all` が通り、画面を目視で確認して変わっていない。README の spec の状態を修正する |
