# 段階 1：構成の検査の補助と読み込み

- 前提：plan が承認された。spec 181 がマージされた。
- 前提：実装を始める前の照合（[開発の進め方](../../../../../rules/workflow.md) 第 5.6 節）を、[plan](../plan.md) の共通の決定の項目のとおりに済ませた。
- 完了条件：DC-186-8〜10、12 を満たす。補助へ移したテストの確認は変わっていない（差分で確認する）。`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-186-3 と DC-186-8〜10、12、DN-186-4、5、[テストの書き方](../../../../../rules/test-writing.md) 第 3 節

## タスク

実装は実装役のサブエージェントに任せる。パスの書き方は、[plan](../plan.md) の共通の決定のとおりである。補助へ移すタスクは、テストの確認を変えない。T186-01-01 と T186-01-02、T186-01-05 と T186-01-06 は、`[テスト]` と実装の組なので、それぞれ 1 commit にする。

- [x] **T186-01-01** [テスト] 本番のクラスの補助 `ProductionClasses` のテストを書き、失敗することを確認する（DC-186-8、10）。確認すること：
  - `handwritten()` が生成したコードを含まず、`modules()` がモジュールの外を含まない。
  - `moduleName` と `isGenerated` の判定。
  - `modulesWith` が、層を持たないモジュールを返さない。
  - `requireNotEmpty` が、0 件で失敗し、1 件以上で通る。
  - レベル A の一覧が、設計書の「レベル」の行と一致する（[plan](../plan.md) 第 1.1 節）。
  - 依存：なし
  - 範囲：`test/ProductionClassesTest.java`（新規）
- [x] **T186-01-02** `ProductionClasses` を作り、遅延の static で 1 回だけ読み込む（[plan](../plan.md) 第 1.1 節の表）。設計書のディレクトリを system property `design.modules.dir` で渡し、test の task と pitest の `inputs` に宣言する。T186-01-01 のテストが通る（DC-186-8、10）。
  - 依存：T186-01-01
  - 範囲：`test/ProductionClasses.java`（新規）、`backend/ecsite/build.gradle.kts`（`unitTestSystemProperties`、test の task と pitest の `inputs`）
- [x] **T186-01-03** `architecture/` の 8 つの検査、`common/message/MessageContractTest`、`contract/ErrorCodeContractTest` の本番のクラスの読み込み、レベル A の一覧、層を持つモジュールを求める処理、モジュールの名前の取り出し、生成したコードの判定を、`ProductionClasses` の呼び出しに置き換える。`ModuleRoleTest` の static に持たない理由のコメントを削除する（DC-186-8、10）。
  - 依存：T186-01-02
  - 範囲：`test/architecture/*.java`、`test/common/message/MessageContractTest.java`、`test/contract/ErrorCodeContractTest.java`
- [x] **T186-01-04** `ModulithTest` と `ModuleDiagramTest` が、`ProductionClasses` のモジュールの一覧と `TEST_CLASSES` を使う。`ModulithTest` の標準出力への書き出しを削除する（DC-186-8、12）。
  - 依存：T186-01-02
  - 範囲：`test/modulith/ModulithTest.java`、`test/modulith/ModuleDiagramTest.java`
- [x] **T186-01-05** [テスト] モジュールの図の描画のテスト `rendersDependenciesAndEvents` を、固定した小さな入力（[plan](../plan.md) 第 1.3 節の record）を描いて確認する形に書き換え、record から描くメソッドがないので失敗する（コンパイルできない）ことを確認する（DC-186-12）。
  - 依存：T186-01-04
  - 範囲：`test/modulith/ModuleDiagramTest.java`
- [x] **T186-01-06** 図を描く処理を、`ApplicationModules` から record を作るメソッドと、record から描くメソッドに分ける。T186-01-05 のテストが通る。一時的に点線を実線に変えて、テストが失敗することを確認する（DC-186-12）。
  - 依存：T186-01-05
  - 範囲：`test/modulith/ModuleDiagramTest.java`
- [x] **T186-01-07** 単体テストの heap の上限を、[plan](../plan.md) 第 1.2 節の手順で測って決める。1024m のままにするときは、`unitTestMaxHeap` のコメントに測った値を書く。測った値を commit の本文に書く（DC-186-9）。
  - 依存：T186-01-03、T186-01-04、T186-01-06
  - 範囲：`backend/ecsite/build.gradle.kts`（`unitTestMaxHeap`）
- [x] **T186-01-08** [テストの書き方](../../../../../rules/test-writing.md)  第 3.3 節と 3.4 節に、`ProductionClasses` と、構成の検査は本番のクラスを補助で読むことを書く。用語集の識別子の検査が求めるときは、用語集に追加する（[plan](../plan.md) 第 5 節）。
  - 依存：T186-01-03
  - 範囲：`docs/rules/test-writing.md`、`docs/design/glossary.md`
- [x] **T186-01-09** 段階のレビューと全体の検査：[plan](../plan.md) 第 7 節の段階 1 の観点とレビュー役で行う。補助の外に本番のクラスの読み込みが残っていないこと（[plan](../plan.md) 第 1.1 節の確認方法）も確認させる。`./gradlew check`、`./gradlew pitest`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T186-01-01〜08

## 段階のレビューの結果

profile「厚め」で、汎用のレビュー役 2 つ（規則と重複、確認の維持）が行った（高 0、必須 3、推奨 13）。結果は `/.local/review/202610261200-186-stage1/` にある。

- 修正した：private のメンバーの Javadoc の漏れ（必須 2）、図の描画のテストの名前と常に通る確認（必須 1）、図の補助の可視性、生成したコードの判定の接頭辞、heap のコメントの spec の番号、テストの書き方の規則をテストの書き方 第 3.4 節に分け、補助の外の読み込みを文字列の検査で確認するテストを追加した。
- 段階 2 に移した：`ConfigurationPropertiesTest` と `ModuleRoleTest` の対象が 0 件のときの確認（T186-02-06 の範囲に加えた）。
- 段階 4 で扱う：`ModulithTest` の Javadoc のタスクの番号。
- 残した：`ModuleRoleTest` の生成したモデルの判定（OpenAPI だけを見る規則で、補助の判定は jOOQ も含むため）、エラーの種類の enum を探す処理とモジュールの名前の集合の補助化（plan の補助の表にない。重複は 2 か所で小さい）。
- `./gradlew check`、`./gradlew pitest`（768m）、`scripts/ci/check-all.sh` は通った。
