package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.CaptureReconcileView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PaymentSummaryView> findSummaries(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return dsl.select(PAYMENT_.ORDER_ID, PAYMENT_.STATUS, PAYMENT_.DEADLINE)
                .from(PAYMENT_)
                .where(PAYMENT_.ORDER_ID.in(orderIds))
                .fetch(r -> new PaymentSummaryView(r.get(PAYMENT_.ORDER_ID),
                        RestoreGuard.restore("payment", () -> PaymentStatus.valueOf(r.get(PAYMENT_.STATUS))),
                        r.get(PAYMENT_.DEADLINE)));
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public long countUnpaidAwaitingAuthorizationUntil(Instant latestAwaitingSince) {
        return dsl.fetchCount(PAYMENT_, PAYMENT_.STATUS.eq(PaymentStatus.UNPAID.name())
                .and(PAYMENT_.AWAITING_AUTHORIZATION_SINCE.le(latestAwaitingSince)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CaptureReconcileView> findCaptureRequestedUntil(Instant latestRequestedAt, int limit) {
        return dsl.select(PAYMENT_.ID, PAYMENT_.AUTHORIZATION_REF, PAYMENT_.CAPTURE_REQUESTED_AT)
                .from(PAYMENT_)
                // 部分索引の条件と同じ（与信済みで、売上確定を求めた時刻がある）
                .where(PAYMENT_.STATUS.eq(PaymentStatus.AUTHORIZED.name()))
                .and(PAYMENT_.CAPTURE_REQUESTED_AT.le(latestRequestedAt))
                .orderBy(PAYMENT_.CAPTURE_REQUESTED_AT, PAYMENT_.ID)
                .limit(limit)
                .fetch(r -> new CaptureReconcileView(r.get(PAYMENT_.ID),
                        // 与信済みの行には与信の参照がある（DB の値の破損は復元の失敗にする）
                        RestoreGuard.restore("payment", () -> Objects.requireNonNull(r.get(PAYMENT_.AUTHORIZATION_REF))),
                        r.get(PAYMENT_.CAPTURE_REQUESTED_AT)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PastDeadlinePaymentView> findUnpaidPastDeadline(Instant now, int limit) {
        return dsl.select(PAYMENT_.ID, PAYMENT_.PAGE_ID, PAYMENT_.PAGE_URL)
                .from(PAYMENT_)
                .where(PAYMENT_.STATUS.eq(PaymentStatus.UNPAID.name()))
                .and(PAYMENT_.DEADLINE.le(now))
                // 与信の反映を待つ支払がたまっても、それより新しい支払を処理できるように除く
                .and(PAYMENT_.AWAITING_AUTHORIZATION_SINCE.isNull())
                .orderBy(PAYMENT_.DEADLINE, PAYMENT_.ID)
                .limit(limit)
                .fetch(r -> new PastDeadlinePaymentView(r.get(PAYMENT_.ID),
                        // 支払画面の ID と URL は、DB の制約で両方あるか両方ないか。
                        // 制約が破られた 1 行で検索の全体が失敗しないように、両方あるときだけ作る
                        r.get(PAYMENT_.PAGE_ID) == null || r.get(PAYMENT_.PAGE_URL) == null
                                ? null
                                : new PaymentPage(r.get(PAYMENT_.PAGE_ID), r.get(PAYMENT_.PAGE_URL))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AwaitingAuthorizationPaymentView> findUnpaidAwaitingAuthorization(int limit) {
        return dsl.select(PAYMENT_.ID, PAYMENT_.PAGE_ID)
                .from(PAYMENT_)
                .where(PAYMENT_.STATUS.eq(PaymentStatus.UNPAID.name()))
                .and(PAYMENT_.AWAITING_AUTHORIZATION_SINCE.isNotNull())
                .orderBy(PAYMENT_.AWAITING_AUTHORIZATION_SINCE, PAYMENT_.ID)
                .limit(limit)
                .fetch(r -> new AwaitingAuthorizationPaymentView(r.get(PAYMENT_.ID), r.get(PAYMENT_.PAGE_ID)));
    }
}
