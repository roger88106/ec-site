package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;

/**
 * 支払のクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqPaymentQueryRepository implements PaymentQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    @Override
    public List<PaymentSummaryView> findSummaries(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return dsl.select(PAYMENT_.ORDER_ID, PAYMENT_.STATUS, PAYMENT_.DEADLINE)
                .from(PAYMENT_)
                .where(PAYMENT_.ORDER_ID.in(orderIds))
                .fetch(row -> new PaymentSummaryView(row.value1(), PaymentStatus.valueOf(row.value2()), row.value3()));
    }

    @Override
    public List<UUID> findOrderIdsAuthorizedUntil(Instant latestAuthorizedAt) {
        // 与信期限を気にする状態の名前（部分インデックスの条件と同じ）
        var watched = Arrays.stream(PaymentStatus.values())
                .filter(PaymentStatus::watchesAuthorizationDeadline)
                .map(PaymentStatus::name)
                .toList();
        return dsl.select(PAYMENT_.ORDER_ID)
                .from(PAYMENT_)
                .where(PAYMENT_.STATUS.in(watched))
                .and(PAYMENT_.AUTHORIZED_AT.le(latestAuthorizedAt))
                .orderBy(PAYMENT_.AUTHORIZED_AT)
                .fetch(PAYMENT_.ORDER_ID);
    }
}
