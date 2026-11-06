package com.ecsite.module.order.domain.checkout;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 注文手続き（docs/03-機能仕様/order.md「注文手続き」）。カートから注文に進んだ 1 回の手続きで、引当の持ち主になる。<br />
 * 作った後は変わらない。
 *
 * @param id          注文手続き ID（引当の持ち主 ID）
 * @param memberId    会員 ID（始めた会員。その会員だけが参照・注文できる）
 * @param cartId      カート ID
 * @param cartVersion 始めたときのカートのバージョン（注文の時点で違えば、カートが変わっている）
 * @param expiresAt   引当の期限
 * @param createdAt   作成日時
 * @param acknowledgedOrderIds 確認した注文の ID の集合（始めるときに会員が確認した支払待ちの注文。確認していなければ空）
 */
@AggregateRoot
public record Checkout(@Identity UUID id, UUID memberId, UUID cartId, long cartVersion, Instant expiresAt, Instant createdAt,
                       Set<UUID> acknowledgedOrderIds) {

    /**
     * 確認した注文の ID の集合は、後から変えられないように複写する。
     */
    public Checkout {
        acknowledgedOrderIds = Set.copyOf(acknowledgedOrderIds);
    }

    /**
     * 注文手続きを始める。
     *
     * @param id             注文手続き ID
     * @param memberId       会員 ID
     * @param cartId         カート ID
     * @param cartVersion    カートのバージョン
     * @param now            現在時刻
     * @param reservationTtl 引当の期限までの時間（15 分）
     * @return 注文手続き
     */
    public static Checkout start(@NonNull UUID id, @NonNull UUID memberId, @NonNull UUID cartId, long cartVersion, @NonNull Instant now,
                                 @NonNull Duration reservationTtl) {
        return start(id, memberId, cartId, cartVersion, now, reservationTtl, Set.of());
    }

    /**
     * 確認した注文の ID の集合を持って、注文手続きを始める。
     *
     * @param id                   注文手続き ID
     * @param memberId             会員 ID
     * @param cartId               カート ID
     * @param cartVersion          カートのバージョン
     * @param now                  現在時刻
     * @param reservationTtl       引当の期限までの時間（15 分）
     * @param acknowledgedOrderIds 確認した注文の ID の集合
     * @return 注文手続き
     */
    public static Checkout start(@NonNull UUID id, @NonNull UUID memberId, @NonNull UUID cartId, long cartVersion, @NonNull Instant now,
                                 @NonNull Duration reservationTtl, @NonNull Set<UUID> acknowledgedOrderIds) {
        return new Checkout(id, memberId, cartId, cartVersion, now.plus(reservationTtl), now, acknowledgedOrderIds);
    }

    /**
     * 今の支払待ちの注文が全て、確認した注文に含まれるかを返す。
     *
     * @param pendingOrderIds 今の支払待ちの注文の ID の集合
     * @return 全て含まれていれば true（支払待ちがなければ常に true）
     */
    public boolean acknowledges(@NonNull Set<UUID> pendingOrderIds) {
        return acknowledgedOrderIds.containsAll(pendingOrderIds);
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

    /**
     * 注文手続きを始めた会員かを返す。
     *
     * @param requester リクエストした会員の ID
     * @return 始めた会員なら true
     */
    public boolean isOwnedBy(UUID requester) {
        return memberId.equals(requester);
    }
}
