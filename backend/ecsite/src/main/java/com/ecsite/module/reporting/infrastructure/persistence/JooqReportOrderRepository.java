package com.ecsite.module.reporting.infrastructure.persistence;

import com.ecsite.common.model.value.BusinessTime;
import com.ecsite.module.reporting.application.port.ReportOrderRepository;
import com.ecsite.module.reporting.domain.DeliveryStatus;
import com.ecsite.module.reporting.domain.HistoryKind;
import com.ecsite.module.reporting.domain.HistoryStatus;
import com.ecsite.module.reporting.domain.ReportCancellationKind;
import com.ecsite.module.reporting.domain.ReportOrder;
import com.ecsite.module.reporting.domain.ReportOrderStatus;
import com.ecsite.module.reporting.domain.StatusHistoryEntry;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER_LINE;
import static com.ecsite.jooq.reporting.Tables.STATUS_HISTORY;

/**
 * 注文の写しと状態の履歴のリポジトリの jOOQ の実装
 */
@Repository
@RequiredArgsConstructor
class JooqReportOrderRepository implements ReportOrderRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 軸の値を jsonb にするための JSON の変換
     */
    private final JsonMapper jsonMapper;

    /**
     * 時計（記録日時、写しの更新日時）
     */
    private final Clock clock;

    /**
     * 注文ごとのロックを、トランザクションの終わりまで取る。
     *
     * @param orderId 注文 ID
     */
    @Override
    public void lock(UUID orderId) {
        // トランザクションの終わりで外れる、注文 ID のロック（PostgreSQL の advisory lock）
        dsl.select(DSL.function("pg_advisory_xact_lock", Object.class,
                DSL.val(orderId.getMostSignificantBits() ^ orderId.getLeastSignificantBits()))).fetch();
    }

    /**
     * 状態の履歴に 1 行追加する。同じイベント ID の行があれば何もしない。
     *
     * @param entry 履歴の行
     * @return 追加したら true
     */
    @Override
    public boolean appendHistory(StatusHistoryEntry entry) {
        return dsl.insertInto(STATUS_HISTORY)
                .set(STATUS_HISTORY.EVENT_ID, entry.eventId())
                .set(STATUS_HISTORY.ORDER_ID, entry.orderId())
                .set(STATUS_HISTORY.KIND, entry.kind().name())
                .set(STATUS_HISTORY.FROM_STATUS, entry.from() == null ? null : entry.from().name())
                .set(STATUS_HISTORY.TO_STATUS, entry.to().name())
                .set(STATUS_HISTORY.OCCURRED_AT, entry.occurredAt())
                .set(STATUS_HISTORY.CANCELLATION_KIND, entry.cancellationKind() == null ? null : entry.cancellationKind().name())
                .set(STATUS_HISTORY.RESTOCKED, entry.restocked())
                .set(STATUS_HISTORY.RECORDED_AT, clock.instant())
                .onConflict(STATUS_HISTORY.EVENT_ID)
                .doNothing()
                .execute() > 0;
    }

    /**
     * 注文の状態の履歴を、発生日時の順に読む。同じ日時はイベント ID の順にする。
     *
     * @param orderId 注文 ID
     * @return 履歴
     */
    @Override
    public List<StatusHistoryEntry> history(UUID orderId) {
        return dsl.selectFrom(STATUS_HISTORY)
                .where(STATUS_HISTORY.ORDER_ID.eq(orderId))
                .orderBy(STATUS_HISTORY.OCCURRED_AT, STATUS_HISTORY.EVENT_ID)
                .fetch(row -> {
                    var kind = HistoryKind.valueOf(row.getKind());
                    return new StatusHistoryEntry(row.getEventId(), row.getOrderId(), kind,
                            row.getFromStatus() == null ? null : historyStatus(kind, row.getFromStatus()),
                            historyStatus(kind, row.getToStatus()), row.getOccurredAt(),
                            row.getCancellationKind() == null ? null : ReportCancellationKind.valueOf(row.getCancellationKind()),
                            row.getRestocked());
                });
    }

    /**
     * 写しの明細の商品 ID を読む。
     *
     * @param orderId 注文 ID
     * @return 行番号 → 商品 ID。写しがない、または商品 ID がない行は含めない
     */
    @Override
    public Map<Integer, UUID> productIds(UUID orderId) {
        return dsl.select(SALES_ORDER_LINE.LINE_NO, SALES_ORDER_LINE.PRODUCT_ID)
                .from(SALES_ORDER_LINE)
                .where(SALES_ORDER_LINE.ORDER_ID.eq(orderId), SALES_ORDER_LINE.PRODUCT_ID.isNotNull())
                .fetch()
                .stream()
                .collect(Collectors.toMap(row -> row.value1().intValue(), row -> row.value2()));
    }

    /**
     * 写しを上書きする（なければ作る）。明細は消して入れ直す。注文日・出荷日は日本時間の日付にする。
     *
     * @param order 写し
     */
    @Override
    public void save(ReportOrder order) {
        var now = clock.instant();
        var record = dsl.newRecord(SALES_ORDER);
        record.setOrderId(order.orderId());
        record.setOrderNumber(order.orderNumber());
        record.setMemberId(order.memberId());
        record.setOrderedAt(order.orderedAt());
        record.setOrderedOn(dateOf(order.orderedAt()));
        record.setStatus(order.status().name());
        record.setShippedAt(order.shippedAt());
        record.setShippedOn(order.shippedAt() == null ? null : dateOf(order.shippedAt()));
        record.setDeliveryStatus(order.deliveryStatus() == null ? null : order.deliveryStatus().name());
        record.setCancelledAt(order.cancelledAt());
        record.setCancellationKind(order.cancellationKind() == null ? null : order.cancellationKind().name());
        record.setRestocked(order.restocked());
        record.setPostalCode(order.postalCode());
        record.setPrefectureCode(order.prefectureCode());
        record.setBuyerAge(order.buyerAge() == null ? null : order.buyerAge().shortValue());
        record.setBuyerGender(order.buyerGender() == null ? null : order.buyerGender().name());
        record.setTotalAmount(order.total().yen());
        record.setShippingFee(order.shippingFee().yen());
        record.setTaxTotal(order.taxTotal().yen());
        record.setShippingTax(order.shippingTax().yen());
        record.setRefreshedAt(now);
        dsl.insertInto(SALES_ORDER).set(record)
                .onConflict(SALES_ORDER.ORDER_ID)
                .doUpdate()
                .set(record)
                .execute();

        dsl.deleteFrom(SALES_ORDER_LINE).where(SALES_ORDER_LINE.ORDER_ID.eq(order.orderId())).execute();
        if (order.lines().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(SALES_ORDER_LINE, SALES_ORDER_LINE.ORDER_ID, SALES_ORDER_LINE.LINE_NO,
                SALES_ORDER_LINE.PRODUCT_ID, SALES_ORDER_LINE.VARIANT_ID, SALES_ORDER_LINE.PRODUCT_CODE,
                SALES_ORDER_LINE.VARIANT_CODE, SALES_ORDER_LINE.PRODUCT_NAME, SALES_ORDER_LINE.AXIS_VALUES,
                SALES_ORDER_LINE.QUANTITY, SALES_ORDER_LINE.RATE_PERCENT, SALES_ORDER_LINE.SUBTOTAL_INCL_TAX,
                SALES_ORDER_LINE.SUBTOTAL_EXCL_TAX);
        for (var line : order.lines()) {
            insert = insert.values(order.orderId(), (short) line.lineNo(), line.productId(),
                    line.variantId(), line.productCode(), line.variantCode(), line.productName(),
                    JSONB.valueOf(jsonMapper.writeValueAsString(line.axisValues())), line.quantity(), line.ratePercent(),
                    line.subtotalInclTax().yen(), line.subtotalExclTax().yen());
        }
        insert.execute();
    }

    /**
     * 履歴の列の状態の値を、出来事に合う列挙にする（注文の状態の変化なら注文の状態、配送状態の変化なら配送状態）。
     */
    private static HistoryStatus historyStatus(HistoryKind kind, String value) {
        return switch (kind) {
            case ORDER_STATUS -> ReportOrderStatus.valueOf(value);
            case DELIVERY_STATUS -> DeliveryStatus.valueOf(value);
        };
    }

    /**
     * 日時の、日本時間の日付
     */
    private static LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, BusinessTime.ZONE);
    }
}
