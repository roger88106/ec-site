# 画面の確認のデータ

決済を確認する、機能紹介のページのスクリーンショットを撮る、画面で確認するデータを探すときに読む。毎回の手順ではない。

## 決済（mock と Stripe）

- profile `local` は mock で、注文すると mock の支払画面に移る（[決済の設計](../../../docs/02-設計/payment.md)の「mock」）。期限切れを早く確認する引数は、mock でも[開発環境の起動](../../../docs/06-手順書/setup.md)の「Stripe で動かす」のものを使う。
- Stripe の sandbox は[開発環境の起動](../../../docs/06-手順書/setup.md)の「Stripe で動かす」のとおり。鍵は `/.local/stripe.env` にある（なければ利用者に頼む。鍵を会話に貼らせない）。
- 転送した通知は、鍵の行を出さずに読む：`docker logs ec-site-stripe-cli 2>&1 | grep -- '-->' | tail`（ログ全体は署名の鍵を含むので出さない）。
- 支払は `payment.payment`（`status`、`page_id`、`authorization_ref`）、注文は `ordering.sales_order`。
- Stripe の Checkout の画面は描画が遅く、入力が遅れて二重に入ることがある。入力の後に拡大して確認してから支払う。

## 機能紹介のページのスクリーンショット

[開発環境の起動](../../../docs/06-手順書/setup.md)の「機能紹介のページのスクリーンショット」のとおり。

## ローカルの画面のデータ

local profile の seed（`backend/ecsite/src/main/resources/db/seed/local/`）が、画面を確認するためのデータを作る。注文を作る script は必要ない。商品（在庫切れ・残りわずかを含む）、会員 `member@example.com` の注文の状態、レポート用の注文、ID の体系は、seed の SQL と `README.md` を読む。

- 会員の注文は、状態に合う支払（mock の与信）を持ち、受付の注文は管理画面から出荷できる（Stripe の profile では売上確定で失敗する）。承認・却下・運営者のキャンセルは、申請中の注文で管理画面か管理 API（`PUT /api/admin/orders/{orderId}/cancellation-request/approve` など）から試す。
- 画面で変えたデータを最初の状態に戻す手順は、[開発環境の起動](../../../docs/06-手順書/setup.md)の「データの再作成」。
