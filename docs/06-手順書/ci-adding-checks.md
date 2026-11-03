# CI の検査の追加

CI の検査の script を加える手順と、補助の script の置き場所。検査を加える・修正するときに読む。

## 1. 検査を追加する手順

- 検査を追加するときは、次の手順で作る。
  - 引数なしで実行できる `.mjs` を直下に置き、テストを `*.test.mjs` に書く。`check-all.sh`、CI、hook は修正しなくてよい。
  - 処理は `if (isMain(import.meta.url)) { … }`（`lib/is-main.mjs`）の中に書き、テストが import しても動かないようにする。
  - 違反は標準出力に出し、最後の行に要約の行（`<何>：<件数>`。例：`切れた参照：0`）を出して、違反があれば終了コード 1 にする。
  - 先頭のコメント（1 行目から `//`）に、確認すること、失敗したとき、除外を書く。
  - [CI の検査の script](ci-checks.md)の表に行を加える（`ci-docs` の検査が、表と直下の script の対応を確認する）。
  - Git の一覧は `listGitFiles`（`lib/git-files.mjs`）で求め、`git ls-files` を直接呼ばない。対象が 0 件のとき `exitIfNoFiles` で失敗にする（`lib/git-files.test.mjs` が確認する）。
  - 対応していない書き方の行は、読み飛ばさずに `describeUnreadableLine`（`lib/unreadable-line.mjs`）で `ファイル:行 内容（理由）` の形にして出し、失敗にする。
  - 除外の一覧を持つなら、パスを `exclusionsPath`、誤りの文を `checkExclusions`（`lib/exclusions.mjs`）で作る。
  - 画面のソースを読むなら `lib/screen-sources.mjs`（違う対象を読むときは理由を書く）、行番号が必要なら `lib/line-number.mjs` を使う。

## 2. 補助の script の置き場所

- 次の補助の script は、各ディレクトリに置く。`check-all.sh` は、これらの script のテストだけを実行し、script 自体は実行しない。
  - `report/` の下の `coverage-summary.mjs`、`coverage-baseline.mjs`、`mutation-report.mjs`：引数が必要（[テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」）。
  - `licenses/`：画面の依存が必要な検査。
  - `globals/` の下の `lib-globals.mjs`：TypeScript の依存が必要な、組み込みの名前の一覧の生成と確認（一覧の `lib-globals.json` を `frontend-rules` が読む。`frontend-rules.mjs` の先頭のコメント）。
  - `workflow/` の下の `main-source.mjs`：workflow から呼ぶ判定（元のブランチの名前が引数）。
  - `review/` の下の `test-diff.mjs`：レビューで手動で使う補助（[テストの実行方法](running-tests.md)の「既存のテストを変えていないことの確認」）。
  - `mermaid/` の下の `mermaid-check.mjs`：Chrome とネットワークが必要な Mermaid の図の描画の検査（[CI の検査の script](ci-checks.md)の「Mermaid の図の描画の検査」）。
- 複数の検査が使う関数は `lib/` に置き（テストは `*.test.mjs`）、役割は各ファイルの先頭のコメントに書く。
