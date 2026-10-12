package com.ecsite.common.security;

/**
 * token の会員（消費者の realm のアカウント）。IdP が正本の情報で、EC では写しを持たない（docs/modules/member.md）。
 *
 * @param subject       IdP のアカウントの ID（token の subject）。会員 ID は会員モジュールで求める
 * @param email         メールアドレス
 * @param emailVerified メールアドレスが確認済みか
 * @param familyName    姓
 * @param givenName     名
 */
public record AuthenticatedMember(String subject, String email, boolean emailVerified, String familyName, String givenName) {
}
