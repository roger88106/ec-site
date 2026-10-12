# 段階 6：E2E とデータの分離

- 前提：段階 4
- 完了条件：DC-22〜25。E2E を 2 回続けて流してどちらも通り、流す前後でローカルの DB の行数と Keycloak の会員の数が変わらない。CI の E2E が通る。
- 参照：[plan](../plan.md) 第 6.1、6.2 節

## タスク

- [x] **T4601** E2E が `seed/local` のどのデータに依存しているかを調べて報告する（税区分の「標準」「軽減」のほか）。
- [x] **T4602** seed を分ける：`db/seed/base/`（税区分と税率）、`db/seed/local/`、`db/seed/e2e/`（要るものがなければ置かない）。`application-local.yml` の Flyway の場所を直す。`db/seed/local/README.md` を、3 つの使い分けの説明にする（DC-25）。
- [x] **T4603** compose：Keycloak の E2E 用（profile `e2e`、ポート 8181、database `keycloak_e2e`、同じ realm の JSON）、`seaweedfs-init` に E2E 用のバケット、imgproxy の読める範囲に E2E 用の受付のバケット。
- [x] **T4604** バックエンドの `application-e2e.yml`（profile `local,e2e` で重ねる）：ポート 8090、database `ec_site_e2e`、seed の場所、issuer（8181）、画像のバケット。管理画面の `vite.config.ts` の API の転送先を環境変数で変えられるようにする。
- [x] **T4605** `e2e/scripts/prepare.ts`：E2E 用の Keycloak を止め、`ec_site_e2e`・`keycloak_e2e` を作り直し、Keycloak を起動して準備ができるのを待つ。`pnpm test` を「prepare → Playwright」にする。
- [x] **T4606** `playwright.config.ts`：バックエンドは profile `local,e2e`・8090 で毎回起動する（使い回さない）。admin は `VITE_OIDC_AUTHORITY`・転送先、store は `STORE_API_BASE_URL`・`AUTH_KEYCLOAK_ISSUER` を渡す。`src/urls.ts` を直す。E2E が手元で 2 回続けて通る（DC-22〜24）。
- [x] **T4607** CI の E2E の job を合わせる（compose の profile、準備の script）。`docs/dev/testing.md` と README から、「E2E のデータがローカルの DB に残る」の記述を消し、新しい手順を書く。
