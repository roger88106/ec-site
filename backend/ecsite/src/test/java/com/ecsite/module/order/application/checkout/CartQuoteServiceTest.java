package com.ecsite.module.order.application.checkout;

import com.ecsite.module.order.api.CartQuote;
import com.ecsite.module.order.domain.quote.UnpurchasableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 見積もりを公開する型に変換するときの、列挙の変換の単体テスト
 */
class CartQuoteServiceTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(UnpurchasableReason.class)
    @DisplayName("AC-277 購入できない理由の全ての値を、公開する型の同じ名前の値に写す")
    void mapsEveryReason(UnpurchasableReason reason) {
        assertThat(CartQuoteService.toReason(reason)).isEqualTo(CartQuote.UnpurchasableReason.valueOf(reason.name()));
    }
}
