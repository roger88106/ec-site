# 段階 2：構成の規則の検査

- 前提：段階 1
- 完了条件：DC-186-1〜7 を満たす。追加した検査は今のコードで違反が 0 件（DN-186-3）。バックエンドの書き方の規則の一覧が、検査の範囲と一致する。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-186-1、2 と DC-186-1〜7、DN-186-3、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.1 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.4 節、[バックエンドの書き方](../../../rules/backend-coding.md) 第 1.5 節

## タスク

各タスクは、違反する例のクラスで違反を検出できることを確認する検査を先に書き、失敗を確認してから規則を追加する（DC-186-7）。規則と例の確認が同じテストのクラスに入るので、各タスクは `[テスト]` と実装を 1 つにまとめ、1 commit にする（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。検査が今のコードで違反を見つけたら、[plan](../plan.md) の共通の決定のとおり扱う。

並行と順序：

- T186-02-01、02、04 は、それぞれ別のファイルだけを変えるので `[P]` である。
- T186-02-03、05、06 は、どれも `ModuleStructureTest` を変えるので、この順に行う。

- [ ] **T186-02-01** [P] [テスト] domain のパッケージの循環の検査 `PackageCycleTest` を追加する。集約のパッケージと `vo/` が互いに依存する例で失敗することを確認する（DC-186-1、7）。
  - 依存：なし
  - 範囲：`test/architecture/PackageCycleTest.java`（新規）
- [ ] **T186-02-02** [P] [テスト] 金額の型の検査の対象を、`application.model` 以下の `View`・`Command` で終わるクラスと、`api`・`event` のクラスにし、対象が 1 件以上あることを `requireNotEmpty` で確認する。`application.model` の直下の view に整数の価格がある例で失敗することを確認する（DC-186-5、6、7）。
  - 依存：なし
  - 範囲：`test/architecture/MoneyTypeTest.java`
- [ ] **T186-02-03** [テスト] 層の依存の表を 1 つの定数にし、表にない組を 1 つの規則で確認する。[plan](../plan.md) 第 2 節の置き換えの表のとおり、既存の層の向きの検査を削除・維持する。共通コードが設定に依存しない規則を追加する。表にない組の例（api → presentation、infrastructure → application の service）で失敗することを確認する（DC-186-3、7）。
  - 依存：なし
  - 範囲：`test/architecture/ModuleStructureTest.java`
- [ ] **T186-02-04** [P] [テスト] `api/` に interface がないことと、`api/` の型を `api/` の外が実装・継承しないことの規則を、新しい `ApiTypeTest` に追加する。2 つの例で失敗することを確認する（DC-186-4、7）。
  - 依存：なし
  - 範囲：`test/architecture/ApiTypeTest.java`（新規）
- [ ] **T186-02-05** [テスト] パッケージの大きさの目安を超えるパッケージを、バックエンドの書き方 第 1.4 節の表と照合する。文書のパスを system property `rules.backend-coding` で渡し、test の task と pitest の `inputs` に宣言する。判定の関数を、表の外の超過、表のパッケージの目安以下、数の違いの 3 つの例で確認する（DC-186-2、7）。
  - 依存：T186-02-03
  - 範囲：`test/architecture/ModuleStructureTest.java`、`backend/ecsite/build.gradle.kts`（`unitTestSystemProperties`、test の task と pitest の `inputs`）
- [ ] **T186-02-06** 一覧を集めて確認する構成の検査（`NamingTest` の 4 つ、`StaticOnlyClassTest`、`ModuleStructureTest` の一覧の検査、`ConfigurationPropertiesTest`、`ModuleRoleTest` のレベル A で presentation を持つモジュール）が、`ProductionClasses.requireNotEmpty` で対象が 1 件以上あることを確認する（DC-186-6）。0 件の例の確認は、T186-01-01 の補助のテストにある。
  - 依存：T186-02-05
  - 範囲：`test/architecture/NamingTest.java`、`StaticOnlyClassTest.java`、`ModuleStructureTest.java`、`ConfigurationPropertiesTest.java`、`ModuleRoleTest.java`
- [ ] **T186-02-07** [バックエンドの書き方](../../../rules/backend-coding.md) の規則の一覧の、バックエンドの書き方 第 1.1 節の行と、バックエンドの書き方 第 1.4 節の行と、バックエンドの書き方 第 1.4 節を、追加した検査に合わせて修正する（[plan](../plan.md) 第 5 節）。表の書式は変えない。
  - 依存：T186-02-01〜06
  - 範囲：`docs/rules/backend-coding.md`
- [ ] **T186-02-08** 段階のレビューと全体の検査：[plan](../plan.md) 第 7 節の段階 2 の観点とレビュー役で行う。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T186-02-01〜07
