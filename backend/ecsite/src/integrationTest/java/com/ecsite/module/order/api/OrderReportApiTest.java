package com.ecsite.module.order.api;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.ShippingTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * レポート用の注文の写しの API の統合テスト（T0112、plan 010 第 2 節）。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class OrderReportApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    OrderPaymentService orderPaymentService;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    @Autowired
    OrderReportApi orderReportApi;

    private OrderTestData orders;

    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        gateway.reset();
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        shirt = data.product(data.taxCategory("標準", "10"), "T シャツ", 1000, "色").variant("白").publish();
        var variantId = shirt.variantIds.getFirst();
        inventory.stock(variantId, shirt.id, 10);
        orders = new OrderTestData(mockMvc, gateway, orderPaymentService);
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("支払待ちの注文の写し：お届け先は郵便番号と都道府県だけ。金額、送料の消費税、明細の税込・税抜の小計を返す")
    void returnsPendingOrder() throws Exception {
        var orderId = orders.placePendingOrder(shirt.variantIds.getFirst(), 2);

        var snapshot = orderReportApi.find(orderId).orElseThrow();

        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.orderNumber()).matches("20261008-\\d{6}");
        assertThat(snapshot.memberId()).isNotNull();
        assertThat(snapshot.orderedAt()).isEqualTo(NOW);
        assertThat(snapshot.status()).isEqualTo(OrderReportSnapshot.Status.PENDING_PAYMENT);
        assertThat(snapshot.cancelledAt()).isNull();
        assertThat(snapshot.postalCode()).isEqualTo("1000001");
        assertThat(snapshot.prefectureCode()).isEqualTo("13");
        // 既定の会員の token には、生年月日と性別がない
        assertThat(snapshot.buyerAge()).isNull();
        assertThat(snapshot.buyerGender()).isNull();
        // 1,100 円 × 2 ＋ 送料 1,000 円。消費税は 3,200 × 10 / 110 の切り捨て、送料の分は 1,000 × 10 / 110 の切り捨て
        assertThat(snapshot.total()).isEqualTo(Money.of(3200));
        assertThat(snapshot.shippingFee()).isEqualTo(Money.of(1000));
        assertThat(snapshot.taxTotal()).isEqualTo(Money.of(290));
        assertThat(snapshot.shippingTax()).isEqualTo(Money.of(90));
        assertThat(snapshot.lines()).singleElement().satisfies(line -> {
            assertThat(line.lineNo()).isEqualTo(1);
            assertThat(line.variantId()).isEqualTo(shirt.variantIds.getFirst());
            assertThat(line.productName()).isEqualTo("T シャツ");
            assertThat(line.axisValues()).containsExactly(new OrderReportSnapshot.AxisValue("色", "白"));
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.ratePercent()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(line.subtotalInclTax()).isEqualTo(Money.of(2200));
            assertThat(line.subtotalExclTax()).isEqualTo(Money.of(2000));
        });
    }

    @Test
    @DisplayName("出荷済みの注文の写しは、状態が出荷済み")
    void returnsShippedOrder() throws Exception {
        var orderId = orders.placeOrder(shirt.variantIds.getFirst(), 1);
        new ShippingTestData(mockMvc, mockCarrier).ship(orderId, Carrier.YAMATO, "123456789012");

        assertThat(orderReportApi.find(orderId).orElseThrow().status()).isEqualTo(OrderReportSnapshot.Status.SHIPPED);
    }

    @Test
    @DisplayName("キャンセルの注文の写しは、キャンセル日時、種類、在庫に戻したかを返す")
    void returnsCancelledOrder() throws Exception {
        var orderId = orders.placeOrder(shirt.variantIds.getFirst(), 1);
        var cancelledAt = NOW.plus(Duration.ofHours(1));
        clock.setInstant(cancelledAt);
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "在庫の誤り"}"""))
                .andExpect(status().isNoContent());

        var snapshot = orderReportApi.find(orderId).orElseThrow();

        assertThat(snapshot.status()).isEqualTo(OrderReportSnapshot.Status.CANCELLED);
        assertThat(snapshot.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(snapshot.cancellationKind()).isEqualTo(OrderReportSnapshot.CancellationKind.OPERATOR);
        assertThat(snapshot.restocked()).isTrue();
    }

    @Test
    @DisplayName("ない注文は空")
    void returnsEmptyForUnknownOrder() {
        assertThat(orderReportApi.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("NFR-16 写しに、氏名・住所・電話番号・メールアドレスの項目を持たない")
    void excludesPersonalInformation() {
        var components = List.of(OrderReportSnapshot.class.getRecordComponents()).stream()
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .toList();

        assertThat(components).noneMatch(name -> name.contains("name") || name.equals("address")
                || name.contains("phone") || name.contains("email"));
    }
}
