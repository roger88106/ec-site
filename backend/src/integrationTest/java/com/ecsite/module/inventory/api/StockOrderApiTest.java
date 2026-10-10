package com.ecsite.module.inventory.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.inventory.application.StockQueryRepository;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文による在庫の増減の統合テスト（T2107、AC-87 の土台、AC-107）。<br />
 * 操作ごとにコミットする。ロールバックしないので、作ったデータを後で消す。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class StockOrderApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 引当の期限（15 分後）
     */
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-482915";

    @Autowired
    StockReservationApi api;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    StockQueryRepository stockQueryRepository;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    private UUID productId;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(productId)).execute();
    }

    @Test
    @DisplayName("AC-107 引当を減算に置き換えると、在庫数が減り、引当が消え、理由「注文」と注文番号の履歴が残る")
    void commitsReservation() {
        var variant = stock(5);
        var holder = UUID.randomUUID();
        api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);

        var shortages = api.commit(holder, Map.of(variant, 2), orderId, ORDER_NUMBER);

        assertThat(shortages).isEmpty();
        assertThat(stockQueryRepository.findQuantity(variant)).contains(3);
        // 引当は消え、自分の引当を除かなくても販売可能数は在庫数と同じ
        assertThat(api.sellableQuantities(List.of(variant), null)).containsEntry(variant, 3);
        assertThat(latestAdjustment(variant)).satisfies(adjustment -> {
            assertThat(adjustment.delta()).isEqualTo(-2);
            assertThat(adjustment.reason()).isEqualTo("ORDER");
            assertThat(adjustment.orderId()).isEqualTo(orderId);
            assertThat(adjustment.orderNumber()).isEqualTo(ORDER_NUMBER);
            assertThat(adjustment.quantityAfter()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("AC-87 引当の期限が切れていても、足りれば減算できる")
    void commitsAfterExpiryWhenSufficient() {
        var variant = stock(2);
        var holder = UUID.randomUUID();
        api.reserve(holder, Map.of(variant, 2), EXPIRES_AT);
        clock.setInstant(EXPIRES_AT.plusSeconds(1));

        assertThat(api.commit(holder, Map.of(variant, 2), orderId, ORDER_NUMBER)).isEmpty();
        assertThat(stockQueryRepository.findQuantity(variant)).contains(0);
    }

    @Test
    @DisplayName("AC-87 期限切れの間にほかの持ち主が引き当てて足りなければ、何も変えずに足りない品目を返す")
    void rejectsCommitWhenOthersReservedAfterExpiry() {
        var plenty = stock(5);
        var scarce = stock(1);
        var holder = UUID.randomUUID();
        api.reserve(holder, Map.of(plenty, 1, scarce, 1), EXPIRES_AT);
        clock.setInstant(EXPIRES_AT.plusSeconds(1));
        api.reserve(UUID.randomUUID(), Map.of(scarce, 1), EXPIRES_AT.plus(Duration.ofMinutes(30)));

        var shortages = api.commit(holder, Map.of(plenty, 1, scarce, 1), orderId, ORDER_NUMBER);

        assertThat(shortages).containsExactly(scarce);
        assertThat(stockQueryRepository.findQuantity(plenty)).contains(5);
        assertThat(stockQueryRepository.findQuantity(scarce)).contains(1);
        assertThat(latestAdjustment(plenty).reason()).isEqualTo("ARRIVAL");
    }

    @Test
    @DisplayName("AC-107 戻すと在庫数が増え、理由「注文のキャンセル」と注文番号の履歴が残る")
    void restoresStock() {
        var variant = stock(3);

        api.restore(Map.of(variant, 2), orderId, ORDER_NUMBER);

        assertThat(stockQueryRepository.findQuantity(variant)).contains(5);
        assertThat(latestAdjustment(variant)).satisfies(adjustment -> {
            assertThat(adjustment.delta()).isEqualTo(2);
            assertThat(adjustment.reason()).isEqualTo("ORDER_CANCELLATION");
            assertThat(adjustment.orderNumber()).isEqualTo(ORDER_NUMBER);
        });
    }

    // ============================== 補助 ==============================

    /**
     * 在庫数を指定してバリアントの在庫を作り、バリアント ID を返す
     */
    private UUID stock(int quantity) {
        var variantId = UUID.randomUUID();
        stockItemRepository.createIfAbsent(variantId, productId);
        stockItemRepository.adjust(variantId, StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, NOW));
        return variantId;
    }

    /**
     * バリアントの最新の調整履歴
     */
    private StockAdjustmentView latestAdjustment(UUID variantId) {
        return stockQueryRepository.findAdjustments(variantId, PageRequest.of(1, 1)).orElseThrow().items().getFirst();
    }
}
