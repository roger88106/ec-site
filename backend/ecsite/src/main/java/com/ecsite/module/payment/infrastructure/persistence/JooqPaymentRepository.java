package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.jooq.payment.tables.records.PaymentRecord;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
     * 新しい支払を保存する。同じ注文の支払がすでにあれば、例外にせず（トランザクションを壊さず）false を返す。
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
        record.setId(snapshot.id());
        record.setOrderId(snapshot.orderId());
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
        payment.pullEvents().forEach(eventPublisher::publishEvent);
        return true;
    }

    /**
     * 支払の変更を保存する。金額と期限は変わらないので、変わりうる項目だけを更新する。<br />
     * 登録されたイベントはすべて発行する（与信の成功、与信の取消の依頼、返金の依頼など）。
     *
     * @param payment 変更した支払
     */
    @Override
    public void update(Payment payment) {
        var snapshot = payment.toSnapshot();
        var record = dsl.newRecord(PAYMENT_);
        writeState(record, snapshot);
        record.setVersion(snapshot.version() + 1);
        record.setUpdatedAt(clock.instant());
        dsl.update(PAYMENT_).set(record).where(PAYMENT_.ID.eq(snapshot.id())).execute();
        payment.pullEvents().forEach(eventPublisher::publishEvent);
    }

    @Override
    public Optional<Payment> lockById(UUID id) {
        return lock(PAYMENT_.ID.eq(id));
    }

    @Override
    public Optional<Payment> lockByOrderId(UUID orderId) {
        return lock(PAYMENT_.ORDER_ID.eq(orderId));
    }

    @Override
    public Optional<Payment> lockByPageId(String pageId) {
        return lock(PAYMENT_.PAGE_ID.eq(pageId));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.ID.eq(id))
                .fetchOptional()
                .map(JooqPaymentRepository::restore);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.ORDER_ID.eq(orderId))
                .fetchOptional()
                .map(JooqPaymentRepository::restore);
    }

    @Override
    public List<Payment> findByOrderIds(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.ORDER_ID.in(orderIds))
                .fetch(JooqPaymentRepository::restore);
    }

    @Override
    public List<Payment> findUnpaidPastDeadline(Instant now, int limit) {
        return dsl.selectFrom(PAYMENT_)
                .where(PAYMENT_.STATUS.eq(PaymentStatus.UNPAID.name()))
                .and(PAYMENT_.DEADLINE.le(now))
                .orderBy(PAYMENT_.DEADLINE)
                .limit(limit)
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
     * 変わりうる項目（状態、支払の画面、与信の参照、与信の時刻）をレコードに書く。
     */
    private static void writeState(PaymentRecord record, Payment.Snapshot snapshot) {
        record.setStatus(snapshot.status().name());
        var page = snapshot.page();
        record.setPageId(page == null ? null : page.id());
        record.setPageUrl(page == null ? null : page.url());
        record.setAuthorizationRef(snapshot.authorizationRef());
        record.setAuthorizedAt(snapshot.authorizedAt());
    }

    /**
     * レコードから支払を再構築する。
     */
    private static Payment restore(PaymentRecord record) {
        var page = record.getPageId() == null ? null : new PaymentPage(record.getPageId(), record.getPageUrl());
        return Payment.restore(new Payment.Snapshot(
                record.getId(),
                record.getOrderId(),
                Money.of(record.getAmount()),
                record.getDeadline(),
                PaymentStatus.valueOf(record.getStatus()),
                page,
                record.getAuthorizationRef(),
                record.getAuthorizedAt(),
                record.getVersion()));
    }
}
