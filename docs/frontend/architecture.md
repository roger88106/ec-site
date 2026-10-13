# フロントのアーキテクチャ

[constitution](../constitution.md) 第 2 節の細則。書き方は [フロントの書き方](coding.md)、アプリごとの構成は [管理画面](apps/admin.md) と [店舗画面](apps/store.md)。

## 1. 全体の構成

C 側（店舗画面）と B 側（管理画面）は別のアプリにし、2 つで同じ処理と表示のコンポーネントは共有パッケージに置く。

```
店舗画面：ブラウザ ──> Next.js のサーバー（サーバーコンポーネント・サーバーアクション）──> 店舗 API（/api/store）
管理画面：ブラウザ（React の SPA）────────────────────────────────────> 管理 API（/api/admin）

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
| API を呼ぶ所 | サーバーだけ（ブラウザから API を呼ばない） | ブラウザ（開発時は Vite が `/api` を転送） |
| 取得 | サーバーコンポーネント。Next.js の fetch のキャッシュ（最大 60 秒） | TanStack Query + openapi-react-query |
| 更新 | サーバーアクション。結果は `ActionResult` | `useApiMutation`。結果は toast と操作した場所 |
| フォーム | `useActionState` | React Hook Form |
| ログイン | Auth.js。サーバーが IdP とやり取りする（confidential client） | oidc-client-ts。ブラウザが IdP とやり取りする（public client、PKCE） |
| token の置き場所 | 暗号化した HttpOnly の Cookie（サーバーだけが読む） | ブラウザのメモリーだけ |
| 文言 | next-intl | use-intl |
| コンポーネントのテスト | HTML に描画して確かめる（結合テストなし ※1） | React Testing Library と MSW の結合テスト |

※1：操作がサーバーアクションを通り、ブラウザの中だけでは再現できないため。画面をつないだ流れは E2E で確かめる。

## 3. 共通の決まり

| 項目 | 決まり |
|------|--------|
| ディレクトリ | `features/<機能>/`（その機能だけの画面・コンポーネント・処理）、`components/`（機能をまたぐコンポーネント）、`components/ui/`（shadcn/ui の生成物）、`lib/`（画面に依存しない関数）、`i18n/`（文言）、`test/`（テストの土台） |
| 機能の間 | 機能どうしは import しない。2 つ以上の機能で使うものは `components/` か `lib/` |
| API | `api/openapi/` から生成した型で呼ぶ。生成物はコミットしない |
| 文言 | 各アプリの `messages/ja.json` と、共有の `common.*`（`frontend/shared/src/messages/ja.json`） |
| ロケール | `frontend/shared/src/lib/locale.ts` の 1 か所 |
| 表示 | 価格は「1,980 円（税抜 1,800 円）」、日時は日本時間（ブラウザのタイムゾーンに依存しない） |
| 操作できるか | 画面で業務の規則から判断せず、API の応答の `actions` の旗で決める |
| 確かめ方 | lint（Biome）、型チェック、テスト、ビルドを CI で流す。見た目は手で確かめる |

## 4. 共有パッケージ

`frontend/shared`（`@ec-site/shared`）には、2 つのアプリで使い、枠組み（Vite・Next.js）と API の接続に依存しないものだけを置く：カテゴリの木、ログインの戻り先の検証、クラス名の結合、数・日付・金額の整形、同じ表示のコンポーネントとテーマ、共通の文言。入れるもの・入れないものの基準は [フロントの書き方](coding.md) 第 5 節。
