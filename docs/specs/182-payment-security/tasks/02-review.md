# 段階 2：完了

- 前提：段階 1
- 完了条件：文書がコードと一致する。spec の最後のレビューの採否を決め、反映した。E2E が通る。起票のまとめの案がある。本 spec を `docs/specs/archive/1to10/182-payment-security/` に移した。
- 参照：[plan](../plan.md) 第 6〜8 節、[spec](../spec.md)、[開発の進め方](../../../rules/workflow.md) 第 4.1 節、[開発の進め方](../../../rules/workflow.md) 第 4.2 節、[開発の進め方](../../../rules/workflow.md) 第 6 節、[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節

## タスク

- [x] **T182-02-01** 文書の照合：店舗 API の `security` に触れる設計書（店舗画面の設計書、モジュールの設計書）、作成の応答の `Location` に触れる文書、トレースの送信に触れる手順書を検索し、本 spec の後のコードと一致させる。契約の差分が `security: []` の追加と `Location` の説明だけであり、`db/migration/` に差分がないことを確認する（DN-182-2）。
  - 依存：段階 1
  - 範囲：`docs/design/`、`docs/guides/`、`docs/rules/`（本 spec に関係する所）
- [x] **T182-02-02** 全体の検査：`./gradlew check`、`scripts/ci/check-all.sh`、`./gradlew pitest` を実行する。mutation とカバレッジが基準を下回らないことを確認する。
  - 依存：T182-02-01
- [x] **T182-02-03** spec の最後のレビュー：profile「厚め」で行い、組み合わせは[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節の表に従う。セキュリティとファイルをまたぐ影響の観点を必ず含める。変えたコードを[書き方の規則とレビュー](../../../rules/review.md) と照合する。DC-182-1〜16 と DN-182-1〜5 を満たすことを確認する。推奨ごとに、自動の検査に移せるかどうかを併記する。指摘の修正でコードを変えたら、T182-02-02 の検査をやり直す。
  - 依存：T182-02-02
  - 結果：4 回（汎用 3、高性能 1）で、高 0、必須 0、推奨（重複を除く）12。T182-01-04 の後のレビューの推奨 3 件を含む。DC-182-1〜16 と DN-182-1〜5 を満たす。推奨の扱いは [spec](../spec.md) 第 10 節。採る 6 件は T182-02-09 で直す。
- [ ] **T182-02-09** spec の最後のレビューの推奨のうち、採る 6 件を直す（[spec](../spec.md) 第 10 節の表の「採る」）。テストの追加は、一時的にコードか期待値を変えて失敗しうることを確認する。直した後に `./gradlew check` と `scripts/ci/check-all.sh` を実行する（T182-02-03 の「検査をやり直す」）。
  - 依存：T182-02-03
  - 範囲：`test/module/payment/infrastructure/stripe/`（支払画面の期限のテスト）、`test/module/media/infrastructure/storage/MediaStoragePropertiesTest.java`、`test/config/security/StoreOperationSecurityTest.java`、`it/config/security/StoreApiTokenContractTest.java`、`main/common/model/base/AggregateGuards.java`、`docs/design/backend-architecture.md`（公開ネットワークへのデプロイの前提の節）
- [ ] **T182-02-04** E2E を実行し（`scripts/dev/e2e.sh`）、通ることを確認する。`Location` と契約の `security` を変えたので、店舗と管理の作成の操作を E2E で通して確認する。
  - 依存：T182-02-09
- [ ] **T182-02-05** 文書のレビュー（skill `review` の文書のレビューの手順）：本 spec で修正した文書を、skill `docs` の言い回しと論理の規則と照合する。
  - 依存：T182-02-09
  - 範囲：本 spec で修正した `docs/` の文書
- [ ] **T182-02-06** 起票のまとめの案を作る。後回しにする作業、未決事項、spec の最後のレビューの推奨のうち採用しなかったものを、既存の issue と重複しないようにまとめ、案を pull request の本文に付ける。利用者が承認したら、issue 担当のセッションが起票する（[開発の進め方](../../../rules/workflow.md) 第 4.1 節）。
  - 依存：T182-02-03〜05
- [ ] **T182-02-07** pull request の前のレビュー：profile「厚め」のとおり、高性能のレビュー役が spec との一致と完了の定義を確認し、組み込みのコードレビュー（`/code-review`）も行う。指摘の扱いを決め、修正する（[エージェントの作業フロー](../../../rules/subagents.md) 第 4 節）。
  - 依存：T182-02-06
- [ ] **T182-02-08** 本 spec（spec、plan、tasks）を `docs/specs/archive/1to10/182-payment-security/` に移し、参照を修正する。移した後に `scripts/ci/check-all.sh` が通る（[開発の進め方](../../../rules/workflow.md) 第 4.2 節）。そのタスク自身を含む全てのチェックボックスを `[x]` にする。
  - 依存：T182-02-07

T182-02-08 は tasks の最後に行うタスクで、pull request を作る直前の commit で行う。理由：アーカイブした spec は、進捗の検査が全てのタスクの済みを求めるため。

## tasks の外の作業

pull request の作成と、その後の `develop` へのマージと後片付けは、tasks に書かない（[開発の進め方](../../../rules/workflow.md) 第 4.2 節、[開発の進め方](../../../rules/workflow.md) 第 5.7 節）。pull request はテンプレートで作り、本文に受け入れの確認、推奨の扱い、起票する issue の案を書き、#182 にコメントする。マージは spec 担当のセッションが利用者の許可を得てから行う。
