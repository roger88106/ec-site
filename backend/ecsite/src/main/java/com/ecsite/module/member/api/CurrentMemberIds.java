package com.ecsite.module.member.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.application.MemberIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * リクエストした会員の会員 ID
 *
 * <p>IdP のアカウント（token の subject）から会員 ID を求める処理は、会員モジュールの中だけで行う。
 */
@Component
@RequiredArgsConstructor
public class CurrentMemberIds {

    /**
     * リクエストの token の会員
     */
    private final CurrentMember currentMember;

    /**
     * 会員 ID を求める処理
     */
    private final MemberIdResolver resolver;

    /**
     * ログインが必要な操作の、会員 ID を返し、初めての会員なら会員を作る。
     *
     * <p>同じアカウントの 2 回目からは、キャッシュから返す。
     *
     * @return 会員 ID
     * @throws BusinessException ログインしていない（{@code UNAUTHENTICATED}）
     */
    public UUID require() {
        return resolver.resolve(currentMember.require().subject());
    }

    /**
     * ログインが任意の操作の会員 ID を返し、初めての会員なら会員を作る。
     *
     * @return 会員 ID。ログインしていなければ空
     */
    public Optional<UUID> find() {
        return currentMember.find().map(member -> resolver.resolve(member.subject()));
    }
}
