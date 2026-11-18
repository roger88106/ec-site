package com.ecsite.module.member.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.UUID;

/**
 * 会員
 *
 * <p>IdP のアカウントと、EC の会員 ID の対応。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@AggregateRoot
public final class Member {

    /**
     * 会員 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    @NonNull
    private final UUID id;

    /**
     * IdP のアカウントの ID（token の subject）
     */
    @NonNull
    private final String idpSubject;

    /**
     * 登録日時（会員を作った日時）
     */
    @NonNull
    private final Instant createdAt;
}
