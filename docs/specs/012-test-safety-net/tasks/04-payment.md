# 段階 4：支払いの実装のテスト

- 前提：段階 3
- 完了条件：Stripe の実装の画面の作成・画面を閉じる・与信の取り消しと通知の受け口にテストがある。支払いの port の共通の振る舞いを mock と Stripe の両方で確かめる。mock の支払いの画面にテストがある。
- 参照：[plan](../plan.md) 第 4 節

## タスク

- [ ] **T012-04-01** [テスト] `StripePaymentGatewayTest` に画面の作成・画面を閉じる・与信の取り消しを足す（plan 第 4.1 節）。
- [ ] **T012-04-02** [テスト] `StripeWebhookController` の slice のテスト（plan 第 4.1 節）。
- [ ] **T012-04-03** [テスト] 支払いの port の共通のテストのクラスと、mock・Stripe の子のクラス（plan 第 4.2 節）。
- [ ] **T012-04-04** [テスト] `MockPaymentPageController` のテスト（plan 第 4.3 節）。
- [ ] **T012-04-05** 段階の完了の確認：`check-all.sh`、`./gradlew check` が通る。payment のパッケージのカバレッジを前と後で記録する。
