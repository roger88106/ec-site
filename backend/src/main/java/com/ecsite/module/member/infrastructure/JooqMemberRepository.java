package com.ecsite.module.member.infrastructure;

import com.ecsite.module.member.service.Member;
import com.ecsite.module.member.service.MemberRepository;
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

    @Override
    public Optional<UUID> findIdByIdpSubject(String idpSubject) {
        return dsl.select(MEMBER_.ID).from(MEMBER_).where(MEMBER_.IDP_SUBJECT.eq(idpSubject)).fetchOptional(MEMBER_.ID);
    }

    @Override
    public void insertIfAbsent(Member member) {
        // 同じアカウントの会員を、ほかの要求が先に作っていたら何もしない（一意制約の違反でトランザクションを壊さない）
        dsl.insertInto(MEMBER_)
                .set(MEMBER_.ID, member.id())
                .set(MEMBER_.IDP_SUBJECT, member.idpSubject())
                .set(MEMBER_.CREATED_AT, member.createdAt())
                .onConflict(MEMBER_.IDP_SUBJECT)
                .doNothing()
                .execute();
    }
}
