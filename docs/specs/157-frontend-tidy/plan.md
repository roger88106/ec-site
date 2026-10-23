# Plan 157：管理画面と共有のパッケージの部品とテストの書き方を整える

ステータス：草案　対象：[spec.md](spec.md)

前提：spec 157 が確定した。文書の書き方は skill `docs`（[SKILL.md](../../../.claude/skills/docs/SKILL.md)）と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`（[SKILL.md](../../../.claude/skills/review/SKILL.md)）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 共有の部品の案内 | 第 1 節 | DS-157-1（DC-157-1、2、35） |
| 数の整形 | 第 2 節 | DS-157-2（DC-157-3〜5） |
| 管理画面の型と構成 | 第 3 節 | DS-157-3（DC-157-6〜9、36） |
| テストの規則とテストの補助 | 第 4 節 | DS-157-6（DC-157-29〜34）、DC-157-13、28 |
| 探し方と API の差し替え | 第 5 節 | DS-157-4（DC-157-10〜18） |
| テストの名前と確認 | 第 6 節 | DS-157-5（DC-157-19〜27） |
| 本 spec で扱わない推奨の指摘 | 第 7 節 | spec 第 7 節の 1 |
| 並行する spec と同じファイル | 第 8 節 | DN-157-7 |
| 段階と確認の方法 | 第 9 節、第 10 節 | 全て |
| 利用者に確認すること | 第 11 節 | 全て |

DC の列は「157-1」の形で書く。spec の番号 157 を付けた DC-157-1 の略である。

共通の決定：

- spec 158 には、本 spec の修正を待って加える検査が 2 つある（spec 158 の DC-158-50、51）。対象の違反は、内部の属性と `querySelector` で探す箇所と、空白で区切った AC の番号で、段階 1 で修正する。理由：本 spec のどの段階とも依存がなく、spec 158 の待ち時間を短くできるため。段階 1 は、先に別の pull request で `develop` に入れる（第 11 節の 1、利用者の決定）。
- 本 spec は見える振る舞いを変えないので、TDD の「レッドから」は、新しい関数（共有の数の整形、テストの補助）だけに当てはまる。修正するテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-157-4）。手順は、元のファイルをスクラッチパッドにコピーしてから手動で変更し、テストの後に戻す。手元の break-check の script は使わない。理由：この script はコマンドを cmd.exe で実行し、Gradle では偽の失敗を出したことがあり、pnpm での結果は確認していないため。
- spec 17 で、フロントのカバレッジの行と分岐に基準が導入された（[テストの書き方](../../rules/test-writing.md) 第 1.2 節）。管理画面、店舗画面、共有のパッケージの許容の幅は 0 である。本 spec はテストを削除・統合するので、各段階の最後に `node scripts/ci/report/coverage-baseline.mjs` で 3 つの対象を確認する。下回ったら、削除したテストが確認していた分岐を、残すテストで確認する。基準を下げない。上回ったら、最後の段階で `--write` で基準を更新する。
- 実装は実装役（汎用）に、1 タスクずつ任せる。`[リスク]` のタスクはない（金額の整形は表示の書式だけを変え、値と出力の文字は変えない。単体テストで出力の一致を確認する）。
- 段階の間では、確認のために中断しない。
- plan と tasks は一緒に利用者が確認する。
- 文書の本文は、日本語の要点を渡して、文書を書くサブエージェントに書かせる。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。
- テストのファイルを変えるタスクは、そのファイルで見つけた規則の違反のうち、本 spec の DC に当たるものを全て修正する。違反の一覧はレビューの結果（`/.local/review/202610231200-157-frontend-tests/`）にあり、実装役にはファイルごとの指摘を渡す。

## 1. 共有の部品の案内（DS-157-1：DC-157-1、2、35）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 157-1 | skill に「共有の部品」の節を追加し、共有のパッケージのコンポーネントと関数を表にする（置き場所、役割、使う場面） | `.claude/skills/new-screen/SKILL.md` | レビュー（共有のパッケージのファイルの一覧と照合する） |
| 157-2 | 同じ節に、画面に表示や処理を書く前に、表から共有の部品を探して使う手順を書く。表にないものを 2 つの画面で使うときは、[フロントの書き方](../../rules/frontend-coding.md) 第 5.1 節に従い共有に置くことも書く | 同上 | レビュー |
| 157-35 | 共有のパッケージに置いているものの一覧に、形の判定、shadcn/ui の共有のコンポーネント、API の生成で使う script を追加する | [フロントのアーキテクチャ](../../design/frontend-architecture.md) 第 4 節 | レビュー（共有のパッケージのディレクトリと照合する） |

- 表の対象は、共有のパッケージの `src/components/`、`src/lib/`、`src/ui/`、`scripts/` の全てのファイルである。テーマと共有の文言は 1 行ずつにする。
- skill の書き方は skill `writing-for-agents` に従う。AGENTS.md の skill の表は変えない（skill を追加しないため）。
- 共有の数の整形（第 2 節）を表に含めるので、段階 2 の第 2 節の後に書く。

## 2. 数の整形（DS-157-2：DC-157-3〜5）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 157-3 | 共有のパッケージに、既定の数の整形の関数を置く。既定の数の書式を作る箇所は、この関数のファイルの 1 か所にする | `frontend/shared/src/lib/format.ts`（`formatNumber`）、`format.test.ts` | 単体テスト（`[テスト]` を先に書き、レッドを確認する）、レビュー（`git grep "new Intl.NumberFormat(LOCALE)"` が 1 件） |
| 157-4 | 共有の金額の整形（円、価格）は、共有の数の整形を使う。管理画面の数の整形を削除し、呼び出し元は共有を使う。店舗画面の税込・税抜の金額の文字も共有を使う | 共有 `lib/format.ts`。管理画面 `lib/format.ts`、`lib/format.test.ts`、`features/report/pages/VariantSalesPage.tsx`、`components/ChartValueTooltip.tsx`（コメント）。店舗画面 `lib/format.ts` | 今の単体テスト（出力の文字が変わらない）、管理画面の吹き出しの結合テスト |
| 157-5 | 符号付き（在庫の増減）、率（税率）、短縮（グラフの軸）の書式は、管理画面に残す | 変更しない：管理画面 `lib/delta.ts`、`lib/rate.ts`、`features/report/report.ts` | レビュー |

- 管理画面の数の整形のテストは、共有のテストに移す。
- [管理画面の構成](../../design/apps/admin.md) 第 2 節の `lib/format.ts` の説明から「数の整形」を削除する（段階 2）。

## 3. 管理画面の型と構成（DS-157-3：DC-157-6〜9、36）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 157-6 | 自分の権限の一覧の戻り値を、状態（`loading`・`failed`・`ready`）を判別の項目に持つ 3 つの型の合併にする。失敗の中身、取得中か、再試行は `failed` の型だけ、権限の判定は `ready` の型だけが持つ（※1） | 管理画面 `auth/permissions.ts`。呼び出し元：`app/Layout.tsx`、`auth/RequirePermission.tsx` | 型チェック、今の結合テスト（`ReportPermission.test.tsx` など） |
| 157-7 | レポートの条件の項目（粒度、送料を除く、商品の指定）の props の型を、条件のフォームのファイルで 1 つ定義して export する。レポートの共通の画面の 2 つのコンポーネントは、その型との交差型で使う | 管理画面 `features/report/components/ReportConditionForm.tsx`、`ReportPage.tsx` | 型チェック、今の結合テスト |
| 157-8 | 商品の項目の制約の組を、操作のスキーマ（商品名と説明の最大の長さ、税抜価格の最小）から作る関数を、商品の項目のフォームのファイルに置く。登録の画面と基本情報のカードは、それぞれのスキーマを渡す | 管理画面 `features/product/components/ProductFields.tsx`、`BasicInfoCard.tsx`、`pages/ProductCreatePage.tsx` | 型チェック、`input-constraints`、今の結合テスト |
| 157-9 | 「middleware」（3 か所）を「ミドルウェア」にする | [管理画面の構成](../../design/apps/admin.md) 第 4 節、[管理画面の構成](../../design/apps/admin.md) 第 10.1 節 | `git grep -n middleware docs/design/apps/admin.md` が 0 件 |
| 157-36 | ディレクトリの図を今のファイルに合わせる。`api/` の変換と hook（フォームのエラー、無効化、変更の hook）、`lib/` の関数（配列、品番、増減、ログイン、率、クラス名）、`hooks/` の画面の幅の hook を追加し、ない `lib/labels.ts` の案内を「2 つ以上の機能で使う表示名は共有の注文の表示名か、使う機能の `labels.ts`」に修正する | [管理画面の構成](../../design/apps/admin.md) 第 2 節 | レビュー（`frontend/admin/src` の一覧と照合する） |

※1：今は全ての状態で同じ型を返し、取得中と失敗でも、権限の判定の関数が false を返す。呼び出し元は状態を先に判定しているので、判別の型にすると、判定の前に権限を読む誤りを型チェックで防げる。

- メニュー（`Layout.tsx`）は、`failed` で全てのメニュー項目、`ready` で権限のあるメニュー項目を表示し、`loading` でメニュー項目を表示しない（spec 134 の DC-134-11、12。振る舞いは変えない）。
- 図の個々のファイルの説明は、今の図の書き方（ファイル名と 1 行の説明）にそろえる。全体の図の再検討はしない。

## 4. テストの規則とテストの補助（DC-157-13、28〜34）

### 4.1 規則

| DC | 決定 | 置き場所 |
|----|------|----------|
| 157-28 | テストの関数は `test` にそろえる（spec の Open questions 3）。`describe` は使ってよい | [テストの書き方](../../rules/test-writing.md) 第 4 節に新しい節 |
| 157-28 | 複数のテストで使うデータ、API の差し替え、操作、確認は、各画面のテストの補助の置き場所に置き、テストのファイルで再定義しない。補助のファイルは役割ごとに分ける（※1） | 同上 |
| 157-28 | テストは型を回避する変換（`as never`、`as unknown as`）を使わない。型に合う値を補助で作る。ライブラリの型が合わず避けられない所だけ、理由をコメントに書いて使う | 同上（第 4.2 節の理由を広げる） |
| 157-13 | 結合テストで詳細の項目の値と一覧の行を探す書き方は、テストの補助だけに置く（E2E の `e2e/src/detail.ts` と同じ考え方） | 同上（第 4.1 節） |

※1：置き場所は、管理画面が `frontend/admin/src/test/`、店舗画面が `frontend/store/src/test/` である。共有のパッケージは補助を置かない（テストが 7 ファイルで、共通の値がないため）。

- 規則の追加は、補助を作る前（段階 3 の最初）に行う。規則の一覧の表に、確認方法「レビュー」で行を追加する。script による検査は #158 と後の issue が扱う（DN-157-6）。
- [管理画面の構成](../../design/apps/admin.md) 第 9 節の結合テストの補助の表に、追加する補助を書く。

### 4.2 補助

| 画面 | 補助 | 役割 | 使う所 | DC |
|------|------|------|--------|----|
| 管理画面 | `test/queries.ts`：詳細の項目の値、一覧の行 | 項目名から値の要素を、名前から行を返す。行は `getByRole('row', { name })` で探す | 注文の詳細、注文の一覧、商品の一覧、カテゴリ、レポートの 3 画面、税区分 | 157-13 |
| 管理画面 | `test/download.ts`：ダウンロードの記録 | URL の作成とリンクのクリックを `vi.spyOn` で差し替え、保存名と本文を返す。差し替えは `restoreAllMocks` で戻る（※2） | レポートの 3 画面 | 157-29、30 |
| 管理画面 | `test/server.ts`：409 の応答、型付きの問題の応答 | 更新の競合の応答を返す。条件で応答を変えるハンドラーが、`Response` でなく型付きの応答を返せるようにする | 商品の編集、カテゴリ、税率、税区分、在庫、注文の詳細 | 157-15、32 |
| 管理画面 | `test/fixtures.ts`：税区分、商品の一覧の項目、カテゴリの節、在庫数の応答 | 今の `taxCategory()` を使い、ファイルごとの再定義を削除する。足りないデータを追加する | 税率、`App.test.tsx`、商品の一覧、カテゴリ、商品の編集 | 157-31、32 |
| 店舗画面 | `test/fixtures.ts`：注文、お届け先、バリアントと軸、カートの ID | 今のファイルごとの定義を移す | 注文の画面と 3 つの欄、商品の表示とバリアントの選択、カートの選択とカートの ID | 157-31 |
| 店舗画面 | `test/env.ts`：本番の環境変数の一式 | 3 つのファイルの同じ定義を移す | 認証の設定、公開の URL、起動時の確認 | 157-31 |
| 店舗画面 | `test/server.ts`：通信の失敗 | 通信の失敗の応答を返すハンドラーを作る | カート、地域、認証、API の接続 | 157-16、32 |
| 店舗画面 | `test/stderr.ts`：標準エラー出力の記録 | 書いた行を JSON の配列と文字で返す | 結果の包み、API の接続、カートの API、ログ、起動時の確認 | 157-33 |
| 店舗画面 | `test/next.ts`：見つからないことの確認 | not found の digest を 1 か所で確認する | 結果の包み、注文手続きの画面、結果 | 157-23 |

※2：今は組み込みの `URL` に関数を直接代入するので、`unstubAllGlobals` で戻らず、後のテストに残る。jsdom にない関数は、店舗画面と同じく setup で補う。

- 管理画面のテストのクリーンアップは `setup.ts` で実行している。対象は `server` と通知である。`restoreAllMocks` を setup に追加し、各ファイルの重複を削除する。店舗画面は setup にすでにあるので、4 ファイルの重複を削除する（DC-157-34）。
- 補助には説明のコメント（TSDoc）を書く（[コメントの書き方](../../rules/comments.md)）。

## 5. 探し方と API の差し替え（DS-157-4：DC-157-10〜18）

| DC | 決定 | 対象（テストのファイル） | 確認方法 |
|----|------|--------------------------|----------|
| 157-10、11 | 内部の属性で探す 5 か所を、役割と名前で探す形にする。名前のない要素は、画面に支援技術向けの名前を追加する（状態のバッジ、出荷のカード）。入力欄のエラーは、エラーの文を入力欄に関連付けて、入力欄の説明で確認する（※1） | 管理画面 `OrderDetailPage.test.tsx`、`ProductCreatePage.test.tsx`。画面：注文の詳細の状態のバッジと出荷のカード、商品の登録の入力欄 | 結合テスト、`git grep -n "data-slot\|querySelector" -- '*.test.tsx'` が 0 件 |
| 157-12 | DOM の位置と HTML の要素の種類で探す所（約 25 か所）と、並びのインデックス（約 10 か所）を、補助（第 4.2 節）か役割と名前で探す形にする | 管理画面：注文の詳細、注文の一覧、カテゴリ、在庫、商品の編集、商品の一覧、レポートの 3 画面 | 結合テスト、レビュー |
| 157-14 | 見た目に依存する確認を修正する。金額の内訳の Tailwind のクラスと区切り線の確認は、見た目の種類ごとに見える文言と順の確認にする。取り消し線と代替画像の確認は削除する。選べないことと購入できないことは、既存の確認（無効、理由の文）で確認する | 共有 `AmountsSummary.test.tsx`。店舗画面 `VariantPicker.test.tsx`、`CartItemControls.test.tsx`、`ProductCard.test.tsx` | 単体テスト、カバレッジの基準 |
| 157-15 | 管理画面の `as never` の 21 か所を、型付きの応答にする。条件で応答を変えるハンドラーは、補助（第 4.2 節）の型付きの応答を返す | 管理画面：カテゴリ、在庫、注文の詳細、商品の登録・編集・一覧、税区分、税率 | 型チェック、`git grep -n "as never" -- 'frontend/admin/*.test.ts*'` が 0 件 |
| 157-15 | 店舗画面の API の接続のテストの、型のないハンドラーのうち、契約にある応答（200、404、502 の問題の応答）を型付きにする | 店舗画面 `api/client.test.ts` | 型チェック |
| 157-16 | 契約にない応答（通信の失敗、JSON でない本文）は、型のないハンドラーに残し、理由を 1 行で書く | 店舗画面 `api/client.test.ts`、通信の失敗の補助 | レビュー |
| 157-17 | 契約の文書は変えない（※2） | なし | 157-15 の型チェック |
| 157-18 | テストの値の型を回避する変換で合わせる所（店舗画面の認証の 8 か所、proxy の 2 か所、画面の props の 2 か所、要求の本文、`closest(...) as HTMLElement`、要素の型の変換、管理画面のログインの利用者と送った本文の `unknown`）を、型に合う値を作る補助か、型の絞り込みにする。ライブラリの型が合わず避けられない所は、理由をコメントに書く | 店舗画面：`auth/index.test.ts`、`auth/cookiePrefix.test.ts`、`proxy.test.ts`、`app/checkout/[checkoutId]/page.test.tsx`、`app/orders/[orderId]/page.test.tsx`、`actions/cart.test.ts`、`actions/checkout.test.ts`、`auth/loginRedirect.test.ts`、`test/next.ts`、バリアントの選択と注文のフォーム。管理画面：`AuthGate.test.tsx`、商品の編集、注文の詳細、カテゴリ | 型チェック、レビュー |

※1：入力欄を囲む要素は shadcn/ui の生成物なので変えない（DN-157-5）。関連付けは画面の側で行う。

※2：テストが使う状態コードを契約と照合した（2026-10-23）。全て契約にある。契約と違うのは、商品の更新に 200 と本文を返すテスト（契約は 204）と、出荷の登録に注文の出荷の型を返すテストで、どちらもテストの誤りなので、テストを契約に合わせる。

## 6. テストの名前と確認（DS-157-5：DC-157-19〜27）

| DC | 決定 | 対象 | 確認方法 |
|----|------|------|----------|
| 157-19 | 関数名だけの名前と、名詞だけの名前を、振る舞いの文にする | 管理画面 `imageUpload.test.ts`、`report.test.ts`、`order/labels.test.ts`、`report/labels.test.ts`、`product/product.test.ts`。店舗画面 `variantSelection.test.ts`、`orderState.test.ts`、`ProductCard.test.tsx`。共有 `orderLabels.test.ts` | レビュー |
| 157-20 | 空白で区切った AC の番号（5 か所）を読点で区切る。AC 一覧を再生成する | 管理画面 `orderFilter.test.ts`、`OrderDetailPage.test.tsx`、`OrderListPage.test.tsx`（2）。店舗画面 `features/order/labels.test.ts` | `ac-check` |
| 157-21 | まとまりの名前だけにある AC の番号を、テストの名前の先頭に移す。注文の詳細のコメントだけにある AC-142 は、テストの名前に入れる | 店舗画面 `remainingTime.test.ts`、`orderListQuery.test.ts`、`orderState.test.ts`、`features/order/labels.test.ts`。管理画面 `OrderDetailPage.test.tsx` | `ac-check`、AC の文との照合 |
| 157-22 | 名前と確認が合わない 3 つのテストを修正する。数量を出さないことは確認を追加する。在庫の調整の履歴の「空」は、その欄が空であることを確認する | 店舗画面 `QuoteItemSummary.test.tsx`、`lib/labels.test.ts`。管理画面 `StockPage.test.tsx` | レビュー、テストの変更で失敗しうることの確認 |
| 157-23 | 引数のない例外の確認を、種類か内容の確認にする。not found は補助（第 4.2 節）で確認する | 店舗画面 `withActionResult.test.ts`、`cartId.test.ts`、`api/result.test.ts`、`app/checkout/[checkoutId]/page.test.tsx` | レビュー |
| 157-24 | 存在するだけの確認を修正する。見つからなければ失敗する検索の結果への `toBeTruthy` と、重複した `expect` は、状態の確認に置き換えるか削除する。件数が 1 以上の確認は件数の確認にする。部分一致の確認は、他の項目も含めた一致にする | 店舗画面 `VariantPicker.test.tsx`、`RemainingTime.test.tsx`、`CancellationRequestForm.test.tsx`、`ShippingAddressDetails.test.tsx`、`lib/server/log.test.ts`、`SiteHeader.test.tsx`。管理画面 `ChartValueTooltip.test.tsx`、`ProductEditPage.test.tsx`、`ProductListPage.test.tsx`、`ReportPermission.test.tsx` | レビュー |
| 157-25 | 郵便番号から住所を補わないことの確認（AC-256）は、検索の結果の処理が終わるのを待ってから確認する | 店舗画面 `OrderForm.test.tsx` | 補う処理を一時的に変更して失敗することの確認 |
| 157-26 | 対象と違うファイルにあるテストを、対象のテストのファイルに移す | 店舗画面：注文の状態のバッジ（→ `OrderStatusBadge.test.tsx`）、注文の操作のサーバーアクション（→ `actions/order.test.ts`）、追跡の ID（→ `lib/server/trace.test.ts`）、カテゴリの一覧（→ `CategoryList.test.tsx`） | レビュー |
| 157-27 | 同じ振る舞いを確認するテストを 1 か所にまとめる | 共有 `format.test.ts`（関数ごとの describe に寄せる）。店舗画面 `lib/login.test.ts`（危険な入力の網羅は共有のテストで確認し、トップに戻すことだけを残す）、`withActionResult.test.ts`、`auth/publicUrl.test.ts` と `auth/config.test.ts`、`auth/index.test.ts` と `auth/cookiePrefix.test.ts`。管理画面 `CategoryPage.test.tsx`（AC-9・270、AC-12 の 2 つ）、`OrderDetailPage.test.tsx`（AC-268、AC-186 は `test.each`） | レビュー、カバレッジの基準 |

- テスト名の AC は、AC の文と照合してから書く（[テストの書き方](../../rules/test-writing.md) 第 2.2 節）。照合した結果は、該当のタスクの commit の本文に書く。
- テストの関数を `test` にそろえる変更（約 25 ファイル）は、画面ごとに 1 タスクにし、他の変更と分ける。理由：差分が機械的で大きく、他の変更の差分が埋もれるため。

## 7. 本 spec で扱わない推奨の指摘

spec の DC に当たらない推奨の指摘は、本 spec で扱わず、最後の起票のまとめの案に入れる（spec の Open questions 1 の案 A）。

| 指摘 | 理由 |
|------|------|
| 1 つのテストに複数の振る舞い（409 の検出、入力の保持、再読み込み、再保存）を詰めた長いテストを分ける | テストの構成の変更で、DC にない |
| 画面の URL の定数の名前が組み込みの `URL` をシャドーイングする | 名前の一貫性で、DC にない |
| テストの間の空行の欠落 | 書式で、整形の検査の対象 |
| 変更をまとめる hook のテストで `renderHook` を使わない | DC にない |
| ファイルの選択と日時の入力の操作の書き方の違い（`user.upload` と `fireEvent`、日時の入力の補助） | 操作の補助の一貫性で、DC にない |
| 店舗画面の HTML のテストの全面の書き換え、フォームのテストの React の差し替え | spec の Non-goals（#16 に残す） |
| 店舗画面のログイン状態と環境変数の作り方の違い（直接の代入と `signInAs`、`process.env` と `vi.stubEnv`、現在の秒の計算） | 書き方の一貫性で、DC にない |

## 8. 並行する spec と同じファイル

| spec | 同じファイル | 扱い |
|------|--------------|------|
| 158（CI の検査） | `docs/rules/test-writing.md`、`docs/guides/ci.md` | 後でマージする側が `develop` を取り込んで解消する（DN-158-5）。`scripts/ci/` は本 spec では変えない（DN-157-6） |
| 156（文書） | `docs/design/apps/admin.md`、`docs/design/frontend-architecture.md` | 同上。spec 156 は、この 2 つの記載の漏れを本 spec に移した |
| 155（バックエンドの構成） | なし | なし |
| 全て | `docs/design/acceptance-criteria.md`（テストの列） | 衝突したら `node scripts/ci/ac-check.mjs --write` で再生成する |

spec 158 と 156 には、plan と tasks の確認を求めるときに、issue 担当のセッションを通じて通知する。

## 9. 段階

| 段階 | ファイル | 内容 | DC |
|------|----------|------|----|
| 1 | [tasks/01-blocking.md](tasks/01-blocking.md) | spec 158 が待つ違反の修正（内部の属性と `querySelector`、空白で区切った AC） | 157-10、11、14 の一部、20 |
| 2 | [tasks/02-parts.md](tasks/02-parts.md) | 共有の数の整形、管理画面の型、構成とアーキテクチャの文書、skill の共有の部品 | 157-1〜9、35、36 |
| 3 | [tasks/03-helpers.md](tasks/03-helpers.md) | テストの規則の追加、テストの補助の作成 | 157-13、28〜34 の補助 |
| 4 | [tasks/04-admin-tests.md](tasks/04-admin-tests.md) | 管理画面のテストの修正 | 157-12、15、18〜27（管理画面） |
| 5 | [tasks/05-store-tests.md](tasks/05-store-tests.md) | 店舗画面と共有のパッケージのテストの修正 | 157-14〜16、18〜27（店舗画面と共有） |
| 6 | [tasks/06-review.md](tasks/06-review.md) | spec の最後のレビュー、起票のまとめの案、pull request の前のレビュー、アーカイブ | 全て |

- 各段階の最後のタスクは、段階のレビュー（profile「厚め」：一般の観点を汎用で 2 回、ファイルをまたぐ影響を高性能と汎用）と、全体の検査（第 10 節）の実行である。
- 段階 4・5 の修正のタスクは、テストのファイルが重ならない単位（機能ごと）に分け、`[P]` にする。段階 3 の補助を使うので、段階 3 の後に行う。
- 段階 6 は、spec の最後のレビュー（skill `review`、ファイルをまたぐ影響を含む、推奨も返す）、E2E、起票のまとめの案、pull request の前のレビュー、spec のアーカイブへの移動である。E2E を実行する理由：段階 1 と 4 で、注文の詳細と商品の登録の画面に支援技術向けの名前を追加するため。

## 10. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・結合テスト | `scripts/dev/check-frontend.sh all`（共有、店舗画面、管理画面、E2E の lint と型検査を含む）。他のセッションが実行しているときは待つ |
| カバレッジの基準 | 各段階の最後に、`check-frontend.sh` の後で `node scripts/ci/report/coverage-baseline.mjs admin|store|shared <報告>`（[テストの実行方法](../../guides/running-tests.md) 第 5 節） |
| CI の検査 | `scripts/ci/check-all.sh`（ac-check、hardcoded-text、frontend-rules、input-constraints、doc-refs など） |
| 修正したテストが失敗しうること | 第 1 節の共通の決定の手順。段階のレビューで、確認したテストの一覧を見る |
| 画面 | 段階 1 の後に、skill `local-dev` で管理画面を起動し、注文の詳細と商品の登録の画面の見た目が変わらないことを目視する。スロットは同時に 1 つまで |
| E2E | 段階 6。`scripts/dev/e2e.sh`。他のセッションが実行しているときは待つ |
| アーカイブ先 | 段階 6。`docs/specs/archive/1to10/157-frontend-tidy/` |

## 11. 利用者に確認すること

1. **段階 1 を先に `develop` に入れるか**：spec 158 は、段階 1 で直す違反の検査（DC-158-50、51）を、本 spec のマージの後に加える。本 spec は段階が 6 つあり、規模 M の spec 158 より遅く終わる見込みである。
   - 案 A（採用。2026-10-23 に利用者が承認した）：段階 1 を終えたら、その commit だけで pull request を作り、先に `develop` に入れる。spec のアーカイブは最後の pull request で行う。[エージェントの作業フロー](../../rules/subagents.md) 第 4 節の「spec 1 つに pull request 1 つ」の例外になる。理由：spec 158 が検査を加えるまでの待ちをなくせるため。段階 1 の差分は、テスト 7 ファイルと画面 2 つで小さい。段階 1 の pull request のマージも、利用者の許可を得てから行う。マージの後に、issue 担当のセッションを通じて spec 158 に知らせる。
   - 案 B：規則どおり pull request を 1 つにする。spec 158 は、本 spec のマージの後に検査を加える（spec 158 が利用者に扱いを相談する）。
2. **カバレッジの基準を下げることがあるか**：テストを統合・削除しても基準を下げない方針にした（第 1 節の共通の決定）。下回ったときは、残すテストに確認を追加する。この方針でよいか。
