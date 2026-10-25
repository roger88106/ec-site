package com.ecsite.module.reporting.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.api.ReportCatalogApi;
import com.ecsite.module.order.api.OrderReportApi;
import com.ecsite.module.order.api.OrderReportSnapshot;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.application.model.query.SalesTrendQuery;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.Granularity;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.TrendPoint;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER_LINE;
import static com.ecsite.jooq.reporting.Tables.STATUS_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 写しと状態の履歴を集める統合テスト（T0205、AC-223〜225、plan 010 第 1 節）。<br />
 * 注文と出荷のイベントをコミットしてレポートに届け、写しと履歴を DB で確認する。注文と商品カタログの API は mock にし、
 * 注文の今の写しをテストで決める。受けるのはコミットの後で非同期なので、結果を待つ。注文 ID はテストごとに変える。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class ReportCollectionTest {

    /**
     * 注文日時（日本時間の 2026-10-08 00:30）
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    SalesTrendQueryService salesTrend;

    @MockitoBean
    OrderReportApi orderReportApi;

    @MockitoBean
    ReportCatalogApi reportCatalogApi;

    private UUID orderId;

    private UUID variantId;

    private UUID productId;

    @BeforeEach
    void setUp() {
        clock.setInstant(ORDERED_AT);
        orderId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        when(reportCatalogApi.productIdsOf(anyCollection())).thenReturn(Map.of(variantId, productId));
    }

    @Test
    @DisplayName("AC-223 注文の状態の変化を受けると、注文の API から写しを作る。お届け先は都道府県のコードで持ち、明細の商品 ID を求める")
    void createsSnapshotFromOrderApi() {
        orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null));

        publish(placed());

        var order = awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);
        assertThat(order.get(SALES_ORDER.ORDER_NUMBER)).isEqualTo("20261008-000001");
        assertThat(order.get(SALES_ORDER.ORDERED_ON)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(order.get(SALES_ORDER.PREFECTURE_CODE)).isEqualTo("13");
        assertThat(order.get(SALES_ORDER.POSTAL_CODE)).isEqualTo("1000001");
        assertThat(order.get(SALES_ORDER.BUYER_AGE)).isEqualTo((short) 36);
        assertThat(order.get(SALES_ORDER.BUYER_GENDER)).isEqualTo("FEMALE");
        assertThat(order.get(SALES_ORDER.TOTAL_AMOUNT)).isEqualTo(3200L);
        assertThat(order.get(SALES_ORDER.SHIPPING_FEE)).isEqualTo(1000L);
        assertThat(order.get(SALES_ORDER.TAX_TOTAL)).isEqualTo(290L);
        assertThat(order.get(SALES_ORDER.SHIPPING_TAX)).isEqualTo(90L);
        assertThat(order.get(SALES_ORDER.SHIPPED_AT)).isNull();
        assertThat(dsl.selectFrom(SALES_ORDER_LINE).where(SALES_ORDER_LINE.ORDER_ID.eq(orderId)).fetch())
                .extracting(line -> line.getProductId(), line -> line.getVariantId(), line -> line.getQuantity(),
                        line -> line.getSubtotalInclTax(), line -> line.getSubtotalExclTax())
                .containsExactly(tuple(productId, variantId, 2, 2200L, 2000L));
        assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.KIND), row -> row.get(STATUS_HISTORY.TO_STATUS))
                .containsExactly(tuple("ORDER_STATUS", "PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("AC-223、224 支払待ちのまま期限切れでキャンセルになった注文も、写しと履歴に入る")
    void collectsExpiredOrder() {
        orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null));
        publish(placed());
        awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);

        var expiredAt = ORDERED_AT.plus(Duration.ofMinutes(30));
        orderIs(snapshot(OrderReportSnapshot.Status.CANCELLED, expiredAt));
        publish(OrderStatusChanged.of(expiredAt, orderId, OrderStatusChanged.Status.PENDING_PAYMENT,
                OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED, true));

        var order = awaitOrder(OrderReportSnapshot.Status.CANCELLED);
        assertThat(order.get(SALES_ORDER.CANCELLED_AT)).isEqualTo(expiredAt);
        assertThat(order.get(SALES_ORDER.CANCELLATION_KIND)).isEqualTo("PAYMENT_EXPIRED");
        assertThat(order.get(SALES_ORDER.RESTOCKED)).isTrue();
        await().atMost(TIMEOUT).until(() -> history().size() == 2);
        assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.FROM_STATUS), row -> row.get(STATUS_HISTORY.TO_STATUS),
                        row -> row.get(STATUS_HISTORY.CANCELLATION_KIND), row -> row.get(STATUS_HISTORY.RESTOCKED))
                .containsExactly(
                        tuple(null, "PENDING_PAYMENT", null, null),
                        tuple("PENDING_PAYMENT", "CANCELLED", "PAYMENT_EXPIRED", true));
    }

    @Test
    @DisplayName("AC-224 キャンセルが受付より先に届いても、注文の今の写し（キャンセル）になる。履歴は発生日時の順に読める")
    void sameSnapshotRegardlessOfOrder() {
        var cancelledAt = ORDERED_AT.plus(Duration.ofHours(1));
        orderIs(snapshot(OrderReportSnapshot.Status.CANCELLED, cancelledAt));

        publish(OrderStatusChanged.of(cancelledAt, orderId, OrderStatusChanged.Status.ACCEPTED,
                OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.OPERATOR, true));
        awaitOrder(OrderReportSnapshot.Status.CANCELLED);
        publish(OrderStatusChanged.of(ORDERED_AT.plusSeconds(30), orderId, OrderStatusChanged.Status.PENDING_PAYMENT,
                OrderStatusChanged.Status.ACCEPTED, null, null));
        await().atMost(TIMEOUT).until(() -> history().size() == 2);

        assertThat(awaitOrder(OrderReportSnapshot.Status.CANCELLED).get(SALES_ORDER.CANCELLED_AT)).isEqualTo(cancelledAt);
        assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.TO_STATUS)).containsExactly("ACCEPTED", "CANCELLED");
    }

    @Test
    @DisplayName("AC-225 集めるときは注文の状態で絞らず、支払待ち・受付・キャンセルの注文を全て写しに入れる。どれを数えるかは、集計の数え方で決まる")
    void collectsAllOrdersAndCountsByReport() {
        var pendingId = collectOrder(OrderReportSnapshot.Status.PENDING_PAYMENT, null, ORDERED_AT, null,
                OrderStatusChanged.Status.PENDING_PAYMENT, null, null);
        var acceptedId = collectOrder(OrderReportSnapshot.Status.ACCEPTED, null, ORDERED_AT.plusSeconds(30),
                OrderStatusChanged.Status.PENDING_PAYMENT, OrderStatusChanged.Status.ACCEPTED, null, null);
        var cancelledAt = ORDERED_AT.plus(Duration.ofMinutes(30));
        var cancelledId = collectOrder(OrderReportSnapshot.Status.CANCELLED, cancelledAt, cancelledAt,
                OrderStatusChanged.Status.PENDING_PAYMENT, OrderStatusChanged.Status.CANCELLED,
                OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED, true);

        // 集めるときは絞らない：3 件とも、その状態のまま写しに入る
        assertThat(dsl.selectFrom(SALES_ORDER).where(SALES_ORDER.ORDER_ID.in(pendingId, acceptedId, cancelledId)).fetch())
                .extracting(row -> row.getOrderId(), row -> row.getStatus())
                .containsExactlyInAnyOrder(tuple(pendingId, "PENDING_PAYMENT"), tuple(acceptedId, "ACCEPTED"),
                        tuple(cancelledId, "CANCELLED"));
        // どれを数えるかは集計で決まる：注文基準の売上の推移は、受付の 1 件（2 個、2,200 円）だけを数える
        var october = ReportPeriod.of(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
        var report = salesTrend.trend(new SalesTrendQuery(october, Granularity.MONTH, Basis.ORDER, productId, false, false));
        assertThat(report.points()).extracting(TrendPoint::amount, TrendPoint::quantity)
                .containsExactly(tuple(Money.of(2200), 2L));
    }

    @Test
    @DisplayName("AC-224 同じイベントが重ねて届いても、履歴は 1 行で、写しは変わらない")
    void ignoresDuplicateEvent() {
        orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null));
        var event = placed();

        publish(event);
        awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);
        asyncEvents.awaitHandled(event::equals);
        publish(event);

        asyncEvents.awaitHandled(event::equals);
        assertThat(history()).hasSize(1);
        assertThat(dsl.fetchCount(SALES_ORDER_LINE, SALES_ORDER_LINE.ORDER_ID.eq(orderId))).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-224 配送状態の変化を受けると、出荷日時と配送状態を履歴から求める。配達完了が出荷済みより先に届いても同じ")
    void deliveryFromHistory() {
        var shippedAt = ORDERED_AT.plus(Duration.ofDays(1));
        var deliveredAt = shippedAt.plus(Duration.ofDays(1));
        orderIs(snapshot(OrderReportSnapshot.Status.SHIPPED, null));

        publish(DeliveryStatusChanged.of(deliveredAt, orderId, DeliveryStatusChanged.Status.SHIPPED,
                DeliveryStatusChanged.Status.DELIVERED));
        await().atMost(TIMEOUT).until(() -> "DELIVERED".equals(order().map(row -> row.get(SALES_ORDER.DELIVERY_STATUS)).orElse(null)));
        publish(DeliveryStatusChanged.of(shippedAt, orderId, null, DeliveryStatusChanged.Status.SHIPPED));

        await().atMost(TIMEOUT).until(() -> order().map(row -> row.get(SALES_ORDER.SHIPPED_AT)).isPresent()
                && order().get().get(SALES_ORDER.SHIPPED_AT) != null);
        var order = order().orElseThrow();
        assertThat(order.get(SALES_ORDER.SHIPPED_AT)).isEqualTo(shippedAt);
        // 日本時間の 2026-10-09 00:30
        assertThat(order.get(SALES_ORDER.SHIPPED_ON)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(order.get(SALES_ORDER.DELIVERY_STATUS)).isEqualTo("DELIVERED");
        assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.KIND), row -> row.get(STATUS_HISTORY.TO_STATUS))
                .containsExactly(tuple("DELIVERY_STATUS", "SHIPPED"), tuple("DELIVERY_STATUS", "DELIVERED"));
    }

    @Test
    @DisplayName("NFR-14 注文の API が失敗したら写しを作らず、未完了のイベントの再発行で再作成する")
    void retriesFailedEvent() {
        when(orderReportApi.find(orderId))
                .thenThrow(new IllegalStateException("注文の API が失敗した"))
                .thenReturn(Optional.of(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null)));

        var event = placed();
        publish(event);

        asyncEvents.awaitFailed(event::equals);
        assertThat(order()).isEmpty();
        assertThat(history()).isEmpty();

        // 再発行のジョブ（発行から 5 分より前のもの）と同じ
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);
        assertThat(history()).hasSize(1);
    }

    // ============================== 補助 ==============================

    /**
     * 注文の API が返す、注文の今の写しを決める
     */
    private void orderIs(OrderReportSnapshot snapshot) {
        when(orderReportApi.find(orderId)).thenReturn(Optional.of(snapshot));
    }

    /**
     * T シャツ 2 枚（税込 1,100 円）、送料 1,000 円、東京都（13）、36 歳の女性の注文の写し
     */
    private OrderReportSnapshot snapshot(OrderReportSnapshot.Status status, Instant cancelledAt) {
        var cancelled = cancelledAt != null;
        return new OrderReportSnapshot(orderId, "20261008-000001", UUID.randomUUID(), ORDERED_AT, status,
                cancelledAt,
                cancelled ? OrderReportSnapshot.CancellationKind.PAYMENT_EXPIRED : null,
                cancelled ? Boolean.TRUE : null,
                "1000001", "13", 36, OrderReportSnapshot.Gender.FEMALE,
                Money.of(3200), Money.of(1000), Money.of(290), Money.of(90),
                List.of(new OrderReportSnapshot.Line(1, variantId, "P-1", "P-1-W", "T シャツ",
                        List.of(new OrderReportSnapshot.AxisValue("色", "白")), 2, BigDecimal.TEN, Money.of(2200),
                        Money.of(2000))));
    }

    /**
     * 作成（支払待ち）のイベント
     */
    private OrderStatusChanged placed() {
        return OrderStatusChanged.of(ORDERED_AT, orderId, null, OrderStatusChanged.Status.PENDING_PAYMENT, null, null);
    }

    /**
     * 新しい注文 ID で、指定の状態の写しを決め、状態の変化のイベントを発行し、写しが指定の状態になるのを待つ。
     *
     * @return 注文 ID
     */
    private UUID collectOrder(OrderReportSnapshot.Status status, Instant cancelledAt, Instant occurredAt,
                              OrderStatusChanged.Status from, OrderStatusChanged.Status to,
                              OrderStatusChanged.CancellationKind kind, Boolean restocked) {
        orderId = UUID.randomUUID();
        orderIs(snapshot(status, cancelledAt));
        publish(OrderStatusChanged.of(occurredAt, orderId, from, to, kind, restocked));
        awaitOrder(status);
        return orderId;
    }

    /**
     * イベントをトランザクションの中で発行し、コミットする（レポートはコミットの後に受ける）。
     */
    private void publish(Object event) {
        transaction.executeWithoutResult(status -> events.publishEvent(event));
    }

    /**
     * 注文の写し
     */
    private Optional<Record> order() {
        return dsl.selectFrom(SALES_ORDER).where(SALES_ORDER.ORDER_ID.eq(orderId)).fetchOptional().map(Record.class::cast);
    }

    /**
     * 注文の写しが指定の状態になるのを待って返す
     */
    private Record awaitOrder(OrderReportSnapshot.Status status) {
        await().atMost(TIMEOUT).until(() -> order().map(row -> status.name().equals(row.get(SALES_ORDER.STATUS))).orElse(false));
        return order().orElseThrow();
    }

    /**
     * 注文の状態の履歴（発生日時の順）
     */
    private List<Record> history() {
        return dsl.selectFrom(STATUS_HISTORY)
                .where(STATUS_HISTORY.ORDER_ID.eq(orderId))
                .orderBy(STATUS_HISTORY.OCCURRED_AT)
                .fetch()
                .map(Record.class::cast);
    }
}
