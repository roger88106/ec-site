---
name: new-screen
description: ec-site のフロントに画面や操作を追加するときの型。店舗画面（frontend/store、Next.js のサーバーコンポーネントとサーバーアクション）と管理画面（frontend/admin、TanStack Query の $api）の最小の例と、読むべき既存のファイル。画面・フォーム・ボタンの操作を作る、修正するときに使う。
---

# 画面を追加するときの型

決まりの正本は [店舗画面の構成](../../../docs/design/apps/store.md) と [管理画面の構成](../../../docs/design/apps/admin.md)。
本書はその要点と、そのままコピーできる最小の例。迷ったら正本を読む（store.md 第 4〜6・8 節、admin.md 第 4〜6・8・9 節）。

共通の前提：

- 使う API が OpenAPI（`api/openapi/store.yaml`、`admin.yaml`）にあること。なければ止めて、plan と OpenAPI を先に修正する（開発の進め方 第 1 節）。
- 画面の文言・コメントは日本語。コメントは何をするかを書き、決定の経緯はコミットメッセージに書く。
- 表示の文言は、コードに直接書かず文言ファイル（`messages/ja.json`）に置き、`t('キー')` で引いて、後ろに表示する内容をコメントで書く（フロントの書き方 第 7 節）。下の例の `foo.*` のキーは、文言ファイルに追加する。
- 表示の決まり（列挙の表示名、金額の整形など）は関数にして `lib/` か `features/<機能>/` に置き、単体テストを横に置く。
- 終わったら `scripts/dev/check-frontend.sh <store|admin>` を通し、プレビューで表示を確認する（skill `local-dev`）。

## 共有の部品（`frontend/shared`）

画面に表示や処理を書く前に、次の表から使える部品を探す。import のパスは `@ec-site/shared/` の後ろに置き場所を続ける（例：`@ec-site/shared/lib/format`）。

1. 書こうとする表示や処理（整形、表示名、リンクのボタン、選択肢、ID や戻り先の判定など）に当たる行を、「使う場面」の列から探す。
2. あれば、その部品を import して使う。自分で同じものを書かない。
3. 表にないものを 2 つの画面で使うときは、各画面に書かず `frontend/shared` に置く。置き場所と条件は [フロントの書き方](../../../docs/rules/frontend-coding.md) 第 5.1 節。共有に置いたら、この表に 1 行を追加する。

| 置き場所（import のパス） | 役割 | 使う場面 |
|---------------------------|------|----------|
| `lib/format` | `formatYen`（円）、`formatPrice`（税込・税抜の価格）、`formatNumber`（数）、`formatDateTime`（日時、日本時間）、`taxLabel`（税率ごとの消費税の行）、`formatPostalCode`（郵便番号）、`axisValuesLabel`（バリアントの軸の値の並び）、`japanDateTimeFormat` | 金額、数、日時、郵便番号の表示。既定の書式で足りるものは自分で `toLocaleString` や `Intl` を呼ばない ※1 |
| `lib/orderLabels` | `orderStatusLabel`、`shipmentStatusLabel`、`carrierLabel`：注文の状態、配送状態、配送業者の表示名 | 2 つの画面で同じ列挙の表示名 |
| `lib/options` | `withAllOption`：選択肢の先頭に「すべて」（値は null）を付ける | 絞り込みの選択肢 |
| `lib/categoryTree` | `flattenTree`（カテゴリの木を深さ付きの一覧にする）、`categoryOptions`（その一覧を選択肢にする） | カテゴリの選択肢と字下げ |
| `lib/patterns` | `isUuid`：ID の形の判定 | URL の ID を API に渡す前の判定 |
| `lib/login` | `isSafeReturnPath`（ログインの後に戻ってよいパスか）、`fullName`（姓名を 1 つの表示にする） | ログインの戻り先の検査、氏名の表示 |
| `lib/cn` | `cn`：クラス名の結合 | 条件つきのクラス名 |
| `lib/locale` | `LOCALE`、`TIME_ZONE` | 整形のツールに渡すロケールとタイムゾーン |
| `lib/translate` | `translate`：共有の文言（`common`）だけを引く関数 | 共有パッケージの中だけで使う。各画面は、その画面の `translate` を使う |
| `components/AmountsSummary` | `AmountsSummary`：金額の内訳（商品の合計、送料、総額、税率ごとの消費税額）。`variant` は `card`（管理画面）か `page`（店舗画面） | カート、注文手続き、注文の金額の内訳 |
| `components/LinkButton` | `LinkButton`：リンクの見た目のボタン。描く `a` は `link` で渡す | ルーターのリンクをボタンの見た目にする |
| `ui/alert`、`ui/badge`、`ui/button`、`ui/input`、`ui/label`、`ui/separator` | shadcn/ui の生成物：`Alert`、`Badge`、`Button`、`Input`、`Label`、`Separator` | 警告、バッジ、ボタン、入力欄、ラベル、区切り線 |
| `theme.css` | shadcn/ui のテーマ（色、角丸、フォント）。各画面の CSS が import する | 色などの値。画面に値を直接書かない |
| `messages/ja.json` | 共有の文言（`common`） | 2 つの画面で同じ文言。列挙の表示名、「すべて」、接続の失敗など |
| `scripts/export-constraints.mjs` | OpenAPI の入力の制約を、画面が使う定数のファイルに書き出す。各画面の API の生成で使う | 画面の入力欄の制約。直接は実行せず、各画面の API 生成の script から実行する |

※1：既定と違う書式（符号付き、率など）は使う画面の `lib/` に置き（1 つの機能だけで使うなら、その機能のディレクトリに置く。[フロントの書き方](../../../docs/rules/frontend-coding.md) 第 1.1 節）、ロケールは `lib/locale` から取る。

## 店舗画面（`frontend/store`）

| 置き場所 | 中身 |
|----------|------|
| `src/app/<URL>/page.tsx` | サーバーコンポーネント。取得とコンポーネントの組み立てだけ |
| `src/features/<機能>/components/` | 画面のコンポーネント。操作のあるコンポーネントだけ `'use client'` |
| `src/actions/<資源>.ts` | サーバーアクション（`'use server'`）。変更系の API はここからだけ呼ぶ |
| `src/api/client.ts` | `getApi()`、`UNCACHED` |
| `src/api/result.ts` | `unwrap()`：404 → `notFound()`、それ以外の失敗 → `ApiError` を投げる（`error.tsx` に表示される） |
| `src/api/cartCookie.ts` | `readCartId()`（コンポーネントとアクション）、`writeCartId()`（アクションだけ） |
| `src/lib/actionResult.ts` | `ActionResult`、`SUCCESS`、`toFailure()`、`CONNECTION_FAILED` |

似た既存の例：取得の画面は `app/orders/[orderId]/page.tsx`、操作は `actions/order.ts` と `features/order/components/CancellationRequestForm.tsx`、カートの Cookie を使う操作は `actions/cart.ts`（404 で再作成する `withCart`）。

### 取得（`app/<URL>/page.tsx`）

```tsx
import { isUuid } from '@ec-site/shared/lib/patterns'
import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { getTranslations } from 'next-intl/server'
import { getApi, UNCACHED } from '@/api/client'
import { unwrap } from '@/api/result'

/** ブラウザーのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('foo.detail')
  return { title: t('title') } // 〇〇
}

/** 〇〇 */
export default async function FooPage({ params }: PageProps<'/foos/[fooId]'>) {
  const t = await getTranslations('foo.detail')
  const { fooId } = await params
  // 形の違う ID は API を呼ばずに「見つかりません」にする
  if (!isUuid(fooId)) {
    notFound()
  }
  const api = await getApi()
  // 1 件の取得と、操作で変わるもの（カート・注文手続き・注文）は UNCACHED。一覧・検索はキャッシュしてよい
  const foo = unwrap(await api.GET('/foos/{fooId}', { params: { path: { fooId } }, ...UNCACHED }))
  return (
    <div className="space-y-8">
      <h1 className="font-heading font-semibold text-2xl">{t('title') /* 〇〇 */}</h1>
      <FooForm fooId={foo.id} />
    </div>
  )
}
```

### 変更（`actions/<資源>.ts`）

```ts
'use server'

import { revalidatePath } from 'next/cache'
import { withActionResult } from '@/actions/withActionResult'
import { getApi } from '@/api/client'
import { type ActionResult, SUCCESS, toFailure } from '@/lib/actionResult'
import { readString } from '@/lib/server/formData'

/**
 * 〇〇する。成功したら、〇〇の画面を再表示する。<br />
 * フォームの項目：fooId、note。
 */
export async function updateFoo(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const fooId = readString(formData, 'fooId')
  const note = readString(formData, 'note')
  // 接続の失敗などの例外は、withActionResult が失敗の結果にする（catch は書かない。Biome で検査）
  return withActionResult(async () => {
    const api = await getApi()
    const { error, response } = await api.PUT('/foos/{fooId}', { params: { path: { fooId } }, body: { note } })
    revalidatePath(`/foos/${fooId}`)
    return response.ok ? SUCCESS : toFailure(response.status, error)
  })
  // 次の画面へ移るときは、結果を受けてから包みの外で redirect('/…')（redirect は例外で移るため。actions/order.ts の payOrder）
}
```

- 失敗は投げずに `ActionResult` で返す。会員の API（`getMemberApi()`）で 401 なら `redirectToLogin()`（`actions/order.ts`）。入力欄ごとのメッセージは `result.fieldErrors?.<項目名>`。
- カートを変更するアクションは `readCartId()` で ID を読み、変更の後に `writeCartId()` で再度書き込む（有効期限を延ばす）。

### フォーム（`features/<機能>/components/FooForm.tsx`）

結果の表示をテストするフォームは、`useActionState` を呼ぶコンポーネントと、結果を props で受ける表示のコンポーネントに分ける（例：`OrderForm` と `OrderFormView`）。テストは表示のコンポーネントに結果を渡す。結果の表示をテストしない他のフォームは、分けなくてよい。

```tsx
'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import { useActionState } from 'react'
import { updateFoo } from '@/actions/foo'
import { ActionError } from '@/components/ActionError'

/** 〇〇のフォーム */
export function FooForm({ fooId }: { fooId: string }) {
  const t = useTranslations('foo.form')
  const [result, formAction, pending] = useActionState(updateFoo, null)
  return (
    // 失敗の表示は送る前から読み上げの領域を描く（店舗画面の構成 第 6 節）。親は flex と gap で並べる
    <form action={formAction} className="flex flex-col items-start gap-3">
      <input name="fooId" type="hidden" value={fooId} />
      {/* 入力欄。誤りは欄の下に出し、aria-describedby と aria-invalid で入力欄に結び付ける（OrderForm の Field） */}
      <Button disabled={pending} type="submit">
        {pending ? t('submitting') : t('submit') /* 送信中か、〇〇する */}
      </Button>
      <ActionError result={result} />
    </form>
  )
}
```

## 管理画面（`frontend/admin`）

| 置き場所 | 中身 |
|----------|------|
| `src/features/<機能>/pages/FooPage.tsx` | 画面。取得とコンポーネントの組み立てだけ |
| `src/features/<機能>/components/` | フォーム、表、操作のボタン |
| `src/app/paths.ts` | 画面の URL の形（`routePatterns`）と、URL を作る関数（`paths`）を追加する |
| `src/app/router.tsx` | ルートを追加する（path は `routePatterns`）。機能の入口なら `src/app/menu.ts` にも |
| `src/api/client.ts` | `$api`（openapi-react-query） |
| `src/api/useApiMutation.ts` | 更新の操作（成功の知らせ、続きの処理、一覧の再取得） |
| `src/api/useMutationGroup.ts` | 1 つの場所の複数の更新の操作の、待ち状態・最初のエラー・エラーの消去 |
| `src/api/invalidate.ts` | 再取得するパスの前方一致 `ApiPathPrefix`。新しいパスは admin.md 第 4 節の表にも追加する |
| `src/components/` | `PageHeader`、`QueryState`、`ErrorMessage`、`ConfirmDialog`、`Pagination`、`DateTime`、`Price` |
| `src/hooks/useVersionedForm.ts` | バージョンを持つリソースのフォーム（409 の扱い。admin.md 第 5 節） |

似た既存の例：一覧と URL の絞り込みは `features/order/pages/OrderListPage.tsx` と `features/order/orderFilter.ts`、詳細は `OrderDetailPage.tsx`、確認つきの操作は `features/order/components/OrderActions.tsx`、フォームは `features/inventory/components/AdjustStockForm.tsx`、結合テストは `features/inventory/pages/StockPage.test.tsx`。

### 一覧（絞り込みは URL のクエリに持つ）

```tsx
import { keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { type FooFilter, parseFilter, toSearchParams } from '@/features/foo/fooFilter'

/** 〇〇の一覧 */
export function FooListPage() {
  const t = useTranslations('foo.list')
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseFilter(searchParams) // 不正な値は無視する。fooFilter.test.ts を書く
  const query = $api.useQuery(
    'get',
    '/foos',
    { params: { query: { keyword: filter.keyword || undefined, page: filter.page } } },
    { placeholderData: keepPreviousData },
  )
  /** 絞り込みを URL に反映する（URL が絞り込みの正本） */
  const apply = (next: FooFilter) => setSearchParams(toSearchParams(next))
  return (
    <>
      <PageHeader title={t('title') /* 〇〇 */} />
      {/* 絞り込みのフォームは key={searchParams.toString()} で、URL が変わったら再作成する */}
      <QueryState query={query}>
        {(result) => (
          <>
            {/* 表（components/ui/table）。0 件なら「該当する〇〇はありません。」 */}
            <Pagination
              onChange={(page) => apply({ ...filter, page })}
              page={result.page}
              totalItems={result.totalItems}
              totalPages={result.totalPages}
            />
          </>
        )}
      </QueryState>
    </>
  )
}
```

### 操作（更新、再取得、toast、確認）

```tsx
import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'use-intl'
import { useApiMutation } from '@/api/useApiMutation'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'

/** 〇〇の操作。取り消せないので確認してから送る */
export function FooActions({ fooId }: { fooId: string }) {
  const t = useTranslations('foo.actions')
  const close = useApiMutation('put', '/foos/{fooId}/close', {
    success: t('closed'), // 〇〇の知らせ
    invalidate: ['/foos'], // 影響する取得をパスの前方一致で再取得する（admin.md 第 4 節の表）
  })
  return (
    <div className="space-y-3">
      <ConfirmDialog
        confirmLabel={t('closeConfirm') /* 〇〇する */}
        description={t('closeDescription') /* 元に戻せないこと */}
        onConfirm={() => close.mutate({ params: { path: { fooId } } })}
        title={t('closeTitle') /* 〇〇の確認 */}
        trigger={<Button disabled={close.isPending}>{t('close') /* 〇〇する */}</Button>}
      />
      {/* 失敗は toast ではなく操作した場所に出す */}
      <ErrorMessage error={close.error} />
    </div>
  )
}
```

- 操作が複数あれば `const { pending, error, resetErrors } = useMutationGroup(close, reopen)`。例は `features/product/components/VariantSection.tsx`。
- 詳細の画面は `const { fooId = '' } = useParams()` と `$api.useQuery('get', '/foos/{fooId}', { params: { path: { fooId } } })`。
- フォームは React Hook Form。サーバーの `errors[]` を欄に表示するのは `api/formErrors.ts` の `setServerFieldErrors`。画面での検証は必須の未入力だけ。
- 列挙の表示名は `lib/labels.ts`（または機能の `*.ts`）に置き、画面に文字列を直接書かない。

### 結合テスト（`pages/FooPage.test.tsx`）

書き方は admin.md 第 9 節。要点：`server.use(http.get('/foos', ({ response }) => response(200).json({...})))`、`const { user } = renderApp('/foos')`、要素は役割と名前で探す、呼ばれる API は全てハンドラーを渡す、テスト名に AC 番号。

## 画面を追加し終えたら

- 利用者に、確認する URL を全て挙げて伝える（店舗画面は `http://localhost:3000/...`、管理画面は `http://localhost:5173/...`。動的な部分は実際に開ける ID にする）。新しい画面は、メニューやヘッダーにリンクを追加しない限りどこからも到達できないため、到達する経路（メニューのどれか、どの画面のどのリンクか）も添える。
- あわせて、何を追加したか（ルート、メニュー・ヘッダーのリンク、API の呼び出し）を短く書く。リンクを追加していない画面は、追加するかを利用者に確認する。

出典：この節は [Ginz9013/t3-flare](https://github.com/Ginz9013/t3-flare) の site-builder（`references/build-features.md` の「Report the routes to the user」）を、このリポジトリ向けに書き換えたもの（MIT。`../LICENSE-t3-flare`）。
