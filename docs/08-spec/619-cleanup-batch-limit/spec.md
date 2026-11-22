# Spec 619：期限切れの片付けの 1 回の削除を、件数の上限以下にする

種類：整理　規模：M　ステータス：確定

規模の理由：判定の表では L だが、特例で M にする（plan は不要）。4 つのモジュールにまたがるが、変更は各 1 メソッドの同じ書き換えである。モジュールをまたぐ設計の決定と、API と AC の対応がないので、plan に書く中身がない。

## 背景と目的

期限切れの片付けの削除（カート、注文手続き、引当）は、選ぶ行を `IN` の副問い合わせ（`LIMIT` と `FOR UPDATE SKIP LOCKED`）に書いている。副問い合わせが入れ子のループの内側に置かれる実行計画では、外側の行ごとに再評価され、期限切れの全てを 1 文で削除する（#619 のコメント）。選ぶ行を `MATERIALIZED` の CTE で 1 回だけ確定させる形（#489 の仮のアセットと同じ）に揃え、1 回の削除の件数が上限を超えないようにする。消費者・運営者に見える振る舞いは変えない。

## スコープ

| 含める | 含めない |
|--------|----------|
| カート、注文手続き、引当の、期限切れを削除する SQL の形 | 片付けのジョブの繰り返し方と、件数の上限の値 |
| 4 つのリポジトリ（仮のアセットを含む）の、計画を強制して上限を確認するテスト | 他の `FOR UPDATE` の問い合わせ |

決定：

- 選ぶ行は `MATERIALIZED` の CTE で確定させ、削除はその CTE の ID で行う（仮のアセットのリポジトリと同じ形）。
- テストは、プランナーの設定をテストのトランザクションの中だけで変え（`SET LOCAL`）、副問い合わせが行ごとに再評価される計画を強制する。自然な計画では再現しない（調べた 23 通りで超えなかった）ため。
- 設定の変更がトランザクションの外に漏れないことを、テストで確認する。

## 目的と完了条件

### DS-619-1 期限切れの片付けの 1 回の削除が、件数の上限を超えない（D / P1）

- **DC-619-1** カート、注文手続き、引当の期限切れの削除が、選ぶ行を `MATERIALIZED` の CTE で確定させる形になっている。
- **DC-619-2** 副問い合わせを行ごとに再評価する計画を強制しても、4 つのリポジトリ（カート、注文手続き、引当、仮のアセット）の削除の件数が上限以下である。テストが、修正の前の形で失敗する。
- **DC-619-3** 計画を強制する設定が、トランザクションの終わりに元に戻る。
- **DC-619-4** `./gradlew check` が通る。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| コード | `backend/ecsite/src/main/java/com/ecsite/module/cart/infrastructure/persistence/JooqCartRepository.java`、`backend/ecsite/src/main/java/com/ecsite/module/order/infrastructure/persistence/JooqCheckoutRepository.java`、`backend/ecsite/src/main/java/com/ecsite/module/inventory/infrastructure/persistence/JooqStockReservationRepository.java` |
| テストの補助 | `backend/ecsite/src/integrationTest/java/com/ecsite/support/persistence/QueryPlans.java` と、そのテスト |
| テスト | `JooqCheckoutRepositoryTest`、`JooqStockReservationRepositoryTest`、`JooqPendingAssetRepositoryTest`、`CartCleanupJobTest`（カートのリポジトリのテストには削除のテストがないため） |

## 非機能要件

- **DN-619-1** 消費者・運営者に見える振る舞いを変えない。API の契約と DB の構造を変えない。

## 依存

| 対象 | 関係 |
|------|------|
| #608 | 並行する。`.claude/agents/` と skill `docs` を変え、ファイルは重ならない |

## Non-goals

| 項目 | 理由 |
|------|------|
| 他の `FOR UPDATE` の問い合わせの確認 | `LIMIT` と `SKIP LOCKED` を `IN` の副問い合わせに置く形は、この 3 つだけのため |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T619-01-01** [テスト] 計画を強制する補助を `QueryPlans` に追加し、設定がトランザクションの外に漏れないことのテストを書く（DC-619-3）。
  - 依存：なし
  - 範囲：`QueryPlans.java` と、そのテスト
  - 確認方法：テストが通る
- [x] **T619-01-02** [テスト] 4 つのリポジトリの削除に、計画を強制して上限を確認するテストを追加し、カート・注文手続き・引当で失敗することを確認する（DC-619-2）。
  - 依存：T619-01-01
  - 範囲：`JooqCheckoutRepositoryTest`、`JooqStockReservationRepositoryTest`、`JooqPendingAssetRepositoryTest`、`CartCleanupJobTest`
  - 確認方法：仮のアセット以外の 3 つが失敗する
- [ ] **T619-01-03** T619-01-02 のテストが通る実装を書く（DC-619-1、DC-619-2）。
  - 依存：T619-01-02
  - 範囲：3 つのリポジトリ
  - 確認方法：テストが通る。`./gradlew check`（DC-619-4）
- [ ] **T619-01-04** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T619-01-03
- [ ] **T619-01-05** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T619-01-04
- [ ] **T619-01-06** spec をアーカイブに移す。
  - 依存：T619-01-05
