package com.ecsite.module.cart.api;

import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.cart.application.model.CartContentsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 注文に公開するカートの API（docs/03-機能仕様/cart.md「他のモジュールへの公開」）。<br />
 * 品目を除く操作は、呼び出し元（注文の与信の成功）のトランザクションに参加する。
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
        return cartService.findContents(cartId).map(CartContentsApi::toContents);
    }

    /**
     * カートの中身を公開の型にする。
     */
    private static CartContents toContents(CartContentsView view) {
        return new CartContents(view.cartId(), view.memberId(), view.version(),
                view.items().stream().map(item -> new CartContents.Item(item.variantId(), item.quantity())).toList());
    }

    /**
     * 注文した品目（バリアント）を、数量によらず行ごとカートから除く（注文の与信の成功）。<br />
     * 変わったらカート変更のイベントを発行する。カートがなければ何もしない。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     */
    public void removeItems(UUID cartId, Collection<UUID> variantIds) {
        cartService.removeItems(cartId, variantIds);
    }
}
