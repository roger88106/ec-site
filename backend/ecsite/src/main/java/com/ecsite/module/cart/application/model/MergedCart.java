package com.ecsite.module.cart.application.model;

import com.ecsite.module.cart.domain.CartMergeResult;

import java.util.UUID;

/**
 * 会員のカートと、移した結果
 *
 * @param cartId 会員のカートの ID
 * @param result 移した結果
 */
public record MergedCart(UUID cartId, CartMergeResult result) {
}
