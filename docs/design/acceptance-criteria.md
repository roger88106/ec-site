# AC 一覧

全ての spec の受け入れ基準（AC）の、今の状態とテストの一覧。AC の本文は各 spec にある。後の spec で変更した AC は、「今の内容」の列に、内容が書いてある文書を示す。

| 列 | 内容 | 更新方法 |
|----|------|--------|
| 要約 | spec の AC の定義の最初の文（40 字まで） | 再生成する |
| 状態 | 有効（spec のとおり）、変更（後の spec か設計の文書で内容が変わった）、廃止（今は成り立たない） | 手動で書く |
| 元の spec | AC を定義した spec | 再生成する |
| 変えた spec | 変更・廃止にした spec。有効なら「—」 | 手動で書く |
| 今の内容 | 今の内容が書いてある所。有効なら「spec」、変更なら設計の文書か後の spec へのリンク | 手動で書く |
| テスト | AC をテスト名に持つテストのファイル（3 つまで）。テストがなく除外の一覧にあれば「除外」、どちらもなければ「なし」 | 再生成する |

- CI の検査 `ac-check`（[CI の検査](../guides/ci.md)）がこの一覧を読む。次のときに失敗する。
  - spec と一覧の AC が食い違う。
  - 状態が有効・変更・廃止のどれでもない。
  - 表が、再生成した表と異なる。
  - 廃止の AC をテストが参照している。
  - 有効・変更の AC にテストも除外もない（CI でだけ失敗し、手元は警告）。
- テストや spec を変えて表が古くなったら、ルートで `node scripts/ci/ac-check.mjs --write` を実行して再生成し、差分を確認する。手動で書く列は保持される。
- AC を追加・変更したときの更新の手順は、[開発の進め方](../rules/workflow.md) 第 1.5 節。

<!-- generated:ac-list -->
| AC | 要約 | 状態 | 元の spec | 変えた spec | 今の内容 | テスト |
|----|------|------|-----------|-------------|----------|--------|
| AC-1 | 税区分を名称で登録できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `JooqTaxCategoryRepositoryTest`、`TaxCategoryApiTest`、`TaxCategoryListPage.test.tsx` ほか 1 |
| AC-2 | 税率は、率（%）、適用開始日時、適用終了日時を持つ。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `EffectivePeriodTest`、`TaxCategoryApiTest` |
| AC-3 | 税区分の税率は、一覧としてまとめて登録・更新する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `TaxCategoryApiTest`、`TaxCategoryTest`、`TaxRatesPage.test.tsx` |
| AC-4 | 期間限定の税率に切り替え、元に戻せる（日時は全て日本時間。BR-3）。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `TaxCategoryApiTest`、`TaxCategoryTest`、`TaxRateApiTest` ほか 1 |
| AC-5 | 過去の根拠を変えない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `EffectivePeriodTest`、`TaxCategoryApiTest`、`TaxCategoryTest` ほか 1 |
| AC-6 | 現時点で有効な税率を持たない税区分は、商品に割り当てられない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest` |
| AC-7 | 税率は 0% 以上 100% 未満。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `RatePercentTest`、`TaxCategoryApiTest` |
| AC-8 | カテゴリを、名称と親カテゴリ（任意）で登録できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `CategoryApiTest`、`CategoryPage.test.tsx`、`CategoryTest` |
| AC-9 | 階層は最大 3 階層。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `CategoryApiTest`、`CategoryPage.test.tsx`、`CategoryTest` |
| AC-10 | 同じ親の下で名称は重複できない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `CategoryApiTest`、`CategoryPage.test.tsx` |
| AC-11 | 名称と、兄弟カテゴリ間の並び順を変更できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `CategoryApiTest`、`CategoryPage.test.tsx`、`CategoryTest` |
| AC-12 | 子カテゴリを持つ、または商品が所属しているカテゴリは削除できない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `CategoryApiTest`、`CategoryPage.test.tsx`、`JooqCategoryRepositoryTest` ほか 1 |
| AC-13 | 商品品番、商品名、説明、税区分、税抜価格、所属カテゴリ（0 個以上、複数可）、バ… | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductTest` |
| AC-14 | 商品品番は全商品で一意。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `JooqProductRepositoryTest`、`ProductApiTest`、`ProductConcurrencyTest` |
| AC-15 | 商品品番は半角の英大文字・数字とハイフンで 1〜32 文字。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductCodeTest`、`ProductCreatePage.test.tsx` ほか 1 |
| AC-16 | 税区分は必須。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest` |
| AC-17 | 登録直後の商品は非公開。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductTest` |
| AC-18 | 商品名、説明、税区分、税抜価格、所属カテゴリは変更できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` |
| AC-19 | 税区分を変更すると、税込価格が変更後の税率で計算される。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx` |
| AC-20 | バリアント品番と軸の値（例：色＝白、サイズ＝M）を指定してバリアントを登録できる… | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` |
| AC-21 | バリアント品番は全バリアントで一意。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `JooqProductRepositoryTest`、`ProductApiTest`、`ProductEditPage.test.tsx` ほか 2 |
| AC-22 | 商品に軸があれば、バリアントは全ての軸の値を持つ。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` |
| AC-23 | 同じ商品の中で、軸の値の組み合わせは重複できない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductTest` |
| AC-24 | 軸を持たない商品のバリアントは 1 つだけ。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductActionsTest`、`ProductApiTest`、`ProductEditPage.test.tsx` ほか 1 |
| AC-25 | 税抜価格は 1 円以上の整数。 | 変更 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | [019](../specs/archive/mvp/019-mvp-completion/spec.md) | [商品カタログモジュール](modules/catalog.md)（商品（集約）。上限 9,999,999 円） | `PriceExclTaxTest`、`ProductApiTest`、`ProductTest` |
| AC-26 | 価格の変更は C 側に反映される（NFR-1）。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`StorefrontFreshnessTest` |
| AC-27 | バリアントの表示順を変更できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` |
| AC-28 | バリアントを販売停止・解除できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` ほか 3 |
| AC-29 | 公開にすると、C 側の一覧・検索・詳細に表示される。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` ほか 3 |
| AC-30 | 非公開にすると、C 側に表示されない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` ほか 2 |
| AC-31 | バリアントのない商品は公開できない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductTest` |
| AC-32 | 状態は「非公開」「公開」「販売終了」。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductTest`、`StorefrontApiTest` |
| AC-33 | C 側に一度も表示されていない商品は削除できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductActionsTest`、`ProductApiTest`、`ProductEditPage.test.tsx` ほか 1 |
| AC-34 | 公開開始日時と公開終了日時（いずれも任意）を設定できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` |
| AC-35 | 公開状態でも、開始日時より前と終了日時以降は C 側に表示されない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductTest`、`StorefrontApiTest`、`publish-schedule.spec.ts` |
| AC-36 | 終了日時は開始日時より後。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx`、`ProductTest` |
| AC-37 | 商品に画像を複数登録し、並び順を変えられる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `MediaApiTest`、`ProductEditPage.test.tsx`、`ProductImageApiTest` ほか 3 |
| AC-38 | バリアントに画像を 1 枚指定できる（任意）。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `OrderCatalogApiTest`、`ProductEditPage.test.tsx`、`ProductImageApiTest` ほか 3 |
| AC-39 | 形式は JPEG、PNG、WebP（US-13 で、AC-67 の形式を追加する… | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `MediaApiTest`、`ProductEditPage.test.tsx`、`ProductImageApiTest` ほか 1 |
| AC-40 | 1 商品あたり最大 20 枚。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductEditPage.test.tsx`、`ProductImageApiTest`、`ProductTest` |
| AC-41 | 新しいバリアントの在庫数は 0。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `CatalogEventHandlerTest`、`InventoryApiTest` |
| AC-42 | 在庫数は、増減量（例：+10、-3）と理由で調整する。 | 変更 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | [019](../specs/archive/mvp/019-mvp-completion/spec.md) | [在庫モジュール](modules/inventory.md)（運営者の調整の上限） | `InventoryApiTest`、`JooqStockItemRepositoryFailureTest`、`JooqStockItemRepositoryTest` ほか 2 |
| AC-43 | 調整後に負になる調整は拒否される。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `InventoryApiTest`、`JooqStockItemRepositoryFailureTest`、`JooqStockItemRepositoryTest` ほか 1 |
| AC-44 | 同じバリアントへの調整が同時に実行されても、全て反映される。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `JooqStockItemRepositoryTest` |
| AC-45 | 調整履歴（日時、増減量、理由、調整後の在庫数）を新しい順に参照できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `InventoryApiTest`、`JooqStockItemRepositoryTest` |
| AC-46 | 理由は「入荷」「棚卸差異」「破損・紛失」「その他」。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `InventoryApiTest`、`StockAdjustmentTest`、`StockPage.test.tsx` |
| AC-47 | 状態に関係なく、全ての商品を一覧できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductListApiTest` |
| AC-48 | 商品品番・バリアント品番の前方一致、商品名の部分一致、状態、カテゴリで絞り込める… | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductListApiTest`、`ProductListPage.test.tsx`、`productFilter.test.ts` |
| AC-49 | 商品品番、商品名、状態、バリアント数、在庫数の合計を表示する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductListApiTest`、`ProductListPage.test.tsx` |
| AC-50 | ページ単位で取得できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductListApiTest` |
| AC-51 | C 側に表示される商品（BR-2）だけが含まれる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StoreCatalogApiTest`、`StorefrontApiTest` |
| AC-52 | カテゴリで絞り込める。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StoreCatalogApiTest`、`listQuery.test.ts` |
| AC-53 | 税込価格の範囲で絞り込める。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StoreCatalogApiTest`、`StorefrontApiTest`、`listQuery.test.ts` |
| AC-54 | 新着順（既定）、価格の安い順、高い順で並べ替えられる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductTest`、`StoreCatalogApiTest`、`StorefrontApiTest` ほか 2 |
| AC-55 | 商品名、代表画像、価格（BR-4）を表示する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductCard.test.tsx`、`StorefrontApiTest` |
| AC-56 | 全バリアントが在庫切れの商品も表示し、「在庫なし」と示す。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductCard.test.tsx`、`StorefrontApiTest`、`VariantPicker.test.tsx` |
| AC-57 | ページ単位で取得できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StorefrontApiTest`、`listQuery.test.ts` |
| AC-58 | 商品名または説明にキーワードを含む商品が表示される。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StoreProductSearchTest`、`StorefrontApiTest`、`publish.spec.ts` ほか 1 |
| AC-59 | 対象は C 側に表示される商品（BR-2）だけ。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StoreProductSearchTest`、`StorefrontApiTest` |
| AC-60 | 精度（表記ゆれ、類義語、関連度順）は問わない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | 除外 |
| AC-61 | ページ単位で取得できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StoreProductSearchTest`、`StorefrontApiTest`、`searchQuery.test.ts` |
| AC-62 | 商品名、説明、画像、価格（BR-4）、バリアント軸と選択肢を表示する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StorefrontApiTest`、`VariantPicker.test.tsx`、`publish.spec.ts` ほか 2 |
| AC-63 | バリアントを選ぶと、その在庫状況を表示する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StorefrontApiTest`、`publish.spec.ts`、`suspension.spec.ts` ほか 1 |
| AC-64 | 在庫状況は「在庫あり」「在庫なし」のみ。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StorefrontApiTest`、`publish.spec.ts`、`variantSelection.test.ts` |
| AC-65 | C 側に表示されない商品は、存在しない商品と同じ結果になる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StorefrontApiTest` |
| AC-66 | URL は品番が変わっても変わらない。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `StorefrontApiTest` |
| AC-67 | BMP、HEIC / HEIF など一般的な画像形式も受け付ける（対応形式は p… | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ImageFormatTest`、`MediaApiTest`、`ProductEditPage.test.tsx` ほか 3 |
| AC-68 | 全ての画像を WebP に変換して配信する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ImgproxyImageConverterFailureTest`、`ImgproxyImageConverterTest`、`MediaApiTest` ほか 1 |
| AC-69 | 変換時に位置情報などのメタデータを除去する。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ImgproxyImageConverterTest` |
| AC-70 | 変換に失敗した画像は登録されず、運営者にその旨が表示される。 | 変更 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | [017](../specs/archive/mvp/017-stability/spec.md) | [メディアモジュール](modules/media.md)（アップロードの流れ ※1）、[API の規約](../rules/api-conventions.md) 第 4 節 | `ImgproxyImageConverterTest`、`MediaApiTest`、`ProductEditPage.test.tsx` ほか 1 |
| AC-71 | C 側に一度も表示されていない商品のバリアントは削除できる。 | 有効 | [001](../specs/archive/mvp/001-catalog-and-inventory/spec.md) | — | spec | `ProductActionsTest`、`ProductApiTest`、`ProductEditPage.test.tsx` ほか 1 |
| AC-72 | C 側に表示される商品（BR-2）の、選べるバリアント（販売停止でなく、在庫あり… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CartApiTest`、`order.spec.ts` |
| AC-73 | カートを持っていなければ、最初に商品を入れたときに作られる。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [003](../specs/archive/mvp/003-auth/spec.md) | [カートモジュール](modules/cart.md)（会員のカート）、[店舗画面](apps/store.md) 第 4.5 節 | `CartTest`、`cart.test.ts`、`cartChoice.test.ts` ほか 1 |
| AC-74 | カートにあるバリアントをもう一度入れると、数量を合算する。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CartApiTest`、`CartTest` |
| AC-75 | 1 品目の数量は 1〜10。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CartApiTest`、`CartTest`、`PlaceOrderApiTest` ほか 1 |
| AC-76 | 入れた後の数量が、その時点の販売可能数（BR-11）を超える場合は拒否される。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CartApiTest`、`CartTest` |
| AC-77 | 品目ごとに商品名、軸の値、画像、単価（BR-4）、数量、小計を表示し、あわせて商… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `AmountsSummary.test.tsx`、`QuoteItemSummary.test.tsx`、`StorefrontCartTest` ほか 1 |
| AC-78 | 品目の数量を変更（AC-75、76 の範囲で）・削除できる。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CartApiTest`、`CartItemControls.test.tsx`、`CartTest` ほか 1 |
| AC-79 | 単価は常に現在の価格で表示する。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `StorefrontCartTest` |
| AC-80 | 購入できなくなった品目は、理由とともに「購入できません」と示し、合計に含めない。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CartItemControls.test.tsx`、`OrderCatalogApiTest`、`QuoteItemSummary.test.tsx` ほか 4 |
| AC-81 | 最後に変更してから 30 日たったカートは、空のカートとして扱う。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [017](../specs/archive/mvp/017-stability/spec.md) | [カートモジュール](modules/cart.md)（期限） | `CartApiTest`、`CartMergeTest`、`CartTest` ほか 1 |
| AC-82 | カートから注文手続きに進むと、全品目をカートの数量だけ 15 分間引き当てる（B… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CheckoutApiTest`、`StockReservationApiTest`、`cart.test.ts` ほか 2 |
| AC-83 | 同じ在庫への引当が同時に要求されても、在庫数を超えて引き当てない。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `StockReservationApiTest`、`stock-hold-race.spec.ts` |
| AC-84 | 引当は次のときに解除される。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [007](../specs/archive/mvp/007-payment/spec.md) | [注文モジュール](modules/order.md)（注文の成立）、[在庫モジュール](modules/inventory.md)（引当） | `CheckoutApiTest`、`CheckoutCartChangeApiTest`、`StockReservationApiTest` |
| AC-85 | 注文手続きでは、カートの内容、送料、総額、税率ごとの消費税額を確認し、メールアド… | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [003](../specs/archive/mvp/003-auth/spec.md)、[016](../specs/archive/mvp/016-master-data/spec.md) | [注文モジュール](modules/order.md)（注文（集約））、[店舗画面](apps/store.md) 第 4.8 節 | `CheckoutApiTest`、`order.spec.ts` |
| AC-86 | 入力の形式。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [016](../specs/archive/mvp/016-master-data/spec.md) | [注文モジュール](modules/order.md)（注文の成立）、[地域モジュール](modules/region.md) | `OrderForm.test.tsx`、`OrderTest`、`PlaceOrderApiTest` ほか 4 |
| AC-87 | 注文が成立すると、全品目の在庫数を注文数だけ減らし、引当を解除する。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [007](../specs/archive/mvp/007-payment/spec.md) | [注文モジュール](modules/order.md)（注文の成立）、[在庫モジュール](modules/inventory.md)（操作） | `OrderForm.test.tsx`、`PlaceOrderApiTest`、`PlaceOrderConcurrencyTest` ほか 2 |
| AC-88 | 空のカート、または購入できない品目（AC-80）を含むカートは注文できない。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CheckoutApiTest`、`OrderTest` |
| AC-89 | 確認した総額と、注文時点で計算した総額が異なる場合（その間に価格や税率が変わった… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `PlaceOrderApiTest`、`checkout.test.ts`、`placeOrderErrors.test.ts` |
| AC-90 | 注文が成立すると注文番号を採番し、カートを空にする。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [007](../specs/archive/mvp/007-payment/spec.md) | [注文モジュール](modules/order.md)（状態遷移の後の箇条）、[カートモジュール](modules/cart.md)（カート） | `PlaceOrderApiTest` |
| AC-91 | 同じ注文の送信が繰り返されても（二度押し、通信の再送）、注文は 1 件だけ作られ… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `JooqOrderRepositoryTest`、`PlaceOrderApiTest`、`PlaceOrderConcurrencyTest` |
| AC-92 | 注文には、注文時点の商品品番、バリアント品番、商品名、軸の値、税抜単価、税率、税… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `OrderCatalogApiTest`、`OrderTest`、`PlaceOrderApiTest` |
| AC-93 | 注文の直後に、注文番号、注文日時、明細、金額の内訳（BR-7、8）、お届け先、状… | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [007](../specs/archive/mvp/007-payment/spec.md) | [店舗画面](apps/store.md) 第 4.7 節 | `PlaceOrderApiTest`、`order.spec.ts` |
| AC-94 | 注文の URL を知っていれば、後から同じ内容を見られる。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [003](../specs/archive/mvp/003-auth/spec.md) | [注文モジュール](modules/order.md)（注文（集約）の操作の表） | `PlaceOrderApiTest` |
| AC-95 | 注文の URL の画面から、「受付」の注文のキャンセルを申請できる。 | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [003](../specs/archive/mvp/003-auth/spec.md) | [注文モジュール](modules/order.md)（注文（集約）の操作の表、状態遷移） | `CancellationApiTest`、`OrderCancellationTest`、`order.spec.ts` ほか 2 |
| AC-96 | キャンセルの申請は、1 つの注文につき 1 回だけ。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CancellationApiTest`、`OrderCancellationTest`、`StorefrontShopTest` ほか 2 |
| AC-97 | 全ての注文を、注文日時の新しい順に一覧できる。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `AdminOrderApiTest`、`OrderListPage.test.tsx` |
| AC-98 | 注文番号の前方一致、状態、注文日の範囲（BR-3）で絞り込める。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `AdminOrderApiTest`、`OrderListPage.test.tsx`、`orderFilter.test.ts` |
| AC-99 | ページ単位で取得できる。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `AdminOrderApiTest` |
| AC-100 | 注文番号、注文日時、状態、明細（AC-92 の写しと数量、小計）、金額の内訳（B… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `AdminOrderApiTest`、`OrderDetailPage.test.tsx`、`labels.test.ts` |
| AC-101 | 「キャンセル申請中」の注文の申請を承認すると、キャンセルになり、明細の数量だけ在… | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [007](../specs/archive/mvp/007-payment/spec.md)、[009](../specs/archive/mvp/009-shipping/spec.md) | [注文モジュール](modules/order.md)（状態遷移）、[決済モジュール](modules/payment.md)（与信の取消） | `CancellationApiTest`、`OrderCancellationTest`、`OrderDetailPage.test.tsx` ほか 1 |
| AC-102 | 申請を却下すると「受付」に戻る。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CancellationApiTest`、`OrderCancellationTest`、`OrderDetailPage.test.tsx` |
| AC-103 | 運営者の判断で、「受付」または「キャンセル申請中」の注文を、申請の承認によらずに… | 変更 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | [007](../specs/archive/mvp/007-payment/spec.md)、[009](../specs/archive/mvp/009-shipping/spec.md) | [注文モジュール](modules/order.md)（状態遷移） | `CancellationApiTest`、`OrderCancellationTest`、`OrderDetailPage.test.tsx` |
| AC-104 | キャンセルと在庫の戻しは、両方とも実行されるか、どちらも実行されない。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CancellationAtomicityTest` |
| AC-105 | キャンセル済みの注文へのキャンセル・承認は、成功し、在庫は戻さない（consti… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CancellationApiTest`、`OrderCancellationTest`、`OrderDetailPage.test.tsx` |
| AC-106 | キャンセル済みの注文も、一覧・詳細に表示される。 | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `AdminOrderApiTest` |
| AC-107 | 注文による在庫数の減算と、キャンセルによる戻しは、在庫の調整履歴（AC-45）に… | 有効 | [002](../specs/archive/mvp/002-cart-and-order/spec.md) | — | spec | `CancellationApiTest`、`InventoryApiTest`、`PlaceOrderApiTest` ほか 4 |
| AC-108 | 店舗から会員登録の画面に遷移し、メールアドレス、パスワード、氏名で登録できる。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest`、`index.test.ts`、`member.spec.ts` |
| AC-109 | 登録済みのメールアドレスでは登録できない。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-110 | パスワードの条件（長さなど）を満たさない登録は拒否される。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-111 | 登録すると確認メールが届く。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest`、`member.spec.ts` |
| AC-112 | メールアドレスの確認が済むと、ログインした状態で店舗（会員登録を始めた画面）に戻… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest`、`member.spec.ts` |
| AC-113 | 店舗からログインの画面に遷移し、メールアドレスとパスワードでログインできる。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `LoginLinks.test.tsx`、`index.test.ts`、`login.test.ts` ほか 1 |
| AC-114 | ログインしている間、店舗の画面に会員の氏名とログアウトの操作を表示する。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `index.test.ts`、`login.test.ts`、`member.spec.ts` |
| AC-115 | ログアウトすると、店舗でも IdP でもログインしていない状態になる。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `index.test.ts`、`member.spec.ts` |
| AC-116 | パスワードを続けて間違えると、そのアカウントは一定時間ログインできなくなる。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-117 | ログインの状態は、最後に操作してから一定時間（plan で決める）続く。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest`、`index.test.ts` |
| AC-118 | ログインの画面からメールアドレスを入力して、パスワードの再設定を申し込める。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-119 | ログインしている会員は、店舗から IdP のアカウントの画面に遷移し、パスワード… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `SiteHeader.test.tsx` |
| AC-120 | ログインしていなくても、これまでどおりカートを使える（US-14、15）。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberCartApiTest`、`cart.test.ts`、`cartChoice.test.ts` |
| AC-121 | ログインしている間は、会員のカートを使う。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `JooqCartRepositoryTest`、`KeycloakIntegrationTest`、`MemberCartApiTest` ほか 3 |
| AC-122 | ログインしたとき、そのブラウザーのカート（ログインせずに使っていたもの）に品目が… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `CartMergeTest`、`MemberCartApiTest`、`cart.test.ts` ほか 4 |
| AC-123 | 移した結果が上限（AC-75）を超える場合は、数量を 10 に切り詰め、30 品… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `CartMergeNoticeView.test.tsx`、`CartMergeTest`、`MemberCartApiTest` ほか 2 |
| AC-124 | ログアウトすると、そのブラウザーのカートは空に見える。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `cart.test.ts`、`member.spec.ts` |
| AC-125 | 注文手続きに進むには、ログインが必要。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberOrderApiTest`、`cart.test.ts`、`member.spec.ts` |
| AC-126 | 注文手続きは、それを始めた会員だけが操作・参照できる。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberOrderApiTest` |
| AC-127 | 注文の連絡先のメールアドレスには、会員のメールアドレス（確認済み）を使い、注文手… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberOrderApiTest`、`OrderForm.test.tsx`、`member.spec.ts` ほか 1 |
| AC-128 | 注文は、注文した会員に結び付く。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberOrderApiTest` |
| AC-129 | ログインしている会員は、自分の注文を注文日時の新しい順に一覧できる。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberOrderApiTest`、`orderListQuery.test.ts` |
| AC-130 | 一覧から注文の詳細（AC-93 と同じ内容）に遷移し、キャンセルを申請できる（A… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `MemberOrderApiTest` |
| AC-131 | 注文の詳細の参照とキャンセルの申請は、注文した会員だけができる。 | 変更 | [003](../specs/archive/mvp/003-auth/spec.md) | [015](../specs/archive/mvp/015-security/spec.md) | [注文モジュール](modules/order.md)（注文（集約）の操作の表：ログインしていなければ 401） | `MemberOrderApiTest` |
| AC-132 | 管理画面は、ログインしなければ何も表示しない。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `AuthGate.test.tsx`、`login.test.ts` |
| AC-133 | 運営者のアカウントは、会員のアカウントとは別にある（constitution 1… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `KeycloakIntegrationTest` |
| AC-134 | 運営者は自分で登録できない。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-135 | 管理画面に運営者の氏名とログアウトの操作を表示する。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `App.test.tsx`、`login.test.ts` |
| AC-136 | 管理 API の各操作は、運営者がその操作の権限（spec 001、002 の各… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `AuthorizationTest`、`permission.spec.ts` |
| AC-137 | 認証のない要求（なし、無効、期限切れ）は、管理 API と、店舗 API の会員… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `AuthorizationTest`、`KeycloakIntegrationTest`、`MemberCartApiTest` ほか 1 |
| AC-138 | 会員のアカウントで得た認証では、管理 API を使えない。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `AuthorizationTest`、`KeycloakIntegrationTest` |
| AC-139 | 運営者の権限は、IdP で職務（BR-12）を割り当てて与える。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `KeycloakIntegrationTest` |
| AC-140 | 職務の付け外しと、アカウントの無効化は、一定時間（plan で決める）のうちに管… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `KeycloakIntegrationTest`、`RealmSettingsContractTest` |
| AC-141 | 管理画面で権限のない操作をすると、「権限がない」と表示する。 | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `StockPage.test.tsx`、`errors.test.ts`、`permission.spec.ts` |
| AC-142 | 在庫の調整（AC-45 の履歴）、注文のキャンセルの申請の承認・却下、運営者の判… | 有効 | [003](../specs/archive/mvp/003-auth/spec.md) | — | spec | `CancellationApiTest`、`InventoryApiTest`、`OrderDetailPage.test.tsx` ほか 1 |
| AC-143 | 注文手続きで注文すると、注文は「支払待ち」で作られ、決済サービスの支払画面に移る… | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`OrderPaymentTest`、`OrderTest` ほか 2 |
| AC-144 | 「支払待ち」の注文を作ると、全品目の在庫数を減らす。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`PlaceOrderApiTest` |
| AC-145 | カード番号は決済サービスの画面で入力する。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `payment.spec.ts` |
| AC-146 | 決済サービスで総額の与信が成功すると、注文は「受付」になる。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`OrderPaymentTest`、`PaymentApiTest` ほか 2 |
| AC-147 | 与信が成功すると、注文した品目（バリアント）をカートから除く。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `CartTest`、`OrderPaymentApiTest`、`payment.spec.ts` |
| AC-148 | カードが拒否されたときは、決済サービスの画面で別のカードを入力して再試行できる。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `payment.spec.ts` |
| AC-149 | 支払を終える・やめると、決済サービスの画面から注文の画面に戻る。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `payment.spec.ts` |
| AC-150 | 戻った時点で EC が与信の結果を受信していなければ、注文の画面に「支払の確認中… | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `PaymentSection.test.tsx`、`orderState.test.ts` |
| AC-151 | 支払の期限の前なら、「支払待ち」の注文画面から、支払画面に再び遷移して支払える。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`PaymentSection.test.tsx`、`order.test.ts` ほか 2 |
| AC-152 | 同じ注文で支払画面に何度移っても、与信は 1 回だけ取られる。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`PaymentApiTest` |
| AC-153 | 支払の期限（BR-16）までに与信が成功しなかった注文は、キャンセルになり、明細… | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`OrderPaymentTest`、`PaymentExpiryJobTest` ほか 2 |
| AC-154 | 期限切れのキャンセルは、期限から一定時間（plan で決める）のうちに行われる。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`PaymentExpiryJobTest` |
| AC-155 | 期限切れのキャンセルの理由は「支払期限切れ」とし、注文の詳細（店舗・管理画面）に… | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `CancellationSection.test.tsx`、`OrderDetailPage.test.tsx`、`OrderPaymentApiTest` ほか 3 |
| AC-156 | 期限の直前に与信が成功した注文は、期限切れでキャンセルしない。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `PaymentExpiryJobTest`、`PaymentTest` |
| AC-157 | 期限切れでキャンセルした後に与信の成功を受信したら、その与信を取り消す。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`PaymentTest`、`PaymentVoidTest` |
| AC-158 | 「支払待ち」の注文は、消費者のキャンセルの申請も、運営者のキャンセルもできない。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`OrderPaymentApiTest`、`orderState.test.ts` ほか 1 |
| AC-159 | 店舗の注文一覧と注文詳細に、「支払待ち」の注文の支払の期限を表示する。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `PaymentSection.test.tsx`、`orderState.test.ts` |
| AC-160 | 管理画面の注文一覧と注文詳細に、支払の状態（BR-15）を表示する。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`OrderListPage.test.tsx`、`OrderPaymentApiTest` ほか 1 |
| AC-161 | 管理画面の注文の一覧で、状態「支払待ち」で絞り込める。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderListPage.test.tsx`、`OrderPaymentApiTest` |
| AC-162 | 与信済みの注文がキャンセルになると、与信を取り消す。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest`、`PaymentTest`、`PaymentVoidTest` ほか 1 |
| AC-163 | キャンセルは、与信の取消の結果を待たずに成立する。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderPaymentApiTest` |
| AC-164 | 与信の取消に失敗したら、取り消せるまで再試行する。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `PaymentVoidTest` |
| AC-165 | 与信の取消が完了するまで、管理画面の注文詳細に「与信の取消待ち」と表示する。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`OrderPaymentApiTest`、`PaymentVoidTest` |
| AC-166 | 同じ与信の取消が重ねて行われても、成功し、何も変わらない。 | 有効 | [007](../specs/archive/mvp/007-payment/spec.md) | — | spec | `PaymentTest`、`PaymentVoidTest` |
| AC-167 | 運営者は、「受付」の注文詳細で、配送業者と送り状番号を入力して出荷を登録できる。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`OrderShipmentTest`、`ShipmentApiTest` ほか 1 |
| AC-168 | 出荷を登録すると、総額で売上を確定し、注文は「出荷済み」になる。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderShipmentTest`、`PaymentCaptureTest`、`ShipmentApiTest` |
| AC-169 | 売上確定に失敗したら、出荷は登録されず、注文は「受付」のまま。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`PaymentCaptureTest`、`ShipmentApiTest` |
| AC-170 | 「受付」以外の注文は出荷を登録できない。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderShipmentTest`、`ShipmentApiTest` |
| AC-171 | 「出荷済み」の注文は、消費者はキャンセルを申請できない。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderShipmentTest`、`ReturnedOrderCancellationApiTest`、`StoreOrderShipmentApiTest` ほか 2 |
| AC-172 | 同じ注文に出荷を重複して登録しても、売上確定は 1 回だけ実行する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `PaymentCaptureTest`、`ShipmentApiTest` |
| AC-173 | 配送業者は決まった一覧（plan で決める）から選ぶ。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `ShipmentApiTest`、`TrackingNumberTest` |
| AC-174 | 配送状態（BR-19）は、物流サービスからの通知で遷移する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `CarrierWebhookTest`、`JooqShipmentRepositoryTest`、`MockCarrierTest` ほか 3 |
| AC-175 | 同じ通知が重複して届いても、1 回だけ反映する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `CarrierWebhookTest`、`ShipmentTest` |
| AC-176 | 通知の順序が入れ替わっても、配送状態は戻らない（例：「配達完了」の後に「配送中」… | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `CarrierWebhookTest`、`ShipmentTest` |
| AC-177 | 途中の状態の通知が届かなくても、後の状態に遷移する（例：「出荷済み」から「配達完… | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `CarrierWebhookTest`、`ShipmentTest` |
| AC-178 | 署名を確認できない通知と、登録済みのどの出荷にも一致しない通知は、処理しない。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `CarrierWebhookTest`、`MockCarrierTest` |
| AC-179 | 店舗の注文詳細に、配送状態、配送業者、送り状番号を表示する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderStatusBadge.test.tsx`、`ShippingApiTest`、`StoreOrderShipmentApiTest` ほか 2 |
| AC-180 | 店舗の注文一覧で、出荷後の注文は、注文状態の代わりに配送状態を表示する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderStatusBadge.test.tsx`、`StoreOrderShipmentApiTest`、`labels.test.ts` ほか 1 |
| AC-181 | 管理画面の注文詳細に、配送状態、配送業者、送り状番号、出荷日時を表示する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`OrderDetailPage.test.tsx` |
| AC-182 | 管理画面の注文一覧で、状態「出荷済み」で絞り込める。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`OrderListPage.test.tsx` |
| AC-183 | 出荷を登録すると、注文のメールアドレスに、注文番号、配送業者、送り状番号を書いた… | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `ShipmentMailTest`、`shipping.spec.ts` |
| AC-184 | 出荷登録は、メール送信の結果を待たない。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `ShipmentMailTest` |
| AC-185 | 再試行しても、1 つの出荷につき出荷通知メールは 1 通だけ届く。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `ShipmentMailTest` |
| AC-186 | 管理画面の注文一覧と注文詳細で、与信期限（BR-20）が近い「受付」の注文に、期… | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`OrderDetailPage.test.tsx`、`OrderListPage.test.tsx` ほか 1 |
| AC-187 | 管理画面の注文一覧で、与信期限が近い注文を絞り込める。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`OrderListPage.test.tsx`、`PaymentAuthorizationDeadlineTest` ほか 1 |
| AC-188 | 与信期限を過ぎた注文を出荷すると、売上確定に失敗する（AC-169）。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`PaymentAuthorizationDeadlineTest`、`PaymentRefundTest` ほか 1 |
| AC-189 | 管理画面の注文一覧で、配送状態「返送」の注文を絞り込める。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`JooqShipmentRepositoryTest`、`OrderListPage.test.tsx` ほか 3 |
| AC-190 | 運営者は、配送状態が「返送」の注文を、理由を入力してキャンセルできる。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`OrderShipmentTest`、`ReturnedOrderCancellationApiTest` ほか 1 |
| AC-191 | 返送された注文をキャンセルするとき、運営者は在庫に戻すかを選ぶ。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`OrderShipmentTest`、`ReturnedOrderCancellationApiTest` ほか 1 |
| AC-192 | 返送された注文をキャンセルすると、総額（送料を含む）を返金する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `PaymentRefundTest`、`ReturnedOrderCancellationApiTest`、`shipping.spec.ts` |
| AC-193 | キャンセルは、返金の結果を待たずに成立する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `PaymentRefundTest`、`ReturnedOrderCancellationApiTest` |
| AC-194 | 返金が済むまで、管理画面の注文詳細に「返金待ち」と表示する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `AdminOrderShipmentViewApiTest`、`OrderDetailPage.test.tsx`、`PaymentRefundTest` ほか 1 |
| AC-195 | 同じ注文の返金が重複して実行されても、返金は 1 回だけ。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `PaymentRefundTest` |
| AC-196 | キャンセルの後、店舗の注文一覧と注文詳細は「キャンセル」を表示する。 | 有効 | [009](../specs/archive/mvp/009-shipping/spec.md) | — | spec | `StoreOrderShipmentApiTest` |
| AC-197 | 運営者は、期間と集計の粒度（週、月、四半期、年）を選び、粒度ごとの売上額と販売数… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest`、`ReportPermissionApiTest`、`SalesTrendPage.test.tsx` ほか 3 |
| AC-198 | 同じ数字を、粒度ごとの行の表でも見られる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest` |
| AC-199 | 商品を 1 つ選ぶと、その商品だけの推移を見られる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest`、`SalesTrendPage.test.tsx`、`SalesTrendQueryTest` ほか 1 |
| AC-200 | 売上の数え方（BR-25）を画面で切り替えられる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest`、`SalesTrendPage.test.tsx`、`SalesTrendQueryTest` ほか 1 |
| AC-201 | 売れなかった区切りも、0 の行として示す。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `SalesTrendPage.test.tsx`、`SalesTrendQueryTest` |
| AC-202 | 運営者は、商品を 1 つと期間を選び、バリエーション（軸の値の組み合わせ）ごとの… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest`、`VariantSalesPage.test.tsx`、`VariantSalesQueryTest` ほか 1 |
| AC-203 | 同じ数字を表でも見られる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest`、`VariantSalesPage.test.tsx`、`VariantSalesQueryTest` |
| AC-204 | 期間に売れなかったバリエーションも、0 として示す。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportApiTest`、`VariantSalesPage.test.tsx`、`VariantSalesQueryTest` |
| AC-205 | 運営者は、期間を選び、お届け先の都道府県ごとの売上額と注文件数を、グラフと表で見… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`CustomerBreakdownQueryTest`、`ReportApiTest` ほか 1 |
| AC-206 | 同じ期間の、年齢層（BR-27）ごとの売上額と注文件数を、グラフと表で見られる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`CustomerBreakdownQueryTest`、`ReportApiTest` ほか 2 |
| AC-207 | 商品を 1 つ選ぶと、その商品を含む注文だけを数える。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`CustomerBreakdownQueryTest`、`ReportApiTest` ほか 1 |
| AC-208 | 生年月日の分からない会員の注文は、年齢層「不明」として数える。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `AgeGroupTest`、`CustomerBreakdownPage.test.tsx`、`CustomerBreakdownQueryTest` ほか 2 |
| AC-209 | IdP の会員登録の画面で、生年月日を入れられる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `RealmSettingsContractTest`、`report.spec.ts` |
| AC-210 | 生年月日は任意とする。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-211 | 会員は、IdP のアカウントの画面で、後から生年月日を入れる・修正することができ… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `RealmSettingsContractTest` |
| AC-212 | 注文の成立のときに、その時点の年齢を注文に記録する。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `BuyerProfileTest`、`PlaceOrderApiTest`、`RealmSettingsContractTest` |
| AC-213 | 未来の日付と、明らかにありえない日付（120 年より前など）は受け付けない。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `BuyerProfileTest`、`PlaceOrderApiTest` |
| AC-214 | 運営者は、権限を持つレポートだけを、管理画面のメニューで見て開ける。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportPermission.test.tsx`、`ReportPermissionApiTest`、`report.spec.ts` |
| AC-215 | 権限のないレポートの API を呼ぶと 403 になる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportPermissionApiTest` |
| AC-216 | 権限はレポートごとに分ける。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `AdminApiPermissionContractTest` |
| AC-217 | 役割ごとの権限は BR-28 のとおり。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportPermissionApiTest`、`report.spec.ts` |
| AC-218 | 運営者は、表示しているレポートの表を CSV で取り出せる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`SalesTrendPage.test.tsx`、`VariantSalesPage.test.tsx` ほか 1 |
| AC-219 | 同じ期間の、性別（BR-29）ごとの売上額と注文件数を、グラフと表で見られる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`CustomerBreakdownQueryTest`、`ReportApiTest` ほか 3 |
| AC-220 | 都道府県は、全国地方公共団体コードの順（北海道から沖縄県）に並べる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`CustomerBreakdownQueryTest`、`ReportApiTest` ほか 1 |
| AC-221 | IdP の会員登録の画面で性別（BR-29）を選べる。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `RealmSettingsContractTest`、`report.spec.ts` |
| AC-222 | 注文の成立のときに、その時点の性別を注文に記録する。 | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `BuyerProfileTest`、`PlaceOrderApiTest`、`RealmSettingsContractTest` ほか 1 |
| AC-223 | 状態によらず、全ての注文をレポートのデータに集める（支払待ち、支払の期限切れ、キ… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportCollectionTest` |
| AC-224 | 注文の状態と配送状態が変わるたびに、何が・いつ・どの状態からどの状態に変わったか… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportCollectionTest` |
| AC-225 | 集めたデータのうち、どの注文を数えるかは、レポートごとに決める（BR-25 など… | 有効 | [010](../specs/archive/mvp/010-sales-report/spec.md) | — | spec | `ReportCollectionTest` |
| AC-226 | 決済と物流に、それぞれ mock フラグがある。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ExternalServiceSelectionTest` |
| AC-227 | 決済のフラグを立てず、Stripe の設定（鍵）が足りなければ、バックエンドは起… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ExternalServiceSelectionTest` |
| AC-228 | 物流のフラグを立てなければ、バックエンドは起動に失敗する。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ExternalServiceSelectionTest` |
| AC-229 | mock の支払画面と、mock の配送業者の画面（通知の操作を含む）は、そのサ… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ExternalServiceSelectionTest` |
| AC-230 | 決済・物流のサービスを選ぶ今の設定（mock か Stripe かの選択）をなく… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ExternalServiceSelectionTest` |
| AC-231 | ローカルの設定は、決済と物流のフラグを立てる。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ProfileFlagsTest` |
| AC-232 | 店舗 API の契約で会員の token が必要な操作は、全て token なし… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `StoreApiTokenContractTest`、`StoreOperationSecurityTest` |
| AC-233 | 契約で token が必要ない操作（商品の閲覧、匿名のカートなど）は、token… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `StoreApiTokenContractTest`、`StoreOperationSecurityTest` |
| AC-234 | 契約と認可の設定が食い違う（契約で token が必要なのに、token なしで… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `StoreApiTokenContractTest` |
| AC-235 | 業務ポートは、店舗 API・管理 API・決済と物流の通知のエンドポイント・mo… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `BusinessPortAccessTest` |
| AC-236 | 死活監視とメトリクスは、運用ポートだけで出す。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ManagementPortTest` |
| AC-237 | 運用ポートの番号は設定で変更でき、ローカルの既定は業務ポートと競合しない。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ManagementPortConfigTest` |
| AC-238 | 管理画面を本番モードでビルドするとき、IdP の接続先と client ID が… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `oidcEnv.test.ts` |
| AC-239 | 管理画面の開発モードとテストは、今までどおりローカルの IdP の既定値で動く。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `oidcEnv.test.ts` |
| AC-240 | 店舗画面は、公開する URL（origin）を環境変数で受け取る。 | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `config.test.ts` |
| AC-241 | 店舗画面のログインの戻り先と、ログアウトの後の戻り先は、公開する URL から作… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `index.test.ts`、`loginRedirect.test.ts` |
| AC-242 | 店舗画面の任意の設定（API の応答を再取得する間隔など）も、必須の設定と同じ … | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `config.test.ts` |
| AC-243 | ローカル環境（compose）のサービスのポートは、既定では手元のマシン（127… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | 除外 |
| AC-244 | バックエンドの本番の jarに、ローカル・E2E・Stripe の sandbo… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | 除外 |
| AC-245 | 本番の jarを、ローカルの profile を指定して起動しても、mock フ… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `ProductionJarStartupTest` |
| AC-246 | mock の決済の、テストだけで使う操作（次の操作を失敗させる、状態を削除するな… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | `MockPaymentGatewayOperationsTest` |
| AC-247 | 1 つの設定を変えるだけで、compose の全てのサービスのポートを、同じネッ… | 有効 | [015](../specs/archive/mvp/015-security/spec.md) | — | spec | 除外 |
| AC-248 | 注文手続きの画面のお届け先は、都道府県・市区町村・それより下の住所を、別の欄で入… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-249 | 市区町村の一覧は、選んだ都道府県の市区町村だけを表示する。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx`、`region.test.ts` |
| AC-250 | 都道府県と市区町村の一覧は、店舗 API のマスターデータから取得する。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx`、`StoreRegionApiTest` |
| AC-251 | 注文の確定は、選んだ都道府県に属さない市区町村、またはマスターデータにないコード… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx`、`PlaceOrderApiTest` |
| AC-252 | 注文の詳細（店舗画面・管理画面）は、お届け先を、都道府県名・市区町村名・それより… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`ShippingAddressDetails.test.tsx`、`order.spec.ts` |
| AC-253 | 店舗 API は、郵便番号から、都道府県・市区町村・町域を返す。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `StoreRegionApiTest` |
| AC-254 | 1 つの郵便番号に複数の町域があるときは、全てを返す。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `StoreRegionApiTest`、`region.test.ts` |
| AC-255 | 注文手続きの画面で郵便番号（7 桁）を入れると、都道府県と市区町村を選んだ状態に… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx`、`order.spec.ts`、`postalCodeFill.test.ts` |
| AC-256 | マスターデータにない郵便番号は、何も補わず、消費者がそのまま入力を続けられる。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx`、`StoreRegionApiTest`、`postalCodeFill.test.ts` ほか 1 |
| AC-257 | 補った後も、消費者は都道府県・市区町村・住所を変えられる。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-258 | 注文は、お届け先の都道府県と市区町村をコードで持つ。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `PlaceOrderApiTest`、`ShippingAddressTest` |
| AC-259 | 都道府県と市区町村のマスターデータは、全国の都道府県（47）と市区町村を、最初の… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `RegionApiTest` |
| AC-260 | 郵便番号のマスターデータは、構造と、開発とテストに使う一部の郵便番号だけを、最初… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `RegionApiTest` |
| AC-261 | 既にある注文は、都道府県名をコードに変換し、住所の先頭をマスターデータの市区町村… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `ShippingAreaMigrationTest` |
| AC-262 | 売上レポートの都道府県別の集計は、注文の都道府県のコードを使い、都道府県名から再… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `CustomerBreakdownPage.test.tsx`、`ReportApiTest`、`labels.test.ts` |
| AC-263 | 都道府県・市区町村・郵便番号の表は、マスターデータのほかに持たない（バックエンド… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | 除外 |
| AC-264 | 注文手続きの画面は、入力の誤り（400）の欄ごとの文を、API の応答の文のまま… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderForm.test.tsx`、`placeOrderErrors.test.ts` |
| AC-265 | 店舗 API の入力の誤りの文は、消費者が読んで修正できる文にする。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `PlaceOrderApiTest` |
| AC-266 | エラーの文に上限・桁数などの値を入れるときは、数字を文に書かず、正となる所から差… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `MessageInterpolationTest` |
| AC-267 | 店舗 API の注文の応答は、キャンセルの申請の結果（申請していない、申請中、承… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `orderState.test.ts` |
| AC-268 | 管理 API の注文の応答は、キャンセルの時に在庫を戻すかを運営者に確認するかを… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `OrderDetailPage.test.tsx`、`ReturnedOrderCancellationApiTest`、`ShipmentApiTest` |
| AC-269 | 管理 API の商品の応答は、商品を削除できないときの理由を返す。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `ProductApiTest`、`ProductEditPage.test.tsx` |
| AC-270 | 管理 API の分類の応答は、子の分類を追加できるかを返す。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `CategoryApiTest`、`CategoryPage.test.tsx` |
| AC-271 | 店舗画面と管理画面は、API の応答の操作の可否と理由のほかに、注文の状態や商品… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | 除外 |
| AC-272 | 管理 API の契約は、商品の画像の形式・大きさの上限・枚数の上限を、型の制約で… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `FieldConstraintContractTest`、`ProductEditPage.test.tsx`、`ProductImageApiTest` ほか 1 |
| AC-273 | 管理 API の契約は、分類の深さの上限を示し、バックエンドの上限と一致する。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `FieldConstraintContractTest` |
| AC-274 | 店舗 API と管理 API の契約は、操作ごとに、返しうるエラーのコードの一覧… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `ErrorCodeCheckTest`、`ErrorCodeContractTest` |
| AC-275 | バックエンドのエラーのコードと、契約のエラーのコードの一覧が食い違うと、CI で… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `ErrorCodeContractTest` |
| AC-276 | 管理 API の商品のバリアントの追加は、作ったバリアントの URL を応答の … | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `ProductApiTest` |
| AC-277 | モジュールの間で列挙を写すときは、全ての値の対応を、コンパイルかテストで確認する… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `InventoryMapperTest`、`OrderApiDirectoryTest`、`OrderQuoteApiTest` ほか 5 |
| AC-278 | 売上レポートは、注文の状態を文字列で比べず、列挙で比べる。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `BasisTest`、`StatusHistoryEntryTest` |
| AC-279 | 列挙を文字列で持つデータベースの列は、全て取りうる値を制約で制限する。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `EnumColumnConstraintTest` |
| AC-280 | 業務の日付の基準にする時間帯（日本時間）は、バックエンドの 1 か所で決める。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | 除外 |
| AC-281 | 時点の税率の判定は 1 か所で決める。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | 除外 |
| AC-282 | 分類の深さの上限は、バックエンドの 1 か所で決める。 | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `ProductListApiTest` |
| AC-283 | 郵便番号のデータの町域名にある、住所でない注記（「以下に掲載がない場合」、括弧の… | 有効 | [016](../specs/archive/mvp/016-master-data/spec.md) | — | spec | `PostalCodeCsvTest`、`StoreRegionApiTest`、`postalCodeFill.test.ts` |
| AC-284 | 片付けの定期ジョブが、次のデータを保存期間を過ぎたら削除する。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `CartCleanupJobTest`、`CartTest`、`CheckoutCleanupJobTest` ほか 2 |
| AC-285 | 保存期間と、片付けのジョブの間隔は、設定で変更できる。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `CleanupJobSettingsTest`、`EventCleanupJobSettingsTest` |
| AC-286 | 片付けは、注文・支払・出荷と、未完了のイベント・止めたイベントを削除しない。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `CheckoutCleanupJobTest`、`EventCleanupJobTest` |
| AC-287 | 片付けのジョブは、複数のインスタンスで動かしても同時に 1 つだけが動く（[バッ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `CleanupJobSettingsTest`、`EventCleanupJobSettingsTest` |
| AC-288 | 片付けのジョブは、データごとに削除した件数をログに出力する。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `CartCleanupJobTest`、`CheckoutCleanupJobTest`、`EventCleanupJobTest` ほか 1 |
| AC-289 | 期限切れのカート・注文手続きを削除した後に、そのカート・注文手続きを使う操作は、… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `CartCleanupJobTest`、`CheckoutCleanupJobTest` |
| AC-290 | 再発行しても処理に失敗し続けるイベントは、決めた回数（設定で変更できる）を超えた… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `EventResubmissionJobTest`、`EventResubmissionSettingsTest` |
| AC-291 | イベントを止めたとき、イベントの種類と ID をエラーのログに出力する。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `EventResubmissionJobTest` |
| AC-292 | 未完了のイベントの件数と止めたイベントの件数を、運用ポートのメトリクスで公開する… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `EventResubmissionJobTest` |
| AC-293 | 止めたイベントは、片付けで削除せずに残す。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `EventResubmissionJobTest` |
| AC-294 | 外部サービスのエラーは、どのモジュールでも 502 と、再試行できることを示すメ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `StripePaymentGatewayTest` |
| AC-295 | 支払画面を作るとき、決済サービスのエラーなら 502 を返す。 | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `OrderPaymentApiTest`、`StripePaymentGatewayTest` |
| AC-296 | 画像の確定で、画像の変換のサービスに接続できない、時間内に応答がない、変換のサー… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `ImgproxyImageConverterFailureTest`、`ProductImageServiceErrorApiTest` |
| AC-297 | 画像の確定で、変換のサービスがファイルを読めない（ファイルの側の問題）ときだけ、… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `ImgproxyImageConverterFailureTest` |
| AC-298 | 画像の確定で、ファイル保存のサービスのエラーなら 502 を返す（受付は署名付き… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `ProductImageServiceErrorApiTest`、`S3ObjectStorageFailureTest` |
| AC-299 | 店舗画面と管理画面は、外部サービスのエラーを受けたら、再試行できることを示すメッ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `ProductEditPage.test.tsx`、`order.test.ts` |
| AC-300 | API の規約に、外部サービスのエラーの扱いと、OpenAPI の外のエンドポイ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | 除外 |
| AC-301 | バックエンドから決済サービスとファイル保存のサービスへの呼び出しに、接続と応答の… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `S3ObjectStorageFailureTest`、`StripeConfigTest` |
| AC-302 | 店舗画面のサーバー側から、バックエンドと IdP への呼び出しに、タイムアウトが… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `config.test.ts`、`timeout.test.ts` |
| AC-303 | タイムアウトしたら、バックエンドは外部サービスのエラー（502）として返し、店舗… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `S3ObjectStorageFailureTest`、`timeout.test.ts` |
| AC-304 | 画像の確定は、画像の変換と保存のサービスを、データベースのトランザクションの外で… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `MediaApiTest`、`ProductImageTransactionApiTest` |
| AC-305 | 画像を公開の保存先に置いた後に、画像の登録か商品への追加が失敗したら、置いたファ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `MediaApiTest`、`ProductImageTransactionApiTest` |
| AC-306 | 他のモジュールの、外部サービスを呼ぶ処理も、データベースのトランザクションの外で… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `MediaApiTest`、`PaymentRefundTest`、`PaymentVoidTest` ほか 1 |
| AC-307 | 店舗画面のサーバーアクションが例外で失敗したら、例外の内容をエラーのログに出力し… | 変更 | [017](../specs/archive/mvp/017-stability/spec.md) | [193](../specs/archive/1to10/193-store-a11y/spec.md) | [店舗画面の構成](apps/store.md)（サーバーアクションの失敗のログ） | `withActionResult.test.ts` |
| AC-308 | 店舗画面のサーバー側で、バックエンドから 5xx を受けたら、警告のログに状態コ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `client.test.ts` |
| AC-309 | 店舗画面のログは、トレース ID を持ち、バックエンドのログと照合できる（[フロ… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `client.test.ts` |
| AC-310 | 支払の期限切れのジョブは、1 件の支払で例外（種類を問わない）が起きても、その支… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `PaymentExpiryJobLoggingTest` |
| AC-311 | 決済サービスで支払済みのまま与信の通知を待つ支払が 1 回の処理の件数を超えて滞… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `PaymentExpiryJobLoggingTest`、`PaymentExpiryJobTest`、`PaymentTest` |
| AC-312 | 与信の通知を待つ支払が、待ち始めてから決めた時間（設定で変更できる）を過ぎても残… | 有効 | [017](../specs/archive/mvp/017-stability/spec.md) | — | spec | `PaymentExpiryJobLoggingTest` |
| AC-193-1 | 事実 2 の 6 つの操作の失敗のエラーメッセージは、フォームを送る前から画面に… | 有効 | [193](../specs/archive/1to10/193-store-a11y/spec.md) | — | spec | `ActionError.test.tsx`、`AddToCartForm.test.tsx`、`CancellationRequestForm.test.tsx` ほか 4 |
| AC-193-2 | 読み上げの領域は、状態の知らせ（status の役割）として支援技術に伝わる。 | 有効 | [193](../specs/archive/1to10/193-store-a11y/spec.md) | — | spec | `ActionError.test.tsx`、`AddToCartForm.test.tsx` |
| AC-193-3 | 失敗の後に成功したとき、前のエラーメッセージの文は消え、読み上げの領域は残る。 | 有効 | [193](../specs/archive/1to10/193-store-a11y/spec.md) | — | spec | `AddToCartForm.test.tsx`、`CancellationRequestForm.test.tsx` |
| AC-193-4 | 注文のフォームで欄に誤りがあるとき、その欄の誤りの文が、入力欄の説明として支援技… | 有効 | [193](../specs/archive/1to10/193-store-a11y/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-193-5 | 誤りのある欄は、誤りのある状態として支援技術に伝わる。 | 有効 | [193](../specs/archive/1to10/193-store-a11y/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-193-6 | 欄の補足の文（郵便番号・住所・電話番号の入力の例）も、入力欄の説明として伝わる。 | 有効 | [193](../specs/archive/1to10/193-store-a11y/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-227-1 | 注文のフォームの送信が欄の誤りで失敗したとき、フォーカスは、選べる誤りの欄のうち… | 有効 | [227](../specs/227-store-forms/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-227-2 | 誤りのある欄が全て選べないときは、フォーカスを移さない。 | 有効 | [227](../specs/227-store-forms/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-227-3 | 欄の誤りがない失敗（在庫不足、通信の失敗など、フォームの下に出すもの）では、フォ… | 有効 | [227](../specs/227-store-forms/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-227-4 | フォーカスを移す処理は、送信の結果を受けたときだけ動く。 | 有効 | [227](../specs/227-store-forms/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-227-5 | 同じ誤りで再び送信に失敗したときも、AC-227-1 のとおりにフォーカスを移す… | 有効 | [227](../specs/227-store-forms/spec.md) | — | spec | `OrderForm.test.tsx` |
| AC-227-6 | バリアントの在庫状況の領域は、status の役割を持つ。 | 有効 | [227](../specs/227-store-forms/spec.md) | — | spec | なし |
<!-- /generated:ac-list -->
