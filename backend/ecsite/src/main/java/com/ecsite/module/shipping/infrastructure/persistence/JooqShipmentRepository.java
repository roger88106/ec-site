package com.ecsite.module.shipping.infrastructure.persistence;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.jooq.shipping.tables.records.ShipmentRecord;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.ShippingErrorCode;
import com.ecsite.module.shipping.domain.TrackingNumber;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.shipping.Tables.SHIPMENT;

/**
 * 出荷のリポジトリの jOOQ の実装
 */
@Repository
@RequiredArgsConstructor
class JooqShipmentRepository implements ShipmentRepository {

    /**
     * 制約名 → 業務のエラー
     */
    private static final ConstraintViolationTranslator CONSTRAINTS = ConstraintViolationTranslator.of(Map.of(
            "shipment_order_id_key", () -> BusinessException.of(ShippingErrorCode.SHIPMENT_ALREADY_EXISTS)));

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 時計（作成日時・更新日時）
     */
    private final Clock clock;

    @Override
    public void insert(Shipment shipment) {
        var now = clock.instant();
        try {
            dsl.insertInto(SHIPMENT)
                    .set(SHIPMENT.ID, shipment.id())
                    .set(SHIPMENT.ORDER_ID, shipment.orderId())
                    .set(SHIPMENT.CARRIER, shipment.carrier().name())
                    .set(SHIPMENT.TRACKING_NUMBER, shipment.trackingNumber().value())
                    .set(SHIPMENT.STATUS, shipment.status().name())
                    .set(SHIPMENT.SHIPPED_AT, shipment.shippedAt())
                    .set(SHIPMENT.LAST_NOTIFIED_AT, shipment.lastNotifiedAt().orElse(null))
                    .set(SHIPMENT.CREATED_AT, now)
                    .set(SHIPMENT.UPDATED_AT, now)
                    .execute();
        } catch (DataAccessException e) {
            throw CONSTRAINTS.translate(e);
        }
    }

    @Override
    public List<Shipment> findAllByOrderIds(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(SHIPMENT).where(SHIPMENT.ORDER_ID.in(orderIds)).fetch(JooqShipmentRepository::toShipment);
    }

    @Override
    public List<Shipment> findAllByTrackingForUpdate(Carrier carrier, TrackingNumber trackingNumber) {
        return dsl.selectFrom(SHIPMENT)
                .where(SHIPMENT.CARRIER.eq(carrier.name()), SHIPMENT.TRACKING_NUMBER.eq(trackingNumber.value()))
                .forUpdate()
                .fetch(JooqShipmentRepository::toShipment);
    }

    @Override
    public void updateProgress(Shipment shipment) {
        dsl.update(SHIPMENT)
                .set(SHIPMENT.STATUS, shipment.status().name())
                .set(SHIPMENT.LAST_NOTIFIED_AT, shipment.lastNotifiedAt().orElse(null))
                .set(SHIPMENT.UPDATED_AT, clock.instant())
                .where(SHIPMENT.ID.eq(shipment.id()))
                .execute();
    }

    @Override
    public List<UUID> findReturnedOrderIds() {
        return dsl.select(SHIPMENT.ORDER_ID)
                .from(SHIPMENT)
                .where(SHIPMENT.STATUS.eq(DeliveryStatus.RETURNED.name()))
                .fetch(SHIPMENT.ORDER_ID);
    }

    @Override
    public List<Shipment> findRecent(int limit) {
        return dsl.selectFrom(SHIPMENT)
                .orderBy(SHIPMENT.SHIPPED_AT.desc(), SHIPMENT.ID)
                .limit(limit)
                .fetch(JooqShipmentRepository::toShipment);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 出荷のレコードを、出荷に変換する。
     */
    private static Shipment toShipment(ShipmentRecord record) {
        return Shipment.restore(record.getId(), record.getOrderId(), Carrier.valueOf(record.getCarrier()),
                new TrackingNumber(record.getTrackingNumber()), DeliveryStatus.valueOf(record.getStatus()),
                record.getShippedAt(), record.getLastNotifiedAt());
    }
}
