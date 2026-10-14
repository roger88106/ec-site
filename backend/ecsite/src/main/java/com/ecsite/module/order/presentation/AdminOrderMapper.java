package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.model.Cancellation;
import com.ecsite.api.admin.model.CancellationRequest;
import com.ecsite.api.admin.model.OrderActions;
import com.ecsite.api.admin.model.OrderAmounts;
import com.ecsite.api.admin.model.OrderAxisValue;
import com.ecsite.api.admin.model.OrderDetail;
import com.ecsite.api.admin.model.OrderLine;
import com.ecsite.api.admin.model.OrderStatus;
import com.ecsite.api.admin.model.OrderSummary;
import com.ecsite.api.admin.model.OrderSummaryPage;
import com.ecsite.api.admin.model.OrderTaxAmount;
import com.ecsite.api.admin.model.Payment;
import com.ecsite.api.admin.model.PaymentStatus;
import com.ecsite.api.admin.model.Shipment;
import com.ecsite.api.admin.model.ShipmentStatus;
import com.ecsite.api.admin.model.ShippingAddress;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.presentation.ApiDateTimes;
import com.ecsite.module.order.application.model.OrderPayment;
import com.ecsite.module.order.application.port.OrderQueryRepository.OrderSummaryView;
import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.quote.Amounts;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 管理 API（注文）のレスポンスと、アプリケーション層のモデルの変換
 */
@Component
class AdminOrderMapper {

    /**
     * 注文一覧のページのレスポンスに変換する。
     *
     * @param page      注文一覧のページ
     * @param payments  注文 ID → 支払
     * @param shipments 注文 ID → 出荷
     * @return 注文一覧のページのレスポンス
     */
    OrderSummaryPage toOrderSummaryPage(PageResult<OrderSummaryView> page, Map<UUID, OrderPayment> payments,
                                        Map<UUID, ShippingDirectory.Shipment> shipments) {
        return new OrderSummaryPage()
                .page(page.page())
                .size(page.size())
                .totalItems(page.totalItems())
                .totalPages(page.totalPages())
                .items(page.items().stream().map(view -> toSummary(view, payments.get(view.id()), shipments.get(view.id()))).toList());
    }

    /**
     * 注文の詳細のレスポンスに変換する。申請・キャンセル・支払・出荷がなければ、その項目を返さない。
     *
     * @param order    注文
     * @param payment  注文の支払。決済を入れる前の注文は null
     * @param shipment 注文の出荷。出荷していなければ null
     * @return 注文の詳細のレスポンス
     */
    OrderDetail toOrderDetail(Order order, @Nullable OrderPayment payment, @Nullable ShippingDirectory.Shipment shipment) {
        var returned = shipment != null && shipment.isReturned();
        var detail = new OrderDetail()
                .id(order.id())
                .orderNumber(order.orderNumber().value())
                .orderedAt(ApiDateTimes.toOffsetDateTime(order.orderedAt()))
                .status(toStatus(order.status()))
                .lines(order.lines().stream().map(AdminOrderMapper::toLine).toList())
                .amounts(toAmounts(order.amounts()))
                .email(order.email())
                .shippingAddress(toAddress(order.shippingAddress()))
                .actions(new OrderActions(order.isCancellationPending(), order.isCancellationPending(),
                        order.isCancellableByOperator(returned), order.isShippable()));
        if (order.cancellationRequest() != null) {
            detail.cancellationRequest(toCancellationRequest(order.cancellationRequest()));
        }
        if (order.cancellation() != null) {
            detail.cancellation(toCancellation(order.cancellation()));
        }
        if (payment != null) {
            detail.payment(new Payment()
                    .status(PaymentStatus.fromValue(payment.status().name()))
                    .deadline(ApiDateTimes.toOffsetDateTime(payment.deadline()))
                    .authorizationDeadline(payment.authorizationDeadline() == null ? null
                            : ApiDateTimes.toOffsetDateTime(payment.authorizationDeadline()))
                    .authorizationExpiring(isAuthorizationExpiring(order.status(), payment)));
        }
        if (shipment != null) {
            detail.shipment(toShipment(shipment));
        }
        return detail;
    }

    /**
     * 出荷のレスポンスに変換する。
     *
     * @param shipment 注文の出荷
     * @return 出荷のレスポンス
     */
    Shipment toShipment(ShippingDirectory.Shipment shipment) {
        return new Shipment()
                .carrier(com.ecsite.api.admin.model.Carrier.fromValue(shipment.carrier().name()))
                .trackingNumber(shipment.trackingNumber())
                .status(ShipmentStatus.fromValue(shipment.status().name()))
                .shippedAt(ApiDateTimes.toOffsetDateTime(shipment.shippedAt()));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 注文一覧の 1 件。支払のない注文（決済を入れる前の注文）は支払の状態を、出荷していない注文は配送状態を返さない。
     */
    private static OrderSummary toSummary(OrderSummaryView view, @Nullable OrderPayment payment,
                                          @Nullable ShippingDirectory.Shipment shipment) {
        var summary = new OrderSummary()
                .id(view.id())
                .orderNumber(view.orderNumber())
                .orderedAt(ApiDateTimes.toOffsetDateTime(view.orderedAt()))
                .customerName(view.customerName())
                .total(view.total())
                .status(toStatus(view.status()))
                .authorizationExpiring(isAuthorizationExpiring(view.status(), payment));
        if (payment != null) {
            summary.paymentStatus(PaymentStatus.fromValue(payment.status().name()));
        }
        if (shipment != null) {
            summary.shipmentStatus(ShipmentStatus.fromValue(shipment.status().name()));
        }
        return summary;
    }

    /**
     * 注文明細 1 件（注文時点の写し）。
     */
    private static OrderLine toLine(com.ecsite.module.order.domain.order.OrderLine line) {
        return new OrderLine()
                .variantId(line.variantId())
                .productCode(line.productCode())
                .variantCode(line.variantCode())
                .productName(line.productName())
                .axisValues(line.axisValues().stream().map(AdminOrderMapper::toAxisValue).toList())
                .unitPriceExclTax(line.unitPriceExclTax().yen())
                .unitPriceInclTax(line.unitPriceInclTax().yen())
                .ratePercent(line.ratePercent())
                .quantity(line.quantity())
                .subtotal(line.subtotal().yen());
    }

    /**
     * 金額の内訳。
     */
    private static OrderAmounts toAmounts(Amounts amounts) {
        return new OrderAmounts()
                .itemsTotal(amounts.itemsTotal().yen())
                .shippingFee(amounts.shippingFee().yen())
                .total(amounts.total().yen())
                .taxes(amounts.taxes().stream().map(AdminOrderMapper::toTaxAmount).toList());
    }

    /**
     * 1 つの税率の消費税額。
     */
    private static OrderTaxAmount toTaxAmount(com.ecsite.module.order.domain.quote.TaxAmount tax) {
        return new OrderTaxAmount()
                .ratePercent(tax.ratePercent())
                .taxableAmount(tax.taxableAmount().yen())
                .taxAmount(tax.taxAmount().yen());
    }

    /**
     * お届け先。
     */
    private static ShippingAddress toAddress(com.ecsite.module.order.domain.order.ShippingAddress address) {
        return new ShippingAddress()
                .name(address.name())
                .postalCode(address.postalCode())
                .prefecture(address.prefecture())
                .address(address.address())
                .phone(address.phone());
    }

    /**
     * キャンセルの申請。
     */
    private static CancellationRequest toCancellationRequest(
            com.ecsite.module.order.domain.order.CancellationRequest request) {
        return new CancellationRequest()
                .requestedAt(ApiDateTimes.toOffsetDateTime(request.requestedAt()))
                .reason(request.reason())
                .result(CancellationRequest.ResultEnum.fromValue(request.result().name()))
                .decidedAt(ApiDateTimes.toOffsetDateTime(request.decidedAt()))
                .decidedBy(request.decidedBy() == null ? null : request.decidedBy().name());
    }

    /**
     * キャンセル。
     */
    private static Cancellation toCancellation(com.ecsite.module.order.domain.order.Cancellation cancellation) {
        return new Cancellation()
                .cancelledAt(ApiDateTimes.toOffsetDateTime(cancellation.cancelledAt()))
                .kind(com.ecsite.api.admin.model.CancellationKind.valueOf(cancellation.kind().name()))
                .reason(cancellation.reason())
                .restocked(cancellation.restocked())
                .cancelledBy(cancellation.cancelledBy() == null ? null : cancellation.cancelledBy().name());
    }

    /**
     * 軸の名前と値。
     */
    private static OrderAxisValue toAxisValue(com.ecsite.module.order.domain.quote.AxisValue axis) {
        return new OrderAxisValue()
                .name(axis.name())
                .value(axis.value());
    }

    /**
     * 与信期限が近い印を付けるか。「受付」の注文だけに付ける（キャンセル済みの注文は、与信期限切れのままでも付けない）。
     */
    private static boolean isAuthorizationExpiring(com.ecsite.module.order.domain.order.OrderStatus status,
                                                   @Nullable OrderPayment payment) {
        return status == com.ecsite.module.order.domain.order.OrderStatus.ACCEPTED
                && payment != null && payment.authorizationExpiring();
    }

    /**
     * 注文の状態。
     */
    private static OrderStatus toStatus(com.ecsite.module.order.domain.order.OrderStatus status) {
        return OrderStatus.fromValue(status.name());
    }
}
