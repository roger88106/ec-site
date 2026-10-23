# 管理画面（`frontend/admin`）の構成

B 側（運営者）の画面。Vite + React の SPA で、管理 API（`admin.yaml`）をブラウザから呼ぶ。店舗画面との違いと共通の決まりは [フロントのアーキテクチャ](../frontend-architecture.md)。新しい画面は第 8 節の手順で作る。

| 項目 | 内容 |
|------|------|
| 依存 | 管理 API（`admin.yaml`）、運営者の realm（IdP。ログイン）、共有のパッケージ（`frontend/shared`） |
| 関連 spec | [014](../../specs/archive/mvp/014-frontend-consistency)、[015](../../specs/archive/mvp/015-security)、[016](../../specs/archive/mvp/016-master-data)、[018](../../specs/archive/mvp/018-docs-and-tools)、[134](../../specs/archive/1to10/134-admin-tidy) |

## 1. 技術

| 用途 | 採用 | 補足 |
|------|------|------|
| ビルド・開発サーバー | Vite | 開発時は `/api` をバックエンド（既定 `localhost:8080`、`ADMIN_API_PROXY_TARGET`）に転送する |
| ルーティング | React Router | ID を持つ画面（商品の編集など）を、再読み込みやリンクで開けるようにする |
| API | openapi-fetch + openapi-react-query | パスと型から呼び、キャッシュのキーも自動で決まる |
| サーバー状態 | TanStack Query | 既定の設定。ただし 4xx は再試行しない |
| 文言 | use-intl | — |
| フォーム | React Hook Form | 入力の状態、変更の有無、項目ごとのエラー |
| UI | shadcn/ui、Tailwind CSS、lucide-react | レイアウトは Sidebar、成功の通知は sonner（toast） |
| グラフ | Recharts | shadcn/ui の chart（`components/ui/chart.tsx`）でラップして使う |
| ログイン | oidc-client-ts + react-oidc-context | 認可コード + PKCE（public client）。token はメモリーだけ |
| テスト | Vitest、React Testing Library、user-event、MSW（openapi-msw）、jsdom | — |

## 2. ディレクトリ

```
frontend/admin/
├── messages/ja.json        # 文言
└── src/
    ├── app/                # エントリーポイント、ルーター、Provider、レイアウト（Sidebar）、URL（paths.ts）
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
    │   ├── date.ts         # 日付の形（yyyy-MM-dd）の判定
    │   ├── format.ts       # 日時の入力欄との変換、日本時間の日付、数の整形（金額・価格・日時などの整形は共有パッケージの lib/format.ts）
    │   └── searchParams.ts # URL のクエリのページ番号の読み取り
    ├── test/               # 結合テストの土台（画面のコードからは import しない）
    └── features/<機能>/    # tax、category、product、inventory、order、report
        ├── pages/          # ルートに対応する画面（取得とコンポーネントの組み立てだけ）
        ├── components/     # その機能だけのコンポーネント
        ├── labels.ts       # その機能だけで使う列挙の表示名と Badge の見た目（2 つ以上の機能で使うなら lib/labels.ts）
        └── *.ts            # その機能だけの関数・型・hook
```

- `pages/` は薄く保ち、フォームや表は `components/` に分ける。
- `components/ui/` は生成物のまま使う。修正したいときは外側のコンポーネントでラップする。
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

- 一覧の絞り込みとページは URL のクエリに持つ（戻る・再読み込みで同じ一覧を表示するため）。
- 左のメニューには、各機能の入口（一覧）だけを表示する。レポートは「レポート」の下に 3 つの画面を並べる。
- レポートの条件も URL のクエリに持つ（再読み込みとリンクの共有のため）。期間がなければ、売上の推移は今年度、他は今月にする。期間の選択肢（今年度、今月など）は、レポートの設定の API の会計年度の始まりの月で算出する。
- 条件の商品の選択肢は、商品の一覧の API の 1 ページ（100 件）までを表示する。商品が増えて足りなくなったら、検索して選ぶ形に変える。
- 表示しているレポートの表は、CSV（UTF-8 の BOM 付き、CRLF）で取り出せる。= + - @ で始まる文字の項目は、表計算ソフトで式として解釈されないよう前に `'` を付ける。

## 4. API の呼び出し

- 取得は `$api.useQuery('get', '<パス>', { params })`、更新は `useApiMutation('<メソッド>', '<パス>', { success, invalidate, onSuccess })`。キャッシュのキーとクエリの関数を手動で書かない。
- 1 つの場所に複数の更新があるときは、`useMutationGroup(...)` で待ち状態・最初のエラー・エラーの消去をまとめる。
- エラーの応答はクライアントの middleware で `ApiError` にして投げ、画面は `ApiError` だけを扱う。
- access token はクライアントの middleware で `Authorization` に付ける（第 10 節）。
- バックエンドへの呼び出しに、タイムアウトを置かない。ブラウザーの fetch で呼ぶので、画面は止まらず、利用者が操作できるため。

| 応答 | 扱い |
|------|------|
| 401 | 再ログインする（IdP へリダイレクトし、戻ったら同じ画面） |
| 403 | 操作した場所に API のメッセージを表示する |
| 4xx | 再試行しない（結果が変わらず、表示が遅れるだけのため） |
| 5xx、通信の失敗 | TanStack Query の既定どおり 3 回再試行する |

更新に成功したら、影響する取得をパスの前方一致で再取得する（`invalidate`）。

| 更新したもの | 再取得するパス |
|--------------|--------------|
| 税区分・税率 | `/tax-categories` |
| カテゴリ | `/categories` |
| 商品・バリアント | `/products`、`/inventory`（バリアントの増減で在庫も変わる） |
| 在庫 | `/inventory` |
| 注文（出荷の登録、承認・却下・キャンセル） | `/orders`、`/inventory`（キャンセルで在庫が変わる） |

削除した対象の詳細は、先に別の画面へ遷移してから再取得する（削除した詳細を再取得すると 404 になるため）。

### 4.1 画像のアップロード

アップロードの受付 → ストレージへの直接の PUT（署名付き URL、アップロードの受付の応答のヘッダーを付ける）→ 登録、の順に送る。

- 送る前に形式・大きさ・枚数を画面でも確認し、誤りがあれば 1 枚も送らない。
- ブラウザーが形式を空で渡すことがある（Windows の HEIC など）ので、拡張子で補って申告する。
- 複数のファイルは 1 枚ずつ登録し、2 枚目からは、直前の登録で上がったバージョンで送る（登録ごとに商品のバージョンが上がるため）。
- ストレージへの PUT など管理 API の外での失敗は、画面で決めたメッセージを表示する。変換の失敗は、登録 API のメッセージを表示する。

## 5. フォーム

- 画面での検証は、送る前に分かるもの（必須の未入力など）だけ。形式や業務の規則はサーバーが検証し、画面では書かない。
- サーバーの入力検証エラー（`errors[]`）は対応する入力欄の下に、項目に対応しないエラーはフォームの下に `message` を表示する。
- 品番は、半角の英小文字を大文字にしてから送る。

### 5.1 バージョンを持つリソースのフォーム

`hooks/useVersionedForm` を使う。送るバージョンは、フォームを作ったときのもの。

- 入力を変更する前にリソースが更新されたら（自分のバリアントの操作など）、フォームを最新の値で再作成する。
- 入力を変更した後に他の人が更新していれば、サーバーは上書きせずに 409 を返す ※1。409（`CONCURRENT_MODIFICATION`）では「他の人が先に更新しました。再読み込みしてください」と、再作成するボタンを表示する。ボタンを押したら、失敗した更新（mutation）を引数にして `useVersionedForm` の `reloadAfter` を呼ぶ（更新の状態をリセットしてから、最新の値でフォームを再作成する）。

※1：最新のバージョンを送ると、画面に表示されていない他の人の変更を、古い入力で警告なしに上書きするため。

## 6. 結果の知らせ方

| 場面 | 出し方 |
|------|--------|
| 更新の成功 | toast（「保存しました」など） |
| 更新の失敗 | 操作した場所（フォームやカードの中）。修正する手がかりが必要なので toast にしない |
| 取得の失敗 | その取得を使う場所 |
| 取り消せない操作（削除、販売終了） | 確認のダイアログ（AlertDialog）を表示してから送る |

## 7. 表示の決まり

| 対象 | 決まり |
|------|--------|
| 価格 | 税込価格がなければ税抜価格だけを表示する |
| 日時 | 入力欄の値は日本時間とみなし、+09:00 のオフセット付きで送る |
| 列挙（状態、理由など） | 表示名は `lib/` の関数で引き、選択肢は生成した列挙の値の配列から作る |
| 操作できないもの | ボタンを表示しないか、理由を添える（C 側に表示されたことのある商品は削除のボタンを表示せず、販売終了を案内する） |
| 操作のフラグと理由 | 下の表の API の値で出し分け、注文・商品の状態や階層の深さから推測しない（[フロントのアーキテクチャ](../frontend-architecture.md) 第 3 節） |

| API の値 | 画面 |
|----------|------|
| 注文の `actions`（`approveCancellation`、`rejectCancellation`、`cancel`、`ship`） | 操作のボタンを表示するか |
| 注文の `actions.cancelAsksRestock` | キャンセルの確認で、在庫に戻すかを選ばせるか（返送の注文だけ真） |
| 商品の `actions`（`publish`、`unpublish`、`discontinue`、`delete`、`deleteVariants`、`addVariant`） | 操作のボタンを表示するか |
| 商品の `actions.deleteBlockedReason` | 削除できない理由の文（理由の値ごとに `product.status.deleteBlocked.<値>`）。なければ表示しない |
| カテゴリの木の節の `actions.addChild` | 子を追加するボタンを表示するか（いちばん深いカテゴリだけ偽） |

## 8. 画面を追加する手順

1. 使う API が `admin.yaml` にあるかを確認する。なければ、先に plan と `admin.yaml` を修正する。
2. `features/<機能>/pages/` に画面を作り、ルーターに URL を追加する。機能の入口ならメニューにも追加する。
3. 取得と更新は第 4 節（再取得するパスを表に追加する）、フォームは第 5 節、知らせ方は第 6 節に従う。
4. 新しい表示の決まりは `lib/` に置き、単体テストを書く。
5. 主な操作の流れに結合テストを書く（第 9 節）。
6. lint、型チェック、テスト、ビルドを通し、開発サーバーで見た目を確認する。

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
| `renderApp(url, { permissions, me })` | その URL で画面全体を描画し、`user`（user-event）と `router` を返す。運営者の権限は既定で全て。`me` で自分の権限の一覧の応答を、サーバーのエラー（`serverError`）、通信の失敗（`networkError`）、応答しない（`pending`）に差し替えられる。キャッシュの再試行の回数は本番と同じにし、間隔は 0 にする |
| `http` | `admin.yaml` の型に合った MSW のハンドラーを作る（パス・本文・状態が契約になければ型エラー） |
| `server` | API の代わりに応答する。既定のハンドラーは持たず、テストごとに `server.use(...)` で渡す |
| `problem(...)` | エラーレスポンスの本文を作る |
| `fixtures.ts` | 応答に使うデータ。関係のない項目には一般的な値を入れ、確認する項目だけを引数で渡す |

- 置き場所は、主に確認する画面（`pages/`）の横。
- リンクの見た目のボタン（共有の `LinkButton`）は、役割が `button` になる。
- ハンドラーのない API が呼ばれるとテストは失敗する。送った内容は、ハンドラーの中で `request.json()` を変数に取って確認する。
- バージョンの決まり（第 5 節）は、入力中に他の人が更新した後で、画面に再取得させる（`fireEvent(window, new Event('visibilitychange'))`）。入力が消えず、送るバージョンが変わらないことまで確認する。
- 管理 API の外への要求（ストレージへの PUT）は、`msw` の `http` で作る（`admin.yaml` の型がないため）。
- 画面の外で起きること（バックエンドの業務の規則、店舗画面への反映）は確認しない。

## 10. ログイン

SPA がブラウザーで IdP とやり取りし（public client）、token はメモリーにだけ保持する。

| 項目 | 決まり |
|------|--------|
| 設定（`auth/oidc.ts`） | `ec-admin` realm の `ec-admin-web`。認可コード + PKCE。接続先は `VITE_OIDC_AUTHORITY`（既定 `http://localhost:8180/realms/ec-admin`）と `VITE_OIDC_CLIENT_ID`（既定 `ec-admin-web`）。戻り先は画面のトップ ※1 |
| token の置き場所 | メモリー（`InMemoryWebStorage`）。sessionStorage に置かない（JavaScript から読める場所に残さない）。IdP へ移る間の `state` と PKCE の値だけは sessionStorage |
| ログインを求める | `auth/AuthGate` が全ての画面をラップし、ログインしていなければ IdP へリダイレクトし、戻ったら元の画面を表示する ※2 |
| token の更新 | 期限が近ければ refresh token で再取得する（自動）。再取得できなければ（最後の操作から 30 分、またはログインから 8 時間を過ぎたとき）再ログインする |
| ログインしている運営者 | `auth/operator` の `useOperator()` で氏名とログアウトを読む。コンポーネントは react-oidc-context を直接使わない（テストで置き換えるため） |
| 権限による出し分け | `auth/permissions` の `usePermissions()` で、管理 API の自分の権限の一覧（`GET /me`）を読む ※3。権限のない画面はメニューに表示せず、URL で開いたら `auth/RequirePermission` が「権限がない」を示す（API は呼ばない）。今はレポートの画面だけを出し分ける。自分の権限の一覧の取得に失敗したら（5xx、通信の失敗）、権限が必要な画面は取得の失敗の文と「再試行」を示し、メニューはレポートの入口を全て表示する（権限の有無が分からないので隠さない）。再試行で取得し直している間は、失敗の表示のままボタンを押せなくする |
| ログアウト | IdP のログアウト（RP-Initiated Logout。ID token と画面のトップを添える）へ移る |

※1：既定値はローカルと CI だけで使う。接続先はビルドの時に埋め込むので、本番ビルド（`vite build` の production モード）では、2 つのどちらかがなければ（空も含む）ビルドに失敗する（`config/oidcEnv.ts` を `vite.config.ts` から呼ぶ。`src/` の外に置くのは、画面のコードに入れないため）。開発サーバー、テスト、本番でないモードのビルドでは確認しない。CI のビルドと E2E は、ローカルの値を渡す。

※2：再読み込み・新しいタブではメモリーに token がないので IdP へリダイレクトするが、IdP のログインが残っていれば、パスワードなしですぐ元の画面を表示する。非表示の iframe は使わない。

※3：画面が token の権限を読むと、権限を読む処理がバックエンドと 2 か所になる（バックエンドのアーキテクチャ 第 7.2 節）。権限は再ログインするまで変わらないので、再取得しない。

### 10.1 API に付ける token

`AuthGate` が、今の access token を読む関数と、401 で再ログインする関数を `auth/token.ts` に渡し、クライアントの middleware がそれを使う。

- 渡すのは layout effect で行う（子コンポーネントでデータを取得する effect が親の effect より先に実行され、ログイン直後の最初の取得に token が付かなくなるため）。
- 401 で再ログインしてから 30 秒以内にまた 401 なら、エラーを画面に表示する（IdP へのリダイレクトを繰り返さないため。時刻は sessionStorage）。
- IdP へ移るのは 1 回だけ（開発時の StrictMode で effect が 2 回動いても、2 つ目の `state` を作らない）。
- 戻る画面は、画面の中のパス（`/` で始まり、`//` と `/\` で始まらず、制御文字（U+0000〜U+001F と U+007F）を含まない）だけを受け付け、それ以外はトップにする。制御文字を拒むのは、URL の解析は TAB と改行を取り除くので、その後に `//` で始まる形（外部のサイト）になる入力があるため。

結合テストの `renderApp` は `AuthGate` を通さず、ログインしている運営者を差し替えて描画する（token は付けず、401 で IdP へ移る処理も行わない）。自分の権限の一覧は、既定で全ての権限を返す（`renderApp(url, { permissions })` で変える）。
