# API の規約

[constitution](../constitution.md) 第 8 節の細則。契約の正本は `api/openapi/`（`store.yaml`：店舗、`admin.yaml`：管理画面）、実装の書き方は [バックエンドの書き方](../backend/coding.md) 第 2、3 節。

## 1. 契約先行

RESTful JSON。OpenAPI を plan で書き、バックエンドは生成したインターフェース（OpenAPI Generator の `spring`）を実装し、フロントは生成した型（`openapi-typescript`）を使う。生成物はコミットしない。

## 2. HTTP メソッド

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
- 状態の切り替え（公開・非公開、販売停止・再開など）は冪等にし、`version` を送らない。すでにその状態なら何もせず成功を返す。
- 日時は ISO 8601 のオフセット付き文字列。

## 3. HTTP ステータス

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
| 502 | 外部のサービス（決済サービスなど）に接続できない。やり直せる ※3 |

※2：403 にすると、そのデータがあることを推測できてしまうため。

※3：`SystemException` に、種類が「外部のサービスに接続できない」のエラーコードを付けて投げる。他のシステムの誤りと違い、エラーコードとメッセージを返す（画面がやり直しを促せるように）。

## 4. エラーの本文

Problem Details（RFC 9457）に、`errorCode`（フロントが分岐に使う）、`message`（画面にそのまま出せる日本語）、`traceId`（すべてのエラー）、`errors`（入力検証のときだけ。`[{ "field", "message" }]`）を加える。

- 入力の形は presentation（Bean Validation）、業務ルールは domain で検証する。
- 業務のエラーはドメインごとの `XxxErrorCode` と `BusinessException` / `SystemException` で投げ、`GlobalExceptionHandler` で変換する。
- DB の制約違反（一意性、外部キーなど）は、リポジトリで業務のエラーに変える（500 にしない）。

エラーのコードは、契約に次の 3 つで書く。

| 書くもの | 置き場所 | 中身 |
|----------|----------|------|
| 全体の列挙 | 各 API の `components.schemas.ErrorCode`。エラーの本文の `errorCode` はこの型 | その API が返しうるすべてのエラーのコード |
| 操作ごとの一覧 | 各操作の `x-error-codes`（コードの配列） | その操作の実装が投げうる業務のエラーのコード。共通のエラーは書かない |
| 共通のエラー | 各 API の `components.schemas.ErrorCode` の `x-common-error-codes` | すべての操作が返しうるエラー。バックエンドの共通のエラー（`CommonErrorCode`）の定数と同じ（認証、権限、入力の検証、見つからない、更新の競合、許可されていないメソッド、金額の誤り、復元の失敗、システムの失敗） |

- 全体の列挙は、操作ごとの一覧と共通のエラーの和と一致させる。バックエンドのすべての `XxxErrorCode` の定数は、どちらかの API の全体の列挙にあり、列挙のすべての値はバックエンドにある（単体テストで確かめる）。
- 統合テストのエラーの応答の `errorCode` は、その操作の一覧か共通のエラーにあることを、テストの設定ですべての統合テストで確かめる。操作の実装に新しいエラーを足したら、その操作の一覧に足す。
- 画面の型は全体の列挙から作る（openapi-typescript は拡張の項目から型を作らないため）。

## 5. ページング

- リクエスト：`page`（1 始まり）、`size`（既定 20、最大 100）、`sort`（endpoint ごとの値。例：`newest`。DB の列名は出さない）。
- レスポンス：`{ "items", "page", "size", "totalItems", "totalPages" }`。
- MVP はオフセット方式。並びの基準が同じ値なら最後に ID で順序を決め、ページをまたいで重複・欠落を出さない（検索エンジンの導入時に見直す）。
