package com.ecsite.module.member.infrastructure.persistence;

import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.member.Tables.MEMBER_;

/**
 * 会員のリポジトリの jOOQ の実装
 */
@Repository
@RequiredArgsConstructor
class JooqMemberRepository implements MemberRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * IdP のアカウントの ID で、会員 ID を探す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID。いなければ空
     */
    @Override
    public Optional<UUID> findIdByIdpSubject(String idpSubject) {
        return dsl.select(MEMBER_.ID).from(MEMBER_).where(MEMBER_.IDP_SUBJECT.eq(idpSubject)).fetchOptional(MEMBER_.ID);
    }

    /**
     * 会員を作る
     *
     * <p>同じ IdP のアカウントの会員が既にいれば、作らない。
     *
     * @param member 会員
     */
    @Override
    public void insertIfAbsent(Member member) {
        dsl.insertInto(MEMBER_)
                .set(MEMBER_.ID, member.id())
                .set(MEMBER_.IDP_SUBJECT, member.idpSubject())
                .set(MEMBER_.CREATED_AT, member.createdAt())
                // IdP のアカウントの ID が重複するときは挿入しない（一意制約の違反でトランザクションを失敗させない）
                .onConflict(MEMBER_.IDP_SUBJECT).doNothing()
                .execute();
    }
}
