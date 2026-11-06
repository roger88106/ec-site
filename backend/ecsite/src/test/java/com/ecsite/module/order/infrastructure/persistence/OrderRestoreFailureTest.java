package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.jooq.ordering.tables.records.SalesOrderRecord;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import org.jooq.JSONB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_LINE;
import static org.mockito.Mockito.mock;

/**
 * 注文を DB の値から戻すときの失敗の単体テスト。例外の出力に DB の値が出ないことを確認する。
 */
class OrderRestoreFailureTest {

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    @Test
    @DisplayName("注文の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        assertFindHides(order -> order.setStatus(value));
    }

    @Test
    @DisplayName("注文時の性別が列挙にないとき、例外の出力に値が出ない")
    void hidesBuyerGender() {
        assertFindHides(order -> order.setBuyerGender(value));
    }

    @Test
    @DisplayName("注文番号の形が違うとき、例外の出力に値が出ない")
    void hidesOrderNumber() {
        assertFindHides(order -> order.setOrderNumber(value));
    }

    @Test
    @DisplayName("キャンセルの申請の結果が列挙にないとき、例外の出力に値が出ない")
    void hidesCancellationRequestResult() {
        assertFindHides(order -> {
            order.setCancellationRequestedAt(Instant.EPOCH);
            order.setCancellationRequestResult(value);
        });
    }

    @Test
    @DisplayName("キャンセルの種類が列挙にないとき、例外の出力に値が出ない")
    void hidesCancellationKind() {
        assertFindHides(order -> {
            order.setCancelledAt(Instant.EPOCH);
            order.setCancellationKind(value);
        });
    }

    @Test
    @DisplayName("明細の軸の値を読めないとき、例外の出力に値が出ない")
    void hidesAxisValues() {
        var line = RECORDS.newRecord(SALES_ORDER_LINE);
        line.setLineNo((short) 1);
        line.setRatePercent(BigDecimal.TEN);
        line.setAxisValues(JSONB.valueOf("[\"" + value + "\"]"));
        var database = new RestoreFailures().with("sales_order", validOrder()).with("sales_order_line", line);

        assertValueIsHidden(() -> repository(database).findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("注文の一覧で状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInList() {
        var row = RECORDS.newRecord(SALES_ORDER.ID, SALES_ORDER.ORDER_NUMBER, SALES_ORDER.ORDERED_AT, SALES_ORDER.RECIPIENT_NAME,
                SALES_ORDER.TOTAL_AMOUNT, SALES_ORDER.STATUS).values(UUID.randomUUID(), "20261010-482915", Instant.EPOCH, "氏名", 0L, value);
        var dsl = new RestoreFailures().with("sales_order", row).dsl();
        var filter = new OrderQueryRepository.Filter(null, null, null, null, null, null);

        assertValueIsHidden(() -> new JooqOrderQueryRepository(dsl).findOrders(filter, new PageRequest(1, 20)), value);
    }

    /**
     * 行の一部を壊して注文を読み、例外の出力に値が出ないことを確認する。
     *
     * @param breaking 行を壊す処理
     */
    private void assertFindHides(Consumer<SalesOrderRecord> breaking) {
        var order = validOrder();
        breaking.accept(order);
        var database = new RestoreFailures().with("sales_order", order);

        assertValueIsHidden(() -> repository(database).findById(UUID.randomUUID()), value);
    }

    /**
     * 注文のリポジトリを作る。
     *
     * @param database 行を返す mock
     * @return リポジトリ
     */
    private static JooqOrderRepository repository(RestoreFailures database) {
        return new JooqOrderRepository(database.dsl(), JsonMapper.builder().build(), mock(ApplicationEventPublisher.class),
                Clock.systemUTC());
    }

    /**
     * 戻せる注文の行を作る。
     *
     * @return 注文の行
     */
    private static SalesOrderRecord validOrder() {
        var order = RECORDS.newRecord(SALES_ORDER);
        order.setId(UUID.randomUUID());
        order.setOrderNumber("20261010-482915");
        order.setOrderedAt(Instant.EPOCH);
        order.setStatus("ACCEPTED");
        order.setItemsTotal(0L);
        order.setShippingFee(0L);
        order.setTotalAmount(0L);
        order.setVersion(0L);
        return order;
    }
}
