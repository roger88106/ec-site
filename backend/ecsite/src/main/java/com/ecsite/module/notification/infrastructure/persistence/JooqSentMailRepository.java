package com.ecsite.module.notification.infrastructure.persistence;

import com.ecsite.module.notification.application.port.SentMailRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.notification.Tables.SENT_MAIL;

/**
 * 送ったメールの記録のリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqSentMailRepository implements SentMailRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 送ったメールを記録する。
     *
     * <p>注文 ID と種類の一意制約で、重ねて届いたイベントでは挿入しない（同時なら先のコミットを待つ）。
     *
     * @param orderId   注文 ID
     * @param kind      メールの種類
     * @param recipient 宛先
     * @param sentAt    送った日時
     * @return 記録したら true。すでに記録があれば false
     */
    @Override
    public boolean recordIfAbsent(UUID orderId, Kind kind, String recipient, Instant sentAt) {
        return dsl.insertInto(SENT_MAIL)
                .set(SENT_MAIL.ID, UUID.randomUUID())
                .set(SENT_MAIL.ORDER_ID, orderId)
                .set(SENT_MAIL.KIND, kind.name())
                .set(SENT_MAIL.RECIPIENT, recipient)
                .set(SENT_MAIL.SENT_AT, sentAt)
                .onConflict(SENT_MAIL.ORDER_ID, SENT_MAIL.KIND)
                .doNothing()
                .execute() == 1;
    }
}
