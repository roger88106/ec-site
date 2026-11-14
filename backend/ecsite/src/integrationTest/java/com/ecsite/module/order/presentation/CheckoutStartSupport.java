package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestJson;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの開始の受け入れテスト（{@code Checkout*ApiTest}）が共有する準備と確認の補助
 *
 * <p>作るたびに、送料の税区分を用意し、標準の税区分（10%）と、テストの会員のカートを作る。
 * テストのクラスは、時計を {@link #NOW} に合わせてから作る。
 */
final class CheckoutStartSupport {

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 注文手続きの引当の期限までの時間
     */
    static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    /**
     * API を呼ぶ MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * 販売可能数を読む在庫の API
     */
    private final StockReservationApi stockReservationApi;

    /**
     * DB の操作
     */
    private final DSLContext dsl;

    /**
     * 注文手続きのリポジトリ
     */
    private final CheckoutRepository checkoutRepository;

    /**
     * 在庫を作る補助
     */
    private final InventoryTestData inventory;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private final CatalogTestData catalog;

    /**
     * 注文を作る補助
     */
    private final OrderTestData orders;

    /**
     * 標準の税区分（10%）の ID
     */
    private final UUID standard;

    /**
     * テストの会員のカート ID
     */
    private final UUID cartId;

    /**
     * 税区分とカートを作る。
     *
     * <p>使う Bean は、テストのコンテキストから取り出す（テストのクラスごとに同じフィールドを並べないため）。
     *
     * @param context テストのコンテキスト
     */
    CheckoutStartSupport(ApplicationContext context) throws Exception {
        var clock = context.getBean(MutableClock.class);
        this.mockMvc = context.getBean(MockMvc.class);
        this.stockReservationApi = context.getBean(StockReservationApi.class);
        this.dsl = context.getBean(DSLContext.class);
        this.checkoutRepository = context.getBean(CheckoutRepository.class);
        this.inventory = new InventoryTestData(context.getBean(StockItemRepository.class), clock);
        ShippingTaxCategory.ensure(dsl);
        this.catalog = new CatalogTestData(mockMvc, clock);
        this.orders = new OrderTestData(mockMvc);
        this.standard = catalog.taxCategory("標準", "10");
        this.cartId = orders.cart();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 在庫を作る補助を返す。
     *
     * @return 在庫を作る補助
     */
    InventoryTestData inventory() {
        return inventory;
    }

    /**
     * 税区分・カテゴリ・商品を作る補助を返す。
     *
     * @return 税区分・カテゴリ・商品を作る補助
     */
    CatalogTestData catalog() {
        return catalog;
    }

    /**
     * 注文を作る補助を返す。
     *
     * @return 注文を作る補助
     */
    OrderTestData orders() {
        return orders;
    }

    /**
     * 標準の税区分（10%）の ID を返す。
     *
     * @return 税区分 ID
     */
    UUID standard() {
        return standard;
    }

    /**
     * テストの会員のカート ID を返す。
     *
     * @return カート ID
     */
    UUID cartId() {
        return cartId;
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す。
     *
     * @param quantity 在庫数
     * @return バリアント ID
     */
    UUID stockedVariant(int quantity) throws Exception {
        return inventory.stockedVariant(catalog, standard, "商品", quantity);
    }

    /**
     * 注文手続きに進み、201 と Location を確認して、注文手続き ID を返す。
     *
     * @param cart カート ID
     * @return 注文手続き ID
     */
    UUID startCheckout(UUID cart) throws Exception {
        var body = mockMvc.perform(startCheckoutRequest(cart))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * 商品を注文した後に販売停止にして、注文 ID を返す。
     *
     * <p>カートには、購入できない品目が残る。
     *
     * @return 支払待ちの注文 ID
     */
    UUID orderThenSuspend() throws Exception {
        var product = catalog.product(standard, "販売停止にする", 1000, "色").variant("白").publish();
        var variant = product.variantIds.getFirst();
        inventory.stock(variant, product.id, 3);
        var pending = orders.placePendingOrder(variant, 1);
        product.suspend(0);
        return pending;
    }

    // ============================== 状態の読み取り ==============================

    /**
     * 注文手続きに保存した、確認した注文の ID を返す。
     *
     * @param checkoutId 注文手続き ID
     * @return 確認した注文の ID
     */
    Set<UUID> acknowledgedOrderIds(UUID checkoutId) {
        return checkoutRepository.findById(checkoutId).orElseThrow().acknowledgedOrderIds();
    }

    /**
     * テストのカートの注文手続きの数を返す。
     *
     * @return 注文手続きの数
     */
    int checkoutCount() {
        return dsl.fetchCount(CHECKOUT, CHECKOUT.CART_ID.eq(cartId));
    }

    /**
     * バリアントの販売可能数を返す。
     *
     * @param variant バリアント ID
     * @return 販売可能数
     */
    int sellable(UUID variant) {
        return stockReservationApi.sellableQuantities(List.of(variant), null).get(variant);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 確認を求める 409 であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 409</li>
     *   <li>エラーコードが {@code PENDING_ORDER_CONFIRMATION_REQUIRED}</li>
     * </ul>
     *
     * @param result 開始の応答
     */
    static void expectConfirmationRequired(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_CONFIRMATION_REQUIRED"));
    }

    /**
     * 支払待ちの注文が上限に達していることを示す 409 であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 409</li>
     *   <li>エラーコードが {@code PENDING_ORDER_LIMIT_EXCEEDED}</li>
     * </ul>
     *
     * @param result 開始の応答
     */
    static void expectLimitExceeded(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_LIMIT_EXCEEDED"));
    }
}
