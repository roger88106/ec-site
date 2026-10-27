# Spec 225：CI の検査の script の残りとマイグレーションの規則を整える

ステータス：確定

## 1. 背景と目的

spec 185 と spec 195 の最後のレビューで、CI の検査の script（`scripts/ci/`）の未対応の項目が挙がった。spec 185 の起票の案では、マイグレーションの規則の抜け（既存の列の既定値の削除）も挙がった。1→10 の改善の第 6 弾として、どちらも `scripts/ci/` の検査を変えるので、1 つの spec で扱う。

- #219：検査の script の残り（spec 185 第 8 節の推奨）と、許可のライセンスの一覧の一致（spec 195 第 8 節の 3）。
- #221：`ADD COLUMN … NOT NULL DEFAULT 0` の後の `ALTER COLUMN … DROP DEFAULT` は、既定値のない NOT NULL の列の追加と同じく、前のリリースのアプリの INSERT を失敗させる。今の規則（[バックエンドの書き方](../../rules/backend-coding.md) 第 8.1 節）と検査は、これを禁じていない。

追跡の issue は [#225](https://github.com/roger88106/ec-site/issues/225)、扱う issue は [#219](https://github.com/roger88106/ec-site/issues/219)、[#221](https://github.com/roger88106/ec-site/issues/221) である。作業ブランチは `225-ci-scripts`。本 spec は整理の spec で、規模は M である（第 6 節の 1）。

#221 の規則を加え、`DROP DEFAULT` を検査で失敗にすることは、起票のときに利用者が決めた（2026-10-27）。禁じる書き方の細部は第 6 節の 4 で決めた。

## 2. スコープ

| # | 目的 | 対象 | 優先度 | issue |
|---|------|------|--------|-------|
| DS-225-1 | 検査の script の共通の処理と対象の選び方が 1 か所にまとまり、テストで確認できる | D | P1 | #219 |
| DS-225-2 | 許可のライセンスの一覧の 2 か所が食い違わない | D | P1 | #219 |
| DS-225-3 | マイグレーションの規則と検査が、既存の列の既定値の削除を禁じる | D | P1 | #221 |

## 3. 目的と完了条件

- 1 つの DC に条件を 1 つ書く。出典の列は、spec 185 第 8 節の行の内容、spec 195 第 8 節の番号、issue である。
- 整理の spec の DC の確認方法は、[開発の進め方](../../rules/workflow.md) 第 1.2 節のとおり plan で決める。規模 M には plan がないので、tasks で決める。
- 新しい検査と強めた検査で今のコードと文書に違反が出るときは、同じタスクで違反を修正する（spec 183・185 と同じ扱い）。正しい書き方だけを、理由付きで除外の一覧に置く。
- 規則の文書に根拠のない検査は、先に規則の文書に書いてから加える（spec 183 の確認で利用者が決めた方針）。

### DS-225-1 検査の script の共通の処理と対象の選び方（D / P1）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-225-1** | 検査の script を走査して共通の関数の使用を確認する 4 つのテスト（`lib/` の `exclusions`、`unreadable-line`、`git-files`、`git-env` のテスト）の走査の部分が、`lib/` の 1 つの補助を使う（Git を実行して振る舞いを確認する部分は対象にしない） | 185：走査のテストの補助 |
| **DC-225-2** | DC-225-1 の補助は、守る関数ごとの「禁じる書き方」と「例外の script」の表を受け取り、違反した script を返す | 185：走査のテストの補助 |
| **DC-225-3** | `frontend-rules` が対象のファイルの種類（画面の本番のソース、画面の単体・結合テスト、テストの補助、E2E など）を判定する正規表現が、種類ごとに 1 か所にある | 185：`frontend-rules` の正規表現の重複 |
| **DC-225-4** | `frontend-rules` の、ファイルを規則のまとまりに振り分ける処理が関数で、テストが振り分けを確認する | 185：対象のファイルの選び方のテスト |
| **DC-225-5** | `main-entry` の検査の対象に、`frontend/shared/scripts/` の下の、テストでない `.mjs`・`.cjs` が入る | 185：`main-entry` の対象 |
| **DC-225-6** | DC-225-5 の後、今のコードの `main-entry` の違反が 0 件である（今は `frontend/shared/scripts/export-constraints.mjs` の 1 件） | 185：`main-entry` の対象 |
| **DC-225-7** | `listGitFiles` が追跡していないファイルを含めることと、その理由と、手元と CI の対象の違いが、[CI の検査](../../guides/ci.md) と `lib/git-files.mjs` の先頭のコメントに書かれている（第 6 節の 2） | 185：`listGitFiles` の扱い |
| **DC-225-8** | `frontend/admin/src/test/inputs.ts` の `fireEvent` の理由が、呼び出しごとに 1 か所（呼び出しの直前の行のコメント）だけにある | 185：`inputs.ts` の理由の重複 |

### DS-225-2 許可のライセンスの一覧の一致（D / P1）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-225-9** | [依存とライセンス](../../design/licenses.md) 第 2 節の表の「許可」の行のライセンスの一覧と、`scripts/ci/licenses/policy.json` の `allowed` に過不足があるときに、検査が失敗する | 195-3 |
| **DC-225-10** | DC-225-9 の検査が表の行を読めない（行がない、ライセンスの区切りが読めない）ときに、失敗する | 195-3 |

### DS-225-3 既存の列の既定値の削除（D / P1）

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-225-11** | [バックエンドの書き方](../../rules/backend-coding.md) 第 8.1 節の「1 つのリリースでしない変更」に、既存の列の既定値の削除（`DROP DEFAULT`、`SET DEFAULT NULL`）がある（第 6 節の 4） | #221 |
| **DC-225-12** | マイグレーションの検査が、新しいマイグレーションの既定値の削除（`ALTER COLUMN … DROP DEFAULT`、`ALTER COLUMN … SET DEFAULT NULL`）を失敗にする。理由付きで除外の一覧に置いたものは数えない | #221 |
| **DC-225-13** | [CI の検査](../../guides/ci.md) の `migrations` の行が、DC-225-12 の変更の種類を挙げている | #221 |

### 全体

| DC | 完了の条件 | 出典 |
|----|------------|------|
| **DC-225-14** | 本 spec の変更の後、`scripts/ci/check-all.sh` と CI が通る | #225 |

## 4. 守る制約

- **DN-225-1** 消費者・運営者に見える振る舞い、API の契約、データベースを変えない。
- **DN-225-2** 検査を弱めない。共通の処理にまとめる変更（DS-225-1）は、変更の前後で全ての検査の今のリポジトリの結果が同じであることを確認する。
- **DN-225-3** 新しく書くコメントと除外の理由に、spec・plan の番号を書かない（[コメントの書き方](../../rules/comments.md) 第 1 節）。
- **DN-225-4** 並行する spec と重なるファイルの扱いは、第 7 節の 1 のとおりにする。実装は #222（文書の整理）のマージの後に始める。
- **DN-225-5** 共有の文書（[CI の検査](../../guides/ci.md)、規則の文書）の衝突は、後でマージする pull request が `develop` を取り込んで解消する。
- **DN-225-6** 新しく書く文書とコメントで、バージョンの意味の一字の語（#187 の語）を使わず、「バージョン」と書く（#222 が「バージョン」にそろえるため）。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| #219 の「起きたら直すもの」（Java のテキストブロックを `closingParen` が読まない、`design-migrations` の表の別名の誤検出、`note-numbers` の字下げのコードブロック、正規表現かの判定の文脈、型の注釈のない引数の見逃し） | 今のコードに該当がない。#219 の完了の目安でも除いている。起きたときに修正する。第 8 節に残す |
| ライセンスの「条件付き」の行と `policy.json` の `conditional.licenses` の一致 | 表は `EPL-1.0・2.0`、`LGPL-2.1・3.0` のように略して書き、`policy.json` は `-only`・`-or-later` を分けて書くので、機械で照合するには表の書き方を変える必要がある。#219 は許可の一覧だけを挙げている（第 6 節の 3） |
| E2E とテストの書き方の検査（#218、#216、#205） | spec 226 が扱う。spec 226 は本 spec のマージの後に実装し、本 spec で整える共通の関数を使う（第 7 節の 2） |

## 6. 決めたこと

spec の確認で、利用者が第 6 節の 1〜4 を全て案 A に決めた（2026-10-27）。Open questions はない。

### 6.1 規模

| 案 | 内容 |
|----|------|
| 案 A（推奨。採用） | 規模 M（1 つのファイルの spec に tasks の節を含める） |
| 案 B | 規模 L（spec、plan、tasks） |

- 案 A の理由：spec 183・185 と同じ種類の作業（検査の script と規則の文書）で、どちらも M で進められた。API・データベース・AC を変えず、モジュールをまたがない（[開発の進め方](../../rules/workflow.md) 第 4.2 節の基準で M）。DC は 14 で、spec 185（32）より少ない。
- 案 B の理由：並行する spec との照合を plan で残せる。ただし、規模の基準に合わず、照合は第 7 節の 1 に書ける。

### 6.2 `listGitFiles` が追跡していないファイルを含めること（DC-225-7）

`listGitFiles` は、追跡しているファイルと、追跡していない無視されないファイル（`git ls-files -co --exclude-standard`）を返す。CI の作業ツリーには追跡していないファイルがないので、手元と CI で対象が違いうる。

| 案 | 内容 |
|----|------|
| 案 A（推奨。採用） | 今のまま、追跡していないファイルも含める。理由と、手元と CI の違い（手元にだけある commit しないファイルで手元だけ失敗しうること、commit し忘れたファイルを指す参照は手元で通り CI で失敗しうること）を、[CI の検査](../../guides/ci.md) と `lib/git-files.mjs` に書く。手元だけの失敗は、ファイルを `.gitignore` か `/.local/` に置いて避ける |
| 案 B | 追跡しているファイル（index にあるもの）だけにする（`git ls-files -c`） |

- 案 A の理由：検査は commit の前に実行する（skill `local-dev`）。新しく作ったファイルは、その時点では `git add` していないことが多い。案 B では新しいテストや文書が検査されずに手元で通り、CI で初めて失敗する。手元だけの失敗は、置き場所の規則（[AGENTS.md](../../../AGENTS.md) の Git の「入れないもの」）で避けられる。
- 案 B の理由：手元と CI の対象が、`git add` の後はそろう。ただし、`git add` を忘れると検査が漏れる。

### 6.3 許可のライセンスの一致の検査の置き場所（DC-225-9、10）

| 案 | 内容 |
|----|------|
| 案 A（推奨。採用） | `scripts/ci/` の直下に新しい検査 `license-policy` を作り、[CI の検査](../../guides/ci.md) の表に行を加える。`check-all.sh` が他の検査と同じく実行する |
| 案 B | `scripts/ci/licenses/` の下に置き、ライセンスの検査（`check-frontend.mjs`）と一緒に実行する |

- 案 A の理由：`policy.json` と文書だけを読み、画面の依存を必要としない。`licenses/` の下は画面の依存が必要な検査の置き場所で、`check-all.sh` は script のテストだけを実行する（[CI の検査](../../guides/ci.md) 第 3 節）。同じ `policy.json` を照合する `gradle-plugins` も直下にある。
- 案 B の理由：ライセンスの検査が 1 か所に集まる。ただし、画面の依存の install を待たないと照合されない。

### 6.4 既定値の削除として禁じる書き方（DC-225-11、12）

起票のときに利用者が決めた「`DROP DEFAULT` を検査で失敗にする」の細部である。

| 案 | 内容 |
|----|------|
| 案 A（推奨。採用） | `ALTER COLUMN … DROP DEFAULT` と、同じ結果になる `ALTER COLUMN … SET DEFAULT NULL` を、列が NOT NULL かに関わらず禁じる。変更の種類は「既存の列の既定値の削除」。正しい削除は、他の禁じる変更と同じく除外の一覧に理由を書く |
| 案 B | `DROP DEFAULT` だけを禁じる |

- 案 A の理由：`SET DEFAULT NULL` も既定値をなくし、NOT NULL の列では同じく前のリリースのアプリの INSERT が失敗する。検査は SQL の字面で探し、列が NOT NULL かは前のマイグレーションをたどらないと分からないので、NULL を許す列も対象にする。NULL を許す列でも、前のリリースのアプリが既定値に依存していれば、入る値が変わる。
- 案 B の理由：issue の文に合わせて最小にする。ただし、`SET DEFAULT NULL` の抜けが残る。

## 7. tasks

tasks は第 6 節の決定に基づく。

- `[P]` のタスクはない。同じ段階のタスクは、番号の順に 1 つずつ行う。理由：多くのタスクが共有の文書（[CI の検査](../../guides/ci.md)）と共通の関数を変えるため。
- `[テスト]` のタスクは、テストを書いてレッドを確認し、同じ実装役が続けて実装し、同じ commit に入れる（[開発の進め方](../../rules/workflow.md) 第 2.1 節の例外）。
- `scripts/ci/` の直下に検査を加えるタスクは、[CI の検査](../../guides/ci.md) の表に行を加える（`ci-docs` の検査が求める）。
- 各段階の最後のレビューは、profile「厚め」で行う（[エージェントの作業フロー](../../rules/subagents.md) 第 4 節）。重要な観点に「検査を弱めていないか」（DN-225-2）を、一般の観点に「コメントと除外の理由に spec の番号がないか」（DN-225-3）と「バージョンの意味の一字の語（#187 の語）を新しく書いていないか」（DN-225-6）を含める。
- 手元の環境（スロット）は使わない。確認は `scripts/ci/check-all.sh` と、変えた script のテストで行う。

### 7.1 並行する spec との照合

第 6 弾の 6 つの spec（222〜227）のうち、本 spec 以外の 5 つとの照合である。文書の段階（2026-10-27）での見込み。実装の前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）で、最新の `develop` と各 spec の tasks を照合し、差があれば本節を修正する。

| spec | 重なりうるファイル | 扱い |
|------|--------------------|------|
| 222（文書の整理） | `scripts/ci/` のコメント、`doc-terms.json`、`docs/guides/ci.md`、`docs/rules/` | 本 spec の実装は 222 のマージの後に始める（DN-225-4） |
| 223（バックエンドの検査） | `scripts/ci/` の検査（`role-permissions` など）、`docs/guides/ci.md` | 本 spec が変える検査（`frontend-rules`、`main-entry`、`migrations`、新しい `license-policy`、`lib/` の走査の補助）と 223 の検査が重ならなければ並行してよい。`ci.md` の表は DN-225-5 |
| 226（E2E とテストの書き方） | `scripts/ci/frontend-rules.mjs`、`lib/` の補助、`docs/guides/ci.md`、`docs/rules/test-writing.md` | 226 は本 spec のマージの後に実装する（#226 の本文）。本 spec が整える関数は第 7 節の 2 |
| 224、227 | なし（バックエンドのテストと店舗画面） | 並行してよい |

### 7.2 spec 226 が使う共通の関数

spec 226（E2E の書き方の検査など）が、本 spec のマージの後に使う形。名前は実装で変わりうるので、変えたときは T225-01-01〜03 の結果に書き、spec 226 の spec 担当のセッションに知らせる。

- `frontend-rules` のファイルの種類（T225-01-02）：本番のソース、画面のテスト、テストの補助、E2E の各種類を、名前付きの定数（または判定の関数）として 1 か所に置き、テストから import できるようにする。ファイルを規則のまとまりに振り分ける関数（例：`groupTargets(files)`）は、種類ごとのファイルの一覧を返す。E2E だけに当てる新しい規則は、振り分けた E2E の一覧に当てる形で加える。
- 走査のテストの補助（T225-01-01）：`lib/` の 1 つのファイルに、走査する script の一覧（`scripts/ci/` の直下と `lib/`、または `scripts/` の下全体）を返す関数と、「守る関数・禁じる書き方・例外の script」の表から違反を返す関数を置く。新しい共通の関数を加えたときは、表に行を追加する。
- `main-entry`（T225-01-03）：対象は `scripts/` と `frontend/shared/scripts/` の下。E2E の script（`e2e/scripts/`）は TypeScript なので対象にしない（今と同じ）。

### 段階 1：検査の script の共通の処理と対象の選び方

- 前提：spec の確定、#222 のマージ、実装の前の照合（第 7 節の 1 の照合を含む）
- 完了条件：DC-225-1〜8 を満たし、`check-all.sh` が通る。
- 参照：第 3 節の DS-225-1、第 6 節の 2、[spec 185](../archive/1to10/185-new-checks/spec.md) 第 8 節

- [x] **T225-01-01** [テスト] 走査のテストの補助を `lib/` にまとめ、4 つの走査のテストを表の形に書き換える（DC-225-1、2）。
  - 依存：なし
  - 範囲：`scripts/ci/lib/`（新しい補助とそのテスト）、`lib/exclusions.test.mjs`、`lib/unreadable-line.test.mjs`、`lib/git-files.test.mjs`、`lib/git-env.test.mjs`、`docs/guides/ci.md` の `lib/` の一覧
  - 確認方法：補助のテストで、表の禁じる書き方を含む script を違反として返し、例外の script を返さないこと。4 つのテストで、守る関数を直接使わない形を意図的に 1 つ書くと失敗すること（確認した後に戻す）
  - 結果：補助は `lib/scan-rules.mjs`。走査する script の一覧は `listScanScripts(scope)`（範囲は `ci`、`ci+lib`、`all`）、違反は `findRuleViolations(scripts, rules)`（表の行は `{ guard, forbidden, target, exceptions }`）。`git-env` の例外は、ファイルの名前の一致から 2 つのパスの一致に限定した
- [x] **T225-01-02** [テスト] `frontend-rules` のファイルの種類の正規表現を 1 か所にまとめ、規則のまとまりへの振り分けを関数にしてテストで確認する（DC-225-3、4）。
  - 依存：なし
  - 範囲：`scripts/ci/frontend-rules.mjs`、`scripts/ci/frontend-rules.test.mjs`
  - 確認方法：振り分けのテストが、各種類の代表のパス（店舗画面・管理画面・共有の本番のソースとテスト、`src/test/` の補助、E2E）を期待するまとまりに入れること。変更の前後で `frontend-rules` の今のリポジトリの結果が同じであること（DN-225-2）
  - 結果：種類の正規表現は `SOURCE`、`FRONTEND_TEST`、`FRONTEND_TEST_SUPPORT`、`E2E_FILE`（`TEST` と `TEST_FILE` は削除）。振り分けは `groupTargets(files)` で、`{ sources, frontendTests, testSupport, e2e, tests, testsWithSupport }` を返す。E2E だけの規則は `e2e` に当てる。変更の前後で結果は同じ
- [x] **T225-01-03** [テスト] `main-entry` の対象を `frontend/shared/scripts/` に広げ、`export-constraints.mjs` の `process.argv[1]` との比較を `isMain` の呼び出しに置き換える（DC-225-5、6）。
  - 依存：なし
  - 範囲：`scripts/ci/main-entry.mjs`、テスト、`frontend/shared/scripts/export-constraints.mjs`、`docs/guides/ci.md` の `main-entry` の行
  - `export-constraints.mjs` は、相対パスで `scripts/ci/lib/is-main.mjs` を import する。管理画面と店舗画面の `generate:api` が動くことを確認する（API の型の生成は CI のフロントの job が行う）
  - 確認方法：`node scripts/ci/main-entry.mjs` が 0 件で通ること。共有のパッケージのテスト（`export-constraints.test.mjs` を含む）が通ること
- [x] **T225-01-04** `listGitFiles` の扱い（第 6 節の 2）を、文書とコメントに書く（DC-225-7）。
  - 依存：T225-01-01（同じ `lib/git-files.test.mjs` を変える）
  - 範囲：`scripts/ci/lib/git-files.mjs`（先頭のコメント）、`lib/git-files.test.mjs`（テストの名前）、`docs/guides/ci.md`
  - 振る舞いは変えない（第 6 節の 2）。追跡していない無視されないファイルを含めるテストが今あるので、テストの名前に理由を合わせる
  - 確認方法：`ci.md` と `git-files.mjs` に、理由と手元・CI の違いがあること（読んで確認する）。`lib/git-files.test.mjs` が通ること
- [x] **T225-01-05** `inputs.ts` の `fireEvent` の理由を、呼び出しの直前の行のコメントだけにする（DC-225-8）。
  - 依存：なし
  - 範囲：`frontend/admin/src/test/inputs.ts`
  - 関数の JSDoc から理由の文を削除し、何をするかだけを残す（`frontend-rules` の `fireEvent` の検査は、直前の行のコメントを求める）
  - 確認方法：`node scripts/ci/frontend-rules.mjs` が通ること
- [x] **T225-01-06** 段階の最後のレビュー（profile「厚め」）を行い、指摘に対応する。
  - 依存：T225-01-01〜05
  - 結果：重要な観点（高性能と汎用）と一般の観点（汎用を 2 回）で、高 0、必須 1（2 つのレビューで重複）。必須は、`lib/git-files.test.mjs` の `exitIfNoFiles` を呼ぶ検査の一覧が検査ごとに独自に書いた走査のままだったもので、`listScanScripts` を使う形に修正した（一覧は前後で同じ 23 件）。検査を弱めた箇所はなかった

### 段階 2：ライセンスの一覧の一致と、既定値の削除の規則

- 前提：段階 1
- 完了条件：DC-225-9〜13 を満たし、`check-all.sh` が通る。
- 参照：第 3 節の DS-225-2・3、第 6 節の 3・4、[spec 195](../archive/1to10/195-deps-major/spec.md) 第 8 節

- [x] **T225-02-01** [テスト] 許可のライセンスの一覧の一致の検査 `license-policy` を作る（DC-225-9、10）。
  - 依存：なし
  - 範囲：`scripts/ci/license-policy.mjs`（新しい検査）とテスト、`docs/guides/ci.md` の表、`docs/design/licenses.md`（第 2 節の確認方法の記述）
  - 表の行は「許可」の区分の行の 2 列目を読み、先に「。他に、」から後ろの文を除いてから `、` で分ける。読めないときは、共通の `describeUnreadableLine` で失敗にする
  - 確認方法：今のリポジトリで 14 と 14 が一致して通ること。テストで、片方にだけあるライセンス、行がない、区切りが読めないときに失敗すること
- [x] **T225-02-02** 既存の列の既定値の削除を、バックエンドの書き方 第 8.1 節の規則に加える（DC-225-11）。
  - 依存：なし
  - 範囲：`docs/rules/backend-coding.md`
  - 理由（前のリリースのアプリの INSERT が失敗する。NULL を許す列でも入る値が変わる）を書く。節の番号は変えない
- [x] **T225-02-03** [テスト] マイグレーションの検査が、新しいマイグレーションの既定値の削除を失敗にする（DC-225-12、13）。
  - 依存：T225-02-02
  - 範囲：`scripts/ci/migrations.mjs`（変更の種類と `classifyAction`）、`scripts/ci/migrations.test.mjs`、`docs/guides/ci.md` の `migrations` の行
  - テストに、`ALTER COLUMN c DROP DEFAULT`、`ALTER c DROP DEFAULT`（`COLUMN` の省略）、`ALTER COLUMN c SET DEFAULT NULL`、1 つの `ALTER TABLE` に複数の変更を並べた形、除外の一覧にあるものを含める。`SET DEFAULT 0` のような既定値の変更は失敗にしない
  - 確認方法：今のリポジトリ（新しいマイグレーションなし）で通ること
- [x] **T225-02-04** 段階の最後のレビュー（profile「厚め」）を行い、指摘に対応する。
  - 依存：T225-02-01〜03
  - 結果：重要な観点（高性能と汎用）と一般の観点（汎用を 2 回）で、高 3、必須 0。`SET DEFAULT CAST(NULL AS 型)` の検出漏れと、「許可」の行が複数あるときに 2 つ目以降の行を照合しないことは修正した。`ALTER COLUMN … DROP IDENTITY`（と `DROP EXPRESSION`）は、第 6 節の 4 で決めた範囲の外で、本 spec の変更で弱めたものでもないので、第 8 節に残した

### 段階 3：完了

- 前提：段階 1、2
- 完了条件：spec の最後のレビューの指摘に対応し、spec をアーカイブに移し、`check-all.sh` と CI が通る（DC-225-14）。
- 参照：[エージェントの作業フロー](../../rules/subagents.md) 第 4 節

- [ ] **T225-03-01** spec の最後のレビュー（profile「厚め」。ファイルをまたぐ影響の観点を含める）を行い、指摘に対応する。
  - 依存：T225-02-04
- [ ] **T225-03-02** pull request の前のレビュー（高性能の区分のモデルのレビューと、組み込みのコードレビュー `/code-review`。[AGENTS.md](../../../AGENTS.md) のモデルの区分）を行い、指摘に対応する。
  - 依存：T225-03-01
- [ ] **T225-03-03** 後回しにする作業と起票の案（第 8 節）を最新にし、spec を `docs/specs/archive/1to10/` に移す。
  - 依存：T225-03-02
  - 範囲：`docs/specs/`、spec へのリンクを持つ文書、移した spec 自身の相対リンク

## 8. 後回しにする作業と起票の案

作業の中で見つけたものは、この節に加える。

| 項目 | 内容 |
|------|------|
| #219 の「起きたら直すもの」 | Java のテキストブロックを `closingParen` が読まない、`design-migrations` の表の別名の誤検出、`note-numbers` の字下げのコードブロックと引用の中の囲み、正規表現かの判定が直前 200 字だけを見る、要素かの判定が型の注釈のない引数を見逃す（内容は [spec 185](../archive/1to10/185-new-checks/spec.md) 第 8 節）。どれも今のコードに該当がない。#219 を閉じるときに、新しい issue にするか、記録だけにするかを起票の案で決める |
| ライセンスの「条件付き」の一覧の一致 | 第 5 節。表の書き方を `policy.json` と同じ名前にそろえれば、機械で照合できる |
| IDENTITY と生成列の解除 | `ALTER COLUMN … DROP IDENTITY` は IDENTITY の列の既定値をなくす。列は NOT NULL のまま残るので、その列を指定しない前のリリースのアプリの INSERT が失敗する。`DROP EXPRESSION` も、NOT NULL の生成列では INSERT が失敗する可能性がある。第 6 節の 4 で決めた範囲（`DROP DEFAULT`、`SET DEFAULT NULL`）の外なので、規則と検査に加えるかを起票の案で決める（段階 2 のレビューで挙がった） |
