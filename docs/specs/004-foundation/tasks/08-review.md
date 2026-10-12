# 段階 8：見直し

- 前提：段階 7
- 完了条件：DC と DN をすべて確かめ、満たさないものを直すか、直さない理由を報告している。すべてのテスト、E2E、CI が通る。
- 参照：[plan](../plan.md) 第 7 節、`docs/style/review.md`

## タスク

- [ ] **T4801** `docs/style/review.md` を使って、本 spec の変更を見直す（DC-4 の初めての使用。使いにくい所があれば `review.md` を直す）。
- [ ] **T4802** 文書の見直し：`.local/agent/check-zh.mjs --all` が通る。constitution、AGENTS.md、README、`docs/style/`、`docs/dev/`、skill を読み、中国語の言い回しや食い違いを直す。
- [ ] **T4803** plan 第 7 節の確かめ方で、DC-1〜26、63 と DN-1〜3 を 1 つずつ確かめて表にする。CI の所要時間（DN-3）を含める。
- [ ] **T4804** 完了確認：直したこと、直さずに残したこと（理由付き）を報告し、spec 004 を完了にする。README の spec の状態を直す。spec の区切りなので、引き継ぎの文書を作る。
