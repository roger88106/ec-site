# 段階 3：NullAway と規則の検査、null の基準

- 前提：段階 2
- 完了条件：DC-192-16〜20 を満たす。追加した検査が、違反の例で違反を検出する（DN-192-4）。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 3 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-192-5 と DC-192-16〜20、DN-192-4、[バックエンドの書き方](../../../rules/backend-coding.md) 第 7 節

## タスク

実装は実装役のサブエージェントに任せる。規則の検査の script のタスク（T192-03-02、03）は、テストが失敗することをローカルで確認し、実装と同じ commit に入れる（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。DC-192-17 は既に満たしているので、コードを変えるタスクを置かず、T192-03-05 で確認する（[plan](../plan.md) 第 3 節）。

- [x] **T192-03-01** `[リスク]` NullAway に `AcknowledgeRestrictiveAnnotations` を加え、NullAway のエラーを修正する。設定のコメントに、何を見つけるかを書く。違反の例を一時的に入れて、コンパイルが失敗することを確認する（DC-192-16）。実装役は高性能にし、実装の後に高性能のレビューを行う。
  - 依存：なし
  - 範囲：`build.gradle.kts` の NullAway の設定、エラーの出た main のクラス
- [x] **T192-03-02** `[テスト]` 規則の検査のテストに、次の違反の例を追加し、失敗することを確認する（DC-192-18、19）。対象のソースセットは、`@SuppressWarnings` の規則が全てのソースセット、ワイルドカード import の規則が今の `Nullable` の規則と同じ（本番・テスト・tools）である。
  - 理由のコメントのない `@SuppressWarnings`
  - Javadoc だけで、直前に `//` のコメントのない `@SuppressWarnings`
  - JSpecify 以外の `Nullable` を含むパッケージのワイルドカード import
  - 依存：なし
  - 範囲：`scripts/ci/java-rules.test.mjs`
- [x] **T192-03-03** 規則の検査に、「`@SuppressWarnings` の直前に理由のコメントを求める規則」と「`Nullable` を含むパッケージのワイルドカード import を禁じる規則」を加える。今の 12 か所の `@SuppressWarnings("unchecked")` に理由を書く（DC-192-18、19）。バックエンドの書き方 第 7.2 節と、CI の検査の文書の `java-rules` の行に、規則の検査が確認することを書く。
  - 依存：T192-03-02
  - 範囲：`scripts/ci/java-rules.mjs`、`@SuppressWarnings("unchecked")` のある 11 ファイル、`docs/rules/backend-coding.md`、`docs/guides/ci.md`
- [x] **T192-03-04** バックエンドの書き方 第 7.1 節に、getter の基準と `Objects.requireNonNull` のメッセージの書き方を書き、規則の形でない 2 つのメッセージを直す。テストの実行方法のコンパイル失敗の読み方から、バックエンドの書き方 第 7.2 節にリンクする（DC-192-20）。
  - 依存：T192-03-03（バックエンドの書き方を共に変えるため）
  - 範囲：`docs/rules/backend-coding.md`、`docs/guides/running-tests.md`、`main/config/security/OpenApiOperations.java`
- [x] **T192-03-05** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`scripts/ci/check-all.sh` を実行する。DC-192-16〜20 を満たすことを確認する。`git grep "required = false"` の全ての引数に `@Nullable` があること（DC-192-17）も確認する。指摘を修正する。
  - 依存：T192-03-01〜04

## 段階のレビューの結果

2026-10-26。profile「厚め」。一般の観点（汎用）は高 0・必須 0、重要な観点（高性能。T192-03-01 の `[リスク]` のレビューを兼ねる）は高 0・必須 2。結果は `/.local/review/202610261500-192-s3/`。

| 指摘 | 扱い |
|------|------|
| `@SuppressWarnings` の理由の規則が、行の途中の注釈（引数、修飾子の後ろ、完全修飾名、入れ子の括弧の注釈の後ろ）を見逃す | 修正した。行の途中まで含めて全ての注釈を見つけ、テストに例を加えた |
| 同じ規則が、間の注釈が複数行のときと、テキストブロックの中の行で、誤って違反にする | 修正した。複数行の注釈を飛ばし、文字列とテキストブロックを読まないようにした |

- T192-03-01 で NullAway が出したエラーは 1 件だった。生成したモデルの `@Nullable` の値を、注釈のない引数に渡していた所である。上書きの引数のエラーは 0 件だった。null を受ける引数に `@Nullable` を付け、null のときの振る舞いは変わらないことをレビューで確認した。
- DC-192-17 の 3 つの引数には、既に `@Nullable` があった。
- 全体の検査で、段階 2 の修正で足した private のメソッドに Javadoc がなく、Checkstyle が失敗した。Javadoc を書いて直した。
- `./gradlew check`、`scripts/ci/check-all.sh` が通った。段階 3 は domain を変えないので、`./gradlew pitest` は実行しない。
