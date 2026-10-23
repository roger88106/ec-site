# 段階 3：モジュールの間の呼び出しと設定の型

- 前提：段階 2
- 完了条件：DC-155-17〜19、21〜23 を満たす。追加した構成の検査の規則について、一時的に違反を入れて失敗することを確認した。関係する文書を更新した。`./gradlew check` と `scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 7 節、[spec](../spec.md) の DS-155-3、DS-155-4 と DC-155-17〜19、21〜23、DN-155-1〜5、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.3 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 6.1 節

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方は、[plan](../plan.md) の共通の決定の最後の項目のとおりである。

T155-03-01〜04 と T155-03-06 は、変えるファイルが重ならないので、互いに `[P]` である。T155-03-04 と T155-03-07 は同じファイルを変えるので、順に行う。構成の検査に規則を追加するタスクは、`allowEmptyShould(true)` を付けない（[plan](../plan.md) の共通の決定）。

- [x] **T155-03-01** [P] `ModuleStructureTest` に規則を追加する。他のモジュールの `api` を使うのは `infrastructure.directory` だけにする。許可の一覧は 1 件で、`member.api.CurrentMemberIds` を presentation から使う。規則はモジュールごとに作り、対象を「そのモジュールの `api` を、外のクラスが使う依存」と記述する。`api` を持つモジュールだけを対象にし、`allowEmptyShould` を付けない。許可の一覧にない使い方を一時的に置いて、失敗することを確認する（DC-155-17、18）。
  - 依存：なし
  - 範囲：`test/architecture/ModuleStructureTest.java`。使う側の `main/module/cart/presentation/CartController.java`、`main/module/order/presentation/{StoreCheckoutController,StoreOrderController}.java`、`main/module/storefront/presentation/StorefrontCartController.java` は、変更せず、検査が通ることを確認する。
- [x] **T155-03-02** [P] 腐敗防止層の実装の public を外す。cart の `CatalogApiDirectory`、`StockApiDirectory`、storefront の `CatalogApiDirectory`、`OrderApiDirectory`、`StockApiDirectory` である。規則を追加する：`*Directory` を実装する `infrastructure.directory` のクラスは public にしない。`TaxRateScheduleCache` は対象外にする。`allowEmptyShould` を付けず、対象が 0 件でないことを確認する。public な例のクラスで、規則が検出することを恒久のテストにする（DC-155-19）。
  - 依存：なし
  - 範囲：`main/module/cart/infrastructure/directory/{CatalogApiDirectory,StockApiDirectory}.java`（`:17`）、`main/module/storefront/infrastructure/directory/{CatalogApiDirectory,OrderApiDirectory,StockApiDirectory}.java`（`CatalogApiDirectory.java:29`、`OrderApiDirectory.java:20`、`StockApiDirectory.java:20`）、`test/architecture/NamingTest.java`（`:49-67`）。`test/module/storefront/infrastructure/directory/OrderApiDirectoryTest.java` は同じパッケージから呼ぶので、変更せず、テストが通ることを確認する。
- [x] **T155-03-03** [P] `ShippingProperties` を `shipping/infrastructure/` に、`NotificationProperties` を `notification/infrastructure/mail/` に移す。`ShippingConfig`、`NotificationConfig` の import を修正する。`ShopProperties` は application に残す（DC-155-21）。
  - 依存：なし
  - 範囲：`main/module/shipping/application/ShippingProperties.java`、`main/module/shipping/infrastructure/ShippingConfig.java`（`:22`）、`main/module/notification/application/NotificationProperties.java`、`main/module/notification/infrastructure/mail/SmtpMailer.java`、`main/module/notification/infrastructure/NotificationConfig.java`（`:11`）。参照するテストはない。`test/config/RequiredPropertiesTest.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-03-04** [P] `StorefrontConfig` を `storefront/infrastructure/` に移す。`ConfigurationPropertiesTest` に検査を追加する。`com.ecsite.module..` の `@Configuration` を付けたクラスは `..infrastructure..` に置く。`allowEmptyShould` を付けない。`infrastructure` の外に置いた例で、失敗することを確認する（DC-155-22）。
  - 依存：なし
  - 範囲：`main/module/storefront/application/StorefrontConfig.java`（`:10`）、`test/architecture/ConfigurationPropertiesTest.java`（`:17-28`）。`test/config/RequiredPropertiesTest.java`（`:7,97`）は、変更せず、テストが通ることを確認する。
- [x] **T155-03-05** 店舗の情報を application の view で返す。`StorefrontService` に店舗の情報を返す操作を追加し、`storefront/application/model/ShopView` を返す。`StorefrontShopController` は service を使い、`StorefrontMapper` は view から応答に変換する（メソッドチェーンの setter で書く）。presentation が `ShopProperties` を受けなくなったことを確認する（DC-155-23）。
  - 依存：T155-02-12
  - 範囲：`main/module/storefront/application/StorefrontService.java`、`main/module/storefront/application/model/ShopView.java`（新規）、`main/module/storefront/presentation/StorefrontShopController.java`（`:5,20,34`）、`main/module/storefront/presentation/StorefrontMapper.java`（`:24,147-149`）。`it/module/storefront/presentation/StorefrontShopTest.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-03-06** [P] レポートの設定を application の view で返す。新しい `reporting/application/ReportSettingsQueryService` が `ReportingProperties` を読み、`reporting/application/model/view/ReportSettingsView` を返す。`ReportController` は service を使い、`ReportMapper` が view から応答に変換する。presentation が `ReportingProperties` を受けなくなったことを確認する（DC-155-23）。
  - 依存：なし
  - 範囲：`main/module/reporting/application/ReportSettingsQueryService.java`（新規）、`main/module/reporting/application/model/view/ReportSettingsView.java`（新規）、`main/module/reporting/presentation/ReportController.java`（`:11,57,127`）、`main/module/reporting/presentation/ReportMapper.java`。`main/module/reporting/application/SalesTrendQueryService.java`（`:27,39`）は変更しない。`it/module/reporting/presentation/ReportPermissionApiTest.java` は、変更せず、テストが通ることを確認する。
- [x] **T155-03-07** 構成の検査を追加する。`com.ecsite.module.*.presentation..` のクラスは、`@ConfigurationProperties` を付けたクラスに依存しない。`api` は対象にしない（spec と同じ範囲）。`allowEmptyShould` を付けない。presentation に設定の型を使う例を一時的に置いて、失敗することを確認する（DC-155-23）。
  - 依存：T155-03-04〜06
  - 範囲：`test/architecture/ConfigurationPropertiesTest.java`
- [x] **T155-03-08** [バックエンドの書き方](../../../rules/backend-coding.md) を修正する。バックエンドの書き方 第 1.3 節に、`member.api.CurrentMemberIds` を presentation から使う例外と理由を書く。バックエンドの書き方 第 6.1 節に、モジュールの `@Configuration` は infrastructure（またはその下）に置くことと、presentation は設定の値の型を受け取らないことを追加する。規則の一覧の確認方法に、追加した自動の検査を反映する。
  - 依存：T155-03-01、T155-03-04、T155-03-07
  - 範囲：`docs/rules/backend-coding.md`（`:7-32,86-94,254-266`）
- [x] **T155-03-09** 各モジュールの設計書の「構成」を、段階 3 のコードに合わせる。shipping（設定の型の置き場所）、notification（`infrastructure/mail/` の設定の型）、storefront（`infrastructure/` の設定のクラス、店舗の情報の view、設定の型の説明）、reporting（設定の service と view）である。
  - 依存：T155-03-03〜06
  - 範囲：`docs/design/modules/shipping.md`（`:127-139`）、`docs/design/modules/notification.md`（`:45-53`）、`docs/design/modules/storefront.md`（`:15-22,40-42`）、`docs/design/modules/reporting.md`（`:126-139`）
- [x] **T155-03-10** 段階のレビューと完了の確認。段階のレビューは profile「厚め」で、一般の観点は汎用 2 回、重要な観点は高性能と汎用で実施する。重要な観点は、ファイルをまたぐ影響である。判定は spec 担当のセッションが行う。確認の内容は次のとおり。`./gradlew check` と `scripts/ci/check-all.sh` が通る。
  - 依存：T155-03-01〜09
- [x] **T155-03-11** 段階 3 のレビューの指摘を修正する。バックエンドの書き方の ※ の番号を付け替えたことで古くなった spec の DC-155-42 の参照（バックエンドの書き方 第 5.2 節 ※2）を ※3 に修正する。バックエンドの書き方 第 1.3 節の本文に、腐敗防止層の実装を public にしない規則を追加する（DC-155-19）。
  - 依存：T155-03-10
  - 範囲：`docs/specs/155-backend-structure/spec.md`、`docs/rules/backend-coding.md`
- [x] **T155-03-12** AC-84 の新しいテストに、作ったデータの後処理（`@AfterEach` で削除する）を戻す。理由：コミットした公開の商品が残ると、店舗の商品の一覧の件数を確認する統合テストが失敗するため（[plan](../plan.md) 第 6 節）。クラスの説明のコメントに理由を書く。`./gradlew check` の全体で、店舗の統合テストが通ることを確認する。
  - 依存：T155-03-11
  - 範囲：`it/module/order/presentation/CheckoutCartChangeApiTest.java`
