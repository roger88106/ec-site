# 段階 4：配送業者の mock の記録、片付けのジョブのループ

- 前提：段階 2、段階 3
- 完了条件：DC-155-25、26、29 を満たす。片付けのジョブのログの文面とロガーが変わらない。共通の片付けの単体テストは先に書き、レッドを確認した。関係する文書を更新した。`./gradlew check` と `scripts/ci/check-all.sh` が通り、カバレッジの基準を下回らない。
- 参照：[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 7 節、[spec](../spec.md) の DS-155-5、DS-155-6 と DC-155-25、26、29、DN-155-1〜5、[開発の進め方](../../../rules/workflow.md) 第 2.1 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 5.3 節

## タスク

実装は実装役のサブエージェントに任せる。`[テスト]` のタスクは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。`[テスト]` と実装の組は、同じ実装役が順に行う。パスの書き方は、[plan](../plan.md) の共通の決定の最後の項目のとおりである。

T155-04-01 と、T155-04-02 から始まる組は、変えるファイルが重ならないので、互いに `[P]` である。

- [x] **T155-04-01** [P] 配送業者の mock から、テスト専用の記録（`started`、`started()`、`Started`）を削除する。testFixtures に `TestCarrierTracking` を作る。`CarrierTracking` を実装して mock を包み、追跡を始めた送り状番号の記録を持つ。統合テストの設定 `TestCarrierTrackingConfig` を作る（`@Primary`、`TestPaymentGatewayConfig` と同じ形、`ecsite.shipping.mock.enabled` の条件を付ける）。`IntegrationTest` の `@Import` に追加する。`ShippingApiTest` は包む実装を使い、トランザクションの有無の記録も包む実装に対して行う。mock のクラスは public のまま残す（DC-155-25、26）。
  - 依存：なし
  - 範囲：`main/module/shipping/infrastructure/mock/MockCarrierTracking.java`（`:13-45`）、`fix/module/shipping/infrastructure/mock/TestCarrierTracking.java`（新規）、`it/TestCarrierTrackingConfig.java`（新規）、`it/IntegrationTest.java`（`:26-27`）、`it/module/shipping/api/ShippingApiTest.java`（`:10,52,136-137,144-147`）。`test/config/ExternalServiceSelectionTest.java`（`:10,90`）は、変更せず、テストが通ることを確認する。
- [x] **T155-04-02** [P] [テスト] 期限切れの片付けの共通のクラス `ExpiredDataCleanup` の単体テストを書き、レッドを確認する。確認する内容：件数の上限ごとに削除し、削除した件数が上限と等しい間繰り返す。合計が 1 件以上なら info、0 件なら debug でログを出す。ログの文面は渡した名詞から作り、渡したロガーに出す。AC の番号は付けない（DC-155-29）。
  - 依存：なし
  - 範囲：`test/common/scheduling/ExpiredDataCleanupTest.java`（新規）
- [x] **T155-04-03** T155-04-02 のテストが通る実装を書く。`common/scheduling/` に static の final クラス `ExpiredDataCleanup`（コンストラクターは private）を作り、ループとログを 1 つのメソッドにする。cart、inventory、order の 3 つのジョブがこのメソッドを使う。ログの文面（「期限切れのカートを削除した：{} 件」など）とロガーは変えない。`@Scheduled`、`@SchedulerLock` と設定の型はジョブに残す。3 つのジョブの統合テストがログの文面の正規表現で通ることを確認する（DC-155-29）。
  - 依存：T155-04-02
  - 範囲：`main/common/scheduling/ExpiredDataCleanup.java`（新規）、`main/module/cart/application/CartCleanupJob.java`（`:44-55`）、`main/module/inventory/application/StockReservationCleanupJob.java`（`:44-55`）、`main/module/order/application/checkout/CheckoutCleanupJob.java`（`:45-56`）。`it/module/cart/application/CartCleanupJobTest.java`、`it/module/inventory/application/StockReservationCleanupJobTest.java`、`it/module/order/application/checkout/CheckoutCleanupJobTest.java`、`test/config/CleanupJobSettingsTest.java`（`:67-79`）は、変更せず、テストが通ることを確認する。
- [x] **T155-04-04** 文書を段階 4 のコードに合わせる。共通コードの設計書の一覧に、期限切れの片付けの共通のクラスの行を追加する。出荷の設計書の mock の記述（追跡を始めた送り状番号を記録する）を、testFixtures の包む実装で確認することに修正する。バックエンドのアーキテクチャ 第 8 節に mock とテスト専用の記録の記述があれば修正する。cart、inventory、order の設計書のジョブの記述に、共通のクラスを使うことを 1 文追加する。
  - 依存：T155-04-01、T155-04-03
  - 範囲：`docs/design/common.md`（`:24-34`）、`docs/design/modules/shipping.md`（`:109-114`）、`docs/design/backend-architecture.md`（バックエンドのアーキテクチャ 第 8 節）、`docs/design/modules/cart.md`（`:33`）、`docs/design/modules/order.md`（`:27`）、`docs/design/modules/inventory.md`（`:59,102`）
- [ ] **T155-04-05** 段階のレビューと完了の確認。段階のレビューは profile「厚め」で、一般の観点は汎用 2 回、重要な観点は高性能と汎用で実施する。重要な観点は、ファイルをまたぐ影響である。判定は spec 担当のセッションが行う。確認の内容は次のとおり。`./gradlew check` と `scripts/ci/check-all.sh` が通り、カバレッジの基準（line 98.09、branch 85.26）を下回らない。
  - 依存：T155-04-01〜04
