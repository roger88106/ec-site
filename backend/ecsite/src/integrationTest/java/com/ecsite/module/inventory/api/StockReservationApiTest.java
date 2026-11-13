package com.ecsite.module.inventory.api;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引当の統合テスト
 *
 * <p>同時の引当を確認するため、操作ごとにコミットする。ロールバックしない。
 */
@IntegrationTest
class StockReservationApiTest {

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 引当の期限（15 分後）
     */
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));

    @Autowired
    StockReservationApi api;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    MutableClock clock;

    /**
     * 在庫を作る商品の ID
     */
    private UUID productId;

    @BeforeEach
    void setUp() {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        productId = UUID.randomUUID();
    }

    @Test
    @DisplayName("BR-11 販売可能数は在庫数から有効な引当を引いた数。持ち主を指定するとその引当を除き、在庫がなければ 0")
    void sellableQuantityExcludesActiveReservations() {
        var variant = inventory.stockNewVariant(productId, 5);
        var withoutStock = UUID.randomUUID();
        var holder = UUID.randomUUID();
        api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);

        assertThat(api.sellableQuantities(List.of(variant, withoutStock), null))
                .containsExactlyInAnyOrderEntriesOf(Map.of(variant, 3, withoutStock, 0));
        assertThat(api.sellableQuantities(List.of(variant), holder)).containsEntry(variant, 5);
    }

    @Test
    @DisplayName("BR-11 期限の切れた引当は数えない")
    void ignoresExpiredReservations() {
        var variant = inventory.stockNewVariant(productId, 5);
        api.reserve(UUID.randomUUID(), Map.of(variant, 2), EXPIRES_AT);

        clock.setInstant(EXPIRES_AT);

        assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
    }

    @Test
    @DisplayName("AC-82 1 品目でも足りなければ、どれも引き当てず、足りない品目を返す")
    void reservesAllOrNothing() {
        var plenty = inventory.stockNewVariant(productId, 5);
        var scarce = inventory.stockNewVariant(productId, 1);
        var withoutStock = UUID.randomUUID();

        var shortages = api.reserve(UUID.randomUUID(), Map.of(plenty, 2, scarce, 2, withoutStock, 1), EXPIRES_AT);

        assertThat(shortages).containsExactlyInAnyOrder(scarce, withoutStock);
        assertThat(api.sellableQuantities(List.of(plenty, scarce), null))
                .containsExactlyInAnyOrderEntriesOf(Map.of(plenty, 5, scarce, 1));
    }

    @Test
    @DisplayName("AC-84 同じ持ち主が引き当て直すと、前の引当を置き換える。自分の引当は販売可能数から除いて判定する")
    void replacesReservationsOfSameHolder() {
        var first = inventory.stockNewVariant(productId, 1);
        var second = inventory.stockNewVariant(productId, 3);
        var holder = UUID.randomUUID();
        assertThat(api.reserve(holder, Map.of(first, 1), EXPIRES_AT)).isEmpty();

        var shortages = api.reserve(holder, Map.of(first, 1, second, 2), EXPIRES_AT);

        assertThat(shortages).isEmpty();
        assertThat(api.sellableQuantities(List.of(first, second), null))
                .containsExactlyInAnyOrderEntriesOf(Map.of(first, 0, second, 1));
    }

    @Test
    @DisplayName("AC-84 引当を解くと、販売可能数が戻る")
    void releasesReservations() {
        var variant = inventory.stockNewVariant(productId, 2);
        var holder = UUID.randomUUID();
        api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);

        api.release(holder);

        assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 2);
    }

    @Test
    @DisplayName("AC-83 同じ在庫に同時に引当が来ても、在庫数を超えて引き当てない")
    void reservesConcurrentlyWithinStock() throws Exception {
        var variant = inventory.stockNewVariant(productId, 1);
        int holders = 10;

        var ready = new CountDownLatch(holders);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(holders);
        List<Set<UUID>> results = new ArrayList<>();
        try {
            List<Future<Set<UUID>>> futures = new ArrayList<>();
            for (int i = 0; i < holders; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return api.reserve(UUID.randomUUID(), Map.of(variant, 1), EXPIRES_AT);
                }));
            }
            assertThat(ready.await(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                results.add(future.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
        } finally {
            // close は全部の処理の終わりを待つので、途中で失敗すると start を待つスレッドが残る。割り込んで止める
            executor.shutdownNow();
        }

        assertThat(results).filteredOn(Set::isEmpty).hasSize(1);
        assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 0);
    }
}
