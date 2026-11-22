package com.ecsite.module.payment.application;

import com.ecsite.module.payment.domain.payment.PageCloseRequested;
import com.ecsite.module.payment.domain.payment.PaymentId;
import com.ecsite.module.payment.domain.payment.RefundRequested;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import com.ecsite.support.async.AsyncEvents;
import org.jooq.DSLContext;

import java.util.UUID;
import java.util.function.Predicate;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;

/**
 * 支払への依頼のイベントを選ぶ補助
 *
 * <p>注文の支払への依頼（返金、与信の取消、支払画面を閉じる）のイベントを選ぶ、{@link AsyncEvents} の条件を作る。
 * 注文のテストなど、他のモジュールのテストからも使う。
 */
public final class PaymentRequests {

    /**
     * インスタンスを作らない。
     */
    private PaymentRequests() {
    }

    /**
     * 注文の支払の、返金、与信の取消、支払画面を閉じる依頼のイベントを選ぶ条件を返す。
     *
     * @param dsl     DB
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    public static Predicate<Object> of(DSLContext dsl, UUID orderId) {
        return refund(dsl, orderId).or(voidAuthorization(dsl, orderId)).or(pageClose(dsl, orderId));
    }

    /**
     * 注文の支払の、返金の依頼のイベントを選ぶ条件を返す。
     *
     * @param dsl     DB
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    public static Predicate<Object> refund(DSLContext dsl, UUID orderId) {
        var paymentId = paymentIdOf(dsl, orderId);
        return event -> event instanceof RefundRequested refund && refund.paymentId().equals(paymentId);
    }

    /**
     * 注文の支払の、与信の取消の依頼のイベントを選ぶ条件を返す。
     *
     * @param dsl     DB
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    public static Predicate<Object> voidAuthorization(DSLContext dsl, UUID orderId) {
        var paymentId = paymentIdOf(dsl, orderId);
        return event -> event instanceof VoidRequested requested && requested.paymentId().equals(paymentId);
    }

    /**
     * 注文の支払の、支払画面を閉じる依頼のイベントを選ぶ条件を返す。
     *
     * @param dsl     DB
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    public static Predicate<Object> pageClose(DSLContext dsl, UUID orderId) {
        var paymentId = paymentIdOf(dsl, orderId);
        return event -> event instanceof PageCloseRequested requested && requested.paymentId().equals(paymentId);
    }

    /**
     * 注文の支払の ID を DB から読む。
     *
     * @param dsl     DB
     * @param orderId 注文 ID
     * @return 支払 ID
     */
    public static PaymentId paymentIdOf(DSLContext dsl, UUID orderId) {
        return dsl.select(PAYMENT_.ID).from(PAYMENT_).where(PAYMENT_.ORDER_ID.eq(orderId)).fetchOne(record -> new PaymentId(record.get(PAYMENT_.ID)));
    }
}
