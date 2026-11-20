package com.ecsite.module.order.presentation;

import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.async.LockWaits;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.security.TestTokens;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.support.data.OrderTestData.startCheckoutRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jooq.impl.DSL.sum;

/**
 * 注文手続きの開始の並行性の受け入れテスト
 *
 * <p>会員のロックが働いていることを確認するため、リクエストごとにコミットする。ロールバックしないが、作ったデータは消さない。
 * 税区分の名称は一意なので、ほかのテストと重ならない名前にする。
 */
@IntegrationTest
class CheckoutStartConcurrencyTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * テストの側でトランザクションを開いて、在庫の行をロックする TransactionTemplate
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    private CatalogTestData.TestProduct product;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        var taxCategoryId = catalog.taxCategory("開始並行-" + UUID.randomUUID().toString().substring(0, 8), "10");
        product = catalog.product(taxCategoryId, "開始並行の商品", 1000).publish();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("同じカートで注文手続きを同時に開始しても、有効な引当は 1 つの注文手続きの分だけである")
    void keepsOnlyOneReservationForConcurrentStarts() throws Exception {
        // 準備：在庫数 5 に、2 個のカートを作る
        inventory.stock(product.variantIds.getFirst(), product.id, 5);
        var owner = TestTokens.member().subject(UUID.randomUUID());
        var cartId = orders.cart(owner.bearer());
        orders.add(cartId, product.variantIds.getFirst(), 2, owner.bearer());
        // カートの変更の処理が、注文手続きと重ならないように待つ
        asyncEvents.awaitHandled(published -> published instanceof CartChanged changed && changed.cartId().equals(cartId));
        var executor = Executors.newFixedThreadPool(2);
        try {
            // 操作：在庫の行をロックしている間に、同じカートで 2 つの開始を送り、両方が先に進めず待つようにしてからロックを放す
            var starting = new ArrayList<Future<MockHttpServletResponse>>();
            whileStockLocked(() -> {
                // 1 つ目は、在庫の行で待つ
                starting.add(executor.submit(() -> start(cartId, owner)));
                LockWaits.awaitBlockedByThisTransaction(dsl);
                // 2 つ目は、1 つ目と並んで在庫の行で待つか、会員のロックで待つ
                starting.add(executor.submit(() -> start(cartId, owner)));
                awaitWaitingSessions(2);
            });
            for (var future : starting) {
                assertThat(future.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS).getStatus()).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }

        // 確認：カートの注文手続きの引当の数量の合計が、カートの数量 1 つ分
        var reserved = dsl.select(sum(STOCK_RESERVATION.QUANTITY)).from(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.HOLDER_ID.in(
                        dsl.select(CHECKOUT.ID).from(CHECKOUT).where(CHECKOUT.CART_ID.eq(cartId))))
                .fetchSingle().value1();
        assertThat(reserved).isEqualByComparingTo(BigDecimal.valueOf(2));
    }

    // ============================== 補助 ==============================

    /**
     * テストのトランザクションで商品の在庫の行をロックしたまま、処理を実行する。
     *
     * <p>処理の後のコミットでロックが解放される
     */
    private void whileStockLocked(Runnable body) {
        transactionTemplate.executeWithoutResult(status -> {
            dsl.selectFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(product.variantIds.getFirst())).forUpdate().fetch();
            body.run();
        });
    }

    /**
     * ロックを取れずに待つセッションが、指定の数になるまで待つ
     */
    private void awaitWaitingSessions(int count) {
        await().atMost(AsyncEvents.TIMEOUT).until(() -> dsl.fetchValue(
                "select count(distinct pid) from pg_locks where not granted") instanceof Number waiting
                && waiting.intValue() >= count);
    }

    /**
     * 注文手続きを開始する
     */
    private MockHttpServletResponse start(UUID cartId, TestTokens.Builder owner) throws Exception {
        return mockMvc.perform(startCheckoutRequest(cartId).with(owner.bearer())).andReturn().getResponse();
    }
}
