package com.ecsite.module.inventory.api;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.async.Concurrently;
import com.ecsite.support.async.LockWaits;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.InventoryTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 引当の統合テスト
 *
 * <p>同時の引当を確認するため、操作ごとにコミットする。ロールバックしない。
 */
@IntegrationTest
class StockReservationApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 引当の期限
     *
     * <p>現在時刻の 15 分後。
     */
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));

    /**
     * 同時に引き当てる持ち主の数
     */
    private static final int CONCURRENT_HOLDERS = 10;

    // ============================== Bean ==============================

    @Autowired
    StockReservationApi api;

    /**
     * 在庫の操作を、呼んだ側のトランザクションに加えるためのテンプレート
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

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
        productId = UUID.randomUUID();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("販売可能数")
    class SellableQuantity {

        @Test
        @DisplayName("BR-11 販売可能数は、在庫数から有効な引当を引いた数")
        void sellableQuantityExcludesActiveReservations() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 2), EXPIRES_AT);

            // 操作：持ち主を指定せずに販売可能数を求める
            var sellable = api.sellableQuantities(List.of(variant), null);

            // 確認：3
            assertThat(sellable).containsExactlyInAnyOrderEntriesOf(Map.of(variant, 3));
        }

        @Test
        @DisplayName("BR-11 持ち主を指定すると、その持ち主の引当を除いて数える")
        void sellableQuantityExcludesHolderReservations() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var holder = new ReservationHolderId(UUID.randomUUID());
            reserveInTransaction(holder, Map.of(variant, 2), EXPIRES_AT);

            // 操作：引き当てた持ち主を指定して販売可能数を求める
            var sellable = api.sellableQuantities(List.of(variant), holder);

            // 確認：在庫数と同じ 5
            assertThat(sellable).containsEntry(variant, 5);
        }

        @Test
        @DisplayName("指定した全てのバリアントの販売可能数を返し、在庫が作られていないバリアントは 0")
        void includesVariantsWithoutStock() {
            // 準備：在庫数 5 のうち 2 を引き当てたバリアントと、在庫が作られていないバリアント
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var withoutStock = new VariantId(UUID.randomUUID());
            reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 2), EXPIRES_AT);

            // 操作：2 つの販売可能数を求める
            var sellable = api.sellableQuantities(List.of(variant, withoutStock), null);

            // 確認：両方を含み、在庫が作られていないものは 0
            assertThat(sellable).containsExactlyInAnyOrderEntriesOf(Map.of(variant, 3, withoutStock, 0));
        }

        @Test
        @DisplayName("AC-84、BR-11 期限の切れた引当は数えない")
        void ignoresExpiredReservations() {
            // 準備：在庫数 5 のうち 2 を引き当て、期限ちょうどになる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 2), EXPIRES_AT);
            clock.setInstant(EXPIRES_AT);

            // 操作と確認：販売可能数が在庫数と同じ 5
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
        }
    }

    @Nested
    @DisplayName("引当")
    class Reserve {

        @Test
        @DisplayName("AC-82 1 品目でも足りなければ、足りない品目を返す")
        void returnsShortages() {
            // 準備：在庫数 5 と 1 の品目と、在庫が作られていない品目
            var plenty = new VariantId(inventory.stockNewVariant(productId, 5));
            var scarce = new VariantId(inventory.stockNewVariant(productId, 1));
            var withoutStock = new VariantId(UUID.randomUUID());

            // 操作：3 品目を引き当てる
            var shortages = reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(plenty, 2, scarce, 2, withoutStock, 1), EXPIRES_AT);

            // 確認：足りない 2 品目を返す
            assertThat(shortages).containsExactlyInAnyOrder(scarce, withoutStock);
        }

        @Test
        @DisplayName("AC-82 1 品目でも足りなければ、どれも引き当てない")
        void reservesAllOrNothing() {
            // 準備：在庫数 5 と 1 の品目と、在庫が作られていない品目
            var plenty = new VariantId(inventory.stockNewVariant(productId, 5));
            var scarce = new VariantId(inventory.stockNewVariant(productId, 1));
            var withoutStock = new VariantId(UUID.randomUUID());

            // 操作：3 品目を引き当てる
            reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(plenty, 2, scarce, 2, withoutStock, 1), EXPIRES_AT);

            // 確認：足りる品目も引き当てられず、販売可能数が在庫数のまま
            assertThat(api.sellableQuantities(List.of(plenty, scarce), null))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(plenty, 5, scarce, 1));
        }

        @Test
        @DisplayName("AC-82 全ての品目の在庫がなければ、全ての品目を足りないとして返す")
        void returnsAllWhenNoStock() {
            // 準備：在庫が作られていない 2 品目
            var first = new VariantId(UUID.randomUUID());
            var second = new VariantId(UUID.randomUUID());

            // 操作：2 品目を引き当てる
            var shortages = reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(first, 1, second, 2), EXPIRES_AT);

            // 確認：2 品目とも返す
            assertThat(shortages).containsExactlyInAnyOrder(first, second);
        }
    }

    @Nested
    @DisplayName("引き当て直しと解除")
    class ReplaceAndRelease {

        @Test
        @DisplayName("AC-84 同じ持ち主が引き当て直すときは、自分の引当を販売可能数から除いて判定する")
        void judgesWithoutOwnReservations() {
            // 準備：在庫数 1 の全てを自分で引き当てる
            var first = new VariantId(inventory.stockNewVariant(productId, 1));
            var second = new VariantId(inventory.stockNewVariant(productId, 3));
            var holder = reserveFirstOnly(first);

            // 操作：同じ持ち主で、同じ品目を含めて引き当て直す
            var shortages = reserveInTransaction(holder, Map.of(first, 1, second, 2), EXPIRES_AT);

            // 確認：自分の引当に阻まれず、足りない品目がない
            assertThat(shortages).isEmpty();
        }

        @Test
        @DisplayName("AC-84 同じ持ち主が引き当て直すと、前の引当を置き換える")
        void replacesReservationsOfSameHolder() {
            // 準備：在庫数 1 の全てを自分で引き当てる
            var first = new VariantId(inventory.stockNewVariant(productId, 1));
            var second = new VariantId(inventory.stockNewVariant(productId, 3));
            var holder = reserveFirstOnly(first);

            // 操作：同じ持ち主で、2 品目を引き当て直す
            reserveInTransaction(holder, Map.of(first, 1, second, 2), EXPIRES_AT);

            // 確認：前の引当は重ならず、新しい内容の分だけ販売可能数が減る
            assertThat(api.sellableQuantities(List.of(first, second), null))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(first, 0, second, 1));
        }

        @Test
        @DisplayName("AC-84 同じ持ち主が引き当て直して 1 品目でも足りなければ、足りない品目を返し、前の引当は残らない")
        void releasesPreviousReservationsWhenShort() {
            // 準備：在庫数 3 と 1 の品目のうち、持ち主が 1 つ目の品目を 2 つ引き当てる
            var first = new VariantId(inventory.stockNewVariant(productId, 3));
            var second = new VariantId(inventory.stockNewVariant(productId, 1));
            var holder = new ReservationHolderId(UUID.randomUUID());
            assertThat(reserveInTransaction(holder, Map.of(first, 2), EXPIRES_AT)).isEmpty();

            // 操作：同じ持ち主で、足りない品目を含めて引き当て直す
            var shortages = reserveInTransaction(holder, Map.of(first, 1, second, 2), EXPIRES_AT);

            // 確認：足りない品目を返す
            assertThat(shortages).containsExactly(second);

            // 確認：前の引当は残らず、販売可能数が在庫数のまま
            assertThat(api.sellableQuantities(List.of(first, second), null))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(first, 3, second, 1));
        }

        @Test
        @DisplayName("AC-84 引当を解くと、販売可能数が戻る")
        void releasesReservations() {
            // 準備：在庫数 2 の全てを引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 2));
            var holder = new ReservationHolderId(UUID.randomUUID());
            reserveInTransaction(holder, Map.of(variant, 2), EXPIRES_AT);

            // 操作：引当を解く
            releaseInTransaction(List.of(holder));

            // 確認：販売可能数が在庫数の 2 に戻る
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 2);
        }

        @Test
        @DisplayName("DC-552-4 複数の持ち主の引当を、まとめて解く")
        void releasesReservationsOfMultipleHolders() {
            // 準備：在庫数 5 のうち、持ち主 2 件が 1 と 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var first = new ReservationHolderId(UUID.randomUUID());
            var second = new ReservationHolderId(UUID.randomUUID());
            reserveInTransaction(first, Map.of(variant, 1), EXPIRES_AT);
            reserveInTransaction(second, Map.of(variant, 2), EXPIRES_AT);

            // 操作：持ち主 2 件の引当をまとめて解く
            releaseInTransaction(List.of(first, second));

            // 確認：販売可能数が在庫数の 5 に戻る
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
        }

        @Test
        @DisplayName("DC-552-4 渡していない持ち主の引当は残る")
        void keepsReservationsOfOtherHolders() {
            // 準備：在庫数 5 のうち、持ち主 3 件が 1、2、1 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var first = new ReservationHolderId(UUID.randomUUID());
            var second = new ReservationHolderId(UUID.randomUUID());
            var other = new ReservationHolderId(UUID.randomUUID());
            reserveInTransaction(first, Map.of(variant, 1), EXPIRES_AT);
            reserveInTransaction(second, Map.of(variant, 2), EXPIRES_AT);
            reserveInTransaction(other, Map.of(variant, 1), EXPIRES_AT);

            // 操作：持ち主 2 件の引当だけを解く
            releaseInTransaction(List.of(first, second));

            // 確認：渡していない持ち主の 1 だけが残り、販売可能数が 4
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 4);
        }

        @Test
        @DisplayName("DC-552-4 引当のない持ち主を含んでも、他の持ち主の引当を解く")
        void skipsHoldersWithoutReservations() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var holder = new ReservationHolderId(UUID.randomUUID());
            reserveInTransaction(holder, Map.of(variant, 2), EXPIRES_AT);

            // 操作：引当のない持ち主を含めて解く
            releaseInTransaction(List.of(new ReservationHolderId(UUID.randomUUID()), holder));

            // 確認：例外にならず、販売可能数が 5 に戻る
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
        }

        @Test
        @DisplayName("DC-552-4 空のコレクションを渡すと、何も変わらない")
        void doesNothingForEmptyHolders() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 2), EXPIRES_AT);

            // 操作：空のコレクションを渡して解く
            releaseInTransaction(List.of());

            // 確認：引当が残り、販売可能数が 3 のまま
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 3);
        }
    }

    @Nested
    @DisplayName("引当のロックの順序")
    class LockOrder {

        @Test
        @DisplayName("DC-552-1 引当は、持ち主の前の引当の行をロックしてから在庫の行をロックする（減算と同じ順序で、デッドロックにならない）")
        void locksReservationBeforeStock() throws Exception {
            // 準備：在庫数 5 のうち、持ち主が 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var holder = new ReservationHolderId(UUID.randomUUID());
            assertThat(reserveInTransaction(holder, Map.of(variant, 2), EXPIRES_AT)).isEmpty();
            var executor = Executors.newSingleThreadExecutor();
            try {
                var reserving = new AtomicReference<Future<Set<VariantId>>>();
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：テストのトランザクションが在庫の行をロックし、同じ持ち主の引き当て直しを在庫の行で待たせる
                    dsl.selectFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variant.value())).forUpdate().fetch();
                    reserving.set(executor.submit(() -> reserveInTransaction(holder, Map.of(variant, 3), EXPIRES_AT)));
                    LockWaits.awaitBlockedByThisTransaction(dsl);

                    // 確認：在庫の行を待つ引当が前の引当の行をロックしていれば、その行は読み飛ばされ、行が返らない
                    assertThat(dsl.selectFrom(STOCK_RESERVATION).where(STOCK_RESERVATION.HOLDER_ID.eq(holder.value()))
                            .forUpdate().skipLocked().fetch()).isEmpty();
                });

                // 確認：ロックを解くと、引き当て直しが上限の時間までに終わり、新しい内容の引当になる
                assertThat(reserving.get().get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEmpty();
                assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 2);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("同時の引当")
    class ConcurrentReserve {

        @Test
        @DisplayName("AC-83 同じ在庫に同時に引当が来ても、在庫数を超えて引き当てない")
        void reservesConcurrentlyWithinStock() throws Exception {
            // 準備：在庫数 1
            var variant = new VariantId(inventory.stockNewVariant(productId, 1));

            // 操作：10 人が同時に 1 つずつ引き当てる
            var results = Concurrently.run(CONCURRENT_HOLDERS, i -> reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 1), EXPIRES_AT));

            // 確認：1 人だけが引き当てられ、販売可能数が 0
            assertThat(results).filteredOn(Set::isEmpty).hasSize(1);
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 0);
        }

        @Test
        @DisplayName("AC-83 同時の引当で引き当てられなかった持ち主には、在庫が足りない品目を返す")
        void returnsShortagesToOthers() throws Exception {
            // 準備：在庫数 1
            var variant = new VariantId(inventory.stockNewVariant(productId, 1));

            // 操作：10 人が同時に 1 つずつ引き当てる
            var results = Concurrently.run(CONCURRENT_HOLDERS, i -> reserveInTransaction(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 1), EXPIRES_AT));

            // 確認：引き当てられなかった 9 人には、その品目が足りないと返る
            assertThat(results).filteredOn(shortages -> !shortages.isEmpty())
                    .hasSize(CONCURRENT_HOLDERS - 1)
                    .allSatisfy(shortages -> assertThat(shortages).containsExactly(variant));
        }
    }

    @Nested
    @DisplayName("トランザクションの前提")
    class TransactionPrecondition {

        @Test
        @DisplayName("引当は、トランザクションの外から呼ぶと失敗する")
        void reserveFailsOutsideTransaction() {
            // 準備：在庫数 5
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));

            // 操作と確認：トランザクションの外で引き当てると失敗する
            assertThatThrownBy(() -> api.reserve(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 2), EXPIRES_AT))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：販売可能数が在庫数のまま
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
        }

        @Test
        @DisplayName("引当の解除は、トランザクションの外から呼ぶと失敗する")
        void releaseFailsOutsideTransaction() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));
            var holder = new ReservationHolderId(UUID.randomUUID());
            assertThat(reserveInTransaction(holder, Map.of(variant, 2), EXPIRES_AT)).isEmpty();

            // 操作と確認：トランザクションの外で解除すると失敗する
            assertThatThrownBy(() -> api.release(List.of(holder))).isInstanceOf(IllegalTransactionStateException.class);

            // 確認：引当は解かれず、販売可能数が減ったまま
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 3);
        }

        @Test
        @DisplayName("減算は、トランザクションの外から呼ぶと失敗する")
        void commitFailsOutsideTransaction() {
            // 準備：在庫数 5
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));

            // 操作と確認：トランザクションの外で減算すると失敗する
            assertThatThrownBy(() -> api.commit(new ReservationHolderId(UUID.randomUUID()), Map.of(variant, 2), new OrderId(UUID.randomUUID()), "ORD-1"))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：販売可能数が在庫数のまま（在庫数が減っていない）
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
        }

        @Test
        @DisplayName("在庫数への戻しは、トランザクションの外から呼ぶと失敗する")
        void restoreFailsOutsideTransaction() {
            // 準備：在庫数 5
            var variant = new VariantId(inventory.stockNewVariant(productId, 5));

            // 操作と確認：トランザクションの外で戻すと失敗する
            assertThatThrownBy(() -> api.restore(Map.of(variant, 2), new OrderId(UUID.randomUUID()), "ORD-1"))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：販売可能数が在庫数のまま
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 5);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 新しい持ち主で、1 つ目の品目だけを 1 つ引き当てる。
     *
     * @param first 1 つ目の品目のバリアント ID
     * @return 持ち主 ID
     */
    private ReservationHolderId reserveFirstOnly(VariantId first) {
        var holder = new ReservationHolderId(UUID.randomUUID());
        assertThat(reserveInTransaction(holder, Map.of(first, 1), EXPIRES_AT)).isEmpty();
        return holder;
    }

    /**
     * 引当を、トランザクションの中で呼ぶ（引当は呼んだ側のトランザクションに加わる）。
     *
     * @param holderId   持ち主 ID
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空
     */
    private Set<VariantId> reserveInTransaction(ReservationHolderId holderId, Map<VariantId, Integer> quantities, Instant expiresAt) {
        return transactionTemplate.execute(status -> api.reserve(holderId, quantities, expiresAt));
    }

    /**
     * 引当の解除を、トランザクションの中で呼ぶ（解除は呼んだ側のトランザクションに加わる）。
     *
     * @param holderIds 持ち主 ID
     */
    private void releaseInTransaction(Collection<ReservationHolderId> holderIds) {
        transactionTemplate.executeWithoutResult(status -> api.release(holderIds));
    }
}
