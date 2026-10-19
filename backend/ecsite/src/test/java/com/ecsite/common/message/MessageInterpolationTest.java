package com.ecsite.common.message;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorDetail;
import com.ecsite.module.cart.domain.CartErrorCode;
import com.ecsite.module.catalog.domain.category.CategoryErrorCode;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import com.ecsite.module.inventory.domain.stock.InventoryErrorCode;
import com.ecsite.module.media.domain.MediaErrorCode;
import com.ecsite.module.order.domain.order.OrderErrorCode;
import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 規則の値を差し込むメッセージの単体テスト（docs/backend/coding.md 第 3.2 節、spec 005 DC-38）。<br />
 * 差し込む値をドメインの定数から取り、整理の前（数字を文に書いていたとき）と同じ文になることを確かめる。
 */
class MessageInterpolationTest {

    /**
     * 1 件の見本。
     *
     * @param error    エラーの定義
     * @param values   差し込む値の出どころ（{@code クラスの完全修飾名#定数名}）。MB にする値は {@code #MAX_BYTES/MB}
     * @param expected 整理の前の文
     */
    record Case(ErrorCodeProvider error, List<String> values, String expected) {

        @Override
        public String toString() {
            return error.toString();
        }
    }

    /**
     * 見本を返す。
     *
     * @return 見本
     */
    static Stream<Case> cases() {
        String tax = "com.ecsite.module.tax.domain.taxcategory.";
        String category = "com.ecsite.module.catalog.domain.category.Category#";
        String product = "com.ecsite.module.catalog.domain.product.Product#";
        String code = "com.ecsite.module.catalog.domain.product.vo.CodeFormat#MAX_LENGTH";
        String address = "com.ecsite.module.order.domain.order.vo.ShippingAddress#";
        String cart = "com.ecsite.module.cart.domain.Cart#";
        String formats = "com.ecsite.module.media.domain.ImageFormat#displayNames()";
        return Stream.of(
                new Case(CommonErrorCode.INVALID_MONEY, List.of("com.ecsite.common.model.value.Money#MIN_YEN"),
                        "金額は 0 円以上にしてください。"),
                new Case(TaxErrorCode.INVALID_RATE_PERCENT,
                        List.of(tax + "vo.RatePercent#LOWER_BOUND", tax + "vo.RatePercent#UPPER_BOUND", tax + "vo.RatePercent#MAX_SCALE"),
                        "税率は 0% 以上 100% 未満、小数第 2 位までにしてください。"),
                new Case(ProductErrorCode.INVALID_PRICE_EXCL_TAX,
                        List.of("com.ecsite.module.catalog.domain.product.vo.PriceExclTax#MIN_YEN"),
                        "税抜価格は 1 円以上にしてください。"),
                new Case(MediaErrorCode.UNSUPPORTED_IMAGE_FORMAT, List.of(formats),
                        "画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。"),
                new Case(MediaErrorCode.INVALID_IMAGE_FILE, List.of(formats),
                        "ファイルの中身が対応している形式（JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF）の画像ではありません。"),
                new Case(OrderErrorCode.INVALID_POSTAL_CODE, List.of(address + "POSTAL_CODE_DIGITS"),
                        "郵便番号は数字 7 桁で入力してください。"),
                new Case(OrderErrorCode.INVALID_PHONE_NUMBER, List.of(address + "PHONE_MIN_DIGITS", address + "PHONE_MAX_DIGITS"),
                        "電話番号は数字 10〜11 桁で入力してください。"),
                new Case(TaxErrorCode.INVALID_TAX_CATEGORY_NAME, List.of(tax + "TaxCategory#NAME_MAX_LENGTH"),
                        "税区分の名称は 1 文字以上 50 文字以内にしてください。"),
                new Case(CategoryErrorCode.INVALID_CATEGORY_NAME, List.of(category + "NAME_MAX_LENGTH"),
                        "カテゴリの名称は 1 文字以上 50 文字以内にしてください。"),
                new Case(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED, List.of(category + "MAX_DEPTH"),
                        "カテゴリは 3 階層までしか作れません。"),
                new Case(ProductErrorCode.INVALID_PRODUCT_CODE, List.of(code),
                        "商品品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。"),
                new Case(ProductErrorCode.INVALID_VARIANT_CODE, List.of(code),
                        "バリアント品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。"),
                new Case(ProductErrorCode.INVALID_PRODUCT_NAME, List.of(product + "NAME_MAX_LENGTH"),
                        "商品名は 1 文字以上 200 文字以内にしてください。"),
                new Case(ProductErrorCode.INVALID_PRODUCT_DESCRIPTION, List.of(product + "DESCRIPTION_MAX_LENGTH"),
                        "説明は 10,000 文字以内にしてください。"),
                new Case(ProductErrorCode.INVALID_VARIANT_AXES,
                        List.of(product + "MAX_AXES", product + "AXIS_NAME_MAX_LENGTH"),
                        "バリアント軸は 3 つまでにし、名前は重複しない 1 文字以上 30 文字以内にしてください。"),
                new Case(ProductErrorCode.INVALID_AXIS_VALUES, List.of(product + "AXIS_VALUE_MAX_LENGTH"),
                        "軸の値は、商品のすべての軸について、1 文字以上 50 文字以内で指定してください。"),
                new Case(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES, List.of(product + "MAX_IMAGES"),
                        "画像は 1 商品あたり 20 枚まで登録できます。"),
                new Case(MediaErrorCode.IMAGE_TOO_LARGE, List.of("com.ecsite.module.media.domain.ImageFormat#MAX_BYTES/MB"),
                        "画像は 1 ファイル 10MB までです。"),
                new Case(InventoryErrorCode.INVALID_STOCK_MEMO,
                        List.of("com.ecsite.module.inventory.domain.stock.StockAdjustment#MEMO_MAX_LENGTH"),
                        "メモは 500 文字以内にしてください。"),
                new Case(CartErrorCode.INVALID_CART_QUANTITY, List.of(cart + "MAX_QUANTITY"),
                        "1 品目の数量は 1〜10 にしてください。"),
                new Case(CartErrorCode.TOO_MANY_CART_ITEMS, List.of(cart + "MAX_ITEMS"),
                        "カートに入れられるのは 30 品目までです。"),
                new Case(OrderErrorCode.INVALID_RECIPIENT_NAME, List.of(address + "NAME_MAX_LENGTH"),
                        "氏名を 50 文字以内で入力してください。"),
                new Case(OrderErrorCode.INVALID_ADDRESS, List.of(address + "ADDRESS_MAX_LENGTH"),
                        "住所を 200 文字以内で入力してください。"),
                new Case(OrderErrorCode.INVALID_CANCELLATION_REASON,
                        List.of("com.ecsite.module.order.domain.order.Order#REASON_MAX_LENGTH"),
                        "キャンセルの理由は 500 文字以内で入力してください。"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("ドメインの定数を差し込んだ文が、整理の前の文と同じになる")
    void interpolatesDomainConstants(Case example) throws ReflectiveOperationException {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        var helper = new MessageHelper(messageSource);

        List<Object> values = example.values().stream().map(MessageInterpolationTest::valueOf).toList();

        assertThat(helper.getMessage(ErrorDetail.of(example.error().getCode(), values))).isEqualTo(example.expected());
    }

    /**
     * 値の出どころから、ドメインの定数の値を読む（package-private の定数を読むため反射を使う）。
     *
     * @param location {@code クラスの完全修飾名#定数名}。末尾が {@code /MB} なら、バイトを MB にする。
     *                 末尾が {@code ()} なら、引数のない static メソッドの戻り値
     * @return 定数の値
     */
    private static Object valueOf(String location) {
        boolean megabytes = location.endsWith("/MB");
        String[] classAndField = location.replace("/MB", "").split("#");
        try {
            if (classAndField[1].endsWith("()")) {
                return Class.forName(classAndField[0]).getMethod(classAndField[1].replace("()", "")).invoke(null);
            }
            var field = Class.forName(classAndField[0]).getDeclaredField(classAndField[1]);
            field.setAccessible(true);
            long value = ((Number) field.get(null)).longValue();
            return megabytes ? value / (1024 * 1024) : value;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(Arrays.toString(classAndField), e);
        }
    }
}
