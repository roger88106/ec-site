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
import com.ecsite.module.payment.api.model.PaymentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    public void create(UUID orderId, Money amount, Instant orderedAt) {
        paymentApi.create(new OrderId(orderId), amount, orderedAt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> pageUrl(UUID orderId) {
        return paymentApi.pageUrl(new OrderId(orderId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void capture(UUID orderId) {
        paymentApi.capture(new OrderId(orderId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel(UUID orderId) {
        paymentApi.cancel(new OrderId(orderId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<UUID, PaymentSnapshot> find(Collection<UUID> orderIds) {
        var ids = orderIds.stream().map(OrderId::new).toList();
        var payable = paymentApi.findPayableOrderIds(ids);
        return paymentApi.find(ids).stream().collect(Collectors.toMap(summary -> summary.orderId().value(),
                summary -> new PaymentSnapshot(toStatus(summary.status()), summary.deadline(),
                        payable.contains(summary.orderId()))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<UUID, PaymentAuthorization> findAuthorizations(Collection<UUID> orderIds) {
        return paymentApi.findAuthorizationDeadlines(orderIds.stream().map(OrderId::new).toList()).stream()
                .collect(Collectors.toMap(deadline -> deadline.orderId().value(),
                        deadline -> new PaymentAuthorization(deadline.deadline(), deadline.expiring())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UUID> findExpiringOrderIds() {
        return paymentApi.findExpiringOrderIds().stream().map(OrderId::value).toList();
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
