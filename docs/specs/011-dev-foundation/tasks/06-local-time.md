# 段階 6：手元の待ち時間と手間（P2）

- 前提：段階 3
- 完了条件：手元のビルドとテストの時間を測った結果と、選んだ短縮の方法が plan にある。効果が測れたものだけが入っている。機能紹介のページの数字を script で出せる。
- 参照：[plan](../plan.md) 第 8 節

## タスク

- [ ] **T011-06-01** 手元の時間の測定（plan 第 8.1 節の表）。`./gradlew check --profile`、1 ファイルを直した後の `compileJava`・`check`、バックエンドの起動。結果を plan に足す。
- [ ] **T011-06-02** 候補の測定：DevTools、configuration cache、Testcontainers の使い回し、統合テストの `maxParallelForks`。それぞれの効果と、入れるかの案を利用者に示す。
- [ ] **T011-06-03** 利用者が選んだものを入れ、使い方を setup に書く（何も入れないなら、理由を plan に書いて終える）。
- [ ] **T011-06-04** `scripts/dev/showcase-counts.mjs`（plan 第 8.2 節）。出力を機能紹介のページの今の数字と照らし、setup 第 6 節から指す。
- [ ] **T011-06-05** 段階の完了の確認：`./gradlew check`、`check-all.sh` が通る。
