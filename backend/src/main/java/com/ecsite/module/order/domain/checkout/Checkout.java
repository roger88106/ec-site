package com.ecsite.module.order.domain.checkout;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 注文手続き（docs/modules/order.md「注文手続き」）。カートから注文に進んだ 1 回の手続きで、引当の持ち主になる。<br />
 * 作った後は変わらない。
 *
 * @param id          注文手続き ID（引当の持ち主 ID）
 * @param cartId      カート ID
 * @param cartVersion 始めたときのカートのバージョン（注文の時点で違えば、カートが変わっている）
 * @param expiresAt   引当の期限
 * @param createdAt   作成日時
 */
@AggregateRoot
public record Checkout(@Identity UUID id, UUID cartId, long cartVersion, Instant expiresAt, Instant createdAt) {

    /**
     * 注文手続きを始める（AC-82）。
     *
     * @param id             注文手続き ID
     * @param cartId         カート ID
     * @param cartVersion    カートのバージョン
     * @param now            現在時刻
     * @param reservationTtl 引当の期限までの時間（15 分）
     * @return 注文手続き
     */
    public static Checkout start(@NonNull UUID id, @NonNull UUID cartId, long cartVersion, @NonNull Instant now,
                                 @NonNull Duration reservationTtl) {
        return new Checkout(id, cartId, cartVersion, now.plus(reservationTtl), now);
    }

    /**
     * 注文手続きを始めた後に、カートが変わったか。
     *
     * @param currentCartVersion 現在のカートのバージョン
     * @return 変わっていれば true
     */
    public boolean cartChangedSince(long currentCartVersion) {
        return cartVersion != currentCartVersion;
    }
}
