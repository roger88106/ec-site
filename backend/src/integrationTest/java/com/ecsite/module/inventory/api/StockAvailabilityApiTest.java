package com.ecsite.module.inventory.api;

import com.ecsite.IntegrationTest;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗向けの在庫の API の統合テスト（T410）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StockAvailabilityApiTest {

    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    StockAvailabilityApi api;

    @Autowired
    StockItemRepository stockItemRepository;

    @Test
    @DisplayName("複数のバリアントの在庫の有無を返す。在庫数 0 と、在庫がまだないバリアントは「在庫なし」")
    void returnsAvailability() {
        var productId = UUID.randomUUID();
        var inStock = UUID.randomUUID();
        var soldOut = UUID.randomUUID();
        var withoutStock = UUID.randomUUID();
        stockItemRepository.createIfAbsent(inStock, productId);
        stockItemRepository.createIfAbsent(soldOut, productId);
        stockItemRepository.adjust(inStock, StockAdjustment.create(1, StockAdjustmentReason.ARRIVAL, null, NOW));

        var availability = api.availabilityOf(List.of(inStock, soldOut, withoutStock));

        assertThat(availability).containsExactlyInAnyOrderEntriesOf(Map.of(
                inStock, true,
                soldOut, false,
                withoutStock, false));
    }

    @Test
    @DisplayName("バリアントを指定しなければ空")
    void returnsEmptyForNoVariants() {
        assertThat(api.availabilityOf(List.of())).isEmpty();
    }
}
