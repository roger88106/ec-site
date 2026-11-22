package com.ecsite.module.storefront.application.port;

import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.module.storefront.application.model.CartView;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * 注文の腐敗防止層の port
 *
 * <p>カートの見積もりを、店舗の型で返す。
 */
public interface OrderDirectory {

    /**
     * カートを見積もり、表示の形にする。
     *
     * @param cartId    カート ID
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @return カートの表示。カートがない、または会員のカートで持ち主でなければ空
     */
    Optional<CartView> findCart(CartId cartId, @Nullable MemberId requester);
}
