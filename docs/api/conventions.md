# API の規約

[constitution](../constitution.md) 第 8 節の細則。契約の正本は `api/openapi/`（`store.yaml`：店舗、`admin.yaml`：管理画面）、実装の書き方は [バックエンドの書き方](../backend/coding.md) 第 2、3 節。

## 1. 契約先行

RESTful JSON。OpenAPI を plan に記述し、バックエンドは生成したインターフェース（OpenAPI Generator の `spring`）を実装し、フロントは生成した型（`openapi-typescript`）を使う。生成物はコミットしない。

## 2. URL

| 項目 | 規則 | 例 |
|------|------|----|
| 接頭辞 | EC の API は全て `/api` で始まる | `/api/store/products` |
| 契約の範囲 | 2 番目のセグメントは契約の範囲で、モジュールではない。`/api/store`（店舗 API、`store.yaml`、消費者が使う）と `/api/admin`（管理 API、`admin.yaml`、運営者が使う）。1 つの契約の操作を、複数のモジュールが実装する | 店舗 API の `/carts` はカート、`/orders` は注文のモジュール |
| `servers.url` と `paths` | 契約の `servers.url` が接頭辞（`/api/store`、`/api/admin`）で、`paths` は接頭辞の後ろだけを書く。生成したインターフェースのパスは `servers.url` を含まないので、バックエンドの `ApiPathConfig` が、生成元の契約に応じた接頭辞を Controller に付ける | `paths` の `/products` は、管理 API では `/api/admin/products` |
| 契約の外の URL | 外部のサービスが呼ぶ通知のエンドポイントは `/api/webhooks/<相手>`。mock の画面は `/api` の外。死活監視とメトリクスは運用ポート（既定 9080）の `/actuator`。応答は [第 4.1 節](#41-openapi-の外のエンドポイント) | `/api/webhooks/stripe`、`/mock-payment`、`/mock-carrier` |
| リソースの名前 | 小文字の kebab-case の複数形。1 つしかないものは単数形。ログインしている本人は `/me` | `tax-categories`、`image-uploads`、`/shop`、`/me/cart`、`/me/orders`、`/orders/{orderId}/shipment` |
| パスのパラメータ | camelCase で、何の ID かを名前に書く。`{id}` にしない | `{productId}`、`{variantId}` |
| ネストの深さ | 親の ID は 2 つまで。集約の一部（バリアント、画像）は集約の下に置く。別の集約は親の下に置かず、上の階層に置く。まとまりの名前を前に置くことがある | `/products/{productId}/variants/{variantId}/image`、`/inventory/variants/{variantId}`、`/reports/...` |
| 操作を表す URL | 作成・取得・更新・削除で表せない操作は、リソースの下に動詞か状態の名前のセグメントを置く。並び順の変更は `.../order`。メソッドの選び方は [第 3 節](#3-http-メソッド) | `PUT /products/{productId}/publish`、`PUT /orders/{orderId}/cancellation-request/approve`、`POST /me/cart/merge`、`PUT /categories/order` |

## 3. HTTP メソッド

| メソッド | 使う場面 | 成功の応答 | 例 |
|----------|----------|------------|----|
| GET | 取得（一覧・詳細） | 200 | `GET /products` |
| POST | 作成 | 201 + `Location` + 作成した ID ※1 | `POST /products` |
| POST | 作成でない処理（カートの引き継ぎなど） | 200 で結果、結果がなければ 204 | `POST /me/cart/merge` |
| PUT | 更新（項目の置き換え、並び順、状態の切り替え） | 204。結果を返すものは 200 | `PUT /products/{id}`、`PUT /products/{id}/publish` |
| DELETE | 削除 | 204。結果を返すものは 200 | `DELETE /products/{id}` |
| PATCH | 使わない | — | — |

※1：操作の URL を持つもの（商品のバリアントの `/products/{productId}/variants/{variantId}` など、集約の一部でも）は、その URL を `Location` に入れる。単独の URL がないもの（商品の画像、履歴の 1 件など）は `Location` を付けず、本文で結果を返す。

- 更新の競合：同時に編集されうるものの更新は `version` を送り、合わなければ 409（`CONCURRENT_MODIFICATION`）。
- 状態の切り替え（公開・非公開、販売停止・再開など）は冪等にし、`version` を送らない。すでにその状態なら、状態を変えずに成功を返す。
- 日時は ISO 8601 のオフセット付き文字列。

## 4. HTTP ステータス

| コード | 場面 |
|--------|------|
| 200 | 取得。結果を返す更新・処理 |
| 201 | 作成 |
| 204 | 結果を返さない更新・削除・処理 |
| 400 | 入力・業務ルールの誤り |
| 401 | 認証がない・無効（`UNAUTHENTICATED`） |
| 403 | 権限がない（`FORBIDDEN`） |
| 404 | 見つからない。他人のデータ（会員のカート・注文など）も 404 ※2 |
| 409 | 更新の競合（`CONCURRENT_MODIFICATION`）など |
| 500 | システムの誤り |
| 502 | 外部サービスのエラー：外部のサービスに届かない、時間内に応答がない、その側の故障。しばらくしてから再試行できる ※3 |

※2：403 にすると、そのデータがあることを推測できてしまうため。

※3：外部サービスごとの分け方（どの失敗を 502 にし、どれを 400・500 にするか）は、[バックエンドのアーキテクチャ](../backend/architecture.md) 第 8 節。`SystemException` に、種類が外部サービスのエラーのエラーコードを付けて投げる。他のシステムの誤りと違い、エラーコードとメッセージを返す（画面が再試行を促せるように）。メッセージは、どのサービスに接続できないかを示す。OpenAPI では、外部サービスを呼ぶ操作に 502（`BadGateway`）とそのエラーコードを書く。

### 4.1 OpenAPI の外のエンドポイント

外部のサービスが呼ぶエンドポイントと、外部のサービスの代わりの mock の画面は、EC の API ではないので OpenAPI に書かない。応答は次のとおり。

| エンドポイント | 呼ぶもの | 応答 |
|----------------|----------|------|
| `POST /api/webhooks/stripe`（決済の通知） | Stripe | 受け付けたら 200、署名を確認できなければ 400。処理に失敗したら 5xx（Stripe の再送で再処理する） |
| `POST /api/webhooks/carrier`（物流の通知） | 物流サービス | 受け付けたら 200（どの出荷とも合わない通知、対応の表にない状態も、記録して 200）。署名を確認できなければ 401、本文を読めなければ 400。処理に失敗したら 5xx（再送で再処理する） |
| `/mock-payment/{pageId}`（mock の支払画面。決済の mock フラグのときだけ） | ブラウザー | 画面は 200 の HTML、開いていない画面は 404 の HTML。「支払う」「やめる」の操作は 303 で店舗の注文の画面に戻す |
| `/mock-carrier`（mock の物流の画面。物流の mock フラグのときだけ） | ブラウザー | 画面は 200 の HTML。通知を送ったら 303 で一覧に戻し、送れなければその旨を示した 200 の HTML。ない出荷は 404 の HTML |

- どれも認証を付けない。通知は署名で確認し、mock の画面は本番で公開しない（[バックエンドのアーキテクチャ](../backend/architecture.md) 第 8 節）。
- 本文は、エラーのときも Problem の形にしない（呼ぶ側は状態のコードだけを見る）。処理の失敗（5xx）だけは、共通の例外ハンドラー（`GlobalExceptionHandler`）が Problem で返す。

## 5. エラーの本文

Problem Details（RFC 9457）に、`errorCode`（フロントが分岐に使う）、`message`（画面にそのまま表示できる日本語）、`traceId`（全てのエラー）、`errors`（入力検証のときだけ。`[{ "field", "message" }]`）を加える。

- 入力の形は presentation（Bean Validation）、業務ルールは domain で検証する。
- 業務のエラーはドメインごとの `XxxErrorCode` と `BusinessException` / `SystemException` で投げ、`GlobalExceptionHandler` で変換する。
- DB の制約違反（一意性、外部キーなど）は、リポジトリで業務のエラーに変える（500 にしない）。

エラーのコードは、契約に次の 3 つで書く。

| 書くもの | 置き場所 | 中身 |
|----------|----------|------|
| 全体の列挙 | 各 API の `components.schemas.ErrorCode`。エラーの本文の `errorCode` はこの型 | その API が返しうる全てのエラーのコード |
| 操作ごとの一覧 | 各操作の `x-error-codes`（コードの配列） | その操作の実装が投げうる業務のエラーのコード。共通のエラーは書かない |
| 共通のエラー | 各 API の `components.schemas.ErrorCode` の `x-common-error-codes` | 全ての操作が返しうるエラー。バックエンドの共通のエラー（`CommonErrorCode`）の定数と一致する（認証、権限、入力の検証、見つからない、更新の競合、許可されていないメソッド、金額の誤り、復元の失敗、システムの失敗） |

- 全体の列挙は、操作ごとの一覧と共通のエラーの和と一致させる。バックエンドの全ての `XxxErrorCode` の定数は、どちらかの API の全体の列挙にあり、列挙の全ての値はバックエンドにある（単体テストで確認する）。
- 統合テストのエラーの応答の `errorCode` は、その操作の一覧か共通のエラーにあることを、テストの設定で全ての統合テストで確認する。操作の実装に新しいエラーを追加したら、その操作の一覧に追加する。
- 画面の型は全体の列挙から作る（openapi-typescript は拡張の項目から型を作らないため）。

## 6. ページング

- リクエスト：`page`（1 始まり）、`size`（既定 20、最大 100）、`sort`（endpoint ごとの値。例：`newest`。DB の列名は出さない）。
- レスポンス：`{ "items", "page", "size", "totalItems", "totalPages" }`。
- MVP はオフセット方式。並びの基準が同じ値なら最後に ID で順序を決め、ページ間で重複・欠落が起きないようにする（検索エンジンの導入時に再検討する）。
