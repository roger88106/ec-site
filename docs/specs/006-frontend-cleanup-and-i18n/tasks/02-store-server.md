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

- [ ] **T6205** `src/auth/` に分ける（`config`・`refresh`・`logout`・`index`）。`@/auth` の import は変えない。テストが通る（DC-47）。
- [ ] **T6206** サーバー側のログの道具を調べて決める（OpenTelemetry の SDK を入れるか、`traceparent` を作る小さな部品にするか）。利用者に確かめる。
- [ ] **T6207** [テスト] ログの部品：JSON の形、レベル、追跡の ID。バックエンドへの要求に `traceparent` が付く。
- [ ] **T6208** ログの部品を作り、`console.error` を置き換える。手元で、店舗画面のログとバックエンドのログの追跡の ID が揃うことを確かめる（DC-62）。
