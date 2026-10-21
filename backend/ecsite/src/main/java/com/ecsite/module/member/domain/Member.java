package com.ecsite.module.member.domain;

import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

/**
 * 会員（docs/design/modules/member.md）。IdP のアカウントと、EC の会員 ID の対応。
 *
 * @param id         会員 ID
 * @param idpSubject IdP のアカウントの ID（token の subject）
 * @param createdAt  登録日時（会員を作った日時）
 */
public record Member(@NonNull UUID id, @NonNull String idpSubject, @NonNull Instant createdAt) {
}
