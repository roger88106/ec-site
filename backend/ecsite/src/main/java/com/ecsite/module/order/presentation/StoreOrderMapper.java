package com.ecsite.module.order.presentation;

import com.ecsite.api.store.model.Amounts;
import com.ecsite.api.store.model.AxisValue;
import com.ecsite.api.store.model.Cancellation;
import com.ecsite.api.store.model.CancellationKind;
import com.ecsite.api.store.model.CancellationRequest;
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
import com.ecsite.module.order.application.model.CheckoutView;
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
     * @param memberId      会員 ID
     * @param email         連絡先のメールアドレス（会員の token のもの）
     * @param emailVerified メールアドレスが確認済みか（token のもの）
     * @return 注文のコマンド
     */
    PlaceOrderCommand toPlaceOrderCommand(UUID checkoutId, PlaceOrderRequest request, UUID memberId, String email,
                                          boolean emailVerified) {
        var address = request.getShippingAddress();
        return new PlaceOrderCommand(
                checkoutId,
                memberId,
                email,
                emailVerified,
                address.getName(),
                address.getPostalCode(),
                address.getPrefecture(),
                address.getAddress(),
                address.getPhone(),
                Money.of(request.getExpectedTotal()));
    }

    /**
     * 注文のレスポンスに変換する。キャンセルの申請・キャンセル・出荷がなければ、その項目を返さない。
     * 支払の期限は、支払待ちのときだけ返す。出荷はキャンセルの後も返す（画面は注文の状態で出し分ける）。
     *
     * @param order    注文
     * @param payment  注文の支払。決済を入れる前の注文は null
     * @param shipment 注文の出荷。出荷していなければ null
     * @return 注文のレスポンス
     */
    Order toOrder(com.ecsite.module.order.domain.order.Order order, @Nullable OrderPayment payment,
                  @Nullable ShippingDirectory.Shipment shipment) {
        var awaitingPayment = order.isAwaitingPayment() && payment != null;
        var response = new Order()
                .id(order.id())
                .orderNumber(order.orderNumber().value())
                .orderedAt(ApiDateTimes.toOffsetDateTime(order.orderedAt()))
                .status(OrderStatus.fromValue(order.status().name()))
                .lines(order.lines().stream().map(StoreOrderMapper::toLine).toList())
                .amounts(toAmounts(order.amounts()))
                .email(order.email())
                .shippingAddress(toAddress(order.shippingAddress()))
                .actions(new OrderActions(order.isCancellationRequestable(), awaitingPayment && payment.payable()));
        if (awaitingPayment) {
            response.paymentDeadline(ApiDateTimes.toOffsetDateTime(payment.deadline()));
        }
        if (order.cancellationRequest() != null) {
            response.cancellationRequest(toCancellationRequest(order.cancellationRequest()));
        }
        if (order.cancellation() != null) {
            response.cancellation(new Cancellation()
                    .cancelledAt(ApiDateTimes.toOffsetDateTime(order.cancellation().cancelledAt()))
                    .kind(CancellationKind.fromValue(order.cancellation().kind().name())));
        }
        if (shipment != null) {
            response.shipment(new Shipment()
                    .carrier(com.ecsite.api.store.model.Carrier.fromValue(shipment.carrier().name()))
                    .trackingNumber(shipment.trackingNumber())
                    .status(ShipmentStatus.fromValue(shipment.status().name())));
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
                        : UnpurchasableReason.fromValue(item.unpurchasableReason().name()));
    }

    /**
     * 注文明細 1 件（注文時点の写し）。
     */
    private static OrderLine toLine(com.ecsite.module.order.domain.order.OrderLine line) {
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
                .result(CancellationRequest.ResultEnum.fromValue(request.result().name()));
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
     * 日時を、オフセット付きの日時（UTC）にする。
     */
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
                .totalAmount(view.total())
                .status(OrderStatus.fromValue(view.status().name()));
        if (view.status() == com.ecsite.module.order.domain.order.OrderStatus.PENDING_PAYMENT && payment != null) {
            summary.paymentDeadline(ApiDateTimes.toOffsetDateTime(payment.deadline()));
        }
        if (shipment != null) {
            summary.shipmentStatus(ShipmentStatus.fromValue(shipment.status().name()));
        }
        return summary;
    }
}
