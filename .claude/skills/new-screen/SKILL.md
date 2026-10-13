---
name: new-screen
description: ec-site のフロントに画面や操作を足すときの型。店舗画面（frontend/store、Next.js のサーバーコンポーネントとサーバーアクション）と管理画面（frontend/admin、TanStack Query の $api）の最小の例と、読むべき既存のファイル。画面・フォーム・ボタンの操作を作る、直すときに使う。
---

# 画面を足すときの型

決まりの正本は [店舗画面の構成](../../../docs/frontend/store.md) と [管理画面の構成](../../../docs/frontend/admin.md)。
本書はその要点と、そのまま写せる最小の例。迷ったら正本を読む（store.md 第 4〜6・8 節、admin.md 第 4〜6・9・10 節）。

共通の前提：

- 使う API が OpenAPI（`api/openapi/store.yaml`、`admin.yaml`）にあること。なければ止めて、plan と OpenAPI を先に直す（constitution 15）。
- 画面の文言・コメントは日本語。コメントは何をするかを書き、決定の経緯はコミットメッセージに書く。
- 表示の決まり（列挙の表示名、金額の整形など）は関数にして `lib/` か `features/<機能>/` に置き、単体テストを横に置く。
- 終わったら `scripts/dev/check-frontend.sh <store|admin>` を通し、プレビューで表示を確かめる（skill `local-dev`）。

## 店舗画面（`frontend/store`）

| 置き場所 | 中身 |
|----------|------|
| `src/app/<URL>/page.tsx` | サーバーコンポーネント。取得と部品の組み立てだけ |
| `src/features/<機能>/components/` | 画面の部品。操作のある部品だけ `'use client'` |
| `src/actions/<資源>.ts` | サーバーアクション（`'use server'`）。変更系の API はここからだけ呼ぶ |
| `src/api/client.ts` | `getApi()`、`UNCACHED` |
| `src/api/result.ts` | `unwrap()`：404 → `notFound()`、それ以外の失敗 → `ApiError` を投げる（`error.tsx` に出る） |
| `src/api/cartCookie.ts` | `readCartId()`（コンポーネントとアクション）、`writeCartId()`（アクションだけ） |
| `src/lib/actionResult.ts` | `ActionResult`、`SUCCESS`、`toFailure()`、`CONNECTION_FAILED` |

似た既存の例：取得の画面は `app/orders/[orderId]/page.tsx`、操作は `actions/order.ts` と `features/order/components/CancellationRequestForm.tsx`、カートの Cookie を使う操作は `actions/cart.ts`（404 で作り直す `withCart`）。

### 取得（`app/<URL>/page.tsx`）

```tsx
import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { getApi, UNCACHED } from '@/api/client'
import { unwrap } from '@/api/result'
import { isUuid } from '@/lib/uuid'

/** ブラウザのタブの題名 */
export const metadata: Metadata = { title: '〇〇' }

/** 〇〇（T____。AC-__） */
export default async function FooPage({ params }: PageProps<'/foos/[fooId]'>) {
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
      <h1 className="font-heading font-semibold text-2xl">〇〇</h1>
      <FooForm fooId={foo.id} />
    </div>
  )
}
```

### 変更（`actions/<資源>.ts`）

```ts
'use server'

import { revalidatePath } from 'next/cache'
import { getApi } from '@/api/client'
import { type ActionResult, CONNECTION_FAILED, SUCCESS, toFailure } from '@/lib/actionResult'

/**
 * 〇〇する（AC-__）。成功したら、〇〇の画面を表示し直す。<br />
 * フォームの項目：fooId、note。
 */
export async function updateFoo(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const fooId = String(formData.get('fooId') ?? '')
  const note = String(formData.get('note') ?? '').trim()
  try {
    const api = await getApi()
    const { error, response } = await api.PUT('/foos/{fooId}', { params: { path: { fooId } }, body: { note } })
    revalidatePath(`/foos/${fooId}`)
    // 次の画面へ移るときは、成功時に redirect('/…')（try の外で呼ぶ。redirect は例外で移るため）
    return response.ok ? SUCCESS : toFailure(response.status, error)
  } catch {
    return { ok: false, message: CONNECTION_FAILED }
  }
}
```

- 失敗は投げずに `ActionResult` で返す。入力欄ごとのメッセージは `result.fieldErrors?.<項目名>`。
- カートを変更するアクションは `readCartId()` で ID を読み、変更の後に `writeCartId()` で書き直す（有効期限を延ばす）。

### フォーム（`features/<機能>/components/FooForm.tsx`）

```tsx
'use client'

import { useActionState } from 'react'
import { updateFoo } from '@/actions/foo'
import { Button } from '@/components/ui/button'

/** 〇〇のフォーム（AC-__） */
export function FooForm({ fooId }: { fooId: string }) {
  const [result, formAction, pending] = useActionState(updateFoo, null)
  return (
    <form action={formAction} className="space-y-3">
      <input name="fooId" type="hidden" value={fooId} />
      {/* 入力欄。誤りは result?.ok === false && result.fieldErrors?.note を欄の下に出す */}
      <Button disabled={pending} type="submit">
        {pending ? '送信しています…' : '〇〇する'}
      </Button>
      {result?.ok === false && (
        <p aria-live="polite" className="text-destructive text-sm">
          {result.message}
        </p>
      )}
    </form>
  )
}
```

## 管理画面（`frontend/admin`）

| 置き場所 | 中身 |
|----------|------|
| `src/features/<機能>/pages/FooPage.tsx` | 画面。取得と部品の組み立てだけ |
| `src/features/<機能>/components/` | フォーム、表、操作のボタン |
| `src/app/router.tsx` | ルートを足す。機能の入口なら `src/app/menu.ts` にも |
| `src/api/client.ts` | `$api`（openapi-react-query） |
| `src/api/useApiMutation.ts` | 更新の操作（成功の知らせ、続きの処理、一覧の取り直し） |
| `src/api/useMutationGroup.ts` | 1 つの場所の複数の更新の操作の、待ち状態・最初のエラー・エラーの消去 |
| `src/api/invalidate.ts` | 取り直すパスの前方一致 `ApiPathPrefix`。新しいパスは admin.md 第 4 節の表にも足す |
| `src/components/` | `PageHeader`、`QueryState`、`ErrorMessage`、`ConfirmDialog`、`Pagination`、`DateTime`、`Price` |
| `src/hooks/useVersionedForm.ts` | バージョンを持つリソースのフォーム（409 の扱い。admin.md 第 5 節） |

似た既存の例：一覧と URL の絞り込みは `features/order/pages/OrderListPage.tsx` と `features/order/orderFilter.ts`、詳細は `OrderDetailPage.tsx`、確認つきの操作は `features/order/components/OrderActions.tsx`、フォームは `features/inventory/components/AdjustStockForm.tsx`、結合テストは `features/inventory/pages/StockPage.test.tsx`。

### 一覧（絞り込みは URL のクエリに持つ）

```tsx
import { keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { $api } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { type FooFilter, parseFilter, toSearchParams } from '@/features/foo/fooFilter'

/** 〇〇の一覧（T____。AC-__） */
export function FooListPage() {
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
      <PageHeader title="〇〇" />
      {/* 絞り込みのフォームは key={searchParams.toString()} で、URL が変わったら作り直す */}
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

### 操作（更新、取り直し、toast、確認）

```tsx
import { useApiMutation } from '@/api/useApiMutation'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'

/** 〇〇の操作（AC-__）。取り消せないので確認してから送る */
export function FooActions({ fooId }: { fooId: string }) {
  const close = useApiMutation('put', '/foos/{fooId}/close', {
    success: '〇〇しました',
    invalidate: ['/foos'], // 影響する取得をパスの前方一致で取り直す（admin.md 第 4 節の表）
  })
  return (
    <div className="space-y-3">
      <ConfirmDialog
        confirmLabel="〇〇する"
        description="元に戻せません。"
        onConfirm={() => close.mutate({ params: { path: { fooId } } })}
        title="〇〇しますか？"
        trigger={<Button disabled={close.isPending}>〇〇する</Button>}
      />
      {/* 失敗は toast ではなく操作した場所に出す */}
      <ErrorMessage error={close.error} />
    </div>
  )
}
```

- 操作が複数あれば `const { pending, error, resetErrors } = useMutationGroup(close, reopen)`。例は `features/product/components/VariantSection.tsx`。
- 詳細の画面は `const { fooId = '' } = useParams()` と `$api.useQuery('get', '/foos/{fooId}', { params: { path: { fooId } } })`。
- フォームは React Hook Form。サーバーの `errors[]` を欄に出すのは `api/formErrors.ts` の `setServerFieldErrors`。画面での検証は必須の未入力だけ。
- 列挙の表示名は `lib/labels.ts`（または機能の `*.ts`）に置き、画面に文字列を直接書かない。

### 結合テスト（`pages/FooPage.test.tsx`）

書き方は admin.md 第 10 節。要点：`server.use(http.get('/foos', ({ response }) => response(200).json({...})))`、`const { user } = renderApp('/foos')`、要素は役割と名前で探す、呼ばれる API はすべてハンドラーを渡す、テスト名に AC 番号。

## 画面を足し終えたら

- 利用者に、確かめる URL を全部挙げて伝える（店舗画面は `http://localhost:3000/...`、管理画面は `http://localhost:5173/...`。動的な部分は実際に開ける ID にする）。新しい画面は、メニューやヘッダーにリンクを足さない限りどこからも辿れないので、辿り方（メニューのどれか、どの画面のどのリンクか）も添える。
- あわせて、何を足したか（ルート、メニュー・ヘッダーのリンク、API の呼び出し）を短く書く。リンクを足していない画面は、足すかを利用者に確かめる。

出典：この節は [Ginz9013/t3-flare](https://github.com/Ginz9013/t3-flare) の site-builder（`references/build-features.md` の「Report the routes to the user」）を、このリポジトリ向けに書き直したもの（MIT。`../LICENSE-t3-flare`）。
