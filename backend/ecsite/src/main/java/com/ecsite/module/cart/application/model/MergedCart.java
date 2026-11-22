package com.ecsite.module.cart.application.model;

import com.ecsite.common.model.id.CartId;
import com.ecsite.module.cart.domain.CartMergeResult;

/**
 * 会員のカートと、移した結果
 *
 * @param cartId 会員のカートの ID
 * @param result 移した結果
 */
public record MergedCart(CartId cartId, CartMergeResult result) {
}
