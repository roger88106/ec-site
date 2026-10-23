package com.ecsite.module.storefront.infrastructure.directory;

import com.ecsite.module.order.api.CartQuote;
import com.ecsite.module.storefront.application.model.CartView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文モジュールの型を、店舗モジュールの型に変換するときの、列挙の変換の単体テスト
 */
class OrderApiDirectoryTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(CartQuote.UnpurchasableReason.class)
    @DisplayName("AC-277 注文の API の購入できない理由の全ての値を、店舗の購入できない理由の同じ名前の値に写す")
    void mapsEveryReason(CartQuote.UnpurchasableReason reason) {
        assertThat(OrderApiDirectory.toReason(reason)).isEqualTo(CartView.UnpurchasableReason.valueOf(reason.name()));
    }
}
