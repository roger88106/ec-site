# 段階 3：AC の確認とテスト名の番号

- 前提：段階 2
- 完了条件：DC-181-1〜17 を満たす。強めたテストと追加したテストについて、一時的に期待値かコードを変えて失敗することを確認した。AC 一覧のテストの列を再生成し、テストのない有効・変更の AC がない。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 3 節、[spec](../spec.md) の DS-181-1、2 と DC-181-1〜17、DN-181-1〜7、[テストの書き方](../../../rules/test-writing.md) 第 2 節、[AC 一覧](../../../design/acceptance-criteria.md)

## タスク

各タスクは、強めたテストと追加したテストについて、一時的に期待値かコードを変えて失敗することを確認し、結果を commit の本文に書く（DN-181-4）。テストが今のコードで通らなければ、不具合として [plan](../plan.md) の共通の決定のとおり扱う。新しいテストは、コミットするなら自分のデータだけを確認し、後処理を書かない。

T181-03-01〜13 は、変えるファイルが重ならないので、互いに `[P]` である。T181-03-14 は、テスト名の番号を変える他のタスクの後に、AC 一覧を再生成する。

- [ ] **T181-03-01** [P] AC-123：会員のカートを 30 品目にしてから移し、移せなかった品目のバリアント ID と数量を確認するテストを追加する（DC-181-1）。
  - 依存：なし
  - 範囲：`it/module/cart/presentation/MemberCartApiTest.java`（`:194-214`）
- [ ] **T181-03-02** [P] AC-85：注文手続きの表示で、10% と 8% の商品を入れ、税率ごとの消費税額を確認する（DC-181-2）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/CheckoutApiTest.java`（`:160-178`）
- [ ] **T181-03-03** [P] AC-158：支払待ちの注文へのキャンセルの申請が業務のエラーの 400 で拒否され、状態が支払待ちのままであることを確認するテストを追加する。運営者のキャンセルは実際の状態コードを確認する（DC-181-3）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/OrderPaymentApiTest.java`（`:305-315`）
- [ ] **T181-03-04** [P] AC-173：出荷の登録の入力の検証を、入力ごとのパラメーター化したテストにし、エラーの種類と項目を確認する（DC-181-4）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/ShipmentApiTest.java`（`:238-252`）
- [ ] **T181-03-05** [P] AC-70：商品の画像の追加に変換できないファイルを渡すと 400 になり、商品の画像とバージョンが変わらないテストを追加する。媒体の API のテストの、常に成り立つ確認を削除し、理由を commit の本文に書く（DC-181-5）。
  - 依存：なし
  - 範囲：`it/module/catalog/presentation/ProductImageApiTest.java`、`it/module/media/api/MediaApiTest.java`（`:197-206`）
- [ ] **T181-03-06** [P] AC-77、81：カートの表示で、バリアントの画像と代表画像のある品目の画像の URL を確認する。最後の変更から 30 日の 1 ミリ秒前では品目が残ることを確認する（DC-181-6、7）。
  - 依存：なし
  - 範囲：`it/module/storefront/presentation/StorefrontCartTest.java`（`:99,156-172`）
- [ ] **T181-03-07** [P] 注文の確定のテスト：AC-144 で、カートの品目が 1 つで数量が 2 のままであることを確認する。AC-90 の名前を注文番号の採番の確認にし、注文番号の形を確認する。AC-213 の生年月日と性別のテストを分け、性別のテストに BR-29 を付ける（DC-181-8、17）。
  - 依存：なし
  - 範囲：`it/module/order/presentation/PlaceOrderApiTest.java`（`:107-135,411-417`）
- [ ] **T181-03-08** [P] レポートの API のテスト：AC-207 の商品を指定した集計、AC-208 の年齢層「不明」、AC-198 の粒度ごとの行を確認するテストを追加する。条件の指定のテスト（`:106-120`）の番号から AC-198 を外し、AC-199、200、BR-26 にする（DC-181-9〜11、17）。
  - 依存：なし
  - 範囲：`it/module/reporting/presentation/ReportApiTest.java`（`:106-120,188-211`）
- [ ] **T181-03-09** [P] AC-91：同じ注文手続きの 2 つ目の注文の挿入が、一意の制約の違反の例外になることを確認する。例外の型は、今の永続化が投げる型を確認して指定する（DC-181-12）。
  - 依存：なし
  - 範囲：`it/module/order/infrastructure/persistence/JooqOrderRepositoryTest.java`（`:87`）
- [ ] **T181-03-10** [P] AC-288：他のテストの影響を受けない古い時刻のデータだけを削除する条件で、ログの件数が削除した件数と一致することを確認する（DC-181-13）。
  - 依存：なし
  - 範囲：`it/module/cart/application/CartCleanupJobTest.java`（`:98`）、`it/module/order/application/checkout/CheckoutCleanupJobTest.java`（`:125`）
- [ ] **T181-03-11** [P] NFR-16：注文のレポート用の API の写しの型の項目名を、入れ子の record まで再帰して確認する（DC-181-14）。
  - 依存：なし
  - 範囲：`it/module/order/api/OrderReportApiTest.java`（`:166-175`）
- [ ] **T181-03-12** [P] 業務のポートで管理用のエンドポイントを呼んだ応答は 404 を、物流の通知のエンドポイントは契約の応答（400）を確認する（DC-181-15）。
  - 依存：なし
  - 範囲：`it/observability/ManagementPortTest.java`（`:50-51`）、`it/config/security/BusinessPortAccessTest.java`（`:47`）
- [ ] **T181-03-13** [P] アプリケーションの起動のテストで、全てのモジュールの schema（13）を一覧にし、全てが作られることを確認する（DC-181-16）。
  - 依存：なし
  - 範囲：`it/EcSiteApplicationTests.java`（`:48`）
- [ ] **T181-03-14** [plan](../plan.md) 第 3.2 節の表の残りのテスト名の番号を修正する（`InventoryApiTest`、`PaymentAuthorizationDeadlineTest`、`TaxRateApiTest`、`ProductActionsTest`、`ReportGenderTest`、`ShippingApiTest`、`CartApiTest`、`PaymentExpiryJobTest`、`VariantSalesQueryTest`、`AdminRealmPermissionContractTest`）。番号を外す前に、外した番号を確認する他のテストがあることを AC 一覧で確認する（plan 第 3.2 節 ※1）。`node scripts/ci/ac-check.mjs --write` で AC 一覧を再生成する（DC-181-17）。
  - 依存：T181-03-01〜13
  - 範囲：上のテストのファイル、`docs/design/acceptance-criteria.md`
- [ ] **T181-03-15** 段階のレビューと全体の検査：profile「厚め」で、一般の観点（テスト、規則）を汎用で 2 回実施する。各テスト名の AC 番号が spec の AC の文と合うことを確認させる。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T181-03-14
