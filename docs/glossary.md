# 用語集

業務の用語（論理名）と、コード・DB で使う識別子（物理名）の対応表。
文書の本文では論理名だけを使い、識別子はこの表で引く（constitution 14）。

## モジュール

| 用語 | 識別子 | 意味 |
|------|--------|------|
| 税モジュール | `tax` | 税区分と税率 |
| 商品カタログモジュール | `catalog` | 商品、バリアント、カテゴリ、公開、商品検索 |
| 在庫モジュール | `inventory` | 在庫数と調整履歴 |
| 店舗モジュール | `storefront` | C 側 API。複数モジュールの結果を組み合わせる |

## 税

| 用語 | 識別子 | 意味 |
|------|--------|------|
| 税区分 | `TaxCategory` / `tax_category` | 税率の種類（例：標準、軽減） |
| 税率 | `TaxRate` / `tax_rate` | 税区分ごとの率と、その適用期間 |
| 率 | `RatePercent` / `rate_percent` | 税率の値（%）。0 以上 100 未満、小数第 2 位まで |
| 適用期間 | `EffectivePeriod` / `effective_from`、`effective_to` | 税率が有効な期間。開始を含み、終了を含まない。空白は無限 |
| 税率の一覧の置き換え | `replaceRates` | 税区分の税率をまとめて置き換える操作 |
| 指定時点の税率 | `rateAt` | ある時点を含む税率 |
| 置き換え後の税率 | `TaxRateDraft` | 税率の一覧の置き換えで指定する 1 件。既存の税率を残す場合は、その ID を持つ |
| 現時点の率 | `currentRatePercent` | 現在時刻を含む税率の率。なければ空 |
| 税区分ごとの税率の一覧 | `TaxRateSchedule` | 税モジュールが商品カタログに渡す、予定を含む税率の一覧 |

## 商品

| 用語 | 識別子 | 意味 |
|------|--------|------|
| 商品 | `Product` / `product` | 消費者から見た 1 つの商品ページの単位 |
| 商品品番 | `ProductCode` / `product_code` | 運営者が商品に付ける業務上の番号。英大文字・数字とハイフン。一意だが、付け替えられることがある |
| バリアント | `Variant` / `variant` | 商品の中で、実際に販売・在庫管理する単位（例：「白 / M」） |
| バリアント品番 | `VariantCode` / `variant_code` | 運営者がバリアントに付ける業務上の番号 |
| バリアント軸 | `VariantAxis` / `product_axis` | バリアントを区別する観点（例：色、サイズ）。1 商品に最大 3 つ |
| 軸の値 | `AxisValue` / `variant_axis_value` | バリアントが各軸で持つ値（例：白、M） |
| 軸キー | `axis_key` | 軸の値を連結した値。商品内での組み合わせの一意性を DB で守るために使う |
| カテゴリ | `Category` / `category` | 商品の分類。最大 3 階層 |
| 階層の深さ | `depth` | カテゴリの階層。最上位が 1、最大 3 |
| 表示順 | `displayOrder` / `display_order` | 兄弟カテゴリの中の並び順。小さいほど先 |
| 税抜価格 | `priceExclTax` / `price_excl_tax` | 運営者が商品に登録する価格。バリアント間で共通 |
| 税込価格 | `priceInclTax` | 税抜価格に、その時点の税率を適用した価格 |
| 金額 | `Money` | 円単位の整数を表す値オブジェクト |
| 公開状態 | `PublicationStatus` / `status` | 非公開（`UNPUBLISHED`）、公開（`PUBLISHED`）、販売終了（`DISCONTINUED`） |
| 公開期間 | `PublicationPeriod` / `publish_start`、`publish_end` | 公開状態でも、C 側に表示する期間を限る |
| 初回表示日時 | `firstShownAt` / `first_shown_at` | 最初に C 側へ表示された日時。未来の値は「表示される予定」で、過去になった時点で確定する |
| 表示条件 | `isVisibleAt` | C 側に表示される条件（公開状態が「公開」で、公開期間内） |
| 販売停止 | `salesSuspended` / `sales_suspended` | バリアントを C 側で選べなくする |
| 商品検索 | `ProductSearch` | キーワード検索の窓口 |
| 商品照会 | `ProductQueryRepository` | カテゴリ・価格帯などの条件による一覧 |

## 在庫

| 用語 | 識別子 | 意味 |
|------|--------|------|
| 在庫 | `StockItem` / `stock_item` | バリアントごとの在庫 |
| 在庫数 | `quantity` | 販売可能な数量 |
| 在庫調整 | `StockAdjustment` / `stock_adjustment` | 増減量と理由による在庫数の変更。履歴として残す |
| 増減量 | `delta` | 在庫調整で加える量（0 以外） |
| 調整理由 | `reason` | 入荷（`ARRIVAL`）、棚卸差異（`STOCKTAKE`）、破損・紛失（`DAMAGE_OR_LOSS`）、その他（`OTHER`） |
| 調整後の在庫数 | `quantity_after` | 履歴に残す、調整した直後の在庫数 |

## イベント

| 用語 | 識別子 | 発行元 |
|------|--------|--------|
| バリアント登録 | `VariantRegistered` | 商品カタログ |
| バリアント削除 | `VariantDeleted` | 商品カタログ |
| 商品削除 | `ProductDeleted` | 商品カタログ |
| 商品公開 | `ProductPublished` | 商品カタログ |
| 商品非公開 | `ProductUnpublished` | 商品カタログ |
| 商品販売終了 | `ProductDiscontinued` | 商品カタログ |
| 商品価格変更 | `ProductPriceChanged` | 商品カタログ |
