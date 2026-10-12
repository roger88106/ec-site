# 段階 3：値と入力の検証

- 前提：段階 2
- 完了条件：DC-31〜35。契約テストが通る。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 2 節

## タスク

- [x] **T5301** API の契約の制約の漏れの一覧と、足す値（`version` は `minimum: 0`、`contentLength` は 10MB まで、`delta` の範囲、`expectedTotal` は `minimum: 0`、都道府県の `maxLength` など）を、spec の AC と DB の列に照らして作り、利用者に確かめる。
- [x] **T5302** [テスト] `OpenApiRequestConstraintTest`：リクエストの数値の項目に範囲、文字の項目に長さか形式。今の契約で失敗することを確かめる（DC-33）。
- [x] **T5303** `admin.yaml`・`store.yaml` に T5301 の制約を足す。T5302 を通す。足した制約の「400 になる」代表の統合テストを足す（長さ・範囲・形式の種類ごとに、無いものだけ）。
- [x] **T5304** [テスト] `FieldConstraintContractTest`：同じ項目の契約・ドメインの定数・DB の列の長さを表で照らす。食い違いが見つかったら、直す前に利用者に確かめる（DC-32、DN-6）。
- [x] **T5305** [テスト] `BoundedText` の単体テスト（空白、code point の数え方、上限ちょうど）。
- [x] **T5306** `common/model/value/BoundedText` を作り、各ドメインの文字の判定を置き換える（`TaxCategory`、`Category`、`Product`、`ShippingAddress`、`StockAdjustment`、`Order`）。T5305 と既存のテストが通る（DC-31）。
- [x] **T5307** `common/model` を `base/`・`value/` に分ける（`git mv` の commit を分ける。DC-35）。
- [ ] **T5308** [テスト] ArchUnit：`api` の record と command に、金額の名前の整数の項目がない。今のコードで失敗することを確かめる。
- [ ] **T5309** モジュールの間の公開の型と command の金額を `Money` にする。整数は presentation と infrastructure だけ。T5308 と既存のテストが通る（DC-34）。

## 足す制約の一覧（T5301）

2026-10-13 に作った。利用者の review の前に、この一覧で進める（利用者の指示）。
今は業務のエラー（固有のエラーコード）で断っている値を、契約の制約で先に断らない。応答のエラーコードが変わり、画面とテストが頼っている扱いが崩れるため（DN-4）。そのような項目では、業務の規則より外側の範囲だけを契約に書く。

| 項目 | 足す制約 | 根拠 |
|------|----------|------|
| `version`（本文 9、クエリ 3。admin） | `minimum: 0` | 楽観ロックの番号は 0 から上がる |
| 画像の受付の `contentLength` | `minimum: 0` | 上限（10MB）と 0 は、業務のエラー `IMAGE_TOO_LARGE` で断っている（テストあり）。契約の上限は書かない |
| 画像の受付の `contentType` | `maxLength: 100` | 対応しない形式は、業務のエラー `UNSUPPORTED_IMAGE_FORMAT` で断っている。enum にしない |
| 在庫の調整の `delta` | `minimum: -2147483648`、`maximum: 2147483647` | 業務の上限はない（0 は業務のエラー）。DB の列（int）の範囲 |
| 店舗の注文の `expectedTotal` | `minimum: 0` | 合計は 0 以上 |
| お届け先の `prefecture` | `maxLength: 4` | DB の列（varchar(4)）。最も長い都道府県の名前も 4 文字。47 都道府県かは、業務のエラー `INVALID_PREFECTURE` で確かめる（店舗画面が入力欄に結び付けている） |

- 数値の項目は、下限か上限の少なくとも一方があれば「範囲がある」とする（`page` の `minimum: 1`、`priceExclTax` の `minimum: 1` は今のまま）。
- 配列の項目の数（`maxItems`）は、spec の対象（数値と文字の項目）の外なので、検査しない。
