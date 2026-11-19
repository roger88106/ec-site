# 段階 2：仮の記録

- 前提：段階 1 の完了。
- 完了条件：DC-489-1〜5、8、9 を満たし、`./gradlew check` が通る。
- 参照：[plan](../plan.md)の「仮の記録」「文書」、[spec](../spec.md)の DS-489-1、DS-489-4

## タスク

実装は、`[リスク]` のタスクを高性能のサブエージェント（実装役）に、それ以外を汎用のサブエージェントに任せる。

- [ ] **T489-02-01** [テスト] 仮の記録のリポジトリの統合テストを書く。記録、1 件の削除、保存期間を過ぎたものを用意した時刻の古い順に削除すること、を確認する。
  - 依存：なし
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/media/infrastructure/persistence/JooqPendingAssetRepositoryTest.java`（新規）
  - 確認方法：コンパイルで失敗する（レッド）
- [ ] **T489-02-02** [リスク] マイグレーションと仮の記録のリポジトリを書く（DC-489-1〜5 の前提になる部分）。
  - 依存：T489-02-01
  - 範囲：`backend/ecsite/src/main/resources/db/migration/V<日時>__media_create_pending_asset.sql`（新規）、`PendingAssetRepository`（新規）、`JooqPendingAssetRepository`（新規）
  - 確認方法：T489-02-01 のテストが通る
- [ ] **T489-02-03** [テスト] 用意・登録・破棄の仮の記録のテストを書く（DC-489-1〜3、5）。
  - 依存：T489-02-02
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/media/api/MediaApiTest.java`
  - 確認方法：失敗する（レッド）
- [ ] **T489-02-04** [リスク] T489-02-03 のテストが通るように、用意・登録・破棄を実装し、コメントを、仮の記録を使う流れに書き換える（DC-489-1〜3、5）。
  - 依存：T489-02-03
  - 範囲：`MediaApi`、`MediaService`、`MediaDirectory`（コメント）
  - 確認方法：`MediaApiTest`、`ProductImageApiTest`、`ProductImageTransactionApiTest` が通る
- [ ] **T489-02-05** [テスト] 片付けのジョブの統合テストと、既存の片付けの設定のテストに、メディアの片付けの既定と上書きのテストを書く（DC-489-4）。
  - 依存：T489-02-04
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/media/application/job/MediaCleanupJobTest.java`（新規）、`backend/ecsite/src/test/java/com/ecsite/config/CleanupJobSettingsTest.java`（既存）
  - 確認方法：コンパイルで失敗する（レッド）
- [ ] **T489-02-06** [リスク] T489-02-05 のテストが通るように、片付けのジョブ、設定、仮の記録からのイベントの生成、定期ジョブのスレッドの数の設定を実装する（DC-489-4）。
  - 依存：T489-02-05
  - 範囲：`MediaCleanupJob`（新規）、`MediaCleanupProperties`（新規）、`MediaService` の片付けの操作、`AssetReleased`、`application.yml`（スレッドの数を含む）
  - 確認方法：T489-02-05 のテストが通る
- [ ] **T489-02-07** 文書を修正する（DC-489-8）。本文は、書き手のサブエージェントが書く。
  - 依存：T489-02-04、T489-02-06
  - 範囲：[plan](../plan.md)の「文書」の 4 つ
  - 確認方法：文書を読み、決定と照合する。`node scripts/ci/docs/doc-refs.mjs`、`node scripts/ci/ja/ja-text.mjs --warning`
- [ ] **T489-02-08** `./gradlew check` を通す（DC-489-9）。重い検査なので、実行の前に issue 担当の合図を待つ。
  - 依存：T489-02-07
  - 確認方法：`./gradlew check`
- [ ] **T489-02-09** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T489-02-08
- [ ] **T489-02-10** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T489-02-09
- [ ] **T489-02-11** spec をアーカイブに移す。
  - 依存：T489-02-10
