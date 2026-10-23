package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.model.UnpurchasableReason;
import com.ecsite.module.storefront.application.model.CartView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗の型を、生成したレスポンスのモデルに変換するときの、列挙の変換の単体テスト
 */
class StorefrontMapperTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(CartView.UnpurchasableReason.class)
    @DisplayName("AC-277 店舗の購入できない理由の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryReason(CartView.UnpurchasableReason reason) {
        assertThat(StorefrontMapper.toUnpurchasableReason(reason)).isEqualTo(UnpurchasableReason.valueOf(reason.name()));
    }
}
