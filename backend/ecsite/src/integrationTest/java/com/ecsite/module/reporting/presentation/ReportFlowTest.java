package com.ecsite.module.reporting.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.ShippingTestData;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の操作からレポートの集計までの、通しの流れのテスト
 *
 * <p>注文・出荷・配送状態の通知・キャンセルを API で行い、レポートがイベントで集めた写しを、レポートの API で集計する。
 * 写しはコミットの後に非同期で集めるので、このテストはコミットし、イベントの処理を待つ。
 * 作ったデータは消さないので、商品と会員はテストごとに作り、集計はこのテストの商品で絞る。
 *
 * <p>集計の規則の細部は application 層のテスト（{@code SalesTrendQueryTest} など）が確認する。
 */
@IntegrationTest
class ReportFlowTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 集計の期間のクエリ（注文日と出荷日を含む 2026 年 10 月）
     */
    private static final String OCTOBER = "from=2026-10-01&to=2026-10-31";

    /**
     * 商品の税抜価格（円）
     */
    private static final long PRICE_EXCL_TAX = 1000;

    /**
     * 商品の税込価格（円。税率 10%）
     */
    private static final long PRICE_INCL_TAX = 1100;

    /**
     * 入荷した在庫数
     */
    private static final int STOCKED = 20;

    /**
     * 配達まで済む注文の数量
     */
    private static final int DELIVERED_QUANTITY = 1;

    /**
     * 受付のままキャンセルする注文の数量
     */
    private static final int CANCELLED_QUANTITY = 2;

    /**
     * 出荷の後に返送されてキャンセルする注文の数量
     */
    private static final int RETURNED_QUANTITY = 4;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * このテストの商品の ID
     */
    private UUID productId;

    /**
     * このテストの商品のバリアント ID
     */
    private UUID variantId;

    /**
     * 注文の補助
     */
    private OrderTestData orders;

    /**
     * 出荷の補助
     */
    private ShippingTestData shipping;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        var inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var taxCategoryId = catalog.taxCategory("集計の流れ-" + UUID.randomUUID().toString().substring(0, 8), "10");
        var product = catalog.product(taxCategoryId, "集計の流れの商品", PRICE_EXCL_TAX).publish();
        productId = product.id;
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, productId, STOCKED);
        // コミットするので、他のテストとカートを共有しないよう、テストごとの会員で注文する
        var owner = TestTokens.member().subject(UUID.randomUUID()).email("report-flow-" + UUID.randomUUID() + "@example.com");
        orders = new OrderTestData(mockMvc, gateway, applicationContext, owner.bearer());
        shipping = new ShippingTestData(mockMvc, mockCarrier);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-225 配達まで済んだ注文だけを、注文基準でも出荷基準でも数え、受付のままのキャンセルと、出荷の後に返送されたキャンセルは数えない")
    void countsOnlyDeliveredOrder() throws Exception {
        // 準備：配達まで済む注文、受付のままキャンセルする注文、出荷の後に返送されてキャンセルする注文
        var delivered = acceptedOrder(DELIVERED_QUANTITY);
        var cancelled = acceptedOrder(CANCELLED_QUANTITY);
        var returned = acceptedOrder(RETURNED_QUANTITY);

        // 操作：出荷と配達完了、受付のままのキャンセル、出荷と返送の後のキャンセルを行い、レポートが集め終わるのを待つ
        var deliveredTracking = ship(delivered);
        shipping.notifyStatus(Carrier.YAMATO, deliveredTracking, "配達完了");
        cancel(cancelled, """
                {"reason": "受付のままキャンセル"}""");
        var returnedTracking = ship(returned);
        shipping.notifyStatus(Carrier.YAMATO, returnedTracking, "返品");
        cancel(returned, """
                {"reason": "返送された", "restock": true}""");
        awaitCollected(Set.of(delivered, cancelled, returned));

        // 確認：売上の推移は、注文基準でも出荷基準でも、配達まで済んだ注文の数量と税込小計だけ
        salesTrend("ORDER").andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].quantity").value(DELIVERED_QUANTITY))
                .andExpect(jsonPath("$.points[0].amount").value(DELIVERED_QUANTITY * PRICE_INCL_TAX));
        salesTrend("SHIPMENT").andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].quantity").value(DELIVERED_QUANTITY))
                .andExpect(jsonPath("$.points[0].amount").value(DELIVERED_QUANTITY * PRICE_INCL_TAX));

        // 確認：バリアントの売れ行きも、配達まで済んだ注文の数量だけ
        mockMvc.perform(get("/api/admin/reports/variant-sales?" + OCTOBER + "&productId=" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].quantity").value(DELIVERED_QUANTITY));

        // 確認：地域の内訳も、配達まで済んだ 1 件だけ（お届け先は東京都）
        mockMvc.perform(get("/api/admin/reports/customer-breakdown?" + OCTOBER + "&productId=" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefectures", hasSize(1)))
                .andExpect(jsonPath("$.prefectures[0].prefectureCode").value(OrderTestData.PREFECTURE_CODE))
                .andExpect(jsonPath("$.prefectures[0].orders").value(1));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文して支払い、受付にする（与信の成功のリスナーが終わるまで待つ）。
     *
     * @param quantity 数量
     * @return 注文 ID
     */
    private UUID acceptedOrder(int quantity) throws Exception {
        var orderId = orders.placeOrder(variantId, quantity);
        // 支払うとリスナーが非同期で注文を受け付け直す。終わる前に出荷やキャンセルをすると、楽観ロックで衝突することがある
        asyncEvents.awaitHandled(OrderTestData.paymentAuthorized(orderId));
        return orderId;
    }

    /**
     * ヤマト運輸で出荷を登録する。
     *
     * @param orderId 注文 ID
     * @return 送り状番号
     */
    private String ship(UUID orderId) throws Exception {
        var trackingNumber = ShippingTestData.trackingNumber();
        shipping.ship(orderId, Carrier.YAMATO, trackingNumber);
        return trackingNumber;
    }

    /**
     * 運営者のキャンセルを送り、204 を確認する。
     *
     * @param orderId 注文 ID
     * @param body    キャンセルの本文
     */
    private void cancel(UUID orderId, String body) throws Exception {
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    /**
     * 注文の状態の変化と配送状態の変化のイベントを、レポートなどのリスナーが処理し終えるまで待つ。
     *
     * @param orderIds 注文 ID
     */
    private void awaitCollected(Set<UUID> orderIds) {
        asyncEvents.awaitPublishedThenHandled(event ->
                (event instanceof OrderStatusChanged order && orderIds.contains(order.orderId()))
                        || (event instanceof DeliveryStatusChanged delivery && orderIds.contains(delivery.orderId())));
    }

    // ============================== 確認の補助 ==============================

    /**
     * このテストの商品で絞った、10 月の月ごとの売上の推移を読む。
     *
     * @param basis 売上の数え方
     * @return 応答（200 を確認済み）
     */
    private ResultActions salesTrend(String basis) throws Exception {
        return mockMvc.perform(get("/api/admin/reports/sales-trend?" + OCTOBER + "&granularity=MONTH&basis=" + basis
                        + "&productId=" + productId))
                .andExpect(status().isOk());
    }
}
