package com.ecsite.module.order.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.api.model.CartQuote;
import com.ecsite.module.order.application.model.view.CartQuoteView;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.UnpurchasableReason;
import com.ecsite.module.order.domain.quote.VariantInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 見積もりを公開する型に変換するときの、列挙の変換と項目の対応の単体テスト
 *
 * <p>{@link OrderQuoteApi} の変換だけを確認する。見積もりの金額の求め方は {@code QuoteTest}、カートの画面に返す流れは
 * API の統合テストが確認する。
 */
class OrderQuoteApiTest {

    // ============================== 定数 ==============================

    /**
     * シャツの商品名
     */
    private static final String PRODUCT_NAME = "シャツ";

    /**
     * シャツの画像の URL
     */
    private static final String IMAGE_URL = "https://example.com/a.png";

    /**
     * シャツの税抜単価
     */
    private static final Money PRICE_EXCL_TAX = new Money(1000);

    /**
     * シャツの税込単価
     */
    private static final Money PRICE_INCL_TAX = new Money(1100);

    /**
     * 税率（%）
     */
    private static final BigDecimal RATE = new BigDecimal("10");

    /**
     * 商品の合計
     */
    private static final Money ITEMS_TOTAL = new Money(2200);

    /**
     * 送料
     */
    private static final Money SHIPPING_FEE = new Money(500);

    /**
     * 総額
     */
    private static final Money TOTAL = new Money(2700);

    /**
     * 消費税額
     */
    private static final Money TAX = new Money(245);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("購入できない理由の変換")
    class Reason {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(UnpurchasableReason.class)
        @DisplayName("AC-277 購入できない理由の全ての値を、公開する型の同じ名前の値に写す")
        void mapsEveryReason(UnpurchasableReason reason) {
            // 操作と確認：同じ名前の値
            assertThat(OrderQuoteApi.toReason(reason)).isEqualTo(CartQuote.UnpurchasableReason.valueOf(reason.name()));
        }
    }

    @Nested
    @DisplayName("見積もりの項目の変換")
    class Fields {

        @Test
        @DisplayName("AC-277 見積もりの全ての項目（品目、軸の値、金額、税額）を、公開する型の同じ値に写す")
        void mapsEveryField() {
            // 準備：購入できる品目と在庫切れの品目の見積もり
            var cartId = UUID.randomUUID();
            var variantId = UUID.randomUUID();
            var productId = UUID.randomUUID();
            var variant = new VariantInfo(variantId, productId, "P-1", "V-1", PRODUCT_NAME,
                    List.of(new AxisValue("色", "赤"), new AxisValue("サイズ", "M")), PRICE_EXCL_TAX,
                    UUID.randomUUID(), IMAGE_URL, true, false);
            var purchasable = new QuoteItem(variant, 2, RATE, PRICE_INCL_TAX, new Money(2200), null);
            var unpurchasable = new QuoteItem(variant, 1, RATE, PRICE_INCL_TAX, new Money(1100), UnpurchasableReason.OUT_OF_STOCK);
            var amounts = new Amounts(ITEMS_TOTAL, SHIPPING_FEE, TOTAL, List.of(new TaxAmount(RATE, TOTAL, TAX)));
            var quote = new Quote(List.of(purchasable, unpurchasable), amounts, false);

            // 操作：公開する型に変換する
            var actual = OrderQuoteApi.toCartQuote(new CartQuoteView(cartId, quote));

            // 確認：全ての項目が同じ値
            assertThat(actual).isEqualTo(expectedCartQuote(cartId, variantId, productId));
        }
    }

    @Nested
    @DisplayName("値のない項目の変換")
    class EmptyFields {

        @Test
        @DisplayName("画像のない品目は、画像の URL をなしで写す")
        void mapsNullImage() {
            // 操作：画像のない品目の見積もりを変換する
            var actual = OrderQuoteApi.toCartQuote(new CartQuoteView(UUID.randomUUID(), minimalQuote()));

            // 確認：画像の URL がない
            assertThat(actual.items()).singleElement().satisfies(item -> assertThat(item.imageUrl()).isNull());
        }

        @Test
        @DisplayName("軸の値のない品目は、軸の値を空の一覧で写す")
        void mapsEmptyAxisValues() {
            // 操作：軸の値のない品目の見積もりを変換する
            var actual = OrderQuoteApi.toCartQuote(new CartQuoteView(UUID.randomUUID(), minimalQuote()));

            // 確認：軸の値が空
            assertThat(actual.items()).singleElement().satisfies(item -> assertThat(item.axisValues()).isEmpty());
        }

        @Test
        @DisplayName("購入できる品目は、購入できない理由をなしで写す")
        void mapsNullReason() {
            // 操作：購入できる品目の見積もりを変換する
            var actual = OrderQuoteApi.toCartQuote(new CartQuoteView(UUID.randomUUID(), minimalQuote()));

            // 確認：購入できない理由がない
            assertThat(actual.items()).singleElement().satisfies(item -> assertThat(item.unpurchasableReason()).isNull());
        }

        @Test
        @DisplayName("消費税額のない見積もりは、消費税額を空の一覧で写す")
        void mapsEmptyTaxes() {
            // 操作：消費税額のない見積もりを変換する
            var actual = OrderQuoteApi.toCartQuote(new CartQuoteView(UUID.randomUUID(), minimalQuote()));

            // 確認：消費税額が空
            assertThat(actual.amounts().taxes()).isEmpty();
        }

        @Test
        @DisplayName("注文できる見積もりは、注文できるとして写す")
        void mapsOrderable() {
            // 操作：注文できる見積もりを変換する
            var actual = OrderQuoteApi.toCartQuote(new CartQuoteView(UUID.randomUUID(), minimalQuote()));

            // 確認：注文できる
            assertThat(actual.orderable()).isTrue();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * {@link Fields#mapsEveryField()} の見積もりを変換した、公開する型の見積もりを、定数から作る。
     *
     * @param cartId    カート ID（実行ごとに作るので、定数にできない）
     * @param variantId バリアント ID（実行ごとに作るので、定数にできない）
     * @param productId 商品 ID（実行ごとに作るので、定数にできない）
     * @return 期待する公開する型の見積もり
     */
    private static CartQuote expectedCartQuote(UUID cartId, UUID variantId, UUID productId) {
        var expectedAxes = List.of(new CartQuote.AxisValue("色", "赤"), new CartQuote.AxisValue("サイズ", "M"));
        return new CartQuote(cartId,
                List.of(new CartQuote.Item(variantId, productId, PRODUCT_NAME, expectedAxes, IMAGE_URL, PRICE_EXCL_TAX,
                                PRICE_INCL_TAX, 2, new Money(2200), null),
                        new CartQuote.Item(variantId, productId, PRODUCT_NAME, expectedAxes, IMAGE_URL, PRICE_EXCL_TAX,
                                PRICE_INCL_TAX, 1, new Money(1100), CartQuote.UnpurchasableReason.OUT_OF_STOCK)),
                new CartQuote.Amounts(ITEMS_TOTAL, SHIPPING_FEE, TOTAL, List.of(new CartQuote.TaxAmount(RATE, TOTAL, TAX))),
                false);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 画像と軸の値のない靴 1 足の、消費税額のない注文できる見積もりを作る。
     *
     * @return 見積もり
     */
    private static Quote minimalQuote() {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-2", "V-2", "靴",
                List.of(), new Money(3000), UUID.randomUUID(), null, true, false);
        var item = new QuoteItem(variant, 1, new BigDecimal("8"), new Money(3240), new Money(3240), null);
        return new Quote(List.of(item), new Amounts(new Money(3240), Money.ZERO, new Money(3240), List.of()), true);
    }
}
