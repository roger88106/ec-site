package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.payment.api.AuthorizationDeadline;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
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
 * 決済の腐敗防止層の実装。決済モジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class PaymentApiDirectory implements PaymentDirectory {

    /**
     * 決済の API
     */
    private final PaymentApi paymentApi;

    @Override
    public void create(UUID orderId, Money amount, Instant orderedAt) {
        paymentApi.create(orderId, amount, orderedAt);
    }

    @Override
    public Optional<String> pageUrl(UUID orderId) {
        return paymentApi.pageUrl(orderId);
    }

    @Override
    public void capture(UUID orderId) {
        paymentApi.capture(orderId);
    }

    @Override
    public void cancel(UUID orderId) {
        paymentApi.cancel(orderId);
    }

    @Override
    public Map<UUID, Payment> find(Collection<UUID> orderIds) {
        var payable = paymentApi.findPayableOrderIds(orderIds);
        return paymentApi.find(orderIds).stream().collect(Collectors.toMap(PaymentSummary::orderId,
                summary -> new Payment(toStatus(summary.status()), summary.deadline(),
                        payable.contains(summary.orderId()))));
    }

    @Override
    public Map<UUID, Authorization> findAuthorizations(Collection<UUID> orderIds) {
        return paymentApi.findAuthorizationDeadlines(orderIds).stream().collect(Collectors.toMap(AuthorizationDeadline::orderId,
                deadline -> new Authorization(deadline.deadline(), deadline.expiring())));
    }

    @Override
    public List<UUID> findExpiringOrderIds() {
        return paymentApi.findExpiringOrderIds();
    }

    /**
     * 決済モジュールの支払の状態を、注文の型にする。
     */
    private static Status toStatus(PaymentSummary.Status status) {
        return switch (status) {
            case UNPAID -> Status.UNPAID;
            case AUTHORIZED -> Status.AUTHORIZED;
            case VOID_PENDING -> Status.VOID_PENDING;
            case VOIDED -> Status.VOIDED;
            case NOT_PAID -> Status.NOT_PAID;
            case CAPTURED -> Status.CAPTURED;
            case AUTHORIZATION_EXPIRED -> Status.AUTHORIZATION_EXPIRED;
            case REFUND_PENDING -> Status.REFUND_PENDING;
            case REFUNDED -> Status.REFUNDED;
        };
    }
}
