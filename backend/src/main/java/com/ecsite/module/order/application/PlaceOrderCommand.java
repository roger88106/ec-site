package com.ecsite.module.order.application;

import java.util.UUID;

/**
 * 注文のコマンド（AC-85、86、89）
 *
 * @param checkoutId    注文手続き ID
 * @param memberId      会員 ID（注文する会員。注文手続きを始めた会員でなければ 404）
 * @param email         メールアドレス（会員の token のもの。AC-127）
 * @param emailVerified メールアドレスが確認済みか（token のもの）
 * @param name          氏名
 * @param postalCode    郵便番号
 * @param prefecture    都道府県
 * @param address       住所
 * @param phone         電話番号
 * @param expectedTotal 注文手続きの画面で表示した総額
 */
public record PlaceOrderCommand(UUID checkoutId, UUID memberId, String email, boolean emailVerified,
                                String name, String postalCode, String prefecture,
                                String address, String phone, long expectedTotal) {
}
