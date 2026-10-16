# 段階 7：見直し

- 前提：段階 1〜6（段階 6 を Non-goals に移したときは、段階 1〜5）
- 完了条件：spec のすべての DC を plan 第 10 節の方法で確かめ、満たさないものは直すか、理由を報告している。CI の直近 3 回の時間を記録している。`./gradlew check`、`check-frontend.sh`、`check-all.sh`、E2E が手元と CI で通る。
- 参照：[plan](../plan.md) 第 10 節、[書き方の規則と見直し](../../../process/review.md)

## タスク

- [x] **T011-07-01** script の見直し：本 spec で足した・直した script（`check-all.sh`、`reset.sh`、`e2e.sh`、hook、`exclude-ports.ps1`、`showcase-counts.mjs`、`ac-check.mjs`、`hardcoded-text.mjs`、`spec-numbers.mjs`）と Gradle・ci.yml の差分を、書き方の規則と照らす。DN-11 のとおり、CI の検査の手順が減っていないことを確かめる。
- [x] **T011-07-02** 文書の見直し：spec・plan・notation・`docs/specs/mvp.md`・`docs/dev/ci.md`・setup・skill `local-dev` と実装の食い違いを直す。
- [ ] **T011-07-03** CI の時間の記録：直近 3 回の全体の時間を spec の末尾に書く（DC-85）。
- [ ] **T011-07-04** 完了確認：直したこと、直さずに残したこと（理由付き）をまとめて報告し、spec 011 を完了にする（README の進捗と `docs/specs/mvp.md` の状態を直す）。
