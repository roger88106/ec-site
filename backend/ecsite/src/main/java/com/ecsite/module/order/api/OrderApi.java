package com.ecsite.module.order.api;

import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.module.order.api.model.Amounts;
import com.ecsite.module.order.api.model.AxisValue;
import com.ecsite.module.order.api.model.OrderSnapshot;
import com.ecsite.module.order.api.model.TaxAmount;
import com.ecsite.module.order.application.OrderReportQueryService;
import com.ecsite.module.order.application.model.view.OrderReportView;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.Gender;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の API
 */
@Component
@RequiredArgsConstructor
public class OrderApi {

    /**
     * 注文の参照
     */
    private final OrderReportQueryService orderReportQueryService;

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 注文の今の値を返す。
     *
     * @param orderId 注文 ID
     * @return 注文の今の値。注文がなければ空
     */
    public Optional<OrderSnapshot> find(@NonNull UUID orderId) {
        return orderReportQueryService.find(orderId).map(OrderApi::toSnapshot);
    }

    /**
     * 注文のメールアドレスを返す。
     *
     * <p>注文時に写したメールアドレスで、注文の今の値の計算（送料の消費税など）を使わないので、注文リポジトリから直接読む。
     *
     * @param orderId 注文 ID
     * @return 注文のメールアドレス。注文がなければ空
     */
    public Optional<String> findEmail(@NonNull UUID orderId) {
        return orderRepository.findById(orderId).map(Order::email);
    }

    /**
     * 注文を公開の型にする。
     */
    private static OrderSnapshot toSnapshot(OrderReportView view) {
        var cancellation = view.cancellation();
        var buyer = view.buyerProfile();
        return new OrderSnapshot(
                view.id(),
                view.orderNumber().value(),
                view.memberId(),
                view.orderedAt(),
                view.status().toEventStatus(),
                cancellation == null ? null : new OrderSnapshot.Cancellation(
                        cancellation.cancelledAt(), cancellation.kind().toEventKind(), cancellation.restocked()),
                view.shippingAddress().postalCode(),
                view.shippingAddress().area().prefectureCode(),
                buyer.age(),
                buyer.gender() == null ? null : toGender(buyer.gender()),
                toAmounts(view.amounts()),
                view.shippingTax(),
                view.lines().stream().map(OrderApi::toLine).toList());
    }

    /**
     * 金額の内訳を公開の型にする。
     */
    private static Amounts toAmounts(com.ecsite.module.order.domain.quote.Amounts amounts) {
        return new Amounts(
                amounts.itemsTotal(),
                amounts.shippingFee(),
                amounts.total(),
                amounts.taxes().stream()
                        .map(tax -> new TaxAmount(tax.ratePercent(), tax.taxableAmount(), tax.taxAmount()))
                        .toList());
    }

    /**
     * 明細を公開の型にする。
     */
    private static OrderSnapshot.Line toLine(OrderLine line) {
        return new OrderSnapshot.Line(
                line.lineNo(),
                line.variantId(),
                line.productCode(),
                line.variantCode(),
                line.productName(),
                line.axisValues().stream().map(axis -> new AxisValue(axis.name(), axis.value())).toList(),
                line.quantity(),
                line.ratePercent(),
                line.unitPriceExclTax(),
                line.subtotal());
    }

    /**
     * 性別を公開の型にする。
     */
    static OrderSnapshot.Gender toGender(Gender gender) {
        return EnumConversion.byName(gender, OrderSnapshot.Gender.class);
    }
}
