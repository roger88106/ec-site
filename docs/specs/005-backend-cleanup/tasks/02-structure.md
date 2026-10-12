# 段階 2：標準の構成

- 前提：段階 1
- 完了条件：DC-27〜30。ArchUnit と Modulith のテスト（単体テスト）が通る。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 1 節

## 準備

- [x] **T5201** 全ファイルの移し先の表を作り（plan 第 1.2 節を元に。order と catalog の application の分け方を含む）、利用者に確かめる。
- [x] **T5202** `ModulithTest` を `src/test` に移す。Docker を止めた状態で通ることを確かめる。

## 検査のテスト

- [x] **T5203** [テスト] `src/test/java/com/ecsite/architecture/` に ArchUnit の規則を書く：層の依存の向き、モジュールの直下のパッケージの名前、`@Transactional`・`@RestController` の置き場所、パッケージの直下のファイルが 20 を超えない、`*dir` のパッケージがない。今のコードで失敗することを確かめる（DC-30）。

## 移動（モジュールごとに、`git mv` の commit と参照の修正の commit を分ける）

- [x] **T5204** cart を標準の構成に（Directory の口と実装を分ける）。
- [x] **T5205** media を標準の構成に（`service/` 11 を application・domain・infrastructure に）。
- [x] **T5206** member を標準の構成に。
- [x] **T5207** storefront を標準の構成に（`catalogdir`・`orderdir`・`stockdir` を `application/port/` と `infrastructure/directory/` に）。
- [x] **T5208** catalog：`infrastructure/` を `persistence/`・`directory/`・`cache/` に、application の参照の Repository の口を `application/port/` に。
- [x] **T5209** order、tax、inventory：application の口を `application/port/` に、order の `domain/port/` の Directory を使う層の port に。
- [ ] **T5211** レベル A の domain の Factory（`TaxCategoryFactory`、`CategoryFactory`、`ProductFactory`）から Spring の注釈（`@Component`）を外す。T5203 で見つかった（domain は何にも依存しない。constitution 4.2）。Repository が Factory を Spring の部品として受け取るのをやめ、Factory を状態を持たないクラスとして直接使う。
- [ ] **T5210** 各モジュールの設計書と `package-info.java` の説明を新しい構成に合わせる。T5203 が通る（DC-27〜30）。

## 移し先の表（T5201）

2026-10-13 に作った。利用者の review の前に、この表で進める（利用者の指示）。`…/` は `module/` の下。表にないファイルは動かさない。`<モジュール>Message` は段階 4 で消すので、置き場所だけ移す。

| モジュール | 今 | 移し先 |
|------------|----|--------|
| cart | `controller/CartController` | `presentation/` |
| cart | `service/Cart`、`CartItem`、`CartMergeResult`、`CartErrorCode`、`CartMessage` | `domain/` |
| cart | `service/CartService` | `application/` |
| cart | `service/CartRepository` | `application/port/` |
| cart | `catalogdir/CartCatalogDirectory`、`stockdir/CartStockDirectory`（どちらも class） | 口を `application/port/CatalogDirectory`・`StockDirectory`（interface）に、実装を `infrastructure/directory/CatalogApiDirectory`・`StockApiDirectory` に分ける |
| cart | `infrastructure/JooqCartRepository` | `infrastructure/persistence/` |
| media | `service/MediaService`、`AssetFileHandler` | `application/` |
| media | `service/AssetRepository`、`ObjectStorage`、`ImageConverter` | `application/port/` |
| media | `service/Asset`、`AssetKeys`、`ImageFormat`、`AssetReleased`、`MediaErrorCode`、`MediaMessage` | `domain/` |
| media | `infrastructure/S3ObjectStorage`、`MediaStorageConfig`、`MediaStorageProperties` | `infrastructure/storage/` |
| media | `infrastructure/ImgproxyImageConverter`、`ImgproxyProperties` | `infrastructure/converter/` |
| media | `infrastructure/JooqAssetRepository` | `infrastructure/persistence/` |
| member | `service/MemberService` → `application/`、`service/MemberRepository` → `application/port/`、`service/Member` → `domain/`、`infrastructure/JooqMemberRepository` → `infrastructure/persistence/` | |
| storefront | `controller/*`（Controller 4、`StorefrontMapper`） | `presentation/` |
| storefront | `service/StorefrontService`、`ProductDetailView`、`ProductSummaryView`、`ShopProperties`、`StorefrontConfig` | `application/` |
| storefront | `catalogdir/CatalogCategory`、`CatalogProduct`、`CatalogProductDetail`、`ProductCondition`、`orderdir/CartView` | `application/port/`（口が返す自分の型） |
| storefront | `catalogdir/CatalogDirectory`、`orderdir/OrderDirectory`、`stockdir/StockDirectory`（どれも class） | 口を `application/port/`（interface）に、実装を `infrastructure/directory/CatalogApiDirectory`・`OrderApiDirectory`・`StockApiDirectory` に分ける |
| catalog | `application/CategoryQueryRepository`、`OrderableVariantQueryRepository`、`ProductQueryRepository`、`StoreProductQueryRepository`、`ProductSearch` | `application/port/` |
| catalog | `domain/product/port/MediaDirectory`、`TaxDirectory`（application と infrastructure だけが使う） | `application/port/` |
| catalog | `api/OrderCatalogApi` の `@Transactional` と処理 | `application/OrderableVariantQueryService` を作って移し、`OrderCatalogApi` はほかの api と同じく application に任せる |
| catalog | `infrastructure/Jooq*`（6）、`PostgresProductSearch`、`CategoryConditions`、`PriceInclTaxField`、`ProductVisibility`、`SellableVariants`、`StoreImageUrls`、`StoreProductSummaries` | `infrastructure/persistence/`（13。目安を超えるが分けない：jOOQ の Repository と、それらだけが使う package-private の部品で、分けると公開しなければならない） |
| catalog | `infrastructure/MediaApiDirectory`、`TaxApiDirectory`、`TaxRateScheduleCache` | `infrastructure/directory/`（キャッシュは `TaxApiDirectory` だけが使う package-private の部品なので、plan の `cache/` ではなく同じパッケージに置く） |
| order | `application/CheckoutView`、`PlaceOrderCommand`、`PlacementResult` | `application/model/` |
| order | `application/OrderQueryRepository` | `application/port/` |
| order | `domain/port/CartDirectory`、`ProductDirectory`、`StockDirectory`（application が使う） | `application/port/` |
| order | `domain/port/TaxDirectory`（domain の `Quote` が使う） | `domain/quote/port/` |
| order | `infrastructure/CartApiDirectory`、`ProductApiDirectory`、`StockApiDirectory`、`TaxRateApiDirectory` | `infrastructure/directory/`（`TaxRateApiDirectory` は名前の規則に合わせて `TaxApiDirectory` に） |
| order | `infrastructure/Jooq*`（3） | `infrastructure/persistence/` |
| order | `infrastructure/OrderConfig` | `infrastructure/`（設定の型 `OrderProperties` を有効にするだけの設定。今のまま） |
| tax | `application/TaxCategoryQueryRepository` | `application/port/` |
| tax | `infrastructure/Jooq*`（2） | `infrastructure/persistence/` |
| inventory | `application/StockQueryRepository` | `application/port/` |
| inventory | `infrastructure/Jooq*`（3）、`ActiveReservations` | `infrastructure/persistence/` |

移した後に目安（10）を超えるパッケージ：`catalog/infrastructure/persistence`（13、上の理由）。order と catalog の application は、口と model を出すと 10 以下になるので、use case で分けない。
