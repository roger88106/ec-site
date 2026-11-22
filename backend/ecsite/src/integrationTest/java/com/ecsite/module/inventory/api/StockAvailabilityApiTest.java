package com.ecsite.module.inventory.api;

import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.InventoryTestData;
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
 * 在庫の有無の API の統合テスト
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StockAvailabilityApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    // ============================== Bean ==============================

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

    // ============================== テスト ==============================

    @Test
    @DisplayName("複数のバリアントの在庫の有無を、まとめて返す")
    void returnsAvailability() {
        // 準備：在庫数 1、在庫数 0、在庫が作られていない、の 3 つのバリアント
        var productId = UUID.randomUUID();
        var inStock = new VariantId(UUID.randomUUID());
        var soldOut = new VariantId(UUID.randomUUID());
        var withoutStock = new VariantId(UUID.randomUUID());
        inventory.stock(inStock.value(), productId, 1);
        inventory.create(soldOut.value(), productId);

        // 操作：3 つの在庫の有無を求める
        var availability = api.availabilityOf(List.of(inStock, soldOut, withoutStock));

        // 確認：在庫数 1 だけが「在庫あり」で、在庫数 0 と在庫が作られていないものは「在庫なし」
        assertThat(availability).containsExactlyInAnyOrderEntriesOf(Map.of(
                inStock, true,
                soldOut, false,
                withoutStock, false));
    }

    @Test
    @DisplayName("BR-11 在庫数がすべて引き当てられたバリアントは「在庫なし」")
    void treatsFullyReservedAsOutOfStock() {
        // 準備：在庫数 1 の全てと、在庫数 2 のうち 1 を引き当てる
        var productId = UUID.randomUUID();
        var reserved = new VariantId(UUID.randomUUID());
        var partlyReserved = new VariantId(UUID.randomUUID());
        inventory.stock(reserved.value(), productId, 1);
        inventory.stock(partlyReserved.value(), productId, 2);
        var expiresAt = NOW.plus(Duration.ofHours(1));
        reservationApi.reserve(new ReservationHolderId(UUID.randomUUID()), Map.of(reserved, 1, partlyReserved, 1), expiresAt);

        // 操作：在庫の有無を求める
        var availability = api.availabilityOf(List.of(reserved, partlyReserved));

        // 確認：全て引き当てたものだけが「在庫なし」
        assertThat(availability).containsExactlyInAnyOrderEntriesOf(Map.of(reserved, false, partlyReserved, true));
    }

    @Test
    @DisplayName("バリアントを指定しなければ空")
    void returnsEmptyForNoVariants() {
        // 操作と確認：空
        assertThat(api.availabilityOf(List.of())).isEmpty();
    }
}
