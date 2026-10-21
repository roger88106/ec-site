package com.ecsite.module.inventory.presentation;

import com.ecsite.api.admin.model.ManualStockAdjustmentReason;
import com.ecsite.api.admin.model.StockAdjustmentReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在庫の型と、生成したリクエスト・レスポンスのモデルの、列挙の変換の単体テスト
 */
class InventoryMapperTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ManualStockAdjustmentReason.class)
    @DisplayName("AC-277 リクエストの調整理由の全ての値を、ドメインの同じ名前の値に写す")
    void mapsEveryRequestReason(ManualStockAdjustmentReason reason) {
        assertThat(InventoryMapper.toDomainReason(reason))
                .isEqualTo(com.ecsite.module.inventory.domain.stock.StockAdjustmentReason.valueOf(reason.name()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(com.ecsite.module.inventory.domain.stock.StockAdjustmentReason.class)
    @DisplayName("AC-277 ドメインの調整理由の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryDomainReason(com.ecsite.module.inventory.domain.stock.StockAdjustmentReason reason) {
        assertThat(InventoryMapper.toResponseReason(reason)).isEqualTo(StockAdjustmentReason.valueOf(reason.name()));
    }
}
