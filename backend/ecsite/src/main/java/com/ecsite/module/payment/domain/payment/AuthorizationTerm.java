package com.ecsite.module.payment.domain.payment;

import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;

/**
 * 与信の有効期間と、期限が近いとみなす長さ（docs/backend/modules/payment.md「与信期限」）。
 *
 * @param validity 与信の有効期間（与信の時刻からの長さ）
 * @param warning  与信期限の何前から「期限が近い」とするか
 */
public record AuthorizationTerm(@NonNull Duration validity, @NonNull Duration warning) {

    /**
     * 与信期限を返す。
     *
     * @param authorizedAt 与信の時刻
     * @return 与信期限
     */
    public Instant deadlineOf(@NonNull Instant authorizedAt) {
        throw new UnsupportedOperationException();
    }

    /**
     * 与信期限が近いかを返す。期限を過ぎていても近いとする。
     *
     * @param authorizedAt 与信の時刻
     * @param now          現在時刻
     * @return 近ければ true
     */
    public boolean isExpiring(@NonNull Instant authorizedAt, @NonNull Instant now) {
        throw new UnsupportedOperationException();
    }

    /**
     * 与信期限が近い与信の、与信の時刻の上限を返す。この時刻以前に与信を受けたものが近い。
     *
     * @param now 現在時刻
     * @return 与信の時刻の上限
     */
    public Instant latestExpiringAuthorizedAt(@NonNull Instant now) {
        throw new UnsupportedOperationException();
    }
}
