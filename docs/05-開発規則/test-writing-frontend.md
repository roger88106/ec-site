# フロントのテストの書き方

画面と E2E のテストを書く・レビューするときの規則。共通の規則（テストの種類と基準、テスト名、待ち方）は [テストの書き方](test-writing.md)。

## 1. 画面に見えるもので探す

結合テストと E2E は、役割と名前で要素を探す（`getByRole('button', { name: '保存' })`、`getByLabel('名称')`）。CSS のクラス、`data-testid`、内部の状態、xpath、並びの 1 つ目は使わない。名前がなければ、画面に支援技術向けの名前を追加する。

- E2E は、位置で選ぶメソッド（`.first()`・`.nth()`・`.last()`）と、`locator` のセレクターの中の位置（`nth=` など。一覧は `scripts/ci/frontend-rules.mjs`）を使わない。名前や親の要素（行・領域）で絞る。
- E2E でボタンを名前で探すときは、`exact: true` を付けた文字列か、`^` と `$` で固定した正規表現（金額のように変わる値を含む名前）で書く（`getByRole('button', { name: /^注文する（[\d,]+ 円）$/ })`）。名前を変数で渡すときは文字列にして `exact: true` を付ける（正規表現の変数は部分一致になる）。
- 自分たちで作っていない画面（IdP）の要素の選び方は、`e2e/src/auth.ts` だけに置く。
- 詳細の項目の値（`dd`）は Biome の検査が名前を認めないので、E2E では `e2e/src/detail.ts` だけに、結合テストでは補助だけに（一覧の行を探す書き方も）置く。テストのファイルは補助を呼ぶ。
- Testing Library は画面の側の空白（全角を含む）を半角 1 つにそろえるが、検索する文字列はそろえないので、その文字列の空白は半角で書く（`'全 45 件 2 / 3 ページ'`）。
- `toBeTruthy`・`toBeDefined` を使わない。要素があることは `toBeInTheDocument`（E2E は `toBeVisible` など）で、値は型や部分的な構造（`expect.any(…)`、`toMatchObject` など）で確認する。理由：`getBy…` は見つからないと先に失敗し、`queryBy…` の `null` は `toBeDefined` を通るため。
- 表示のコンポーネントを HTML にして確認するときは、HTML の属性や要素の並びに依存する正規表現・文字の一致を使わず、役割と名前で探して jest-dom で確認する。例外は、描画の結果が空文字であることの確認（`expect(renderMarkup(…)).toBe('')`）。

理由：画面を再作成しても振る舞いが同じならテストが壊れず、並びや名前の似た別の要素に一致して通ることがないようにするため。

確認方法：

- CSS のクラスの確認、`toBeTruthy`・`toBeDefined`、`getAllBy…` などの結果を `[数字]`・`.at(数字)` で選ぶこと：フロントのテストと E2E で自動
- 画面のテストの `data-slot`・`querySelector`、HTML の属性の並びに依存する正規表現：自動（`scripts/ci/frontend-rules.mjs`）
- E2E の位置で選ぶメソッドとボタンの名前の完全一致：自動（同じ script）
- その他の探し方：レビュー

## 2. API の差し替えは型付きの MSW

管理画面の結合テストと、店舗画面のサーバーの処理（サーバーアクション、API の接続）とページのテストは、`src/test/` の `http`（openapi-msw）で OpenAPI の型に合ったハンドラーを作る。既定のハンドラーは置かず、テストごとに `server.use(...)` で渡す。

契約にない応答（通信の失敗、JSON でない本文）だけは、型のない msw のハンドラーで作り、`as never` などを使わない。複数のテストで使うものは `src/test/server.ts` の補助（例：`networkError`）にし、型付きの `http` を使わない理由をコメントに書く。

理由：契約にない応答を作る場所を限り、契約の応答と区別できるようにするため。

確認方法：自動（型チェック）

## 3. E2E のデータに実行ごとの印

E2E で作るデータの品番・名称に、実行ごとの印（`E2E-<印>-…`）を付ける（`e2e/src/data.ts`）。

理由：前の実行や手動で作ったデータと混ざらないようにするため。

確認方法：レビュー

## 4. テストの関数は `test` にそろえる

フロントのテストの関数は `test` を使い、`it` を使わない。`describe` でまとめてよい。

理由：どのファイルでも同じ名前で検索できるようにするため。

確認方法：レビュー

## 5. 複数のテストで使うものは、テストの補助に置く

複数のテストで使うデータ・API の差し替え・操作・確認は、各画面のテストの補助の置き場所に置き、テストのファイルで再定義しない。補助のファイルは、データ、API の差し替え、操作のように役割ごとに分ける。共有パッケージ（`frontend/shared`）には補助を置かない。

| 画面 | 補助の置き場所 |
|------|----------------|
| 管理画面 | `frontend/admin/src/test/` |
| 店舗画面 | `frontend/store/src/test/` |
| E2E と撮影（`e2e/tests/`、`e2e/showcase/`、`e2e/docs/`） | `e2e/src/` |

理由：同じデータと手順の修正を 1 か所で済ませ、テストごとに食い違わないようにするため。

確認方法：レビュー

## 6. 型を回避する変換を使わない

テストは型を回避する変換（`as never`、`as unknown as`）を使わず、型に合う値を補助で作る。ライブラリの型が合わず避けられないときだけ、理由をコメントに書いて使う。

理由：「API の差し替えは型付きの MSW」が禁じる契約にない応答を、型付きの `http` を通さずに作れてしまうため。

確認方法：理由のコメントのない `as never`・`as unknown as` は自動（`scripts/ci/frontend-rules.mjs`）、理由が妥当かはレビュー

## 7. 要素のプロパティ・属性・状態を jest-dom で確認する

要素のプロパティや属性（`.disabled`、`getAttribute(…)`、`.value`）を `toBe` で比べず、jest-dom の `toBeDisabled`・`toHaveAttribute`・`toHaveValue` などで確認する。状態（表示、選択、フォーカスなど）も `toBeVisible`、`toBeChecked`、`toHaveFocus` などで確認する。

理由：失敗したときの表示が分かりやすく、支援技術から見える状態で確認するため。

確認方法：受け手が要素のもの（`getBy…` の結果やそれを入れた変数など）のプロパティ・属性は自動（`scripts/ci/frontend-rules.mjs`）、ほかはレビュー

## 8. `fireEvent` を直接使わない

テストのファイルで `fireEvent` を直接使わず、`userEvent` で操作する。`userEvent` で起こせないイベント（入力できない種類の入力欄への値の設定、ブラウザーが起こすイベントなど）だけは使ってよく、理由を呼び出しごとに、同じ行か直前の行のコメントに書く。

理由：`fireEvent` は利用者の操作の一連のイベント（フォーカス、キー入力など）を再現せず、実際には起きない状態でも成功することがあるため。

確認方法：理由のコメントのないものは自動（`scripts/ci/frontend-rules.mjs`）、理由が妥当かはレビュー

## 9. 環境変数は `vi.stubEnv` で変える

テストで環境変数を変えるときは `vi.stubEnv` を使い、`process.env` に代入しない。

理由：`process.env` への代入はテストの後も残り、他のテストの結果に影響するため。

確認方法：レビュー

## 10. 組み込みの名前をシャドーイングしない

フロントのテスト・テストの補助・E2E（`e2e/` の下の全ての TypeScript）では、定数・変数（`const`・`let`）の名前を、グローバルの名前と同じにしない。対象は、TypeScript の lib（ES と DOM）が宣言する名前（`URL`、`screen`、`location`、`name`、`status` など）。関数の引数は対象外。

理由：変数の宣言を忘れたときに、エラーにならずにグローバルの値が使われ、意図しない値でテストが通るため。

確認方法：自動（`scripts/ci/frontend-rules.mjs`）

## 11. テストとテストの間に空行を置く

フロントのテスト・テストの補助・E2E では、テスト（`test.each(`・`test.skip(` などを含む）とテストの間、`describe` とその前後のテストの間には、空行を置く。`test.beforeEach(`・`test.use(`・`test.step(` などは対象にしない。

理由：テストの境目を、一目で分かるようにするため。

確認方法：自動（`scripts/ci/frontend-rules.mjs`）

## 12. 入力のキーの間で待たない

管理画面の `renderApp` が返す `user` は、入力のキーの間で待たない（`userEvent.setup({ delay: null })`）。キーの間の非同期の処理（キーごとの検索など）を確認するテストだけは、既定の delay の `userEvent.setup()` を使う。`renderApp` を使わないテストの `userEvent.setup()` は対象外。

理由：既定では 1 文字ごとにタイマーで待ち、入力の多いテストがタイムアウトで失敗しやすくなるため。

確認方法：レビュー

## 13. 店舗画面のテストは、対象ごとの方法で書く

店舗画面（`frontend/store`）のテストは、次の表の方法で、`frontend/store/src/test/` の補助を使って書く。各補助の使い方はファイルの TSDoc にある。

| 対象 | テスト |
|------|--------|
| `lib/`・`features/` の関数（判定、変換、整形、エラーの欄への対応） | Vitest の単体テスト。置き場所は、対象の横（`*.test.ts`） |
| 表示のコンポーネント（状態を持たないもの。クライアントコンポーネントの初期表示を含む） | `renderStatic` で HTML にして DOM に読み込み、役割と名前で探し、jest-dom で確認する。効果（`useEffect`）は実行されない |
| サーバーアクションの結果に応じた表示（エラーメッセージの出し方など） | 結果を props で受ける表示のコンポーネント（`OrderFormView`、`AddToCartFormView`）に結果を渡し、`renderStatic` で確認する。`useActionState` などは差し替えない |
| クライアントコンポーネントの、ブラウザーの中だけで終わる操作 | `renderInBrowser` を使い、jsdom で操作する。サーバーアクションを呼ぶ操作は、サーバーアクションのテストと E2E で確認する |
| ページ（サーバーコンポーネント）の、取得した内容に応じた表示 | ページの関数を呼び、`renderStatic` で DOM に読み込んで、役割と名前で探す。ログインの状態は `signInAs`、店舗 API は MSW で差し替える。置き場所は、ページの横（`app/**/page.test.tsx`） |
| サーバーの処理（サーバーアクション、`api/cart.ts`、`auth/` のコールバック、`proxy.ts`） | Node で直接呼ぶ。Next.js のモジュール（`next/headers` など）と `@/auth` は `src/test/` で置き換え、バックエンドは MSW（`msw/node`）。置き場所は、対象の横（`*.test.ts`） |

- サーバーアクションの結果に応じた表示は、送る関数の代わりを渡し、ボタンを押したときに渡す値を jsdom で確認してよい。結果を受けた後の振る舞い（誤りの欄へのフォーカスの移動など）は、jsdom で描画し、`rerender` で結果を変えて確認する（どちらも `OrderForm.test.tsx` の形）。API に送る内容は、サーバーアクションのテストと E2E で確認する。
- ブラウザーの中だけで終わる操作の例外は 2 つ。
  - 読むだけのサーバーアクション（お届け先の市区町村の一覧、郵便番号の検索。[店舗画面の構成](../02-設計/apps/store.md)の「お届け先の都道府県と市区町村」）は、`vi.mock` で結果を差し替えて、フォームの操作を確認する ※1。
  - サーバーアクションの結果の表示の遷移（同じ読み上げの領域が残り、前の文が消えるなど）は、`vi.mock` で差し替え、jsdom で操作して確認する（理由は[テストの書き方](test-writing.md)の「テストの種類」の注）。
- 確認の順（ログイン、ID の形）だけを確認するページのテストは、jsdom を使わずに Node でページの関数を呼び、移る先（`src/test/next.ts` の `redirectedTo`）と「見つからない」（`expectNotFound`）で確認する（`app/checkout/[checkoutId]/page.test.tsx`）。

※1：住所の補完は結果と入力の組み合わせが多く E2E では確認しきれず、読むだけなので差し替えても送る内容の確認に漏れが生じないため。

理由：対象ごとに確認できる範囲と手間が違うので、最小の方法で確認し、サーバーを通る流れは E2E に任せるため。

確認方法：レビュー

## 14. 管理画面の結合テストは、本物の描画で、API だけを差し替える

管理画面（`frontend/admin`）の結合テストは、画面を本物のルーター・キャッシュ・toast で描画し、API の応答だけを MSW で差し替えて、利用者と同じように操作する。補助は `frontend/admin/src/test/`（使い方はファイルの TSDoc）。

```tsx
// features/<機能>/pages/<画面>.test.tsx
server.use(
  http.get('/tax-categories', ({ response }) => response(200).json({ items: [...] })),
  http.put('/tax-categories/{taxCategoryId}/rates', ({ response }) =>
    conflict(response),
  ),
)
const { user } = renderApp('/tax-categories/<ID>')
await user.click(await screen.findByRole('button', { name: '保存' }))
expect(await screen.findByText('他の人が先に更新しました。再読み込みしてください')).toBeInTheDocument()
```

- 置き場所は、主に確認する画面（`pages/`）の横。
- リンクの見た目のボタン（共有の `LinkButton`）は、役割が `button` になる。
- 送った内容は、ハンドラーの中で `request.json()` を変数に取って確認する。
- バージョンの規則（[管理画面の構成](../02-設計/apps/admin.md)の「フォーム」）は、入力中に他の人が更新した後で、画面にデータを再取得させる（`inputs.ts` の `returnToTab()`）。入力が消えず、送るバージョンが変わらないことまで確認する。
- 管理 API の外へのリクエスト（ストレージへの PUT）は、`msw` の `http` で作る（`admin.yaml` の型がないため）。
- 画面の外で起きること（バックエンドの業務の規則、店舗画面への反映）は確認しない。

理由：利用者の操作の流れを本物に近い描画で確認し、API の呼び出しの契約とのずれを型で見つけるため。

確認方法：レビュー
