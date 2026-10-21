# 段階 5：重複と大きいクラス

- 前提：段階 4
- 完了条件：DC-39、40。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 4.1、4.2 節

## タスク

- [x] **T5501** [テスト] `CurrentMemberIds` の単体テスト：`require()` はログインがなければ 401 の例外、`find()` は空、会員の ID の求め方。
- [x] **T5502** `member/api/CurrentMemberIds` を作り、4 つの Controller の `requester()`・`memberId()` を置き換える。T5501 と既存のテストが通る（DC-39）。
- [x] **T5503** [テスト] 並び順の再採番の部品の単体テスト（移動、末尾への追加、存在しない ID）。
- [x] **T5504** 並び順の部品を `common/model/value/` に作り、商品（バリアント、画像）とカテゴリで使う。T5503 と既存のテストが通る。
- [x] **T5505** `ProductCommandService` を、商品、バリアント、公開、画像に分ける（取引の境界は今のまま）。既存のテストが通る。`Product` の行数を報告する（DC-40）。

## 結果（T5505）

- `ProductCommandService`（312 行）を、商品（登録・更新・削除、169 行）、`VariantCommandService`（100 行）、`ProductPublicationService`（74 行）に分けた。画像は今の `ProductImageService`。商品の取り出し（版の確認）と状態の切り替えは、4 つで共通の `ProductLoader` にまとめた。
- `Product` は 669 行で、目安の 400 行を超える。集約は分けない（spec の Non-goals）。文字の判定と並び順は共通の部品に移した。残りは集約の不変条件（軸、バリアント、画像、公開）で、段階 7 の走査でさらに減らせる所を探す。
