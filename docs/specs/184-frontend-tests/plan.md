# Plan 184：フロントのテストの書き方の残りをそろえる（第 2 回）

ステータス：草案　対象：[spec.md](spec.md)

前提：spec 184 が確定した。文書の書き方は skill `docs`（[SKILL.md](../../../.claude/skills/docs/SKILL.md)）と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`（[SKILL.md](../../../.claude/skills/review/SKILL.md)）。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| DOM に読み込む補助と jest-dom | 第 1 節 | DS-184-1、2（DC-184-1、2、4〜7） |
| フォームのコンポーネントの分割 | 第 2 節 | DS-184-3（DC-184-8〜10） |
| HTML に描画するテストの書き換え | 第 3 節 | DS-184-1（DC-184-1、3） |
| テストの構成と書き方 | 第 4 節 | DS-184-4（DC-184-11〜17） |
| テストの補助とデータ | 第 5 節 | DS-184-5（DC-184-18〜22） |
| 並行する spec と同じファイル | 第 6 節 | DN-184-6、8 |
| 段階と確認の方法 | 第 7 節、第 8 節 | 全て |
| 利用者に確認すること | 第 9 節 | 全て |

DC の列は「184-1」の形で書く。spec の番号 184 を付けた DC-184-1 の略である。

共通の決定：

- 本 spec は見える振る舞いを変えないので、TDD の「レッドから」は、新しい関数（DOM に読み込む補助、日時の入力の補助、現在の秒の補助）と、分けたコンポーネントの props だけに当てはまる。書き換えたテストは、一時的に実装か期待値を変更して、失敗しうることを確認する（DN-184-4）。手順は spec 157 と同じく、元のファイルをスクラッチパッドにコピーしてから手動で変更し、テストの後に戻す。
- カバレッジの基準（DN-184-5）：各段階の最後に、`check-frontend.sh` の後で `node scripts/ci/report/coverage-baseline.mjs admin|store|shared <報告>` で 3 つの対象を確認する。下回ったら、削除・統合したテストが確認していた分岐を、残すテストで確認する。基準を下げない。上回ったら、最後の段階で `--write` で更新する。
- 実装は実装役（汎用）に、1 タスクずつ任せる。`[リスク]` のタスクはない（テストとコンポーネントの構成だけを変え、API と金額の計算に触れない）。
- テストのファイルを変えるタスクは、そのファイルにある本 spec の DC の違反を全て直す。実装役には、spec 157 のレビューの結果（`/.local/review/202610231200-157-frontend-tests/`、`/.local/review/202610232100-157-final/`）のうち、そのファイルの指摘を渡す。
- 段階の間では、確認のために中断しない。plan と tasks は一緒に利用者が確認する。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。
- 実装を始める前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）で、第 3〜5 節の対象の箇所が今の `develop` に残っているかを確認し直す。spec 183 が先にマージされていれば、その検査の対象と第 6 節の扱いを照合する。

## 1. DOM に読み込む補助と jest-dom（DC-184-1、2、4〜7）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 184-1、2 | 店舗画面の補助に、部品を `renderMarkup` で HTML にし、その HTML を jsdom の文書に読み込んで、問い合わせの関数（Testing Library の `within` の戻り値）を返す関数を置く（※1）。効果は実行されない（DC-184-2） | 店舗画面 `src/test/render.tsx`（`renderStatic`）、`render.test.tsx` | 単体テスト（`[テスト]` を先に書き、レッドを確認する） |
| 184-1 | 共有のパッケージの金額の内訳のテストは、テストのファイルの中で同じ処理を書く（※2） | 共有 `src/components/AmountsSummary.test.tsx` | 単体テスト |
| 184-4 | 開始タグの補助（`startTags`、`findTag`）とそのテストを、全ての書き換えの後に削除する | 店舗画面 `src/test/markup.ts`、`markup.test.ts` | `git grep -n "test/markup"` が 0 件 |
| 184-5 | テストの表と補助の表を書き換える。表示のコンポーネントとページは `renderStatic` で DOM に読み込んで役割と名前で探すこと、サーバーアクションの結果の表示は結果を props で受けるコンポーネントで確認すること（第 2 節）、`markup.ts` の行の削除、`renderStatic` の行の追加 | [店舗画面の構成](../../design/apps/store.md) 第 7 節、[テストの書き方](../../rules/test-writing.md) 第 1.1 節（「HTML に描画し」の文） | レビュー |
| 184-6 | 店舗画面と共有のパッケージに `@testing-library/jest-dom` を開発の依存として加える。版は管理画面と同じにする。店舗画面は `src/test/setup.ts` で、共有のパッケージは `vitest.config.ts` の setupFiles で読み込む。共有のパッケージには、`jsdom` と `@testing-library/dom` も加える（※2） | 店舗画面・共有 `package.json`、`pnpm-lock.yaml`、店舗画面 `src/test/setup.ts`、共有 `vitest.config.ts` | 型チェック、単体テスト |
| 184-7 | `.disabled`、`getAttribute` の比較（14 か所）を jest-dom の照合の関数にする | 店舗画面 `ProductShowcase.test.tsx`、`VariantPicker.test.tsx`、`CancellationRequestForm.test.tsx` | `git grep -nE "\.disabled\)|getAttribute\(" -- 'frontend/store/**/*.test.tsx'` が 0 件 |

※1：jsdom の文書は、テストの環境（Node）を変えずに、補助の中で `jsdom` の `JSDOM` で作る。理由：ページとサーバーコンポーネントのテストは、Node の環境で MSW（`msw/node`）と Next.js の置き換えを使っており、テストの環境を jsdom に変えると、それらの動きが変わるおそれがあるため。テストの環境を jsdom にしている 5 ファイル（操作を確認するもの）は、今の `renderInBrowser` のまま使う。補助の単体テストで、Node の環境で役割と名前の問い合わせと jest-dom の照合の関数が動くことを確認する。動かなければ、作業を止めて報告する（DC-184-1 の方法の変更になるため）。

※2：共有のパッケージは補助を置かない（spec 157 の plan 第 4.1 節）。使うのは 1 ファイルなので、その中で `JSDOM` と `@testing-library/dom` の `within` を使う。React の描画は今の `renderToStaticMarkup` のままにする。

- `renderStatic` の戻り値の要素は、テストのファイルの中で `screen` の代わりに使う。補助には説明のコメント（TSDoc）を書く（[コメントの書き方](../../rules/comments.md)）。
- 用語集には「jest-dom」を追加する（spec 第 2 節）。他の語は本 spec の中だけで使うので追加しない。

## 2. フォームのコンポーネントの分割（DC-184-8〜10）

| DC | 決定 | 置き場所 | 確認方法 |
|----|------|----------|----------|
| 184-8 | 2 つのフォームを、`useActionState` を呼ぶコンポーネント（今の名前のまま。呼び出し元は変えない）と、結果（`ActionResult | null`）、送る関数、送信中かを props で受けて表示するコンポーネントに分ける。表示のコンポーネントは同じファイルに置いて export する（名前は `OrderFormView`、`AddToCartFormView`） | 店舗画面 `features/checkout/components/OrderForm.tsx`、`features/catalog/components/AddToCartForm.tsx` | 型チェック、E2E |
| 184-9 | 2 つのフォームのテストは、表示のコンポーネントに結果を渡して確認し、`vi.mock('react')` を削除する。読むだけのサーバーアクション（地域）の差し替えは今のまま残す（[店舗画面の構成](../../design/apps/store.md) 第 7 節 ※6） | 店舗画面 `OrderForm.test.tsx`、`AddToCartForm.test.tsx` | `git grep -n "vi.mock('react'"` が 0 件、単体テスト |
| 184-10 | 表示のコンポーネントは、今の描画の内容を移すだけにする。送信中の表示、エラーの出し方、入力欄の名前を変えない | 同上 | E2E（注文、カートに入れる）、カバレッジの基準 |

- 画面を作るときの skill（`new-screen`）のフォームの例に、結果の表示をテストするフォームは表示のコンポーネントを分けることを 1 行で書く（skill `writing-for-agents`）。他のフォームは分けない。理由：今のテストが結果を差し替えているのは 2 つだけで、全てのフォームを分けると差分が広がるため。

## 3. HTML に描画するテストの書き換え（DC-184-1、3）

対象は次の 19 ファイルである。どれも `renderStatic`（共有は第 1 節の ※2）で DOM に読み込み、役割と名前、見える文言、見出しで探し、jest-dom で確認する。HTML の文字の一致、正規表現、`findTag` を使わない。

| 範囲 | ファイル |
|------|----------|
| 共通の部品 | `components/ActionError`、`LoginLinks`、`PageLinks`、`QuoteItemSummary`、`SiteHeader` |
| カート | `features/cart/components/CartItemControls`、`CartMergeNoticeView`、`StartCheckoutForm` |
| 商品 | `features/catalog/components/AddToCartForm`、`CategoryList`、`ListFilterForm`、`ProductCard` |
| 注文手続き | `features/checkout/components/OrderForm`（HTML に描画する部分） |
| 注文 | `features/order/components/CancellationSection`、`OrderStatusBadge`、`PaymentSection`、`ShippingAddressDetails`、`app/orders/[orderId]/page` |
| 共有 | `shared/src/components/AmountsSummary` |

- DC-184-3：探す名前がない要素（例：注文の操作の欄、合計の行）は、画面に支援技術向けの名前を追加する。名前は見える見出しか文言に `aria-labelledby` で結ぶことを優先し、`aria-label` で見える文言と違う語を足さない（DN-184-1）。追加した名前は、E2E の探し方と衝突しないかを確認する。
- 「出さない」ことの確認（例：`<nav` がない、`<img` がない）は、役割と名前で探して `queryBy…` が null であることを確認する。
- フォームの送り先（`action`）と選択肢の値（`value`）のように、見えない属性が振る舞いの一部であるものは、役割と名前で探した要素に `toHaveAttribute` と `toHaveValue` で確認する。

## 4. テストの構成と書き方（DC-184-11〜17）

| DC | 決定 | 対象 | 確認方法 |
|----|------|------|----------|
| 184-11 | 409 の流れを詰めたテストを、「競合を知らせる」「入力を保持する」「読み込み直すと最新になる」「読み込み直した後に保存できる」の振る舞いごとに分ける。共通の準備は、ファイルの中の関数にする | 管理画面 `ProductEditPage.test.tsx`（3 つ）、`TaxRatesPage.test.tsx`（1 つ） | レビュー（もとの確認が全て残ること）、カバレッジの基準 |
| 184-12 | `const URL` を、画面を表す名前（例：`PAGE_PATH`）にする | 管理画面 `StockPage`、`OrderDetailPage`、`ProductEditPage`、`TaxCategoryListPage`、`TaxRatesPage` のテスト | `git grep -n "const URL =" -- '*.test.tsx'` が 0 件 |
| 184-13 | テストの間に空行を入れる | 管理画面 `ProductEditPage.test.tsx`、`OrderDetailPage.test.tsx` ほか、書き換えたファイル | レビュー（整形の検査は空行を見ない） |
| 184-14 | 変更をまとめる hook のテストを `renderHook` で呼ぶ | 管理画面 `api/useMutationGroup.test.ts` | 単体テスト |
| 184-15 | 日時の入力の補助を管理画面のテストの補助に移し、商品の編集の `fireEvent.change` を補助に置き換える。ファイルの選択は `user.upload` にする。jsdom が受け付けない入力（`datetime-local` の直接の入力、受け付けない形式のファイル）だけは補助の中で `fireEvent` を使い、理由をコメントに書く | 管理画面 `src/test/`（`inputs.ts`、新規）、`TaxRatesPage.test.tsx`、`ProductEditPage.test.tsx` | 単体テスト（補助の `[テスト]` を先に書く）、`git grep -n "fireEvent.change" -- 'frontend/admin/src/features'` が 0 件 |
| 184-16 | `process.env` への直接の代入を `vi.stubEnv` にする。現在の秒（`Math.floor(Date.now() / 1000)`）は店舗画面のログインの補助に関数を置いて使う | 店舗画面 `lib/server/timeout.test.ts`、`auth/index.test.ts`、`src/test/auth.ts` | `git grep -n "Math.floor(Date.now()" -- 'frontend/store/src'` が補助の 1 件 |
| 184-17 | 偽の時計の後始末を、店舗画面と管理画面の `setup.ts` の `afterEach` に `vi.useRealTimers()` で置き、各ファイルから削除する | 店舗画面・管理画面 `src/test/setup.ts`、`SalesTrendPage.test.tsx`、`RemainingTime.test.tsx` | `git grep -n useRealTimers -- '*.test.ts*'` が 0 件 |

- DC-184-16 の「ログインの状態は `signInAs` で作る」は、今の `develop` で `signInAs` の外の直接の代入がない（補助の中だけ）。照合で残っていなければ、変更しない。
- 店舗画面の構成 第 7 節と管理画面の構成 第 9 節の補助の表に、追加・移動した補助を書く。

## 5. テストの補助とデータ（DC-184-18〜22）

| DC | 決定 | 対象 | 確認方法 |
|----|------|------|----------|
| 184-18 | レポートの条件のテストの商品 ID は、補助の `SHIRT_ID` を使う。東京都と千代田区のデータ（地域のサーバーアクションと注文のフォームのテスト）は、店舗画面の `test/fixtures.ts` に置く。商品の編集の在庫数のハンドラーを包むだけの関数は、補助を直接使う | 管理画面 `reportCondition.test.ts`、`test/reportServer.ts`、`ProductEditPage.test.tsx`（`StockPage.test.tsx` も照合する）。店舗画面 `actions/region.test.ts`、`OrderForm.test.tsx`、`test/fixtures.ts` | レビュー |
| 184-19 | 管理画面のログインの戻り先のテストから、共有の `isSafeReturnPath` のテストと同じ危険な入力の網羅を削除し、画面の外のパスでトップに戻ること（1 つ）と、オブジェクトでない値の扱いだけを残す。網羅に漏れがあれば、共有のテストに加える | 管理画面 `lib/login.test.ts`、共有 `lib/login.test.ts` | レビュー、カバレッジの基準 |
| 184-20 | 一覧のセルの補助（`getCell`）は、行の見出しのセル（`rowheader`）と入れ子の表を、TSDoc に書いた扱いに合わせる（行の見出しのセルも列に数える、入れ子の表の中は探さない）。補助のテストで確認する。ダウンロードの記録の補助は、`download` の属性のないリンクのクリックを記録せず、本物のクリックに渡す。補助のテストで確認する | 管理画面 `test/queries.ts`、`queries.test.tsx`、`test/download.ts`、`download.test.ts` | 単体テスト（`[テスト]` を先に書く） |
| 184-21 | 支援技術向けの名前を 1 つの要素だけに付ける。カテゴリの木は、行のまとまり（`role="group"`）の名前を残し、`li` の名前を外す。商品の画像は、`img` の `alt` を残し、`li` の名前を外す。テストと E2E の探し方を合わせる | 管理画面 `features/category/components/CategoryTreeList.tsx`、`features/product/components/ProductImagesCard.tsx`、それらのテスト、`e2e/`（探し方が当たる所だけ） | 結合テスト、E2E |
| 184-22 | 購入できない品目の小計のテストは、購入できる品目との違い（理由の文と、合計に含めないこと）を確認する名前と確認にする。金額の内訳の 2 つの見た目の種類のテストは、種類ごとに見える違い（見出し、行の順）を名前に書く。`expectInOrder` は、見つからない文言が分かる確認にする | 店舗画面 `CartItemControls.test.tsx`。共有 `AmountsSummary.test.tsx` | レビュー、テストの変更で失敗しうることの確認 |

- DC-184-21 の名前の変更は、見える文言を変えない（DN-184-1）。`e2e/` のテストのコードは Non-goals だが、探し方が名前の変更で壊れる所だけは直す。
- DC-184-22 の `AmountsSummary.test.tsx` は、第 3 節の書き換えと同じタスクで直す。

## 6. 並行する spec と同じファイル

| spec | 同じファイル | 扱い |
|------|--------------|------|
| 183（CI の検査） | `docs/rules/test-writing.md`、`frontend/*/package.json`・`pnpm-lock.yaml`（#167 の package の書き方）、`docs/guides/ci.md` | 後でマージする側が `develop` を取り込んで解消する。`pnpm-lock.yaml` は取り込みの後に `pnpm install` で再生成する。`scripts/ci/` は本 spec では変えない（DN-184-6） |
| 181（バックエンドのテスト） | `docs/rules/test-writing.md`（バックエンドの節） | 同上 |
| 182（決済と設定） | なし（バックエンドだけ） | なし |
| 全て | `docs/design/acceptance-criteria.md`（テストの列）、`docs/design/glossary.md` | 衝突したら `node scripts/ci/ac-check.mjs --write` で再生成する。用語集は両方の行を残す |

- spec 183 には、spec 第 6 節の違反の一覧と、jest-dom の書き方（プロパティと `getAttribute` の比較）を検査に加えるかの判断を、plan と tasks の確認のときに知らせる。spec 183 が検査を加えるのは、本 spec のマージの後である。

## 7. 段階

| 段階 | ファイル | 内容 | DC |
|------|----------|------|----|
| 1 | [tasks/01-helpers.md](tasks/01-helpers.md) | 依存の追加、DOM に読み込む補助、フォームの分割、文書の規則 | 184-1、2、5〜10 の土台 |
| 2 | [tasks/02-store-tests.md](tasks/02-store-tests.md) | 店舗画面と共有のパッケージの HTML のテストの書き換え、jest-dom、開始タグの補助の削除 | 184-1、3、4、7、9、22 の一部 |
| 3 | [tasks/03-tidy.md](tasks/03-tidy.md) | テストの構成と書き方、テストの補助とデータ（管理画面と店舗画面） | 184-11〜21 |
| 4 | [tasks/04-review.md](tasks/04-review.md) | spec の最後のレビュー、E2E、起票のまとめの案、pull request の前のレビュー、アーカイブ | 全て |

- 各段階の最後のタスクは、段階のレビュー（profile「厚め」：一般の観点を汎用で 2 回、ファイルをまたぐ影響を高性能と汎用）と、全体の検査（第 8 節）の実行である。
- 段階 2 と 3 の修正のタスクは、テストのファイルが重ならない単位（機能ごと）に分け、`[P]` にする。段階 2 と 3 は、同じファイル（`OrderForm.test.tsx`、`CartItemControls.test.tsx`、`AmountsSummary.test.tsx`）を触るので、段階 2 の後に段階 3 を行う。

## 8. 確認の方法

| 確認 | 方法 |
|------|------|
| 単体・結合テスト | `scripts/dev/check-frontend.sh all`（共有、店舗画面、管理画面、E2E の lint と型検査を含む）。他のセッションが実行しているときは待つ |
| カバレッジの基準 | 各段階の最後に、`node scripts/ci/report/coverage-baseline.mjs admin|store|shared <報告>`（[テストの実行方法](../../guides/running-tests.md) 第 5 節） |
| CI の検査 | `scripts/ci/check-all.sh` |
| 書き換えたテストが失敗しうること | 共通の決定の手順。段階のレビューで、確認したテストの一覧を見る |
| 画面 | 目視はしない。見える文言と配置を変えないため。支援技術向けの名前とフォームの分割は、テストと E2E で確認する |
| E2E | 段階 4。`scripts/dev/e2e.sh`。他のセッションが実行しているときは待つ |
| アーカイブ先 | 段階 4。`docs/specs/archive/1to10/184-frontend-tests/` |

## 9. 利用者に確認すること

1. **画面の目視**：本 spec は見える文言と配置を変えないので、手元の環境で画面を目視しない（第 8 節）。フォームの分割と支援技術向けの名前は、テストと E2E で確認する。spec 157 では、支援技術向けの名前を加えた画面を目視した。
   - 案 A（推奨）：目視しない。スロットを使わず、使用量を抑えられる。
   - 案 B：段階 2 の後に、注文手続きと商品の画面を目視する。
