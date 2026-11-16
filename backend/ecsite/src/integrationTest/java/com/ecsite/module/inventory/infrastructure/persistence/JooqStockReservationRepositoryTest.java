package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.InventoryTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引当のリポジトリの統合テスト
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqStockReservationRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 期限切れの引当を消すテストの現在時刻
     *
     * <p>表の全体から期限切れを消すので、ロールバックしない片付けのジョブのテストが残す引当（2000 年）より前の時刻にする。
     */
    private static final Instant LONG_AGO = Instant.parse("1980-01-01T00:00:00Z");

    // ============================== Bean ==============================

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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("在庫のロック")
    class LockStocks {

        @Test
        @DisplayName("在庫のあるバリアントだけを、バリアント ID の順に数量つきで取得する")
        void locksStocksInVariantOrder() {
            // 準備：ID の順と違う順に作った 3 つの在庫と、在庫のないバリアント
            var first = UUID.fromString("00000000-0000-4000-8000-000000000001");
            var second = UUID.fromString("00000000-0000-4000-8000-000000000002");
            var third = UUID.fromString("00000000-0000-4000-8000-000000000003");
            var noStock = UUID.fromString("00000000-0000-4000-8000-000000000004");
            var secondStock = createStock(second, 7);
            var firstStock = createStock(first, 3);
            var thirdStock = createStock(third, 0);

            // 操作：ID の順と違う順に指定してロックする
            var locked = repository.lockStocks(List.of(third, noStock, first, second));

            // 確認：在庫のない 1 つを除き、ID の順に並ぶ
            assertThat(locked).containsExactly(new LockedStock(firstStock, first, 3), new LockedStock(secondStock, second, 7),
                    new LockedStock(thirdStock, third, 0));
        }
    }

    @Nested
    @DisplayName("有効な引当の合計")
    class SumActive {

        @Test
        @DisplayName("有効な引当だけを、在庫ごとに合計する")
        void sumsOnlyActiveReservations() {
            // 準備：在庫 A・B・C
            var stockA = createStock(UUID.randomUUID(), 10);
            var stockB = createStock(UUID.randomUUID(), 10);
            var stockC = createStock(UUID.randomUUID(), 10);
            var excluded = UUID.randomUUID();
            // 準備：数える引当（A に 2 と 3、B に 1）
            repository.replace(UUID.randomUUID(), Map.of(stockA, 2, stockB, 1), NOW.plusSeconds(60), NOW);
            repository.replace(UUID.randomUUID(), Map.of(stockA, 3), NOW.plusSeconds(60), NOW);
            // 準備：数えない引当（除く持ち主、期限切れ、期限ちょうど）
            repository.replace(excluded, Map.of(stockA, 4), NOW.plusSeconds(60), NOW);
            repository.replace(UUID.randomUUID(), Map.of(stockA, 5), NOW.minusSeconds(1), NOW.minusSeconds(60));
            repository.replace(UUID.randomUUID(), Map.of(stockB, 6), NOW, NOW.minusSeconds(60));

            // 操作：除く持ち主を指定して、3 つの在庫の引当を合計する
            var sums = repository.sumActiveReservations(List.of(stockA, stockB, stockC), excluded, NOW);

            // 確認：数える引当だけの合計で、引当のない C は含まない
            assertThat(sums).containsOnly(Map.entry(stockA, 5), Map.entry(stockB, 1));
        }
    }

    @Nested
    @DisplayName("持ち主の引当の置き換え")
    class Replace {

        @Test
        @DisplayName("持ち主の引当を置き換えると、前の引当は消えて新しい内容と数量になり、ほかの持ち主の引当は変わらない")
        void replacesReservationsOfHolder() {
            // 準備：持ち主の引当（A に 2、B に 1）と、ほかの持ち主の引当（A に 4）
            var stockA = createStock(UUID.randomUUID(), 10);
            var stockB = createStock(UUID.randomUUID(), 10);
            var holder = reserveForHolderAndOther(stockA, stockB);

            // 操作：持ち主の引当を、B に 5 で置き換える
            repository.replace(holder, Map.of(stockB, 5), NOW.plusSeconds(120), NOW);

            // 確認：A はほかの持ち主の 4 だけ、B は新しい 5
            assertThat(repository.sumActiveReservations(List.of(stockA, stockB), UUID.randomUUID(), NOW))
                    .containsOnly(Map.entry(stockA, 4), Map.entry(stockB, 5));
        }

        @Test
        @DisplayName("持ち主の引当を置き換えると、新しい期限になる")
        void replacesExpiresAt() {
            // 準備：持ち主の引当と、ほかの持ち主の引当（どちらも 60 秒後が期限）
            var stockA = createStock(UUID.randomUUID(), 10);
            var stockB = createStock(UUID.randomUUID(), 10);
            var holder = reserveForHolderAndOther(stockA, stockB);

            // 操作：持ち主の引当を、120 秒後が期限の内容で置き換える
            repository.replace(holder, Map.of(stockB, 5), NOW.plusSeconds(120), NOW);

            // 確認：90 秒後には、置き換えた引当だけが有効
            assertThat(repository.sumActiveReservations(List.of(stockB), UUID.randomUUID(), NOW.plusSeconds(90)))
                    .containsOnly(Map.entry(stockB, 5));
        }

        @Test
        @DisplayName("空の内容に置き換えると、持ち主の引当が全て消える")
        void replacesWithNothing() {
            // 準備：持ち主の引当
            var stock = createStock(UUID.randomUUID(), 10);
            var holder = UUID.randomUUID();
            repository.replace(holder, Map.of(stock, 2), NOW.plusSeconds(60), NOW);

            // 操作：空の内容に置き換える
            repository.replace(holder, Map.of(), NOW.plusSeconds(60), NOW);

            // 確認：引当がない
            assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), NOW)).isEmpty();
        }
    }

    @Nested
    @DisplayName("持ち主の引当の削除")
    class DeleteByHolder {

        @Test
        @DisplayName("持ち主の引当を消すと、その持ち主の引当だけが消える")
        void deletesReservationsOfHolder() {
            // 準備：持ち主の引当 2 と、ほかの持ち主の引当 3
            var stock = createStock(UUID.randomUUID(), 10);
            var holder = UUID.randomUUID();
            repository.replace(holder, Map.of(stock, 2), NOW.plusSeconds(60), NOW);
            repository.replace(UUID.randomUUID(), Map.of(stock, 3), NOW.plusSeconds(60), NOW);

            // 操作：持ち主の引当を消す
            repository.deleteByHolder(holder);

            // 確認：ほかの持ち主の 3 だけが残る
            assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), NOW))
                    .containsOnly(Map.entry(stock, 3));
        }

        @Test
        @DisplayName("引当のない持ち主の引当を消しても、何も起きない")
        void ignoresHolderWithoutReservations() {
            // 準備：持ち主の引当 2 と、ほかの持ち主の引当 3
            var stock = createStock(UUID.randomUUID(), 10);
            repository.replace(UUID.randomUUID(), Map.of(stock, 2), NOW.plusSeconds(60), NOW);
            repository.replace(UUID.randomUUID(), Map.of(stock, 3), NOW.plusSeconds(60), NOW);

            // 操作：引当のない持ち主の引当を消す
            repository.deleteByHolder(UUID.randomUUID());

            // 確認：合計が 5 のまま
            assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), NOW))
                    .containsOnly(Map.entry(stock, 5));
        }
    }

    @Nested
    @DisplayName("期限切れの引当の削除")
    class DeleteExpired {

        @Test
        @DisplayName("期限切れの引当を、1 回に件数の上限までしか消さない")
        void deletesExpiredReservationsUpToLimit() {
            // 準備：期限切れ 2 つ、期限ちょうど 1 つ、有効 1 つ
            createExpiringReservations();

            // 操作と確認：上限 1 件なら 1 件だけ消す
            assertThat(repository.deleteExpiredBefore(LONG_AGO, 1)).isEqualTo(1);

            // 操作と確認：上限 10 件なら、残りの期限切れの 1 件を消す
            assertThat(repository.deleteExpiredBefore(LONG_AGO, 10)).isEqualTo(1);

            // 操作と確認：期限切れが残っていなければ、何も消さない
            assertThat(repository.deleteExpiredBefore(LONG_AGO, 10)).isZero();
        }

        @Test
        @DisplayName("期限が指定した時刻ちょうどの引当と、後の引当は消さない")
        void keepsReservationsOnOrAfterBoundary() {
            // 準備：期限切れ 2 つ、期限ちょうど 1 つ、有効 1 つ
            var stock = createExpiringReservations();

            // 操作：期限切れを消す
            repository.deleteExpiredBefore(LONG_AGO, 10);

            // 確認：期限ちょうどと有効の 2 つが残り、どちらも 5 秒前の時点で有効
            assertThat(countReservations(stock)).isEqualTo(2L);
            assertThat(repository.sumActiveReservations(List.of(stock), UUID.randomUUID(), LONG_AGO.minusSeconds(5)))
                    .containsOnly(Map.entry(stock, 2));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 在庫の引当の行数を数える。
     *
     * @param stockItemId 在庫 ID
     * @return 引当の行数
     */
    private Long countReservations(UUID stockItemId) {
        return (long) dsl.fetchCount(STOCK_RESERVATION, STOCK_RESERVATION.STOCK_ITEM_ID.eq(stockItemId));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 在庫を作り、数量を入荷として設定する。
     *
     * @param variantId バリアント ID
     * @param quantity  在庫数
     * @return 在庫 ID
     */
    private UUID createStock(UUID variantId, int quantity) {
        var productId = UUID.randomUUID();
        if (quantity > 0) {
            inventory.stock(variantId, productId, quantity);
        } else {
            inventory.create(variantId, productId);
        }
        return dsl.select(STOCK_ITEM.ID).from(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId))
                .fetchSingle(STOCK_ITEM.ID);
    }

    /**
     * 持ち主の引当（A に 2、B に 1）と、ほかの持ち主の引当（A に 4）を作る。
     *
     * <p>どちらも期限は 60 秒後。
     *
     * @param stockA 在庫 A の ID
     * @param stockB 在庫 B の ID
     * @return 持ち主 ID
     */
    private UUID reserveForHolderAndOther(UUID stockA, UUID stockB) {
        var holder = UUID.randomUUID();
        repository.replace(holder, Map.of(stockA, 2, stockB, 1), NOW.plusSeconds(60), NOW);
        repository.replace(UUID.randomUUID(), Map.of(stockA, 4), NOW.plusSeconds(60), NOW);
        return holder;
    }

    /**
     * 1 つの在庫に、{@link #LONG_AGO} の時点で期限切れの引当 2 つ、期限ちょうどの引当 1 つ、有効な引当 1 つを作る。
     *
     * <p>どの引当も数量は 1。
     *
     * @return 在庫 ID
     */
    private UUID createExpiringReservations() {
        var stock = createStock(UUID.randomUUID(), 10);
        repository.replace(UUID.randomUUID(), Map.of(stock, 1), LONG_AGO.minusSeconds(20), LONG_AGO.minusSeconds(100));
        repository.replace(UUID.randomUUID(), Map.of(stock, 1), LONG_AGO.minusSeconds(10), LONG_AGO.minusSeconds(100));
        repository.replace(UUID.randomUUID(), Map.of(stock, 1), LONG_AGO, LONG_AGO.minusSeconds(100));
        repository.replace(UUID.randomUUID(), Map.of(stock, 1), LONG_AGO.plusSeconds(10), LONG_AGO.minusSeconds(100));
        return stock;
    }
}
