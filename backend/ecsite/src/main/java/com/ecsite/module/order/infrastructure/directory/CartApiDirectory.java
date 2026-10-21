package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.cart.api.CartContentsApi;
import com.ecsite.module.order.application.port.CartDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの腐敗防止層の実装。カートモジュールの API（{@link CartContentsApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class CartApiDirectory implements CartDirectory {

    /**
     * カートの API
     */
    private final CartContentsApi cartContentsApi;

    /**
     * カートの中身を取り、注文モジュールの型に変換する。
     */
    @Override
    public Optional<CartSnapshot> find(UUID cartId) {
        return cartContentsApi.find(cartId).map(contents -> new CartSnapshot(contents.cartId(), contents.memberId(), contents.version(),
                contents.items().stream().map(item -> new Line(item.variantId(), item.quantity())).toList()));
    }

    /**
     * 注文した品目をカートから除く。
     */
    @Override
    public void removeItems(UUID cartId, Collection<UUID> variantIds) {
        cartContentsApi.removeItems(cartId, variantIds);
    }
}
