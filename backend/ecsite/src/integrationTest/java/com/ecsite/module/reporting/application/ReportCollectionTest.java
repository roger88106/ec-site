package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.jooq.reporting.tables.records.SalesOrderLineRecord;
import com.ecsite.jooq.reporting.tables.records.SalesOrderRecord;
import com.ecsite.module.catalog.api.ReportCatalogApi;
import com.ecsite.module.order.api.OrderReportApi;
import com.ecsite.module.order.api.model.OrderReportSnapshot;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.application.model.query.SalesTrendQuery;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.Granularity;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.TrendPoint;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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

import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER_LINE;
import static com.ecsite.jooq.reporting.Tables.STATUS_HISTORY;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 写しと状態の履歴を集める統合テスト
 *
 * <p>注文と出荷のイベントをコミットしてレポートに届け、写しと履歴を DB で確認する。注文と商品カタログの API は mock にし、
 * 注文の今の写しをテストで決める。受けるのはコミットの後で非同期なので、結果を待つ。注文 ID はテストごとに変える。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
class ReportCollectionTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 注文日
     *
     * <p>注文日時の日本時間の日付。
     */
    private static final LocalDate ORDERED_ON = LocalDate.of(2026, 10, 8);

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-000001";

    /**
     * お届け先の郵便番号
     */
    private static final String POSTAL_CODE = "1000001";

    /**
     * お届け先の都道府県のコード
     *
     * <p>東京都。
     */
    private static final String PREFECTURE_CODE = "13";

    /**
     * 注文時の年齢
     */
    private static final int BUYER_AGE = 36;

    /**
     * 明細の数量
     */
    private static final int QUANTITY = 2;

    /**
     * 明細の税込小計
     *
     * <p>税込 1,100 円 × 2 枚。
     */
    private static final long SUBTOTAL_INCL_TAX = 2200;

    /**
     * 明細の税抜小計
     *
     * <p>税抜 1,000 円 × 2 枚。
     */
    private static final long SUBTOTAL_EXCL_TAX = 2000;

    /**
     * 送料
     */
    private static final long SHIPPING_FEE = 1000;

    /**
     * 総額
     *
     * <p>明細の税込小計 ＋ 送料。
     */
    private static final long TOTAL_AMOUNT = 3200;

    /**
     * 消費税の合計
     */
    private static final long TAX_TOTAL = 290;

    /**
     * 送料の消費税
     */
    private static final long SHIPPING_TAX = 90;

    // ============================== Bean ==============================

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

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

    /**
     * 今のテストの注文 ID
     */
    private UUID orderId;

    /**
     * 明細のバリアント ID
     */
    private UUID variantId;

    /**
     * 明細の商品 ID
     */
    private UUID productId;

    @BeforeEach
    void setUp() {
        clock.setInstant(ORDERED_AT);
        orderId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        when(reportCatalogApi.productIdsOf(anyCollection())).thenReturn(Map.of(variantId, productId));
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("写しの作成")
    class Creation {

        @Test
        @DisplayName("AC-223 注文の状態の変化を受けると、注文の API から注文の写しを作り、お届け先は都道府県のコードで持つ")
        void createsOrderSnapshotFromOrderApi() {
            // 準備：注文の API が、支払待ちの注文を返す
            orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));

            // 操作：注文の成立のイベントを届ける
            publish(placed());

            // 確認：注文の写しの項目が、注文の API の値のまま
            assertOrderCopiedFromSnapshot(awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT));
        }

        @Test
        @DisplayName("AC-223 注文の写しを作ると、明細の写しも作り、明細の商品 ID を商品カタログから求める")
        void createsLineSnapshotWithProductId() {
            // 準備：注文の API が、支払待ちの注文を返す
            orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));

            // 操作：注文の成立のイベントを届ける
            publish(placed());
            awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);

            // 確認：明細が 1 行で、商品 ID は商品カタログが返したもの
            assertThat(dsl.selectFrom(SALES_ORDER_LINE).where(SALES_ORDER_LINE.ORDER_ID.eq(orderId)).fetch())
                    .extracting(SalesOrderLineRecord::getProductId, SalesOrderLineRecord::getVariantId,
                            SalesOrderLineRecord::getQuantity, SalesOrderLineRecord::getSubtotalInclTax,
                            SalesOrderLineRecord::getSubtotalExclTax)
                    .containsExactly(tuple(productId, variantId, QUANTITY, SUBTOTAL_INCL_TAX, SUBTOTAL_EXCL_TAX));
        }

        @Test
        @DisplayName("AC-223 商品カタログで商品 ID を求められなくなっても、写しを再作成するときは、前の写しの商品 ID を残す")
        void keepsPreviousProductIdWhenCatalogMisses() {
            // 準備：商品 ID を求めて作った写しと、その後に商品 ID を返さなくなった商品カタログ（商品の削除など）
            orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));
            publish(placed());
            awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);
            when(reportCatalogApi.productIdsOf(anyCollection())).thenReturn(Map.of());
            orderIs(snapshot(OrderReportSnapshot.Status.ACCEPTED, null, null));

            // 操作：受付のイベントを届ける
            publish(OrderStatusChanged.of(ORDERED_AT.plusSeconds(60), orderId, OrderStatusChanged.Status.PENDING_PAYMENT,
                    OrderStatusChanged.Status.ACCEPTED, null, null));
            awaitOrder(OrderReportSnapshot.Status.ACCEPTED);

            // 確認：明細の商品 ID は前の写しのまま
            assertThat(lineProductIds()).containsExactly(productId);
        }

        @Test
        @DisplayName("AC-223 初めて写しを作るときに商品カタログで商品 ID を求められなければ、明細の商品 ID は空")
        void leavesProductIdEmptyWhenCatalogMissesFirst() {
            // 準備：商品 ID を返さない商品カタログ
            when(reportCatalogApi.productIdsOf(anyCollection())).thenReturn(Map.of());
            orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));

            // 操作：注文の成立のイベントを届ける
            publish(placed());
            awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);

            // 確認：明細は 1 行で、商品 ID は空
            assertThat(lineProductIds()).containsExactly((UUID) null);
        }

        @Test
        @DisplayName("AC-224 状態の変化を受けると、出来事・発生日時・前と後の状態を、履歴に 1 行追加する")
        void appendsHistoryRow() {
            // 準備：注文の API が、支払待ちの注文を返す
            orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));

            // 操作：注文の成立のイベントを届ける
            publish(placed());
            awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);

            // 確認：注文の状態の「なし → 支払待ち」が、イベントの発生日時で 1 行
            assertThat(history())
                    .extracting(row -> row.get(STATUS_HISTORY.KIND), row -> row.get(STATUS_HISTORY.FROM_STATUS),
                            row -> row.get(STATUS_HISTORY.TO_STATUS), row -> row.get(STATUS_HISTORY.OCCURRED_AT))
                    .containsExactly(tuple("ORDER_STATUS", null, "PENDING_PAYMENT", ORDERED_AT));
        }
    }

    @Nested
    @DisplayName("集める注文")
    class CollectedOrders {

        @Test
        @DisplayName("AC-223 支払待ちのまま期限切れでキャンセルになった注文も、キャンセルの日時・種類・在庫に戻したかを写しに持つ")
        void collectsExpiredOrder() {
            // 準備：支払待ちの注文を集めた後、注文の API が 30 分後の期限切れのキャンセルを返す
            var expiredAt = collectPendingOrderThatExpires();

            // 操作：期限切れのキャンセルの変化を届ける
            publish(expired(expiredAt));

            // 確認：キャンセルの写しになり、キャンセルの項目が注文の API の値のまま
            var order = awaitOrder(OrderReportSnapshot.Status.CANCELLED);
            assertThat(order.get(SALES_ORDER.CANCELLED_AT)).isEqualTo(expiredAt);
            assertThat(order.get(SALES_ORDER.CANCELLATION_KIND)).isEqualTo("PAYMENT_EXPIRED");
            assertThat(order.get(SALES_ORDER.RESTOCKED)).isTrue();
        }

        @Test
        @DisplayName("AC-223、224 支払待ちのまま期限切れでキャンセルになった注文も、状態の変化を履歴に持つ")
        void collectsExpiredOrderHistory() {
            // 準備：支払待ちの注文を集めた後、注文の API が 30 分後の期限切れのキャンセルを返す
            var expiredAt = collectPendingOrderThatExpires();

            // 操作：期限切れのキャンセルの変化を届ける
            publish(expired(expiredAt));

            // 確認：履歴が 2 行になり、成立と期限切れのキャンセルの 2 行で、キャンセルの種類と在庫に戻したかを持つ
            await().atMost(TIMEOUT).until(() -> history().size() == 2);
            assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.FROM_STATUS), row -> row.get(STATUS_HISTORY.TO_STATUS),
                            row -> row.get(STATUS_HISTORY.CANCELLATION_KIND), row -> row.get(STATUS_HISTORY.RESTOCKED))
                    .containsExactly(
                            tuple(null, "PENDING_PAYMENT", null, null),
                            tuple("PENDING_PAYMENT", "CANCELLED", "PAYMENT_EXPIRED", true));
        }

        @Test
        @DisplayName("AC-223 出荷の後にキャンセルになった（返送の）注文も、写しと履歴に入る")
        void collectsReturnedOrder() {
            // 準備：注文の API が、出荷の後にキャンセルになった注文を返す
            var cancelledAt = ORDERED_AT.plus(Duration.ofDays(5));
            orderIs(snapshot(OrderReportSnapshot.Status.CANCELLED, cancelledAt, OrderReportSnapshot.CancellationKind.OPERATOR));

            // 操作：出荷済みからキャンセルへの変化を届ける
            publish(OrderStatusChanged.of(cancelledAt, orderId, OrderStatusChanged.Status.SHIPPED,
                    OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.OPERATOR, true));

            // 確認：キャンセルの写しができ、履歴に「出荷済み → キャンセル」がある
            awaitOrder(OrderReportSnapshot.Status.CANCELLED);
            assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.FROM_STATUS), row -> row.get(STATUS_HISTORY.TO_STATUS))
                    .containsExactly(tuple("SHIPPED", "CANCELLED"));
        }

        @Test
        @DisplayName("AC-223、225 集めるときは注文の状態で絞らず、支払待ち・受付・キャンセルの注文を全て、その状態のまま写しに入れる")
        void collectsAllStatuses() {
            // 操作：支払待ち・受付・キャンセルの 3 件の注文の変化を届ける
            var collected = collectThreeOrders();

            // 確認：3 件とも、その状態のまま写しに入る
            assertThat(dsl.selectFrom(SALES_ORDER)
                    .where(SALES_ORDER.ORDER_ID.in(collected.pendingId(), collected.acceptedId(), collected.cancelledId())).fetch())
                    .extracting(SalesOrderRecord::getOrderId, SalesOrderRecord::getStatus)
                    .containsExactlyInAnyOrder(tuple(collected.pendingId(), "PENDING_PAYMENT"),
                            tuple(collected.acceptedId(), "ACCEPTED"), tuple(collected.cancelledId(), "CANCELLED"));
        }

        @Test
        @DisplayName("AC-225 集めた注文のうち、どれを数えるかは集計の数え方で決まる")
        void countsByReport() {
            // 準備：支払待ち・受付・キャンセルの 3 件を集める
            collectThreeOrders();

            // 操作：この商品の、2026 年 10 月の注文基準の売上の推移を求める
            var october = ReportPeriod.of(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
            var report = salesTrend.trend(new SalesTrendQuery(october, Granularity.MONTH, Basis.ORDER, productId, false, false));

            // 確認：受付の 1 件の明細だけを数える
            assertThat(report.points()).extracting(TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(tuple(Money.of(SUBTOTAL_INCL_TAX), (long) QUANTITY));
        }
    }

    @Nested
    @DisplayName("届く順と重複")
    class ArrivalOrder {

        @Test
        @DisplayName("AC-224 キャンセルが受付より先に届いても、写しは注文の今の状態（キャンセル）になる")
        void snapshotIsCurrentRegardlessOfOrder() {
            // 準備：注文の API が、今の状態として 1 時間後のキャンセルを返す
            var cancelledAt = ORDERED_AT.plus(Duration.ofHours(1));
            orderIs(snapshot(OrderReportSnapshot.Status.CANCELLED, cancelledAt, OrderReportSnapshot.CancellationKind.PAYMENT_EXPIRED));

            // 操作：キャンセル、受付の順に届ける
            deliverCancellationBeforeAcceptance(cancelledAt);

            // 確認：写しはキャンセルで、キャンセルの日時が注文の API の値
            var order = awaitOrder(OrderReportSnapshot.Status.CANCELLED);
            assertThat(order.get(SALES_ORDER.CANCELLED_AT)).isEqualTo(cancelledAt);
        }

        @Test
        @DisplayName("AC-224 キャンセルが受付より先に届いても、履歴は発生日時の順に読める")
        void historyInOccurredOrder() {
            // 準備：注文の API が、今の状態として 1 時間後のキャンセルを返す
            var cancelledAt = ORDERED_AT.plus(Duration.ofHours(1));
            orderIs(snapshot(OrderReportSnapshot.Status.CANCELLED, cancelledAt, OrderReportSnapshot.CancellationKind.PAYMENT_EXPIRED));

            // 操作：キャンセル、受付の順に届ける
            deliverCancellationBeforeAcceptance(cancelledAt);

            // 確認：発生日時の順に読むと、受付、キャンセルの順
            assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.TO_STATUS)).containsExactly("ACCEPTED", "CANCELLED");
        }

        @Test
        @DisplayName("AC-224 同じイベントが重ねて届いても、履歴は 1 行のまま")
        void ignoresDuplicateEventInHistory() {
            // 操作：同じ成立のイベントを 2 回届ける
            deliverPlacedTwice();

            // 確認：履歴は 1 行
            assertThat(history()).hasSize(1);
        }

        @Test
        @DisplayName("AC-224 同じイベントが重ねて届いても、明細の写しは増えない")
        void ignoresDuplicateEventInLines() {
            // 操作：同じ成立のイベントを 2 回届ける
            deliverPlacedTwice();

            // 確認：明細は 1 行
            assertThat(dsl.fetchCount(SALES_ORDER_LINE, SALES_ORDER_LINE.ORDER_ID.eq(orderId))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("配送状態の変化")
    class DeliveryStatusChanges {

        @Test
        @DisplayName("AC-224 配達完了が出荷済みより先に届いても、出荷日時と出荷日を、履歴の出荷済みの変化から求める")
        void shippedAtFromHistory() {
            // 準備：注文の API が、出荷済みの注文を返す
            orderIs(snapshot(OrderReportSnapshot.Status.SHIPPED, null, null));

            // 操作：配達完了、出荷済みの順に届ける
            var shippedAt = deliverDeliveredBeforeShipped();

            // 確認：出荷日時が入り、出荷済みの変化の日時になる。出荷日はその日本時間の日付（2026-10-09 00:30）
            await().atMost(TIMEOUT).until(() -> order().map(row -> row.get(SALES_ORDER.SHIPPED_AT)).isPresent());
            var order = order().orElseThrow();
            assertThat(order.get(SALES_ORDER.SHIPPED_AT)).isEqualTo(shippedAt);
            assertThat(order.get(SALES_ORDER.SHIPPED_ON)).isEqualTo(LocalDate.of(2026, 10, 9));
        }

        @Test
        @DisplayName("AC-224 配達完了が出荷済みより先に届いても、配送状態は履歴の最も進んだ状態（配達完了）")
        void deliveryStatusFromHistory() {
            // 準備：注文の API が、出荷済みの注文を返す
            orderIs(snapshot(OrderReportSnapshot.Status.SHIPPED, null, null));

            // 操作：配達完了、出荷済みの順に届ける
            deliverDeliveredBeforeShipped();

            // 確認：出荷日時が入った（後に届いた出荷済みを処理した）後も、出荷済みでなく、配達完了のまま
            await().atMost(TIMEOUT).until(() -> order().map(row -> row.get(SALES_ORDER.SHIPPED_AT)).isPresent());
            assertThat(order().orElseThrow().get(SALES_ORDER.DELIVERY_STATUS)).isEqualTo("DELIVERED");
        }

        @Test
        @DisplayName("AC-224 配送状態の変化も、発生日時の順に履歴に入る")
        void deliveryHistoryInOccurredOrder() {
            // 準備：注文の API が、出荷済みの注文を返す
            orderIs(snapshot(OrderReportSnapshot.Status.SHIPPED, null, null));

            // 操作：配達完了、出荷済みの順に届ける
            deliverDeliveredBeforeShipped();

            // 確認：出荷日時が入った（後に届いた出荷済みを処理した）後、配送状態の変化が、出荷済み、配達完了の順
            await().atMost(TIMEOUT).until(() -> order().map(row -> row.get(SALES_ORDER.SHIPPED_AT)).isPresent());
            assertThat(history()).extracting(row -> row.get(STATUS_HISTORY.KIND), row -> row.get(STATUS_HISTORY.TO_STATUS))
                    .containsExactly(tuple("DELIVERY_STATUS", "SHIPPED"), tuple("DELIVERY_STATUS", "DELIVERED"));
        }
    }

    @Test
    @DisplayName("NFR-14 注文の API が失敗したら写しを作らず、未完了のイベントの再発行で再作成する")
    void retriesFailedEvent() {
        // 準備：注文の API が、1 回目は失敗し、2 回目は支払待ちの注文を返す
        when(orderReportApi.find(orderId))
                .thenThrow(new IllegalStateException("注文の API が失敗した"))
                .thenReturn(Optional.of(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null)));
        var event = placed();

        // 操作：注文の成立のイベントを届け、失敗するのを待つ
        publish(event);
        asyncEvents.awaitFailed(event::equals);

        // 確認（前提）：失敗した処理は、写しも履歴も残さない
        assertThat(order()).isEmpty();
        assertThat(history()).isEmpty();

        // 操作：再発行のジョブ（発行から 5 分より前のもの）と同じく、このテストのイベントを再発行する
        clock.advance(Duration.ofMinutes(6));
        asyncEvents.resubmit(event::equals);

        // 確認：写しができ、履歴は 1 行
        awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);
        assertThat(history()).hasSize(1);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の写しが、注文の API の写し（{@link #snapshot}）の値のままであることを確認する。
     *
     * <p>確認すること（期待値は定数。性別は、列に入る文字列で書く）：
     * <ul>
     *   <li>注文番号</li>
     *   <li>注文日（注文日時の日本時間の日付）</li>
     *   <li>都道府県のコード、郵便番号</li>
     *   <li>注文時の年齢、性別</li>
     *   <li>総額、送料、消費税の合計、送料の消費税</li>
     *   <li>出荷日時がない</li>
     * </ul>
     *
     * @param order 確認する注文の写し
     */
    private static void assertOrderCopiedFromSnapshot(Record order) {
        assertThat(order.get(SALES_ORDER.ORDER_NUMBER)).isEqualTo(ORDER_NUMBER);
        assertThat(order.get(SALES_ORDER.ORDERED_ON)).isEqualTo(ORDERED_ON);
        assertThat(order.get(SALES_ORDER.PREFECTURE_CODE)).isEqualTo(PREFECTURE_CODE);
        assertThat(order.get(SALES_ORDER.POSTAL_CODE)).isEqualTo(POSTAL_CODE);
        assertThat(order.get(SALES_ORDER.BUYER_AGE)).isEqualTo((short) BUYER_AGE);
        assertThat(order.get(SALES_ORDER.BUYER_GENDER)).isEqualTo("FEMALE");
        assertThat(order.get(SALES_ORDER.TOTAL_AMOUNT)).isEqualTo(TOTAL_AMOUNT);
        assertThat(order.get(SALES_ORDER.SHIPPING_FEE)).isEqualTo(SHIPPING_FEE);
        assertThat(order.get(SALES_ORDER.TAX_TOTAL)).isEqualTo(TAX_TOTAL);
        assertThat(order.get(SALES_ORDER.SHIPPING_TAX)).isEqualTo(SHIPPING_TAX);
        assertThat(order.get(SALES_ORDER.SHIPPED_AT)).isNull();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 支払待ちの注文を集め、その後は注文の API が、30 分後に期限切れでキャンセルになった注文を返すようにする。
     *
     * @return 期限切れの日時
     */
    private Instant collectPendingOrderThatExpires() {
        orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));
        publish(placed());
        awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);

        var expiredAt = ORDERED_AT.plus(Duration.ofMinutes(30));
        orderIs(snapshot(OrderReportSnapshot.Status.CANCELLED, expiredAt, OrderReportSnapshot.CancellationKind.PAYMENT_EXPIRED));
        return expiredAt;
    }

    /**
     * 支払待ちから期限切れでキャンセル（在庫に戻した）になった変化のイベントを作る。
     *
     * @param expiredAt 期限切れの日時
     * @return イベント
     */
    private OrderStatusChanged expired(Instant expiredAt) {
        return OrderStatusChanged.of(expiredAt, orderId, OrderStatusChanged.Status.PENDING_PAYMENT,
                OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED, true);
    }

    /**
     * キャンセルの変化を、30 秒後の受付の変化より先に届け、両方が履歴に入るのを待つ。
     *
     * <p>キャンセルが写しに入るのを待ってから、受付を届ける。
     *
     * @param cancelledAt キャンセルの日時
     */
    private void deliverCancellationBeforeAcceptance(Instant cancelledAt) {
        publish(OrderStatusChanged.of(cancelledAt, orderId, OrderStatusChanged.Status.ACCEPTED,
                OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.OPERATOR, true));
        awaitOrder(OrderReportSnapshot.Status.CANCELLED);
        publish(OrderStatusChanged.of(ORDERED_AT.plusSeconds(30), orderId, OrderStatusChanged.Status.PENDING_PAYMENT,
                OrderStatusChanged.Status.ACCEPTED, null, null));
        await().atMost(TIMEOUT).until(() -> history().size() == 2);
    }

    /**
     * 同じ成立のイベントを、前の処理が終わってから、もう一度届け、その処理が終わるのを待つ。
     */
    private void deliverPlacedTwice() {
        orderIs(snapshot(OrderReportSnapshot.Status.PENDING_PAYMENT, null, null));
        var event = placed();

        publish(event);
        awaitOrder(OrderReportSnapshot.Status.PENDING_PAYMENT);
        asyncEvents.awaitHandled(event::equals);
        publish(event);
        asyncEvents.awaitHandled(event::equals);
    }

    /**
     * 2 日後の配達完了の変化を、1 日後の出荷済みの変化より先に届ける。
     *
     * <p>配達完了が写しに入るのを待ってから、出荷済みを届ける。出荷済みの処理は待たない。
     *
     * @return 出荷済みの日時
     */
    private Instant deliverDeliveredBeforeShipped() {
        var shippedAt = ORDERED_AT.plus(Duration.ofDays(1));
        var deliveredAt = shippedAt.plus(Duration.ofDays(1));

        publish(DeliveryStatusChanged.of(deliveredAt, orderId, DeliveryStatusChanged.Status.SHIPPED,
                DeliveryStatusChanged.Status.DELIVERED));
        await().atMost(TIMEOUT).until(() -> "DELIVERED".equals(order().map(row -> row.get(SALES_ORDER.DELIVERY_STATUS)).orElse(null)));
        publish(DeliveryStatusChanged.of(shippedAt, orderId, null, DeliveryStatusChanged.Status.SHIPPED));
        return shippedAt;
    }

    /**
     * 支払待ち・受付・キャンセル（期限切れ）の 3 件の注文を、それぞれ新しい注文 ID で集める。
     *
     * @return 3 件の注文 ID
     */
    private ThreeOrders collectThreeOrders() {
        var pendingId = collectOrder(OrderReportSnapshot.Status.PENDING_PAYMENT, null, ORDERED_AT, null,
                OrderStatusChanged.Status.PENDING_PAYMENT, null, null);
        var acceptedId = collectOrder(OrderReportSnapshot.Status.ACCEPTED, null, ORDERED_AT.plusSeconds(30),
                OrderStatusChanged.Status.PENDING_PAYMENT, OrderStatusChanged.Status.ACCEPTED, null, null);
        var cancelledAt = ORDERED_AT.plus(Duration.ofMinutes(30));
        var cancelledId = collectOrder(OrderReportSnapshot.Status.CANCELLED, cancelledAt, cancelledAt,
                OrderStatusChanged.Status.PENDING_PAYMENT, OrderStatusChanged.Status.CANCELLED,
                OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED, true);
        return new ThreeOrders(pendingId, acceptedId, cancelledId);
    }

    /**
     * 新しい注文 ID で、指定の状態の写しを決め、状態の変化のイベントを発行し、写しが指定の状態になるのを待つ。
     *
     * @param status      注文の API が返す状態
     * @param cancelledAt キャンセルの日時。キャンセルでなければ null
     * @param occurredAt  変化の発生日時
     * @param from        前の状態
     * @param to          後の状態
     * @param kind        キャンセルの種類。キャンセルでなければ null
     * @param restocked   在庫に戻したか。キャンセルでなければ null
     * @return 注文 ID
     */
    private UUID collectOrder(OrderReportSnapshot.Status status, Instant cancelledAt, Instant occurredAt,
                              OrderStatusChanged.Status from, OrderStatusChanged.Status to,
                              OrderStatusChanged.CancellationKind kind, Boolean restocked) {
        orderId = UUID.randomUUID();
        orderIs(snapshot(status, cancelledAt, kind == null ? null : OrderReportSnapshot.CancellationKind.valueOf(kind.name())));
        publish(OrderStatusChanged.of(occurredAt, orderId, from, to, kind, restocked));
        awaitOrder(status);
        return orderId;
    }

    /**
     * 注文の API が返す、注文の今の写しを決める。
     *
     * @param snapshot 注文の今の写し
     */
    private void orderIs(OrderReportSnapshot snapshot) {
        when(orderReportApi.find(orderId)).thenReturn(Optional.of(snapshot));
    }

    /**
     * 注文の写しを、定数から作る。
     *
     * <p>T シャツ 2 枚（税込 1,100 円）、送料 1,000 円、東京都、36 歳の女性の注文。キャンセルなら在庫に戻している。
     *
     * @param status           状態
     * @param cancelledAt      キャンセルの日時。キャンセルでなければ null
     * @param cancellationKind キャンセルの種類。キャンセルでなければ null
     * @return 注文の写し
     */
    private OrderReportSnapshot snapshot(OrderReportSnapshot.Status status, Instant cancelledAt,
                                         OrderReportSnapshot.CancellationKind cancellationKind) {
        return new OrderReportSnapshot(orderId, ORDER_NUMBER, UUID.randomUUID(), ORDERED_AT, status,
                cancelledAt, cancellationKind, cancelledAt != null ? Boolean.TRUE : null,
                POSTAL_CODE, PREFECTURE_CODE, BUYER_AGE, OrderReportSnapshot.Gender.FEMALE,
                Money.of(TOTAL_AMOUNT), Money.of(SHIPPING_FEE), Money.of(TAX_TOTAL), Money.of(SHIPPING_TAX),
                List.of(new OrderReportSnapshot.Line(1, variantId, "P-1", "P-1-W", "T シャツ",
                        List.of(new OrderReportSnapshot.AxisValue("色", "白")), QUANTITY, BigDecimal.TEN,
                        Money.of(SUBTOTAL_INCL_TAX), Money.of(SUBTOTAL_EXCL_TAX))));
    }

    /**
     * 成立（支払待ち）のイベントを作る。
     *
     * @return イベント
     */
    private OrderStatusChanged placed() {
        return OrderStatusChanged.of(ORDERED_AT, orderId, null, OrderStatusChanged.Status.PENDING_PAYMENT, null, null);
    }

    /**
     * イベントをトランザクションの中で発行し、コミットする（レポートはコミットの後に受ける）。
     *
     * @param event イベント
     */
    private void publish(Object event) {
        transaction.executeWithoutResult(status -> events.publishEvent(event));
    }

    /**
     * 今のテストの注文の写しを読む。
     *
     * @return 注文の写し。まだなければ空
     */
    private Optional<Record> order() {
        return dsl.selectFrom(SALES_ORDER).where(SALES_ORDER.ORDER_ID.eq(orderId)).fetchOptional().map(Record.class::cast);
    }

    /**
     * 注文の写しが指定の状態になるのを待って返す。
     *
     * @param status 待つ状態
     * @return 注文の写し
     */
    private Record awaitOrder(OrderReportSnapshot.Status status) {
        await().atMost(TIMEOUT).until(() -> order().map(row -> status.name().equals(row.get(SALES_ORDER.STATUS))).orElse(false));
        return order().orElseThrow();
    }

    /**
     * このテストの注文の、明細の商品 ID を行の番号の順に読む。
     *
     * @return 商品 ID（求められていなければ null）
     */
    private List<UUID> lineProductIds() {
        return dsl.select(SALES_ORDER_LINE.PRODUCT_ID).from(SALES_ORDER_LINE)
                .where(SALES_ORDER_LINE.ORDER_ID.eq(orderId))
                .orderBy(SALES_ORDER_LINE.LINE_NO)
                .fetch(SALES_ORDER_LINE.PRODUCT_ID);
    }

    /**
     * このテストの注文の、状態の履歴を発生日時の順に読む。
     *
     * @return 履歴
     */
    private List<Record> history() {
        return dsl.selectFrom(STATUS_HISTORY)
                .where(STATUS_HISTORY.ORDER_ID.eq(orderId))
                .orderBy(STATUS_HISTORY.OCCURRED_AT)
                .fetch()
                .map(Record.class::cast);
    }

    /**
     * 集めた 3 件の注文 ID
     *
     * @param pendingId   支払待ちの注文 ID
     * @param acceptedId  受付の注文 ID
     * @param cancelledId キャンセルの注文 ID
     */
    private record ThreeOrders(UUID pendingId, UUID acceptedId, UUID cancelledId) {
    }
}
