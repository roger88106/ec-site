package com.ecsite.module.reporting.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.port.ReportOrderRepository;
import com.ecsite.module.reporting.domain.snapshot.DeliveryStatus;
import com.ecsite.module.reporting.domain.snapshot.HistoryKind;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import com.ecsite.module.reporting.domain.snapshot.StatusHistoryEntry;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レポートの注文の写しと状態の履歴のリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqReportOrderRepositoryTest {

    /**
     * 注文日時。日本時間では翌日の 0 時 30 分
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 出荷日時。日本時間では同じ日の 23 時 30 分
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-09T14:30:00Z");

    @Autowired
    ReportOrderRepository repository;

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("写しを保存すると、注文の項目と明細を保存し、注文日と出荷日は日本時間の日付になる")
    void savesSnapshotWithJapanDates() {
        var productId = UUID.randomUUID();
        var order = order(UUID.randomUUID(), ReportOrderStatus.SHIPPED, List.of(line(1, productId), line(2, null)))
                .withShipment(SHIPPED_AT, DeliveryStatus.IN_TRANSIT);

        repository.save(order);

        var row = dsl.fetchOne("select * from reporting.sales_order where order_id = ?", order.orderId());
        assertThat(row).isNotNull();
        assertThat(row.get("order_number", String.class)).isEqualTo("20261008-000001");
        assertThat(row.get("member_id", UUID.class)).isEqualTo(order.memberId());
        assertThat(row.get("ordered_on", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(row.get("shipped_on", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(row.get("status", String.class)).isEqualTo("SHIPPED");
        assertThat(row.get("delivery_status", String.class)).isEqualTo("IN_TRANSIT");
        assertThat(row.get("postal_code", String.class)).isEqualTo("1000001");
        assertThat(row.get("prefecture_code", String.class)).isEqualTo("13");
        assertThat(row.get("buyer_age", Integer.class)).isEqualTo(36);
        assertThat(row.get("buyer_gender", String.class)).isEqualTo("FEMALE");
        assertThat(row.get("total_amount", Long.class)).isEqualTo(5302L);
        assertThat(row.get("shipping_fee", Long.class)).isEqualTo(1000L);
        assertThat(row.get("tax_total", Long.class)).isEqualTo(458L);
        assertThat(row.get("shipping_tax", Long.class)).isEqualTo(91L);
        assertThat(dsl.fetchValue("select count(*) from reporting.sales_order_line where order_id = ?", order.orderId()))
                .isEqualTo(2L);
        assertThat(dsl.fetchValue("select axis_values::text from reporting.sales_order_line where order_id = ? and line_no = 1",
                order.orderId())).isEqualTo("[{\"name\": \"色\", \"value\": \"白\"}]");
        assertThat(repository.productIds(order.orderId())).isEqualTo(Map.of(1, productId));
    }

    @Test
    @DisplayName("同じ注文の写しをもう一度保存すると、注文の項目を上書きし、明細は入れ直す。注文の行は増えない")
    void overwritesSnapshotAndReplacesLines() {
        var orderId = UUID.randomUUID();
        repository.save(order(orderId, ReportOrderStatus.ACCEPTED,
                List.of(line(1, UUID.randomUUID()), line(2, UUID.randomUUID()), line(3, UUID.randomUUID()))));
        var newProductId = UUID.randomUUID();
        var cancelled = new ReportOrder(orderId, "20261008-000001", UUID.randomUUID(), ORDERED_AT,
                ReportOrderStatus.CANCELLED, null, null, SHIPPED_AT, ReportCancellationKind.OPERATOR, true, "1000001",
                "13", 36, ReportBuyerGender.FEMALE, Money.of(5302), Money.of(1000), Money.of(458), Money.of(91),
                List.of(line(1, newProductId)));

        repository.save(cancelled);

        assertThat(dsl.fetchValue("select count(*) from reporting.sales_order where order_id = ?", orderId)).isEqualTo(1L);
        assertThat(dsl.fetchValue("select status from reporting.sales_order where order_id = ?", orderId))
                .isEqualTo("CANCELLED");
        assertThat(dsl.fetchValue("select cancellation_kind from reporting.sales_order where order_id = ?", orderId))
                .isEqualTo("OPERATOR");
        assertThat(dsl.fetchValue("select restocked from reporting.sales_order where order_id = ?", orderId))
                .isEqualTo(true);
        assertThat(dsl.fetchValue("select count(*) from reporting.sales_order_line where order_id = ?", orderId))
                .isEqualTo(1L);
        assertThat(repository.productIds(orderId)).isEqualTo(Map.of(1, newProductId));
    }

    @Test
    @DisplayName("明細の商品 ID は、商品 ID のない行を含めず、写しのない注文には空を返す")
    void readsProductIds() {
        assertThat(repository.productIds(UUID.randomUUID())).isEmpty();

        var order = order(UUID.randomUUID(), ReportOrderStatus.ACCEPTED, List.of(line(1, null), line(2, null)));
        repository.save(order);

        assertThat(repository.productIds(order.orderId())).isEmpty();
    }

    @Test
    @DisplayName("状態の履歴を追加すると、注文の状態と配送状態を、発生日時の順に、キャンセルの種類と在庫に戻したかを含めて読める")
    void appendsAndReadsHistory() {
        var orderId = UUID.randomUUID();
        var cancelled = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.ORDER_STATUS,
                ReportOrderStatus.ACCEPTED, ReportOrderStatus.CANCELLED, ORDERED_AT.plusSeconds(120),
                ReportCancellationKind.OPERATOR, true);
        var accepted = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.ORDER_STATUS,
                ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, ORDERED_AT.plusSeconds(60), null, null);
        var delivery = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.DELIVERY_STATUS, null,
                DeliveryStatus.SHIPPED, ORDERED_AT.plusSeconds(90), null, null);

        assertThat(repository.appendHistory(cancelled)).isTrue();
        assertThat(repository.appendHistory(accepted)).isTrue();
        assertThat(repository.appendHistory(delivery)).isTrue();

        assertThat(repository.history(orderId)).containsExactly(accepted, delivery, cancelled);
        assertThat(repository.history(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("同じイベント ID の履歴を重ねて追加すると、追加せずに false を返し、履歴は 1 行のまま")
    void ignoresDuplicateHistory() {
        var orderId = UUID.randomUUID();
        var entry = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.ORDER_STATUS,
                ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, ORDERED_AT, null, null);
        repository.appendHistory(entry);

        var duplicate = new StatusHistoryEntry(entry.eventId(), orderId, HistoryKind.ORDER_STATUS,
                ReportOrderStatus.ACCEPTED, ReportOrderStatus.CANCELLED, ORDERED_AT.plusSeconds(60),
                ReportCancellationKind.PAYMENT_EXPIRED, true);

        assertThat(repository.appendHistory(duplicate)).isFalse();

        assertThat(repository.history(orderId)).containsExactly(entry);
    }

    // ============================== 補助 ==============================

    /**
     * 注文の写し。注文日時は日本時間では翌日になる
     */
    private static ReportOrder order(UUID orderId, ReportOrderStatus status, List<ReportOrder.Line> lines) {
        return new ReportOrder(orderId, "20261008-000001", UUID.randomUUID(), ORDERED_AT, status, null, null, null, null,
                null, "1000001", "13", 36, ReportBuyerGender.FEMALE, Money.of(5302), Money.of(1000), Money.of(458),
                Money.of(91), lines);
    }

    /**
     * 指定した行番号と商品の注文の明細を作る
     */
    private static ReportOrder.Line line(int lineNo, UUID productId) {
        return new ReportOrder.Line(lineNo, productId, UUID.randomUUID(), "TS-001", "TS-001-WM", "T シャツ",
                List.of(new ReportOrder.AxisValue("色", "白")), 1, BigDecimal.TEN, Money.of(2970), Money.of(2700));
    }
}
