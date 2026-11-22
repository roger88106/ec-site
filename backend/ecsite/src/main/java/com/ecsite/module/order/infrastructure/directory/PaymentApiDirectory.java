package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.model.PaymentAmountRange;
import com.ecsite.module.order.application.model.PaymentAuthorization;
import com.ecsite.module.order.application.model.PaymentSnapshot;
import com.ecsite.module.order.application.model.view.OrderPayment;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.AuthorizationDeadline;
import com.ecsite.module.payment.api.model.PaymentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 決済の腐敗防止層
 *
 * <p>決済モジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class PaymentApiDirectory implements PaymentDirectory {

    /**
     * 決済の API
     */
    private final PaymentApi paymentApi;

    /**
     * {@inheritDoc}
     */
    @Override
    public void create(OrderId orderId, Money amount, Instant orderedAt) {
        paymentApi.create(orderId, amount, orderedAt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> pageUrl(OrderId orderId) {
        return paymentApi.pageUrl(orderId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void capture(OrderId orderId) {
        paymentApi.capture(orderId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel(OrderId orderId) {
        paymentApi.cancel(orderId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<OrderId, PaymentSnapshot> find(Collection<OrderId> orderIds) {
        var payable = paymentApi.findPayableOrderIds(orderIds);
        return paymentApi.find(orderIds).stream().collect(Collectors.toMap(PaymentSummary::orderId,
                summary -> new PaymentSnapshot(toStatus(summary.status()), summary.deadline(),
                        payable.contains(summary.orderId()))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<OrderId, PaymentAuthorization> findAuthorizations(Collection<OrderId> orderIds) {
        return paymentApi.findAuthorizationDeadlines(orderIds).stream()
                .collect(Collectors.toMap(AuthorizationDeadline::orderId,
                        deadline -> new PaymentAuthorization(deadline.deadline(), deadline.expiring())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OrderId> findExpiringOrderIds() {
        return paymentApi.findExpiringOrderIds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PaymentAmountRange amountRange() {
        var range = paymentApi.amountRange();
        return new PaymentAmountRange(range.min(), range.max());
    }

    /**
     * 決済モジュールの支払の状態を、注文の型にする。
     */
    static OrderPayment.Status toStatus(PaymentSummary.Status status) {
        return EnumConversion.byName(status, OrderPayment.Status.class);
    }
}
