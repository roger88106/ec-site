package com.ecsite.module.payment.infrastructure.stripe;

import org.jspecify.annotations.Nullable;

/**
 * 署名を確認できない Stripe の通知の例外
 */
class InvalidStripeNotificationException extends RuntimeException {

    /**
     * 例外を作る。
     *
     * @param message 拒否の理由（ログに出す）
     * @param cause   原因
     */
    InvalidStripeNotificationException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
