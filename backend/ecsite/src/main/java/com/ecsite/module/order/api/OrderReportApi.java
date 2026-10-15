package com.ecsite.module.order.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.OrderReportQueryService;
import com.ecsite.module.order.domain.order.OrderLine;
import com.ecsite.module.order.domain.quote.TaxAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * レポートに公開する、レポート用の注文の写しの API（docs/backend/modules/order.md「他のモジュールへの公開」）。<br />
 * レポートは、注文の状態の変化のイベントを受けるたびに、ここで今の写しを読み直す。
 */
@Component
@RequiredArgsConstructor
public class OrderReportApi {

    /**
     * レポート用に注文を読むサービス
     */
    private final OrderReportQueryService orderReportQueryService;

    /**
     * 注文の今の写しを返す。
     *
     * @param orderId 注文 ID
     * @return 写し。注文がなければ空
     */
    public Optional<OrderReportSnapshot> find(UUID orderId) {
        return orderReportQueryService.find(orderId).map(OrderReportApi::toSnapshot);
    }

    /**
     * 注文を公開の型にする。
     */
    private static OrderReportSnapshot toSnapshot(OrderReportQueryService.Result result) {
        var order = result.order();
        var cancellation = order.cancellation();
        var buyer = order.buyerProfile();
        var amounts = order.amounts();
        return new OrderReportSnapshot(
                order.id(),
                order.orderNumber().value(),
                order.memberId(),
                order.orderedAt(),
                OrderReportSnapshot.Status.valueOf(order.status().name()),
                cancellation == null ? null : cancellation.cancelledAt(),
                cancellation == null ? null : OrderReportSnapshot.CancellationKind.valueOf(cancellation.kind().name()),
                cancellation == null ? null : cancellation.restocked(),
                order.shippingAddress().postalCode(),
                order.shippingAddress().prefecture(),
                buyer.age(),
                buyer.gender() == null ? null : OrderReportSnapshot.Gender.valueOf(buyer.gender().name()),
                amounts.total().yen(),
                amounts.shippingFee().yen(),
                amounts.taxes().stream().map(TaxAmount::taxAmount).reduce(Money.ZERO, Money::plus).yen(),
                result.shippingTax().yen(),
                order.lines().stream().map(OrderReportApi::toLine).toList());
    }

    /**
     * 明細を公開の型にする。税抜小計は税抜単価 × 数量。
     */
    private static OrderReportSnapshot.Line toLine(OrderLine line) {
        return new OrderReportSnapshot.Line(
                line.lineNo(),
                line.variantId(),
                line.productCode(),
                line.variantCode(),
                line.productName(),
                line.axisValues().stream()
                        .map(axis -> new OrderReportSnapshot.AxisValue(axis.name(), axis.value()))
                        .toList(),
                line.quantity(),
                line.ratePercent(),
                line.subtotal().yen(),
                line.unitPriceExclTax().yen() * line.quantity());
    }
}
