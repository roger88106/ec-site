# 段階 3：設定と契約の補助、並び順の既定値

- 前提：段階 1。[plan](../plan.md) 第 8 節の Q1 の答え（推奨。店舗の presentation に残す）
- 完了条件：DC-186-11、15 を満たす。補助へ移したテストの確認は変わっていない。消費者に見える並び順は変わらない。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-186-3、5 と DC-186-11、15、DN-186-1、4

## タスク

T186-03-01〜03 は、それぞれ別のファイルだけを変えるので `[P]` である。

- [x] **T186-03-01** [P] 設定の補助 `TestSettings`（profile を重ねた設定ファイルの読み込みと、設定の型への値の当てはめ）を作る。設定のテスト 5 つと支払の設定のテスト 2 つで、各テストに重複していた定義を、補助の呼び出しに置き換える（DC-186-11）。
  - 依存：なし
  - 範囲：`test/TestSettings.java`（新規）、`test/config/ProfileFlagsTest.java`、`ManagementPortConfigTest.java`、`CleanupJobSettingsTest.java`、`test/config/event/EventCleanupJobSettingsTest.java`、`EventResubmissionSettingsTest.java`、`test/module/payment/application/PaymentExpiryJobLoggingTest.java`、`test/module/payment/infrastructure/stripe/StripeConfigTest.java`
- [x] **T186-03-02** [P] [テスト] 契約の補助 `Contracts` を作り、契約と realm の置き場所が渡されないときに失敗することを確認する。契約のテスト 6 つの置き場所の読み取りと YAML の読み込みを補助の呼び出しに置き換え、既定の置き場所を削除する。`AdminApiPermissionContractTest` の `listOperations` の Javadoc に、本番の `OpenApiOperations` を使わない理由を書く（[plan](../plan.md) 第 3.1 節。DC-186-11）。
  - 依存：なし
  - 範囲：`test/contract/Contracts.java`（新規）、`test/contract/ContractsTest.java`（新規）、`test/contract/` の 6 つのテスト
- [x] **T186-03-03** [P] 店舗向けの並び順の既定値を、Q1 の答えの層だけに置く。変えるファイルとテストの修正は、[plan](../plan.md) 第 3.2 節の、答えに合う項目のとおりにする。店舗の API の既定の並びの統合テストが通る（DC-186-15）。
  - 依存：なし
  - 範囲（推奨のとき）：`main/module/catalog/api/StoreProductQuery.java`、`StoreCatalogApi.java`、`main/module/catalog/application/model/query/StoreProductListQuery.java`、`main/module/catalog/infrastructure/persistence/JooqStoreProductQueryRepository.java`、`main/module/storefront/infrastructure/directory/CatalogApiDirectory.java`（変えなくてよいかの確認）、`test/module/catalog/api/StoreCatalogApiSortTest.java`、`it/module/catalog/api/StoreCatalogApiTest.java`、`StoreProductSearchTest.java`
  - 範囲（別案のとき）：`main/module/storefront/presentation/StorefrontMapper.java`、`main/module/catalog/application/model/query/StoreProductListQuery.java`、`main/module/catalog/infrastructure/persistence/JooqStoreProductQueryRepository.java` と、それらのテスト
- [x] **T186-03-04** [テストの書き方](../../../rules/test-writing.md) 第 3.4 節（T186-01-08 と同じ節。段階 1 のレビューで、テストの置き場所の節から分けた）に `TestSettings` を書く。用語集の識別子の検査が求めるときは、用語集に追加する（[plan](../plan.md) 第 5 節）。
  - 依存：T186-03-01
  - 範囲：`docs/rules/test-writing.md`、`docs/design/glossary.md`
- [x] **T186-03-05** 段階のレビューと全体の検査：[plan](../plan.md) 第 7 節の段階 3 の観点とレビュー役で行う。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T186-03-01〜04

## 段階のレビューの結果

profile「厚め」で、汎用のレビュー役 2 つ（規則と重複、確認の維持）が行った（高 0、必須 1、推奨 11）。結果は `/.local/review/202610261700-186-stage3/` にある。

- 修正した：店舗の presentation の並び順の引数の `@Nullable`（必須）。並び順の既定値の単体テスト（既定値が 1 つの層だけになり、守るテストがなかった）。realm の設定を読む処理を契約の補助に移した。補助の Javadoc（例外、戻り値）、設定の値が null のキーの扱い、テストの書き方の規則の一覧の文言。
- 残した：system property の置き場所の読み方を、構成の検査（`rules.backend-coding`、`design.overview`）と契約の補助でそろえること（本 spec の範囲の外。値がないときの失敗の分かりやすさだけの違い）。
- 起票の案に入れる：統合テストの `KeycloakIntegrationTest` と `StoreApiTokenContractTest` が、契約と realm を既定の相対パスで読むこと。
- 補助へ移した 14 のテストの確認は、変わっていないことを差分で確認した。`./gradlew check`（並び順の変更の後）、`./gradlew test`、`scripts/ci/check-all.sh`（修正の後）は通った。
