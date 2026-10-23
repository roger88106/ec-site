package com.ecsite.module.order.application.checkout;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.api.CartQuote;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.UnpurchasableReason;
import com.ecsite.module.order.domain.quote.VariantInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 見積もりを公開する型に変換するときの、列挙の変換と項目の対応の単体テスト
 */
class CartQuoteServiceTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(UnpurchasableReason.class)
    @DisplayName("AC-277 購入できない理由の全ての値を、公開する型の同じ名前の値に写す")
    void mapsEveryReason(UnpurchasableReason reason) {
        assertThat(CartQuoteService.toReason(reason)).isEqualTo(CartQuote.UnpurchasableReason.valueOf(reason.name()));
    }

    @Test
    @DisplayName("AC-277 見積もりの全ての項目（品目、軸の値、金額、税額）を、公開する型の同じ値に写す")
    void mapsEveryField() {
        var cartId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var variant = new VariantInfo(variantId, productId, "P-1", "V-1", "シャツ",
                List.of(new AxisValue("色", "赤"), new AxisValue("サイズ", "M")), new Money(1000),
                UUID.randomUUID(), "https://example.com/a.png", true, false);
        var rate = new BigDecimal("10");
        var purchasable = new QuoteItem(variant, 2, rate, new Money(1100), new Money(2200), null);
        var unpurchasable = new QuoteItem(variant, 1, rate, new Money(1100), new Money(1100),
                UnpurchasableReason.OUT_OF_STOCK);
        var amounts = new Amounts(new Money(2200), new Money(500), new Money(2700),
                List.of(new TaxAmount(rate, new Money(2700), new Money(245))));
        var quote = new Quote(List.of(purchasable, unpurchasable), amounts, false);

        var actual = CartQuoteService.toCartQuote(cartId, quote);

        var expectedAxes = List.of(new CartQuote.AxisValue("色", "赤"), new CartQuote.AxisValue("サイズ", "M"));
        assertThat(actual).isEqualTo(new CartQuote(cartId,
                List.of(new CartQuote.Item(variantId, productId, "シャツ", expectedAxes,
                                "https://example.com/a.png", new Money(1000), new Money(1100), 2, new Money(2200),
                                null),
                        new CartQuote.Item(variantId, productId, "シャツ", expectedAxes,
                                "https://example.com/a.png", new Money(1000), new Money(1100), 1, new Money(1100),
                                CartQuote.UnpurchasableReason.OUT_OF_STOCK)),
                new CartQuote.Amounts(new Money(2200), new Money(500), new Money(2700),
                        List.of(new CartQuote.TaxAmount(rate, new Money(2700), new Money(245)))),
                false));
    }

    @Test
    @DisplayName("AC-277 画像がない品目と、注文できる見積もりを、そのまま写す")
    void mapsNullImageAndOrderable() {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-2", "V-2", "靴",
                List.of(), new Money(3000), UUID.randomUUID(), null, true, false);
        var item = new QuoteItem(variant, 1, new BigDecimal("8"), new Money(3240), new Money(3240), null);
        var quote = new Quote(List.of(item),
                new Amounts(new Money(3240), Money.ZERO, new Money(3240), List.of()), true);

        var actual = CartQuoteService.toCartQuote(UUID.randomUUID(), quote);

        assertThat(actual.orderable()).isTrue();
        assertThat(actual.items()).singleElement().satisfies(i -> {
            assertThat(i.imageUrl()).isNull();
            assertThat(i.axisValues()).isEmpty();
            assertThat(i.unpurchasableReason()).isNull();
        });
        assertThat(actual.amounts().taxes()).isEmpty();
    }
}
