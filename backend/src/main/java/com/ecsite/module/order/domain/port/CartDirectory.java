package com.ecsite.module.order.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの腐敗防止層（constitution 4.1）
 */
public interface CartDirectory {

    /**
     * カートの中身を返す。期限切れのカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    Optional<CartSnapshot> find(UUID cartId);

    /**
     * カートを空にする（注文の成立。AC-90）。
     *
     * @param cartId カート ID
     */
    void clear(UUID cartId);

    /**
     * カートの中身
     *
     * @param cartId  カート ID
     * @param version バージョン（注文手続きの後の変更を見つけるため）
     * @param lines   品目（入れた順）
     */
    record CartSnapshot(UUID cartId, long version, List<Line> lines) {
    }

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    record Line(UUID variantId, int quantity) {
    }
}
