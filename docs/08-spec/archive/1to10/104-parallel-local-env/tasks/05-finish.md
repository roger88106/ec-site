# 段階 5：spec の最後

- 前提：段階 4
- 完了条件：spec の最後のレビューの指摘を修正し、全ての検査が通り、spec をアーカイブに移して、pull request を作った。
- 参照：[plan](../plan.md) 第 8 節、[spec](../spec.md) の DN-104-5、[開発の進め方](../../../../../rules/workflow.md) 第 4.2 節

## タスク

- [x] **T104-05-01** spec の最後のレビュー（profile「厚め」：一般の観点を汎用で 2 回、重要な観点を高性能と汎用。ファイルをまたぐ影響の観点を含める）を行い、指摘を修正する。
  - 依存：段階 4
  - 範囲：本 spec で変えた全てのファイル
  - 結果（2026-10-22）：段階 4 のレビューを兼ねた。一般の観点は汎用を 2 回、重要な観点（ファイルをまたぐ影響を含む）は高性能と汎用、言い回しは汎用を 1 回（合計約 90 万 token、各 1〜8 分）。高 0 件、必須 1 件、推奨 38 件、言い回しの候補 25 件。
    - 必須 1 件（skill の E2E の節の、画面のサーバーを使い回す旨の古い記述）を直した。
    - 推奨のうち、コード 10 件（設定のファイルに書き写したスロットの値とのテスト、`release` の前のバックエンドの確認、ロックの待ちと上限、E2E のロックの作成の原子性、ロックのパスの案内、コメントの spec の番号、テストの期待値と名前、`gradlew.bat` の呼び方、バケットの削除の誤りの扱い）と、文書 12 件と言い回しを採用した（6c9634ed、30c65874 の取り込み）。
    - 採らない推奨 6 件と理由は、pull request の「推奨の指摘の扱い」に書く。
    - 機械の検査に移したもの：設定のファイル（realm、プレビューの設定、compose、SeaweedFS の権限、ポートの予約の script）のスロットの値と、ロックの待ちと上限を、script のテストにした。
- [x] **T104-05-02** `CI=true scripts/ci/check-all.sh`、`./gradlew check`、フロントの検査の script、E2E を続けて実行する。
  - 依存：T104-05-01
  - 範囲：なし（検査の実行）
  - 結果：`CI=true scripts/ci/check-all.sh`、`./gradlew check`、フロントの検査の script、E2E（20 件）を続けて実行し、全て通った。
- [x] **T104-05-03** spec を `docs/specs/archive/1to10/` に移し、pull request の前のレビュー（高性能と組み込みのコードレビュー）を行う。
  - 依存：T104-05-02
  - 範囲：`docs/specs/archive/1to10/104-parallel-local-env/`、`docs/specs/archive/1to10/`
  - 結果：pull request の前のレビュー（高性能と組み込みのコードレビュー）を行った。高 0 件、必須 1 件（DC-104-22 のプレビューでの確認の記録がない。確認して T104-04-06 に記録した）、推奨 10 件。推奨のうち、バックエンドの稼働の判定をタイムアウトで「動いている」とする修正、店舗画面の Cookie の接頭辞を 1 回だけ読む修正、skill の compose の節の案内を採用した（6c92abd2）。採らない推奨は pull request に書く。
- [x] **T104-05-04** pull request を作る。本文に、マージの後に利用者がメインの作業ツリーで行う手順（`git pull`、`docker compose up -d --force-recreate seaweedfs imgproxy`、`scripts/dev/reset.sh keycloak`、管理者の PowerShell で `exclude-ports.ps1`）と、起票のまとめ方の案を書く。
  - 依存：T104-05-03
  - 範囲：pull request
  - 結果：pull request を作った。
