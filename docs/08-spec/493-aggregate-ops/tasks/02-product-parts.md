# 段階 2：商品の型の分割

- 前提：段階 1 が完了した。
- 完了条件：DC-493-5〜8 を満たす。次のテストが通る：`PublicationTest`、`VariantsTest`、`ProductImagesTest`、`ProductTest`、`ProductPublicationTest`、`ProductActionsTest`、`ProductInvariantsTest`、`ProductRestoreFailureTest`、`JooqProductRepositoryTest`。
- 参照：[plan](../plan.md)の「商品の型の分割」、[spec](../spec.md)の DS-493-2

## タスク

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T493-02-01** [テスト] 公開の値の型の単体テストを書き、レッドを確認する。各操作の遷移と結果、表示中の判定、一度でも表示したかの判定、不変条件の違反の拒否、その境界値を確認する。復元のテストには、公開の状態に違反する行の場合を追加する（DC-493-5、DC-493-7、DC-493-8）。
  - 依存：なし
  - 範囲：`PublicationTest`（新規）、`ProductRestoreFailureTest`
  - 確認方法：新しい型がないので、コンパイルで失敗する。テストを通すためにテストを弱めない
- [x] **T493-02-02** T493-02-01 のテストが通る実装を書く。公開の値の型を作り、列挙を `vo/` に移し、商品の集約が値の型に処理を任せる形にする。
  - 依存：T493-02-01
  - 範囲：`catalog/domain/product/`（`vo/Publication.java`、`vo/PublicationStatus.java`、`Product.java`）、公開状態の列挙を参照する全てのファイル（import の書き換えだけ）
  - 確認方法：T493-02-01 のテストと、既存の商品のテストが通る
- [x] **T493-02-03** [テスト] バリアントの一覧と画像の一覧の単体テストを書き、レッドを確認する。ID で探す・次の表示順の操作と、置き換え・追加・削除・並べ替えの操作を確認する。画像の数の上限の検査も確認する。復元のテストには、画像の数が上限を超える行の場合を追加する（DC-493-6〜8）。
  - 依存：T493-02-02
  - 範囲：`VariantsTest`、`ProductImagesTest`（どちらも新規）、`ProductRestoreFailureTest`
  - 確認方法：新しい型がないので、コンパイルで失敗する。テストを通すためにテストを弱めない
- [ ] **T493-02-04** T493-02-03 のテストが通る実装を書く。一覧の型を作り、商品の集約の重複した処理を、一覧の型の操作に置き換える。
  - 依存：T493-02-03
  - 範囲：`catalog/domain/product/`（`Variants.java`、`ProductImages.java`、`Product.java`）、`JooqProductRepository`
  - 確認方法：T493-02-03 のテストと、既存の商品のテスト、`JooqProductRepositoryTest` が通る
- [ ] **T493-02-05** 段階の最後のレビュー。変えたコードを書き方の規則と照合し、振る舞いを変えていないことを見る。指摘を修正する。
  - 依存：T493-02-01〜04
  - 確認する項目：変えたコードを[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。新しい型の例外のメッセージとログに、DB の値と個人の情報を含めない（DN-493-4）。
  - 結果：未実施。1 つの表に、レビューごとに 1 行で書く（列の形は skill `review` の手順 6）。
