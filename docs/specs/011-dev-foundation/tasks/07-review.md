# 段階 7：レビュー

- 前提：段階 1〜6（段階 6 を Non-goals に移したときは、段階 1〜5）
- 完了条件：spec の全ての DC を plan 第 10 節の方法で確認し、満たさないものは修正するか、理由を報告している。CI の直近 3 回の時間を記録している。`./gradlew check`、`check-frontend.sh`、`check-all.sh`、E2E がローカルと CI で通る。
- 参照：[plan](../plan.md) 第 10 節、[書き方の規則とレビュー](../../../rules/review.md)

## タスク

- [x] **T011-07-01** script のレビュー：本 spec で追加した・修正した script（`check-all.sh`、`reset.sh`、`e2e.sh`、hook、`exclude-ports.ps1`、`showcase-counts.mjs`、`ac-check.mjs`、`hardcoded-text.mjs`、`spec-numbers.mjs`）と Gradle・ci.yml の差分を、書き方の規則と照合する。DN-11 のとおり、CI の検査の手順が減っていないことを確認する。
- [x] **T011-07-02** 文書のレビュー：spec・plan・notation・`docs/specs/mvp.md`・`docs/dev/ci.md`・setup・skill `local-dev` と実装の食い違いを修正する。
- [x] **T011-07-03** CI の時間の記録：直近 3 回の全体の時間を spec の末尾に書く（DC-85）。
- [x] **T011-07-04** 完了確認：修正したこと、修正せずに残したこと（理由付き）をまとめて報告し、spec 011 を完了にする（README の進捗と `docs/specs/mvp.md` の状態を修正する）。

## レビューの結果

### 修正したこと

- `ac-check`：クラスの `@DisplayName` の後の注釈の引数に括弧があるときと、record・sealed のクラスのときに、まとまりの名前を対応とみなしていた（T011-07-01）。
- `hardcoded-text`：初めの版は JSX の属性の後の `/>` を正規表現の始まりと読んだ（T011-02-08 で修正し、テストを追加した）。
- skill `local-dev` と CI の検査の文書を、実装に合わせた（T011-07-02）。
- Javadoc の検査を、利用者の判断で private まで広げ、漏れの 8 件を修正した（T011-05-08、T011-05-09）。
- 利用者がマシンでポートの予約を変え、Zipkin（9411）も使えるようになった（T011-03-06）。

### 修正せずに残したこと

| 項目 | 理由 |
|------|------|
| `hardcoded-text` が、JSX の文字の中の記号の後の `/`（`<p>(/日本語)</p>` など）を正規表現と読み、行の終わりまでを検査しない | まれで、修正するには JSX の構文を読む必要がある。冒頭のコメントに検出漏れとして書いた |
| 統合テストの `maxParallelForks` | ローカルで 13% 短くなるが、CI が 10 分以内なので採用しない（plan 第 4.2 節、第 8.1 節） |
| 機能紹介のページの仕様書の数（10） | 再撮影するときに `showcase-counts.mjs` で修正する（setup 第 6 節） |
| commit の単位 | T011-03-05 の setup の修正が T011-03-06 の commit に入り、T011-06-01〜03 を 1 つの commit にした。push した後なので書き換えない |

### spec 012 に回したもの

[MVP の完成までの一覧](../../mvp.md) 第 3 節の表（AC-90 のテスト、管理画面のタイムアウトで 1 回失敗したテスト）。
