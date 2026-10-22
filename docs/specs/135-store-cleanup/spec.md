# Spec 135：店舗画面を整える（MVP 019 の全体のレビューの指摘）

ステータス：確定

## 1. 背景と目的

MVP 019 の全体のレビュー（2026-10-21）で、店舗画面（`frontend/store`）に必須 2 件、推奨 18 件の指摘が残った。1→10 の機能を追加する前に整える。指摘は 2026-10-22 に今のコードで確認した。もう直っているものはなかった。推奨 18 件のうち 3 件は別の issue で扱い、1 件は事実が今は成り立たないので外す（第 5 節）。

追跡の issue は [#135](https://github.com/roger88106/ec-site/issues/135)、扱う issue は [#83](https://github.com/roger88106/ec-site/issues/83)（必須の 2 件：クエリ付きの URL の組み立て、`traceparent`）と [#87](https://github.com/roger88106/ec-site/issues/87)（推奨）、作業ブランチは `135-store-cleanup` である。本 spec は整理の spec で、規模は M である（[開発の進め方](../../rules/workflow.md) 第 4.2 節）。API の契約とデータベースと AC を変えない。範囲は店舗画面の中だが、指摘が多いので S ではない。

## 2. スコープ

| # | 目的 | 対象 | 優先度 |
|---|------|------|--------|
| DS-135-1 | 店舗画面のクエリ付きの URL と、バックエンドへのリクエストの追跡 ID が、フロントの書き方に合う（#83） | D | P1 |
| DS-135-2 | 同じ判定・表示・状態が 1 か所にまとまり、直すときに 1 か所で済む（#87） | D | P1 |
| DS-135-3 | レビューで見つけた不具合（画面の確認の順、設定の値の確認、ログの内容）を直す（#87） | D | P1 |
| DS-135-4 | テストと店舗画面の構成の文書が、規則と今のコードに合う（#87） | D | P1 |

## 3. 目的と完了条件

### DS-135-1 クエリ付きの URL と追跡 ID を、フロントの書き方に合わせる（D / P1）

- **DC-135-1** カテゴリの一覧の、最上位のカテゴリと子のカテゴリのリンクが、商品一覧のクエリ付きの URL を作る関数で作られる。根拠：[フロントの書き方](../../rules/frontend-coding.md) 第 6 節。
- **DC-135-2** DC-135-1 の前後で、リンクの URL は変わらない（`/products?categoryId=<ID>`）。テストで確認する。
- **DC-135-3** 公開の API クライアント（カート、郵便番号と市区町村、商品とカテゴリの取得）のリクエストにも、会員の API クライアントと同じく、追跡 ID（`traceparent`）が付く。リクエストの中で呼ぶときに限る（第 6 節 5）。根拠：[フロントの書き方](../../rules/frontend-coding.md) 第 8.2 節。
- **DC-135-4** 両方の API クライアントで `traceparent` が付くことを、単体テストで確認する。
- **DC-135-5** [店舗画面の構成](../../design/apps/store.md)に、両方の API クライアントが `traceparent` を付けること、キャッシュするリクエストに付けてもキャッシュが使われることを書く。理由：Next.js はキャッシュの鍵から `traceparent` を除くため。

### DS-135-2 重複をまとめる（D / P1）

- **DC-135-6** 郵便番号の判定（ハイフンを除いて 7 桁の数字か）が 1 つの関数にあり、注文のフォームと郵便番号の検索のサーバーアクションがそれを使う。関数は単体テストで確認する。
- **DC-135-7** 受け付ける郵便番号の形は、今と変えない。
- **DC-135-8** サーバーアクションの失敗の表示が 1 つの部品にあり、今の 6 か所（カートの数量、注文手続きの開始、カートに入れる、支払、キャンセルの申し込み、注文のフォーム）がそれを使う。
- **DC-135-9** 失敗の表示の部品は、子の要素（注文のフォームの「カートを見る」のリンク）を受けられる。
- **DC-135-10** 6 か所の失敗の表示で、読み上げの設定と文字の大きさが同じになる。
- **DC-135-11** 注文の画面で、支払の欄とキャンセルの欄を出すかの判定が、それぞれ 1 か所にある。今はページと部品の 2 か所にある。
- **DC-135-12** 商品の詳細で、選んだバリアントの状態を 1 つの部品だけが持つ。今は 2 つの部品が持ち、コールバックで同期している。
- **DC-135-13** フォームの値を整数として読み、値がない、または数でないときに NaN を返す処理が 1 つの関数にあり、今の 3 か所（カートの 2 か所、注文手続きの 1 か所）がそれを使う。

### DS-135-3 レビューで見つけた不具合を直す（D / P1）

- **DC-135-14** 注文手続きの画面は、ID の形を確認する前に、ログインを確認する。ログインしていない人が形の違う ID の URL を開くと、「見つかりません」でなくログインの画面に移る。テストで確認する。根拠：[店舗画面の構成](../../design/apps/store.md) 第 9.2 節。注文の画面と同じ順である。
- **DC-135-15** 店舗画面は、API の応答を再取得する間隔の設定（`STORE_API_REVALIDATE_SECONDS`）が 0 以上の整数でなければ、設定の名前を含む誤りを出して起動に失敗する。テストで確認する。
- **DC-135-16** 再取得の間隔の設定が 0 のときは、キャッシュしない設定として受け付ける。テストで確認する。
- **DC-135-17** [環境変数](../../guides/environment.md)の文書に、DC-135-15、16 の値の範囲を書く。
- **DC-135-18** カートを移せなかったときの警告のログに、カートの ID（Cookie の値）を出力しない。テストで確認する。根拠：[店舗画面の構成](../../design/apps/store.md) 第 4.2 節（ログに Cookie を出力しない）。

### DS-135-4 テストと文書を、規則と今のコードに合わせる（D / P1）

- **DC-135-19** [店舗画面の構成](../../design/apps/store.md) 第 2 節のディレクトリの説明が、今の構成と一致する。説明に載っていないもの（2026-10-22 時点）：`actions` の `region`・`memberAction`・`withActionResult`、`api` の `result`、`lib` の `cartChoice`・`cartId`・`cartMergeNotice`・`login`・`quantity`・`utils`・`server/`（formData、log、timeout、trace）。一覧は T135-02-10 で、そのときの構成と照合して確定する。
- **DC-135-20** ログインのリンクの部品のテストが、その部品の横のテストのファイルにある。今は別の部品のテストのファイルにあり、ファイル全体に不要な差し替えがかかっている。根拠：[店舗画面の構成](../../design/apps/store.md) 第 7 節。
- **DC-135-21** 必須の設定の定数の一覧をそのまま写したテスト（「必須の値は 6 つ」）がない。同じ内容は、足りない名前を並べて失敗するテストが確認している。根拠：[テストの書き方](../../rules/test-writing.md) 第 2.2 節。
- **DC-135-22** 必須の設定の定数の export が、テストのためだけのものなら削除する。
- **DC-135-23** 起動時の設定の確認（`instrumentation.ts` の `register`）を単体テストで確認する。本番モードで必須の値がなければ、足りない設定の名前を標準エラー出力に書いて終了する。根拠：[フロントの書き方](../../rules/frontend-coding.md) 第 8.1 節。
- **DC-135-24** 起動時の設定の確認は、Node.js 以外の実行環境では何もしない。単体テストで確認する。
- **DC-135-25** バリアントを選ぶと、そのバリアントの画像に切り替わる。jsdom のテストで確認する。根拠：[店舗画面の構成](../../design/apps/store.md) 第 5 節、第 7 節。
- **DC-135-26** 小さな画像を押すと、その画像に切り替わる。jsdom のテストで確認する。
- **DC-135-27** バリアントを選ぶまで、および買えないバリアントを選んだときは、「カートに入れる」を押せない。jsdom のテストで確認する。
- **DC-135-28** 店舗画面のテストが、CSS のクラス（`line-through`、`text-destructive`、`hover:underline`、`size-full object-cover`、`text-muted-foreground text-sm` など）で確認しない。役割と名前、意味のある要素で確認する。取り消し線は、取り消しを表す HTML の要素で示す。対象のテストは、カートの数量の操作、サイトのヘッダー、ページのリンク、商品のカード、注文のフォームである。根拠：[テストの書き方](../../rules/test-writing.md) 第 4.1 節。

## 4. 守る制約

- **DN-135-1** API の契約、データベース、バックエンドを変えない。
- **DN-135-2** 共有のパッケージ（`frontend/shared`）と管理画面（`frontend/admin`）を変えない。変える必要が出たら、先に利用者に相談する。理由：spec 134 が同時に変えるため。
- **DN-135-3** 消費者・運営者に見える振る舞いを変えない。例外は DC-135-14、15、16 で、レビューで見つけた不具合の修正として扱う（[開発の進め方](../../rules/workflow.md) 第 1.5 節）。失敗の表示の読み上げの設定と文字の大きさをそろえるのは、表示の統一で、振る舞いの変更ではない。
- **DN-135-4** AC を変えない。AC 一覧も変えない。
- **DN-135-5** テストの数と、テストが確認する内容を減らさない。例外は DC-135-21 のテストの削除で、同じ内容を他のテストが確認している。
- **DN-135-6** spec 134 が変える店舗画面のファイル（下の表）を変えない。理由：spec 134 が、管理画面と店舗画面で重なる表示を共有のパッケージに移すため（2026-10-22 に利用者が承認）。注は ※1。

| ファイル（`frontend/store/src/` の下） | spec 134 で変えること |
|------|------|
| `features/order/labels.ts` | 注文の状態・出荷の状態・運送会社の表示名を共有に移す |
| `components/AmountsSummary.tsx` | 金額の内訳の表示を共有にする |
| `components/PageLinks.tsx` | リンクの見た目のボタンを共有にする |
| `lib/login.ts` | spec 134 が共有の戻り先の判定を直す。店舗画面の側はそれを使う |

※1：DC-135-20、28 は `components/PageLinks.test.tsx` を変える。spec 134 も同じファイルを変えるときは、後でマージする側の pull request で、`develop` を取り込んで衝突を解消する（[エージェントの作業フロー](../../rules/subagents.md) 第 4 節）。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| 使われていない型の別名（`api/types.ts` の 5 つ）を削除する | [#85](https://github.com/roger88106/ec-site/issues/85)（使われていないコードを削除する）で扱う |
| 直接の依存 `cn` を外す | #85 で扱う。なお、レビューの「店舗画面のコードは import していない」は今は成り立たない（`components/ui/native-select.tsx` が import している）。#85 で扱うときに確認する |
| 注文の状態・出荷の状態・運送会社の表示名の関数を、管理画面と共有にする | [#88](https://github.com/roger88106/ec-site/issues/88)（spec 134）で扱う。共有のパッケージを変えるため（DN-135-2） |
| 注文のフォームで、送信に失敗した後も入力が残ることを、店舗画面のテストで確認する | 行わない。E2E（`e2e/tests/order.spec.ts`）が、郵便番号の誤りの送信の後に氏名が残ることを確認しているため |
| 支払のボタン、商品の画像、カテゴリの一覧の部品のテストを追加する（DC-135-2 のカテゴリの一覧の URL のテストを除く） | 支払と画像は E2E が操作を確認しているため |
| クエリ付きの URL の文字列での組み立てと、テストの CSS のクラスを、機械の検査（`frontend-rules.mjs` など）で見つける | 本 spec では行わない。spec の最後に、起票の案に入れるかを決める |

## 6. 決めたこと

2026-10-22 に、利用者が確認した。Open questions はない。

1. **規模**：M にする。
2. **注文手続きの画面の確認の順**（DC-135-14）：
   - 案 A（採用）：ログインを先にする。[店舗画面の構成](../../design/apps/store.md) 第 9.2 節（取得の前にログインを確認する）に合い、注文の画面と同じになる。今の振る舞いを確認するテストはない。
   - 案 B：外す。
3. **再取得の間隔の設定の確認**（DC-135-15、16）：
   - 案 A（採用）：0 以上の整数でなければ起動に失敗する（待つ時間の 2 つの設定と同じ扱い）。
   - 案 B：範囲の外なら既定の値に戻す（誤りに気づきにくい）。
   - 案 C：外す。
4. **注文のフォームの入力が残る確認**：外す（第 5 節）。
5. **`traceparent`**：両方の API クライアントに付ける。キャッシュする GET に付けても、キャッシュの鍵は変わらない。
   - 根拠：Next.js のキャッシュの鍵を作る処理（`next/dist/server/lib/incremental-cache/index.js`）が、`traceparent` と `tracestate` を鍵から除く。
   - 2026-10-22 に手元の Next.js 16.4.0 で確認した。店舗画面の 16.4.2 でも同じかは、T135-01-02 で確認する。
   - リクエストの外（リクエストのヘッダーを読めない所）から公開の API クライアントを呼ぶ箇所があれば、そこでは付けない。実装のときに呼び出し元を確認する。
6. **用語集**：新しい識別子（失敗の表示の部品、郵便番号の判定の関数など）を[用語集](../../design/glossary.md)に追加するかは、用語集の今の書き方（フロントの部品を載せているか）を見て、T135-02-10 で判断する。

## 7. tasks

### 段階 1：URL・追跡 ID と、重複をまとめる

- 前提：spec の確定
- 完了条件：DC-135-1〜13、25〜27 を満たし、店舗画面のテストと CI が通る。
- 参照：第 3 節の DS-135-1、DS-135-2、[フロントの書き方](../../rules/frontend-coding.md)、[テストの書き方](../../rules/test-writing.md)、skill `new-screen`

- [x] **T135-01-01** [テスト] 公開の API クライアントのリクエストに `traceparent` が付くテストを書き、レッドを確認する（DC-135-4）。
  - 依存：なし
  - 範囲：`frontend/store/src/api/client.test.ts`
  - 結果：公開の口の要求に traceparent が付くテストと、付かないときに付けないテストを足した。実装の前は公開の口のテストが失敗した（レッド）。
- [x] **T135-01-02** T135-01-01 のテストが通るように、`traceparent` を付ける処理を両方の API クライアントで使う。呼び出し元にリクエストの外のものがないかを確認する。店舗画面の Next.js 16.4.2 でも、キャッシュの鍵から `traceparent` を除くことを確認する。店舗画面の構成に DC-135-5 を書く（DC-135-3、5）。
  - 依存：T135-01-01
  - 範囲：`frontend/store/src/api/client.ts`、`docs/design/apps/store.md`
  - 結果：付ける処理を 1 つの middleware にまとめ、両方の口で使った。呼び出し元は全て画面・サーバーアクション・`api/cart.ts` で、`"use cache"` や `generateStaticParams` はなく、`getApi` は `connection()` の後に返すので要求の外はない。分ける必要はなかった。Next.js 16.4.2 も、キャッシュの鍵から traceparent を除く（`incremental-cache/index.js` 277 行）。店舗画面の構成 第 4.2 節に書いた。
- [x] **T135-01-03** カテゴリの一覧のリンクの URL のテストを書いてから、商品一覧の URL を作る関数で組み立てる（DC-135-1、2）。テストは今の URL で通る。一時的に URL を変えて、テストが失敗することを確認し、戻す。
  - 依存：なし
  - 範囲：`frontend/store/src/features/catalog/listQuery.ts`（`listHref`）、`frontend/store/src/features/catalog/components/CategoryList.tsx`
  - 結果：リンクの href を役割と名前で確認するテストを書き、今の URL で通ること、子のリンクの URL を変えると失敗することを確認した。5 項目を毎回書かずに済むよう、`listQuery.ts` にカテゴリだけを受ける `categoryHref` を足して（単体テストはレッドを確認）、最上位と子の両方をこれで作るようにした。
- [x] **T135-01-04** [テスト] 郵便番号の判定の関数の単体テストを書き、レッドを確認する。受け付ける形は今の 2 か所の処理と同じにする（DC-135-6、7）。
  - 依存：なし
  - 範囲：`frontend/store/src/features/checkout/`（郵便番号の補完の `postalCodeFill.ts` と同じ所）
  - 結果：判定は補完とは役割が違うので、新しいファイル `postalCode.ts` の `normalizePostalCode`（7 桁の数字か null）にして、単体テストを書き、関数がなくて失敗するレッドを確認した。最初のハイフン 1 つだけを除く今の形を保ち、`123-45-67` と `123--4567` は通らない境界をテストに入れた。
- [x] **T135-01-05** T135-01-04 の関数を作り、注文のフォームと郵便番号のサーバーアクションで使う（DC-135-6、7）。
  - 依存：T135-01-04
  - 範囲：`frontend/store/src/features/checkout/`、`frontend/store/src/actions/region.ts`
  - 結果：注文のフォームとサーバーアクションの正規表現とハイフンの除去を `normalizePostalCode` に置き換えた。依存の向きの検査（`frontend-rules.mjs`）は機能の間だけを禁じ、`actions/` から `features/` の import は許されるので、置き場所は spec のとおり `features/checkout/`。
- [ ] **T135-01-06** サーバーアクションの失敗の表示の部品を作り、6 か所で使う。部品のテストを書く。変わる期待の分だけ、注文のフォームのテストを直す（DC-135-8、9、10）。振る舞いを変えない部品の共通化なので、テストを実装と同じタスクで書く（[開発の進め方](../../rules/workflow.md) 第 2.2 節）。
  - 依存：T135-01-05（両方が注文のフォームを変えるため）
  - 範囲：`frontend/store/src/` の下の `components/`（新しい部品）、`features/cart/components/CartItemControls.tsx`、`features/cart/components/StartCheckoutForm.tsx`、`features/catalog/components/AddToCartForm.tsx`、`features/order/components/PayForm.tsx`、`features/order/components/CancellationRequestForm.tsx`、`features/checkout/components/OrderForm.tsx` とそれぞれのテスト
- [ ] **T135-01-07** 注文の画面の支払の欄とキャンセルの欄を出す判定を、それぞれ 1 か所にまとめる（DC-135-11）。
  - 依存：なし
  - 範囲：`frontend/store/src/app/orders/[orderId]/page.tsx`、`frontend/store/src/features/order/components/PaymentSection.tsx`、`frontend/store/src/features/order/components/CancellationSection.tsx`、判定の関数の置き場所（`frontend/store/src/features/order/`。ただし `features/order/labels.ts` は変えない）
- [ ] **T135-01-08** 商品の詳細の操作の jsdom のテストを書く（DC-135-25、26、27）。今の実装で通る。一時的に実装を変えて、テストが失敗することを確認し、戻す。
  - 依存：なし
  - 範囲：`frontend/store/src/features/catalog/components/`
- [ ] **T135-01-09** 選んだバリアントの状態を 1 つの部品に持たせる。T135-01-08 のテストが通ったままにする（DC-135-12）。
  - 依存：T135-01-08
  - 範囲：`frontend/store/src/features/catalog/components/`
- [ ] **T135-01-10** フォームの値を整数か NaN で読む関数を作り、3 か所で使う。関数の単体テストを書く（DC-135-13）。振る舞いを変えない関数の共通化なので、テストを実装と同じタスクで書く（[開発の進め方](../../rules/workflow.md) 第 2.2 節）。
  - 依存：なし
  - 範囲：`frontend/store/src/lib/server/formData.ts`、`frontend/store/src/actions/`
- [ ] **T135-01-11** 段階の最後のレビュー（profile「厚め」：一般の観点を汎用で 2 回、重要な観点を高性能と汎用で）。
  - 依存：T135-01-01〜10

### 段階 2：不具合、テスト、文書

- 前提：段階 1
- 完了条件：DC-135-14〜24、28 を満たし、店舗画面のテストと CI が通る。
- 参照：第 3 節の DS-135-3、DS-135-4

- [ ] **T135-02-01** [テスト] ログインしていない人が、形の違う ID で注文手続きの画面を開くと、ログインの画面に移るテストを書き、レッドを確認する（DC-135-14）。
  - 依存：なし
  - 範囲：`frontend/store/src/app/checkout/[checkoutId]/`
- [ ] **T135-02-02** T135-02-01 のテストが通るように、確認の順を変える（DC-135-14）。
  - 依存：T135-02-01
  - 範囲：`frontend/store/src/app/checkout/[checkoutId]/`
- [ ] **T135-02-03** [テスト] 再取得の間隔の設定が 0 以上の整数でなければ起動に失敗し、0 を受け付けるテストを書き、レッドを確認する（DC-135-15、16）。
  - 依存：なし
  - 範囲：`frontend/store/src/auth/config.test.ts`
- [ ] **T135-02-04** T135-02-03 のテストが通るように、設定の確認を追加する。環境変数の文書を直す（DC-135-15、16、17）。
  - 依存：T135-02-03
  - 範囲：`frontend/store/src/auth/config.ts`、`docs/guides/environment.md`
- [ ] **T135-02-05** [テスト] カートを移せなかったときのログにカートの ID がないテストを書き、レッドを確認してから、警告のログの 2 か所からカートの ID を除く（DC-135-18）。小さいので、テストと実装を 1 つのタスクにまとめる。
  - 依存：なし
  - 範囲：`frontend/store/src/api/cart.ts` とそのテスト
- [ ] **T135-02-06** 起動時の設定の確認のテストを書く（DC-135-23、24）。今の実装で通る。一時的に実装を変えて、テストが失敗することを確認し、戻す。
  - 依存：なし
  - 範囲：`frontend/store/src/instrumentation.test.ts`
- [ ] **T135-02-07** ログインのリンクの部品のテストを、`components/LoginLinks.test.tsx` に移す（DC-135-20）。
  - 依存：なし
  - 範囲：`frontend/store/src/components/LoginLinks.test.tsx`、`frontend/store/src/components/PageLinks.test.tsx`。`components/PageLinks.tsx` は変えない（DN-135-6）
- [ ] **T135-02-08** 必須の設定の定数の一覧のテストを削除し、定数の export が不要かを確認する（DC-135-21、22）。
  - 依存：T135-02-03（同じテストのファイルを変えるため）
  - 範囲：`frontend/store/src/auth/config.test.ts`、`frontend/store/src/auth/config.ts`
  - 補足：spec 104 が、Cookie の接頭辞が必須の値でないことを、同じ定数で確認するテストを足した（実装の前の照合で確認）。export を外すなら、このテストを振る舞い（本番のモードで接頭辞がなくても起動できる）の確認に替える。
- [ ] **T135-02-09** テストが CSS のクラスで確認しないようにし、取り消し線は取り消しを表す HTML の要素にする（DC-135-28）。
  - 依存：T135-01-06（注文のフォームのテストを両方が変えるため）、T135-02-07（`PageLinks.test.tsx` を両方が変えるため）
  - 範囲：`frontend/store/src/components/PageLinks.test.tsx`、`frontend/store/src/components/SiteHeader.test.tsx`、`frontend/store/src/features/cart/components/CartItemControls.test.tsx`、`frontend/store/src/features/catalog/components/ProductCard.test.tsx`、`frontend/store/src/features/checkout/components/OrderForm.test.tsx` と、取り消し線を示す部品。`components/PageLinks.tsx` は変えない（DN-135-6）
- [ ] **T135-02-10** 店舗画面の構成 第 2 節のディレクトリの説明を、今の構成に合わせる。新しい識別子を用語集に追加するかを判断する（DC-135-19、第 6 節 6）。
  - 依存：段階 1、T135-02-01〜09（作ったファイルを含めるため）
  - 範囲：`docs/design/apps/store.md`、`docs/design/glossary.md`
- [ ] **T135-02-11** 段階の最後のレビュー（profile「厚め」）。
  - 依存：T135-02-10

### 段階 3：完了

- 前提：段階 1、2
- 完了条件：spec の最後のレビューの指摘に対応し、spec をアーカイブに移している。
- 参照：[エージェントの作業フロー](../../rules/subagents.md) 第 4 節

- [ ] **T135-03-01** spec の最後のレビュー（profile「厚め」。ファイルをまたぐ影響の観点を含める）を行い、指摘に対応する。
  - 依存：T135-02-11
- [ ] **T135-03-02** 後回しにする作業と起票のまとめ方の案を書き、spec を `docs/specs/archive/1to10/` に移す。
  - 依存：T135-03-01
  - 範囲：`docs/specs/`

## 8. 後回しにする作業と起票の案

T135-03-02 で書く。
