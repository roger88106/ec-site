package com.ecsite.module.payment.domain.payment;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Duration;
import java.time.Instant;

/**
 * 与信の有効期間と、期限が近いとみなす長さ
 *
 * @param validity 与信の有効期間（与信の時刻からの長さ）
 * @param warning  与信期限のどれだけ前から「期限が近い」とするか
 */
@ValueObject
public record AuthorizationTerm(@NonNull Duration validity, @NonNull Duration warning) {

    /**
     * コンストラクタ
     *
     * @throws IllegalArgumentException 有効期間が正でない、期限が近いとする長さが負か、有効期間より短くない
     */
    public AuthorizationTerm {
        if (!validity.isPositive()) {
            throw new IllegalArgumentException("与信の有効期間は正にする");
        }
        if (warning.isNegative()) {
            throw new IllegalArgumentException("与信期限が近いとする長さは 0 以上にする");
        }
        // 有効期間以上にすると、与信を受けた時点から期限が近いことになる
        if (warning.compareTo(validity) >= 0) {
            throw new IllegalArgumentException("与信期限が近いとする長さは、与信の有効期間より短くする");
        }
    }

    /**
     * 与信期限を返す。
     *
     * @param authorizedAt 与信の時刻
     * @return 与信期限
     */
    public Instant deadlineOf(@NonNull Instant authorizedAt) {
        return authorizedAt.plus(validity);
    }

    /**
     * 与信期限が近いかを返す。
     *
     * <p>期限を過ぎていても近いとする。
     *
     * @param authorizedAt 与信の時刻
     * @param now          現在時刻
     * @return 近ければ true
     */
    public boolean isExpiring(@NonNull Instant authorizedAt, @NonNull Instant now) {
        return !now.isBefore(deadlineOf(authorizedAt).minus(warning));
    }

    /**
     * 与信期限が近い与信の、与信の時刻の上限を返す。
     *
     * <p>この時刻以前に与信を受けたものが近い。
     *
     * @param now 現在時刻
     * @return 与信の時刻の上限
     */
    public Instant latestExpiringAuthorizedAt(@NonNull Instant now) {
        return now.minus(validity.minus(warning));
    }
}
