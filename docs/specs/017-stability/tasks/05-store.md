# 段階 5：店舗画面のタイムアウトとログ

- 前提：なし
- 完了条件：AC-302 と US-66 のテストが通る。店舗画面のサーバー側から、バックエンドと IdP への呼び出しに待つ時間の上限がある。サーバーアクションの失敗と、バックエンドの 5xx がログに出力される。既存のテストが全て通る（`check-frontend.sh`、`e2e.sh`）。
- 参照：[plan](../plan.md) 第 4、6 節、spec の US-64、66、[店舗画面の構成](../../../frontend/apps/store.md) 第 4、9 節、[フロントの書き方](../../../frontend/coding.md) 第 3、8 節

## タスク

- [x] **T017-05-01** Auth.js の provider の fetch を差し替える仕組み（`customFetch`）が、使う版で IdP への呼び出し（discovery、token の交換）に効くかを確認する（plan 第 4 節 ※9）。結果をタスクの下に書く。効かなければ止めて報告する。
  - 効く。next-auth 5.0.0-beta.32 は @auth/core 0.41.3 を使い、`customFetch` を export する。@auth/core は provider の `customFetch` を、ログインの開始の discovery、callback の discovery・token の交換・userinfo の要求に渡す（`lib/actions/signin/authorization-url.js`、`lib/actions/callback/oauth/callback.js`）。下の oauth4webapi（3.8.8）は、`signal` を渡されなければ付けないので、差し替えた fetch で上限の signal を付けられる。
- [x] **T017-05-02** [テスト] 店舗画面の設定の単体テスト：バックエンドへの上限（`STORE_API_TIMEOUT_SECONDS`、既定 30 秒）と IdP への上限（`AUTH_KEYCLOAK_TIMEOUT_SECONDS`、既定 10 秒）を、設定の 1 か所で読み、値が正の数でなければ失敗する（AC-302）。レッドを確認する。
  - 確認は、起動の時に読む `loadServerConfig` で、本番・開発のモードによらず行う（モジュールの読み込みで読む `readServerConfig` は確認しない、今の作り）。
- [x] **T017-05-03** 店舗画面の設定に上限を追加する。T017-05-02 を通す。
- [x] **T017-05-04** [テスト] 上限の単体テストを書く。レッドを確認する。
  - API の client（公開用と会員用）の fetch に、上限の signal が付く。上限を超えたら、サーバーアクションは今の「接続できない」の結果になる（AC-302、303）。
  - token の更新と、Auth.js の provider の fetch に、上限の signal が付く。token の更新が上限を超えたら、今の「IdP に接続できない」の扱いになる（AC-302）。
  - 上限を 0.05 秒にして読み込み、応答しないサーバーで確認する。Auth.js の provider は、テストで置き換えた provider の設定から差し替えた fetch を取り出して呼ぶ。レッドは、上限がなくテストの時間切れになることと、差し替えた fetch がないことで確認した。
- [x] **T017-05-05** API の client、token の更新、Auth.js の provider に上限を付ける。T017-05-04 を通す。
  - API の client は、要求の middleware で上限の signal を付ける（呼ぶときに fetch を替える `UNCACHED` にも効く）。上限の signal と、上限を付けた fetch は `lib/server/timeout.ts` に置いた。
- [x] **T017-05-06** [テスト] ログの単体テストを書く。レッドを確認する。
  - サーバーアクションが例外で失敗したら、error のログに例外の名前とメッセージが出力され、「接続できない」の結果を返す。画面の移動・not found の例外は、ログに出力せずに通す（AC-307）。
  - API の client が 5xx を受けたら、warn のログに状態コード・エラーコード・メソッド・API の定義のパスが出力される。4xx では出力されない（AC-308）。
  - ログに token、Cookie、要求の本文、クエリが出力されない（AC-309）。
- [x] **T017-05-07** サーバーアクションの共通の関数と、API の client の応答の middleware に、ログを追加する。T017-05-06 を通す。
- [x] **T017-05-08** 文書を修正する：店舗画面の構成 第 4.2 節に上限と 5xx のログ、第 4.3 節にサーバーアクションの失敗のログ、第 9.1 節に IdP の上限。環境変数に 2 つの上限を追加する。
- [x] **T017-05-09** 段階の完了の確認：`check-frontend.sh`、`check-all.sh`、`e2e.sh` が通る。
  - `check-frontend.sh`、`check-all.sh`、`e2e.sh`（20 件。Keycloak でログインする流れを含み、差し替えた fetch で本物の Auth.js がログインできる）が通った。
