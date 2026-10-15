package com.ecsite.module.order.application.model;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 注文のコマンド
 *
 * @param checkoutId    注文手続き ID
 * @param memberId      会員 ID（注文する会員。注文手続きを始めた会員でなければ 404）
 * @param email         メールアドレス（会員の token のもの）
 * @param emailVerified メールアドレスが確認済みか（token のもの）
 * @param name          氏名
 * @param postalCode    郵便番号
 * @param prefecture    都道府県
 * @param address       住所
 * @param phone         電話番号
 * @param expectedTotal 注文手続きの画面で表示した総額
 * @param birthdate     生年月日（会員の token のもの。なければ null）
 * @param gender        性別（会員の token のもの。なければ null）
 */
public record PlaceOrderCommand(UUID checkoutId, UUID memberId, String email, boolean emailVerified,
                                String name, String postalCode, String prefecture,
                                String address, String phone, Money expectedTotal,
                                @Nullable String birthdate, @Nullable String gender) {
}
