# 段階 8：コメント

- 前提：段階 7
- 完了条件：DC-44、65。コメントだけの commit（DN-5）。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 6 節、`docs/style/comments.md`

## タスク

- [x] **T5801** 「複雑な処理」の基準に当たる所の一覧を作る（stream の 2 段以上の変換、`groupingBy`・`flatMap`・`reduce`、入れ子の繰り返し、3 つ以上の条件、3 つ以上の分岐）。すでに説明があるものを除く。
- [x] **T5802** 一覧の所に、コードを読んで意味を確認してから、何の結果を得るか・なぜそうするかを 1 行で書く。分からない所は書かずに一覧にして利用者に聞く。モジュールごとに commit。
- [x] **T5803** 本番のコード（`src/main` と `build.gradle.kts`）のコメントの spec・plan・tasks の番号を、規則の言葉に書き換える（`docs/style/comments.md` 第 1 節）。番号を削除するだけで済む所は削除する。モジュールごとに commit（DC-65）。

## 複雑な処理の一覧（T5801）

2026-10-13 に `.local/scripts/complex.cjs`（Git に入れない）で作った。基準は `docs/style/comments.md` 第 5 節。直前の行にコメントがあるものは除いた（4 か所）。plan の「stream は約 120 か所」は `.stream()` の全ての数で、基準に当たる 2 段以上の変換はこのうちの一部である。

1 行のメソッドで、メソッドの Javadoc が何の結果を得るかをすでに書いているもの（例：`BoundedText.isFilledWithin`）は、T5802 で読んで「説明がある」と判断したら書かない。

```
common/error/handler/GlobalExceptionHandler.java:159  [集計]  List<FieldValidationError> errors = ex.getParameterValidationResults()
common/error/handler/GlobalExceptionHandler.java:290  [分岐 6]  return switch (errorCode.type()) {
common/error/handler/GlobalExceptionHandler.java:307  [分岐 3]  return switch (status.value()) {
common/message/MessageHelper.java:72  [条件 3 つ]  if (value instanceof Long || value instanceof Integer || value instanc
common/model/value/BoundedText.java:32  [条件 3 つ]  return text != null && !text.isBlank() && length(text) <= maxLength;
common/paging/PageRequest.java:39  [条件 3 つ]  if (page < 1 || size < 1 || size > MAX_SIZE) {
common/security/CurrentOperator.java:34  [stream 2 段]  var name = Stream.of(jwt.getClaimAsString("family_name"), jwt.getClaim
config/security/AdminOperationPermissions.java:113  [stream 2 段]  return operations.stream()
config/security/PermissionConverter.java:51  [stream 2 段]  }
config/security/PermissionConverter.java:52  [stream 2 段]  return roles.stream()
module/cart/application/CartService.java:101  [stream 2 段]  var current = cart.itemsAt(now).stream()
module/cart/application/CartService.java:133  [stream 2 段]  return cartRepository.findById(cartId).map(cart -> new CartContents(ca
module/cart/domain/Cart.java:146  [stream 2 段]  return itemsAt(now).stream()
module/cart/domain/Cart.java:227  [分岐 3]  if (index >= 0) {
module/cart/infrastructure/directory/CatalogApiDirectory.java:33  [条件 3 つ]  return variant != null && variant.visible() && !variant.salesSuspended
module/cart/presentation/CartController.java:117  [stream 2 段]  return ResponseEntity.ok(new MergeCartResult(
module/catalog/domain/product/Product.java:557  [stream 2 段]  return items.stream()
module/catalog/domain/product/vo/CodeFormat.java:32  [条件 3 つ]  return value != null && value.length() <= MAX_LENGTH && PATTERN.matche
module/catalog/domain/product/vo/PublicationPeriod.java:32  [条件 3 つ]  if (publishStart != null && publishEnd != null && !publishStart.isBefo
module/catalog/domain/product/vo/PublicationPeriod.java:55  [条件 4 つ]  return (publishStart == null || !instant.isBefore(publishStart))
module/catalog/infrastructure/persistence/JooqOrderableVariantQueryRepository.java:61  [stream 2 段]  var imageUrls = storeImageUrls.ofImages(
module/catalog/infrastructure/persistence/JooqStoreProductQueryRepository.java:134  [分岐 3]  return switch (sort == null ? StoreProductSort.NEWEST : sort) {
module/catalog/presentation/ProductMapper.java:118  [stream 2 段]  return new ProductDetail(
module/order/domain/order/Order.java:161  [条件 3 つ]  if (email == null || email.length() > EMAIL_MAX_LENGTH || !EMAIL.match
module/order/domain/quote/Quote.java:34  [集計]  var itemsTotal = purchasable.stream().map(QuoteItem::subtotal).reduce(
module/order/domain/quote/Quote.java:45  [条件 3 つ]  var orderable = !items.isEmpty() && !missingVariant && purchasable.siz
module/order/infrastructure/directory/CartApiDirectory.java:28  [stream 2 段]  return cartContentsApi.find(cartId).map(contents -> new CartSnapshot(c
module/storefront/application/StorefrontService.java:118  [集計]  var inStock = stockDirectory.inStock(page.items().stream()
module/storefront/infrastructure/directory/CatalogApiDirectory.java:89  [分岐 3]  return switch (sort) {
module/storefront/infrastructure/directory/StockApiDirectory.java:35  [stream 2 段]  return stockAvailabilityApi.availabilityOf(variantIds).entrySet().stre
module/storefront/presentation/StorefrontMapper.java:45  [分岐 3]  return switch (sort) {
module/storefront/presentation/StorefrontMapper.java:74  [stream 2 段]  return new ProductDetail()
module/tax/api/TaxRateApi.java:47  [集計]  return queryService.findTaxCategory(taxCategoryId).flatMap(view -> toS
module/tax/api/TaxRateSchedule.java:28  [stream 2 段]  return rates.stream().filter(rate -> rate.contains(instant)).findFirst
module/tax/application/TaxCategoryQueryService.java:79  [stream 2 段]  var current = view.rates().stream()
module/tax/domain/taxcategory/TaxCategory.java:150  [条件 3 つ]  if (draft.id() != null && (!currentById.containsKey(draft.id()) || !se
module/tax/domain/taxcategory/TaxCategory.java:199  [条件 4 つ]  if ((fromChanged && isNotFuture(from, now)) || (toChanged && isNotFutu
module/tax/domain/taxcategory/vo/EffectivePeriod.java:26  [条件 3 つ]  if (effectiveFrom != null && effectiveTo != null && !effectiveFrom.isB
module/tax/domain/taxcategory/vo/RatePercent.java:37  [条件 3 つ]  if (normalized.signum() < 0 || normalized.compareTo(UPPER_BOUND) >= 0 
module/tax/infrastructure/persistence/JooqTaxCategoryQueryRepository.java:72  [集計]  }
```

## 結果（T5802）

一覧の 40 か所のうち 20 か所に書いた。残りの 20 か所は、Javadoc がすでに結果を説明している 1 行のメソッド、1 段の変換を並べただけのもの、すでにコメントのあるものとして書かなかった（各 commit に理由を書いた）。意味が分からず利用者に聞く所はなかった。
