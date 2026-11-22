package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.cart.api.CartContentsApi;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.port.CartDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの腐敗防止層
 *
 * <p>カートモジュールの API を呼ぶ。
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
        return cartContentsApi.find(new CartId(cartId)).map(contents -> new CartSnapshot(contents.cartId().value(),
                contents.memberId() == null ? null : contents.memberId().value(), contents.version(),
                contents.items().stream().map(item -> new CartSnapshot.Line(item.variantId().value(), item.quantity())).toList()));
    }

    /**
     * 会員のカートの ID を取る。
     */
    @Override
    public Optional<UUID> findMemberCartId(UUID memberId) {
        return cartContentsApi.findMemberCartId(new MemberId(memberId)).map(CartId::value);
    }

    /**
     * 注文した品目をカートから除く。
     */
    @Override
    public void removeItems(UUID cartId, Collection<UUID> variantIds, Instant addedUntil) {
        cartContentsApi.removeItems(new CartId(cartId), variantIds.stream().map(VariantId::new).toList(), addedUntil);
    }
}
