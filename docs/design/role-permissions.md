# ロールと権限の対応表

どのロールが、どの API の操作を実行できるかの一覧。認証と認可の方式は [バックエンドのアーキテクチャ](../backend/architecture.md) 第 7 節、ロールと権限の名前の意味は [用語集](../reference/glossary.md)にある。

- 表は `scripts/ci/role-permissions.mjs` が、管理 API の契約（各操作の `x-required-permission`）、運営者の realm の設定（`docker/keycloak/realms/ec-admin-realm.json` のロール）、店舗 API の契約（各操作の `security`）から作る。手で直さない。
- 契約か realm の設定を変えると、CI の検査が失敗する。ルートで `node scripts/ci/role-permissions.mjs --write` を流して表を書き直し、差分を確かめて commit する。
- 管理画面は、権限のない画面のうちレポートの画面だけをメニューから外す（[管理画面](../frontend/apps/admin.md)）。ほかの操作は画面に出るが、権限がなければ API が 403 を返す。
- 操作のパスは、契約の `servers.url`（管理 API は `/api/admin`、店舗 API は `/api/store`）の後ろの部分。
- 管理 API の「ログインだけ」は、権限によらず、運営者としてログインしていればよい操作。店舗 API の「会員」はログインが要る操作、「任意」はログインしていれば会員として扱う操作（カート）、「不要」はログインの要らない操作。

<!-- generated:role-permissions -->
### ロールと権限

| ロール | 説明 | tax:read | tax:write | category:read | category:write | product:read | product:write | inventory:read | inventory:write | order:read | order:write | report-sales-trend:read | report-variant-sales:read | report-customer:read |
|--------|------|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `administrator` | 管理者：すべての権限 | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| `viewer` | 閲覧者：参照の権限すべて | ○ |  | ○ |  | ○ |  | ○ |  | ○ |  |  |  |  |
| `manager` | 管理職：参照の権限すべてと、すべてのレポート | ○ |  | ○ |  | ○ |  | ○ |  | ○ |  | ○ | ○ | ○ |
| `sales` | 営業：参照の権限すべてと、売上の推移を除くレポート | ○ |  | ○ |  | ○ |  | ○ |  | ○ |  |  | ○ | ○ |

### 管理 API

| 操作 | 内容 | 権限 | `administrator` | `viewer` | `manager` | `sales` |
|------|------|------|---|---|---|---|
| `GET /tax-categories` | 税区分の一覧（税率を含む） | `tax:read` | ○ | ○ | ○ | ○ |
| `POST /tax-categories` | 税区分の登録 | `tax:write` | ○ |  |  |  |
| `GET /tax-categories/{taxCategoryId}` | 税区分の取得（税率を含む） | `tax:read` | ○ | ○ | ○ | ○ |
| `PUT /tax-categories/{taxCategoryId}/rates` | 税率の一覧を置き換える | `tax:write` | ○ |  |  |  |
| `GET /categories` | カテゴリの木 | `category:read` | ○ | ○ | ○ | ○ |
| `POST /categories` | カテゴリの登録 | `category:write` | ○ |  |  |  |
| `PUT /categories/order` | 兄弟カテゴリの並び順を変更する | `category:write` | ○ |  |  |  |
| `GET /categories/{categoryId}` | カテゴリの取得 | `category:read` | ○ | ○ | ○ | ○ |
| `PUT /categories/{categoryId}` | カテゴリ名の変更 | `category:write` | ○ |  |  |  |
| `DELETE /categories/{categoryId}` | カテゴリの削除 | `category:write` | ○ |  |  |  |
| `GET /products` | 商品の一覧・絞り込み | `product:read` | ○ | ○ | ○ | ○ |
| `POST /products` | 商品の登録 | `product:write` | ○ |  |  |  |
| `GET /products/{productId}` | 商品の詳細（バリアントを含む） | `product:read` | ○ | ○ | ○ | ○ |
| `PUT /products/{productId}` | 商品の編集 | `product:write` | ○ |  |  |  |
| `DELETE /products/{productId}` | 商品の削除 | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/publish` | 公開する | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/unpublish` | 非公開にする | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/discontinue` | 販売終了にする | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/publication-period` | 公開期間の設定 | `product:write` | ○ |  |  |  |
| `POST /products/{productId}/variants` | バリアントの登録 | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/variants/order` | バリアントの表示順を変更する | `product:write` | ○ |  |  |  |
| `DELETE /products/{productId}/variants/{variantId}` | バリアントの削除 | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/variants/{variantId}/suspend` | バリアントを販売停止にする | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/variants/{variantId}/resume` | バリアントの販売停止を解除する | `product:write` | ○ |  |  |  |
| `POST /products/{productId}/image-uploads` | 画像のアップロードを受け付ける | `product:write` | ○ |  |  |  |
| `POST /products/{productId}/images` | 受け付けた画像を登録する | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/images/order` | 画像の並び順の変更 | `product:write` | ○ |  |  |  |
| `DELETE /products/{productId}/images/{imageId}` | 画像の削除 | `product:write` | ○ |  |  |  |
| `PUT /products/{productId}/variants/{variantId}/image` | バリアントの画像の指定 | `product:write` | ○ |  |  |  |
| `GET /inventory/variants/{variantId}` | バリアントの在庫数 | `inventory:read` | ○ | ○ | ○ | ○ |
| `POST /inventory/variants/{variantId}/adjustments` | 在庫の調整 | `inventory:write` | ○ |  |  |  |
| `GET /inventory/variants/{variantId}/adjustments` | 在庫調整の履歴 | `inventory:read` | ○ | ○ | ○ | ○ |
| `GET /inventory/stock-summaries` | 商品ごとの在庫数の合計 | `inventory:read` | ○ | ○ | ○ | ○ |
| `GET /orders` | 注文一覧 | `order:read` | ○ | ○ | ○ | ○ |
| `GET /orders/{orderId}` | 注文の詳細 | `order:read` | ○ | ○ | ○ | ○ |
| `PUT /orders/{orderId}/cancellation-request/approve` | キャンセルの申請の承認 | `order:write` | ○ |  |  |  |
| `PUT /orders/{orderId}/cancellation-request/reject` | キャンセルの申請の却下 | `order:write` | ○ |  |  |  |
| `PUT /orders/{orderId}/cancel` | 運営者の判断によるキャンセル | `order:write` | ○ |  |  |  |
| `POST /orders/{orderId}/shipment` | 出荷の登録 | `order:write` | ○ |  |  |  |
| `GET /me` | 自分の権限の一覧 | ログインだけ | ○ | ○ | ○ | ○ |
| `GET /reports/sales-trend` | 売上の推移 | `report-sales-trend:read` | ○ |  | ○ |  |
| `GET /reports/variant-sales` | バリアントの売れ行き | `report-variant-sales:read` | ○ |  | ○ | ○ |
| `GET /reports/customer-breakdown` | 地域・年齢層・性別 | `report-customer:read` | ○ |  | ○ | ○ |
| `GET /reports/settings` | レポートの設定 | ログインだけ | ○ | ○ | ○ | ○ |

### 店舗 API

| 操作 | 内容 | ログイン |
|------|------|----------|
| `GET /products` | 商品一覧 | 不要 |
| `GET /products/search` | キーワード検索 | 不要 |
| `GET /products/{productId}` | 商品の詳細 | 不要 |
| `GET /categories` | カテゴリの木 | 不要 |
| `POST /carts` | カートを作る | 不要 |
| `GET /carts/{cartId}` | カートの表示 | 任意 |
| `POST /carts/{cartId}/items` | カートに入れる | 任意 |
| `PUT /carts/{cartId}/items/{variantId}` | 品目の数量の変更 | 任意 |
| `DELETE /carts/{cartId}/items/{variantId}` | 品目の削除 | 任意 |
| `GET /me/cart` | 会員のカート | 会員 |
| `POST /me/cart/merge` | カートを移す | 会員 |
| `POST /carts/{cartId}/checkouts` | 注文手続きに進む | 会員 |
| `GET /checkouts/{checkoutId}` | 注文手続きの表示 | 会員 |
| `POST /checkouts/{checkoutId}/order` | 注文する | 会員 |
| `GET /me/orders` | 自分の注文の一覧 | 会員 |
| `GET /orders/{orderId}` | 注文の表示 | 会員 |
| `PUT /orders/{orderId}/cancellation-request` | キャンセルの申請 | 会員 |
| `POST /orders/{orderId}/payment-page` | 支払の画面を開く | 会員 |
| `GET /shop` | 店舗の情報 | 不要 |
| `GET /prefectures` | 都道府県の一覧 | 不要 |
| `GET /prefectures/{prefectureCode}/municipalities` | 都道府県の市区町村の一覧 | 不要 |
| `GET /postal-codes/{postalCode}` | 郵便番号の住所 | 不要 |
<!-- /generated:role-permissions -->
