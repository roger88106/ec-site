# 段階 1：解放と破棄の例外

- 前提：plan と tasks の承認。issue 担当の開始の合図。全体のブランチの取り込みと、実装を始める前の照合（[開発の進め方](../../../05-開発規則/workflow.md)の「実装を始める前の照合」）。
- 完了条件：DC-489-6、7 を満たし、変えたテストのクラスが通る。
- 参照：[plan](../plan.md)の「解放」「破棄の例外」、[spec](../spec.md)の DS-489-2、DS-489-3

## タスク

実装は、汎用のサブエージェント（実装役）に 1 タスクずつ任せる。

- [ ] **T489-01-01** [P] [テスト] リポジトリの統合テストと、サービスの単体テストを書く（DC-489-7）。統合テスト：記録をまとめて削除する操作が、渡した ID のうち、記録があったものだけを返し、2 回目は空を返す。単体テスト：リポジトリが、渡された ID の一部だけを返すとき、返した分だけイベントを発行する。
  - 依存：なし
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/media/infrastructure/persistence/JooqAssetRepositoryTest.java`、`backend/ecsite/src/test/java/com/ecsite/module/media/application/MediaServiceReleaseTest.java`（新規）
  - 確認方法：コンパイルで失敗する（レッド）
- [ ] **T489-01-02** T489-01-01 のテストが通る実装を書く（DC-489-7）。
  - 依存：T489-01-01
  - 範囲：`AssetRepository`、`JooqAssetRepository`、`MediaService` の解放
  - 確認方法：T489-01-01 の 2 つのテストと、`MediaApiTest` が通る
- [ ] **T489-01-03** [P] [テスト] 画像の追加の単体テストを書く（DC-489-6）。保存が競合の例外を投げ、破棄も例外を投げるとき、競合の例外が投げられ、破棄の例外が抑制された例外にある。
  - 依存：なし
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/module/catalog/application/product/ProductImageGatewayServiceTest.java`（新規）
  - 確認方法：今のコードで失敗する（破棄の例外が投げられる）
- [ ] **T489-01-04** T489-01-03 のテストが通る実装を書く（DC-489-6）。
  - 依存：T489-01-03
  - 範囲：`ProductImageGatewayService` の画像の追加
  - 確認方法：T489-01-03 の単体テストと、`ProductImageTransactionApiTest` が通る
- [ ] **T489-01-05** 段階の最後のレビュー（厚め）。指摘を修正する。
  - 依存：T489-01-01〜04
  - 確認する項目：変えたコードを[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。
  - 結果：
