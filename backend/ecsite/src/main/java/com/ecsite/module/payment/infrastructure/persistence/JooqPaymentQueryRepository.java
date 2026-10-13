package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

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
}
