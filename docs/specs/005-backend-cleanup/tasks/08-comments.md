# 段階 8：コメント

- 前提：段階 7
- 完了条件：DC-44、65。コメントだけの commit（DN-5）。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 6 節、`docs/style/comments.md`

## タスク

- [ ] **T5801** 「複雑な処理」の基準に当たる所の一覧を作る（stream の 2 段以上の変換、`groupingBy`・`flatMap`・`reduce`、入れ子の繰り返し、3 つ以上の条件、3 つ以上の分岐）。すでに説明があるものを除く。
- [ ] **T5802** 一覧の所に、コードを読んで意味を確かめてから、何の結果を得るか・なぜそうするかを 1 行で書く。分からない所は書かずに一覧にして利用者に聞く。モジュールごとに commit。
- [ ] **T5803** 本番のコード（`src/main` と `build.gradle.kts`）のコメントの spec・plan・tasks の番号を、規則の言葉に書き換える（`docs/style/comments.md` 第 1 節）。番号を消すだけで済む所は消す。モジュールごとに commit（DC-65）。
