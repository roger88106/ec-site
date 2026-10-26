# 段階 4：Java の import の検査と修正

- 前提：段階 1〜3（変えたファイルの import を、まとめて直すため）
- 完了条件：DC-192-13〜15 を満たす。Checkstyle が全てのソースセットで実行され、import の違反が 0 件である。追加した検査が、違反の例で違反を検出する（DN-192-4）。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 6 節、[spec](../spec.md) の DS-192-4 と DC-192-13〜15、DN-192-4、[バックエンドの書き方](../../../rules/backend-coding.md) 第 5.4 節

## タスク

実装は実装役のサブエージェントに任せる。先に import を直し、その後に検査を加える。理由：直す commit に import の行の外の変更を入れず、検査を加える commit では違反が 0 件になっているため（[plan](../plan.md) 第 4 節）。

- [x] **T192-04-01** 全てのソースセットの、使われていない import と並びの乱れた import を、[plan](../plan.md) 第 4 節の並びに直す（DC-192-13、14）。commit には import の行の変更だけを入れる。
  - 依存：なし
  - 範囲：違反のある Java のファイル（再調査で 52 ファイルと 2 件。実装役は、[plan](../plan.md) 第 4 節の並びで全てのファイルを調べ直す）
- [x] **T192-04-02** Checkstyle に `UnusedImports` と `CustomImportOrder` を加え、全てのソースセットで実行する。Javadoc の書き漏れの検査は main だけにする。違反の例を一時的に入れて、検査が失敗することを確認する（DC-192-15）。
  - 依存：T192-04-01
  - 範囲：`config/checkstyle/checkstyle.xml`、`config/checkstyle/` の抑止の設定（新規）、`build.gradle.kts` の Checkstyle の設定
- [x] **T192-04-03** バックエンドの書き方 第 5.4 節に、import の並び、IntelliJ の import 整理の設定の合わせ方、Checkstyle が確認することを書く。「CI と commit の前の hook では検査しない」の文を、import だけは検査する形に直す。CI の検査の文書に、Checkstyle の対象の広がりを書く（DC-192-14）。
  - 依存：T192-04-02
  - 範囲：`docs/rules/backend-coding.md`、`docs/guides/ci.md`
- [x] **T192-04-04** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`scripts/ci/check-all.sh` を実行する。DC-192-13〜15 を満たし、T192-04-01 の commit に import の行の外の変更がないことを確認する。指摘を修正する。
  - 依存：T192-04-01〜03

## 段階のレビューの結果

2026-10-26。段階 4 と段階 5 をまとめて、profile「厚め」でレビューした。一般の観点（汎用）は高 0・必須 2、重要な観点（高性能）は高 0・必須 2（2 件とも一般の観点と同じ）。結果は `/.local/review/202610261700-192-s45/`。段階 4 の指摘はなかった（段階 5 の結果を見る）。

- import の修正の commit は、import の行と空行の外を変えていないことを、レビューと `git diff` で確認した。直したのは 794 ファイルのうち 66 ファイルで、使われていない import は 4 件だった。再調査（52 ファイルと 2 件）より多いのは、段階 1〜3 で変えたファイルと、再調査の数え方の違い（ネストしたクラスなど）のためである。
- IntelliJ の既定の import の並び（`javax.*` が `java.*` の前）と、規則の並び（文字コードの順で `java.` が先）の違いを、バックエンドの書き方 第 5.4 節に書いた。
- `./gradlew check`（全てのソースセットの Checkstyle を含む）、`scripts/ci/check-all.sh` が通った。段階 4 は domain の振る舞いを変えないので、`./gradlew pitest` は実行しない。
