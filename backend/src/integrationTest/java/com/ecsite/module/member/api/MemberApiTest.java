package com.ecsite.module.member.api;

import com.ecsite.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ecsite.jooq.member.Tables.MEMBER_;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会員の統合テスト（T3304、docs/modules/member.md）。<br />
 * 同時の初回の要求を確かめるため、要求ごとにコミットする。ロールバックしないので、作った会員を後で消す。
 */
@IntegrationTest
class MemberApiTest {

    @Autowired
    MemberApi memberApi;

    @Autowired
    DSLContext dsl;

    /**
     * テストで使った IdP のアカウントの ID（後で消す）
     */
    private final List<String> subjects = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(MEMBER_).where(MEMBER_.IDP_SUBJECT.in(subjects)).execute();
    }

    @Test
    @DisplayName("初めての IdP のアカウントなら、会員を作って会員 ID を返す")
    void createsMemberOnFirstRequest() {
        var subject = newSubject();

        var memberId = memberApi.memberIdOf(subject);

        assertThat(memberId).isNotNull();
        assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
        assertThat(dsl.select(MEMBER_.ID).from(MEMBER_).where(MEMBER_.IDP_SUBJECT.eq(subject)).fetchOne(MEMBER_.ID))
                .isEqualTo(memberId);
    }

    @Test
    @DisplayName("同じ IdP のアカウントは、同じ会員 ID になる。会員 ID は IdP のアカウントの ID とは別")
    void returnsSameMemberForSameAccount() {
        var subject = newSubject();

        var first = memberApi.memberIdOf(subject);
        var second = memberApi.memberIdOf(subject);

        assertThat(second).isEqualTo(first);
        assertThat(first.toString()).isNotEqualTo(subject);
        assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
    }

    @Test
    @DisplayName("別の IdP のアカウントは、別の会員になる")
    void returnsDifferentMembersForDifferentAccounts() {
        assertThat(memberApi.memberIdOf(newSubject())).isNotEqualTo(memberApi.memberIdOf(newSubject()));
    }

    @Test
    @DisplayName("同じアカウントの初回の要求が同時に来ても、会員は 1 人だけ作られ、すべて同じ会員 ID になる")
    void createsOnlyOneMemberOnConcurrentFirstRequests() throws Exception {
        var subject = newSubject();
        var threads = 8;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return memberApi.memberIdOf(subject);
                }));
            }
            ready.await();
            start.countDown();

            var memberIds = new HashSet<UUID>();
            for (var future : futures) {
                memberIds.add(future.get());
            }

            assertThat(memberIds).hasSize(1);
            assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String newSubject() {
        var subject = UUID.randomUUID().toString();
        subjects.add(subject);
        return subject;
    }
}
