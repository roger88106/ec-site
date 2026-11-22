package com.ecsite.module.member.infrastructure.persistence;

import com.ecsite.common.model.id.MemberId;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
    public Optional<MemberId> findIdByIdpSubject(String idpSubject) {
        return dsl.select(MEMBER_.ID).from(MEMBER_).where(MEMBER_.IDP_SUBJECT.eq(idpSubject)).fetchOptional(MEMBER_.ID).map(MemberId::new);
    }

    /**
     * 会員を作り、会員 ID を返す。
     *
     * <p>同じ IdP のアカウントの会員が既にいれば、その会員 ID を返し、他の列は変えない。
     *
     * @param member 会員
     * @return 会員 ID
     */
    @Override
    public MemberId createOrGetId(Member member) {
        // 衝突したときは、一意の列を同じ値で書き換えて RETURNING で既存の行の ID を得る（DO NOTHING では行が返らない）。他の列は書き換えない
        var id = dsl.insertInto(MEMBER_)
                .set(MEMBER_.ID, member.id().value())
                .set(MEMBER_.IDP_SUBJECT, member.idpSubject())
                .set(MEMBER_.CREATED_AT, member.createdAt())
                .onConflict(MEMBER_.IDP_SUBJECT)
                .doUpdate()
                .set(MEMBER_.IDP_SUBJECT, DSL.excluded(MEMBER_.IDP_SUBJECT))
                .returning(MEMBER_.ID)
                .fetchSingle()
                .getId();
        return new MemberId(id);
    }
}
