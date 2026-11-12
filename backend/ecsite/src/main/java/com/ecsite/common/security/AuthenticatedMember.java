package com.ecsite.common.security;

import org.jspecify.annotations.Nullable;

/**
 * token の会員（消費者の realm のアカウント）
 *
 * <p>IdP が正本の情報で、EC では写しを持たない。
 *
 * @param subject       IdP のアカウントの ID（token の subject。EC の会員 ID とは別）
 * @param email         メールアドレス
 * @param emailVerified メールアドレスが確認済みか
 * @param familyName    姓
 * @param givenName     名
 * @param birthdate     生年月日（IdP の任意の属性。token の値のまま。形と範囲は確認していない）。なければ null
 * @param gender        性別（IdP の任意の属性。token の値のまま）。なければ null
 */
public record AuthenticatedMember(@Nullable String subject, @Nullable String email, boolean emailVerified,
                                  @Nullable String familyName, @Nullable String givenName,
                                  @Nullable String birthdate, @Nullable String gender) {
}
