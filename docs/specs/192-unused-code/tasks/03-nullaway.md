# 段階 3：NullAway と規則の検査、null の基準

- 前提：段階 2
- 完了条件：DC-192-16〜20 を満たす。追加した検査が、違反の例で違反を検出する（DN-192-4）。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-192-5 と DC-192-16〜20、DN-192-4、[バックエンドの書き方](../../../rules/backend-coding.md) 第 7 節

## タスク

実装は実装役のサブエージェントに任せる。規則の検査の script のタスク（T192-03-02、03）は、テストが失敗することをローカルで確認し、実装と同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。DC-192-17 は既に満たしているので、コードを変えるタスクを置かず、T192-03-05 で確認する（[plan](../plan.md) 第 3 節）。

- [ ] **T192-03-01** `[リスク]` NullAway に `AcknowledgeRestrictiveAnnotations` を加え、NullAway のエラーを修正する。設定のコメントに、何を見つけるかを書く。違反の例を一時的に入れて、コンパイルが失敗することを確認する（DC-192-16）。実装役は高性能にし、実装の後に高性能のレビューを行う。
  - 依存：なし
  - 範囲：`build.gradle.kts` の NullAway の設定、エラーの出た main のクラス
- [ ] **T192-03-02** `[テスト]` 規則の検査のテストに、次の違反の例を追加し、失敗することを確認する（DC-192-18、19）。対象のソースセットは、`@SuppressWarnings` の規則が全てのソースセット、ワイルドカード import の規則が今の `Nullable` の規則と同じ（本番・テスト・tools）である。
  - 理由のコメントのない `@SuppressWarnings`
  - Javadoc だけで、直前に `//` のコメントのない `@SuppressWarnings`
  - JSpecify 以外の `Nullable` を含むパッケージのワイルドカード import
  - 依存：なし
  - 範囲：`scripts/ci/java-rules.test.mjs`
- [ ] **T192-03-03** 規則の検査に、「`@SuppressWarnings` の直前に理由のコメントを求める規則」と「`Nullable` を含むパッケージのワイルドカード import を禁じる規則」を加える。今の 12 か所の `@SuppressWarnings("unchecked")` に理由を書く（DC-192-18、19）。バックエンドの書き方 第 7.2 節と、CI の検査の文書の `java-rules` の行に、規則の検査が確認することを書く。
  - 依存：T192-03-02
  - 範囲：`scripts/ci/java-rules.mjs`、`@SuppressWarnings("unchecked")` のある 11 ファイル、`docs/rules/backend-coding.md`、`docs/guides/ci.md`
- [ ] **T192-03-04** バックエンドの書き方 第 7.1 節に、getter の基準と `Objects.requireNonNull` のメッセージの書き方を書き、規則の形でない 2 つのメッセージを直す。テストの実行方法のコンパイル失敗の読み方から、バックエンドの書き方 第 7.2 節にリンクする（DC-192-20）。
  - 依存：T192-03-03（バックエンドの書き方を共に変えるため）
  - 範囲：`docs/rules/backend-coding.md`、`docs/guides/running-tests.md`、`main/config/security/OpenApiOperations.java`
- [ ] **T192-03-05** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`scripts/ci/check-all.sh` を実行する。DC-192-16〜20 を満たすことを確認する。`git grep "required = false"` の全ての引数に `@Nullable` があること（DC-192-17）も確認する。指摘を修正する。
  - 依存：T192-03-01〜04
