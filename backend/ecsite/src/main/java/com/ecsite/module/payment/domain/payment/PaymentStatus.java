package com.ecsite.module.payment.domain.payment;

/**
 * 支払の状態（docs/backend/modules/payment.md「状態遷移」）
 */
public enum PaymentStatus {

    /**
     * 未払い。与信がまだ成功していない
     */
    UNPAID,

    /**
     * 与信済み。総額の与信が成功し、売上は確定していない
     */
    AUTHORIZED,

    /**
     * 与信の取消待ち。注文がキャンセルになり、決済サービスでの取消が済んでいない
     */
    VOID_PENDING,

    /**
     * 与信の取消済み
     */
    VOIDED,

    /**
     * 支払なし。与信が成功しないまま、期限切れかキャンセルになった
     */
    NOT_PAID,
}
