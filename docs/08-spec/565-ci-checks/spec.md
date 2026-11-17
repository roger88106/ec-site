# Spec 565：workflow の action の固定と、追跡しているファイルの除外を CI で検査する

種類：整理　規模：M　ステータス：確定

## 背景と目的

workflow と複合の action の `uses` は、#419 で commit の SHA に固定した。しかし、新しい workflow にタグを指定しても CI は通るので、固定が外れても検出できない。また、`.gitignore` は許可リストの形（全てを除外し、Git に入れるものだけを `!` で戻す。spec 248 で変えた）なので、`.gitignore` の変更で追跡しているファイルが除外の対象になっても、検出できない。この 2 つを CI で検査する。

今の状態は、全体のブランチ（`agent/544-cleanup-before-release`）で確認した。workflow と複合の action の `uses` は、ローカルの action を除き、全て 40 桁の SHA に固定済みである。除外の対象になった追跡しているファイルは、今はない。

消費者・運営者に見える振る舞いは変えない。追跡の issue は #565。扱う issue は #427（固定していない action の指定の検出）と #255（除外された追跡しているファイルの検査）で、どちらも全体の issue #544 の一部である。

## スコープ

| 含める | 含めない |
|--------|----------|
| workflow と複合の action の `uses` の固定を確認する検査の追加 | `ci.yml` の `paths` の変更（#255 の 2 つ目の箇条） |
| 追跡しているファイルがリポジトリの `.gitignore` で除外されていないかを確認する検査の追加 | SHA の後のバージョンのコメント（`# v7.0.1`）の照合 |
| commit の前の hook が実行する検査を選ぶ表と、CI の検査の script の文書の一覧への行の追加 | `docker://` の指定の固定の形（digest）の許可 |
| | 手元の除外の設定（`.git/info/exclude`、全体の `core.excludesFile`）の検査 |

決定：

- 検査は 2 つの script にし、`scripts/ci/config/` の直下に置く。`check-all.sh` が引数なしで実行する。名前は次の表。

  | 検査 | ファイル |
  |------|----------|
  | action の固定 | `scripts/ci/config/action-pins.mjs`（テストは `action-pins.test.mjs`） |
  | 除外された追跡しているファイル | `scripts/ci/config/ignored-tracked.mjs`（テストは `ignored-tracked.test.mjs`） |

- `ci.yml` の `paths` から `!.gitignore` を外さず、`.github/workflows/` を変えない。理由：文書の workflow（`.github/workflows/docs.yml`）は `paths` の指定がなく、全ての push と pull request で `check-all.sh` を実行する。そのため、新しい検査は `.gitignore` だけの変更でも実行される。
- 判定の区分は、どちらも誤りの防止とする。誤って書いたものを拒否し、意図して作った回避は対象にしない。
- action の固定は、許可する形の一覧で判定する。許可する形は次の 2 つだけで、それ以外は全て失敗にする。失敗にする例は、タグ、ブランチ、短い SHA、大文字を含む 16 進、`docker://`、`@` のない指定、文字列でない値である。

  | 形 | 例 |
  |----|----|
  | ローカルの action（`./` で始まる） | `./.github/actions/setup-frontend` |
  | `<owner>/<repo>` と任意のパスの後に、`@` と 40 桁の小文字の 16 進 | `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1` |

- action の固定の対象は、Git の対象のファイル（追跡しているファイルと、除外されない新しいファイル）のうち、`.github/workflows/` 直下の `*.yml`・`*.yaml` と、`.github/actions/` の下の `action.yml`・`action.yaml`。読む場所は、workflow の各 job の `steps` の `uses`、job の `uses`（再利用の workflow）、複合の action の `runs.steps` の `uses`。YAML として読み、`run` の中の文字列やコメントは見ない。読めない YAML は失敗にする。対象のファイルが 0 件のときも失敗にする。
- 除外の判定は、リポジトリの `.gitignore`（各フォルダーのものを含む）だけで行い、`.git/info/exclude` と全体の `core.excludesFile` は読まない。理由：手元の除外の設定で、手元でだけ失敗することを避け、手元と CI で同じ結果にするため。判定のコマンドの形は `git ls-files -z -i -c --exclude-per-directory=.gitignore` である。
- 除外の判定では、大文字と小文字を区別する（Git の設定 `core.ignorecase` を false にして実行する）。理由：Windows の clone は区別しない設定になり、Linux の CI と結果が変わるため。
- 失敗の出力は、action の固定ではファイル、場所（job と step）、値と、固定の形への修正の方法を示す。除外された追跡しているファイルでは、ファイルの一覧と、一致した行を調べるコマンド（`git check-ignore -v --no-index <ファイル>`）を示す。
- commit の前の hook は、`.github/` の下のファイルをステージしたときに `action-pins` を、常に `ignored-tracked` を実行する。理由：`git add -f` で除外のファイルを加えると、`.gitignore` を変えずに検査が失敗する状態になるため。hook では、除外の検査も、ステージした `.gitignore` とファイルで判定する。
- 文書は、CI の検査の script の文書（`docs/06-手順書/ci-checks.md`）の「検査の一覧」の表に 2 行を加える。`ci-docs.mjs` が、表と検査の 1 対 1 を確認する。同じ文書の「commit の前の hook」の表で、常に実行する検査の列挙に `ignored-tracked` を加える。
- 検出すべき例と検出してはいけない例を、次の 4 つの表に置く（[文書の構成](../../05-開発規則/doc-structure.md)の「形を判定する検査」）。

action の固定（`action-pins`）・検出すべき例：

| 番号 | 例 | 失敗の理由 |
|------|----|------------|
| 1 | `actions/checkout@v4` | タグ |
| 2 | `actions/checkout@main` | ブランチ |
| 3 | `actions/checkout@3d3c42e` | 短い SHA |
| 4 | 大文字の 16 進を含む 40 桁の SHA | 小文字でない |
| 5 | 39 桁か 41 桁の 16 進 | 40 桁でない |
| 6 | `actions/checkout`（`@` がない） | 固定がない |
| 7 | `checkout@<40 桁>`（owner がない） | 形が違う |
| 8 | `github/codeql-action/init@v3` | パスの付いた指定のタグ |
| 9 | job の `uses: org/repo/.github/workflows/a.yml@v1` | 再利用の workflow のタグ |
| 10 | 複合の action の `runs.steps` にあるタグの指定 | 複合の action も対象 |
| 11 | `docker://alpine:3.20`、`docker://alpine@sha256:<64 桁>` | 許可する形にない |
| 12 | `../other/action`、`.github/actions/x`（`./` で始まらない） | 許可する形にない |
| 13 | `uses` の値が数値、空、`null` | 文字列でない、空 |
| 14 | 引用符の中で SHA の後に空白か文字が続く | 40 桁で終わらない |
| 15 | YAML のアンカーとエイリアスで入れたタグの指定 | 展開後の値で判定する |
| 16 | 拡張子 `.yaml` の workflow、`action.yaml` の中のタグ | 対象のファイル |
| 17 | 読めない YAML | 判定できない |
| 18 | 対象のファイルが 0 件 | 検査の対象がないまま通るのを防ぐ |
| 19 | `actions/checkout@${{ matrix.ref }}`（step と job の `uses` の式） | 許可する形にない |
| 20 | `.github/actions/a/b/action.yml`（2 段の下のフォルダー）の中のタグの指定 | `.github/actions/` の下の全ての深さが対象 |

action の固定（`action-pins`）・検出してはいけない例：

| 番号 | 例 | 理由 |
|------|----|------|
| 1 | `actions/checkout@<40 桁の小文字> # v7.0.1` | 許可する形。コメントは見ない |
| 2 | `./.github/actions/setup-frontend` | ローカルの action |
| 3 | `github/codeql-action/init@<40 桁の小文字>` | パスの付いた固定 |
| 4 | job の `uses` の再利用の workflow を 40 桁で固定 | 許可する形 |
| 5 | `uses` のない step（`run` だけ） | 対象がない |
| 6 | `run` の文字列やコメントの中の `uses: actions/checkout@v4` | YAML の値でない |
| 7 | `with` の入力の値 `ref: v1` | `uses` でない |
| 8 | `.github/` の外の YAML、`.github/dependabot.yml` | 対象のファイルでない |
| 9 | フロースタイル（`- { uses: a/b@<40 桁> }`） | 展開後の値で判定する |
| 10 | job の `uses: ./.github/workflows/build.yml`（ローカルの再利用の workflow） | `./` で始まる |
| 11 | owner と repo に大文字、`-`、`_`、`.` を含む固定（`Azure/login@<40 桁の小文字>`） | 小文字に限るのは SHA だけ |
| 12 | 引用符で囲んだ許可する形（`"actions/checkout@<40 桁の小文字>"`、`'./.github/actions/setup-frontend'`） | YAML の値は同じ |
| 13 | `.github/workflows/` のサブフォルダーの `*.yml` | 対象は直下だけ（GitHub Actions もサブフォルダーを読まない） |

除外された追跡しているファイル（`ignored-tracked`）・検出すべき例：

| 番号 | 例 | 失敗の理由 |
|------|----|------------|
| 1 | 追跡しているファイルを除外する行を、ルートの `.gitignore` に加えた | 除外の対象 |
| 2 | 子のフォルダーの `.gitignore` が、追跡しているファイルを除外する | 各フォルダーの `.gitignore` も読む |
| 3 | 許可の行（`!/LICENSE`）を消した | 戻していたファイルが除外になる |
| 4 | 追跡しているディレクトリを丸ごと除外する（`/docs/`） | ディレクトリの下の全て |
| 5 | `git add -f` で、除外のファイルを加えた | 追跡しているファイルが除外の対象 |
| 6 | 名前に空白・日本語を含む追跡しているファイルが除外の対象 | 名前の形によらない |
| 7 | 親のディレクトリを除外した後に、その下のファイルを `!` で戻す（`/backend/` の後に `!/backend/ecsite/build.gradle.kts`） | 親のディレクトリが除外されていると戻せない |
| 8 | 許可の行を除外の行より前に移した（`!/LICENSE` を `/*` より前に置く） | 最後に一致する行が除外する |
| 9 | `/` のない行が深いフォルダーの追跡しているファイルに一致する（`bin/` が `scripts/bin/` に） | `/` のない行はどの階層にも一致する |
| 10 | 大文字と小文字だけが違う許可の行（`!/readme.md` と、追跡している `README.md`） | 大文字と小文字を区別する |

除外された追跡しているファイル（`ignored-tracked`）・検出してはいけない例：

| 番号 | 例 | 理由 |
|------|----|------|
| 1 | 追跡していない除外のファイル（`/.local/` の下、ビルドの出力） | 追跡していない |
| 2 | `.git/info/exclude` か全体の `core.excludesFile` だけが除外する追跡しているファイル | 手元の設定は読まない |
| 3 | 除外の後に `!` で戻した追跡しているファイル（親のディレクトリは除外されていない） | 最後に一致する行が `!` で戻している |
| 4 | 追跡していない、除外されないファイル | 追跡していない |
| 5 | `/*` で除外し、`!/docs/` で戻したディレクトリの下の追跡しているファイル | 許可リストの書き方 |
| 6 | 許可の行の末尾に空白がある（`!/LICENSE` の後に空白） | 末尾の空白は無視される |
| 7 | `git rm --cached` で追跡をやめた除外のファイル | 追跡していない |

## 目的と完了条件

### DS-565-1 workflow と複合の action の `uses` の固定が外れたことを、CI で検出できるようにする（D / P1）

- **DC-565-1** 「検出すべき例」（action の固定）の全ての形で、`action-pins` が失敗する。
- **DC-565-2** 「検出してはいけない例」（action の固定）の全ての形と、今の workflow と複合の action で、`action-pins` が通る。
- **DC-565-3** `action-pins` の失敗の出力に、ファイル、場所、値、固定の形への修正の方法がある。

### DS-565-2 追跡しているファイルが `.gitignore` で除外されたことを、CI で検出できるようにする（D / P1）

- **DC-565-4** 「検出すべき例」（除外された追跡しているファイル）の全ての形で、`ignored-tracked` が失敗する。
- **DC-565-5** 「検出してはいけない例」（除外された追跡しているファイル）の全ての形と、今のリポジトリで、`ignored-tracked` が通る。
- **DC-565-6** `ignored-tracked` の失敗の出力に、ファイルの一覧と、一致した行を調べるコマンドがある。

### DS-565-3 2 つの検査が、CI と commit の前の hook で実行され、文書の一覧に載る（D / P1）

- **DC-565-7** 2 つの検査が `scripts/ci/config/` の直下にあり、`check-all.sh` が実行する。
- **DC-565-8** commit の前の hook が、`.github/` の下のファイルをステージしたときに `action-pins` を、常に `ignored-tracked` を実行する。
- **DC-565-9** `docs/06-手順書/ci-checks.md` の「検査の一覧」に 2 行があり、`ci-docs.mjs` が通る。
- **DC-565-10** `scripts/ci/check-all.sh` が通る。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 検査 | `scripts/ci/config/action-pins.mjs`、`scripts/ci/config/action-pins.test.mjs`、`scripts/ci/config/ignored-tracked.mjs`、`scripts/ci/config/ignored-tracked.test.mjs` |
| commit の前の hook | `scripts/dev/hooks/pre-commit-select.mjs` |
| 文書 | `docs/06-手順書/ci-checks.md`、`docs/08-spec/565-ci-checks/spec.md` |

## 非機能要件

- **DN-565-1** 消費者・運営者に見える振る舞いを変えない。`.github/workflows/` と `.github/actions/` を変えない。
- **DN-565-2** 既存の検査の判定を変えない。
- **DN-565-3** アーカイブした spec を変えない。

## 依存

| 対象 | 関係 |
|------|------|
| #563（既存の検査の script の漏れと誤検出の修正） | `pre-commit-select.mjs` と `ci-checks.md` を変える可能性がある。自分の行だけを加え、マージの前に全体のブランチを取り込む |
| #566（文書の検査の script の漏れの修正） | #563 と同じ |

## Non-goals

| 項目 | 理由 |
|------|------|
| `ci.yml` の `paths` から `!.gitignore` を外す（#255 の 2 つ目の箇条） | 決定のとおり、文書の workflow が検査を実行するため。`ci.yml` を変えると、結果に関わらない job も実行される |
| SHA の後に書くバージョンのコメントの照合 | 実行する action は SHA で決まり、コメントは読む人の目印で実行に影響しないため |
| `docker://` の digest の指定を許す | 今は使っていない。使うときに、許可する形に加える |
| Docker の action の `runs.image` の固定 | `uses` と別の項目で、今は Docker の action もない。作るときに対象に加える |
| 手元の除外の設定の検査 | 手元の設定はリポジトリに入らず、CI の結果に関わらないため |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T565-01-01** 例の 4 つの表をレビュー役（spec の文脈を持たない）に読ませ、例を追加する（DC-565-1、DC-565-2、DC-565-4、DC-565-5）。
  - 依存：なし
  - 範囲：`docs/08-spec/565-ci-checks/spec.md`
  - 確認方法：追加した例が表にある
- [x] **T565-01-02** [テスト] `action-pins` のテストを書き、レッドを確認する（DC-565-1〜3）。※ CI の検査の script のテストは、続く実装のタスクと同じ commit に入れる（[開発の進め方](../../05-開発規則/workflow.md)の「循環」）。
  - 依存：T565-01-01
  - 範囲：`scripts/ci/config/action-pins.test.mjs`
  - 確認方法：`node --test` で失敗する
- [x] **T565-01-03** T565-01-02 のテストが通る `action-pins` を書く。
  - 依存：T565-01-02
  - 範囲：`scripts/ci/config/action-pins.mjs`
  - 確認方法：テストが通る。今の workflow で通る
- [x] **T565-01-04** [テスト] `ignored-tracked` のテストを書き、レッドを確認する（DC-565-4〜6）。
  - 依存：T565-01-01
  - 範囲：`scripts/ci/config/ignored-tracked.test.mjs`
  - 確認方法：`node --test` で失敗する
- [x] **T565-01-05** T565-01-04 のテストが通る `ignored-tracked` を書く。
  - 依存：T565-01-04
  - 範囲：`scripts/ci/config/ignored-tracked.mjs`
  - 確認方法：テストが通る。今のリポジトリで通る
- [x] **T565-01-06** `pre-commit-select.mjs` と `ci-checks.md` に行を加える（DC-565-7〜9）。
  - 依存：T565-01-03、T565-01-05
  - 範囲：`scripts/dev/hooks/pre-commit-select.mjs`、`docs/06-手順書/ci-checks.md`
  - 確認方法：`node --test scripts/dev/hooks/pre-commit-select.test.mjs`、`node scripts/ci/docs/ci-docs.mjs`
- [x] **T565-01-07** 壊しての確認と全体の検査（DC-565-1、DC-565-4、DC-565-10）。
  - 依存：T565-01-06
  - 範囲：なし（確認だけ）
  - 確認方法：今の workflow の 1 か所をタグにして `action-pins` が失敗し、`.gitignore` に追跡しているファイルを除外する行を加えて `ignored-tracked` が失敗することを確認し、元に戻す。`scripts/ci/check-all.sh` が通る
- [ ] **T565-01-08** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T565-01-07
- [ ] **T565-01-09** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T565-01-08
- [ ] **T565-01-10** spec をアーカイブに移す。
  - 依存：T565-01-09
