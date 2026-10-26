# 段階 1：テスト専用の操作と可視性

- 前提：plan が承認された。実装を始める前の照合（[開発の進め方](../../../rules/workflow.md) 第 5.6 節）を済ませた。
- 完了条件：DC-192-1〜4 を満たす。替えたテストは、一時的にコードか期待値を変えて失敗することを確認した。地域のマイグレーションの生成結果が変わらない。`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 10 節の 1、[spec](../spec.md) の DS-192-1 と DC-192-1〜4、DN-192-1〜5

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方は、[plan](../plan.md) の共通の決定のとおりである。削除の前に、表の外の呼び出し元がないことを確認する。

T192-01-01、02、04、05、06 は、それぞれ別のモジュールとテストを変えるので `[P]` である。T192-01-03 は地域の設計書を T192-01-02 と共に変えるので、その後に行う。T192-01-07 は、出荷の mock と決済のクラスを他のタスクと共に変えうるので、最後に行う。

- [x] **T192-01-01** `[P]` 注文がキャンセル済みかを返す操作、集計基準が注文の状態を数えるかを返す操作、商品の代表画像を返す操作を削除し、単体テストを本番の操作で確認する形にする（DC-192-1）。用語集の「代表画像」の識別子の列を直す。
  - 依存：なし
  - 範囲：`main/module/order/domain/order/Order.java`、`OrderCancellationTest.java`、`main/module/reporting/domain/sales/Basis.java`、`BasisTest.java`、`main/module/catalog/domain/product/Product.java`、`ProductTest.java`、`docs/design/glossary.md`
- [x] **T192-01-02** `[P]` 地域 API の郵便番号検索と応答の型を削除する。AC-253、AC-254 のテストを店舗 API のテストに移し、AC-254 の並び順と、2 つの市区町村にまたがる例の確認を加える（DC-192-1）。地域の設計書と、AC 一覧のテストの列を直す。
  - 依存：なし
  - 範囲：`main/module/region/api/`、`it/module/region/api/RegionApiTest.java`、`it/module/region/presentation/StoreRegionApiTest.java`、`docs/design/modules/region.md`、`docs/design/acceptance-criteria.md`
- [x] **T192-01-03** 郵便番号の CSV を読む処理を tools に移し、Lombok を Java の書き方に置き換える。テストを `test/tools/region/` に移し、単体テストのソースセットのクラスパスに tools の出力を加える。`./gradlew generateRegionMigration` の出力が今のマイグレーションと一致することを確認する（DC-192-2、DN-192-2）。
  - 依存：T192-01-02（地域の設計書を共に変えるため）
  - 範囲：`main/module/region/infrastructure/source/`、`tools/tools/region/`、`test/module/region/infrastructure/source/PostalCodeCsvTest.java`、`test/tools/region/`、`build.gradle.kts` の source set、`docs/design/modules/region.md`
- [x] **T192-01-04** `[P]` 最近の出荷を一覧する問い合わせを、出荷の Repository の port と実装から外し、mock 側の問い合わせのクラスに移す。テストも mock の側に移す（DC-192-3）。出荷の設計書を直す。
  - 依存：なし
  - 範囲：`main/module/shipping/application/port/ShipmentRepository.java`、`main/module/shipping/infrastructure/persistence/JooqShipmentRepository.java`、`main/module/shipping/infrastructure/mock/`、`it/module/shipping/infrastructure/persistence/JooqShipmentRepositoryTest.java`、`it/module/shipping/infrastructure/mock/`、`docs/design/modules/shipping.md`
- [x] **T192-01-05** `[P]` ファイル保存設定の複製 2 つを削除し、テストは設定の型を直接作る。ファイル保存のクライアントを作る static の操作を `@Bean` メソッドに戻し、テストはその `@Bean` メソッドを呼ぶ（DC-192-3）。
  - 依存：なし
  - 範囲：`main/module/media/infrastructure/storage/MediaStorageProperties.java`、`MediaStorageConfig.java`、`it/module/media/infrastructure/storage/S3ObjectStorageTest.java`、`test/module/media/infrastructure/storage/S3ObjectStorageFailureTest.java`
- [x] **T192-01-06** `[P]` 税率キャッシュの破棄を package-private にし、クラスの public を外す。4 つの統合テストは、統合テストの同じパッケージのテストの補助クラスから破棄する（DC-192-3、[plan](../plan.md) 第 10 節の 1 の案 A）。
  - 依存：なし
  - 範囲：`main/module/catalog/infrastructure/directory/TaxRateScheduleCache.java`、`it/module/catalog/infrastructure/directory/`（新しい補助クラス）、`StoreCatalogApiTest.java`、`StoreProductSearchTest.java`、`StorefrontApiTest.java`、`StorefrontFreshnessTest.java`
- [x] **T192-01-07** 決済の期限切れジョブを package-private にし、注文のテストは統合テストの同じパッケージのテストの補助クラスから実行する（DC-192-4）。[plan](../plan.md) 第 1.4 節の表の 9 クラスが public である理由を確認し、テストのためだけに広げたものを package-private にする。残したものは、理由と共に段階の報告に書く。
  - 依存：T192-01-01〜06
  - 範囲：`main/module/payment/application/PaymentExpiryJob.java`、`it/module/payment/application/PaymentJobs.java`（新規）、`it/module/order/presentation/OrderPaymentApiTest.java`、[plan](../plan.md) 第 1.4 節の表の 9 クラスとその呼び出し元
- [x] **T192-01-08** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` を実行する。DC-192-1〜4 を満たし、替えたテストが元のテストと同じ条件を確認していることを確認する。指摘を修正する。
  - 依存：T192-01-01〜07

## 段階のレビューの結果

2026-10-26。profile「厚め」。一般の観点（汎用）は高 0・必須 0、重要な観点（高性能。ファイルをまたぐ影響、境界と依存、安定性）は高 0・必須 2。結果は `/.local/review/202610261200-192-s1/`。

| 指摘 | 扱い |
|------|------|
| 単体テストのクラスパスに tools の出力を加えたため、構成の検査が読む本番のクラスに tools のクラスが入る（plan 第 1.2 節と食い違う） | 修正した。構成の検査の読み込みで tools の出力を除き、除いたことを確認するテストを加えた（機械の検査に移した） |
| tools に移した CSV の読み込みの Javadoc が、移す前の使われ方のまま | 修正した |

- 全体の検査で、外部サービスの選択の単体テストが、mock の出荷の問い合わせの依存がなく失敗した。テストのコンテキストに依存を与えて直した。
- 実装の中で、plan と違う扱いを 2 つ決めた。AC-283 のテストも、地域 API の郵便番号検索のテストと一緒に店舗 API のテストに移した（同じ操作を使うため）。テストのためだけに public だったクラスのうち、決済の注文のサービスと 2 つのイベントの型を package-private にし、設定の型 3 つと mock 3 つは、他のパッケージのテストの設定と testFixtures が型として使うので残した。
- 地域のマイグレーションを再生成すると、データは一致し、先頭のコメントの文書のパス（古い `docs/backend/`）だけが違う。マイグレーションは変えないので、起票の案に入れる。
- `./gradlew check`、`./gradlew pitest`（652 の変異のうち 594 を検出、91%）、`scripts/ci/check-all.sh` が通った。
