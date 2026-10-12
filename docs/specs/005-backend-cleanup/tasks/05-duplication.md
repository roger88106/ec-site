# 段階 5：重複と大きいクラス

- 前提：段階 4
- 完了条件：DC-39、40。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 4.1、4.2 節

## タスク

- [x] **T5501** [テスト] `CurrentMemberIds` の単体テスト：`require()` はログインがなければ 401 の例外、`find()` は空、会員の ID の求め方。
- [x] **T5502** `member/api/CurrentMemberIds` を作り、4 つの Controller の `requester()`・`memberId()` を置き換える。T5501 と既存のテストが通る（DC-39）。
- [x] **T5503** [テスト] 並び順の付け直しの部品の単体テスト（移動、末尾への追加、存在しない ID）。
- [x] **T5504** 並び順の部品を `common/model/value/` に作り、商品（バリアント、画像）とカテゴリで使う。T5503 と既存のテストが通る。
- [ ] **T5505** `ProductCommandService` を、商品、バリアント、公開、画像に分ける（取引の境界は今のまま）。既存のテストが通る。`Product` の行数を報告する（DC-40）。
