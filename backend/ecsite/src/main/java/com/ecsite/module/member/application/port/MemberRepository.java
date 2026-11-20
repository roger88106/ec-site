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
     * 会員を作り、会員 ID を返す。
     *
     * <p>同じ IdP のアカウントの会員がすでにいれば、何も変えずにその会員 ID を返す（一意制約の違反でトランザクションを失敗させない）。
     * 1 つの文で行い、同時に作られたときも、先に作られた会員 ID を返す。
     *
     * @param member 会員
     * @return 会員 ID（作ったとき {@code member} の ID、すでにいたとき既存の会員の ID）
     */
    UUID createOrGetId(Member member);
}
