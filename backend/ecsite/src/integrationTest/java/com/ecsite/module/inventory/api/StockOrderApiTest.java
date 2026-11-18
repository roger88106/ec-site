package com.ecsite.module.inventory.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.port.StockQueryRepository;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文による在庫の増減の統合テスト
 *
 * <p>操作ごとにコミットする。ロールバックしない。
 *
 * <p>確認の期待値は、準備した物から取り出さない。複数のテストで使う値は定数にし、1 つのテストの中だけで使う在庫数などは、
 * 準備と確認に同じ数を書く。
 */
@IntegrationTest
class StockOrderApiTest {

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
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-482915";

    /**
     * 減算する在庫の在庫数
     */
    private static final int STOCK_QUANTITY = 5;

    /**
     * 注文数
     */
    private static final int ORDER_QUANTITY = 2;

    // ============================== Bean ==============================

    @Autowired
    StockReservationApi api;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    StockQueryRepository stockQueryRepository;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    /**
     * テストの側でトランザクションを開いて、在庫の行をロックする TransactionTemplate
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 在庫を作る商品の ID
     */
    private UUID productId;

    /**
     * 注文の ID
     */
    private UUID orderId;

    @BeforeEach
    void setUp() {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("減算への置き換え")
    class Commit {

        @Test
        @DisplayName("AC-87 引当を減算に置き換えると、在庫数が注文数だけ減る")
        void commitsReservation() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);

            // 操作：引き当てた 2 を減算に置き換える
            var shortages = commit(holder, variant);

            // 確認（前提）：足りない品目がない
            assertThat(shortages).isEmpty();

            // 確認：在庫数が 3
            assertThat(stockQueryRepository.findQuantity(variant)).contains(STOCK_QUANTITY - ORDER_QUANTITY);
        }

        @Test
        @DisplayName("AC-87 全品目の在庫数を、それぞれの注文数だけ減らす")
        void commitsEveryItem() {
            // 準備：在庫数 5 と 3 の 2 品目を、2 と 1 ずつ引き当てる
            var first = inventory.stockNewVariant(productId, 5);
            var second = inventory.stockNewVariant(productId, 3);
            var holder = UUID.randomUUID();
            var quantities = Map.of(first, 2, second, 1);
            assertThat(api.reserve(holder, quantities, EXPIRES_AT)).isEmpty();

            // 操作：2 品目を減算に置き換える
            var shortages = api.commit(holder, quantities, orderId, ORDER_NUMBER);

            // 確認（前提）：足りない品目がない
            assertThat(shortages).isEmpty();

            // 確認：どちらの在庫数も注文数だけ減っている
            assertThat(stockQueryRepository.findQuantity(first)).contains(3);
            assertThat(stockQueryRepository.findQuantity(second)).contains(2);
        }

        @Test
        @DisplayName("AC-84 注文の成立で、引当は減算に置き換わり、残らない")
        void removesReservationOnCommit() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);

            // 操作：減算に置き換える
            commit(holder, variant);

            // 確認：自分の引当を除かなくても、販売可能数が減算の後の在庫数と同じ
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, STOCK_QUANTITY - ORDER_QUANTITY);
        }

        @Test
        @DisplayName("AC-107 減算は、理由「注文」と注文番号の履歴を残す")
        void recordsOrderAdjustment() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);

            // 操作：減算に置き換える
            commit(holder, variant);

            // 確認：最新の履歴が、この注文の減算
            assertOrderAdjustment(latestAdjustment(variant), orderId);
        }
    }

    @Nested
    @DisplayName("引当の期限が切れた後の減算")
    class CommitAfterExpiry {

        @Test
        @DisplayName("AC-87 引当の期限が切れていても、足りれば減算できる")
        void commitsAfterExpiryWhenSufficient() {
            // 準備：在庫数 2 の全てを引き当て、期限を過ぎる
            var variant = inventory.stockNewVariant(productId, 2);
            var holder = UUID.randomUUID();
            api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);
            clock.setInstant(EXPIRES_AT.plusSeconds(1));

            // 操作と確認：足りない品目がない
            assertThat(api.commit(holder, Map.of(variant, 2), orderId, ORDER_NUMBER)).isEmpty();

            // 確認：在庫数が 0
            assertThat(stockQueryRepository.findQuantity(variant)).contains(0);
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの持ち主が引き当てて足りなければ、足りない品目を返す")
        void returnsShortagesWhenOthersReservedAfterExpiry() {
            // 準備：期限切れの間に、在庫数 1 の品目をほかの持ち主が引き当てる
            var plenty = inventory.stockNewVariant(productId, 5);
            var scarce = inventory.stockNewVariant(productId, 1);
            var holder = reserveThenLoseToOthers(plenty, scarce);

            // 操作：減算に置き換える
            var shortages = api.commit(holder, Map.of(plenty, 1, scarce, 1), orderId, ORDER_NUMBER);

            // 確認：足りない品目だけを返す
            assertThat(shortages).containsExactly(scarce);
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの持ち主が引き当てて足りなければ、どの在庫も減らさない")
        void keepsQuantitiesWhenOthersReservedAfterExpiry() {
            // 準備：期限切れの間に、在庫数 1 の品目をほかの持ち主が引き当てる
            var plenty = inventory.stockNewVariant(productId, 5);
            var scarce = inventory.stockNewVariant(productId, 1);
            var holder = reserveThenLoseToOthers(plenty, scarce);

            // 操作：減算に置き換える
            api.commit(holder, Map.of(plenty, 1, scarce, 1), orderId, ORDER_NUMBER);

            // 確認：足りる品目も、足りない品目も、在庫数が変わらない
            assertThat(stockQueryRepository.findQuantity(plenty)).contains(5);
            assertThat(stockQueryRepository.findQuantity(scarce)).contains(1);
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの持ち主が引き当てて足りなければ、減算の履歴を残さない")
        void recordsNothingWhenOthersReservedAfterExpiry() {
            // 準備：期限切れの間に、在庫数 1 の品目をほかの持ち主が引き当てる
            var plenty = inventory.stockNewVariant(productId, 5);
            var scarce = inventory.stockNewVariant(productId, 1);
            var holder = reserveThenLoseToOthers(plenty, scarce);

            // 操作：減算に置き換える
            api.commit(holder, Map.of(plenty, 1, scarce, 1), orderId, ORDER_NUMBER);

            // 確認：足りる品目の最新の履歴が、準備の入荷のまま
            assertThat(latestAdjustment(plenty).reason()).isEqualTo(StockAdjustmentReason.ARRIVAL);
        }
    }

    @Nested
    @DisplayName("減算のロックの順序")
    class LockOrder {

        @Test
        @DisplayName("AC-87 減算は、持ち主の引当の行をロックしてから在庫の行をロックする（注文手続きの開始と同じ順序で、デッドロックにならない）")
        void locksReservationBeforeStock() throws Exception {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var committing = new AtomicReference<Future<Set<UUID>>>();
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：テストのトランザクションが在庫の行をロックし、減算を在庫の行で待たせる
                    dsl.selectFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variant)).forUpdate().fetch();
                    committing.set(executor.submit(() -> commit(holder, variant)));
                    LockWaits.awaitBlockedByThisTransaction(dsl);

                    // 確認：在庫の行を待つ減算が引当の行をロックしていれば、引当の行は読み飛ばされ、行が返らない
                    assertThat(dsl.selectFrom(STOCK_RESERVATION).where(STOCK_RESERVATION.HOLDER_ID.eq(holder))
                            .forUpdate().skipLocked().fetch()).isEmpty();
                });

                // 確認：ロックを解くと、減算が上限の時間までに終わり、在庫数が減る
                assertThat(committing.get().get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEmpty();
                assertThat(stockQueryRepository.findQuantity(variant)).contains(STOCK_QUANTITY - ORDER_QUANTITY);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("注文のキャンセルによる戻し")
    class Restore {

        @Test
        @DisplayName("AC-107 戻すと、在庫数が注文数だけ増える")
        void restoresStock() {
            // 準備：在庫数 3
            var variant = inventory.stockNewVariant(productId, 3);

            // 操作：注文数 2 を戻す
            api.restore(Map.of(variant, ORDER_QUANTITY), orderId, ORDER_NUMBER);

            // 確認：在庫数が 5
            assertThat(stockQueryRepository.findQuantity(variant)).contains(3 + ORDER_QUANTITY);
        }

        @Test
        @DisplayName("AC-107 戻すと、理由「注文のキャンセル」と注文番号の履歴を残す")
        void recordsCancellationAdjustment() {
            // 準備：在庫数 3
            var variant = inventory.stockNewVariant(productId, 3);

            // 操作：注文数 2 を戻す
            api.restore(Map.of(variant, ORDER_QUANTITY), orderId, ORDER_NUMBER);

            // 確認：最新の履歴が、この注文のキャンセルの戻し
            assertThat(latestAdjustment(variant)).satisfies(adjustment -> {
                assertThat(adjustment.delta()).isEqualTo(ORDER_QUANTITY);
                assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ORDER_CANCELLATION);
                assertThat(adjustment.orderNumber()).isEqualTo(ORDER_NUMBER);
            });
        }

        @Test
        @DisplayName("AC-42 注文のキャンセルの戻しには在庫数の上限を当てず、上限の 1,000,000 を超えて戻せる")
        void restoresBeyondManualLimit() {
            // 準備：在庫数が運営者の調整の上限の 1,000,000
            var variant = inventory.stockNewVariant(productId, 1_000_000);

            // 操作：1 個戻す
            api.restore(Map.of(variant, 1), orderId, ORDER_NUMBER);

            // 確認：在庫数が 1,000,001 で、最新の履歴がこの戻し
            assertThat(stockQueryRepository.findQuantity(variant)).contains(1_000_001);
            assertThat(latestAdjustment(variant).quantityAfter()).isEqualTo(1_000_001);
        }

        @Test
        @DisplayName("在庫のなくなったバリアントの戻しは飛ばし、残りのバリアントだけを戻す")
        void skipsVariantWithoutStock() {
            // 準備：在庫数 3 のバリアントと、在庫のないバリアント
            var variant = inventory.stockNewVariant(productId, 3);
            var removed = UUID.randomUUID();

            // 操作：両方を戻す
            api.restore(Map.of(variant, ORDER_QUANTITY, removed, 1), orderId, ORDER_NUMBER);

            // 確認：在庫のあるバリアントだけが増える
            assertThat(stockQueryRepository.findQuantity(variant)).contains(3 + ORDER_QUANTITY);
            assertThat(stockQueryRepository.findQuantity(removed)).isEmpty();
        }

        @Test
        @DisplayName("全てのバリアントの在庫がなくても、戻しは失敗しない")
        void ignoresWhenNoStockRemains() {
            // 準備：在庫のないバリアント
            var removed = UUID.randomUUID();

            // 操作と確認：例外にならず、在庫も作らない
            assertThatCode(() -> api.restore(Map.of(removed, 1), orderId, ORDER_NUMBER)).doesNotThrowAnyException();
            assertThat(stockQueryRepository.findQuantity(removed)).isEmpty();
        }
    }

    @Nested
    @DisplayName("引当の後の在庫の削除")
    class DeletedAfterReservation {

        @Test
        @DisplayName("AC-87 引き当てた後に商品が削除されて在庫がなくなると、減算は全ての品目を足りないとして返す")
        void returnsAllWhenStockDeleted() {
            // 準備：引き当てた後に、商品の在庫を削除する
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);
            stockItemRepository.deleteByProductId(productId);

            // 操作：減算に置き換える
            var shortages = commit(holder, variant);

            // 確認：その品目を足りないとして返し、在庫は作らない
            assertThat(shortages).containsExactly(variant);
            assertThat(stockQueryRepository.findQuantity(variant)).isEmpty();
        }
    }

    @Nested
    @DisplayName("同時の減算")
    class ConcurrentCommit {

        @Test
        @DisplayName("AC-87 在庫数 1 に 2 人が同時に減算しても、1 人だけが減らし、在庫数は負にならない")
        void commitsOnlyOneOfTwo() throws Exception {
            // 準備：在庫数 1（どちらも引き当てていない）
            var variant = inventory.stockNewVariant(productId, 1);

            // 操作：2 人が 1 個ずつ同時に減算する
            var results = Concurrently.run(2, i -> api.commit(UUID.randomUUID(), Map.of(variant, 1), UUID.randomUUID(), ORDER_NUMBER));

            // 確認：1 人は足りない品目がなく、もう 1 人はその品目が足りず、在庫数は 0
            assertThat(results).filteredOn(Set::isEmpty).hasSize(1);
            assertThat(results).filteredOn(shortages -> shortages.contains(variant)).hasSize(1);
            assertThat(stockQueryRepository.findQuantity(variant)).contains(0);
        }

        @Test
        @DisplayName("AC-43、87 在庫数 1 に注文の減算と運営者の減算が同時に来ても、1 つだけが減らし、在庫数は負にならない")
        void commitAndManualDecrementDoNotGoNegative() throws Exception {
            // 準備：在庫数 1
            var variant = inventory.stockNewVariant(productId, 1);

            // 操作：注文の減算と運営者の 1 個の減算を同時に行い、成功を数える
            var results = Concurrently.run(2, i -> {
                if (i == 0) {
                    return api.commit(UUID.randomUUID(), Map.of(variant, 1), UUID.randomUUID(), ORDER_NUMBER).isEmpty();
                }
                try {
                    inventory.adjust(variant, -1);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            });

            // 確認：成功は 1 つだけで、在庫数は 0
            assertThat(results).containsOnlyOnce(true);
            assertThat(stockQueryRepository.findQuantity(variant)).contains(0);
        }
    }

    @Nested
    @DisplayName("数量が 0 以下の品目")
    class NonPositiveQuantity {

        @ParameterizedTest(name = "数量 {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("引当で、数量が 0 以下の品目があれば、プログラムの誤りとして拒否され、在庫数も引当も変わらない")
        void rejectsReserve(int quantity) {
            // 準備：在庫数 5 のうち 2 を引き当てた持ち主と、正常な品目と数量が 0 以下の品目
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var invalid = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);

            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> api.reserve(holder, Map.of(variant, 1, invalid, quantity), EXPIRES_AT))
                    .isInstanceOf(IllegalArgumentException.class);

            // 確認：在庫数が変わらず、持ち主の引当が置き換わらず、数量が 0 以下の品目に引当が増えない
            assertThat(stockQueryRepository.findQuantity(variant)).contains(STOCK_QUANTITY);
            assertThat(api.sellableQuantities(List.of(variant, invalid), null))
                    .containsEntry(variant, STOCK_QUANTITY - ORDER_QUANTITY)
                    .containsEntry(invalid, STOCK_QUANTITY);
        }

        @ParameterizedTest(name = "数量 {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("減算で、数量が 0 以下の品目があれば、プログラムの誤りとして拒否され、在庫数も引当も変わらない")
        void rejectsCommit(int quantity) {
            // 準備：在庫数 5 のうち 2 を引き当てた持ち主と、数量が 0 以下の品目
            var variant = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var invalid = inventory.stockNewVariant(productId, STOCK_QUANTITY);
            var holder = reserve(variant);

            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> api.commit(holder, Map.of(variant, ORDER_QUANTITY, invalid, quantity), orderId, ORDER_NUMBER))
                    .isInstanceOf(IllegalArgumentException.class);

            // 確認：在庫数が変わらず、持ち主の引当が解かれていない
            assertThat(stockQueryRepository.findQuantity(variant)).contains(STOCK_QUANTITY);
            assertThat(stockQueryRepository.findQuantity(invalid)).contains(STOCK_QUANTITY);
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, STOCK_QUANTITY - ORDER_QUANTITY);
        }

        @ParameterizedTest(name = "数量 {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("戻しで、数量が 0 以下の品目があれば、プログラムの誤りとして拒否され、在庫数が変わらない")
        void rejectsRestore(int quantity) {
            // 準備：在庫数 3 の正常な品目と、数量が 0 以下の品目
            var variant = inventory.stockNewVariant(productId, 3);
            var invalid = inventory.stockNewVariant(productId, 3);

            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> api.restore(Map.of(variant, ORDER_QUANTITY, invalid, quantity), orderId, ORDER_NUMBER))
                    .isInstanceOf(IllegalArgumentException.class);

            // 確認：どちらの在庫数も変わらない
            assertThat(stockQueryRepository.findQuantity(variant)).contains(3);
            assertThat(stockQueryRepository.findQuantity(invalid)).contains(3);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 調整履歴が、この注文の減算であることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>増減量が、注文数を負にした値</li>
     *   <li>理由が「注文」</li>
     *   <li>注文 ID</li>
     *   <li>注文番号</li>
     *   <li>調整後の在庫数が、在庫数から注文数を引いた値</li>
     * </ul>
     *
     * @param adjustment 確認する調整履歴
     * @param orderId    注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertOrderAdjustment(StockAdjustmentView adjustment, UUID orderId) {
        assertThat(adjustment.delta()).isEqualTo(-ORDER_QUANTITY);
        assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ORDER);
        assertThat(adjustment.orderId()).isEqualTo(orderId);
        assertThat(adjustment.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(adjustment.quantityAfter()).isEqualTo(STOCK_QUANTITY - ORDER_QUANTITY);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 新しい持ち主で、バリアントを注文数だけ引き当てる。
     *
     * @param variant バリアント ID
     * @return 持ち主 ID
     */
    private UUID reserve(UUID variant) {
        var holder = UUID.randomUUID();
        assertThat(api.reserve(holder, Map.of(variant, ORDER_QUANTITY), EXPIRES_AT)).isEmpty();
        return holder;
    }

    /**
     * 持ち主の引当を、注文数の減算に置き換える。
     *
     * @param holder  持ち主 ID
     * @param variant バリアント ID
     * @return 足りないバリアントの ID
     */
    private Set<UUID> commit(UUID holder, UUID variant) {
        return api.commit(holder, Map.of(variant, ORDER_QUANTITY), orderId, ORDER_NUMBER);
    }

    /**
     * 2 品目を 1 つずつ引き当て、期限が切れた間に、片方をほかの持ち主に引き当てられる。
     *
     * <p>足りない側の在庫数は 1 なので、ほかの持ち主の引当で販売可能数が 0 になる。
     *
     * @param plenty 足りる品目のバリアント ID
     * @param scarce 足りなくなる品目のバリアント ID（在庫数 1）
     * @return 期限の切れた引当の持ち主 ID
     */
    private UUID reserveThenLoseToOthers(UUID plenty, UUID scarce) {
        var holder = UUID.randomUUID();
        api.reserve(holder, Map.of(plenty, 1, scarce, 1), EXPIRES_AT);
        clock.setInstant(EXPIRES_AT.plusSeconds(1));
        api.reserve(UUID.randomUUID(), Map.of(scarce, 1), EXPIRES_AT.plus(Duration.ofMinutes(30)));
        return holder;
    }

    /**
     * バリアントの最新の調整履歴を取得する。
     *
     * @param variantId バリアント ID
     * @return 最新の調整履歴
     */
    private StockAdjustmentView latestAdjustment(UUID variantId) {
        return stockQueryRepository.findAdjustments(variantId, PageRequest.of(1, 1)).orElseThrow().items().getFirst();
    }
}
