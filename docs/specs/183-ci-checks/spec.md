# Spec 183：CI の検査の script の共通の処理と読み方を整える

ステータス：確定

## 1. 背景と目的

spec 156、157、158 の後に、CI の検査の script（`scripts/ci/`）の整理と、新しい検査の追加が残っている。追跡の issue #183 は、同じ script を変える 7 つの issue をまとめた。

- spec 158 の後回しにした項目（spec 158 第 8 節の 1〜27）は、#165〜#168 に起票した。spec 157 の備忘が、#166 のコメントに加わっている。
- spec の確認で、新しい検査（#20、#161、#169）を別の spec（追跡の issue #185）に分けると決めた（第 6 節の 1）。本 spec は、今の検査を整える #165〜#168 を扱う。

追跡の issue は [#183](https://github.com/roger88106/ec-site/issues/183)、扱う issue は [#165](https://github.com/roger88106/ec-site/issues/165)、[#166](https://github.com/roger88106/ec-site/issues/166)、[#167](https://github.com/roger88106/ec-site/issues/167)、[#168](https://github.com/roger88106/ec-site/issues/168) である。作業ブランチは `183-ci-checks`。本 spec は整理の spec で、規模は M である（第 6 節の 1）。API の契約とデータベースを変えず、AC を変えないため。

## 2. スコープ

| # | 目的 | 対象 | 優先度 | issue |
|---|------|------|--------|-------|
| DS-183-1 | 検査の script の共通の処理が 1 か所にまとまり、テストがある | D | P1 | #165 |
| DS-183-2 | 既存の検査の読み方の誤検出と見逃しがなくなる | D | P1 | #166 |
| DS-183-3 | CI と package の書き方がそろう | D | P2 | #167 |
| DS-183-4 | ローカルのポートの一覧が 1 か所にあり、食い違いを検査が見つける | D | P2 | #168 |

## 3. 目的と完了条件

- 1 つの DC に条件を 1 つ書く。出典の列は、spec 158 第 8 節の項目の番号か、issue のコメントである。
- 整理の spec の DC の確認方法は、[開発の進め方](../../rules/workflow.md) 第 1.2 節では plan で決める。規模 M には plan がないので、tasks で決める。
- 新しい検査と強めた検査で今のコードと文書に違反が出るときは、同じタスクで違反を修正する（spec 158 第 6 節の 2 と同じ扱い）。正しい書き方だけを、理由付きで除外の一覧に置く。

### DS-183-1 検査の script の共通の処理（D / P1）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-183-1** | 検査の script が直接実行されたかの判定が、全ての script で 1 通りである | 1 |
| **DC-183-2** | 検査の一括の実行（`check-all.sh`）は、要約の行を出さずに終わった検査を失敗にする | 2 |
| **DC-183-3** | shallow clone の確認が、共通の関数の 1 か所にある | 3 |
| **DC-183-4** | テストのファイルの探し方とテストの関数の読み方が、AC の照合と機能紹介の数え方で共通である | 4 |
| **DC-183-5** | 文言の直書きと入力の制約の検査の出力の形（件数の行、出力先）が、他の検査と同じである | 5 |
| **DC-183-6** | commit の前の hook に、検査を選ぶ処理と失敗の終了コードのテストがある | 6 |
| **DC-183-7** | 検査の一括の実行に、成功・失敗の判定のテストがある | 6 |
| **DC-183-8** | 文言の番号の検査に、共通の関数と同じ名前で意味の違う関数がない | 24 |
| **DC-183-9** | 一致した位置の行番号を求める処理が、一致ごとにファイルの先頭から数え直さない | 25 |
| **DC-183-10** | フロントの規則の検査が、両方の画面にあるファイルの一覧を 1 回だけ求める | 26 |
| **DC-183-11** | E2E が実行中かの判定が、E2E の実行の script と開発の script で 1 か所にある | 27 |

### DS-183-2 既存の検査の読み方（D / P1）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-183-12** | compose のポートの検査と、ロールと権限の対応表の検査が、YAML の書き方（1 行の書き方、字下げ）の違いで項目を読み飛ばさない | 7 |
| **DC-183-13** | 環境変数の既定の検査は、設定の値のクラスを読めなかったときに失敗にする | 8 |
| **DC-183-14** | Java の規則の検査は、コメントの中を違反と読まない。対象は、文字列でない値を `valueOf` に渡す規則、Lombok、`Nullable` の規則 | 9 |
| **DC-183-15** | 使わない語の検査は、使わない語を一部に含む正しい語（許す語の一覧にあるもの）を失敗にしない | 10 |
| **DC-183-16** | テストの時間の待ちの検査は、E2E の実行の script、設定のファイル、画面のテストの補助も読む | 11 |
| **DC-183-17** | 文言の直書きの検査は、コメントの除去に共通の関数を使う | 12 |
| **DC-183-18** | マイグレーションの検査は、`develop` に入っていない自分のブランチのマイグレーションの振り直し（改名・削除）を失敗にしない | 19 |
| **DC-183-19** | Git の一覧を読む全ての検査が、対象のファイルが 0 件のときに失敗にする | 20 |
| **DC-183-20** | 実行環境のバージョンの検査は、新しい job に直接書いた Java と Node のバージョンを見つける | 21 |
| **DC-183-21** | 閉じ括弧の探し方とコメントの除去は、正規表現のリテラルの中の括弧と引用符を、括弧・文字列として扱わない | 22 |
| **DC-183-22** | AC の照合は、`each` の表の中に関数の呼び出しがあっても、テスト名を読む。読めないテストは、無視せず失敗にする | #166 のコメント |

### DS-183-3 CI と package の書き方（D / P2）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-183-23** | バックエンドの全体の検査のコマンドが、文書と CI で 1 通りである | 13 |
| **DC-183-24** | 統合テストを分ける数が、`ci.yml` の 1 か所にある | 14 |
| **DC-183-25** | 店舗画面と管理画面のテストのコマンドが、テストのないときの扱い（`--passWithNoTests`）で同じである | 15 |
| **DC-183-26** | React のバージョンの指定の書き方が、管理画面、店舗画面、共有のパッケージで同じである | 16 |
| **DC-183-27** | ライセンスの方針の Gradle のプラグインの一覧が、ビルドで使うプラグインと一致することを、検査で確認する | 17 |
| **DC-183-28** | 複合の action を加えて Dependabot の対象（`directories`）に加えていないときに、検査が失敗する | 23 |

### DS-183-4 ローカルのポートの一覧（D / P2）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-183-29** | ローカルのポートの一覧が 1 か所にある | 18 |
| **DC-183-30** | compose、スロットの script、Windows の予約の script、Playwright の設定、開発環境の起動の文書のポートが一覧と食い違うときに、検査が失敗する。一覧から読む所は対象にしない | 18 |

## 4. 守る制約

- **DN-183-1** 消費者・運営者に見える振る舞い、API の契約、データベースを変えない。
- **DN-183-2** 検査を弱めない。検査の対象を狭めるのは、誤検出を減らすとき（DC-183-15、18 など）だけにし、理由を検査に書く。
- **DN-183-3** `scripts/ci/` は本 spec だけが変える。spec 184 は変えない。
- **DN-183-4** 共有の文書（[CI の検査](../../guides/ci.md)、規則の文書）と、spec 181、182、184 と重なるファイルの衝突は、後でマージする pull request が `develop` を取り込んで解消する。
- **DN-183-5** 新しく書くコメントと除外の理由に、spec・plan の番号を書かない（コメントの書き方 第 1 節。spec 158 の DC-158-44 と同じ）。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| マイグレーションの互換性、文書の食い違い、フロントのテストの書き方の検査（#20、#161、#169） | 第 6 節の 1 で新しい spec に分けた。DC は DC-183-31〜49 を振っていたが、新しい spec で振り直す（第 8 節） |

## 6. 決めたこと

spec の確認で、利用者が全て案 A に決めた（2026-10-24）。規模 M も同じ時に承認した。Open questions はない。

### 6.1 規模と、2 つの spec への分け方

DC は 49 で、spec 158（M、51）と同じくらいである。変えるのは検査の script、workflow、文書と、違反の修正の数行で、規模の基準（[開発の進め方](../../rules/workflow.md) 第 4.2 節）では M に当たる。分けるかは、次の 2 つで判断した。

- DS-183-1〜4（#165〜#168）は、今の検査を整える作業で、他の spec を待たずに始められる。
- DS-183-5〜7（#20、#161、#169）は新しい検査で、#20 の規則の決定と、spec 184 のマージを待つ。DS-183-1 の共通の関数（テストのファイルの探し方など）を使うので、DS-183-1 の後に作るほうが手戻りがない。

| 案 | 内容 |
|----|------|
| 案 A（推奨。採用） | 2 つの M の spec に分ける。本 spec は DS-183-1〜4（#165〜#168。DC 30）、新しい spec は DS-183-5〜7（#20、#161、#169。DC 19）。新しい spec は本 spec のマージの後に始める |
| 案 B | 1 つの M の spec のまま、段階を DS の順に並べ、DS-183-7 の段階を spec 184 のマージの後にする |
| 案 C | 1 つの L の spec にし、plan を書く |

- 案 A の理由：pull request が小さくなる。spec 184 のマージや #20 の決定を待つ間も、本 spec の作業を進められる。新しい spec は、本 spec の共通の関数がそろった `develop` から始められる。
- 案 A の手間：追跡の issue を 1 つ増やす（issue 担当のセッションが作り、#20、#161、#169 を移す）。spec の最後のレビューが 2 回になる。
- 案 A のとき、本 spec の DS-183-5〜7 の節は、新しい spec に移して番号を振り直し、本 spec から除く。
- 案 B の理由：レビューと pull request が 1 回で済む。ただし、spec 184 のマージが遅れると、本 spec の全体が待つ。
- 案 C は、API・データベース・モジュールをまたがないので、規模の基準に合わない。

### 6.2〜6.4 新しい spec に移した決定

マイグレーションの互換性の規則（6.2）、規則の文書にない #169 の検査（6.3）、spec 184 と重なる違反と検査を加える順序（6.4）は、利用者が全て案 A に決めた。新しい spec に移したので、本 spec には書かない（第 8 節）。

### 6.5 YAML を読む検査の読み方（DC-183-12）

今の検査の script は Node の標準の機能だけを使い、依存を install せずに動く（commit の前の hook でも同じ）。

- 案 A（推奨。採用）：パーサーを使わず、読む節の中で、知っている書き方に当たらない行があれば失敗にする。理由：依存を加えずに、読み飛ばしたまま成功することをなくせ、新しい書き方は失敗で気づいて読み方を足せるため。
- 案 B：YAML のパーサーをルートの開発用の依存に加え、検査がそれで読む。全ての書き方を読めるが、CI の文書の検査のジョブと hook の前に install が必要になる。

## 7. tasks

第 6 節の 1 で分けたので、分ける前の段階 4〜6（DS-183-5〜7）は新しい spec に移した。

- `[P]` のタスクはない。同じ段階のタスクは、番号の順に 1 つずつ行う。理由：多くのタスクが共有の文書（[CI の検査](../../guides/ci.md)）と共通の関数を変えるため。
- `[テスト]` のタスクは、テストを書いてレッドを確認し、同じ実装役が続けて実装する（タスクの名前は 1 つ。[開発の進め方](../../rules/workflow.md) 第 2.1 節の例外のとおり同じ commit に入れる）。
- 新しい検査と強めた検査で違反が出るときは、検査を変えるタスクで違反も修正する（第 3 節）。修正は振る舞いを変えない（DN-183-1）。
- `scripts/ci/` の直下に検査を加えるタスクは、[CI の検査](../../guides/ci.md) の表に行を加える（`ci-docs` の検査が求める）。
- 各段階の最後のレビューは、profile「厚め」で行う（[エージェントの作業フロー](../../rules/subagents.md) 第 4 節）。重要な観点に「検査を弱めていないか」（DN-183-2）を含める。

### 段階 1：検査の script の共通の処理

- 前提：spec の確定、実装の前の照合
- 完了条件：DC-183-1〜11 を満たし、`check-all.sh` が通る。
- 参照：第 3 節の DS-183-1

- [x] **T183-01-01** [テスト] 直接実行の判定、shallow clone の確認、行番号を求める処理を `lib/` の共通の関数にし、全ての検査が使う（DC-183-1、3、9）。
  - 依存：なし
  - 範囲：`scripts/ci/lib/`、`scripts/ci/` の検査とテスト
  - 確認方法：DC-183-1 は、入口の判定が `isMain` だけで、`process.argv[1]` を直接比べる所が `lib/is-main.mjs` のほかにないことを `git grep -n "process.argv\[1\]" -- scripts` で確認する。DC-183-3 は、`--is-shallow-repository` が `lib/shallow-clone.mjs` の 1 か所だけであることを `git grep` で確認する。
  - 結果：`lib/is-main.mjs`（27 の script の入口の判定）、`lib/shallow-clone.mjs`（migrations、related-specs。失敗の文を 1 つにそろえた）、`lib/line-number.mjs`（改行の位置を 1 回求めて二分探索。frontend-rules、java-rules、message-comments、time-waits）を作った。変更の前後で 25 の検査の出力が同じ。
- [x] **T183-01-02** [テスト] テストのファイルの探し方とテストの関数の読み方を共通の関数にし、AC の照合と機能紹介の数え方が使う（DC-183-4）。
  - 依存：T183-01-01（同じ `ac-check.mjs` を変える）
  - 範囲：`scripts/ci/lib/`、`scripts/ci/ac-check.mjs`、`scripts/dev/showcase-counts.mjs`、テスト
  - 変更の前後で、AC の照合の結果と機能紹介の数が同じであることを確認する
  - 結果：`lib/test-files.mjs`（ファイルの種類、テストの関数の読み方）を作り、ac-check と showcase-counts が使う。AC の照合の出力は変更の前後で同一。showcase の Vitest・Playwright の数が 583 → 579（RegExp の `.test(` 4 件を数えなくなった）。
- [x] **T183-01-03** [テスト] 文言の直書きと入力の制約の検査の出力の形を他の検査にそろえ、検査の一括の実行が要約の行のない検査を失敗にする。一括の実行の判定にテストを書く（DC-183-2、5、7）。
  - 依存：T183-01-01
  - 範囲：`scripts/ci/check-all.sh`、`scripts/ci/hardcoded-text.mjs`、`scripts/ci/input-constraints.mjs`、テスト、`docs/guides/ci.md`
  - 結果：判定を `lib/check-result.mjs`（要約の行の判定と成功・失敗の判定）に移し、`check-all.sh` から呼ぶ。2 つの検査は、違反を標準出力、最後の行を要約の行にそろえた。全ての検査が通ることを確認した。
- [x] **T183-01-04** [テスト] commit の前の hook の、検査を選ぶ処理と失敗の終了コードにテストを書く（DC-183-6）。
  - 依存：T183-01-03
  - 範囲：`scripts/dev/hooks/`、テスト
  - テストは、Git を呼ぶ前に `GIT_` で始まる環境変数を外す（spec 158 の T158-01-02 の経緯）
  - 結果：hook の処理（index のパスの絶対パス化、取り出し、検査の実行、終了コード）を `scripts/dev/lib/pre-commit.mjs` に移し、hook はそれを呼ぶだけにした。取り出しの失敗は 2、検査の失敗はその終了コードを返すことを、差し替えた処理と一時リポジトリでの実際の hook の実行でテストした。
- [x] **T183-01-05** 文言の番号の検査の関数の名前、両方の画面のファイルの一覧を 1 回だけ求めること、E2E の実行中の判定を 1 か所にまとめる（DC-183-8、10、11）。
  - 依存：T183-01-01
  - 範囲：`scripts/ci/message-numbers.mjs`、`scripts/ci/frontend-rules.mjs`、`scripts/dev/lib/e2e-running.mjs`、`e2e/scripts/run.ts`、テスト
  - 結果：message-numbers の関数を findExclusionsWithoutNumbers に改名し、frontend-rules の同じ内容のファイルの一覧を 1 回だけ求めるようにし、プロセスが実行中かの判定を lock-io.mjs の isProcessRunning にまとめた（e2e-running.mjs、run.ts、slot.mjs が使う）。既存のテストと e2e の型チェックが通り、isProcessRunning のテストを足した。
  - 確認方法：DC-183-8 は、関数の名前をレビューで読む。DC-183-10 は、frontend-rules のテスト（`duplicatedFiles` の呼び出しが定義のほかに 1 つだけであること）と、レビューで読む。DC-183-11 は、`process.kill(pid, 0)` が `scripts/dev/lib/lock-io.mjs` の `isProcessRunning` の 1 か所だけであることを `git grep -n "process.kill(" -- scripts e2e` で確認し、e2e-running.mjs・run.ts・slot.mjs がそれを使うことをレビューで読む。
- [x] **T183-01-06** 段階の最後のレビュー（profile「厚め」）を行い、指摘に対応する。
  - 依存：T183-01-01〜05
  - 結果：一般の観点を汎用で 2 回（高 0、必須 2・3）、重要な観点を高性能と汎用で（高 0・1、必須 2・0）。重複を除いて 5 件を修正した。(1) check-all.sh が終了コードを直接見て、判定の CLI が OK の行を出したときだけ成功にする。isMain は実体のパスでも比べ、commit の前の hook は入口の pre-commit-main.mjs から必ず処理を実行する。(2) ci.md の lib/ の一覧に 5 つを加えた。(3) closing-paren.mjs の使う所を直した。(4) slot.mjs の isRunning を isProcessRunning（中身は同じ、EPERM は動いているとみなす）に置き換えた。(5) hardcoded-text と input-constraints の出力の形と終了コード、--count の振る舞いのテストを足した。check-all.sh の配線（summary と none の受け渡し）の実行テストは、検査の script を一時ディレクトリに複製する必要があるので作らず、判定の関数と CLI のテストで DC-183-7 を満たすとした。

### 段階 2：既存の検査の読み方

- 前提：段階 1
- 完了条件：DC-183-12〜22 を満たし、各 DC にテストがあり、`check-all.sh` が通る。
- 参照：第 3 節の DS-183-2、第 6 節の 5

- [x] **T183-02-01** [テスト] compose のポートと、ロールと権限の対応表の検査が、読む節の中の知らない書き方の行を失敗にする（DC-183-12）。
  - 依存：なし
  - 範囲：`scripts/ci/compose-ports.mjs`、`scripts/ci/role-permissions.mjs`、テスト
  - 結果：2 つの検査が、`ports:` の節と契約の `paths:` の下で、知らない書き方の行（フロー形式、字下げの違い、引用符で囲んだパスなど）を `unknown` に集めて失敗にする。今の compose と 2 つの契約は 0 件で通り、ci.md の 2 つの行を合わせた。
- [x] **T183-02-02** [テスト] 環境変数の既定の検査が、設定の値のクラスを読めないときに失敗にする。Java の規則の検査が、コメントの中を違反と読まない（DC-183-13、14）。
  - 依存：なし
  - 範囲：`scripts/ci/env-defaults.mjs`、`scripts/ci/java-rules.mjs`、`scripts/ci/lib/strip-comments.mjs`（使うとき）、テスト
  - 結果：環境変数の既定の検査が、設定のクラスを 1 つも読めないとき、record でない・引数が空・プロパティが 0 件などのときを、ファイルの名前付きの理由で失敗にする。Java の規則の検査は、コメントを同じ長さの空白にしてから読み（`stripJavaComments` を `lib/strip-comments.mjs` に移した）、今のリポジトリは違反 0 件で通る。
- [x] **T183-02-03** [テスト] 使わない語の検査に、許す語の一覧を加える（DC-183-15）。
  - 依存：なし
  - 範囲：`scripts/ci/doc-terms.mjs`、`scripts/ci/doc-terms.json`、テスト
  - 結果：ng の項目に任意の `allow`（許す語の配列）を持てるようにし、一致の位置が許す語の中なら数えない。許す語が使わない語を含まないときは設定の誤りとして例外で失敗にする。今のリポジトリは違反 0 件で通り、ci.md の行を合わせた。
- [x] **T183-02-04** [テスト] テストの時間の待ちの検査の対象を広げ、文言の直書きの検査のコメントの除去を共通の関数にする（DC-183-16、17）。
  - 依存：なし
  - 範囲：`scripts/ci/time-waits.mjs`、`scripts/ci/hardcoded-text.mjs`、`scripts/ci/lib/strip-comments.mjs`、テスト
  - 結果：`time-waits` が E2E の実行の script、Playwright・Vitest・Vite の設定、画面のテストの補助も読む。対象を広げて見つかった `e2e/scripts/run.ts` の sleep（ロックの待ち時間が 0 で実際には待たない）は、理由付きの除外の一覧（`time-waits-exclusions.json`）に置いた。`hardcoded-text` は既に共通の `stripComments` を使っていたので実装は変えず、行番号の回帰テストを加えた。今のリポジトリで 0 件で通り、ci.md の行を合わせた。
- [x] **T183-02-05** [テスト] マイグレーションの検査の改名・削除の判定を、`develop` との分岐点までの履歴に絞る。Git の一覧を読む全ての検査が、対象が 0 件のときに失敗にする（DC-183-18、19）。
  - 依存：T183-01-01、T183-02-04（同じ検査のファイルを変える）
  - 範囲：`scripts/ci/migrations.mjs`、`scripts/ci/lib/git-files.mjs`、Git の一覧を読む検査、テスト
  - 結果：`migrations` が、`develop` と `main`（それぞれ `origin/` を先に見る）との merge-base までの履歴で判定し、どちらにも入っていない分岐の後のものの振り直しと中身の変更を通す。main にだけあるもの（hotfix）は検査の対象にする（片方の ref だけならある方、どちらもなければ全ての履歴で判定して警告）。共通の `exitIfNoFiles` を 15 の検査に置き、一時リポジトリのテストを加えた。CI（docs.yml）は fetch-depth: 0 で origin/develop があるので workflow は変えていない。
- [x] **T183-02-06** [テスト] 実行環境のバージョンの検査が新しい job のバージョンの直書きを見つける。閉じ括弧の探し方とコメントの除去が、正規表現のリテラルを扱う（DC-183-20、21）。
  - 依存：T183-02-04（同じコメントの除去の関数）
  - 範囲：`scripts/ci/runtime-versions.mjs`、`scripts/ci/lib/strip-comments.mjs`、閉じ括弧を探す関数、テスト
  - 結果：実行環境のバージョンの検査が、yml の java-version と node-version 系の入力の直書きを失敗にするようにした。closingParen が正規表現のリテラルを読み飛ばすようにした（stripComments は対応済みで、テストだけ加えた）。check-all の結果は変わらない。
- [x] **T183-02-07** [テスト] AC の照合が、`each` の表の中の関数の呼び出しを含むテスト名を読み、読めないテストを失敗にする（DC-183-22）。
  - 依存：T183-01-02
  - 範囲：`scripts/ci/ac-check.mjs`、テスト
  - 変更の前後で AC 一覧が変わらないか、変わる理由（今まで読めずに除かれていたテストが加わる）を結果に書く
  - 結果：テストの関数の読み方（lib/test-files）を、each の表を括弧の対応（closingParen）とテンプレートの表で読む形にし、読めない呼び出しを `ファイル:行：理由` で失敗にした。変更の前後で ac-check（テスト名 1825 件、AC 312 件、警告なし）と showcase-counts（1825 件）は同じで、今のリポジトリに読めない呼び出しはない。
- [x] **T183-02-08** 段階の最後のレビュー（profile「厚め」）を行い、指摘に対応する。
  - 依存：T183-02-01〜07
  - 結果（高）：閉じ括弧の探し方に正規表現を読まない指定を加えて Java（java-rules）で使い、TypeScript の正規表現の判定を直前の意味のある字で決める形にした。migrations は履歴の commit でなく、develop・main との分岐点の tree（分岐点が HEAD なら HEAD とその親）と今の index を比べる形にし、merge commit の後の改名で失敗しないことをテストで確認した。role-permissions は security の中の字下げ 8 以外の項目と項目のない `security:` を失敗にした。4 つの検査の今のリポジトリの結果は 0 件のまま。その後、基準（develop・main）を求められないときは、T183-02-05 の「全ての履歴で判定して警告」でなく、HEAD の 1 つ目の親を基準にして警告する形に変えた。
  - 結果：一般の観点を汎用で 2 回（高 0・1、必須 3・1）、重要な観点を高性能と汎用で（高 1・2、必須 1・0）レビューした。重複を除いて高 3 件、必須 5 件を修正した。
  - 結果（必須）：テストの関数の読み方（lib/test-files）が test.for と skipIf・runIf・sequential・fails・todo を読み、知らない修飾の付いた test・it を読めない呼び出しとして失敗にした。互換の関数 parseProperties・parseOperations を削除し、テストを readProperties・readOperations に替えた。docs/guides/ci.md の lib の行と、5 つの検査（migrations、compose-ports、role-permissions、env-defaults、time-waits）と ac-check の先頭のコメントを今の振る舞いに合わせた。ac-check は script の場所をルートにしていたので、他の検査と同じく今のディレクトリをルートにし、テストのファイルが 0 件なら失敗にして、0 件の検査のテストの一覧に加えた。ac-check の結果（AC 312、テスト名 1825）は変わらない。

### 段階 3：CI と package の書き方、ローカルのポートの一覧

- 前提：段階 2
- 完了条件：DC-183-23〜30 を満たし、CI と `check-all.sh` が通る。
- 参照：第 3 節の DS-183-3、DS-183-4

- [x] **T183-03-01** バックエンドの全体の検査のコマンドを文書と CI で 1 通りにし、統合テストを分ける数を `ci.yml` の 1 か所にし、2 つの画面のテストのコマンドをそろえる（DC-183-23〜25）。
  - 依存：なし
  - 範囲：`.github/workflows/ci.yml`、`frontend/store/package.json`、`frontend/admin/package.json`、`docs/guides/`
  - CI の統合テストのジョブは push の後に確認する（手元では統合テストを実行しない）
  - 結果：全体の検査を `./gradlew check` に決めた（`check` が checkLicense・テスト・compileToolsJava・OpenAPI の検証・checkBootJar・coverageReport を含み、`build` は `assemble` を加えるだけで、`checkLicense build` は重複）。ci.yml と docs/guides/ci.md・running-tests.md を合わせ、分ける数は matrix の `shard` の一覧だけにして総数は `strategy.job-total` から読み、店舗画面の `--passWithNoTests` を外した（外して `pnpm --filter ./frontend/store run test` が 317 件通った）。
  - 確認方法：DC-183-23 は文書と CI を読む（レビュー）。DC-183-24 は CI の統合テストのジョブ名が 1/2・2/2 で通ったこと（run 40023186252）。DC-183-25 は 2 つの `package.json` の `test` を読む（レビュー）。
- [x] **T183-03-02** [テスト] React のバージョンの指定をそろえ、Gradle のプラグインの一覧と Dependabot の対象を確認する検査を加える（DC-183-26〜28）。
  - 依存：なし
  - 範囲：3 つの `package.json`、`pnpm-lock.yaml`、`scripts/ci/` の直下（新しい検査。`check-all.sh` が実行する置き場所）、`scripts/ci/licenses/policy.json`、テスト、`docs/guides/ci.md`
  - 結果：React の指定を、他の依存と同じ `^` の範囲にそろえた（store の `react`・`react-dom` と shared の devDependencies の固定 `19.3.1` を `^19.3.1` に。admin はすでに `^19.3.1`、shared の peerDependencies `^19.3.0` は範囲のまま）。解決は 19.3.1 のままで、`pnpm-lock.yaml` の差分は specifier の 4 行だけ。検査 `gradle-plugins`（カタログの `[plugins]` と `plugins {}` の id を `policy.json` の `gradlePlugins` と過不足照合）と `dependabot-actions`（`action.yml` のディレクトリが Dependabot の `directories` にあるか）を加え、ci.md の表に行を加えた。
  - 確認方法：DC-183-26 は `runtime-versions` の React の指定の判定（`findReactSpecifierProblems`。`peerDependencies` は範囲で書くので対象外）のテストと、今のリポジトリの結果が 0 件であること。DC-183-27 は `gradle-plugins` のテスト（読めない書き方の失敗を含む）。DC-183-28 は `dependabot-actions` のテスト。
- [x] **T183-03-03** [テスト] ローカルのポートの一覧を 1 か所に置き、他の所との食い違いを見つける検査を作る（DC-183-29、30）。
  - 依存：なし
  - 範囲：一覧の置き場所（`scripts/dev/` か `docker/`。結果に書く）、`docker/compose.yaml`、`scripts/dev/slot.mjs`・`scripts/dev/lib/`、`scripts/dev/windows/`、`e2e/playwright.config.ts`、`scripts/ci/`、テスト、`docs/guides/setup.md`、`docs/guides/ci.md`
  - compose の検査（compose-ports）と重なる所は、1 つの検査にまとめるかを結果に書く
  - 確認方法：DC-183-29 は、ポートを持つのが `scripts/dev/ports.json` だけで、スロットの script（`lib/slot.mjs`）がそこから読むことをレビューで読み、`git grep -n "ports.json" -- scripts/dev` で読む所を確認する。DC-183-30 は、local-ports のテスト。
  - 結果：一覧を `scripts/dev/ports.json`（key・ポート・名前・group・スロットでずらすか）に置き、スロットの script（`lib/slot.mjs`）はここから読む。検査 `local-ports` が compose、Windows の予約の script、起動と E2E の文書、E2E の設定を一覧と照合する（今の食い違いは 0 件）。compose-ports は待ち受けるアドレスの検査として残し、local-ports がその読み取りを再利用する。
- [x] **T183-03-04** 段階の最後のレビュー（profile「厚め」）を行い、指摘に対応する。
  - 依存：T183-03-01〜03
  - 結果：一般の観点を汎用で 2 回（高 1・0、必須 0・1）、重要な観点を高性能と汎用で（高 1・0、必須 2・1）レビューした。重複を除いて高 1 件、必須 3 件を修正した。
  - 修正：`gradle-plugins` は、読めない書き方（`[plugins.名前]` の表、複数行の表、`plugins {}` の中の知らない行、`apply(plugin = "…")`）をファイル:行を出して失敗にし、文字列の形、同じ行の `id("…")`、`kotlin("…")` を読む。ci.yml の push の paths に `scripts/dev/ports.json` を加えた。`local-ports` は、バックエンドの yml、launch.json、画面の認証の設定、補助の script、ci.yml を一覧と照合し、realm の redirect URI に一覧の画面のポートが全てあることを確認する（テストコードの中のポートは対象外）。React の指定の書き方は `runtime-versions` が確認する。DC-183-23〜28 の確認方法を追記した。

### 段階 4：完了

- 前提：段階 1〜3
- 完了条件：spec の最後のレビューの指摘に対応し、spec をアーカイブに移し、pull request を作っている。
- 参照：[エージェントの作業フロー](../../rules/subagents.md) 第 4 節

- [x] **T183-04-01** spec の最後のレビュー（profile「厚め」。ファイルをまたぐ影響の観点を含める）と、pull request の前のレビューを行い、指摘に対応する。
  - 依存：T183-03-04
  - 結果（spec の最後のレビュー）：一般の観点を汎用で 2 回（高 1・0、必須 0・2、推奨 7・11）、重要な観点（ファイルをまたぐ影響を含む）を高性能と汎用で（高 1・0、必須 1・0、推奨 7・3）、文書の言い回しを汎用で 1 回（候補 30）行った。重複を除いて高 2 件、必須 3 件と、推奨のうち実害に近い 8 件を修正した（commit fbb4353c と、この commit）。残りの推奨は第 8 節に回した。
  - 修正の要約：コード（fbb4353c）は、コメント・文字列の中のテストを数えない、compose・ローカルのポートの読めない項目の失敗、runtime-versions の読み方、check-all.sh のテストの数の判定、テンプレートの入れ子の読み方、commit の前の hook の bash の指定。この commit は、0 件の失敗のテストの追加（gradle-plugins、dependabot-actions）、E2E の設定のポートの読み方、DC-183-10 のテスト、コメントと文書、言い回し。なお、T183-01-06 の結果の判定の種類 `none` は、この修正（fbb4353c）でテストの数を見る `tests` に替えた。
  - 結果（pull request の前のレビュー）：高性能（高 0、必須 2、推奨 5）と組み込みのコードレビュー（5 件）。必須 2 件と、コードレビューの 5 件、推奨 4 件を修正し、1 件（統合テストを分ける数の既定値）を第 8 節に回した。
- [ ] **T183-04-02** 後回しにする作業と起票の案（第 8 節）を最新にし、spec を `docs/specs/archive/1to10/` に移す。
  - 依存：T183-04-01
  - 範囲：`docs/specs/`、spec へのリンクを持つ文書

## 8. 後回しにする作業と起票の案

| 起票の案 | 扱う issue | 内容 |
|----------|------------|------|
| [#185](https://github.com/roger88106/ec-site/issues/185)（新しい追跡の issue。起票済み） | #20、#161、#169 | マイグレーションの互換性の規則と検査、文書の食い違いの検査、フロントのテストの書き方の検査。spec の確認（2026-10-24）で分けると決め、issue 担当のセッションが起票した |

最後のレビューで回した推奨：

| # | 項目 | 起票の案 |
|---|------|----------|
| 1 | isMain が 1 通りであることを守る検査がない | #185 に加える（検査の script の整理。新しい検査を加える spec の前に、共通の処理として行う） |
| 2 | Git を呼ぶテストの `GIT_` の環境変数を外す処理が 6 つのテストに重複する | #185 に加える（同上） |
| 3 | 除外の一覧のパスの求め方が 4 通りあり、related-specs が除外の誤りの文を `checkExclusions` と別に組み立てる | #185 に加える（同上） |
| 4 | 読めない行の出力の形が 3 通りある | #185 に加える（同上） |
| 5 | migrations が `emptyListReason` と同じ処理を別に持つ | #185 に加える（同上） |
| 6 | コメント・文字列・正規表現を読み飛ばす走査が closingParen、codeEnd、stripComments の 3 か所にある | #185 に加える（同上） |
| 7 | doc-terms の `allow` に理由と不要の検出がない | #185 に加える（同上） |
| 8 | dependabot-actions が知らない書き方の行を失敗にしない | #185 に加える（同上） |
| 9 | env-defaults、glossary-ids、related-specs が `listGitFiles` を使わず `git ls-files` を直接呼ぶ | #185 に加える（同上） |
| 10 | 時間の待ちの検査の除外がファイルの単位なので、`e2e/scripts/run.ts` に後から書いた待ちも通る。除外を行か関数の単位にする | #185 に加える（同上） |
| 11 | 統合テストを分ける数の既定（`backend/ecsite/build.gradle.kts` の `integrationTestShards` の `orElse(2)`）と、ci.yml の matrix の食い違いを見つける検査がない | #185 に加える（同上） |

- 新しい spec は、本 spec のマージの後に始める。本 spec の共通の関数（テストのファイルの探し方、許す語の一覧など）を使うため。
- 新しい spec の決定（マイグレーションの互換性の規則、規則の文書にない検査を先に規則に書くこと、テストの書き方の検査を spec 184 のマージの後に加えること）は、spec の確認で利用者が決めた（2026-10-24）。新しい spec は、その決定をそのまま書く。
- 作業の中で見つけたものは、この節に加える。
