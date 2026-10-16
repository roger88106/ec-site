# 段階 7：見直し

- 前提：段階 1〜6（段階 6 を Non-goals に移したときは、段階 1〜5）
- 完了条件：spec のすべての DC を plan 第 10 節の方法で確かめ、満たさないものは直すか、理由を報告している。CI の直近 3 回の時間を記録している。`./gradlew check`、`check-frontend.sh`、`check-all.sh`、E2E が手元と CI で通る。
- 参照：[plan](../plan.md) 第 10 節、[書き方の規則と見直し](../../../process/review.md)

## タスク

- [x] **T011-07-01** script の見直し：本 spec で足した・直した script（`check-all.sh`、`reset.sh`、`e2e.sh`、hook、`exclude-ports.ps1`、`showcase-counts.mjs`、`ac-check.mjs`、`hardcoded-text.mjs`、`spec-numbers.mjs`）と Gradle・ci.yml の差分を、書き方の規則と照らす。DN-11 のとおり、CI の検査の手順が減っていないことを確かめる。
- [x] **T011-07-02** 文書の見直し：spec・plan・notation・`docs/specs/mvp.md`・`docs/dev/ci.md`・setup・skill `local-dev` と実装の食い違いを直す。
- [x] **T011-07-03** CI の時間の記録：直近 3 回の全体の時間を spec の末尾に書く（DC-85）。
- [x] **T011-07-04** 完了確認：直したこと、直さずに残したこと（理由付き）をまとめて報告し、spec 011 を完了にする（README の進捗と `docs/specs/mvp.md` の状態を直す）。

## 見直しの結果

### 直したこと

- `ac-check`：クラスの `@DisplayName` の後の注釈の引数に括弧があるときと、record・sealed のクラスのときに、まとまりの名前を読んでいた（T011-07-01）。
- `hardcoded-text`：初めの版は JSX の属性の後の `/>` を正規表現の始まりと読んだ（T011-02-08 で直し、テストを足した）。
- skill `local-dev` と CI の検査の文書を、実装に合わせた（T011-07-02）。
- Javadoc の検査を、利用者の判断で private まで広げ、漏れの 8 件を直した（T011-05-08、T011-05-09）。
- 利用者がマシンでポートの予約を変え、Zipkin（9411）も使えるようになった（T011-03-06）。

### 直さずに残したこと

| 項目 | 理由 |
|------|------|
| `hardcoded-text` が、JSX の文字の中の記号の後の `/`（`<p>(/日本語)</p>` など）を正規表現と読み、行の終わりまでを見ない | まれで、直すには JSX の構文を読む必要がある。冒頭のコメントに見逃しとして書いた |
| 統合テストの `maxParallelForks` | 手元で 13% 短くなるが、CI が 10 分以内なので入れない（plan 第 4.2 節、第 8.1 節） |
| 機能紹介のページの仕様書の数（10） | 撮り直すときに `showcase-counts.mjs` で直す（setup 第 6 節） |
| commit の単位 | T011-03-05 の setup の直しが T011-03-06 の commit に入り、T011-06-01〜03 を 1 つの commit にした。push した後なので書き換えない |

### spec 012 に回したもの

[MVP の完成までの一覧](../../mvp.md) 第 3 節の表（AC-90 のテスト、管理画面の時間切れで 1 回落ちたテスト）。
