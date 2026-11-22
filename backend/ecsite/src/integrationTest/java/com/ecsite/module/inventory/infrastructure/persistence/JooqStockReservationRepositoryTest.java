package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import com.ecsite.module.inventory.domain.stock.vo.StockItemId;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.persistence.QueryPlans;
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
            var first = new VariantId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
            var second = new VariantId(UUID.fromString("00000000-0000-4000-8000-000000000002"));
            var third = new VariantId(UUID.fromString("00000000-0000-4000-8000-000000000003"));
            var noStock = new VariantId(UUID.fromString("00000000-0000-4000-8000-000000000004"));
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
            var stockA = createStock(new VariantId(UUID.randomUUID()), 10);
            var stockB = createStock(new VariantId(UUID.randomUUID()), 10);
            var stockC = createStock(new VariantId(UUID.randomUUID()), 10);
            var excluded = new ReservationHolderId(UUID.randomUUID());
            // 準備：数える引当（A に 2 と 3、B に 1）
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stockA, 2, stockB, 1), NOW.plusSeconds(60), NOW);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stockA, 3), NOW.plusSeconds(60), NOW);
            // 準備：数えない引当（除く持ち主、期限切れ、期限ちょうど）
            repository.insert(excluded, Map.of(stockA, 4), NOW.plusSeconds(60), NOW);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stockA, 5), NOW.minusSeconds(1), NOW.minusSeconds(60));
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stockB, 6), NOW, NOW.minusSeconds(60));

            // 操作：除く持ち主を指定して、3 つの在庫の引当を合計する
            var sums = repository.sumActiveReservations(List.of(stockA, stockB, stockC), excluded, NOW);

            // 確認：数える引当だけの合計で、引当のない C は含まない
            assertThat(sums).containsOnly(Map.entry(stockA, 5), Map.entry(stockB, 1));
        }
    }

    @Nested
    @DisplayName("持ち主の引当の追加")
    class Insert {

        @Test
        @DisplayName("持ち主の前の引当を消してから加えると、前の引当は消えて新しい内容と数量になり、ほかの持ち主の引当は変わらない")
        void insertsAfterDeletingReservationsOfHolder() {
            // 準備：持ち主の引当（A に 2、B に 1）と、ほかの持ち主の引当（A に 4）
            var stockA = createStock(new VariantId(UUID.randomUUID()), 10);
            var stockB = createStock(new VariantId(UUID.randomUUID()), 10);
            var holder = reserveForHolderAndOther(stockA, stockB);

            // 操作：持ち主の引当を消し（呼ぶ側の手順）、B に 5 で加える
            repository.deleteByHolder(holder);
            repository.insert(holder, Map.of(stockB, 5), NOW.plusSeconds(120), NOW);

            // 確認：A はほかの持ち主の 4 だけ、B は新しい 5
            assertThat(repository.sumActiveReservations(List.of(stockA, stockB), new ReservationHolderId(UUID.randomUUID()), NOW))
                    .containsOnly(Map.entry(stockA, 4), Map.entry(stockB, 5));
        }

        @Test
        @DisplayName("持ち主の前の引当を消してから加えると、新しい期限になる")
        void insertsWithNewExpiresAt() {
            // 準備：持ち主の引当と、ほかの持ち主の引当（どちらも 60 秒後が期限）
            var stockA = createStock(new VariantId(UUID.randomUUID()), 10);
            var stockB = createStock(new VariantId(UUID.randomUUID()), 10);
            var holder = reserveForHolderAndOther(stockA, stockB);

            // 操作：持ち主の引当を消し（呼ぶ側の手順）、120 秒後が期限の内容で加える
            repository.deleteByHolder(holder);
            repository.insert(holder, Map.of(stockB, 5), NOW.plusSeconds(120), NOW);

            // 確認：90 秒後には、新しく加えた引当だけが有効
            assertThat(repository.sumActiveReservations(List.of(stockB), new ReservationHolderId(UUID.randomUUID()), NOW.plusSeconds(90)))
                    .containsOnly(Map.entry(stockB, 5));
        }

        @Test
        @DisplayName("空の内容を加えても、何も加わらず、持ち主の前の引当は消えない")
        void insertsNothing() {
            // 準備：持ち主の引当
            var stock = createStock(new VariantId(UUID.randomUUID()), 10);
            var holder = new ReservationHolderId(UUID.randomUUID());
            repository.insert(holder, Map.of(stock, 2), NOW.plusSeconds(60), NOW);

            // 操作：空の内容を加える
            repository.insert(holder, Map.of(), NOW.plusSeconds(60), NOW);

            // 確認：持ち主の前の引当は残る（消すのは呼ぶ側）
            assertThat(repository.sumActiveReservations(List.of(stock), new ReservationHolderId(UUID.randomUUID()), NOW))
                    .containsOnly(Map.entry(stock, 2));
        }
    }

    @Nested
    @DisplayName("持ち主の引当の削除")
    class DeleteByHolder {

        @Test
        @DisplayName("持ち主の引当を消すと、その持ち主の引当だけが消える")
        void deletesReservationsOfHolder() {
            // 準備：持ち主の引当 2 と、ほかの持ち主の引当 3
            var stock = createStock(new VariantId(UUID.randomUUID()), 10);
            var holder = new ReservationHolderId(UUID.randomUUID());
            repository.insert(holder, Map.of(stock, 2), NOW.plusSeconds(60), NOW);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 3), NOW.plusSeconds(60), NOW);

            // 操作：持ち主の引当を消す
            repository.deleteByHolder(holder);

            // 確認：ほかの持ち主の 3 だけが残る
            assertThat(repository.sumActiveReservations(List.of(stock), new ReservationHolderId(UUID.randomUUID()), NOW))
                    .containsOnly(Map.entry(stock, 3));
        }

        @Test
        @DisplayName("引当のない持ち主の引当を消しても、何も起きない")
        void ignoresHolderWithoutReservations() {
            // 準備：持ち主の引当 2 と、ほかの持ち主の引当 3
            var stock = createStock(new VariantId(UUID.randomUUID()), 10);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 2), NOW.plusSeconds(60), NOW);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 3), NOW.plusSeconds(60), NOW);

            // 操作：引当のない持ち主の引当を消す
            repository.deleteByHolder(new ReservationHolderId(UUID.randomUUID()));

            // 確認：合計が 5 のまま
            assertThat(repository.sumActiveReservations(List.of(stock), new ReservationHolderId(UUID.randomUUID()), NOW))
                    .containsOnly(Map.entry(stock, 5));
        }
    }

    @Nested
    @DisplayName("複数の持ち主の引当の削除")
    class DeleteByHolders {

        @Test
        @DisplayName("複数の持ち主の引当を消すと、渡した持ち主の引当だけが消える")
        void deletesReservationsOfHolders() {
            // 準備：2 人の持ち主の引当 2 と 3 と、ほかの持ち主の引当 4
            var stock = createStock(new VariantId(UUID.randomUUID()), 10);
            var first = new ReservationHolderId(UUID.randomUUID());
            var second = new ReservationHolderId(UUID.randomUUID());
            repository.insert(first, Map.of(stock, 2), NOW.plusSeconds(60), NOW);
            repository.insert(second, Map.of(stock, 3), NOW.plusSeconds(60), NOW);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 4), NOW.plusSeconds(60), NOW);

            // 操作：2 人の引当をまとめて消す
            repository.deleteByHolders(List.of(first, second));

            // 確認：ほかの持ち主の 4 だけが残る
            assertThat(repository.sumActiveReservations(List.of(stock), new ReservationHolderId(UUID.randomUUID()), NOW))
                    .containsOnly(Map.entry(stock, 4));
        }

        @Test
        @DisplayName("空のコレクションを渡すと、何も消えない")
        void deletesNothingForEmptyHolders() {
            // 準備：持ち主の引当 2
            var stock = createStock(new VariantId(UUID.randomUUID()), 10);
            repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 2), NOW.plusSeconds(60), NOW);

            // 操作：空のコレクションを渡す
            repository.deleteByHolders(List.of());

            // 確認：合計が 2 のまま
            assertThat(repository.sumActiveReservations(List.of(stock), new ReservationHolderId(UUID.randomUUID()), NOW))
                    .containsOnly(Map.entry(stock, 2));
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
        @DisplayName("選ぶ行を再評価する計画でも、1 回に件数の上限までしか消さない")
        void deletesUpToLimitEvenIfSubqueryIsRescanned() {
            // 準備：期限切れ 2 つ、期限ちょうど 1 つ、有効 1 つと、選ぶ行を外側の行ごとに再評価する計画
            createExpiringReservations();
            QueryPlans.forceSubqueryRescan(dsl);

            // 操作と確認：上限 1 件なら 1 件だけ消す
            assertThat(repository.deleteExpiredBefore(LONG_AGO, 1)).isEqualTo(1);
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
            assertThat(repository.sumActiveReservations(List.of(stock), new ReservationHolderId(UUID.randomUUID()), LONG_AGO.minusSeconds(5)))
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
    private Long countReservations(StockItemId stockItemId) {
        return (long) dsl.fetchCount(STOCK_RESERVATION, STOCK_RESERVATION.STOCK_ITEM_ID.eq(stockItemId.value()));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 在庫を作り、数量を入荷として設定する。
     *
     * @param variantId バリアント ID
     * @param quantity  在庫数
     * @return 在庫 ID
     */
    private StockItemId createStock(VariantId variantId, int quantity) {
        var productId = UUID.randomUUID();
        if (quantity > 0) {
            inventory.stock(variantId.value(), productId, quantity);
        } else {
            inventory.create(variantId.value(), productId);
        }
        return new StockItemId(dsl.select(STOCK_ITEM.ID).from(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId.value()))
                .fetchSingle(STOCK_ITEM.ID));
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
    private ReservationHolderId reserveForHolderAndOther(StockItemId stockA, StockItemId stockB) {
        var holder = new ReservationHolderId(UUID.randomUUID());
        repository.insert(holder, Map.of(stockA, 2, stockB, 1), NOW.plusSeconds(60), NOW);
        repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stockA, 4), NOW.plusSeconds(60), NOW);
        return holder;
    }

    /**
     * 1 つの在庫に、{@link #LONG_AGO} の時点で期限切れの引当 2 つ、期限ちょうどの引当 1 つ、有効な引当 1 つを作る。
     *
     * <p>どの引当も数量は 1。
     *
     * @return 在庫 ID
     */
    private StockItemId createExpiringReservations() {
        var stock = createStock(new VariantId(UUID.randomUUID()), 10);
        repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 1), LONG_AGO.minusSeconds(20), LONG_AGO.minusSeconds(100));
        repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 1), LONG_AGO.minusSeconds(10), LONG_AGO.minusSeconds(100));
        repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 1), LONG_AGO, LONG_AGO.minusSeconds(100));
        repository.insert(new ReservationHolderId(UUID.randomUUID()), Map.of(stock, 1), LONG_AGO.plusSeconds(10), LONG_AGO.minusSeconds(100));
        return stock;
    }
}
