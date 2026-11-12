package com.ecsite.module.cart.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.cart.api.model.CartContents;
import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.cart.application.model.CartContentsView;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの API
 */
@Component
@RequiredArgsConstructor
public class CartContentsApi {

    /**
     * カートのサービス
     */
    private final CartService cartService;

    /**
     * カートの中身を返す。
     *
     * <p>最後の変更から 30 日たったカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    public Optional<CartContents> find(@NonNull UUID cartId) {
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
     * 指定したバリアントの品目を、数量によらず行ごとカートから除く。
     *
     * <p>変わったらカート変更のイベントを発行する。カートがなければ何もしない。持ち主は確認しない。
     * 呼んだ側のトランザクションに加わる。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    public void removeItems(@NonNull UUID cartId, @NonNull Collection<UUID> variantIds) {
        cartService.removeItems(cartId, variantIds);
    }
}
