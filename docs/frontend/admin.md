# 管理画面（`frontend/admin`）の構成

B 側（運営者）の画面。Vite + React の SPA で、管理 API（`admin.yaml`）をブラウザから呼ぶ。
画面が増えても散らからないよう、置き場所と作り方の決まりを本書にまとめる。新しい画面は、第 9 節の手順どおりに作る。

- 技術の採用：[constitution](../constitution.md) 第 2 節
- 画面の一覧と spec ごとの決定：各 spec の plan（例：[plan 001](../specs/001-catalog-and-inventory/plan.md) 第 3 節）

## 1. 技術

| 用途 | 採用 | 補足 |
|------|------|------|
| ビルド・開発サーバー | Vite | 開発時は `/api` をバックエンド（`localhost:8080`）に転送する |
| 画面の切り替え | React Router | URL に ID を持つ画面（商品の編集など）を、再読み込みやリンクで開けるようにする |
| API の型 | openapi-typescript | `admin.yaml` から生成する。生成物はコミットしない（constitution 9） |
| API の呼び出し | openapi-fetch + openapi-react-query | 取得・更新を、パスと型から呼ぶ。キャッシュのキーも自動で決まる |
| サーバー状態 | TanStack Query | 既定の設定。ただし 4xx は繰り返さない（第 4 節） |
| フォーム | React Hook Form | 入力の状態、変更の有無、サーバーの項目ごとのエラーの表示に使う |
| UI | shadcn/ui、Tailwind CSS、lucide-react | レイアウトは shadcn の Sidebar、成功の通知は sonner（toast） |
| lint・整形 | Biome | lint、整形、import の整理、Tailwind のクラスの並べ替え |
| ログイン | oidc-client-ts + react-oidc-context | 認可コード + PKCE（public client）。token はメモリーにだけ持つ（第 11 節、plan 003 第 1、4 節） |
| テスト | Vitest、React Testing Library、user-event、MSW（openapi-msw）、jsdom | 単体テストは画面に依存しない関数と表示の部品、結合テストは画面の操作の流れ（第 10 節、constitution 12） |

## 2. ディレクトリ

```
frontend/admin/src/
├── app/              # 入口、ルーター、Provider、レイアウト（Sidebar）
├── auth/             # ログイン（IdP の設定、ログインを求める枠、ログインしている運営者、API に付ける token）。第 11 節
├── api/
│   ├── generated/    # admin.yaml から生成（コミットしない）
│   ├── client.ts     # openapi-fetch と openapi-react-query のクライアント
│   ├── errors.ts     # エラーの変換（ApiError）
│   └── types.ts      # 画面から使うスキーマの型の別名
├── components/
│   ├── ui/           # shadcn/ui が生成する部品。手で直さない
│   └── *.tsx         # 機能をまたいで使う部品（価格、日時、エラー、ページングなど）
├── hooks/            # 機能をまたいで使う hook（バージョン付きのフォームなど。shadcn/ui の use-mobile もここに生成される）
├── lib/              # 画面に依存しない関数（表示の整形、品番の正規化など）。単体テストを横に置く
├── test/             # 結合テストの土台（描画の補助、API の応答の差し替え、jsdom の補い）。画面のコードからは import しない
└── features/
    └── <機能>/       # tax、category、product、inventory、order
        ├── pages/        # ルートに対応する画面。データの取得と部品の組み立てだけを書く
        ├── components/   # その機能の中だけで使う部品
        └── *.ts          # その機能の中だけで使う関数・型・hook
```

- 機能（`features/<機能>`）どうしは import しない。2 つ以上の機能で使うものは、`components/` か `lib/` に置く（例：カテゴリの選択は、商品の一覧と編集で使うので `components/`）。
- `pages/` は薄く保つ。フォームや表は `components/` に分ける。
- `components/ui/` は shadcn/ui の生成物のまま使い、lint の一部の規則を外す。直したいときは、外側の部品で包む。
- ファイル名：部品は `PascalCase.tsx`、それ以外は `camelCase.ts`。テストは `*.test.ts(x)` を対象の横に置く。

## 3. 画面の URL

| URL | 画面 | 機能 |
|-----|------|------|
| `/products` | 商品の一覧（既定の画面） | product |
| `/products/new` | 商品の登録 | product |
| `/products/:productId` | 商品の編集（状態、公開期間、基本情報、画像、バリアントと販売停止・画像） | product |
| `/products/:productId/variants/:variantId/stock` | バリアントの在庫（在庫数、調整、履歴） | inventory |
| `/categories` | カテゴリ | category |
| `/tax-categories` | 税区分の一覧と登録 | tax |
| `/tax-categories/:taxCategoryId` | 税率の一覧の編集 | tax |
| `/orders` | 注文一覧（クエリ：`orderNumber`、`status`、`orderedFrom`、`orderedTo`、`page`） | order |
| `/orders/:orderId` | 注文の詳細（明細、金額の内訳、お届け先、キャンセルの申請とキャンセル。承認・却下・キャンセルの操作） | order |

- 一覧の絞り込みとページは、URL のクエリに持つ。戻る・再読み込みで同じ一覧を出すため。
- 左のメニューに出すのは、各機能の入口（一覧）だけ。

## 4. API の呼び出し

- 取得は `$api.useQuery('get', '<パス>', { params })`、更新は `$api.useMutation('<メソッド>', '<パス>')` で書く。キャッシュのキーとクエリの関数を、画面ごとに手で書かない。
- エラーの応答は、クライアントの middleware で `ApiError`（状態コードとエラーレスポンス）に変換して投げる。画面は `ApiError` だけを扱う。
- 運営者の access token は、クライアントの middleware で `Authorization` ヘッダーに付ける（第 11 節）。
- 401 は、ログインし直す（IdP へ移り、戻ったら同じ画面）。403 は、操作した場所に API のメッセージ（「この操作を行う権限がありません。」。AC-141）を出す。ほかのエラーと同じく第 6 節のとおりに出す。
- 失敗した取得の繰り返しは、5xx と通信の失敗だけ（TanStack Query の既定の 3 回）。4xx は繰り返しても結果が変わらず、表示が遅れるだけなので繰り返さない。
- 更新に成功したら、影響する取得をパスの前方一致で取り直す（`invalidate` の補助関数を使う）。

| 更新したもの | 取り直すパス |
|--------------|--------------|
| 税区分・税率 | `/tax-categories` |
| カテゴリ | `/categories` |
| 商品・バリアント | `/products`、`/inventory`（バリアントの増減で在庫も変わる） |
| 在庫 | `/inventory` |
| 注文（承認・却下・キャンセル） | `/orders`、`/inventory`（キャンセルで在庫数と調整履歴が変わる） |

- 削除した対象の詳細は、先に別の画面へ移ってから取り直す（削除した詳細を取り直すと 404 になるため）。
- 画像のアップロードは、受付 → ストレージへの直接の PUT（署名付き URL。受付の応答のヘッダーを付ける）→ 登録の順に送る（plan 001 第 7.2 節）。
  - 送る前に、形式・大きさ・枚数を画面でも確かめ、誤りがあれば 1 枚も送らない。サーバーも同じ検証をする。
  - ブラウザがファイルの形式を空で渡すことがある（Windows の HEIC など）。そのときは拡張子で形式を補って受付に申告する。ファイルの選択にも拡張子を並べる。
  - 複数のファイルは 1 枚ずつ登録する。登録ごとに商品のバージョンが 1 つ上がるので、2 枚目からは前の登録の後のバージョンで送る。
  - ストレージへの PUT など、管理 API の外での失敗は、画面で決めたメッセージを出す（`ApiError` ではない）。変換の失敗（AC-70）は、登録の API のメッセージをそのまま出す。

## 5. フォーム

- React Hook Form で入力を持つ。画面での検証は、必須の未入力など送る前にわかるものだけにする。形式や業務ルール（品番の形式、期間の重複など）はサーバーが検証し、画面では同じ検証を書かない。
- サーバーの入力検証エラー（`errors[]`）は、対応する入力欄の下に出す。項目に対応しないエラーは、フォームの下に `message` をそのまま出す。
- 品番の入力は、半角の英小文字を大文字にしてから送る。
- **同時に編集されうるリソースのバージョン**（constitution 9）：送るバージョンは、フォームを作ったときのリソースのもの。
  - 入力を変える前にリソースが変わったら（自分のバリアントの操作など）、フォームを最新の値で作り直す。
  - 入力を変えた後に他の人が更新していれば、上書きせずに 409 になる。最新のバージョンを送ると、画面に出ていない他の人の変更を古い入力で黙って上書きしてしまうため。
  - 409（`CONCURRENT_MODIFICATION`）では「他の人が先に更新しました。再読み込みしてください」と、取得し直してフォームを作り直すボタンを出す。
  - この決まりは `hooks/useVersionedForm` にまとめてあり、バージョンを持つリソースのフォームはこれを使う。

## 6. 結果の知らせ方

| 場面 | 出し方 |
|------|--------|
| 更新の成功 | toast（例：「保存しました」） |
| 更新の失敗 | 操作した場所（フォームやカードの中）に出す。toast は消えるので、直す手がかりが要る失敗には使わない |
| 取得の失敗 | その取得を使う場所に出す |
| 取り消せない操作（削除、販売終了） | 確認のダイアログ（shadcn の AlertDialog）を出してから送る |

## 7. 表示の決まり

| 対象 | 決まり |
|------|--------|
| 価格 | 「1,980 円（税抜 1,800 円）」（BR-4）。税込価格がなければ税抜価格だけを出す |
| 日時 | 表示も入力も日本時間（BR-3）。ブラウザのタイムゾーンに依存させない。入力欄の値は日本時間とみなし、+09:00 のオフセット付きで送る |
| 列挙（状態、理由など） | 表示名の対応表を `lib/` に置き、画面で直接文字列を書かない。選択肢は生成した列挙の値の配列から作る |
| 操作できないもの | ボタンを出さないか、理由を添える（例：C 側に表示されたことのある商品は削除のボタンを出さず、販売終了を案内する） |

## 8. 品質

- CI で、lint（Biome）、型チェック、テスト、ビルドを実行する（constitution 13）。型チェックとビルドは、型の生成の後に行う。
- 単体テストの対象は `lib/` の関数と、価格・日時などの表示の部品。
- 主な操作の流れは結合テスト（第 10 節）で確かめる。見た目（配置、色、狭い画面）は、段階の最後に手で確かめる（constitution 12）。

## 9. 画面を追加する手順

1. 使う API が `admin.yaml` にあることを確かめる。なければ、先に plan と `admin.yaml` を直す（constitution 15）。
2. `features/<機能>/pages/` に画面を作り、`app/` のルーターに URL を足す。機能の入口なら、Sidebar のメニューにも足す。
3. 取得と更新は第 4 節、フォームは第 5 節、結果の知らせ方は第 6 節に従う。更新の後に取り直すパスを、第 4 節の表に足す。
4. 新しい表示の決まり（列挙の表示名など）は `lib/` に置き、単体テストを書く。
5. 主な操作の流れに結合テストを書く（第 10 節）。
6. lint、型チェック、テスト、ビルドを通し、開発サーバーで見た目を確かめる。

## 10. 結合テストの書き方

画面を本物のルーター・サーバー状態のキャッシュ・toast で描画し、API の応答だけを MSW で差し替えて、利用者と同じように操作する（constitution 12）。

```tsx
// features/<機能>/pages/<画面>.test.tsx
server.use(
  http.get('/tax-categories', ({ response }) => response(200).json({ items: [...] })),
  http.put('/tax-categories/{taxCategoryId}/rates', ({ response }) =>
    response(409).json(problem(409, 'CONCURRENT_MODIFICATION', 'バージョンが一致しません。')),
  ),
)
const { user } = renderApp('/tax-categories/<ID>')
await user.click(await screen.findByRole('button', { name: '保存' }))
expect(await screen.findByText('他の人が先に更新しました。再読み込みしてください')).toBeInTheDocument()
```

| 補助（`src/test/`） | 内容 |
|------|------|
| `renderApp(url)` | その URL を開いた状態で画面全体を描画する。操作に使う `user`（user-event）と、URL を確かめる `router` を返す |
| `http` | `admin.yaml` の型に合わせた MSW のハンドラーを作る（openapi-msw）。パス・本文・状態が `admin.yaml` にないと型エラーになる |
| `server` | API の代わりに応答する。既定のハンドラーは持たず、テストごとに `server.use(...)` で、そのテストで呼ばれる API の応答を渡す |
| `problem(...)` | エラーレスポンスの本文を作る |
| `fixtures.ts` | API の応答に使うデータ（税区分、商品の詳細、バリアント）。テストに関係のない項目にありふれた値を入れ、確かめる項目だけを引数で与える |

- 置き場所は、主に確かめる画面（`pages/`）の横。テスト名は、画面に起きることを書き、AC があれば AC 番号を入れる（例：`AC-43 調整後に負になる調整はエラーを出す`）。
- 要素は、画面に見える役割と名前で探す（`getByRole('button', { name: '保存' })`、`getByLabelText('名称')`）。CSS のクラスや内部の状態は見ない。
  - ボタンの見た目のリンク（`Button` の `render={<Link />}`）は、役割が `button` になる。
- ハンドラーのない API が呼ばれると、テストは失敗する。送った内容を確かめるときは、ハンドラーの中で `request.json()` を変数に取っておく。
- バージョンの決まり（第 5 節）を確かめるテストでは、入力中に他の人が更新した後で、画面に取得し直させる（`fireEvent(window, new Event('visibilitychange'))`：別のタブから戻った）。取得し直しても入力が消えず、送るバージョンがフォームを作ったときのままであることまで確かめる。
- 管理 API の外への要求（画像のファイルのストレージへの PUT）は、`msw` の `http` でハンドラーを作る（`admin.yaml` の型がないため）。
- 画面の外で起きること（バックエンドの業務ルール、店舗画面への反映）は確かめない。前者はバックエンドのテスト、後者は E2E で確かめる。

## 11. ログイン

plan 003 第 1、4、6 節の決定を、管理画面でどう作るか。SPA がブラウザーで IdP とやり取りし（public client）、token はメモリーにだけ持つ。

- **設定**（`auth/oidc.ts`）：`ec-admin` realm の `ec-admin-web`。認可コード + PKCE。接続先は環境変数 `VITE_OIDC_AUTHORITY`（既定 `http://localhost:8180/realms/ec-admin`）と `VITE_OIDC_CLIENT_ID`（既定 `ec-admin-web`）。IdP からの戻り先と、ログアウトの後の戻り先は、画面のオリジンのトップ。
- **token をメモリーにだけ持つ**：oidc-client-ts の利用者の保存先を、メモリー（`InMemoryWebStorage`）にする（既定の sessionStorage には置かない。JavaScript から読める場所に残さないため）。IdP へ移る間の `state` と PKCE の値だけは、戻ってきたときに要るので sessionStorage に置く（token ではない）。
- **ログインを求める**（AC-132）：`auth/AuthGate` がすべての画面を包み、ログインしていなければ、何も表示せずに IdP のログインへ移る。戻ってきたら、移る前の画面（パスとクエリ）に戻す。
  - 再読み込み・新しいタブでは、メモリーに token がないので IdP へ移るが、IdP のログインの状態（SSO セッション）があれば、パスワードを求めずにすぐ戻る（plan 003 第 1 節）。画面の中に隠した iframe は使わない（IdP のログインの状態を iframe で確かめる仕組みも切る）。
- **token の更新**：access token の期限が近ければ、refresh token で取り直す（oidc-client-ts の自動更新）。取り直せなければ（IdP のログインの状態が切れた。最後の操作から 30 分、最長 8 時間）、ログインし直す。
- **ログインしている運営者**：`auth/operator` の `useOperator()` で、氏名（姓 名）とログアウトの操作を読む。画面の部品は react-oidc-context を直接使わない（結合テストでログインを置き換えるため）。
- **API に付ける token**：`AuthGate` が、いまの access token を読む関数と、401 のときにログインし直す関数を `auth/token.ts` に渡し、API のクライアントの middleware がそれを使う。
- **ログアウト**（AC-135）：IdP のログアウト（RP-Initiated Logout。ID token と、画面のトップを戻り先に添える）へ移る。メモリーの token は、画面を離れるので消える。戻ってきたらログインを求める（パスワードが要る）。
- **結合テスト**：`renderApp` は `AuthGate` を通さず、ログインしている運営者（氏名とログアウトの操作）を差し替えて描画する。token は付けないので、MSW のハンドラーは `Authorization` を見ない。401 で IdP へ移る処理は、テストでは何もしない。
