package com.ecsite.module.storefront.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層の口（constitution 4.1、docs/modules/storefront.md）。<br />
 * カートの見積もりを、店舗の型で返す。
 */
public interface OrderDirectory {

    /**
     * カートを見積もり、表示の形にする。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @return カートの表示。カートがない、または会員のカートで持ち主でなければ空
     */
    Optional<CartView> findCart(UUID cartId, UUID requester);
}
