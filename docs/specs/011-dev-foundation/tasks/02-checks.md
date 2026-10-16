# 段階 2：CI の検査を手元で流す

- 前提：段階 1
- 完了条件：`scripts/ci/check-all.sh` が手元と CI で CI の検査の script をすべて流す。E2E のコードの lint・型チェックが手元でも流れる。AC とテストの対応の検査と表示の文言の検査の漏れが直り、そのテストがある。CI の検査の文書があり、skill がそこを指す。commit の前の hook が `check-all.sh` を流す。
- 参照：[plan](../plan.md) 第 2 節

## タスク

- [x] **T011-02-01** `coverage-summary.mjs` を `scripts/ci/report/` に移し、ci.yml の呼び出しを直す。
- [x] **T011-02-02** `scripts/ci/check-all.sh`：script のテストと、`scripts/ci/` の直下の検査をすべて流し、1 つごとに 1 行を出す（plan 第 2.1 節）。仮の検査の script を足して流れることを確かめ、消す。
- [x] **T011-02-03** ci.yml：ジョブ「AC とテストの対応」を「CI の検査の script」にして `check-all.sh` を流す。店舗画面のジョブから入力の制約と表示の文言の手順を除く。CI で通ることを確かめる（push は利用者の許可を得てから。許可がなければ段階 4 の push で確かめる）。
- [x] **T011-02-04** `check-frontend.sh` の対象に `e2e`（lint、typecheck）を足す。引数なしの実行で e2e も流れることを確かめる。
- [x] **T011-02-05** [テスト] `ac-check.test.mjs`：`describe` だけにある AC と、`@Nested` のクラスの `@DisplayName` だけにある AC が対応とみなされない例を足し、レッドを確かめる。
- [ ] **T011-02-06** `ac-check.mjs` を直す（plan 第 2.3 節）。検査で失敗した AC を `ac-exclusions.json` に理由付きで除き、一覧を `docs/specs/mvp.md` の後に回した決定の表（spec 012）に書く。
- [ ] **T011-02-07** [テスト] `hardcoded-text.test.mjs`：引用符を含む正規表現のリテラルが文言とみなされない例と、割り算を正規表現とみなさない例を足し、レッドを確かめる。
- [ ] **T011-02-08** `hardcoded-text.mjs` の `stripComments` に正規表現のリテラルを足す（plan 第 2.4 節）。
- [ ] **T011-02-09** `docs/dev/ci.md`：CI のジョブと CI の検査の script の一覧（plan 第 2.5 節）。skill `local-dev` の「CI の script の検査」と ci.yml の冒頭のコメントを、この文書を指す形にする。
- [ ] **T011-02-10** `scripts/dev/hooks/pre-commit` と、setup に置き方の手順を書く（plan 第 2.6 節）。このマシンの `.git/hooks/pre-commit` に呼ぶ 1 行を足し、検査に当たる変更で commit が止まることを確かめる。
- [ ] **T011-02-11** 段階の完了の確認：`check-all.sh`、`check-frontend.sh` が通る。DN-10 のとおり、既存のテストを変えていない。
