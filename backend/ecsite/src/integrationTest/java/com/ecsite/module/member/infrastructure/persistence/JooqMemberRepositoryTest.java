package com.ecsite.module.member.infrastructure.persistence;

import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

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

    // ============================== テスト ==============================

    @Test
    @DisplayName("会員を作ると、IdP のアカウントの ID から会員 ID を探せる")
    void createsAndFindsMember() {
        // 準備：会員
        var member = new Member(UUID.randomUUID(), SUBJECT, CREATED_AT);

        // 操作：保存する
        repository.insertIfAbsent(member);

        // 確認：アカウントの ID で、その会員 ID が見つかる
        assertThat(repository.findIdByIdpSubject(SUBJECT)).contains(member.id());
    }

    @Test
    @DisplayName("会員のいない IdP のアカウントの ID には、空を返す")
    void findsNothingForUnknownSubject() {
        // 準備：別のアカウントの会員を保存する
        repository.insertIfAbsent(new Member(UUID.randomUUID(), SUBJECT, CREATED_AT));

        // 操作と確認：空
        assertThat(repository.findIdByIdpSubject(UNKNOWN_SUBJECT)).isEmpty();
    }

    @Test
    @DisplayName("同じ IdP のアカウントの会員がすでにいれば何もせず、先の会員 ID が残る。例外にならずトランザクションも壊れない")
    void keepsFirstMemberForSameAccount() {
        // 準備：会員を保存する
        var first = new Member(UUID.randomUUID(), SUBJECT, CREATED_AT);
        repository.insertIfAbsent(first);

        // 操作：同じアカウントの別の会員を保存する（例外にならない）
        repository.insertIfAbsent(new Member(UUID.randomUUID(), SUBJECT, CREATED_AT.plusSeconds(60)));

        // 確認：同じトランザクションで続けて読め、先の会員 ID
        assertThat(repository.findIdByIdpSubject(SUBJECT)).contains(first.id());
    }
}
