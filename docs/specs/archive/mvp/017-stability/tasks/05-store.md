# 段階 5：店舗画面のタイムアウトとログ

- 前提：なし
- 完了条件：AC-302 と US-66 のテストが通る。店舗画面のサーバー側から、バックエンドと IdP への呼び出しにタイムアウトがある。サーバーアクションの失敗と、バックエンドの 5xx がログに出力される。既存のテストが全て通る（`check-frontend.sh`、`e2e.sh`）。
- 参照：[plan](../plan.md) 第 4、6 節、spec の US-64、66、[店舗画面の構成](../../../../../design/apps/store.md) 第 4、9 節、[フロントの書き方](../../../../../rules/frontend-coding.md) 第 3、8 節

## タスク

- [x] **T017-05-01** Auth.js の provider の fetch を差し替える仕組み（`customFetch`）が、使う版で IdP への呼び出し（discovery、token の交換）に適用されるかを確認する（plan 第 4 節 ※9）。結果をタスクの下に書く。効かなければ止めて報告する。
  - 適用される。next-auth 5.0.0-beta.32 は @auth/core 0.41.3 を使い、`customFetch` を公開している。@auth/core は provider の `customFetch` を、ログインの開始の discovery、callback の discovery・token の交換・userinfo のリクエストに渡す（`lib/actions/signin/authorization-url.js`、`lib/actions/callback/oauth/callback.js`）。内部で使う oauth4webapi（3.8.8）は、`signal` を渡されなければ付けないので、差し替えた fetch でタイムアウトの signal を付けられる。
- [x] **T017-05-02** [テスト] 店舗画面の設定の単体テスト：バックエンドへのタイムアウト（`STORE_API_TIMEOUT_SECONDS`、既定 30 秒）と IdP へのタイムアウト（`AUTH_KEYCLOAK_TIMEOUT_SECONDS`、既定 10 秒）を、設定の 1 か所で読み、値が正の数でなければ失敗する（AC-302）。レッドを確認する。
  - 確認は、起動の時に読む `loadServerConfig` で、本番・開発のモードによらず行う（モジュールの読み込みで読む `readServerConfig` は確認しない、今の作り）。
- [x] **T017-05-03** 店舗画面の設定にタイムアウトを追加する。T017-05-02 を通す。
- [x] **T017-05-04** [テスト] タイムアウトの単体テストを書く。レッドを確認する。
  - API の client（公開用と会員用）の fetch に、タイムアウトの signal が付く。タイムアウトしたら、サーバーアクションは今の「接続できない」の結果になる（AC-302、303）。
  - token の更新と、Auth.js の provider の fetch に、タイムアウトの signal が付く。token の更新がタイムアウトしたら、今の「IdP に接続できない」の扱いになる（AC-302）。
  - タイムアウトを 0.05 秒にして読み込み、応答しないサーバーで確認する。Auth.js の provider は、テストで置き換えた provider の設定から差し替えた fetch を取り出して呼ぶ。レッドは、タイムアウトがなく、テストがタイムアウトで失敗することと、差し替えた fetch がないことで確認した。
- [x] **T017-05-05** API の client、token の更新、Auth.js の provider にタイムアウトを付ける。T017-05-04 を通す。
  - API の client は、リクエストの middleware でタイムアウトの signal を付ける（呼ぶときに fetch を替える `UNCACHED` にも適用される）。タイムアウトの signal と、タイムアウトを付けた fetch は `lib/server/timeout.ts` に置いた。
- [x] **T017-05-06** [テスト] ログの単体テストを書く。レッドを確認する。
  - サーバーアクションが例外で失敗したら、error のログに例外の名前とメッセージが出力され、「接続できない」の結果を返す。画面の移動・not found の例外は、ログに出力せずに通す（AC-307）。
  - API の client が 5xx を受けたら、warn のログに状態コード・エラーコード・メソッド・API の定義のパスが出力される。4xx では出力されない（AC-308）。
  - ログに token、Cookie、リクエストの本文、クエリが出力されない（AC-309）。
- [x] **T017-05-07** サーバーアクションの共通の関数と、API の client の応答の middleware に、ログを追加する。T017-05-06 を通す。
- [x] **T017-05-08** 文書を修正する：店舗画面の構成 第 4.2 節にタイムアウトと 5xx のログ、第 4.3 節にサーバーアクションの失敗のログ、第 9.1 節に IdP のタイムアウト。環境変数に 2 つのタイムアウトを追加する。
- [x] **T017-05-09** 段階の完了の確認：`check-frontend.sh`、`check-all.sh`、`e2e.sh` が通る。
  - `check-frontend.sh`、`check-all.sh`、`e2e.sh`（20 件。Keycloak でログインする流れを含み、差し替えた fetch で本物の Auth.js がログインできる）が通った。
