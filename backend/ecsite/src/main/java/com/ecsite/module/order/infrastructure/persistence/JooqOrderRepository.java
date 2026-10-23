package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
import com.ecsite.jooq.ordering.tables.records.SalesOrderRecord;
import com.ecsite.module.order.domain.order.CancellationKind;
import com.ecsite.module.order.domain.order.Gender;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.AddressArea;
import com.ecsite.module.order.domain.order.BuyerProfile;
import com.ecsite.module.order.domain.order.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.TaxAmount;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_LINE;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_TAX;

/**
 * 注文のリポジトリ。注文、明細、税率ごとの消費税額を、集約の単位で保存・再構築する。
 */
@Repository
@RequiredArgsConstructor
class JooqOrderRepository implements OrderRepository {

    /**
     * 軸の値の一覧の型（jsonb の読み書き）
     */
    private static final TypeReference<List<AxisValue>> AXIS_VALUES = new TypeReference<>() {
    };

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 軸の値の写しを jsonb にするための JSON の変換
     */
    private final JsonMapper jsonMapper;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 時計（作成・更新日時）
     */
    private final Clock clock;

    /**
     * 注文を保存する。注文番号の一意制約に当たったら、何もせずに false を返す。
     *
     * @param order 注文
     * @return 保存したら true
     */
    @Override
    public boolean insert(Order order) {
        var snapshot = order.toSnapshot();
        var now = clock.instant();
        var record = dsl.newRecord(SALES_ORDER);
        writeState(record, snapshot);
        record.setId(snapshot.id());
        record.setOrderNumber(snapshot.orderNumber().value());
        record.setCheckoutId(snapshot.checkoutId());
        record.setMemberId(snapshot.memberId());
        record.setOrderedAt(snapshot.orderedAt());
        record.setItemsTotal(snapshot.amounts().itemsTotal().yen());
        record.setShippingFee(snapshot.amounts().shippingFee().yen());
        record.setTotalAmount(snapshot.amounts().total().yen());
        record.setEmail(snapshot.email());
        var address = snapshot.shippingAddress();
        record.setRecipientName(address.name());
        record.setPostalCode(address.postalCode());
        record.setPrefectureCode(address.area().prefectureCode());
        record.setPrefectureName(address.area().prefectureName());
        record.setMunicipalityCode(address.area().municipalityCode());
        record.setMunicipalityName(address.area().municipalityName());
        record.setAddress(address.address());
        record.setPhone(address.phone());
        var buyer = snapshot.buyerProfile();
        record.setBuyerAge(buyer.age() == null ? null : buyer.age().shortValue());
        record.setBuyerGender(buyer.gender() == null ? null : buyer.gender().name());
        record.setVersion(snapshot.version());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        // 注文番号の重複は、例外にせず（トランザクションを壊さず）挿入されなかったことで知る
        int inserted = dsl.insertInto(SALES_ORDER).set(record)
                .onConflict(SALES_ORDER.ORDER_NUMBER)
                .doNothing()
                .execute();
        if (inserted == 0) {
            return false;
        }
        insertLines(snapshot);
        order.pullEvents().forEach(eventPublisher::publishEvent);
        return true;
    }

    /**
     * 注文の状態の変更を保存する。明細と金額は成立の後に変わらないので、注文の行だけを更新する。
     *
     * @param order 変更した注文
     */
    @Override
    public void update(Order order) {
        var snapshot = order.toSnapshot();
        var record = dsl.newRecord(SALES_ORDER);
        writeState(record, snapshot);
        record.setVersion(snapshot.version() + 1);
        record.setUpdatedAt(clock.instant());
        dsl.update(SALES_ORDER).set(record).where(SALES_ORDER.ID.eq(snapshot.id())).execute();
        order.pullEvents().forEach(eventPublisher::publishEvent);
    }

    /**
     * 注文を行ロックして取得する。
     *
     * @param id 注文 ID
     * @return 注文
     */
    @Override
    public Optional<Order> lockById(UUID id) {
        return dsl.selectFrom(SALES_ORDER)
                .where(SALES_ORDER.ID.eq(id))
                .forUpdate()
                .fetchOptional()
                .map(this::restore);
    }

    /**
     * 注文を取得する。
     *
     * @param id 注文 ID
     * @return 注文
     */
    @Override
    public Optional<Order> findById(UUID id) {
        return dsl.selectFrom(SALES_ORDER)
                .where(SALES_ORDER.ID.eq(id))
                .fetchOptional()
                .map(this::restore);
    }

    /**
     * 注文手続きの注文 ID を取得する。
     *
     * @param checkoutId 注文手続き ID
     * @return 注文 ID
     */
    @Override
    public Optional<UUID> findIdByCheckoutId(UUID checkoutId) {
        return dsl.select(SALES_ORDER.ID)
                .from(SALES_ORDER)
                .where(SALES_ORDER.CHECKOUT_ID.eq(checkoutId))
                .fetchOptional(SALES_ORDER.ID);
    }

    /**
     * 注文番号が使われているか。
     *
     * @param orderNumber 注文番号
     * @return 使われていれば true
     */
    @Override
    public boolean existsByOrderNumber(OrderNumber orderNumber) {
        return dsl.fetchExists(SALES_ORDER, SALES_ORDER.ORDER_NUMBER.eq(orderNumber.value()));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 変わりうる項目（状態、キャンセルの申請、キャンセル）をレコードに書く。
     */
    private static void writeState(SalesOrderRecord record, Order.Snapshot snapshot) {
        record.setStatus(snapshot.status().name());
        var request = snapshot.cancellationRequest();
        record.setCancellationRequestedAt(request == null ? null : request.requestedAt());
        record.setCancellationRequestReason(request == null ? null : request.reason());
        record.setCancellationRequestResult(request == null ? null : request.result().name());
        record.setCancellationDecidedAt(request == null ? null : request.decidedAt());
        var decidedBy = request == null ? null : request.decidedBy();
        record.setCancellationDecidedById(decidedBy == null ? null : decidedBy.id());
        record.setCancellationDecidedByName(decidedBy == null ? null : decidedBy.name());
        var cancellation = snapshot.cancellation();
        record.setCancelledAt(cancellation == null ? null : cancellation.cancelledAt());
        record.setCancellationKind(cancellation == null ? null : cancellation.kind().name());
        record.setCancellationReason(cancellation == null ? null : cancellation.reason());
        record.setCancellationRestocked(cancellation == null ? null : cancellation.restocked());
        var cancelledBy = cancellation == null ? null : cancellation.cancelledBy();
        record.setCancelledById(cancelledBy == null ? null : cancelledBy.id());
        record.setCancelledByName(cancelledBy == null ? null : cancelledBy.name());
    }

    /**
     * 明細と税率ごとの消費税額を保存する。
     */
    private void insertLines(Order.Snapshot snapshot) {
        var lines = dsl.insertInto(SALES_ORDER_LINE, SALES_ORDER_LINE.ORDER_ID, SALES_ORDER_LINE.LINE_NO,
                SALES_ORDER_LINE.VARIANT_ID, SALES_ORDER_LINE.PRODUCT_CODE, SALES_ORDER_LINE.VARIANT_CODE,
                SALES_ORDER_LINE.PRODUCT_NAME, SALES_ORDER_LINE.AXIS_VALUES, SALES_ORDER_LINE.UNIT_PRICE_EXCL_TAX,
                SALES_ORDER_LINE.RATE_PERCENT, SALES_ORDER_LINE.UNIT_PRICE_INCL_TAX, SALES_ORDER_LINE.QUANTITY,
                SALES_ORDER_LINE.SUBTOTAL);
        for (var line : snapshot.lines()) {
            lines = lines.values(snapshot.id(), (short) line.lineNo(), line.variantId(),
                    line.productCode(), line.variantCode(), line.productName(),
                    JSONB.valueOf(jsonMapper.writeValueAsString(line.axisValues())), line.unitPriceExclTax().yen(),
                    line.ratePercent(), line.unitPriceInclTax().yen(), line.quantity(), line.subtotal().yen());
        }
        lines.execute();

        var taxes = snapshot.amounts().taxes();
        if (!taxes.isEmpty()) {
            var insert = dsl.insertInto(SALES_ORDER_TAX, SALES_ORDER_TAX.ORDER_ID, SALES_ORDER_TAX.RATE_PERCENT,
                    SALES_ORDER_TAX.TAXABLE_AMOUNT, SALES_ORDER_TAX.TAX_AMOUNT);
            for (var tax : taxes) {
                insert = insert.values(snapshot.id(), tax.ratePercent(), tax.taxableAmount().yen(),
                        tax.taxAmount().yen());
            }
            insert.execute();
        }
    }

    /**
     * 注文のレコードと、その明細・税率ごとの消費税額から、注文を再構築する。
     */
    private Order restore(SalesOrderRecord record) {
        var lines = dsl.selectFrom(SALES_ORDER_LINE)
                .where(SALES_ORDER_LINE.ORDER_ID.eq(record.getId()))
                .orderBy(SALES_ORDER_LINE.LINE_NO)
                .fetch(line -> new OrderLine(
                        line.getLineNo(),
                        line.getVariantId(),
                        line.getProductCode(),
                        line.getVariantCode(),
                        line.getProductName(),
                        jsonMapper.readValue(line.getAxisValues().data(), AXIS_VALUES),
                        Money.of(line.getUnitPriceExclTax()),
                        normalize(line.getRatePercent()),
                        Money.of(line.getUnitPriceInclTax()),
                        line.getQuantity(),
                        Money.of(line.getSubtotal())));
        var taxes = dsl.selectFrom(SALES_ORDER_TAX)
                .where(SALES_ORDER_TAX.ORDER_ID.eq(record.getId()))
                // 税率の高い順（の表示の順）
                .orderBy(SALES_ORDER_TAX.RATE_PERCENT.desc())
                .fetch(tax -> new TaxAmount(normalize(tax.getRatePercent()), Money.of(tax.getTaxableAmount()),
                        Money.of(tax.getTaxAmount())));
        var amounts = new Amounts(Money.of(record.getItemsTotal()), Money.of(record.getShippingFee()),
                Money.of(record.getTotalAmount()), taxes);
        var area = new AddressArea(record.getPrefectureCode(), record.getPrefectureName(), record.getMunicipalityCode(),
                record.getMunicipalityName());
        var address = new ShippingAddress(record.getRecipientName(), record.getPostalCode(), area, record.getAddress(),
                record.getPhone());
        var request = record.getCancellationRequestedAt() == null ? null : new CancellationRequest(
                record.getCancellationRequestedAt(), record.getCancellationRequestReason(),
                CancellationRequest.Result.valueOf(record.getCancellationRequestResult()),
                record.getCancellationDecidedAt(),
                operatorOf(record.getCancellationDecidedById(), record.getCancellationDecidedByName()));
        var cancellation = record.getCancelledAt() == null ? null : new Cancellation(record.getCancelledAt(),
                CancellationKind.valueOf(record.getCancellationKind()), record.getCancellationReason(),
                operatorOf(record.getCancelledById(), record.getCancelledByName()), record.getCancellationRestocked());
        return Order.restore(new Order.Snapshot(
                record.getId(),
                OrderNumber.of(record.getOrderNumber()),
                record.getCheckoutId(),
                record.getMemberId(),
                record.getOrderedAt(),
                OrderStatus.valueOf(record.getStatus()),
                lines,
                amounts,
                record.getEmail(),
                address,
                new BuyerProfile(record.getBuyerAge() == null ? null : record.getBuyerAge().intValue(),
                        record.getBuyerGender() == null ? null : Gender.valueOf(record.getBuyerGender())),
                request,
                cancellation,
                record.getVersion()));
    }

    /**
     * DB の率（小数第 2 位までの桁を持つ）を、末尾の 0 を除いた形にそろえる（10.00 → 10）。
     */
    private static BigDecimal normalize(BigDecimal ratePercent) {
        var stripped = ratePercent.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * 操作した運営者。記録を始める前の行は空なので null。
     */
    private static @Nullable Operator operatorOf(@Nullable UUID id, String name) {
        return id == null ? null : new Operator(id, name);
    }
}
