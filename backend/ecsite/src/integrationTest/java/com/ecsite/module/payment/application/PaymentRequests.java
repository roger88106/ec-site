package com.ecsite.module.payment.application;

import com.ecsite.AsyncEvents;
import com.ecsite.module.payment.domain.payment.RefundRequested;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import org.jooq.DSLContext;

import java.util.UUID;
import java.util.function.Predicate;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;

/**
 * 支払への依頼のイベントを選ぶ補助
 *
 * <p>注文の支払への依頼（返金、与信の取消）のイベントを選ぶ、{@link AsyncEvents} の条件を作る。
 */
final class PaymentRequests {

    /**
     * インスタンスを作らない。
     */
    private PaymentRequests() {
    }

    /**
     * 注文の支払の、返金と与信の取消の依頼のイベントを選ぶ条件を返す。
     *
     * @param dsl     DB
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    static Predicate<Object> of(DSLContext dsl, UUID orderId) {
        var paymentId = dsl.select(PAYMENT_.ID).from(PAYMENT_).where(PAYMENT_.ORDER_ID.eq(orderId)).fetchOne(PAYMENT_.ID);
        return event -> (event instanceof RefundRequested refund && refund.paymentId().equals(paymentId))
                || (event instanceof VoidRequested requested && requested.paymentId().equals(paymentId));
    }
}
