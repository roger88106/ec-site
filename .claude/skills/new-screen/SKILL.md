---
name: new-screen
description: ec-site のフロントに画面や操作を追加・修正するときの型。店舗画面（frontend/store）と管理画面（frontend/admin）の最小の例と、読むべき既存のファイル。
---

# 画面を追加するときの型

決まりの正本は [店舗画面の構成](../../../docs/02-設計/apps/store.md) と [管理画面の構成](../../../docs/02-設計/apps/admin.md)。
本書はその要点と、そのままコピーできる最小の例。迷ったら、次の正本を読む。

- [店舗画面の構成](../../../docs/02-設計/apps/store.md)の「API の呼び出し」から「結果の知らせ方」までと「画面を追加する手順」
- [管理画面の構成](../../../docs/02-設計/apps/admin.md)の「API の呼び出し」から「結果の知らせ方」までと「画面を追加する手順」
- [フロントのテストの書き方](../../../docs/05-開発規則/test-writing-frontend.md)の「管理画面の結合テストは、本物の描画で、API だけを差し替える」

共通の前提：

- 使う API が OpenAPI（`api/openapi/store.yaml`、`admin.yaml`）にあること。なければ止めて、plan と OpenAPI を先に修正する（開発の進め方の「仕様駆動開発」）。
- 画面の文言・コメントは日本語。コメントは何をするかを書き、決定の経緯はコミットメッセージに書く。
- 表示の文言は、コードに直接書かず文言ファイル（`messages/ja.json`）に置き、`t('キー')` で引いて、後ろに表示する内容をコメントで書く（フロントの書き方の「表示の文言とロケール」）。下の例の `foo.*` のキーは、文言ファイルに追加する。
- 表示の決まり（列挙の表示名、金額の整形など）は関数にして `lib/` か `features/<機能>/` に置き、単体テストを横に置く。
- 終わったら `scripts/dev/check-frontend.sh <store|admin>` を通し、プレビューで表示を確認する（skill `local-dev`）。

## 共有の部品（`frontend/shared`）

画面に表示や処理を書く前に、共有の部品を探す。

1. 書こうとする表示や処理（整形、表示名、リンクのボタンなど）に当たるものを、`frontend/shared/src/lib/`・`components/`・`ui/` のファイル名と export で探す（例：`git grep -n "^export" -- frontend/shared/src`）。
2. あれば import して使い、自分で同じものを書かない。import のパスは `@ec-site/shared/` の後ろに置き場所を続ける（例：`@ec-site/shared/lib/format`）。金額、数、日時の既定の書式で足りるときは、自分で `toLocaleString` や `Intl` を呼ばない。
3. ないものを 2 つの画面で使うときは、各画面に書かず `frontend/shared` に置く。置き場所と条件は [フロントの書き方](../../../docs/05-開発規則/frontend-coding.md)の「入れるもの・入れないもの」。

共有の文言は `frontend/shared/src/messages/ja.json` の `common` にある（置く条件は[フロントの書き方](../../../docs/05-開発規則/frontend-coding.md)の「表示の文言は文言ファイルに置く」）。色などの値は `frontend/shared/src/theme.css` のテーマから使い、画面に直接書かない。`frontend/shared/scripts/export-constraints.mjs`（OpenAPI の入力の制約を定数のファイルに出力する）は直接実行せず、各画面の `pnpm generate:api` から実行する。

## 店舗画面（`frontend/store`）

| 置き場所 | 中身 |
|----------|------|
| `src/app/<URL>/page.tsx` | サーバーコンポーネント。取得とコンポーネントの組み立てだけ |
| `src/features/<機能>/components/` | 画面のコンポーネント。操作のあるコンポーネントだけ `'use client'` |
| `src/actions/<資源>.ts` | サーバーアクション（`'use server'`）。変更系の API はここからだけ呼ぶ |
| `src/api/`、`src/lib/actionResult.ts` | API の呼び出し、`unwrap()`（404 は `notFound()`、それ以外の失敗は `ApiError`）、カートの Cookie（`writeCartId()` はアクションだけ）、`ActionResult`。export の名前は各ファイルを読む |

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

- 結果の表示をテストするフォームは、`useActionState` を呼ぶコンポーネントと、結果を props で受ける表示のコンポーネントに分ける（例：`OrderForm` と `OrderFormView`）。テストは表示のコンポーネントに結果を渡す。
- 結果の表示をテストしないフォームは、分けなくてよい。
- 同じ読み上げの領域が残るなど、結果の表示の遷移を確認するときは、分けずにサーバーアクションを `vi.mock` で差し替え、jsdom で操作する（`CancellationRequestForm.test.tsx`。テストの書き方の「テストの種類と基準」）。

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
    // 失敗の読み上げの領域は、送る前から描画しておく（店舗画面の構成の「結果の知らせ方」）。領域が最後の子なので、親は flex と gap で並べる（components/ActionError.tsx のコメント）
    <form action={formAction} className="flex flex-col items-start gap-3">
      <input name="fooId" type="hidden" value={fooId} />
      {/* 入力欄。誤りは欄の下に表示し、aria-describedby と aria-invalid で入力欄に結び付ける（OrderForm の Field） */}
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
| `src/api/` | `$api`、更新の操作（`useApiMutation.ts`、`useMutationGroup.ts`）、再取得するパス `ApiPathPrefix`（`invalidate.ts`。新しいパスは [管理画面の構成](../../../docs/02-設計/apps/admin.md)の「API の呼び出し」の表にも追加する）。名前は各ファイルを読む |
| `src/components/` | 共通のコンポーネント。名前はフォルダーを読む |
| `src/hooks/useVersionedForm.ts` | バージョンを持つリソースのフォーム（409 の扱い。[管理画面の構成](../../../docs/02-設計/apps/admin.md)の「フォーム」） |

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
    invalidate: ['/foos'], // 影響する取得をパスの前方一致で再取得する（管理画面の構成の「API の呼び出し」の表）
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

書き方は [フロントのテストの書き方](../../../docs/05-開発規則/test-writing-frontend.md)の「管理画面の結合テストは、本物の描画で、API だけを差し替える」。要点：`server.use(http.get('/foos', ({ response }) => response(200).json({...})))`、`const { user } = renderApp('/foos')`、要素は役割と名前で探す、呼ばれる API は全てハンドラーを渡す、テスト名に AC 番号。

## 画面を追加し終えたら

- 利用者に、確認する URL を全て挙げて伝える（店舗画面は `http://localhost:3000/...`、管理画面は `http://localhost:5173/...`。動的な部分は実際に開ける ID にする）。新しい画面は、メニューやヘッダーにリンクを追加しない限りどこからも到達できないため、到達する経路（メニューのどれか、どの画面のどのリンクか）も添える。
- あわせて、何を追加したか（ルート、メニュー・ヘッダーのリンク、API の呼び出し）を短く書く。リンクを追加していない画面は、追加するかを利用者に確認する。

出典：この節は [Ginz9013/t3-flare](https://github.com/Ginz9013/t3-flare) の site-builder（`references/build-features.md` の「Report the routes to the user」）を、このリポジトリ向けに書き換えたもの（MIT。`../LICENSE-t3-flare`）。
