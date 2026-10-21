# 段階 2：CI の検査を手元で実行する

- 前提：段階 1
- 完了条件：`scripts/ci/check-all.sh` が手元と CI で CI の検査の script をすべて実行する。E2E のコードの lint・型チェックが手元でも実行される。AC とテストの対応の検査と表示の文言の検査の漏れが修正され、そのテストがある。CI の検査の文書があり、skill がそこを指す。commit の前の hook が `check-all.sh` を実行する。
- 参照：[plan](../plan.md) 第 2 節

## タスク

- [x] **T011-02-01** `coverage-summary.mjs` を `scripts/ci/report/` に移し、ci.yml の呼び出しを修正する。
- [x] **T011-02-02** `scripts/ci/check-all.sh`：script のテストと、`scripts/ci/` の直下の検査をすべて実行し、1 つごとに 1 行を出力する（plan 第 2.1 節）。仮の検査の script を追加して実行されることを確認し、削除する。
- [x] **T011-02-03** ci.yml：ジョブ「AC とテストの対応」を「CI の検査の script」にして `check-all.sh` を実行する。店舗画面のジョブから入力の制約と表示の文言の手順を除く。CI で通ることを確認する（push は利用者の許可を得てから。許可がなければ段階 4 の push で確認する）。
- [x] **T011-02-04** `check-frontend.sh` の対象に `e2e`（lint、typecheck）を追加する。引数なしの実行で e2e も実行されることを確認する。
- [x] **T011-02-05** [テスト] `ac-check.test.mjs`：`describe` だけにある AC と、`@Nested` のクラスの `@DisplayName` だけにある AC が対応とみなされない例を追加し、レッドを確認する。
- [x] **T011-02-06** `ac-check.mjs` を修正する（plan 第 2.3 節）。検査で失敗した AC を `ac-exclusions.json` に理由付きで除き、一覧を `docs/specs/mvp.md` の後に回した決定の表（spec 012）に書く。
- [x] **T011-02-07** [テスト] `hardcoded-text.test.mjs`：引用符を含む正規表現のリテラルが文言とみなされない例と、割り算を正規表現とみなさない例を追加し、レッドを確認する。
- [x] **T011-02-08** `hardcoded-text.mjs` の `stripComments` に正規表現のリテラルを追加する（plan 第 2.4 節）。
- [x] **T011-02-09** `docs/dev/ci.md`：CI のジョブと CI の検査の script の一覧（plan 第 2.5 節）。skill `local-dev` の「CI の script の検査」と ci.yml の冒頭のコメントを、この文書を指す形にする。
- [x] **T011-02-10** `scripts/dev/hooks/pre-commit` と、setup に置き方の手順を書く（plan 第 2.6 節）。このマシンの `.git/hooks/pre-commit` に呼ぶ 1 行を追加し、検査に当たる変更で commit が失敗することを確認する。
- [x] **T011-02-11** 段階の完了の確認：`check-all.sh`、`check-frontend.sh` が通る。DN-10 のとおり、既存のテストを変えていない。
