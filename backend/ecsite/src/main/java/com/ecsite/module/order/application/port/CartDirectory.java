package com.ecsite.module.order.application.port;

import com.ecsite.module.order.application.model.CartSnapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの腐敗防止層の port
 */
public interface CartDirectory {

    /**
     * カートの中身を返す。
     *
     * <p>期限切れのカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    Optional<CartSnapshot> find(UUID cartId);

    /**
     * 会員のカートの ID を返す（作らない）。
     *
     * @param memberId 会員 ID
     * @return カート ID。会員のカートがなければ空
     */
    Optional<UUID> findMemberCartId(UUID memberId);

    /**
     * 品目（バリアント）を、数量によらず行ごとカートから除く。
     *
     * <p>入れた日時が指定した時刻より後の品目は除かない。カートがなければ何もしない。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     * @param addedUntil 除く品目を入れた日時の上限
     */
    void removeItems(UUID cartId, Collection<UUID> variantIds, Instant addedUntil);
}
