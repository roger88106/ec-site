# CI の検査の追加

CI の検査の script を加える手順と、補助の script の置き場所。検査を加える・修正するときに読む。

## 検査を追加する手順

- 引数なしで実行できる `.mjs` を検査のフォルダ（`scripts/ci/` の `docs`・`spec`・`ja`・`java`・`config`・`frontend` のどれか）の直下に置き、テストを `*.test.mjs` に書く。`check-all.sh`、CI、hook は修正しなくてよい。
- 処理は `if (import.meta.main) { … }`（Node の標準の判定）の中に書き、テストが import しても動かないようにする。
- 違反は標準出力に出し、最後の行に要約の行（`<何>：<件数>`。例：`切れた参照：0`）を出す。終了コードは 0（通過）・1（違反）・2（使い方か環境の誤り。例：知らない引数、読むファイルがない、対象が 0 件）にする。
- 先頭のコメント（1 行目から `//`）に、確認すること、失敗したとき、除外を書く。
- [CI の検査の script](ci-checks.md)の表に行を加える（`ci-docs` の検査が対応を確認する）。
- Git の一覧は `listGitFiles`（`scripts/shared/git/git-files.mjs`）で求め、`git ls-files` を直接呼ばない。対象が 0 件のとき `exitIfNoFiles` で失敗にする。
- 対応していない書き方の行は、読み飛ばさずに `describeUnreadableLine`（`lib/source/unreadable-line.mjs`）で失敗にする。
- 除外の一覧を持つなら、パスを `exclusionsPath`、誤りの文を `checkExclusions`（`lib/output/exclusions.mjs`）で作る。
- 画面のソースを読むなら `lib/docs/screen-sources.mjs`（違う対象を読むときは理由を書く）、行番号が必要なら `lib/source/line-number.mjs` を使う。

## 検査にするかの基準

検査を書く前に、規則ごとに次の順で決める。

- 既製のツール（ArchUnit、Checkstyle、Biome など）で書けるなら、ツールに任せる。script には、複数のファイルを比べる規則と、ツールが対象にしないものを読む規則だけを置く（[scripts の設計](../02-設計/scripts.md)の「設計の判断」の※1）。
- 検査を書いて保つ費用が、防げる誤りの大きさを上回るなら、検査にしない。レビューだけで見るか、規則を消す。

## script の書き方

- 関数名は、動きで決める。
  - `read*`：読む。
  - `parse*`：解析する。
  - `check*`：真偽か違反の一覧を返す判定。
  - `find*`：見つからないときに空を返す。
  - `get*`：見つからないときに失敗する。
- catch の変数は `e` にする。
- 外部のコマンドは `execFileSync` に引数の配列を渡して呼ぶ。シェルの文字列を組み立てて渡さない。
- 他から使われない export を置かない。
- ディレクトリの列挙の順や現在時刻に依存しない（順は並べてから使う）。
- 行ごと・語ごとに、同じ計算（正規表現の組み立て、ファイルの読み込み）を繰り返さない。検査は commit のたびに実行されるので、小さな無駄も積み重なるため。
- パスの区切りと改行（CRLF）の差で結果が変わらないように書く。Windows と CI（Linux）の両方で同じ結果を出すため。

## テストの観点

- 失敗の条件ごとに、通る例と失敗する例を書く。
- 閾値は、境界の両側（通る最大の値と失敗する最小の値）を確認する。
- 違反の出力は、`ファイル:行` と規則の名前まで確認する。
- 一時のフォルダに入力を作って動かし、リポジトリの実際のファイルに依存しない。
- git を呼ぶテストは、環境変数を `withoutGitEnv` で外して動かす。理由：commit の前の hook の中では `GIT_DIR` などが設定され、試す一時のリポジトリでなく commit 中のリポジトリを指すため。

## 補助の script の置き場所

- 次の補助の script は、各ディレクトリに置く。`check-all.sh` は、これらの script のテストだけを実行し、script 自体は実行しない。
  - `report/` の下の `coverage-summary.mjs`、`mutation-report.mjs`：引数が必要（[テストの実行方法](running-tests.md)の「カバレッジと mutation score の基準の検査」）。
  - `licenses/`：画面の依存が必要な検査。
  - `backend/` の下の `config-defaults.mjs`：バックエンドのコンパイルの出力（設定の一覧）が必要な、環境変数の文書の既定値の照合。CI のビルドのジョブだけで実行し、commit の前の hook では実行しない。
  - `review/` の下の `test-diff.mjs`：レビューで手動で使う補助（[テストの実行方法](running-tests.md)の「既存のテストを変えていないことの確認」）。
  - `mermaid/` の下の `mermaid-check.mjs`：Chrome とネットワークが必要な検査（[CI の検査の script](ci-checks.md)の「Mermaid の図の描画の検査」）。
- 複数の検査が使う関数は `lib/` に置き（テストは `*.test.mjs`）、役割は各ファイルの先頭のコメントに書く。
