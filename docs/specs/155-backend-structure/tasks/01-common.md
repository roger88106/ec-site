# 段階 1：注文の AC のテスト、共通コードと設定

- 前提：spec 17 のマージ（`09730eeb`）の後。plan が承認された。
- 完了条件：DC-155-1〜7、43、47、48 を満たす。AC-84、AC-128 のテストについて、一時的に期待値を変えて失敗することを確認した。追加した構成の検査の規則について、一時的に違反を入れて失敗することを確認した。関係する文書を更新し、AC 一覧のテストの列を再生成した。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 7 節、[spec](../spec.md) の DS-155-1、DS-155-9 と DC-155-1〜7、43、47、48、DN-155-1〜5、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[バックエンドの書き方](../../../rules/backend-coding.md)、[テストの書き方](../../../rules/test-writing.md)

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方は、[plan](../plan.md) の共通の決定の最後の項目のとおりである。

T155-01-01〜06 は、変えるファイルが重ならないので、互いに `[P]` である。T155-01-06 と T155-01-07 は同じファイルを変えるので、順に行う。構成の検査に規則を追加するタスクは、`allowEmptyShould(true)` を付けない（[plan](../plan.md) の共通の決定）。

- [x] **T155-01-01** [P] [テスト] AC-84 のテストを、新しい非トランザクションの統合テストのクラスに移す。カートの変更の API（数量を減らす `PUT`）を呼び、`AsyncEvents.awaitHandled` でカートの変更のイベントの処理を待ってから、販売可能数が戻ることを確認する。品目の追加の後も、注文手続きを始める前にイベントの処理を待つ。作ったデータは `@AfterEach` で削除する。`CheckoutApiTest` から AC-84 のテストと `CheckoutService` の注入を削除し、クラスの説明のコメントを修正する。テスト名に AC-84 を入れる。一時的に期待値を変えて、失敗することを確認する（DC-155-47）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/CheckoutCartChangeApiTest.java`（新規）、`it/module/order/presentation/CheckoutApiTest.java`（`:33-37,49,70,152-171`）
- [x] **T155-01-02** [P] [テスト] AC-128 のテストを強める。持ち主の `sub` を変数に保持し、会員の表から会員 ID を取得して、注文の会員 ID と等しいことを確認する。一時的に期待値を変えて、失敗することを確認する（DC-155-48）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/MemberOrderApiTest.java`（`:76,161-166`）
- [x] **T155-01-03** [P] イベントの再発行と片付けの 6 クラスを `config/event/` に移す。`@Scheduled` の式にある設定の型の Bean の名前の文字列 2 か所を修正する。テスト 4 つを `config/event` のパッケージに移す。`CleanupJobSettingsTest` は、片付けのジョブの部分だけを `test/config/event/` の新しいテストに分ける。移した後、config 直下が 6 クラスになることを確認する（DC-155-1、2）。
  - 依存：なし
  - 範囲：`main/config/{EventCleanupConfig,EventCleanupJob,EventPublicationMetrics,EventPublicationRecords,EventResubmissionConfig,EventResubmissionJob}.java`（`EventCleanupJob.java:72-73`、`EventResubmissionJob.java:63-64`）、`test/config/CleanupJobSettingsTest.java`（`:29,52,53,68,74`）、`test/config/EventResubmissionSettingsTest.java`、`it/config/EventCleanupJobTest.java`、`it/config/EventResubmissionJobTest.java`
- [x] **T155-01-04** [P] 表示順の再採番（`DisplayOrders`）を `module/catalog/domain/` の直下に移し、`DisplayOrdersTest` も `test/module/catalog/domain/` に移す。`Category` と `Product` の import を修正する（DC-155-3、4）。
  - 依存：なし
  - 範囲：`main/common/model/value/DisplayOrders.java`、`main/module/catalog/domain/category/Category.java`（`:6,144`）、`main/module/catalog/domain/product/Product.java`（`:7,337,593,613`）、`test/common/model/value/DisplayOrdersTest.java`
- [x] **T155-01-05** [P] LIKE の検索の文字の扱い（`LikePatterns`）を `module/catalog/infrastructure/persistence/` に移し、クラスとメソッドの public を外す。`JooqProductQueryRepository`、`PostgresProductSearch` の import を削除する（DC-155-3、4）。
  - 依存：なし
  - 範囲：`main/common/persistence/LikePatterns.java`、`main/module/catalog/infrastructure/persistence/JooqProductQueryRepository.java`（`:6,106,107,111,114`）、`main/module/catalog/infrastructure/persistence/PostgresProductSearch.java`（`:5,56,60,61`）
- [x] **T155-01-06** [P] `ModuleStructureTest.restControllerOnlyInPresentation` の対象を `com.ecsite..` に広げ、`MeController` を完全修飾名で除外する。表示名に「`MeController` を除く」を追加する。`MeController` 以外のクラスに `@RestController` を一時的に付けて、失敗することを確認する（DC-155-5）。
  - 依存：なし
  - 範囲：`test/architecture/ModuleStructureTest.java`（`:144-151`）
- [x] **T155-01-07** `ModuleStructureTest` に 2 つの規則を追加する。共通コードと設定（`com.ecsite.common..`、`com.ecsite.config..`）はモジュールに依存しない。モジュールは設定に依存しない。`allowEmptyShould` を付けない。一時的に違反を入れて、失敗することを確認する（DC-155-6、7）。
  - 依存：T155-01-06
  - 範囲：`test/architecture/ModuleStructureTest.java`
- [ ] **T155-01-08** [バックエンドの書き方](../../../rules/backend-coding.md) を、段階 1 のコードに合わせる。バックエンドの書き方 第 1.1 節に「モジュールの domain の直下には、複数の集約が使う値を置く」を追加する。バックエンドの書き方 第 1.4 節の表に `config.security`（11）の行を追加し、`catalog.infrastructure.persistence` を 14 に修正する。バックエンドの書き方 第 1.5 節に「共通コードには、2 つ以上のモジュールが使うものだけを置く」を追加する。バックエンドの書き方 第 2.1 節の例外の段落に `MeController` の例外と理由を書く。規則の一覧と依存の表に、共通コードと設定の依存の規則の行を追加する。
  - 依存：T155-01-03〜07
  - 範囲：`docs/rules/backend-coding.md`
- [ ] **T155-01-09** 共通コードの設計書を修正する（DC-155-43）。修正の内容は次のとおり。
  - 共通コードの設計書：エラーの詳細、認証の設定の値の型、契約の操作の一覧を一覧に追加する。表示順の再採番と LIKE の検索の行を削除する。イベントの 6 クラスの置き場所を `config/event/` に修正する。
  - 共通コードの設計書の調査のメモ 2 行を削除し、基準はバックエンドの書き方 第 1.5 節を指す。
  - 商品カタログの設計書の「構成」に、domain 直下の表示順の再採番と LIKE の検索の文字の扱いを追加する。
  - バックエンドのアーキテクチャの「処理の済んだイベント」の持ち主の記述が、移動後のパッケージと合うか確認する。
  - 依存：T155-01-08
  - 範囲：`docs/design/common.md`（`:3,15,19,24,26,30,31,41,42,44,72`）、`docs/design/modules/catalog.md`（`:140-156`）、`docs/design/backend-architecture.md`（`:107`）
- [ ] **T155-01-10** AC 一覧のテストの列を `node scripts/ci/ac-check.mjs --write` で再生成し、`ac-check.mjs` が通ることを確認する。AC の内容は変えない（DN-155-3）。
  - 依存：T155-01-01、T155-01-02、T155-01-03、T155-01-04
  - 範囲：`docs/design/acceptance-criteria.md`（AC-84、AC-128）
- [ ] **T155-01-11** 段階のレビューと完了の確認。段階のレビューは profile「厚め」で、一般の観点は汎用 2 回、重要な観点は高性能と汎用で実施する。重要な観点は、ファイルをまたぐ影響である。判定は spec 担当のセッションが行う。確認の内容は次のとおり。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。pitest は、表示順の再採番が domain に入ったため実行し、基準（90.2、許容 0）と比べる。下回るときは、表示順の再採番の単体テストを追加する。
  - 依存：T155-01-01〜10
