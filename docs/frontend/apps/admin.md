# 管理画面（`frontend/admin`）の構成

B 側（運営者）の画面。Vite + React の SPA で、管理 API（`admin.yaml`）をブラウザから呼ぶ。店舗画面との違いと共通の決まりは [フロントのアーキテクチャ](../architecture.md)。新しい画面は第 8 節の手順で作る。

## 1. 技術

| 用途 | 採用 | 補足 |
|------|------|------|
| ビルド・開発サーバー | Vite | 開発時は `/api` をバックエンド（既定 `localhost:8080`、`ADMIN_API_PROXY_TARGET`）に転送する |
| 画面の切り替え | React Router | ID を持つ画面（商品の編集など）を、再読み込みやリンクで開けるようにする |
| API | openapi-fetch + openapi-react-query | パスと型から呼び、キャッシュのキーも自動で決まる |
| サーバー状態 | TanStack Query | 既定の設定。ただし 4xx は繰り返さない |
| 文言 | use-intl | — |
| フォーム | React Hook Form | 入力の状態、変更の有無、項目ごとのエラー |
| UI | shadcn/ui、Tailwind CSS、lucide-react | レイアウトは Sidebar、成功の通知は sonner（toast） |
| グラフ | Recharts | shadcn/ui の chart（`components/ui/chart.tsx`）で包んで使う |
| ログイン | oidc-client-ts + react-oidc-context | 認可コード + PKCE（public client）。token はメモリーだけ |
| テスト | Vitest、React Testing Library、user-event、MSW（openapi-msw）、jsdom | — |

## 2. ディレクトリ

```
frontend/admin/
├── messages/ja.json        # 文言
└── src/
    ├── app/                # 入口、ルーター、Provider、レイアウト（Sidebar）、URL（paths.ts）
    ├── auth/               # ログイン（第 10 節）
    ├── api/
    │   ├── generated/      # admin.yaml から生成（型と、入力の制約の定数）
    │   ├── client.ts       # クライアント
    │   ├── errors.ts       # エラーの変換（ApiError）
    │   └── types.ts        # 画面から使う型の別名
    ├── components/         # 機能をまたぐコンポーネント（価格、日時、エラー、ページングなど）。ui/ は shadcn/ui
    ├── i18n/               # 文言の Provider、合わせ方、キーの型、コンポーネントの外で引く translate
    ├── hooks/              # 機能をまたぐ hook（バージョン付きのフォームなど）
    ├── lib/                # 画面に依存しない関数（単体テストを横に置く）
    │   ├── format.ts       # 日時の入力欄との変換と、日本時間の日付（金額・価格・日時などの整形は共有パッケージの lib/format.ts）
    │   └── searchParams.ts # URL のクエリのページ番号の読み取り
    ├── test/               # 結合テストの土台（画面のコードからは import しない）
    └── features/<機能>/    # tax、category、product、inventory、order、report
        ├── pages/          # ルートに対応する画面（取得とコンポーネントの組み立てだけ）
        ├── components/     # その機能だけのコンポーネント
        ├── labels.ts       # その機能だけで使う列挙の表示名と Badge の見た目（2 つ以上の機能で使うなら lib/labels.ts）
        └── *.ts            # その機能だけの関数・型・hook
```

- `pages/` は薄く保ち、フォームや表は `components/` に分ける。
- `components/ui/` は生成物のまま使う。直したいときは外側のコンポーネントで包む。
- ファイル名は、コンポーネントが `PascalCase.tsx`、それ以外が `camelCase.ts`。テストは `*.test.ts(x)` を対象の横に置く。

## 3. 画面の URL

URL は `src/app/paths.ts` で作る（ルーターは `routePatterns`、リンク・`navigate()`・メニューは `paths` の関数）。

| URL | 画面 |
|-----|------|
| `/products` | 商品一覧（既定の画面） |
| `/products/new` | 商品の登録 |
| `/products/:productId` | 商品の編集（状態、公開期間、基本情報、画像、バリアント） |
| `/products/:productId/variants/:variantId/stock` | バリアントの在庫（在庫数、調整、履歴） |
| `/categories` | カテゴリ |
| `/tax-categories` | 税区分の一覧と登録 |
| `/tax-categories/:taxCategoryId` | 税率の一覧の編集 |
| `/orders` | 注文一覧（クエリ：`orderNumber`、`status`、`orderedFrom`、`orderedTo`、`authorizationExpiring`、`returned`、`page`） |
| `/orders/:orderId` | 注文の詳細（支払の状態と期限、出荷の登録と配送状態、明細、金額の内訳、お届け先、キャンセルの承認・却下・キャンセル） |
| `/reports/sales-trend` | 売上の推移（クエリ：`from`、`to`、`granularity`、`basis`、`productId`、`excludeTax`、`excludeShipping`） |
| `/reports/variant-sales` | バリアントの売れ行き（クエリ：`from`、`to`、`basis`、`productId`、`excludeTax`） |
| `/reports/customer-breakdown` | 地域・年齢層・性別（クエリ：`from`、`to`、`basis`、`productId`、`excludeTax`、`excludeShipping`） |

- 一覧の絞り込みとページは URL のクエリに持つ（戻る・再読み込みで同じ一覧を出すため）。
- 左のメニューには、各機能の入口（一覧）だけを出す。レポートは「レポート」の下に 3 つの画面を並べる。
- レポートの条件も URL のクエリに持つ（再読み込みとリンクの共有のため）。期間がなければ、売上の推移は今年度、他は今月にする。期間の選択肢（今年度、今月など）は、レポートの設定の API の会計年度の始まりの月で求める。
- 条件の商品の選択肢は、商品の一覧の API の 1 ページ（100 件）までを出す。商品が増えて足りなくなったら、検索して選ぶ形に変える。
- 表示しているレポートの表は、CSV（UTF-8 の BOM 付き、CRLF）で取り出せる。= + - @ で始まる文字の項目は、表計算ソフトが式として読まないよう前に `'` を付ける。

## 4. API の呼び出し

- 取得は `$api.useQuery('get', '<パス>', { params })`、更新は `useApiMutation('<メソッド>', '<パス>', { success, invalidate, onSuccess })`。キャッシュのキーとクエリの関数を手で書かない。
- 1 つの場所に複数の更新があるときは、`useMutationGroup(...)` で待ち状態・最初のエラー・エラーの消去をまとめる。
- エラーの応答はクライアントの middleware で `ApiError` にして投げ、画面は `ApiError` だけを扱う。
- access token はクライアントの middleware で `Authorization` に付ける（第 10 節）。
- バックエンドへの呼び出しに、タイムアウトを置かない。ブラウザーの fetch で呼ぶので、画面は止まらず、利用者が操作できるため。

| 応答 | 扱い |
|------|------|
| 401 | ログインし直す（IdP へ移り、戻ったら同じ画面） |
| 403 | 操作した場所に API のメッセージを出す |
| 4xx | 繰り返さない（結果が変わらず、表示が遅れるだけのため） |
| 5xx、通信の失敗 | TanStack Query の既定どおり 3 回繰り返す |

更新に成功したら、影響する取得をパスの前方一致で取り直す（`invalidate`）。

| 更新したもの | 取り直すパス |
|--------------|--------------|
| 税区分・税率 | `/tax-categories` |
| カテゴリ | `/categories` |
| 商品・バリアント | `/products`、`/inventory`（バリアントの増減で在庫も変わる） |
| 在庫 | `/inventory` |
| 注文（出荷の登録、承認・却下・キャンセル） | `/orders`、`/inventory`（キャンセルで在庫が変わる） |

削除した対象の詳細は、先に別の画面へ移ってから取り直す（削除した詳細を取り直すと 404 になるため）。

### 4.1 画像のアップロード

アップロードの受付 → ストレージへの直接の PUT（署名付き URL、アップロードの受付の応答のヘッダーを付ける）→ 登録、の順に送る。

- 送る前に形式・大きさ・枚数を画面でも確かめ、誤りがあれば 1 枚も送らない。
- ブラウザが形式を空で渡すことがある（Windows の HEIC など）ので、拡張子で補って申告する。
- 複数のファイルは 1 枚ずつ登録し、2 枚目からは前の登録の後のバージョンで送る（登録ごとに商品のバージョンが上がるため）。
- ストレージへの PUT など管理 API の外での失敗は、画面で決めたメッセージを出す。変換の失敗は、登録 API のメッセージを出す。

## 5. フォーム

- 画面での検証は、送る前に分かるもの（必須の未入力など）だけ。形式や業務ルールはサーバーが検証し、画面では書かない。
- サーバーの入力検証エラー（`errors[]`）は対応する入力欄の下に、項目に対応しないエラーはフォームの下に `message` を出す。
- 品番は、半角の英小文字を大文字にしてから送る。

### 5.1 バージョンを持つリソースのフォーム

`hooks/useVersionedForm` を使う。送るバージョンは、フォームを作ったときのもの。

- 入力を変える前にリソースが変わったら（自分のバリアントの操作など）、フォームを最新の値で作り直す。
- 入力を変えた後に他の人が更新していれば、上書きせずに 409 にする ※1。409（`CONCURRENT_MODIFICATION`）では「他の人が先に更新しました。再読み込みしてください」と、作り直すボタンを出す。

※1：最新のバージョンを送ると、画面に出ていない他の人の変更を、古い入力で黙って上書きしてしまうため。

## 6. 結果の知らせ方

| 場面 | 出し方 |
|------|--------|
| 更新の成功 | toast（「保存しました」など） |
| 更新の失敗 | 操作した場所（フォームやカードの中）。直す手がかりが必要なので toast にしない |
| 取得の失敗 | その取得を使う場所 |
| 取り消せない操作（削除、販売終了） | 確認のダイアログ（AlertDialog）を出してから送る |

## 7. 表示の決まり

| 対象 | 決まり |
|------|--------|
| 価格 | 税込価格がなければ税抜価格だけを出す |
| 日時 | 入力欄の値は日本時間とみなし、+09:00 のオフセット付きで送る |
| 列挙（状態、理由など） | 表示名は `lib/` の関数で引き、選択肢は生成した列挙の値の配列から作る |
| 操作できないもの | ボタンを出さないか、理由を添える（C 側に表示されたことのある商品は削除のボタンを出さず、販売終了を案内する） |
| 操作の旗と理由 | 下の表の API の値で出し分け、注文・商品の状態や階層の深さから推し量らない（[フロントのアーキテクチャ](../architecture.md) 第 3 節） |

| API の値 | 画面 |
|----------|------|
| 注文の `actions`（`approveCancellation`、`rejectCancellation`、`cancel`、`ship`） | 操作のボタンを出すか |
| 注文の `actions.cancelAsksRestock` | キャンセルの確認で、在庫に戻すかを選ばせるか（返送の注文だけ真） |
| 商品の `actions`（`publish`、`unpublish`、`discontinue`、`delete`、`deleteVariants`、`addVariant`） | 操作のボタンを出すか |
| 商品の `actions.deleteBlockedReason` | 削除できない理由の文（理由の値ごとに `product.status.deleteBlocked.<値>`）。なければ出さない |
| カテゴリの木の節の `actions.addChild` | 子を追加するボタンを出すか（いちばん深いカテゴリだけ偽） |

## 8. 画面を追加する手順

1. 使う API が `admin.yaml` にあるかを確かめる。なければ、先に plan と `admin.yaml` を直す。
2. `features/<機能>/pages/` に画面を作り、ルーターに URL を足す。機能の入口ならメニューにも足す。
3. 取得と更新は第 4 節（取り直すパスを表に足す）、フォームは第 5 節、知らせ方は第 6 節に従う。
4. 新しい表示の決まりは `lib/` に置き、単体テストを書く。
5. 主な操作の流れに結合テストを書く（第 9 節）。
6. lint、型チェック、テスト、ビルドを通し、開発サーバーで見た目を確かめる。

## 9. 結合テストの書き方

画面を本物のルーター・キャッシュ・toast で描画し、API の応答だけを MSW で差し替えて、利用者と同じように操作する。

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
|---------------------|------|
| `renderApp(url, { permissions })` | その URL で画面全体を描画し、`user`（user-event）と `router` を返す。運営者の権限は既定ですべて |
| `http` | `admin.yaml` の型に合った MSW のハンドラーを作る（パス・本文・状態が契約になければ型エラー） |
| `server` | API の代わりに応答する。既定のハンドラーは持たず、テストごとに `server.use(...)` で渡す |
| `problem(...)` | エラーレスポンスの本文を作る |
| `fixtures.ts` | 応答に使うデータ。関係のない項目にはありふれた値を入れ、確かめる項目だけを引数で渡す |

- 置き場所は、主に確かめる画面（`pages/`）の横。
- ボタンの見た目のリンク（`Button` の `render={<Link />}`）は、役割が `button` になる。
- ハンドラーのない API が呼ばれるとテストは失敗する。送った内容は、ハンドラーの中で `request.json()` を変数に取って確かめる。
- バージョンの決まり（第 5 節）は、入力中に他の人が更新した後で画面に取得し直させ（`fireEvent(window, new Event('visibilitychange'))`）、入力が消えず、送るバージョンが変わらないことまで確かめる。
- 管理 API の外への要求（ストレージへの PUT）は、`msw` の `http` で作る（`admin.yaml` の型がないため）。
- 画面の外で起きること（バックエンドの業務ルール、店舗画面への反映）は確かめない。

## 10. ログイン

SPA がブラウザで IdP とやり取りし（public client）、token はメモリーにだけ持つ。

| 項目 | 決まり |
|------|--------|
| 設定（`auth/oidc.ts`） | `ec-admin` realm の `ec-admin-web`。認可コード + PKCE。接続先は `VITE_OIDC_AUTHORITY`（既定 `http://localhost:8180/realms/ec-admin`）と `VITE_OIDC_CLIENT_ID`（既定 `ec-admin-web`）。戻り先は画面のトップ ※1 |
| token の置き場所 | メモリー（`InMemoryWebStorage`）。sessionStorage に置かない（JavaScript から読める場所に残さない）。IdP へ移る間の `state` と PKCE の値だけは sessionStorage |
| ログインを求める | `auth/AuthGate` がすべての画面を包み、ログインしていなければ IdP へ移り、戻ったら元の画面に戻す ※2 |
| token の更新 | 期限が近ければ refresh token で取り直す（自動）。取り直せなければ（最後の操作から 30 分、最長 8 時間）ログインし直す |
| ログインしている運営者 | `auth/operator` の `useOperator()` で氏名とログアウトを読む。コンポーネントは react-oidc-context を直接使わない（テストで置き換えるため） |
| 権限による出し分け | `auth/permissions` の `usePermissions()` で、管理 API の自分の権限の一覧（`GET /me`）を読む ※3。権限のない画面はメニューに出さず、URL で開いたら `auth/RequirePermission` が「権限がない」を示す（API は呼ばない）。今はレポートの画面だけを出し分ける |
| ログアウト | IdP のログアウト（RP-Initiated Logout。ID token と画面のトップを添える）へ移る |

※1：既定値はローカルと CI だけで使う。接続先はビルドの時に埋め込むので、本番ビルド（`vite build` の production モード）では、2 つのどちらかがなければ（空も含む）ビルドに失敗する（`config/oidcEnv.ts` を `vite.config.ts` から呼ぶ。`src/` の外に置くのは、画面のコードに入れないため）。開発サーバー、テスト、本番でないモードのビルドでは確かめない。CI のビルドと E2E は、ローカルの値を渡す。

※2：再読み込み・新しいタブではメモリーに token がないので IdP へ移るが、IdP のログインが残っていればパスワードなしですぐ戻る。隠した iframe は使わない。

※3：画面が token の権限を読むと、権限を読む処理がバックエンドと 2 か所になる（バックエンドのアーキテクチャ 第 7.2 節）。権限はログインし直すまで変わらないので、取り直さない。

### 10.1 API に付ける token

`AuthGate` が、今の access token を読む関数と、401 でログインし直す関数を `auth/token.ts` に渡し、クライアントの middleware がそれを使う。

- 渡すのは layout effect で行う（子の取得の effect が親の effect より先に動き、ログイン直後の最初の取得に token が付かなくなるため）。
- 401 でログインし直してから 30 秒以内にまた 401 なら、エラーを画面に出す（IdP との間を回り続けないため。時刻は sessionStorage）。
- IdP へ移るのは 1 回だけ（開発時の StrictMode で effect が 2 回動いても、2 つ目の `state` を作らない）。
- 戻る画面は、画面の中のパス（`/` で始まり、`//` と `/\` で始まらない）だけを受け付け、それ以外はトップにする。

結合テストの `renderApp` は `AuthGate` を通さず、ログインしている運営者を差し替えて描画する（token は付けず、401 で IdP へ移る処理も何もしない）。自分の権限の一覧は、既定ですべての権限を返す（`renderApp(url, { permissions })` で変える）。
