# 段階 3：ローカルの環境

- 前提：段階 2
- 完了条件：`scripts/dev/reset.sh` でローカルの DB・IdP・全てを再作成でき、全てを再作成すると seed・画像・IdP がそろう。E2E の script が前提のコンテナを確認する。開発のサーバーが作るファイルが作業ツリーに残らない。Mailpit の SMTP の既定のポートが 2525 になる。ポートの予約から外す script と手順がある。
- 参照：[plan](../plan.md) 第 3 節、第 7 節、[開発環境の起動](../../../dev/setup.md)

## タスク

- [x] **T011-03-01** `scripts/dev/reset.sh`（`db`・`keycloak`・`all`。plan 第 3.1 節）。`db` の後に `ec_site` が空で、`ec_site_e2e` と `keycloak` の database が残ることを確認する。setup 第 4 節を `reset.sh` に置き換える。
- [x] **T011-03-02** 正本のそろいの確認：`reset.sh all` の後にバックエンドと店舗画面を起動し、seed の商品の画像が出て、`member@example.com` で seed の注文が見えることを確認する。一致しないものは正本を修正する（修正するときは利用者に先に示す）。
- [x] **T011-03-03** `e2e.sh` の前提のコンテナの確認（plan 第 3.2 節）。mailpit を停止して実行し、名前を示して終了することを確認する。
- [x] **T011-03-04** 開発のサーバーが作るファイル（plan 第 3.3 節）：Next.js の設定で `AGENTS.md` を作らせない方法を調べ、なければ `.gitignore` に例外として書く。admin・store・backend を起動した後の `git status` に出ないことを確認する。
- [x] **T011-03-05** Mailpit の SMTP の既定のポートを 2525 にする（plan 第 7.2 節）。`MAILPIT_SMTP_PORT` を付けずに compose とバックエンドを起動し、E2E の出荷通知のメールのテストが通ることを確認する。setup を修正する。
- [x] **T011-03-06** `scripts/dev/windows/exclude-ports.ps1` と、setup の「ポートが使えないとき」の節（plan 第 7.1 節）。変える前の表示と、管理者でないときに何もせず終了することを確認する。予約の変更は、利用者の許可を得て利用者のマシンで一度実行する。
- [x] **T011-03-07** 段階の完了の確認：`check-all.sh`、`./gradlew check`、`e2e.sh` が通る。
