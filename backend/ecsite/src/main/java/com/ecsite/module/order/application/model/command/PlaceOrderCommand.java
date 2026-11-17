package com.ecsite.module.order.application.model.command;

import com.ecsite.common.model.value.Money;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 注文のコマンド
 *
 * @param checkoutId       注文手続き ID
 * @param memberId         会員 ID（注文する会員）
 * @param email            メールアドレス（会員の token のもの）
 * @param emailVerified    メールアドレスが確認済みか（token のもの）
 * @param name             氏名
 * @param postalCode       郵便番号
 * @param prefectureCode   都道府県のコード
 * @param municipalityCode 市区町村のコード
 * @param address          市区町村より下の住所
 * @param phone            電話番号
 * @param expectedTotal    注文手続きの画面で表示した総額
 * @param birthdate        生年月日（会員の token のもの。なければ null）
 * @param gender           性別（会員の token のもの。なければ null）
 */
public record PlaceOrderCommand(@NonNull UUID checkoutId, @NonNull UUID memberId, @Nullable String email, boolean emailVerified,
                                @NonNull String name, @NonNull String postalCode, @NonNull String prefectureCode,
                                @NonNull String municipalityCode, @NonNull String address, @NonNull String phone,
                                @NonNull Money expectedTotal,
                                @Nullable String birthdate, @Nullable String gender) {
}
