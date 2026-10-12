package com.ecsite.module.member.application.port;

import com.ecsite.module.member.domain.Member;

import java.util.Optional;
import java.util.UUID;

/**
 * 会員のリポジトリ
 */
public interface MemberRepository {

    /**
     * IdP のアカウントの ID で、会員 ID を探す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID。いなければ空
     */
    Optional<UUID> findIdByIdpSubject(String idpSubject);

    /**
     * 会員を作る。同じ IdP のアカウントの会員がすでにいれば、何もしない（トランザクションを壊さない）。
     *
     * @param member 会員
     */
    void insertIfAbsent(Member member);
}
