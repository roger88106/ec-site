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
     * 会員を作る。
     *
     * <p>同じ IdP のアカウントの会員がすでにいれば、何もしない（一意制約の違反でトランザクションを失敗させない）。
     *
     * @param member 会員
     */
    void insertIfAbsent(Member member);
}
