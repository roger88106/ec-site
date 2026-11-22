package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.DomainEvents;
import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.value.Money;
import com.ecsite.jooq.payment.tables.records.PaymentRecord;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentId;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;

/**
 * 支払のリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqPaymentRepository implements PaymentRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 時計（作成・更新日時）
     */
    private final Clock clock;

    /**
     * 新しい支払を保存する。
     *
     * <p>同じ注文の支払がすでにあれば、例外にせず（トランザクションを壊さず）false を返す。
     *
     * @param payment 支払
     * @return 保存したら true
     */
    @Override
    public boolean insertIfAbsent(Payment payment) {
        var snapshot = payment.toSnapshot();
        var now = clock.instant();
        var record = dsl.newRecord(PAYMENT_);
        writeState(record, snapshot);
        record.setId(snapshot.id().value());
        record.setOrderId(snapshot.orderId().value());
        record.setAmount(snapshot.amount().yen());
        record.setDeadline(snapshot.deadline());
        record.setVersion(snapshot.version());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        int inserted = dsl.insertInto(PAYMENT_).set(record)
                .onConflict(PAYMENT_.ORDER_ID)
                .doNothing()
                .execute();
        if (inserted == 0) {
            return false;
        }
        DomainEvents.publish(eventPublisher, payment);
        return true;
    }

    /**
     * 支払の変更を保存し、登録されたイベント（与信の成功、与信の取消の依頼、返金の依頼など）を発行する。
     *
     * <p>金額と期限は変わらないので、バージョンが読んだときと同じ場合に、変わりうる項目だけを更新する。
     * 行ロックして読んだ支払を保存するので競合は起きないが、ロックを取らずに保存したときの安全網にする。
     *
     * @param payment 変更した支払
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Override
    public void update(Payment payment) {
        var snapshot = payment.toSnapshot();
        var record = dsl.newRecord(PAYMENT_);
        writeState(record, snapshot);
        record.setVersion(snapshot.version() + 1);
        record.setUpdatedAt(clock.instant());
        int updated = dsl.update(PAYMENT_).set(record)
                .where(PAYMENT_.ID.eq(snapshot.id().value()))
                .and(PAYMENT_.VERSION.eq(snapshot.version()))
                .execute();
        if (updated == 0) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        DomainEvents.publish(eventPublisher, payment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Payment> lockById(PaymentId id) {
        return lock(PAYMENT_.ID.eq(id.value()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Payment> lockByOrderId(OrderId orderId) {
        return lock(PAYMENT_.ORDER_ID.eq(orderId.value()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Payment> lockByPageId(String pageId) {
        return lock(PAYMENT_.PAGE_ID.eq(pageId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Payment> findById(PaymentId id) {
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.ID.eq(id.value()))
                .fetchOptional()
                .map(JooqPaymentRepository::restore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Payment> findByOrderId(OrderId orderId) {
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.ORDER_ID.eq(orderId.value()))
                .fetchOptional()
                .map(JooqPaymentRepository::restore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Payment> findByOrderIds(Collection<OrderId> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.ORDER_ID.in(orderIds.stream().map(OrderId::value).toList()))
                .fetch(JooqPaymentRepository::restore);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 条件に合う支払を行ロックして取得する。
     */
    private Optional<Payment> lock(Condition condition) {
        return dsl.selectFrom(PAYMENT_)
                .where(condition)
                .forUpdate()
                .fetchOptional()
                .map(JooqPaymentRepository::restore);
    }

    /**
     * 変わりうる項目（状態、支払画面、与信の参照、与信の時刻、与信の反映を待ち始めた時刻、売上確定を求めた時刻）をレコードに書く。
     */
    private static void writeState(PaymentRecord record, Payment.Snapshot snapshot) {
        record.setStatus(snapshot.status().name());
        var page = snapshot.page();
        record.setPageId(page == null ? null : page.id());
        record.setPageUrl(page == null ? null : page.url());
        record.setAuthorizationRef(snapshot.authorizationRef());
        record.setAuthorizedAt(snapshot.authorizedAt());
        record.setAwaitingAuthorizationSince(snapshot.awaitingAuthorizationSince());
        record.setCaptureRequestedAt(snapshot.captureRequestedAt());
    }

    /**
     * レコードから支払を再構築する。
     *
     * <p>列挙にない状態、URL のない支払画面、状態と項目が整合しない行は、復元の失敗にする。
     */
    private static Payment restore(PaymentRecord record) {
        return RestoreGuard.restore("payment", () -> {
            var page = record.getPageId() == null ? null : new PaymentPage(record.getPageId(), record.getPageUrl());
            return Payment.restore(new Payment.Snapshot(
                    new PaymentId(record.getId()),
                    new OrderId(record.getOrderId()),
                    Money.of(record.getAmount()),
                    record.getDeadline(),
                    PaymentStatus.valueOf(record.getStatus()),
                    page,
                    record.getAuthorizationRef(),
                    record.getAuthorizedAt(),
                    record.getAwaitingAuthorizationSince(),
                    record.getCaptureRequestedAt(),
                    record.getVersion()));
        });
    }
}
