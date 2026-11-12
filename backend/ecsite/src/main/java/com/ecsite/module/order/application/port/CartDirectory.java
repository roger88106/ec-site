package com.ecsite.module.order.application.port;

import com.ecsite.module.order.application.model.CartSnapshot;

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
     * 品目（バリアント）を、数量によらず行ごとカートから除く。
     *
     * <p>カートがなければ何もしない。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     */
    void removeItems(UUID cartId, Collection<UUID> variantIds);
}
