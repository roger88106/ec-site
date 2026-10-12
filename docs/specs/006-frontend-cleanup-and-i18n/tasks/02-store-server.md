# 段階 2：店舗画面のサーバーの処理

- 前提：段階 1
- 完了条件：DC-45〜47、62。店舗画面のテスト（spec 004 のサーバーの処理のテストを含む）、ビルド、E2E が通る。
- 参照：[plan](../plan.md) 第 1 節、`docs/frontend/store.md` 第 9 節

## 環境変数（DC-45）

- [x] **T6201** [テスト] 起動時の確かめ：本番のモードで必須の値がなければ、足りない名前を並べた例外。開発のモードでは既定の値。
- [x] **T6202** `src/auth/config.ts` と `instrumentation.ts` を作り、T6201 を通す。`playwright.config.ts` と CI の E2E に値を渡す。値のない `next start` が起動に失敗することを手元で一度確かめる。`docs/frontend/store.md` 第 9 節と `docs/dev/environment.md` を直す。

## サーバーアクション（DC-46）

- [x] **T6203** [テスト] `readString`・`readInt` の単体テスト（空、数でない、小数、範囲）と、`withActionResult` の単体テスト（画面の移動の例外を通す、接続の失敗、予期しない例外）。
- [x] **T6204** `src/lib/server/formData.ts` と `src/actions/withActionResult.ts` を作り、`actions/` を置き換える。T6203 と、spec 004 のサーバーの処理のテストが通る。

## 認証とログ（P2）

- [x] **T6205** `src/auth/` に分ける（`config`・`refresh`・`logout`・`index`）。`@/auth` の import は変えない。テストが通る（DC-47）。
- [x] **T6206** サーバー側のログの道具を調べて決める（OpenTelemetry の SDK を入れるか、`traceparent` を作る小さな部品にするか）。利用者に確かめる。
- [x] **T6207** [テスト] ログの部品：JSON の形、レベル、追跡の ID。バックエンドへの要求に `traceparent` が付く。
- [x] **T6208** ログの部品を作り、`console.error` を置き換える。手元で、店舗画面のログとバックエンドのログの追跡の ID が揃うことを確かめる（DC-62）。

## ログの道具の決定（T6206）

2026-10-13 に利用者が決めた：OpenTelemetry の SDK は入れず、W3C の `traceparent` を作る小さな部品にする。ログを出す所が 2 か所（カートの引き継ぎの失敗）しかなく、SDK は重いため。

- 追跡の ID は、要求ごとに 1 つ（React の `cache` で、同じ要求の中では同じ ID を返す）。
- `traceparent` は、会員の口（キャッシュを使わない `memberApi`）の要求にだけ付ける。公開の口は Next.js の fetch のキャッシュを使い、要求のヘッダーがキャッシュのキーに入るので、要求ごとに違う値を付けるとキャッシュが効かなくなる。キャッシュした応答は 1 つの要求に結びつかないので、追跡の ID も要らない。
