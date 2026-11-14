package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.LogCapture;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>DB の値から注文を戻せないとき、応答が共通のシステムエラーになることと、ログに DB の値が出ないことを確認する。
 * 戻すときの例外の包み方そのものは、{@code RestoreGuard} の単体テストが確認する。
 *
 * <p>列挙の列は制約が壊れた値を入れさせないので、制約のない明細の軸の値（jsonb）を壊す。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class OrderRestoreFailureLogTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * ログの、集約の名前と原因の例外のクラス名の形
     *
     * <p>{@code order restore error: } の後に、パッケージを含む例外のクラス名が続く。
     */
    private static final String RESTORE_ERROR_LOG = "order restore error: [a-z][\\w.]*\\.[A-Z][\\w$]*";

    // ============================== Bean ==============================

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

    /**
     * 全てのロガーに出たログ（壊した値がどのロガーにも出ないことを確認する）
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.all();

    // ============================== テスト ==============================

    @Test
    @DisplayName("注文を戻せないリクエストは、500 の共通のシステムエラーを返す")
    void returnsSystemError() throws Exception {
        // 準備：明細の軸の値を壊した注文
        var orderId = orderWithBrokenLine("secret-" + UUID.randomUUID());

        // 操作と確認：500 で、エラーコードは共通のシステムエラー
        mockMvc.perform(get("/api/store/orders/" + orderId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ERROR"));
    }

    @Test
    @DisplayName("注文を戻せないとき、ログには集約の名前と原因の例外の種類が出て、DB の値は出ない")
    void logsWithoutDatabaseValue() throws Exception {
        // 準備：明細の軸の値を、テストごとの値で壊した注文
        var value = "secret-" + UUID.randomUUID();
        var orderId = orderWithBrokenLine(value);

        // 操作：準備のログを捨ててから、注文を読む
        logs.clear();
        mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(status().isInternalServerError());

        // 確認：この要求のログに、集約の名前と例外のクラス名が出て、壊した値は出ない
        assertThat(logs.text())
                .containsPattern(RESTORE_ERROR_LOG)
                .doesNotContain(value);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文して、明細の軸の値を、軸の値の形でない値に書き換える。
     *
     * @param value 書き換える値（ログに出ないことを確認するので、テストごとに作る）
     * @return 注文 ID
     */
    private UUID orderWithBrokenLine(String value) throws Exception {
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        new InventoryTestData(stockItemRepository, NOW).stock(variantId, product.id, 5);
        var orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, 1);
        dsl.update(SALES_ORDER_LINE)
                .set(SALES_ORDER_LINE.AXIS_VALUES, JSONB.valueOf("[\"" + value + "\"]"))
                .where(SALES_ORDER_LINE.ORDER_ID.eq(orderId))
                .execute();
        return orderId;
    }
}
