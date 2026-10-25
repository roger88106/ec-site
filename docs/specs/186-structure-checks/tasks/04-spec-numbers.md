# 段階 4：テストのコメントの spec の番号

- 前提：段階 1〜3
- 完了条件：DC-186-13、14 を満たす。`node scripts/ci/spec-numbers.mjs` が 0 件。`./gradlew check`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 4 節、[plan](../plan.md) 第 5 節、[spec](../spec.md) の DS-186-4 と DC-186-13、14、[コメントの書き方](../../../rules/comments.md) 第 1 節

## タスク

順序は [plan](../plan.md) 第 4 節のとおり、書き換え（T186-04-02〜04）を先にし、検査を広げる T186-04-05 を最後にする。T186-04-02〜04 は、それぞれ別のファイルだけを変えるので `[P]` である。

- [x] **T186-04-01** [テスト] Java のコメントだけを残す関数 `keepJavaComments` を `scripts/ci/lib/strip-comments.mjs` に追加し、単体テストを書く。テストは、Javadoc、ブロックのコメント、行のコメントを残す例と、文字列とテキストブロックの中の `//`・`/*` を残さない例と、行番号が変わらない例を含め、先に失敗することを確認する。この関数で、テストのコメントの番号の件数を数え、T186-04-02〜04 の分け方を確認する（[plan](../plan.md) 第 4 節。DC-186-13）。
  - 依存：なし
  - 範囲：`scripts/ci/lib/strip-comments.mjs`、`scripts/ci/lib/strip-comments.test.mjs`
- [x] **T186-04-02** [P] モジュールの外のテスト（`test/` と `it/` の `architecture/`、`modulith/`、`contract/`、`config/`、`common/`、`observability/`、直下）と、`fix/` の全体のコメントの番号を、言葉に書き換える（DC-186-14）。
  - 依存：T186-04-01
  - 範囲：上に挙げたディレクトリ
- [x] **T186-04-03** [P] 商品カタログのモジュール（tax、catalog、media、inventory、storefront、region）のテストのコメントの番号を、言葉に書き換える（DC-186-14）。
  - 依存：T186-04-01
  - 範囲：`test/module/` と `it/module/` の、tax、catalog、media、inventory、storefront、region
- [x] **T186-04-04** [P] 注文のモジュール（cart、order、member、payment、shipping、notification、reporting）のテストのコメントの番号を、言葉に書き換える（DC-186-14）。
  - 依存：T186-04-01
  - 範囲：`test/module/` と `it/module/` の、cart、order、member、payment、shipping、notification、reporting
- [x] **T186-04-05** [テスト] `scripts/ci/spec-numbers.test.mjs` に、テストの Javadoc と行のコメントの番号を検出する例と、`@DisplayName` の番号を検出しない例を追加し、失敗することを確認する。`spec-numbers.mjs` の対象にバックエンドのテストのコメントを加え（T186-04-01 の `keepJavaComments` を使う）、テストが通り、検査が 0 件になる。0 件にならなければ、残った番号を同じタスクで書き換える（DC-186-13）。
  - 依存：T186-04-02〜04
  - 範囲：`scripts/ci/spec-numbers.mjs`、`scripts/ci/spec-numbers.test.mjs`、残った番号のあるテスト
- [x] **T186-04-06** [コメントの書き方](../../../rules/comments.md) 第 1 節の対象に、テストのコメントを加える（DC-186-14）。
  - 依存：T186-04-05
  - 範囲：`docs/rules/comments.md`
- [x] **T186-04-07** 段階のレビューと全体の検査：[plan](../plan.md) 第 7 節の段階 4 の観点とレビュー役で行う。`./gradlew check`、`scripts/ci/check-all.sh` を実行する。
  - 依存：T186-04-01〜06

## 段階のレビューの結果

profile「厚め」で、汎用のレビュー役 1 つが、書き換えたコメントと検査を読んだ（高 0、必須 1、推奨 5）。結果は `/.local/review/202610262000-186-stage4/` にある。

- 書き換えた件数：テストのコメントの番号 357 件（モジュールの外と `fix/` が 78、商品カタログのモジュールが 102、注文のモジュールが 177）。plan の概算（約 150 行）より多かったが、タスクの分け方は変えなかった。コメント以外の行の変更は、`AggregateGuardsTest` の `@DisplayName` の語（「版」を「バージョン」）だけである。
- 修正した：店舗の鮮度のテストの説明が、非機能要件（1 分以内の反映）と食い違っていた（必須）。番号を消した後に参照先が分からなくなった説明、spec の作業の事情だけが残った説明、同じファイルの「版」と「バージョン」の不揃い。
- 残した：検査の正規表現が拾わない番号の形（区切りのない `AC10`、全角など）。今のコメントに該当する行はない。
- `./gradlew check`（修正の前）、`node scripts/ci/spec-numbers.mjs`（0 件）、`scripts/ci/check-all.sh`（修正の後）は通った。
