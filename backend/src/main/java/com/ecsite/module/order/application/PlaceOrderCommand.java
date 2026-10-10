package com.ecsite.module.order.application;

import java.util.UUID;

/**
 * 注文のコマンド（AC-85、86、89）
 *
 * @param checkoutId    注文手続き ID
 * @param email         メールアドレス
 * @param name          氏名
 * @param postalCode    郵便番号
 * @param prefecture    都道府県
 * @param address       住所
 * @param phone         電話番号
 * @param expectedTotal 注文手続きの画面で表示した総額
 */
public record PlaceOrderCommand(UUID checkoutId, String email, String name, String postalCode, String prefecture,
                                String address, String phone, long expectedTotal) {
}
