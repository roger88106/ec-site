package com.ecsite.module.payment.domain.payment;

/**
 * 支払の状態
 */
public enum PaymentStatus {

    /**
     * 未払い
     *
     * <p>与信がまだ成功していない。
     */
    UNPAID,

    /**
     * 与信済み
     *
     * <p>総額の与信が成功し、売上は確定していない。
     */
    AUTHORIZED,

    /**
     * 与信の取消待ち
     *
     * <p>注文がキャンセルになり、決済サービスでの取消が済んでいない。
     */
    VOID_PENDING,

    /**
     * 与信の取消済み
     */
    VOIDED,

    /**
     * 支払なし
     *
     * <p>与信が成功しないまま、期限切れかキャンセルになった。
     */
    NOT_PAID,

    /**
     * 売上確定済み
     *
     * <p>総額の売上が確定した。
     */
    CAPTURED,

    /**
     * 与信期限切れ
     *
     * <p>売上確定が、与信の期限切れで決済サービスに断られた。
     */
    AUTHORIZATION_EXPIRED,

    /**
     * 返金待ち
     *
     * <p>注文がキャンセルになり、決済サービスでの返金が済んでいない。
     */
    REFUND_PENDING,

    /**
     * 返金済み
     */
    REFUNDED;

    /**
     * 与信期限を気にする状態かを返す。
     *
     * <p>売上を確定する前の、与信済みと与信期限切れ。
     *
     * @return 与信期限を気にする状態なら true
     */
    public boolean watchesAuthorizationDeadline() {
        return this == AUTHORIZED || this == AUTHORIZATION_EXPIRED;
    }
}
