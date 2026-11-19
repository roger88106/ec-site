# Plan 489：商品の画像の受け付けで、失敗したときに、参照されないファイルが残らず、元のエラーが失われないようにする

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：規模 L と、仮の記録の表を加える案（案 A）は、issue 担当のセッションが利用者に代わって 2026-11-19 に承認した。仮の記録の表は issue の案 1 にある変更なので、利用者の確認は待たない（issue 担当の判断）。spec・plan・tasks は、issue 担当のセッションが利用者に代わって 2026-11-19 に承認した。読む文書は[文書の書き方](../../05-開発規則/writing.md)、[文書の構成](../../05-開発規則/doc-structure.md)、[文書の記号と番号](../../05-開発規則/notation.md)。

| 本書の節 | 主な担当 |
|----------|----------|
| 解放 | DS-489-3（DC-489-7） |
| 破棄の例外 | DS-489-2（DC-489-6） |
| 仮の記録 | DS-489-1（DC-489-1〜5） |
| 文書 | DS-489-4（DC-489-8） |
| 段階と変えるファイル | 全て（DS-489-4：DC-489-9 を含む） |
| 利用者に確認すること | なし |

共通の決定：

- 危険度は高にしない。
- 1 タスクを 1 commit にする。
- 実装は汎用のサブエージェント（実装役）に任せる。仮の記録とトランザクション・イベントを扱うタスク（段階 2 の実装）に `[リスク]` を付け、高性能のサブエージェントに任せる（[エージェントの作業フロー](../../05-開発規則/subagents.md)の「任せる作業とモデル」）。
- レビューの profile は厚めにする（issue 担当の指定）。
- 重い検査（バックエンドの全体のテスト、CI の全検査、E2E）は、並行する全てのセッションを通して同時に 1 つだけ実行する。実行の前に issue 担当に知らせ、合図を待つ。Gradle の daemon を停止する操作は実行しない。途中の確認は、変えたテストのクラスを指定して実行する。
- 並行する spec との重なりは、spec の「依存」のとおり。実装を始める前の照合で、重なりが増えていないかを確認する。

## 解放（DS-489-3：DC-489-7）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| アセットの記録をまとめて削除する操作を、実際に削除した行のアセットを返す操作に変える（`DELETE … RETURNING`） | `media/application/port/AssetRepository`、`media/infrastructure/persistence/JooqAssetRepository` | リポジトリの統合テスト：あるものとないものを渡すと、あったものだけを返す。2 回目は空を返す（レッドを確認する。戻り値がないので、コンパイルで失敗する） |
| 解放は、先に読まず、削除する操作が返したアセットの分だけイベントを発行する | `media/application/MediaService` の解放 | サービスの単体テスト（新規）：リポジトリが、渡された ID の一部だけを返すとき、返した分だけイベントを発行する |
| 配信の URL の取得は、今のまとめて読む操作を使い続ける | （変えない） | 既存のテスト |

- 理由：同時に解放したとき、PostgreSQL の行ロックで後の削除は削除した行を返さないため、イベントは 1 回だけ発行される。
- PostgreSQL の行ロックの動きは、PostgreSQL の文書による。同時に解放するテストは書かない（spec の Non-goals）。

## 破棄の例外（DS-489-2：DC-489-6）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 画像の追加で保存が失敗したら、破棄を try で囲み、破棄の例外を、元の例外に抑制された例外として付けて、元の例外を投げる | `catalog/application/product/ProductImageGatewayService` の画像の追加 | 単体テスト（新規。src/test の `ProductImageGatewayServiceTest`）：保存が競合の例外を投げ、破棄も例外を投げるとき、競合の例外が投げられ、破棄の例外が抑制された例外にある（レッドを確認する） |

- 破棄の例外は、抑制された例外として付けたうえで、警告のログに、用意した画像のアセット ID と破棄の例外を出力する。理由：元の例外が業務の例外のときは、エラーの入口がエラーのコードだけを出力し、元の例外（抑制された例外を含む）を出力しないため（段階 1 のレビューで判明した）。

## 仮の記録（DS-489-1：DC-489-1〜5）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 仮の記録の表を加える。列：アセット ID（主キー）、配信のバケットのキー（一意）、用意した時刻。索引：用意した時刻とアセット ID（片付けの検索と並び順のため） | 新しいマイグレーション `V<日時>__media_create_pending_asset.sql`（日時は実装のときに、全体のブランチの最新より後にする） | リポジトリの統合テスト。レビュー：マイグレーションが表と索引を加えるだけである（DN-489-4） |
| 仮の記録のリポジトリ（port と jOOQ の実装）：記録する、1 件を削除して削除できたかを返す、保存期間を過ぎたものを件数の上限まで削除して削除した行を返す | `media/application/port/PendingAssetRepository`、`media/infrastructure/persistence/JooqPendingAssetRepository`（新規） | リポジトリの統合テスト（新規） |
| 用意：変換の後、配信のバケットに書く前に、仮の記録を短いトランザクションで記録する。用意の操作は、トランザクションの外で呼ぶ ※1 | `MediaService` の用意 | `MediaApiTest`：用意の後に仮の記録がある。仮の記録を記録できないとき、配信のバケットに書かない |
| 登録：呼んだ側のトランザクションの中で仮の記録を削除し、アセットを記録する。仮の記録を削除できなかった（ない）ときは例外を投げ、呼んだ側の保存も取り消される ※2 | `MediaService` の登録。例外は `IllegalStateException` | `MediaApiTest`：登録で仮の記録が削除される。呼んだ側を巻き戻すと仮の記録が残る。仮の記録がないと例外になり、呼んだ側のトランザクションも巻き戻る（アセットの記録が残らない） |
| 破棄：配信のファイルを削除し、成功したら仮の記録を短いトランザクションで削除する。どちらの失敗も例外を投げない ※3 | `MediaService` の破棄 | `MediaApiTest`：破棄でファイルと仮の記録が削除される。ファイルの削除が失敗すると仮の記録が残る |
| 片付けのジョブ：1 回の実行で、保存期間を過ぎた仮の記録を件数の上限まで削除し、削除した行ごとにアセット解放のイベントを同じトランザクションで登録する。残りは次の回に回す ※4 | `media/application/job/MediaCleanupJob`（新規）、`MediaService` に片付けの操作。件数のログは共通の `ExpiredDataCleanup` | ジョブの統合テスト（新規。`CartCleanupJobTest` の形）：保存期間を過ぎた仮の記録とファイルが削除され、保存期間の中のものは残る。1 回の実行で件数の上限までしか削除しない |
| ジョブのロック：名前 `media-cleanup`、持つ時間の上限 10 分 | `MediaCleanupJob` | レビュー |
| 片付けの設定 `ecsite.media.cleanup`：間隔 1h、保存期間 1d（用意した時刻から）、1 回に削除する件数の上限 500（1 以上 500 以下）。保存期間は 1 分以上 ※5 | `media/application/job/MediaCleanupProperties`（新規）、`application.yml` | 既存の片付けの設定のテスト（`config/CleanupJobSettingsTest`）に、既定と上書きのテストを加える |
| 定期ジョブのスレッドの数を 6 から 7 にし、コメントに書いたジョブの数も 7 にする | `application.yml` の `spring.task.scheduling.pool.size` | レビュー |
| アセット解放のイベントを、仮の記録からも作れるようにする（アセット ID と配信のキー） | `media/application/AssetReleased` | 上のジョブのテスト |
| 用意・登録・破棄のコメントを、仮の記録の形に書き換える | `media/api/MediaApi`、`MediaService`、`catalog/application/port/MediaDirectory` | レビュー |

※1：変換と保存の間に DB の接続を持たないため（AC-304）。用意の操作をトランザクションの外で呼ぶ点は、今のまま。

※2：例外にする理由は、片付けのジョブがファイルを削除した後の画像を、アセットとして記録しないため。保存期間（既定 1 日）は、用意から登録までの時間（1 つのリクエストの中）より十分に長いので、通常は起きない。

※3：失敗は、警告のログに配信のバケットのキーを出力する（AC-305 のまま）。ファイルを削除できなかったら、仮の記録を残し、片付けのジョブが後で削除する。

※4：既存の片付けのジョブと違い、上限に達しても同じ回の中で繰り返さない。理由：削除した行の数だけ、外部サービスを呼ぶリスナーのキュー（上限 1000）にイベントが入り、キューの上限を超えると、同じキューを使う他のリスナーのイベントも拒否されるため（イベントの再発行の件数の上限と同じ考え方。[バックエンドのアーキテクチャ](../../02-設計/backend-architecture.md)の「イベントの再発行」）。件数の上限の最大の 500 も、再発行の件数の上限と同じ値である。段階 2 の [リスク] のタスクの後のレビューで判明した。配信のファイルは、コミットの後に今のアセット解放のリスナーが削除する。ファイルの削除が失敗したときは、今のイベントの再発行の仕組みで削除を再実行する。

※5：0 にすると、用意から登録までの間の仮の記録を削除しうるため。他の片付けの保存期間（0 以上）と違う。

- 理由（別の表にする）：spec の Non-goals のとおり、アセットの操作（配信の URL、解放）を変えずに済むため。
- 理由（イベントを再利用する）：ファイルの削除と、失敗したときの再発行の仕組みを、そのまま使えるため。
- 理由（ロックの上限 10 分）：DB の削除とイベントの登録だけで外部を呼ばないので、既存の片付けのジョブと同じ値にする。
- 同じ受付 ID で用意を繰り返しても、仮の記録がそれぞれ残り、保存期間の後に片付けのジョブが削除する。

## 文書（DS-489-4：DC-489-8）

| 何を | どこに |
|------|--------|
| 「アップロードの流れ」の用意・登録・破棄の規則と、最後の箇条（登録のないファイルを探さない）を、仮の記録と片付けの形に書き換える。データモデルに仮の記録の表を加える | `docs/03-機能仕様/media.md` |
| 「画像のアップロード」の図に仮の記録を加え、片付けのジョブの流れ（図か表）を加える。パッケージの構成に `application/job/` を加える | `docs/02-設計/media.md` |
| 環境変数 `ECSITE_MEDIA_CLEANUP_INTERVAL`、`_RETENTION`、`_BATCH_SIZE` を加える。保存期間の許す範囲（1 分以上）の例外を書く | `docs/06-手順書/environment.md` |
| 「仮のアセット」（`PendingAsset` / `pending_asset`）を加える | `docs/03-機能仕様/glossary.md` |

確認方法：文書を読んで決定と照合する。`node scripts/ci/docs/doc-refs.mjs`、`node scripts/ci/ja/ja-text.mjs --warning`。

## 段階と変えるファイル

| 段階 | ファイル | 内容 | DC | 開始の条件 |
|------|----------|------|----|------------|
| 1 | `tasks/01-release-and-discard.md` | 解放と破棄の例外 | DC-489-6、7 | plan と tasks の承認、issue 担当の開始の合図、実装を始める前の照合 |
| 2 | `tasks/02-pending-asset.md` | 仮の記録、片付けのジョブ、文書、最後のレビュー | DC-489-1〜5、8、9 | 段階 1 の完了 |

| まとまり | ファイル |
|----------|----------|
| メディアのコード | `backend/ecsite/src/main/java/com/ecsite/module/media/` の `api/MediaApi.java`、`application/MediaService.java`、`application/AssetReleased.java`、`application/port/AssetRepository.java`、`application/port/PendingAssetRepository.java`（新規）、`domain/PendingAsset.java`（新規）、`application/job/MediaCleanupJob.java`（新規）、`application/job/MediaCleanupProperties.java`（新規）、`infrastructure/persistence/JooqAssetRepository.java`、`infrastructure/persistence/JooqPendingAssetRepository.java`（新規） |
| 商品カタログのコード | `backend/ecsite/src/main/java/com/ecsite/module/catalog/` の `application/product/ProductImageGatewayService.java`、`application/port/MediaDirectory.java`（コメント） |
| 設定とマイグレーション | `backend/ecsite/src/main/resources/application.yml`、`backend/ecsite/src/main/resources/db/migration/V<日時>__media_create_pending_asset.sql`（新規） |
| 統合テスト | `backend/ecsite/src/integrationTest/java/com/ecsite/module/media/` の `api/MediaApiTest.java`、`infrastructure/persistence/JooqAssetRepositoryTest.java`、`infrastructure/persistence/JooqPendingAssetRepositoryTest.java`（新規）、`application/job/MediaCleanupJobTest.java`（新規） |
| 単体テスト | `backend/ecsite/src/test/java/com/ecsite/module/media/application/MediaServiceReleaseTest.java`（新規）、`backend/ecsite/src/test/java/com/ecsite/config/CleanupJobSettingsTest.java`（既存。片付けの設定の既定と上書きのテスト）、`backend/ecsite/src/test/java/com/ecsite/module/catalog/application/product/ProductImageGatewayServiceTest.java`（新規） |
| 文書 | `docs/03-機能仕様/media.md`、`docs/02-設計/media.md`、`docs/06-手順書/environment.md`、`docs/03-機能仕様/glossary.md` |

## 利用者に確認すること

なし。
