package com.ecsite.module.cart.api;

import com.ecsite.module.cart.application.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文に公開するカートの API（docs/backend/modules/cart.md「他のモジュールへの公開」）。<br />
 * カートを空にする操作は、呼び出し元（注文の成立）のトランザクションに参加する。
 */
@Component
@RequiredArgsConstructor
public class CartContentsApi {

    /**
     * カートのサービス
     */
    private final CartService cartService;

    /**
     * カートの中身を返す。最後の変更から 30 日たったカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    public Optional<CartContents> find(UUID cartId) {
        return cartService.findContents(cartId);
    }

    /**
     * カートを空にする（注文の成立）。カート変更のイベントを発行する。
     *
     * @param cartId カート ID
     */
    public void clear(UUID cartId) {
        cartService.clear(cartId);
    }
}
