package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
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
 * 引当のリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqStockReservationRepositoryTest {

    /**
     * 現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    StockReservationRepository repository;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        inventory = new InventoryTestData(stockItemRepository, NOW);
    }

    @Test
    @DisplayName("バリアントの在庫を、バリアント ID の順に数量つきで取得し、在庫のないバリアントは含まない")
    void locksStocksInVariantOrder() {
        var first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        var second = UUID.fromString("00000000-0000-4000-8000-000000000002");
        var third = UUID.fromString("00000000-0000-4000-8000-000000000003");
        var noStock = UUID.fromString("00000000-0000-4000-8000-000000000004");
        var secondStock = createStock(second, 7);
        var firstStock = createStock(first, 3);
        var thirdStock = createStock(third, 0);

        var locked = repository.lockStocks(List.of(third, noStock, first, second));

        assertThat(locked).containsExactly(new LockedStock(firstStock, first, 3), new LockedStock(secondStock, second, 7),
                new LockedStock(thirdStock, third, 0));
    }

    @Test
    @DisplayName("有効な引当を在庫ごとに合計する。期限が切れた引当（期限ちょうどを含む）と除く持ち主の引当は含めず、引当のない在庫は含まない")
    void sumsOnlyActiveReservations() {
        var stockA = createStock(UUID.randomUUID(), 10);
        var stockB = createStock(UUID.randomUUID(), 10);
        var stockC = createStock(UUID.randomUUID(), 10);
        var holder1 = UUID.randomUUID();
        var holder2 = UUID.randomUUID();
        var excluded = UUID.randomUUID();
        var expiredHolder = UUID.randomUUID();
        var onDeadlineHolder = UUID.randomUUID();
        repository.replace(holder1, Map.of(stockA, 2, stockB, 1), NOW.plusSeconds(60), NOW);
        repository.replace(holder2, Map.of(stockA, 3), NOW.plusSeconds(60), NOW);
        repository.replace(excluded, Map.of(stockA, 4), NOW.plusSeconds(60), NOW);
        repository.replace(expiredHolder, Map.of(stockA, 5), NOW.minusSeconds(1), NOW.minusSeconds(60));
        repository.replace(onDeadlineHolder, Map.of(stockB, 6), NOW, NOW.minusSeconds(60));

        var sums = repository.sumActiveReservations(List.of(stockA, stockB, stockC), excluded, NOW);

        assertThat(sums).containsOnly(Map.entry(stockA, 5), Map.entry(stockB, 1));
    }

    @Test
    @DisplayName("持ち主の引当を置き換えると、前の引当は消えて新しい内容と数量になり、ほかの持ち主の引当は変わらない")
    void replacesReservationsOfHolder() {
        var stockA = createStock(UUID.randomUUID(), 10);
        var stockB = createStock(UUID.randomUUID(), 10);
        var holder = UUID.randomUUID();
        var other = UUID.randomUUID();
        var observer = UUID.randomUUID();
        repository.replace(holder, Map.of(stockA, 2, stockB, 1), NOW.plusSeconds(60), NOW);
        repository.replace(other, Map.of(stockA, 4), NOW.plusSeconds(60), NOW);

        repository.replace(holder, Map.of(stockB, 5), NOW.plusSeconds(120), NOW);

        assertThat(repository.sumActiveReservations(List.of(stockA, stockB), observer, NOW))
                .containsOnly(Map.entry(stockA, 4), Map.entry(stockB, 5));
        assertThat(repository.sumActiveReservations(List.of(stockB), observer, NOW.plusSeconds(90)))
                .containsOnly(Map.entry(stockB, 5));
    }

    @Test
    @DisplayName("空の内容に置き換えると、持ち主の引当が全て消える")
    void replacesWithNothing() {
        var stock = createStock(UUID.randomUUID(), 10);
        var holder = UUID.randomUUID();
        repository.replace(holder, Map.of(stock, 2), NOW.plusSeconds(60), NOW);

        repository.replace(holder, Map.of(), NOW.plusSeconds(60), NOW);

        assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), NOW)).isEmpty();
    }

    @Test
    @DisplayName("持ち主の引当を消すと、その持ち主の引当だけが消える。引当のない持ち主では何も起きない")
    void deletesReservationsOfHolder() {
        var stock = createStock(UUID.randomUUID(), 10);
        var holder = UUID.randomUUID();
        var other = UUID.randomUUID();
        repository.replace(holder, Map.of(stock, 2), NOW.plusSeconds(60), NOW);
        repository.replace(other, Map.of(stock, 3), NOW.plusSeconds(60), NOW);

        repository.deleteByHolder(holder);
        repository.deleteByHolder(UUID.randomUUID());

        assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), NOW))
                .containsOnly(Map.entry(stock, 3));
    }

    @Test
    @DisplayName("期限が指定した時刻より前の引当だけを消す。ちょうどの時刻のものは残り、件数の上限までしか消さない")
    void deletesExpiredReservationsUpToLimit() {
        // 表の全体から期限切れを消すので、ロールバックしない他のテストの引当（2026 年）に当たらない過去の時刻にする
        var now = Instant.parse("2000-01-01T00:00:00Z");
        var stock = createStock(UUID.randomUUID(), 10);
        var expired1 = UUID.randomUUID();
        var expired2 = UUID.randomUUID();
        var onBoundary = UUID.randomUUID();
        var alive = UUID.randomUUID();
        repository.replace(expired1, Map.of(stock, 1), now.minusSeconds(20), now.minusSeconds(100));
        repository.replace(expired2, Map.of(stock, 1), now.minusSeconds(10), now.minusSeconds(100));
        repository.replace(onBoundary, Map.of(stock, 1), now, now.minusSeconds(100));
        repository.replace(alive, Map.of(stock, 1), now.plusSeconds(10), now.minusSeconds(100));

        assertThat(repository.deleteExpiredBefore(now, 1)).isEqualTo(1);
        assertThat(repository.deleteExpiredBefore(now, 10)).isEqualTo(1);
        assertThat(repository.deleteExpiredBefore(now, 10)).isZero();

        assertThat(countReservations(stock)).isEqualTo(2L);
        assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), now.minusSeconds(5)))
                .containsOnly(Map.entry(stock, 2));
    }

    // ============================== 補助 ==============================

    /**
     * 在庫を作り、数量を入荷として設定して、在庫 ID を返す
     */
    private UUID createStock(UUID variantId, int quantity) {
        var productId = UUID.randomUUID();
        if (quantity > 0) {
            inventory.stock(variantId, productId, quantity);
        } else {
            inventory.create(variantId, productId);
        }
        return dsl.resultQuery("select id from inventory.stock_item where variant_id = ?", variantId)
                .fetchSingle(0, UUID.class);
    }

    /**
     * 在庫の引当の行数を数える
     */
    private Long countReservations(UUID stockItemId) {
        return dsl.resultQuery("select count(*) from inventory.stock_reservation where stock_item_id = ?", stockItemId)
                .fetchSingle(0, Long.class);
    }
}
