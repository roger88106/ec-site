package com.ecsite.module.order.infrastructure;

import com.ecsite.module.cart.api.CartContentsApi;
import com.ecsite.module.order.domain.port.CartDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    @Override
    public Optional<CartSnapshot> find(UUID cartId) {
        return cartContentsApi.find(cartId).map(contents -> new CartSnapshot(contents.cartId(), contents.version(),
                contents.items().stream().map(item -> new Line(item.variantId(), item.quantity())).toList()));
    }

    @Override
    public void clear(UUID cartId) {
        cartContentsApi.clear(cartId);
    }
}
