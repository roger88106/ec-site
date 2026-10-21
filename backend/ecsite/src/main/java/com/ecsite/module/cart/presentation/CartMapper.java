package com.ecsite.module.cart.presentation;

import com.ecsite.api.store.model.MemberCart;
import com.ecsite.api.store.model.MergeCartResult;
import com.ecsite.api.store.model.MergedCartItem;
import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.cart.domain.CartMergeResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * カートの API の型への変換（バックエンドの書き方 第 2.2 節）
 */
@Component
class CartMapper {

    /**
     * 会員のカートのレスポンスに変換する。
     *
     * @param cartId カート ID
     * @return 会員のカートのレスポンス
     */
    MemberCart toMemberCart(UUID cartId) {
        return new MemberCart().cartId(cartId);
    }

    /**
     * カートを移した結果のレスポンスに変換する。
     *
     * @param merged 移した結果
     * @return カートを移した結果のレスポンス
     */
    MergeCartResult toMergeCartResult(CartService.MergedCart merged) {
        return new MergeCartResult()
                .cartId(merged.cartId())
                .truncatedItems(merged.result().truncatedItems().stream().map(CartMapper::toMergedItem).toList())
                .skippedItems(merged.result().skippedItems().stream().map(CartMapper::toMergedItem).toList());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 移した結果の品目を、レスポンスの品目にする。
     */
    private static MergedCartItem toMergedItem(CartMergeResult.Item item) {
        return new MergedCartItem()
                .variantId(item.variantId())
                .quantity(item.quantity());
    }
}
