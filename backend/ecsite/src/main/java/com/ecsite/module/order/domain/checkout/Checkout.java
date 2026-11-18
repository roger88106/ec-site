package com.ecsite.module.order.domain.checkout;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 注文手続き
 *
 * <p>カートから注文に進んだ 1 回の手続きで、引当の持ち主になる。作った後は変わらない。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AggregateRoot
public final class Checkout {

    /**
     * 注文手続き ID（引当の持ち主 ID）
     */
    @Identity
    @EqualsAndHashCode.Include
    private final UUID id;

    /**
     * 会員 ID（始めた会員。その会員だけが参照・注文できる）
     */
    private final UUID memberId;

    /**
     * カート ID
     */
    private final UUID cartId;

    /**
     * 始めたときのカートのバージョン（注文の時点で違えば、カートが変わっている）
     */
    private final long cartVersion;

    /**
     * 引当の期限
     */
    private final Instant expiresAt;

    /**
     * 作成日時
     */
    private final Instant createdAt;

    /**
     * 確認した注文の ID の集合（始めるときに会員が確認した支払待ちの注文。確認していなければ空）
     */
    private final Set<UUID> acknowledgedOrderIds;

    /**
     * コンストラクタ
     *
     * <p>引当の期限が作成日時より後であることを検査する。確認した注文の ID の集合は、後から変えられないようにコピーする。
     *
     * @param id                   注文手続き ID
     * @param memberId             会員 ID
     * @param cartId               カート ID
     * @param cartVersion          カートのバージョン
     * @param expiresAt            引当の期限（作成日時より後）
     * @param createdAt            作成日時
     * @param acknowledgedOrderIds 確認した注文の ID の集合
     */
    public Checkout(UUID id, UUID memberId, UUID cartId, long cartVersion, Instant expiresAt, Instant createdAt,
                    Set<UUID> acknowledgedOrderIds) {
        // メッセージに日時を書かない（DB から戻した値が入るため）
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("引当の期限が作成日時より後でない");
        }
        this.id = id;
        this.memberId = memberId;
        this.cartId = cartId;
        this.cartVersion = cartVersion;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.acknowledgedOrderIds = Set.copyOf(acknowledgedOrderIds);
    }

    /**
     * 確認した注文の ID の集合を持って、注文手続きを始める。
     *
     * @param id                   注文手続き ID
     * @param memberId             会員 ID
     * @param cartId               カート ID
     * @param cartVersion          カートのバージョン
     * @param now                  現在時刻
     * @param reservationTtl       引当の期限までの時間
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
     * @param pendingOrderIds 今の支払待ちの注文の ID
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
