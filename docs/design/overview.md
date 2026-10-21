# 全体構成

システムの全体を 1 ページで示す。モジュールの依存とイベント、外部システムとの境目を載せる。詳しい設計は [バックエンドのアーキテクチャ](../backend/architecture.md)、[モジュールの設計書](../backend/modules/)、[フロントのアーキテクチャ](../frontend/architecture.md) に書く。モジュールの名前の論理名は [用語集](../reference/glossary.md) に書く。

## 1. モジュールの依存とイベント

- 図は `ModuleDiagramTest`（`backend/ecsite/src/test/java/com/ecsite/modulith/ModuleDiagramTest.java`）がコードから作る。手動で修正しない。
- 実線は、モジュールが相手の API を呼ぶ（矢印の向きが依存の向き）。点線は、イベントを発行するモジュールから受けるモジュールへ向かい、線の上にイベントの名前を書く。
- 他のモジュールが受けるイベントだけを書く。受け手のない公開イベントは図に出ない。
- 依存は package-info の `allowedDependencies` で宣言し、`ModulithTest` が守らせる（[バックエンドのアーキテクチャ](../backend/architecture.md) 第 1 節）。
- モジュールの依存かイベントが変わると、テストが失敗する。`backend/ecsite` で `./gradlew test --tests ModuleDiagramTest -PupdateDocs` を実行すると図を再生成するので、差分を確認して commit する。

<!-- generated:modules -->
```mermaid
flowchart LR
    cart
    catalog
    inventory
    media
    member
    notification
    order
    payment
    region
    reporting
    shipping
    storefront
    tax
    cart --> catalog
    cart --> inventory
    cart --> member
    catalog --> media
    catalog --> tax
    notification --> order
    order --> cart
    order --> catalog
    order --> inventory
    order --> member
    order --> payment
    order --> region
    order --> shipping
    order --> tax
    reporting --> catalog
    reporting --> order
    reporting --> region
    reporting --> shipping
    storefront --> catalog
    storefront --> inventory
    storefront --> member
    storefront --> order
    cart -. CartChanged .-> order
    catalog -. ProductDeleted<br>VariantDeleted<br>VariantRegistered .-> inventory
    order -. OrderShipped .-> notification
    order -. OrderStatusChanged .-> reporting
    payment -. PaymentAuthorized<br>PaymentExpired .-> order
    shipping -. DeliveryStatusChanged .-> reporting
```
<!-- /generated:modules -->

## 2. 外部システムとの境目

図は手動で書く。`ModuleDiagramTest` が比べるのはモジュールの名前の一覧だけで、モジュールを追加する・削除するとテストが失敗する。外部システムとの線は比べないので、境目を変えたらこの図も修正する。

<!-- external-systems -->
```mermaid
flowchart LR
    store_app["店舗画面（Next.js）"]
    admin_app["管理画面（Vite の SPA）"]
    idp["IdP（Keycloak）"]
    payment_service["決済サービス（Stripe）"]
    carrier["物流サービス"]
    smtp["メール（SMTP）"]
    storage["オブジェクトストレージ（S3 互換）"]
    converter["画像の変換（imgproxy）"]

    subgraph backend["バックエンド"]
        module_storefront["店舗"]
        module_cart["カート"]
        module_order["注文"]
        module_member["会員"]
        module_catalog["商品カタログ"]
        module_inventory["在庫"]
        module_tax["税"]
        module_region["地域"]
        module_media["メディア"]
        module_payment["決済"]
        module_shipping["出荷"]
        module_notification["通知"]
        module_reporting["レポート"]
    end

    store_app -->|ログイン| idp
    admin_app -->|ログイン| idp
    store_app -->|店舗 API| backend
    admin_app -->|管理 API| backend
    admin_app -->|画像のアップロード（署名付き URL）| storage
    backend -->|token の検証の鍵| idp
    module_payment -->|支払画面、与信、売上の確定、取消、返金| payment_service
    payment_service -->|通知| module_payment
    module_shipping -->|追跡を始める| carrier
    carrier -->|通知| module_shipping
    module_notification -->|出荷通知メール| smtp
    module_media -->|変換| converter
    module_media -->|保存| storage
    store_app -->|商品の画像の表示| storage
```
<!-- /external-systems -->

| 外部システム | 使うもの | 接続するモジュール | 詳しい文書 |
|--------------|----------|--------------------|------------|
| IdP | Keycloak | なし ※1 | [バックエンドのアーキテクチャ](../backend/architecture.md) 第 7 節、[会員モジュール](../backend/modules/member.md) |
| 決済サービス | Stripe（ローカルの既定は mock） | 決済。通知（webhook）を受ける | [決済モジュール](../backend/modules/payment.md)、[バックエンドのアーキテクチャ](../backend/architecture.md) 第 8 節 |
| 物流サービス | mock だけ（本番の実装はまだない） | 出荷。通知を受ける | [出荷モジュール](../backend/modules/shipping.md) |
| メール | SMTP（ローカルは Mailpit） | 通知 | [通知モジュール](../backend/modules/notification.md) |
| オブジェクトストレージ | S3 互換（ローカルは SeaweedFS）と、画像の変換（imgproxy） | メディア | [メディアモジュール](../backend/modules/media.md) |

※1：画面がログインし、バックエンドは access token を検証するだけで、モジュールでなく共通の設定が行う。会員モジュールが token の subject と会員 ID を結ぶ。

画面は 2 つある。店舗画面は Next.js で、サーバーから API を呼ぶ。管理画面は Vite の SPA で、ブラウザーから API を呼ぶ。構成は [フロントのアーキテクチャ](../frontend/architecture.md) に書く。
