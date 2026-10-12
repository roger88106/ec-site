package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の並行性の受け入れテスト（T2313、AC-87、91）。<br />
 * 行ロックが効いていることを確かめるため、要求ごとにコミットする。ロールバックしないので、作ったデータを後で消す。
 * 税区分の名称は一意なので、ほかのテストと重ならない名前にする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class PlaceOrderConcurrencyTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 注文の本文（総額は %d）
     */
    private static final String ORDER_BODY = """
            {"expectedTotal": %d, "shippingAddress": {"name": "山田 太郎",
             "postalCode": "1000001", "prefecture": "東京都", "address": "千代田区千代田 1-1", "phone": "0312345678"}}""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    private UUID taxCategoryId;

    private CatalogTestData.TestProduct product;

    private final List<UUID> cartIds = new ArrayList<>();

    /**
     * カート ID → 持ち主の会員の token（同じテストで 2 人の消費者を表すため、カートごとに会員を分ける）
     */
    private final Map<UUID, TestTokens.Builder> owners = new HashMap<>();

    /**
     * 注文手続き ID → 持ち主の会員の token
     */
    private final Map<UUID, TestTokens.Builder> checkoutOwners = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        taxCategoryId = data.taxCategory("並行-" + UUID.randomUUID().toString().substring(0, 8), "10");
        product = data.product(taxCategoryId, "並行の商品", 1000).publish();
    }

    @AfterEach
    void cleanUp() {
        var checkouts = dsl.select(CHECKOUT.ID).from(CHECKOUT).where(CHECKOUT.CART_ID.in(cartIds));
        // 明細と税率ごとの消費税額は、注文の削除で一緒に消える
        dsl.deleteFrom(SALES_ORDER).where(SALES_ORDER.CHECKOUT_ID.in(checkouts)).execute();
        dsl.deleteFrom(CHECKOUT).where(CHECKOUT.CART_ID.in(cartIds)).execute();
        dsl.deleteFrom(CART_).where(CART_.ID.in(cartIds)).execute();
        // 引当と調整履歴は、在庫の削除で一緒に消える
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(product.id)).execute();
        dsl.deleteFrom(PRODUCT).where(PRODUCT.ID.eq(product.id)).execute();
        dsl.deleteFrom(TAX_RATE).where(TAX_RATE.TAX_CATEGORY_ID.eq(taxCategoryId)).execute();
        dsl.deleteFrom(TAX_CATEGORY).where(TAX_CATEGORY.ID.eq(taxCategoryId)).execute();
    }

    @Test
    @DisplayName("AC-91 同じ注文手続きで同時に何度送っても、注文は 1 件だけで、どの応答も同じ注文 ID")
    void placesOnceForConcurrentResends() throws Exception {
        stock(3);
        var cartId = cartWith(2);
        var checkoutId = startCheckout(cartId);

        var responses = concurrently(5, () -> place(checkoutId));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201, 200)
                .filteredOn(status -> status == 201).hasSize(1);
        assertThat(responses).extracting(response -> JsonPath.<String>read(response.getContentAsString(), "$.id"))
                .containsOnly(JsonPath.<String>read(responses.getFirst().getContentAsString(), "$.id"));
        assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isEqualTo(1);
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-87 引当の期限が切れた 2 人が、販売可能数 1 の在庫を同時に注文すると、1 件だけ成立する")
    void placesOnlyOneForLastItem() throws Exception {
        stock(1);
        var first = startCheckout(cartWith(1));
        // 1 人目の引当の期限が切れてから、2 人目が引き当てる
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));
        var second = startCheckout(cartWith(1));
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));

        var checkouts = List.of(first, second);
        var index = new AtomicInteger();
        var responses = concurrently(2, () -> place(checkouts.get(index.getAndIncrement())));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        assertThat(quantity()).isZero();
    }

    // ============================== 補助 ==============================

    /**
     * 同じ処理を、複数のスレッドで同時に始める
     */
    private List<MockHttpServletResponse> concurrently(int threads, Callable<MockHttpServletResponse> task)
            throws Exception {
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            var responses = new ArrayList<MockHttpServletResponse>();
            for (var future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            // close は全部の処理の終わりを待つので、途中で失敗すると start を待つスレッドが残る。割り込んで止める
            executor.shutdownNow();
        }
    }

    /**
     * 注文する（総額は 1,100 円 × 数量 ＋ 送料。数量はカートのとおり）
     */
    private MockHttpServletResponse place(UUID checkoutId) throws Exception {
        var owner = checkoutOwners.get(checkoutId);
        var checkout = mockMvc.perform(get("/api/store/checkouts/" + checkoutId).with(owner.bearer()))
                .andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        return mockMvc.perform(post("/api/store/checkouts/" + checkoutId + "/order").with(owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY.formatted(total)))
                .andReturn().getResponse();
    }

    /**
     * 新しい会員の会員のカートに商品を入れ、カート ID を返す
     */
    private UUID cartWith(int quantity) throws Exception {
        var owner = TestTokens.member().subject(UUID.randomUUID());
        var body = mockMvc.perform(get("/api/store/me/cart").with(owner.bearer())).andReturn().getResponse().getContentAsString();
        var cartId = UUID.fromString(JsonPath.read(body, "$.cartId"));
        cartIds.add(cartId);
        owners.put(cartId, owner);
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items").with(owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(product.variantIds.getFirst(), quantity)))
                .andExpect(status().isNoContent());
        return cartId;
    }

    /**
     * 注文手続きに進み、注文手続き ID を返す
     */
    private UUID startCheckout(UUID cartId) throws Exception {
        var owner = owners.get(cartId);
        var body = mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts").with(owner.bearer()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var checkoutId = UUID.fromString(JsonPath.read(body, "$.id"));
        checkoutOwners.put(checkoutId, owner);
        return checkoutId;
    }

    /**
     * 商品の在庫を作り、在庫数を設定する
     */
    private void stock(int quantity) {
        var variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId, StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, NOW));
    }

    /**
     * 商品の在庫数
     */
    private int quantity() {
        return dsl.select(STOCK_ITEM.QUANTITY).from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.eq(product.variantIds.getFirst()))
                .fetchSingle(STOCK_ITEM.QUANTITY);
    }
}
