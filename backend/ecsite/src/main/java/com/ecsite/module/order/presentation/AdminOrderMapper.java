package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.model.Cancellation;
import com.ecsite.api.admin.model.CancellationKind;
import com.ecsite.api.admin.model.CancellationRequest;
import com.ecsite.api.admin.model.Carrier;
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
import com.ecsite.module.order.application.model.OrderDetailView;
import com.ecsite.module.order.application.model.OrderPayment;
import com.ecsite.module.order.application.port.OrderQueryRepository.OrderSummaryView;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.ShippingDirectory;
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
     * @param view 注文の詳細
     * @return 注文の詳細のレスポンス
     */
    OrderDetail toOrderDetail(OrderDetailView view) {
        var actions = view.actions();
        var detail = new OrderDetail()
                .id(view.id())
                .orderNumber(view.orderNumber().value())
                .orderedAt(ApiDateTimes.toOffsetDateTime(view.orderedAt()))
                .status(toStatus(view.status()))
                .lines(view.lines().stream().map(AdminOrderMapper::toLine).toList())
                .amounts(toAmounts(view.amounts()))
                .email(view.email())
                .shippingAddress(toAddress(view.shippingAddress()))
                .actions(new OrderActions()
                        .approveCancellation(actions.cancellationPending())
                        .rejectCancellation(actions.cancellationPending())
                        .cancel(actions.cancellable())
                        .ship(actions.shippable())
                        .cancelAsksRestock(actions.cancelAsksRestock()));
        if (view.cancellationRequest() != null) {
            detail.cancellationRequest(toCancellationRequest(view.cancellationRequest()));
        }
        if (view.cancellation() != null) {
            detail.cancellation(toCancellation(view.cancellation()));
        }
        var payment = view.payment();
        var shipment = view.shipment();
        if (payment != null) {
            detail.payment(new Payment()
                    .status(toPaymentStatus(payment.status()))
                    .deadline(ApiDateTimes.toOffsetDateTime(payment.deadline()))
                    .authorizationDeadline(payment.authorizationDeadline() == null ? null
                            : ApiDateTimes.toOffsetDateTime(payment.authorizationDeadline()))
                    .authorizationExpiring(isAuthorizationExpiring(view.status(), payment)));
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
                .carrier(toCarrier(shipment.carrier()))
                .trackingNumber(shipment.trackingNumber())
                .status(toShipmentStatus(shipment.status()))
                .shippedAt(ApiDateTimes.toOffsetDateTime(shipment.shippedAt()));
    }

    /**
     * 絞り込みの注文の状態を、ドメインの型にする。
     *
     * @param status 注文の状態
     * @return ドメインの注文の状態
     */
    com.ecsite.module.order.domain.order.OrderStatus toDomainStatus(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> com.ecsite.module.order.domain.order.OrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> com.ecsite.module.order.domain.order.OrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> com.ecsite.module.order.domain.order.OrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> com.ecsite.module.order.domain.order.OrderStatus.CANCELLED;
            case SHIPPED -> com.ecsite.module.order.domain.order.OrderStatus.SHIPPED;
        };
    }

    /**
     * 出荷の登録の配送業者を、ドメインの型にする。
     *
     * @param carrier 配送業者
     * @return ドメインの配送業者
     */
    com.ecsite.module.order.domain.order.Carrier toDomainCarrier(Carrier carrier) {
        return switch (carrier) {
            case YAMATO -> com.ecsite.module.order.domain.order.Carrier.YAMATO;
            case SAGAWA -> com.ecsite.module.order.domain.order.Carrier.SAGAWA;
            case JAPAN_POST -> com.ecsite.module.order.domain.order.Carrier.JAPAN_POST;
        };
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
                .total(view.total().yen())
                .status(toStatus(view.status()))
                .authorizationExpiring(isAuthorizationExpiring(view.status(), payment));
        if (payment != null) {
            summary.paymentStatus(toPaymentStatus(payment.status()));
        }
        if (shipment != null) {
            summary.shipmentStatus(toShipmentStatus(shipment.status()));
        }
        return summary;
    }

    /**
     * 注文明細 1 件（注文時点の写し）。
     */
    private static OrderLine toLine(com.ecsite.module.order.domain.order.vo.OrderLine line) {
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
    private static ShippingAddress toAddress(com.ecsite.module.order.domain.order.vo.ShippingAddress address) {
        return new ShippingAddress()
                .name(address.name())
                .postalCode(address.postalCode())
                .prefectureCode(address.area().prefectureCode())
                .prefectureName(address.area().prefectureName())
                .municipalityCode(address.area().municipalityCode())
                .municipalityName(address.area().municipalityName())
                .address(address.address())
                .phone(address.phone());
    }

    /**
     * キャンセルの申請。
     */
    private static CancellationRequest toCancellationRequest(
            com.ecsite.module.order.domain.order.vo.CancellationRequest request) {
        return new CancellationRequest()
                .requestedAt(ApiDateTimes.toOffsetDateTime(request.requestedAt()))
                .reason(request.reason())
                .result(toResult(request.result()))
                .decidedAt(ApiDateTimes.toOffsetDateTime(request.decidedAt()))
                .decidedBy(request.decidedBy() == null ? null : request.decidedBy().name());
    }

    /**
     * キャンセル。
     */
    private static Cancellation toCancellation(com.ecsite.module.order.domain.order.vo.Cancellation cancellation) {
        return new Cancellation()
                .cancelledAt(ApiDateTimes.toOffsetDateTime(cancellation.cancelledAt()))
                .kind(toKind(cancellation.kind()))
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
        return switch (status) {
            case PENDING_PAYMENT -> OrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> OrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> OrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> OrderStatus.CANCELLED;
            case SHIPPED -> OrderStatus.SHIPPED;
        };
    }

    /**
     * 支払の状態。
     */
    private static PaymentStatus toPaymentStatus(PaymentDirectory.Status status) {
        return switch (status) {
            case UNPAID -> PaymentStatus.UNPAID;
            case AUTHORIZED -> PaymentStatus.AUTHORIZED;
            case VOID_PENDING -> PaymentStatus.VOID_PENDING;
            case VOIDED -> PaymentStatus.VOIDED;
            case NOT_PAID -> PaymentStatus.NOT_PAID;
            case CAPTURED -> PaymentStatus.CAPTURED;
            case AUTHORIZATION_EXPIRED -> PaymentStatus.AUTHORIZATION_EXPIRED;
            case REFUND_PENDING -> PaymentStatus.REFUND_PENDING;
            case REFUNDED -> PaymentStatus.REFUNDED;
        };
    }

    /**
     * 配送業者。
     */
    private static Carrier toCarrier(com.ecsite.module.order.domain.order.Carrier carrier) {
        return switch (carrier) {
            case YAMATO -> Carrier.YAMATO;
            case SAGAWA -> Carrier.SAGAWA;
            case JAPAN_POST -> Carrier.JAPAN_POST;
        };
    }

    /**
     * 配送状態。
     */
    private static ShipmentStatus toShipmentStatus(ShippingDirectory.Status status) {
        return switch (status) {
            case SHIPPED -> ShipmentStatus.SHIPPED;
            case IN_TRANSIT -> ShipmentStatus.IN_TRANSIT;
            case DELIVERED -> ShipmentStatus.DELIVERED;
            case RETURNED -> ShipmentStatus.RETURNED;
        };
    }

    /**
     * キャンセルの申請の結果。
     */
    private static CancellationRequest.ResultEnum toResult(
            com.ecsite.module.order.domain.order.vo.CancellationRequest.Result result) {
        return switch (result) {
            case PENDING -> CancellationRequest.ResultEnum.PENDING;
            case APPROVED -> CancellationRequest.ResultEnum.APPROVED;
            case REJECTED -> CancellationRequest.ResultEnum.REJECTED;
        };
    }

    /**
     * キャンセルの種類。
     */
    private static CancellationKind toKind(com.ecsite.module.order.domain.order.CancellationKind kind) {
        return switch (kind) {
            case REQUEST_APPROVED -> CancellationKind.REQUEST_APPROVED;
            case OPERATOR -> CancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> CancellationKind.PAYMENT_EXPIRED;
        };
    }
}
