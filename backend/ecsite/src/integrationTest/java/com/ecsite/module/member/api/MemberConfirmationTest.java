package com.ecsite.module.member.api;

import com.ecsite.IntegrationTest;
import com.ecsite.module.member.application.port.MemberRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ecsite.jooq.member.Tables.MEMBER_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 会員の確認の統合テスト（docs/03-機能仕様/member.md）。<br />
 * 同時の初回の要求を確認するため、要求ごとにコミットする。ロールバックしないので、作った会員を後で消す。
 * トランザクションが読み取り専用かは、会員のリポジトリを包み、呼ばれたときの状態を記録して確認する。
 */
@IntegrationTest
class MemberConfirmationTest {

    @Autowired
    CurrentMemberIds currentMemberIds;

    @MockitoSpyBean
    MemberRepository memberRepository;

    @Autowired
    DSLContext dsl;

    /**
     * テストで使った IdP のアカウントの ID（後で消す）
     */
    private final List<String> subjects = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        dsl.deleteFrom(MEMBER_).where(MEMBER_.IDP_SUBJECT.in(subjects)).execute();
    }

    @Test
    @DisplayName("初めての IdP のアカウントなら、会員を作って会員 ID を返す")
    void createsMemberOnFirstRequest() {
        var subject = newSubject();

        var memberId = memberIdOf(subject);

        assertThat(memberId).isNotNull();
        assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
        assertThat(dsl.select(MEMBER_.ID).from(MEMBER_).where(MEMBER_.IDP_SUBJECT.eq(subject)).fetchOne(MEMBER_.ID))
                .isEqualTo(memberId);
    }

    @Test
    @DisplayName("同じ IdP のアカウントは、同じ会員 ID になる。会員 ID は IdP のアカウントの ID とは別")
    void returnsSameMemberForSameAccount() {
        var subject = newSubject();

        var first = memberIdOf(subject);
        var second = memberIdOf(subject);

        assertThat(second).isEqualTo(first);
        assertThat(first.toString()).isNotEqualTo(subject);
        assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
    }

    @Test
    @DisplayName("別の IdP のアカウントは、別の会員になる")
    void returnsDifferentMembersForDifferentAccounts() {
        assertThat(memberIdOf(newSubject())).isNotEqualTo(memberIdOf(newSubject()));
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
                    try {
                        return memberIdOf(subject);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
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

    @Test
    @DisplayName("NFR-25 会員がいるときは、読み取り専用のトランザクションで探し、会員を作る操作を呼ばない")
    void findsExistingMemberReadOnly() {
        var subject = newSubject();
        var memberId = memberIdOf(subject);
        // 会員を作った呼び出しは数えない
        clearInvocations(memberRepository);
        var readOnly = recordReadOnly(subject);

        var found = memberIdOf(subject);

        assertThat(found).isEqualTo(memberId);
        assertThat(readOnly).containsExactly(true);
        verify(memberRepository, never()).insertIfAbsent(any());
    }

    @Test
    @DisplayName("NFR-25 会員がいないときは、読み取り専用で探した後、読み書きのトランザクションで作る")
    void createsMissingMemberInReadWriteTransaction() {
        var subject = newSubject();
        var readOnly = recordReadOnly(subject);
        var insertReadOnly = new CopyOnWriteArrayList<Boolean>();
        doAnswer(invocation -> {
            insertReadOnly.add(currentTransactionReadOnly());
            return invocation.callRealMethod();
        }).when(memberRepository).insertIfAbsent(any());

        memberIdOf(subject);

        assertThat(readOnly.getFirst()).isTrue();
        assertThat(insertReadOnly).containsExactly(false);
    }

    // ============================== 補助 ==============================

    private String newSubject() {
        var subject = UUID.randomUUID().toString();
        subjects.add(subject);
        return subject;
    }

    /**
     * IdP のアカウントでログインした要求として、会員 ID を求める。
     */
    private UUID memberIdOf(String subject) {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        return currentMemberIds.require();
    }

    /**
     * 会員を探す操作が呼ばれたときの、トランザクションが読み取り専用か（トランザクションがなければ null）を記録する。
     */
    private List<Boolean> recordReadOnly(String subject) {
        var readOnly = new CopyOnWriteArrayList<Boolean>();
        doAnswer(invocation -> {
            readOnly.add(currentTransactionReadOnly());
            return invocation.callRealMethod();
        }).when(memberRepository).findIdByIdpSubject(eq(subject));
        return readOnly;
    }

    /**
     * いまのトランザクションが読み取り専用か。トランザクションがなければ null
     */
    private static Boolean currentTransactionReadOnly() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                ? TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                : null;
    }
}
