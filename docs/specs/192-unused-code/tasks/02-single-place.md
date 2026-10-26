# 段階 2：同じ処理を 1 か所に

- 前提：段階 1
- 完了条件：DC-192-5〜8 を満たす。[plan](../plan.md) 第 2 節の確認方法の検索の結果が、表のとおりである。`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-192-2 と DC-192-5〜8、DN-192-1〜5、[バックエンドのアーキテクチャ](../../../design/backend-architecture.md) 第 7.2 節

## タスク

実装は実装役のサブエージェントに任せる。T192-02-01〜03 は、変えるファイルが重ならないので `[P]` である。

- [x] **T192-02-01** `[P]` 配送業者の表示名のキーを出荷モジュールのキーにし、通知のメールと mock の画面がそれを使う（DC-192-5）。出荷の設計書に、表示名のキーを出荷モジュールが持つことを書く。
  - 依存：なし
  - 範囲：`main/resources/messages.properties`、`main/module/notification/application/ShipmentMailComposer.java`、`main/module/shipping/infrastructure/mock/MockCarrierController.java`、通知と mock のテスト、`docs/design/modules/shipping.md`
- [x] **T192-02-02** `[P]` 変換元のファイルの場所を、ファイル保存の実装が作る。画像変換のクラスは、ファイル保存設定の型を持たない（DC-192-6）。
  - 依存：なし
  - 範囲：`main/module/media/infrastructure/converter/ImgproxyImageConverter.java`、`main/module/media/infrastructure/storage/S3ObjectStorage.java`、そのテスト
- [x] **T192-02-03** `[P]` 片付けのジョブが行う削除の SQL を、イベント記録の表を読み書きするクラスに移す（DC-192-7）。共通コードの設計を直す。
  - 依存：なし
  - 範囲：`main/config/event/EventCleanupJob.java`、`main/config/event/EventPublicationRecords.java`、`it/config/event/EventCleanupJobTest.java`、`docs/design/common.md`
- [x] **T192-02-04** `[リスク]` 認証情報から token を取り出し、なければ未認証のエラーにする処理を、`common/security/` の 1 つの操作にする。会員、運営者、ログイン中の利用者の情報を返す API は、それを呼ぶ。token がないときに会員の取り出しが空を返す振る舞いを残す（DC-192-8）。共通コードの設計を直す。実装役は高性能にし、実装の後に高性能のレビューを行う。
  - 依存：T192-02-03（共通コードの設計を共に変えるため）
  - 範囲：`main/common/security/`、`main/config/security/MeController.java`、認証のテスト、`docs/design/common.md`
- [ ] **T192-02-05** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` を実行する。[plan](../plan.md) 第 2 節の検索で、DC-192-5〜8 を満たすことを確認する。指摘を修正する。
  - 依存：T192-02-01〜04
