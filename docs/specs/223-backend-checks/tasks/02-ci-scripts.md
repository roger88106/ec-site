# 段階 2：CI の検査の script

- 前提：段階 1。spec 225 がマージされた。最新の `develop` を取り込み、spec 225 が整えた共通の関数と走査のテストの表、spec 226 の進み具合（`spec-numbers` の対象）を、この段階のタスクの前提と照合した。結果を追跡の issue にコメントした（[plan](../plan.md) 第 8 節）。
- 完了条件：DC-223-2、6、13〜17、22 を満たす。追加・修正した検査は、テストの違反の例で失敗することを確認した（DN-223-6）。`./gradlew check`、`scripts/ci/check-all.sh`、`scripts/dev/check-frontend.sh admin` が通る。
- 参照：[plan](../plan.md) 第 1 節、[plan](../plan.md) 第 2 節、[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 10 節、[plan](../plan.md) 第 11.5 節、[spec](../spec.md) の DS-223-1、2、4、6、DN-223-1〜8

## タスク

実装は実装役のサブエージェントに任せる。CI の検査の script のテストと実装は、同じ commit にする（[開発の進め方](../../../rules/workflow.md) 第 2.1 節）。commit の本文に、レッドを確認したことを書く。

T223-02-01〜05 は、全て [CI の検査](../../../guides/ci.md) の表を変えるので、`[P]` のタスクはない。番号の順に行う。`scripts/ci/` の直下に検査を加えるタスクは、CI の検査の表に行を加え、spec 225 の走査のテストの表に従う。

- [ ] **T223-02-01** 権限の対応表の検査の script で、`security` のない店舗の操作を問題に加える。テストに違反の例と、token 不要を明示した操作の例を書く（DC-223-2）。CI の検査の行を直す。
  - 依存：なし
  - 範囲：`scripts/ci/role-permissions.mjs`、`role-permissions.test.mjs`、`docs/guides/ci.md`
  - 確認方法：テストが、実装の前に失敗し、実装の後に通る。
- [ ] **T223-02-02** spec の番号の検査で、区切りのない番号と全角の番号を検出する（DC-223-13、14、[plan](../plan.md) 第 4 節）。テストに違反の例と、誤検出がない例を書く。
  - 依存：T223-02-01（同じ文書を変えるため）
  - 範囲：`scripts/ci/spec-numbers.mjs`、`spec-numbers.test.mjs`、`docs/guides/ci.md`
  - 確認方法：テストが、実装の前に失敗し、実装の後に通る。今のコードで新しい違反がない（あれば、このタスクで書き換える）。
- [ ] **T223-02-03** spec の番号の検査の対象に、Gradle の設定のファイル（`*.gradle.kts`）と、ルートと各パッケージの直下の設定のファイル（[plan](../plan.md) 第 4 節の表）を加え、新しく見つかる番号を書き換える（DC-223-15〜17）。コメントの書き方 第 1 節と CI の検査の行を直す。
  - 依存：T223-02-02
  - 範囲：`scripts/ci/spec-numbers.mjs`、`spec-numbers.test.mjs`、`backend/ecsite/build.gradle.kts`（コメントだけ）、`e2e/playwright.config.ts`、`e2e/playwright.showcase.config.ts`、`docs/rules/comments.md`、`docs/guides/ci.md`
  - 確認方法：テストに、対象のファイルの種類ごとの違反の例を書く。`node scripts/ci/spec-numbers.mjs` が通る。spec 226 が先にマージされていれば、対象の定義を 1 つにまとめる（[plan](../plan.md) 第 10 節）。
- [ ] **T223-02-04** 新しい CI の検査の script `test-helpers` を作り、統合テストでイベントの発行の記録を読む操作をテストの補助の外で呼ばないことを確認する（DC-223-6、[plan](../plan.md) 第 2 節）。CI の検査の表に行を加え、テストの書き方と用語集の「テストの補助」の行を直す。
  - 依存：T223-02-03（同じ文書を変えるため）
  - 範囲：`scripts/ci/test-helpers.mjs`、`test-helpers.test.mjs`、`scripts/ci/check-all.sh`（検査の一覧が script の中にあるとき）、`docs/guides/ci.md`、`docs/rules/test-writing.md`、`docs/design/glossary.md`
  - 確認方法：テストに、補助の外で記録を読む例（違反）と、補助へ渡すために注入するだけの例（違反でない）を書く。対象のファイルが 0 件のときに失敗することを、テストで確認する。今のコードで通る。
- [ ] **T223-02-05** 管理画面の IdP の既定値と、ローカルの検査の script、CI、環境変数の文書の値が一致することを、環境変数の既定値の検査に加える（DC-223-22、[plan](../plan.md) 第 11.5 節）。CI の検査の行を直す。
  - 依存：T223-02-04（同じ文書を変えるため）
  - 範囲：`scripts/ci/env-defaults.mjs`、`env-defaults.test.mjs`、`docs/guides/ci.md`
  - 確認方法：テストに、どれかの値が違う例を書く。今の値で通る。`scripts/dev/check-frontend.sh admin` で管理画面のビルドが通る。
- [ ] **T223-02-06** 段階のレビュー（profile「厚め」）と全体の検査：`./gradlew check`、`scripts/ci/check-all.sh`、`scripts/dev/check-frontend.sh admin` を続けて実行する。DC を満たし、追加・修正した検査がテストの違反の例で失敗することを確認する。指摘を修正する。
  - 依存：T223-02-01〜05
