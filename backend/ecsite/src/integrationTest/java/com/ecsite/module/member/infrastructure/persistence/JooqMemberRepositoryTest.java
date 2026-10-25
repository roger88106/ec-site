package com.ecsite.module.member.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会員のリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqMemberRepositoryTest {

    /**
     * 会員の作成日時
     */
    private static final Instant CREATED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    MemberRepository repository;

    @Test
    @DisplayName("会員を作ると、IdP のアカウントの ID から会員 ID を探せる。ないアカウントには空を返す")
    void createsAndFindsMember() {
        var member = new Member(UUID.randomUUID(), "idp-subject-1", CREATED_AT);

        repository.insertIfAbsent(member);

        assertThat(repository.findIdByIdpSubject("idp-subject-1")).contains(member.id());
        assertThat(repository.findIdByIdpSubject("idp-subject-none")).isEmpty();
    }

    @Test
    @DisplayName("同じ IdP のアカウントの会員がすでにいれば何もせず、先の会員 ID が残る。例外にならずトランザクションも壊れない")
    void keepsFirstMemberForSameAccount() {
        var first = new Member(UUID.randomUUID(), "idp-subject-2", CREATED_AT);
        repository.insertIfAbsent(first);

        repository.insertIfAbsent(new Member(UUID.randomUUID(), "idp-subject-2", CREATED_AT.plusSeconds(60)));

        assertThat(repository.findIdByIdpSubject("idp-subject-2")).contains(first.id());
    }
}
