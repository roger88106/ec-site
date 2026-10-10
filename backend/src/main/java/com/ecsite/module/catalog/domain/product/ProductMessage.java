package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 商品のメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductMessage {

    /**
     * 商品品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
     */
    public static final I18nMessage INVALID_PRODUCT_CODE = I18nMessage.of(Code.INVALID_PRODUCT_CODE);

    /**
     * バリアント品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
     */
    public static final I18nMessage INVALID_VARIANT_CODE = I18nMessage.of(Code.INVALID_VARIANT_CODE);

    /**
     * 税抜価格は 1 円以上にしてください。
     */
    public static final I18nMessage INVALID_PRICE_EXCL_TAX = I18nMessage.of(Code.INVALID_PRICE_EXCL_TAX);

    /**
     * 商品名は 1 文字以上 200 文字以内にしてください。
     */
    public static final I18nMessage INVALID_PRODUCT_NAME = I18nMessage.of(Code.INVALID_PRODUCT_NAME);

    /**
     * 説明は 10,000 文字以内にしてください。
     */
    public static final I18nMessage INVALID_PRODUCT_DESCRIPTION = I18nMessage.of(Code.INVALID_PRODUCT_DESCRIPTION);

    /**
     * バリアント軸は 3 つまでにし、名前は重複しない 1 文字以上 30 文字以内にしてください。
     */
    public static final I18nMessage INVALID_VARIANT_AXES = I18nMessage.of(Code.INVALID_VARIANT_AXES);

    /**
     * 軸の値は、商品のすべての軸について、1 文字以上 50 文字以内で指定してください。
     */
    public static final I18nMessage INVALID_AXIS_VALUES = I18nMessage.of(Code.INVALID_AXIS_VALUES);

    /**
     * 公開開始日時は、公開終了日時より前にしてください。
     */
    public static final I18nMessage INVALID_PUBLICATION_PERIOD = I18nMessage.of(Code.INVALID_PUBLICATION_PERIOD);

    /**
     * 並べ替えるバリアントが、商品のバリアントと一致しません。再読み込みしてください。
     */
    public static final I18nMessage INVALID_VARIANT_ORDER = I18nMessage.of(Code.INVALID_VARIANT_ORDER);

    /**
     * 並べ替える画像が、商品の画像と一致しません。再読み込みしてください。
     */
    public static final I18nMessage INVALID_IMAGE_ORDER = I18nMessage.of(Code.INVALID_IMAGE_ORDER);

    /**
     * バリアントには、その商品の画像だけを指定できます。
     */
    public static final I18nMessage INVALID_VARIANT_IMAGE = I18nMessage.of(Code.INVALID_VARIANT_IMAGE);

    /**
     * 画像は 1 商品あたり 20 枚まで登録できます。
     */
    public static final I18nMessage TOO_MANY_PRODUCT_IMAGES = I18nMessage.of(Code.TOO_MANY_PRODUCT_IMAGES);

    /**
     * 画像の形式は JPEG、PNG、WebP のいずれかにしてください。
     */
    public static final I18nMessage UNSUPPORTED_IMAGE_FORMAT = I18nMessage.of(Code.UNSUPPORTED_IMAGE_FORMAT);

    /**
     * 画像は 1 ファイル 10MB までです。
     */
    public static final I18nMessage IMAGE_TOO_LARGE = I18nMessage.of(Code.IMAGE_TOO_LARGE);

    /**
     * アップロードした画像が見つかりません。もう一度アップロードしてください。
     */
    public static final I18nMessage IMAGE_UPLOAD_NOT_FOUND = I18nMessage.of(Code.IMAGE_UPLOAD_NOT_FOUND);

    /**
     * ファイルの中身が JPEG、PNG、WebP の画像ではありません。
     */
    public static final I18nMessage INVALID_IMAGE_FILE = I18nMessage.of(Code.INVALID_IMAGE_FILE);

    /**
     * 存在しないカテゴリが指定されています。
     */
    public static final I18nMessage INVALID_CATEGORY_ID = I18nMessage.of(Code.INVALID_CATEGORY_ID);

    /**
     * 現時点で有効な税率のない税区分は、商品に割り当てられません。
     */
    public static final I18nMessage TAX_CATEGORY_NOT_ASSIGNABLE = I18nMessage.of(Code.TAX_CATEGORY_NOT_ASSIGNABLE);

    /**
     * 同じ商品品番の商品がすでにあります。
     */
    public static final I18nMessage PRODUCT_CODE_ALREADY_EXISTS = I18nMessage.of(Code.PRODUCT_CODE_ALREADY_EXISTS);

    /**
     * 同じバリアント品番のバリアントがすでにあります。
     */
    public static final I18nMessage VARIANT_CODE_ALREADY_EXISTS = I18nMessage.of(Code.VARIANT_CODE_ALREADY_EXISTS);

    /**
     * 同じ軸の値の組み合わせのバリアントがすでにあります。
     */
    public static final I18nMessage VARIANT_AXIS_VALUES_ALREADY_EXISTS = I18nMessage.of(Code.VARIANT_AXIS_VALUES_ALREADY_EXISTS);

    /**
     * バリアント軸のない商品には、バリアントを 1 つだけ登録できます。
     */
    public static final I18nMessage SINGLE_VARIANT_ONLY = I18nMessage.of(Code.SINGLE_VARIANT_ONLY);

    /**
     * バリアントのない商品は公開できません。
     */
    public static final I18nMessage PRODUCT_HAS_NO_VARIANTS = I18nMessage.of(Code.PRODUCT_HAS_NO_VARIANTS);

    /**
     * 販売終了の商品は、公開にも非公開にも戻せません。
     */
    public static final I18nMessage PRODUCT_DISCONTINUED = I18nMessage.of(Code.PRODUCT_DISCONTINUED);

    /**
     * C 側に表示されたことのある商品とそのバリアントは削除できません。販売終了にしてください。
     */
    public static final I18nMessage PRODUCT_ALREADY_SHOWN = I18nMessage.of(Code.PRODUCT_ALREADY_SHOWN);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {
        /**
         * 商品品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
         */
        public static final String INVALID_PRODUCT_CODE = "ecsite.catalog.invalid_product_code";

        /**
         * バリアント品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
         */
        public static final String INVALID_VARIANT_CODE = "ecsite.catalog.invalid_variant_code";

        /**
         * 税抜価格は 1 円以上にしてください。
         */
        public static final String INVALID_PRICE_EXCL_TAX = "ecsite.catalog.invalid_price_excl_tax";

        /**
         * 商品名は 1 文字以上 200 文字以内にしてください。
         */
        public static final String INVALID_PRODUCT_NAME = "ecsite.catalog.invalid_product_name";

        /**
         * 説明は 10,000 文字以内にしてください。
         */
        public static final String INVALID_PRODUCT_DESCRIPTION = "ecsite.catalog.invalid_product_description";

        /**
         * バリアント軸は 3 つまでにし、名前は重複しない 1 文字以上 30 文字以内にしてください。
         */
        public static final String INVALID_VARIANT_AXES = "ecsite.catalog.invalid_variant_axes";

        /**
         * 軸の値は、商品のすべての軸について、1 文字以上 50 文字以内で指定してください。
         */
        public static final String INVALID_AXIS_VALUES = "ecsite.catalog.invalid_axis_values";

        /**
         * 公開開始日時は、公開終了日時より前にしてください。
         */
        public static final String INVALID_PUBLICATION_PERIOD = "ecsite.catalog.invalid_publication_period";

        /**
         * 並べ替えるバリアントが、商品のバリアントと一致しません。再読み込みしてください。
         */
        public static final String INVALID_VARIANT_ORDER = "ecsite.catalog.invalid_variant_order";

        /**
         * 並べ替える画像が、商品の画像と一致しません。再読み込みしてください。
         */
        public static final String INVALID_IMAGE_ORDER = "ecsite.catalog.invalid_image_order";

        /**
         * バリアントには、その商品の画像だけを指定できます。
         */
        public static final String INVALID_VARIANT_IMAGE = "ecsite.catalog.invalid_variant_image";

        /**
         * 画像は 1 商品あたり 20 枚まで登録できます。
         */
        public static final String TOO_MANY_PRODUCT_IMAGES = "ecsite.catalog.too_many_product_images";

        /**
         * 画像の形式は JPEG、PNG、WebP のいずれかにしてください。
         */
        public static final String UNSUPPORTED_IMAGE_FORMAT = "ecsite.catalog.unsupported_image_format";

        /**
         * 画像は 1 ファイル 10MB までです。
         */
        public static final String IMAGE_TOO_LARGE = "ecsite.catalog.image_too_large";

        /**
         * アップロードした画像が見つかりません。もう一度アップロードしてください。
         */
        public static final String IMAGE_UPLOAD_NOT_FOUND = "ecsite.catalog.image_upload_not_found";

        /**
         * ファイルの中身が JPEG、PNG、WebP の画像ではありません。
         */
        public static final String INVALID_IMAGE_FILE = "ecsite.catalog.invalid_image_file";

        /**
         * 存在しないカテゴリが指定されています。
         */
        public static final String INVALID_CATEGORY_ID = "ecsite.catalog.invalid_category_id";

        /**
         * 現時点で有効な税率のない税区分は、商品に割り当てられません。
         */
        public static final String TAX_CATEGORY_NOT_ASSIGNABLE = "ecsite.catalog.tax_category_not_assignable";

        /**
         * 同じ商品品番の商品がすでにあります。
         */
        public static final String PRODUCT_CODE_ALREADY_EXISTS = "ecsite.catalog.product_code_already_exists";

        /**
         * 同じバリアント品番のバリアントがすでにあります。
         */
        public static final String VARIANT_CODE_ALREADY_EXISTS = "ecsite.catalog.variant_code_already_exists";

        /**
         * 同じ軸の値の組み合わせのバリアントがすでにあります。
         */
        public static final String VARIANT_AXIS_VALUES_ALREADY_EXISTS = "ecsite.catalog.variant_axis_values_already_exists";

        /**
         * バリアント軸のない商品には、バリアントを 1 つだけ登録できます。
         */
        public static final String SINGLE_VARIANT_ONLY = "ecsite.catalog.single_variant_only";

        /**
         * バリアントのない商品は公開できません。
         */
        public static final String PRODUCT_HAS_NO_VARIANTS = "ecsite.catalog.product_has_no_variants";

        /**
         * 販売終了の商品は、公開にも非公開にも戻せません。
         */
        public static final String PRODUCT_DISCONTINUED = "ecsite.catalog.product_discontinued";

        /**
         * C 側に表示されたことのある商品とそのバリアントは削除できません。販売終了にしてください。
         */
        public static final String PRODUCT_ALREADY_SHOWN = "ecsite.catalog.product_already_shown";
    }
}
