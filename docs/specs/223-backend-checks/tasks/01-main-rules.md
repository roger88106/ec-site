# 段階 1：契約の読み込み、構成の検査、Checkstyle、モジュールの境界

- 前提：plan が承認された。spec 222 がマージされた。実装を始める前の照合（[開発の進め方](../../../rules/workflow.md) 第 5.6 節）を済ませた。
- 完了条件：DC-223-1、3、4、11、12、18〜21、23 を満たす。追加した検査は、違反の例で失敗することを確認した（DN-223-6）。`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 11.3 節、[plan](../plan.md) 第 11.4 節、[spec](../spec.md) の DS-223-1、3、5、6、DN-223-1〜8

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方と「補助」の語は、[plan](../plan.md) の共通の決定のとおりである。

- T223-01-01 は `ProductionClasses` の定数を先に作る。後の構成の検査のタスク（T223-01-02、05、07）がそれを使う。
- T223-01-02、05、07、08 は、構成の検査かバックエンドの書き方を共に変えるので、番号の順に行う。
- T223-01-03 と 04 は `[テスト]` と実装の組で、同じ実装役が続けて行う。この組と、T223-01-06、09 は、他のタスクと変えるファイルが重ならないので `[P]` である。

- [x] **T223-01-01** 生成したコードとモジュールのパッケージの接頭辞を `ProductionClasses` の定数にまとめ、他のテストの同じ定数と文字列をそれに置き換える。エラーの種類の列挙を探す処理を `ProductionClasses` の操作にし、文言の契約のテストとエラーコードの契約のテストがそれを使う（DC-223-11、12）。
  - 依存：なし
  - 範囲：`test/ProductionClasses.java`、`test/ProductionClassesTest.java`、`test/architecture/SchemaOwnershipTest.java`、`ModuleStructureTest.java`、`ConfigurationPropertiesTest.java`、`ModuleRoleTest.java`、`test/common/message/MessageContractTest.java`、`test/contract/ErrorCodeContractTest.java`
  - 確認方法：[plan](../plan.md) 第 3 節の `git grep` の結果を、このタスクの結果に書く。列挙を探す操作を一時的に空の一覧にして、2 つの契約のテストが失敗することを確認する（DN-223-7）。
- [x] **T223-01-02** 秘密の値の項目を持つ設定の型が `toString` を宣言することを、構成の検査に加える（DC-223-1、[plan](../plan.md) 第 1.1 節）。違反の例のクラスを置く。バックエンドの書き方の規則の一覧に加える。
  - 依存：T223-01-01
  - 範囲：`test/architecture/ConfigurationPropertiesTest.java`、`test/architecture/` の下の違反の例、`docs/rules/backend-coding.md`
  - 確認方法：違反の例で検査が失敗し、今の 4 つの設定の型で通る。
- [x] **T223-01-03** `[P]` `[テスト]` `[リスク]` 契約の読み込みで、パスの下の大文字のメソッドと、認める項目の外のキーを、起動の失敗にする単体テストを書き、失敗することを確認する（DC-223-3、4）。
  - 依存：なし
  - 範囲：`test/config/security/OpenApiOperationsTest.java`
- [x] **T223-01-04** `[P]` `[リスク]` 契約の読み込みが、パスの下の大文字のメソッドと、認める項目の外のキーで例外を投げるようにする。例外のメッセージに、契約のファイル、パス、キーを入れる（DC-223-3、4、[plan](../plan.md) 第 1.2 節）。
  - 依存：T223-01-03（同じ実装役が続けて行い、同じ commit にする）
  - 範囲：`main/config/security/OpenApiOperations.java`
  - 確認方法：T223-01-03 のテストと、今の 2 つの契約で起動する統合テストが通る。実装の後に高性能のレビュー（profile「厚め」）を行う。
- [x] **T223-01-05** token の取り出しと、会員・運営者の取り出しを使えるのは、presentation 層、`common.security`、[plan](../plan.md) 第 5 節の表の例外の 2 つだけであることを、構成の検査に加える（DC-223-19）。違反の例のクラスを置く。バックエンドの書き方に規則と例外の表を加える。
  - 依存：T223-01-02（同じ文書を変えるため）
  - 範囲：`test/architecture/ModuleStructureTest.java`、`test/architecture/` の下の違反の例、`docs/rules/backend-coding.md`
  - 確認方法：違反の例（application 層からの取り出し）で検査が失敗し、今のコードで通る。
- [x] **T223-01-06** `[P]` 出荷モジュールの公開 API に、配送業者の表示名のキーを返す操作を置く。キーの文字列はドメインの配送業者の 1 か所にし、出荷の mock の画面もそれを使う（[plan](../plan.md) 第 6 節）。通知モジュールは、キーを得る port と、出荷モジュールの公開 API を呼ぶ `infrastructure/directory/` の実装を置いて使う。通知モジュールの依存に出荷モジュールの公開 API を加え、package-info の説明を直す（DC-223-20、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 11.3 節）。出荷と通知のモジュールの設計書を直す。
  - 依存：なし
  - 範囲：`main/module/shipping/api/`、`main/module/shipping/infrastructure/mock/MockCarrierController.java`、`main/module/notification/application/ShipmentMailComposer.java`、`main/module/notification/application/port/`、`main/module/notification/infrastructure/directory/`、`main/module/notification/package-info.java`、`main/module/shipping/domain/`、`test/module/notification/application/ShipmentMailComposerTest.java`、`it/module/notification/` の `ShipmentMailTest.java`、`docs/design/modules/shipping.md`、`docs/design/modules/notification.md`、`docs/design/overview.md`（モジュールの図）
  - 確認方法：出荷通知メールのテストが、今と同じ文面を確認して通る。キーを返す操作の単体テストで、全ての配送業者のキーが文言のファイルにあることを確認する。Spring Modulith の検査と、他のモジュールの公開 API を `infrastructure/directory/` だけから使う構成の検査が、例外の一覧を変えずに通る。
- [x] **T223-01-07** jOOQ を使えるのが `infrastructure/persistence/` だけであることを、構成の検査に加える。mock の出荷の問い合わせを例外にする（DC-223-21、[plan](../plan.md) 第 11.4 節）。違反の例のクラスを置く。バックエンドの書き方に規則と例外の理由を加える。
  - 依存：T223-01-05（同じ文書を変えるため）
  - 範囲：`test/architecture/SchemaOwnershipTest.java`、`test/architecture/` の下の違反の例、`docs/rules/backend-coding.md`
  - 確認方法：違反の例（application 層の jOOQ）で検査が失敗し、今のコードで通る。
- [x] **T223-01-08** Checkstyle に `AvoidStarImport` を加え、バックエンドの書き方の規則の一覧に加える（DC-223-18）。
  - 依存：T223-01-07（同じ文書を変えるため）
  - 範囲：`backend/ecsite/config/checkstyle/checkstyle.xml`、`docs/rules/backend-coding.md`
  - 確認方法：一時的にワイルドカードの import と static のワイルドカードの import を入れて、Checkstyle が失敗する。
- [x] **T223-01-09** `[P]` 地域のマイグレーションの生成の処理が書く先頭のコメントを、コミット済みのマイグレーションと同じにし、比べる単体テストを加える。生成の処理の Javadoc に、適用済みのマイグレーションに合わせる理由と、正しい設計書のパスを書く（DC-223-23）。地域のモジュールの設計書に理由を書く。
  - 依存：なし
  - 範囲：`tools/tools/region/RegionMigrationGenerator.java`、`test/tools/region/`、`docs/design/modules/region.md`
  - 確認方法：比べる単体テストは、生成の処理の先頭のコメントを一時的に変えて失敗する。メインの作業ツリーの郵便番号の CSV で `./gradlew generateRegionMigration` を実行し、出力がコミット済みのマイグレーションと一致する。
- [x] **T223-01-10** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` を実行する。DC を満たし、追加した検査が違反の例で失敗することを確認する。指摘を修正する。
  - 依存：T223-01-01〜09
