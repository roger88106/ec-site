# 段階 4：Repository の統合テストとカートの作成

- 前提：段階 3
- 完了条件：DC-181-28〜31 を満たす。`[リスク]` のタスクの後の高性能のレビューを終えた。追加したテストについて、一時的にコードを変えて失敗することを確認した。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` が通る。
- 参照：[plan](../plan.md) 第 4 節、[spec](../spec.md) の DS-181-5 と DC-181-28〜31、DN-181-1〜7、[テストの書き方](../../../rules/test-writing.md) 第 1.1 節

## タスク

T181-04-01〜03 は `[リスク]`（会員のカートの作成。並行と一意の制約を扱う）で、実装役は高性能にし、T181-04-03 の後に高性能のレビューを実施する（[plan](../plan.md) の共通の決定）。T181-04-01（レッド）と T181-04-02 は、1 つの commit にする（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。T181-04-04〜08 は、変えるファイルが重ならないので、互いに `[P]` である。

- [ ] **T181-04-01** [テスト] [リスク] カートの Repository の統合テストを作る。作成と再構築、品目の差分の更新、楽観ロック、会員のカートの 2 回目の挿入が「挿入できなかった」を返すことを確認する。最後の確認は、作成の操作が結果を返さないのでコンパイルに失敗する（レッド）（DC-181-28、31）。
  - 依存：なし
  - 範囲：`it/module/cart/infrastructure/persistence/JooqCartRepositoryTest.java`（新規）
- [ ] **T181-04-02** [リスク] カートの作成の永続化を、挿入できたかを返す形にする。会員のカートの ID を求める操作は、挿入できたら作った ID を返し、できなかったときだけ読み直す。注文の挿入と同じ書き方にする。呼び出し元（`CartService` の 2 か所、`CartCleanupJobTest`）を合わせる（DC-181-28）。
  - 依存：T181-04-01
  - 範囲：`main/module/cart/application/port/CartRepository.java`（`:19`）、`main/module/cart/infrastructure/persistence/JooqCartRepository.java`（`:43-52`）、`main/module/cart/application/CartService.java`（`:81,170-186`）、`it/module/cart/application/CartCleanupJobTest.java`（`:184`）
- [ ] **T181-04-03** [リスク] 同じ会員の最初のカートの要求を同時に送り、カートが 1 つで、全ての要求が同じ ID を受け取ることを確認する並行のテストを作る。会員の確定の並行のテストと同じ書き方にする。一時的に一意の制約の扱い（重複の無視）を外して、失敗することを確認する（DC-181-29）。
  - 依存：T181-04-02
  - 範囲：`it/module/cart/presentation/MemberCartConcurrencyTest.java`（新規）
- [ ] **T181-04-04** [P] 注文の Repository のテストに、状態を変えた更新、古いバージョンでの更新の競合、行の lock、注文番号の存在の確認を追加する（DC-181-30）。
  - 依存：なし
  - 範囲：`it/module/order/infrastructure/persistence/JooqOrderRepositoryTest.java`
- [ ] **T181-04-05** [P] 注文手続きと支払の Repository の統合テストを作る。永続化と再構築、更新を確認する（DC-181-31）。
  - 依存：なし
  - 範囲：`it/module/order/infrastructure/persistence/JooqCheckoutRepositoryTest.java`（新規）、`it/module/payment/infrastructure/persistence/JooqPaymentRepositoryTest.java`（新規）
- [ ] **T181-04-06** [P] 会員と送ったメールの Repository の統合テストを作る（DC-181-31）。
  - 依存：なし
  - 範囲：`it/module/member/infrastructure/persistence/JooqMemberRepositoryTest.java`（新規）、`it/module/notification/infrastructure/persistence/JooqSentMailRepositoryTest.java`（新規）
- [ ] **T181-04-07** [P] レポートの注文の写しの Repository の統合テストを作る。写しの保存と再構築、重複の無視を確認する（DC-181-31）。
  - 依存：なし
  - 範囲：`it/module/reporting/infrastructure/persistence/JooqReportOrderRepositoryTest.java`（新規）
- [ ] **T181-04-08** [P] 在庫の引当と画像の資産の Repository の統合テストを作る（DC-181-31）。
  - 依存：なし
  - 範囲：`it/module/inventory/infrastructure/persistence/JooqStockReservationRepositoryTest.java`（新規）、`it/module/media/infrastructure/persistence/JooqAssetRepositoryTest.java`（新規）
- [ ] **T181-04-09** 段階のレビューと全体の検査：profile「厚め」で、一般の観点（テスト、規則）を汎用で 2 回、重要な観点（`[リスク]`、ファイルをまたぐ影響）を高性能と汎用で 1 回ずつ実施する。`node scripts/ci/ac-check.mjs --write` で AC 一覧のテストの列を再生成する。`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` を実行し、カバレッジと mutation score が基準を下回らないことを確認する。
  - 依存：T181-04-01〜08

新しい各テストのクラス名と置き場所は、実装の Repository の置き場所に合わせる。実装のクラスの名前が上と違えば、実装に合わせる（[plan](../plan.md) 第 4 節）。
