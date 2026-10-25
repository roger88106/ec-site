package com.ecsite.module.inventory.api;

import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗向けの在庫の API の統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StockAvailabilityApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    StockAvailabilityApi api;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    StockReservationApi reservationApi;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @BeforeEach
    void setUp() {
        inventory = new InventoryTestData(stockItemRepository, NOW);
    }

    @Test
    @DisplayName("複数のバリアントの在庫の有無を返す。在庫数 0 と、在庫がまだないバリアントは「在庫なし」")
    void returnsAvailability() {
        var productId = UUID.randomUUID();
        var inStock = UUID.randomUUID();
        var soldOut = UUID.randomUUID();
        var withoutStock = UUID.randomUUID();
        inventory.stock(inStock, productId, 1);
        inventory.create(soldOut, productId);

        var availability = api.availabilityOf(List.of(inStock, soldOut, withoutStock));

        assertThat(availability).containsExactlyInAnyOrderEntriesOf(Map.of(
                inStock, true,
                soldOut, false,
                withoutStock, false));
    }

    @Test
    @DisplayName("BR-11 在庫数がすべて引き当てられたバリアントは「在庫なし」")
    void treatsFullyReservedAsOutOfStock() {
        var productId = UUID.randomUUID();
        var reserved = UUID.randomUUID();
        var partlyReserved = UUID.randomUUID();
        inventory.stock(reserved, productId, 1);
        inventory.stock(partlyReserved, productId, 2);
        // 期限は実際の時計より十分先にする
        var expiresAt = Instant.now().plus(Duration.ofHours(1));
        reservationApi.reserve(UUID.randomUUID(), Map.of(reserved, 1, partlyReserved, 1), expiresAt);

        var availability = api.availabilityOf(List.of(reserved, partlyReserved));

        assertThat(availability).containsExactlyInAnyOrderEntriesOf(Map.of(reserved, false, partlyReserved, true));
    }

    @Test
    @DisplayName("バリアントを指定しなければ空")
    void returnsEmptyForNoVariants() {
        assertThat(api.availabilityOf(List.of())).isEmpty();
    }
}
