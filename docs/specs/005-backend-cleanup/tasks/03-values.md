# 段階 3：値と入力の検証

- 前提：段階 2
- 完了条件：DC-31〜35。契約テストが通る。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 2 節

## タスク

- [ ] **T5301** API の契約の制約の漏れの一覧と、足す値（`version` は `minimum: 0`、`contentLength` は 10MB まで、`delta` の範囲、`expectedTotal` は `minimum: 0`、都道府県の `maxLength` など）を、spec の AC と DB の列に照らして作り、利用者に確かめる。
- [ ] **T5302** [テスト] `OpenApiRequestConstraintTest`：リクエストの数値の項目に範囲、文字の項目に長さか形式。今の契約で失敗することを確かめる（DC-33）。
- [ ] **T5303** `admin.yaml`・`store.yaml` に T5301 の制約を足す。T5302 を通す。足した制約の「400 になる」代表の統合テストを足す（長さ・範囲・形式の種類ごとに、無いものだけ）。
- [ ] **T5304** [テスト] `FieldConstraintContractTest`：同じ項目の契約・ドメインの定数・DB の列の長さを表で照らす。食い違いが見つかったら、直す前に利用者に確かめる（DC-32、DN-6）。
- [ ] **T5305** [テスト] `BoundedText` の単体テスト（空白、code point の数え方、上限ちょうど）。
- [ ] **T5306** `common/model/value/BoundedText` を作り、各ドメインの文字の判定を置き換える（`TaxCategory`、`Category`、`Product`、`ShippingAddress`、`StockAdjustment`、`Order`）。T5305 と既存のテストが通る（DC-31）。
- [ ] **T5307** `common/model` を `base/`・`value/` に分ける（`git mv` の commit を分ける。DC-35）。
- [ ] **T5308** [テスト] ArchUnit：`api` の record と command に、金額の名前の整数の項目がない。今のコードで失敗することを確かめる。
- [ ] **T5309** モジュールの間の公開の型と command の金額を `Money` にする。整数は presentation と infrastructure だけ。T5308 と既存のテストが通る（DC-34）。
