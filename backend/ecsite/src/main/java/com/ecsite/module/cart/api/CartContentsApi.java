package com.ecsite.module.cart.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.cart.api.model.CartContents;
import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.cart.application.model.CartContentsView;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

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
     * <p>最後の変更から有効期間がたったカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    public Optional<CartContents> find(@NonNull CartId cartId) {
        return cartService.findContents(cartId).map(CartContentsApi::toContents);
    }

    /**
     * 会員のカートの ID を返す（作らない）。
     *
     * @param memberId 会員 ID
     * @return カート ID。会員のカートがなければ空
     */
    public Optional<CartId> findMemberCartId(MemberId memberId) {
        return cartService.findMemberCartId(memberId);
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
     * <p>入れた日時が指定した時刻より後の品目は除かない。変わったらカート変更のイベントを発行する。カートがなければ何もしない。持ち主は確認しない。
     * 呼んだ側のトランザクションに加わる（トランザクションの外から呼ぶと例外）。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     * @param addedUntil 除く品目を入れた日時の上限
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    public void removeItems(@NonNull CartId cartId, @NonNull Collection<VariantId> variantIds, Instant addedUntil) {
        cartService.removeItems(cartId, variantIds, addedUntil);
    }
}
