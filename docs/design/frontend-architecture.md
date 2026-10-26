# フロントのアーキテクチャ

[constitution](../constitution.md) 第 2 節の細則。書き方は [フロントの書き方](../rules/frontend-coding.md)、アプリごとの構成は [管理画面](apps/admin.md) と [店舗画面](apps/store.md)。

## 1. 全体の構成

C 側（店舗画面）と B 側（管理画面）は別のアプリにし、2 つで同じ処理と表示のコンポーネントは共有パッケージに置く。

```
店舗画面：ブラウザー ──> Next.js のサーバー（サーバーコンポーネント・サーバーアクション）──> 店舗 API（/api/store）
管理画面：ブラウザー（React の SPA）────────────────────────────────────> 管理 API（/api/admin）

frontend/
├── store/    # 店舗画面
├── admin/    # 管理画面
└── shared/   # 共有パッケージ（@ec-site/shared）
```

## 2. 2 つのアプリの違い

| 項目 | 店舗画面（`frontend/store`） | 管理画面（`frontend/admin`） |
|------|------------------------------|------------------------------|
| 利用者 | 消費者 | 運営者 |
| フレームワーク | Next.js（App Router） | Vite + React（SPA）、React Router |
| API を呼ぶ場所 | サーバーだけ（ブラウザーから API を呼ばない） | ブラウザー（開発時は Vite が `/api` を転送） |
| 取得 | サーバーコンポーネント。Next.js の fetch キャッシュ（最大 60 秒） | TanStack Query + openapi-react-query |
| 更新 | サーバーアクション。結果は `ActionResult` | `useApiMutation`。結果は toast と操作した場所 |
| フォーム | `useActionState` | React Hook Form |
| ログイン | Auth.js。サーバーが IdP とやり取りする（confidential client） | oidc-client-ts。ブラウザーが IdP とやり取りする（public client、PKCE） |
| token の置き場所 | 暗号化した HttpOnly の Cookie（サーバーだけが読む） | ブラウザーのメモリーだけ |
| 文言 | next-intl | use-intl |
| コンポーネントのテスト | HTML にして DOM に読み込み、役割と名前で探して確認する。ブラウザーの中だけで終わる操作と、サーバーアクションの結果の表示の遷移は、jsdom と Testing Library で操作する（結合テストなし ※1） | React Testing Library と MSW の結合テスト |

※1：操作がサーバーアクションを通り、ブラウザーの中だけでは再現できないため。結果の表示の遷移を確認するときは、サーバーアクションを差し替える（[テストの書き方](../rules/test-writing.md) 第 1 節）。画面をつないだ流れは E2E で確認する。

## 3. 共通の決まり

| 項目 | 決まり |
|------|--------|
| ディレクトリ | `features/<機能>/`（その機能だけの画面・コンポーネント・処理）、`components/`（機能をまたぐコンポーネント）、`components/ui/`（shadcn/ui の生成物）、`lib/`（画面に依存しない関数）、`i18n/`（文言）、`test/`（テストの基盤） |
| 機能の間 | 機能どうしは import しない。2 つ以上の機能で使うものは `components/` か `lib/` |
| API | `api/openapi/` から生成した型で呼ぶ。生成物はコミットしない |
| 文言 | 各アプリの `messages/ja.json` と、共有の `common.*`（`frontend/shared/src/messages/ja.json`） |
| ロケール | `frontend/shared/src/lib/locale.ts` の 1 か所 |
| 表示 | 価格は「1,980 円（税抜 1,800 円）」、日時は日本時間（ブラウザーのタイムゾーンに依存しない） |
| 操作できるか | 画面で業務の規則（注文・商品の状態、階層の深さなど）から判断せず、API の応答の `actions` のフラグで決める。操作の中の選択（キャンセルで在庫に戻すか）と、できない理由（商品を削除できない理由）も API の値で表示する。状態と比べるのは、表示（ラベル、状態ごとの説明）だけ ※2 |
| 確認方法 | lint（Biome）、型チェック、テスト、ビルドを CI で実行する。見た目は目視で確認する |
| TypeScript | 型チェックとビルドは TypeScript 7。API の型の生成（openapi-typescript）だけが TypeScript 6 を使う ※3 |

※2：管理画面のフラグと理由の一覧は [管理画面の構成](apps/admin.md) 第 7 節。状態とフラグが食い違うテストデータ（フラグが偽の出荷済みの注文など）を使う結合テストで、画面が状態から推測しないことを確認する。

※3：API の型の生成だけが TypeScript 6 を使うのは、TypeScript 7 に JavaScript のコンパイラーの API がなく、openapi-typescript がその API で型を組み立てるためである。ルートの `.pnpmfile.cjs` が、インストールのときに openapi-typescript にだけ TypeScript 6 を渡す。pnpm の overrides は peer の依存に適用されないので、フックを使う。lock がフックのチェックサムを持つので、フックを変えたら lock も更新する。openapi-typescript の peer の依存の範囲が変わると、フックが install を失敗させる。openapi-typescript が TypeScript 7 で動くようになったら、`.pnpmfile.cjs` を削除する。

## 4. 共有パッケージ

`frontend/shared`（`@ec-site/shared`）には、2 つのアプリで使い、フレームワーク（Vite・Next.js）と API の接続に依存しないものだけを置く。今置いているのは、カテゴリの木、ログインの戻り先の検証、数・日付・金額の整形、注文の表示名、「すべて」を先頭に付けた選択肢、同じ表示のコンポーネント（金額の内訳、リンクの見た目のボタンなど）とテーマ、共通の文言、形の判定（UUID など）、shadcn/ui の共有のコンポーネント、2 つの画面の API の契約から入力の制約の定数を生成する script。入れるもの・入れないものの基準は [フロントの書き方](../rules/frontend-coding.md) 第 5 節。
