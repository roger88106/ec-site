package com.ecsite.module.order.presentation;

import com.ecsite.api.store.model.Amounts;
import com.ecsite.api.store.model.AxisValue;
import com.ecsite.api.store.model.Cancellation;
import com.ecsite.api.store.model.CancellationKind;
import com.ecsite.api.store.model.CancellationRequest;
import com.ecsite.api.store.model.Carrier;
import com.ecsite.api.store.model.Checkout;
import com.ecsite.api.store.model.Order;
import com.ecsite.api.store.model.OrderActions;
import com.ecsite.api.store.model.OrderLine;
import com.ecsite.api.store.model.OrderStatus;
import com.ecsite.api.store.model.OrderSummary;
import com.ecsite.api.store.model.OrderSummaryPage;
import com.ecsite.api.store.model.PlaceOrderRequest;
import com.ecsite.api.store.model.QuoteItem;
import com.ecsite.api.store.model.Shipment;
import com.ecsite.api.store.model.ShipmentStatus;
import com.ecsite.api.store.model.ShippingAddress;
import com.ecsite.api.store.model.TaxAmount;
import com.ecsite.api.store.model.UnpurchasableReason;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.presentation.ApiDateTimes;
import com.ecsite.common.presentation.StorePrices;
import com.ecsite.common.security.AuthenticatedMember;
import com.ecsite.module.order.application.model.CheckoutView;
import com.ecsite.module.order.application.model.OrderDetailView;
import com.ecsite.module.order.application.model.OrderPayment;
import com.ecsite.module.order.application.model.PlaceOrderCommand;
import com.ecsite.module.order.application.port.OrderQueryRepository.OrderSummaryView;
import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.quote.Quote;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 店舗 API（注文手続き・注文）のレスポンスと、アプリケーション層のモデルの変換
 */
@Component
class StoreOrderMapper {

    /**
     * 注文手続きのレスポンスに変換する。
     *
     * @param view 注文手続きの表示
     * @return 注文手続きのレスポンス
     */
    Checkout toCheckout(CheckoutView view) {
        return new Checkout()
                .id(view.id())
                .items(toQuoteItems(view.quote()))
                .amounts(toAmounts(view.quote().amounts()))
                .expiresAt(ApiDateTimes.toOffsetDateTime(view.expiresAt()))
                .orderId(view.orderId());
    }

    /**
     * 注文のコマンドに変換する。
     *
     * @param checkoutId 注文手続き ID
     * @param request    注文のリクエスト
     * @param memberId   会員 ID
     * @param member     token の会員（連絡先のメールアドレス、確認済みか、生年月日、性別）
     * @return 注文のコマンド
     */
    PlaceOrderCommand toPlaceOrderCommand(UUID checkoutId, PlaceOrderRequest request, UUID memberId,
                                          AuthenticatedMember member) {
        var address = request.getShippingAddress();
        return new PlaceOrderCommand(
                checkoutId,
                memberId,
                member.email(),
                member.emailVerified(),
                address.getName(),
                address.getPostalCode(),
                address.getPrefectureCode(),
                address.getMunicipalityCode(),
                address.getAddress(),
                address.getPhone(),
                Money.of(request.getExpectedTotal()),
                member.birthdate(),
                member.gender());
    }

    /**
     * 注文のレスポンスに変換する。キャンセルの申請・キャンセル・出荷がなければ、その項目を返さない。
     * 支払の期限は、支払待ちのときだけ返す。出荷はキャンセルの後も返す（画面は注文の状態で出し分ける）。
     *
     * @param view 注文の詳細
     * @return 注文のレスポンス
     */
    Order toOrder(OrderDetailView view) {
        var payment = view.payment();
        var shipment = view.shipment();
        var response = new Order()
                .id(view.id())
                .orderNumber(view.orderNumber().value())
                .orderedAt(ApiDateTimes.toOffsetDateTime(view.orderedAt()))
                .status(toStatus(view.status()))
                .lines(view.lines().stream().map(StoreOrderMapper::toLine).toList())
                .amounts(toAmounts(view.amounts()))
                .email(view.email())
                .shippingAddress(toAddress(view.shippingAddress()))
                .actions(new OrderActions()
                        .requestCancellation(view.actions().cancellationRequestable())
                        .pay(view.actions().payable()));
        if (view.awaitingPayment()) {
            response.paymentDeadline(ApiDateTimes.toOffsetDateTime(payment.deadline()));
        }
        if (view.cancellationRequest() != null) {
            response.cancellationRequest(toCancellationRequest(view.cancellationRequest()));
        }
        if (view.cancellation() != null) {
            response.cancellation(new Cancellation()
                    .cancelledAt(ApiDateTimes.toOffsetDateTime(view.cancellation().cancelledAt()))
                    .kind(toKind(view.cancellation().kind())));
        }
        if (shipment != null) {
            response.shipment(new Shipment()
                    .carrier(toCarrier(shipment.carrier()))
                    .trackingNumber(shipment.trackingNumber())
                    .status(toShipmentStatus(shipment.status())));
        }
        return response;
    }

    /**
     * 見積もりの品目のレスポンスに変換する。
     *
     * @param quote 見積もり
     * @return 品目のレスポンス
     */
    List<QuoteItem> toQuoteItems(Quote quote) {
        return quote.items().stream().map(StoreOrderMapper::toQuoteItem).toList();
    }

    /**
     * 金額の内訳のレスポンスに変換する。
     *
     * @param amounts 金額の内訳
     * @return 金額の内訳のレスポンス
     */
    Amounts toAmounts(com.ecsite.module.order.domain.quote.Amounts amounts) {
        return new Amounts()
                .itemsTotal(amounts.itemsTotal().yen())
                .shippingFee(amounts.shippingFee().yen())
                .total(amounts.total().yen())
                .taxes(amounts.taxes().stream().map(StoreOrderMapper::toTaxAmount).toList());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 見積もりの品目 1 件。画像と購入できない理由は、null でも項目として返す。
     */
    private static QuoteItem toQuoteItem(com.ecsite.module.order.domain.quote.QuoteItem item) {
        var variant = item.variant();
        return new QuoteItem()
                .variantId(variant.variantId())
                .productId(variant.productId())
                .productName(variant.productName())
                .axisValues(variant.axisValues().stream().map(StoreOrderMapper::toAxisValue).toList())
                .imageUrl(variant.imageUrl())
                .unitPrice(StorePrices.of(item.unitPriceInclTax(), variant.priceExclTax()))
                .quantity(item.quantity())
                .subtotal(item.subtotal().yen())
                .purchasable(item.purchasable())
                .unpurchasableReason(item.purchasable()
                        ? null
                        : toReason(item.unpurchasableReason()));
    }

    /**
     * 注文明細 1 件（注文時点の写し）。
     */
    private static OrderLine toLine(com.ecsite.module.order.domain.order.vo.OrderLine line) {
        return new OrderLine()
                .productCode(line.productCode())
                .variantCode(line.variantCode())
                .productName(line.productName())
                .axisValues(line.axisValues().stream().map(StoreOrderMapper::toAxisValue).toList())
                .unitPrice(StorePrices.of(line.unitPriceInclTax(), line.unitPriceExclTax()))
                .ratePercent(line.ratePercent())
                .quantity(line.quantity())
                .subtotal(line.subtotal().yen());
    }

    /**
     * 1 つの税率の消費税額。
     */
    private static TaxAmount toTaxAmount(com.ecsite.module.order.domain.quote.TaxAmount tax) {
        return new TaxAmount()
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
                .result(toResult(request.result()));
    }

    /**
     * 軸の名前と値。
     */
    private static AxisValue toAxisValue(com.ecsite.module.order.domain.quote.AxisValue axis) {
        return new AxisValue()
                .name(axis.name())
                .value(axis.value());
    }

    /**
     * 自分の注文の一覧のレスポンスに変換する。
     *
     * @param page      注文の一覧
     * @param payments  注文 ID → 支払
     * @param shipments 注文 ID → 出荷
     * @return 注文の一覧のレスポンス
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
     * 自分の注文の一覧の 1 件。支払の期限は支払待ちのときだけ、配送状態は出荷したときだけ返す。
     */
    private static OrderSummary toSummary(OrderSummaryView view, @Nullable OrderPayment payment,
                                          @Nullable ShippingDirectory.Shipment shipment) {
        var summary = new OrderSummary()
                .id(view.id())
                .orderNumber(view.orderNumber())
                .orderedAt(ApiDateTimes.toOffsetDateTime(view.orderedAt()))
                .totalAmount(view.total().yen())
                .status(toStatus(view.status()));
        if (view.status() == com.ecsite.module.order.domain.order.OrderStatus.PENDING_PAYMENT && payment != null) {
            summary.paymentDeadline(ApiDateTimes.toOffsetDateTime(payment.deadline()));
        }
        if (shipment != null) {
            summary.shipmentStatus(toShipmentStatus(shipment.status()));
        }
        return summary;
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
     * キャンセルの種類。
     */
    private static CancellationKind toKind(com.ecsite.module.order.domain.order.CancellationKind kind) {
        return switch (kind) {
            case REQUEST_APPROVED -> CancellationKind.REQUEST_APPROVED;
            case OPERATOR -> CancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> CancellationKind.PAYMENT_EXPIRED;
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
     * 買えない理由。
     */
    private static UnpurchasableReason toReason(com.ecsite.module.order.domain.quote.UnpurchasableReason reason) {
        return switch (reason) {
            case NOT_SHOWN -> UnpurchasableReason.NOT_SHOWN;
            case SALES_SUSPENDED -> UnpurchasableReason.SALES_SUSPENDED;
            case OUT_OF_STOCK -> UnpurchasableReason.OUT_OF_STOCK;
            case INSUFFICIENT_STOCK -> UnpurchasableReason.INSUFFICIENT_STOCK;
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
}
