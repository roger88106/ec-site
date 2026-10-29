# 弾と段の終わり

skill `sessions` の「弾の終わり」「段の終わり」の詳細。

## 集計と profile の再検討

- `node .claude/skills/review/stats.cjs --round <弾> --profile <profile> <spec の番号...>` を、弾の終わりに issue 担当のセッションが実行する。弾の spec と profile は、issue 担当のセッションが知っている。
- profile ごとに、その profile で動いた spec だけを渡して実行する。1 つの弾に複数の profile があれば、実行を分ける。理由：profile は spec の起動ごとに決まるため（[作業の形と使用量](../../../docs/rules/resources.md)）。
- 結果（profile・時点ごとの指摘の数と採用の割合）を読み、profile の再検討を利用者に提案する。レビューを減らすときは、見逃しの台帳で確認してから決める（[エージェントの作業フロー](../../../docs/rules/subagents.md) 第 5 節）。

## 見逃しの台帳

- 弾の終わりに、issue 担当のセッションが台帳（label「見逃し」の issue）を読む（`gh issue list --label 見逃し`）。
- 本文の「関連」に、エージェントがマージしたと書いたものが 1 件でもあれば、高の経路の一覧と、低のマージの条件の再検討を利用者に提案する。台帳の書き方は [エージェントの作業フロー](../../../docs/rules/subagents.md) 第 5 節。

## 効果の測定

- 整理の第 1 段の測定は、issue #265（2026-10-29）で済んだ。閾値・並行の上限・spec の最後のレビューの量は、判断する数が足りず変えていない（[作業の形と使用量](../../../docs/rules/resources.md)）。次は、整理の第 2 段の最初の弾で、この 3 つを測り直す。測る作業そのものは、後回しの作業の起票の案に入れる。
- 方法は、整理の前の基準（2026-10-27）と同じ script に、レビューの記録の集計（`node .claude/skills/review/stats.cjs`。使い方は [review](../review/SKILL.md) 手順 6 と stats.cjs の冒頭）を加える。セッションの記録（jsonl）から spec ごとの token と時間を集計する `/.local/refactor/baseline/tokens.cjs`（`node tokens.cjs sessions|specs`）と、結果の `report.md`。Git に入らないので、新しい clone にはない。ないときは、利用者に所在を聞く。
- 比べる項目：レビューの費用の割合、段階ごとの高の件数、待機の token、固定の文脈の大きさ。

## 振り返り

- 弾と段の終わりに、issue 担当のセッションが skill `agent-retro` で行う。ファイルは `/.local/retro/` に置く。
- 弾の終わりに、ファイルの問題の短い名前を数え、別のセッションで 2 回以上出た問題を整理の issue の案にして利用者に示す。案にした問題には、ファイルに印を付ける。

## 定期の全体のレビュー

- 段（または milestone）の終わりに、issue 担当のセッションが、提案だけを出す全体のレビューとして行う。skill `review` の全体のレビューの手順を使う。
- 材料に、既存の指摘の一覧（issue「既存のレビューの指摘の一覧」）と、振り返りの集計を加える。
- 定期の自動の実行は使わない。無人で動かすと使用量の配分に従わなくなるため。
