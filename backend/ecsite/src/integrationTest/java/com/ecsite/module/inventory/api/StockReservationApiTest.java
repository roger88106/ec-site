package com.ecsite.module.inventory.api;

import com.ecsite.Concurrently;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
            var variant = inventory.stockNewVariant(productId, 5);
            api.reserve(UUID.randomUUID(), Map.of(variant, 2), EXPIRES_AT);

            // 操作：持ち主を指定せずに販売可能数を求める
            var sellable = api.sellableQuantities(List.of(variant), null);

            // 確認：3
            assertThat(sellable).containsExactlyInAnyOrderEntriesOf(Map.of(variant, 3));
        }

        @Test
        @DisplayName("BR-11 持ち主を指定すると、その持ち主の引当を除いて数える")
        void sellableQuantityExcludesHolderReservations() {
            // 準備：在庫数 5 のうち 2 を引き当てる
            var variant = inventory.stockNewVariant(productId, 5);
            var holder = UUID.randomUUID();
            api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);

            // 操作：引き当てた持ち主を指定して販売可能数を求める
            var sellable = api.sellableQuantities(List.of(variant), holder);

            // 確認：在庫数と同じ 5
            assertThat(sellable).containsEntry(variant, 5);
        }

        @Test
        @DisplayName("指定した全てのバリアントの販売可能数を返し、在庫がまだないバリアントは 0")
        void includesVariantsWithoutStock() {
            // 準備：在庫数 5 のうち 2 を引き当てたバリアントと、在庫がまだないバリアント
            var variant = inventory.stockNewVariant(productId, 5);
            var withoutStock = UUID.randomUUID();
            api.reserve(UUID.randomUUID(), Map.of(variant, 2), EXPIRES_AT);

            // 操作：2 つの販売可能数を求める
            var sellable = api.sellableQuantities(List.of(variant, withoutStock), null);

            // 確認：両方を含み、在庫がまだないものは 0
            assertThat(sellable).containsExactlyInAnyOrderEntriesOf(Map.of(variant, 3, withoutStock, 0));
        }

        @Test
        @DisplayName("AC-84、BR-11 期限の切れた引当は数えない")
        void ignoresExpiredReservations() {
            // 準備：在庫数 5 のうち 2 を引き当て、期限ちょうどになる
            var variant = inventory.stockNewVariant(productId, 5);
            api.reserve(UUID.randomUUID(), Map.of(variant, 2), EXPIRES_AT);
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
            // 準備：在庫数 5 と 1 の品目と、在庫がまだない品目
            var plenty = inventory.stockNewVariant(productId, 5);
            var scarce = inventory.stockNewVariant(productId, 1);
            var withoutStock = UUID.randomUUID();

            // 操作：3 品目を引き当てる
            var shortages = api.reserve(UUID.randomUUID(), Map.of(plenty, 2, scarce, 2, withoutStock, 1), EXPIRES_AT);

            // 確認：足りない 2 品目を返す
            assertThat(shortages).containsExactlyInAnyOrder(scarce, withoutStock);
        }

        @Test
        @DisplayName("AC-82 1 品目でも足りなければ、どれも引き当てない")
        void reservesAllOrNothing() {
            // 準備：在庫数 5 と 1 の品目と、在庫がまだない品目
            var plenty = inventory.stockNewVariant(productId, 5);
            var scarce = inventory.stockNewVariant(productId, 1);
            var withoutStock = UUID.randomUUID();

            // 操作：3 品目を引き当てる
            api.reserve(UUID.randomUUID(), Map.of(plenty, 2, scarce, 2, withoutStock, 1), EXPIRES_AT);

            // 確認：足りる品目も引き当てられず、販売可能数が在庫数のまま
            assertThat(api.sellableQuantities(List.of(plenty, scarce), null))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(plenty, 5, scarce, 1));
        }

        @Test
        @DisplayName("AC-82 全ての品目の在庫がなければ、全ての品目を足りないとして返す")
        void returnsAllWhenNoStock() {
            // 準備：在庫がまだない 2 品目
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();

            // 操作：2 品目を引き当てる
            var shortages = api.reserve(UUID.randomUUID(), Map.of(first, 1, second, 2), EXPIRES_AT);

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
            var first = inventory.stockNewVariant(productId, 1);
            var second = inventory.stockNewVariant(productId, 3);
            var holder = reserveFirstOnly(first);

            // 操作：同じ持ち主で、同じ品目を含めて引き当て直す
            var shortages = api.reserve(holder, Map.of(first, 1, second, 2), EXPIRES_AT);

            // 確認：自分の引当に阻まれず、足りない品目がない
            assertThat(shortages).isEmpty();
        }

        @Test
        @DisplayName("AC-84 同じ持ち主が引き当て直すと、前の引当を置き換える")
        void replacesReservationsOfSameHolder() {
            // 準備：在庫数 1 の全てを自分で引き当てる
            var first = inventory.stockNewVariant(productId, 1);
            var second = inventory.stockNewVariant(productId, 3);
            var holder = reserveFirstOnly(first);

            // 操作：同じ持ち主で、2 品目を引き当て直す
            api.reserve(holder, Map.of(first, 1, second, 2), EXPIRES_AT);

            // 確認：前の引当は重ならず、新しい内容の分だけ販売可能数が減る
            assertThat(api.sellableQuantities(List.of(first, second), null))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(first, 0, second, 1));
        }

        @Test
        @DisplayName("AC-84 引当を解くと、販売可能数が戻る")
        void releasesReservations() {
            // 準備：在庫数 2 の全てを引き当てる
            var variant = inventory.stockNewVariant(productId, 2);
            var holder = UUID.randomUUID();
            api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);

            // 操作：引当を解く
            api.release(holder);

            // 確認：販売可能数が在庫数の 2 に戻る
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 2);
        }
    }

    @Nested
    @DisplayName("同時の引当")
    class ConcurrentReserve {

        @Test
        @DisplayName("AC-83 同じ在庫に同時に引当が来ても、在庫数を超えて引き当てない")
        void reservesConcurrentlyWithinStock() throws Exception {
            // 準備：在庫数 1
            var variant = inventory.stockNewVariant(productId, 1);

            // 操作：10 人が同時に 1 つずつ引き当てる
            var results = Concurrently.run(CONCURRENT_HOLDERS, i -> api.reserve(UUID.randomUUID(), Map.of(variant, 1), EXPIRES_AT));

            // 確認：1 人だけが引き当てられ、販売可能数が 0
            assertThat(results).filteredOn(Set::isEmpty).hasSize(1);
            assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 0);
        }

        @Test
        @DisplayName("AC-83 同時の引当で引き当てられなかった持ち主には、在庫が足りない品目を返す")
        void returnsShortagesToOthers() throws Exception {
            // 準備：在庫数 1
            var variant = inventory.stockNewVariant(productId, 1);

            // 操作：10 人が同時に 1 つずつ引き当てる
            var results = Concurrently.run(CONCURRENT_HOLDERS, i -> api.reserve(UUID.randomUUID(), Map.of(variant, 1), EXPIRES_AT));

            // 確認：引き当てられなかった 9 人には、その品目が足りないと返る
            assertThat(results).filteredOn(shortages -> !shortages.isEmpty())
                    .hasSize(CONCURRENT_HOLDERS - 1)
                    .allSatisfy(shortages -> assertThat(shortages).containsExactly(variant));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 新しい持ち主で、1 つ目の品目だけを 1 つ引き当てる。
     *
     * @param first 1 つ目の品目のバリアント ID
     * @return 持ち主 ID
     */
    private UUID reserveFirstOnly(UUID first) {
        var holder = UUID.randomUUID();
        assertThat(api.reserve(holder, Map.of(first, 1), EXPIRES_AT)).isEmpty();
        return holder;
    }
}
