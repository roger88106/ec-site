# 段階 4：エラーとメッセージ

- 前提：段階 3
- 完了条件：DC-36〜38、DC-64。変わる文を利用者が確かめた。`./gradlew check`、管理画面のテスト、E2E が通る。
- 参照：[plan](../plan.md) 第 3 節

## タスク

- [x] **T5401** 一覧を作る：文に規則の値を数字で書いているメッセージ（plan 第 3.3 節）、入力の検証のメッセージの今の文（ローカルと CI の JVM のロケールで）と新しい文（plan 第 3.2 節）、管理画面に表示が変わる所。
- [x] **T5402** [テスト] `MessageContractTest`：すべての `ErrorCodeProvider` の定数に `messages.properties` のキーがあり、逆もある。キーは `ecsite.<モジュール>.<定数の名前の小文字>`。
- [x] **T5403** `ErrorCodeProvider` にキーを作る既定のメソッドを足し、各 `<モジュール>ErrorCode` をそれに合わせる。`<モジュール>Message` と `Code` を消す。T5402 と既存のテストが通る（DC-36）。
- [x] **T5404** [テスト] 入力の検証のメッセージ：制約の種類ごとに `ecsite.validation.<種類>` の文になり、上限・下限が差し込まれる。知らない種類は `ecsite.validation.invalid`。JVM のロケールによらない（DC-37）。
- [x] **T5405** `GlobalExceptionHandler` の検証のエラーを、キーから文を作る形にする。`messages.properties` に検証のキーを足す。T5404 を通す。管理画面・E2E のテストで、変わった文に合わせる所を直す（DN-4 の例外）。
- [x] **T5406** [テスト] 文に数字を書いていたメッセージを差し込みにしても、整理の前と同じ文になる。差し込む値がドメインの定数から来る（DC-38）。
- [ ] **T5409** 業務の例外に差し込む値を持たせ、T5401 の一覧のメッセージを差し込みにする。T5406 を通す（DC-38）。
- [ ] **T5407** [テスト] `ImgproxyImageConverterFailureTest`：imgproxy の代わりが 200 で WebP でない本文（PNG など）を返すと、変換の失敗のエラーになる。今のコードで失敗することを確かめる（DC-64）。
- [ ] **T5408** `ImgproxyImageConverter` で、応答の本文の先頭のバイトが WebP でなければ変換の失敗にする。T5407 を通す。`docs/modules/media.md` の変換の説明に書き足す（DC-64）。

## 一覧（T5401）

2026-10-13 に作った。利用者の review の前に、この一覧で進める（利用者の指示）。

### 差し込みにするメッセージ（plan 第 3.3 節）

ドメインの定数で決まる値だけを差し込みにする。定義から決まる値（必須の「1 文字以上」、金額の「0 円以上」、税率の「100% 未満」）と、形式の正規表現で決まる値（郵便番号の 7 桁、電話番号の 10〜11 桁）は、文のまま残す。数は 3 桁ごとにカンマで区切って差し込む（今の文「10,000 文字」と同じにするため）。

| キー | 差し込む値 | 値の出どころ |
|------|------------|--------------|
| `ecsite.tax.invalid_rate_percent` | `{scale}`（小数の桁） | `RatePercent.MAX_SCALE` |
| `ecsite.tax.invalid_tax_category_name` | `{max}` | `TaxCategory.NAME_MAX_LENGTH` |
| `ecsite.catalog.invalid_category_name` | `{max}` | `Category.NAME_MAX_LENGTH` |
| `ecsite.catalog.category_depth_exceeded` | `{max}` | `Category.MAX_DEPTH` |
| `ecsite.catalog.invalid_product_code`、`invalid_variant_code` | `{max}` | `CodeFormat.MAX_LENGTH` |
| `ecsite.catalog.invalid_product_name` | `{max}` | `Product.NAME_MAX_LENGTH` |
| `ecsite.catalog.invalid_product_description` | `{max}` | `Product.DESCRIPTION_MAX_LENGTH` |
| `ecsite.catalog.invalid_variant_axes` | `{maxAxes}`、`{max}` | `Product.MAX_AXES`、`AXIS_NAME_MAX_LENGTH` |
| `ecsite.catalog.invalid_axis_values` | `{max}` | `Product.AXIS_VALUE_MAX_LENGTH` |
| `ecsite.catalog.too_many_product_images` | `{max}` | `Product.MAX_IMAGES` |
| `ecsite.media.image_too_large` | `{maxMegabytes}` | `ImageFormat.MAX_BYTES` を MB にした値 |
| `ecsite.inventory.invalid_stock_memo` | `{max}` | `StockAdjustment.MEMO_MAX_LENGTH` |
| `ecsite.cart.invalid_cart_quantity` | `{max}` | `Cart.MAX_QUANTITY` |
| `ecsite.cart.too_many_cart_items` | `{max}` | `Cart.MAX_ITEMS` |
| `ecsite.order.invalid_recipient_name`、`invalid_address` | `{max}` | `ShippingAddress.NAME_MAX_LENGTH`、`ADDRESS_MAX_LENGTH` |
| `ecsite.order.invalid_cancellation_reason` | `{max}` | `Order.REASON_MAX_LENGTH` |

### 入力の検証のメッセージの今の文と新しい文（plan 第 3.2 節）

今は Hibernate Validator の日本語の既定の文を使っている。ロケールは `application.yml` で `ja` に固定しているので、ローカル（JVM のロケールは zh_TW）でも CI でも同じ文になる。

| 種類 | 今の文（既定） | 新しい文 |
|------|----------------|----------|
| NotNull | null は許可されていません | 入力してください。 |
| NotBlank | 空白は許可されていません | 入力してください。 |
| Size | {min} から {max} の間のサイズにしてください | {min} 文字以上 {max} 文字以内にしてください。（下限が 0 なら {max} 文字以内にしてください。） |
| Min・DecimalMin | {value} 以上の値にしてください | {value} 以上にしてください。 |
| Max・DecimalMax | {value} 以下の値にしてください | {value} 以下にしてください。 |
| Pattern | 正規表現 "{regexp}" にマッチさせてください | 形式が正しくありません。 |
| Email | 電子メールアドレスとして正しい形式にしてください | メールアドレスの形式が正しくありません。 |
| そのほか | （制約ごとの既定の文） | 値が正しくありません。 |

### 管理画面に表示が変わる所

- 管理画面は、入力の検証のエラーの `errors` を、入力欄の下か画面の上のエラーの欄に、そのまま出す（`ErrorMessage`・`formErrors`）。
- 管理画面の入力欄は、同じ制約を画面でも確かめるので、ふだんは API の検証のエラーまで届かない。表示が変わるのは、画面の確かめをすり抜けたときだけである。
- 管理画面・店舗画面の結合テストと E2E に、既定の文を確かめているものはない（2026-10-13 に検索した）。
