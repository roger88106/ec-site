# 段階 5：spec の最後

- 前提：段階 4
- 完了条件：spec の最後のレビューの指摘を修正し、全ての検査が通り、spec をアーカイブに移して、pull request を作った。
- 参照：[plan](../plan.md) 第 8 節、[spec](../spec.md) の DN-104-5、[開発の進め方](../../../rules/workflow.md) 第 4.2 節

## タスク

- [ ] **T104-05-01** spec の最後のレビュー（profile「厚め」：一般の観点を汎用で 2 回、重要な観点を高性能と汎用。ファイルをまたぐ影響の観点を含める）を行い、指摘を修正す。
  - 依存：段階 4
  - 範囲：本 spec で変えた全てのファイル
- [ ] **T104-05-02** `CI=true scripts/ci/check-all.sh`、`./gradlew check`、フロントの検査の script、E2E を続けて実行する。
  - 依存：T104-05-01
  - 範囲：なし（検査の実行）
- [ ] **T104-05-03** spec を `docs/specs/archive/1to10/` に移し、pull request の前のレビュー（高性能と組み込みのコードレビュー）を行う。
  - 依存：T104-05-02
  - 範囲：`docs/specs/104-parallel-local-env/`、`docs/specs/archive/1to10/`
- [ ] **T104-05-04** pull request を作る。本文に、マージの後に利用者がメインの作業ツリーで行う手順（`git pull`、`docker compose up -d --force-recreate seaweedfs imgproxy`、`scripts/dev/reset.sh keycloak`、管理者の PowerShell で `exclude-ports.ps1`）と、起票のまとめ方の案を書く。
  - 依存：T104-05-03
  - 範囲：pull request
