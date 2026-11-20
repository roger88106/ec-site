package com.ecsite.module.member.infrastructure.persistence;

import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.member.Tables.MEMBER_;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会員のリポジトリの統合テスト
 *
 * <p>会員の保存と、IdP のアカウントの ID での検索を確認する。リクエストから会員 ID を求める流れは {@code MemberConfirmationTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqMemberRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 会員の作成日時
     */
    private static final Instant CREATED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 保存する会員の、IdP のアカウントの ID
     */
    private static final String SUBJECT = "idp-subject-1";

    /**
     * 会員のいない、IdP のアカウントの ID
     */
    private static final String UNKNOWN_SUBJECT = "idp-subject-none";

    // ============================== Bean ==============================

    @Autowired
    MemberRepository repository;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("会員を作ると、IdP のアカウントの ID から会員 ID を探せる")
    void createsAndFindsMember() {
        // 準備：会員
        var member = new Member(UUID.randomUUID(), SUBJECT, CREATED_AT);

        // 操作：保存する
        repository.createOrGetId(member);

        // 確認：アカウントの ID で、その会員 ID が見つかる
        assertThat(repository.findIdByIdpSubject(SUBJECT)).contains(member.id());
    }

    @Test
    @DisplayName("会員のいない IdP のアカウントの ID には、空を返す")
    void findsNothingForUnknownSubject() {
        // 準備：別のアカウントの会員を保存する
        repository.createOrGetId(new Member(UUID.randomUUID(), SUBJECT, CREATED_AT));

        // 操作と確認：空
        assertThat(repository.findIdByIdpSubject(UNKNOWN_SUBJECT)).isEmpty();
    }

    @Test
    @DisplayName("会員がいないときは、作った会員の ID を返し、IdP のアカウントの ID から探せる")
    void createOrGetIdReturnsNewId() {
        // 準備：会員
        var member = new Member(UUID.randomUUID(), SUBJECT, CREATED_AT);

        // 操作：1 つの SQL の文で作って ID を得る
        var id = repository.createOrGetId(member);

        // 確認：渡した会員の ID で、アカウントの ID から探せる
        assertThat(id).isEqualTo(member.id());
        assertThat(repository.findIdByIdpSubject(SUBJECT)).contains(member.id());
    }

    @Test
    @DisplayName("同じ IdP のアカウントの会員がすでにいれば、先の会員 ID を返し、既存の行の値を変えない")
    void createOrGetIdReturnsExistingIdAndKeepsRow() {
        // 準備：会員を作り、行の全ての値を控える
        var first = new Member(UUID.randomUUID(), SUBJECT, CREATED_AT);
        repository.createOrGetId(first);
        var before = dsl.selectFrom(MEMBER_).where(MEMBER_.ID.eq(first.id())).fetchOne();

        // 操作：同じアカウントで、別の ID と別の作成日時の会員を渡す（例外にならない）
        var id = repository.createOrGetId(new Member(UUID.randomUUID(), SUBJECT, CREATED_AT.plusSeconds(60)));

        // 確認：先の会員 ID を返し、行の値（作成日時を含む）は変わらず、行は 1 つのまま
        assertThat(id).isEqualTo(first.id());
        assertThat(dsl.selectFrom(MEMBER_).where(MEMBER_.ID.eq(first.id())).fetchOne()).isEqualTo(before);
        assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(SUBJECT))).isEqualTo(1);
    }
}
