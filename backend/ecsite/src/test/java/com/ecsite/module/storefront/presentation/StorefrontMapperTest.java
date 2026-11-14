package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.model.ProductSort;
import com.ecsite.api.store.model.UnpurchasableReason;
import com.ecsite.module.storefront.application.model.CartView;
import com.ecsite.module.storefront.application.model.ProductCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗の型を、生成したレスポンスのモデルに変換するときの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。項目の変換と応答の形は、店舗の API の統合テストと契約のテストが確認する。
 */
class StorefrontMapperTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(CartView.UnpurchasableReason.class)
    @DisplayName("AC-277 店舗の購入できない理由の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryReason(CartView.UnpurchasableReason reason) {
        // 操作と確認：同じ名前の値
        assertThat(StorefrontMapper.toUnpurchasableReason(reason)).isEqualTo(UnpurchasableReason.valueOf(reason.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(ProductSort.class)
    @DisplayName("AC-277 並び順の全ての値を、店舗のサービスの同じ名前の値に写す")
    void mapsEverySort(ProductSort sort) {
        // 操作と確認：同じ名前の値
        assertThat(new StorefrontMapper().toSort(sort)).isEqualTo(ProductCondition.Sort.valueOf(sort.name()));
    }
}
