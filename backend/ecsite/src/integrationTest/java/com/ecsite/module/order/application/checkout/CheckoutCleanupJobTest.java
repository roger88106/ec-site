package com.ecsite.module.order.application.checkout;

import com.ecsite.CapturedLogs;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.OrderTestData.placeOrderRequest;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの片付けのジョブの統合テスト。<br />
 * ジョブはテストから呼ぶ。テストごとにロールバックする（片付けはテストのトランザクションに参加する）。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CheckoutCleanupJobTest {

    /**
     * 注文する時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 注文手続きの期限（引当の期限と同じ 15 分）
     */
    private static final Duration TTL = Duration.ofMinutes(15);

    /**
     * 注文した注文手続きの期限から、1 日と 1 時間後（保存期間の 1 日を過ぎた）
     */
    private static final Instant AFTER_RETENTION = NOW.plus(TTL).plus(Duration.ofDays(1)).plus(Duration.ofHours(1));

    @Autowired
    CheckoutCleanupJob job;

    @Autowired
    CheckoutService service;

    @Autowired
    CheckoutRepository checkoutRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(data.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
    }

    @Test
    @DisplayName("AC-284 期限から保存期間（1 日）より前の注文手続きは消え、その後の注文手続きは残る")
    void deletesCheckoutsExpiredBeforeRetention() {
        var old = insertCheckout(NOW);
        var recent = insertCheckout(NOW.plus(Duration.ofHours(2)));

        clock.setInstant(AFTER_RETENTION);
        job.cleanUp();

        assertThat(remaining(List.of(old, recent))).containsExactly(recent);
    }

    @Test
    @DisplayName("AC-286 注文になった注文手続きを消しても、注文の詳細・一覧（店舗・管理）とキャンセルの申請は今どおり使える")
    void ordersRemainUsableAfterCheckoutDeleted() throws Exception {
        var orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, 1);
        var checkoutId = dsl.select(SALES_ORDER.CHECKOUT_ID).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                .fetchOne(SALES_ORDER.CHECKOUT_ID);

        clock.setInstant(AFTER_RETENTION);
        job.cleanUp();

        assertThat(remaining(List.of(checkoutId))).isEmpty();
        mockMvc.perform(get("/api/store/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(get("/api/store/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(orderId.toString())));
        mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(orderId.toString())));
        mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC-289 消した注文手続きを読む操作と、注文する操作は 404 になる")
    void deletedCheckoutIsNotFound() throws Exception {
        var orders = new OrderTestData(mockMvc);
        var cartId = orders.cart();
        orders.add(cartId, variantId, 1);
        var checkoutId = orders.startCheckout(cartId);

        clock.setInstant(AFTER_RETENTION);
        job.cleanUp();

        mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andExpect(status().isNotFound());
        mockMvc.perform(placeOrderRequest(checkoutId, 2100)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("NFR-24 件数の上限を超える注文手続きは、上限ずつ分けて、残りがなくなるまで消す")
    void deletesInBatchesUntilNoneLeft() {
        var checkouts = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            checkouts.add(insertCheckout(NOW));
        }
        var smallBatch = new CheckoutCleanupJob(service,
                new CheckoutCleanupProperties(Duration.ofHours(1), Duration.ofDays(1), 2), clock);

        clock.setInstant(AFTER_RETENTION);
        smallBatch.cleanUp();

        assertThat(remaining(checkouts)).isEmpty();
    }

    @Test
    @DisplayName("NFR-24 1 回の削除は、件数の上限までしか消さない")
    void deletesUpToLimitAtOnce() {
        // ほかのテストが使わない古い時刻にし、消す条件に当たるのを自分の 3 つだけにする
        var longAgo = Instant.parse("2000-01-01T00:00:00Z");
        var checkouts = new ArrayList<UUID>();
        for (int i = 0; i < 3; i++) {
            checkouts.add(insertCheckout(longAgo));
        }

        var deleted = service.deleteExpired(longAgo.plus(TTL).plusSeconds(1), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(remaining(checkouts)).hasSize(1);
    }

    @Test
    @DisplayName("AC-288 消した件数（分けて消した合計）をログに出す")
    void logsDeletedCount(CapturedOutput output) {
        var logStart = CapturedLogs.mark(output);
        // ほかのテストが使わない古い時刻にし、消す条件に当たるのを自分の 5 つだけにする
        var longAgo = Instant.parse("2000-01-01T00:00:00Z");
        var checkouts = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            checkouts.add(insertCheckout(longAgo));
        }
        var smallBatch = new CheckoutCleanupJob(service,
                new CheckoutCleanupProperties(Duration.ofHours(1), Duration.ofDays(1), 2), clock);

        clock.setInstant(longAgo.plus(TTL).plus(Duration.ofDays(1)).plusSeconds(1));
        smallBatch.cleanUp();

        var deleted = checkouts.size() - remaining(checkouts).size();
        assertThat(deleted).isEqualTo(5);
        assertThat(CapturedLogs.since(output, logStart)).contains("期限切れの注文手続きを消した：" + deleted + " 件");
    }

    /**
     * 指定した時刻に始めた注文手続きを、直接保存する（カートと引当は作らない）。
     *
     * @return 注文手続き ID
     */
    private UUID insertCheckout(Instant startedAt) {
        var checkout = Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, startedAt, TTL);
        checkoutRepository.insert(checkout);
        return checkout.id();
    }

    /**
     * 指定した注文手続きのうち、残っているもの
     */
    private List<UUID> remaining(List<UUID> checkoutIds) {
        return dsl.select(CHECKOUT.ID).from(CHECKOUT).where(CHECKOUT.ID.in(checkoutIds)).fetch(CHECKOUT.ID);
    }
}
