package com.ecsite.module.order.presentation;

import com.ecsite.CapturedLogs;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_LINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文を戻せなかったリクエストの応答とログのテスト
 *
 * <p>列挙の列は制約が壊れた値を入れさせないので、制約のない明細の軸の値（jsonb）を壊す。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class OrderRestoreFailureLogTest {

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

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

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("注文を戻せないリクエストは 500 の共通のシステムエラーを返し、ログには集約の名前と原因の例外の種類が出て、DB の値は出ない")
    void logsWithoutDatabaseValue(CapturedOutput output) throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        new InventoryTestData(stockItemRepository, NOW).stock(variantId, product.id, 5);
        var orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, 1);
        var value = "secret-" + UUID.randomUUID();
        dsl.update(SALES_ORDER_LINE)
                .set(SALES_ORDER_LINE.AXIS_VALUES, JSONB.valueOf("[\"" + value + "\"]"))
                .where(SALES_ORDER_LINE.ORDER_ID.eq(orderId))
                .execute();

        var logStart = CapturedLogs.mark(output);
        mockMvc.perform(get("/api/store/orders/" + orderId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ERROR"));

        assertThat(CapturedLogs.since(output, logStart))
                .contains("order restore error: ")
                .doesNotContain(value);
    }
}
