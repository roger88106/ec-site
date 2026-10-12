package com.ecsite.module.member.api;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.application.MemberService;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 現在の会員の ID を求める部品の単体テスト（spec 005 DC-39）
 */
class CurrentMemberIdsTest {

    /**
     * 会員の記録（IdP の subject → 会員 ID）を持つだけのリポジトリ
     */
    private final Map<String, UUID> members = new HashMap<>();

    /**
     * テストの対象
     */
    private final CurrentMemberIds currentMemberIds = new CurrentMemberIds(new CurrentMember(),
            new MemberService(new MemberRepository() {
                @Override
                public Optional<UUID> findIdByIdpSubject(String idpSubject) {
                    return Optional.ofNullable(members.get(idpSubject));
                }

                @Override
                public void insertIfAbsent(Member member) {
                    members.putIfAbsent(member.idpSubject(), member.id());
                }
            }, Clock.systemUTC()));

    /**
     * ログインの状態を消す
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("ログインしていれば、IdP のアカウントの会員 ID を返す（初めてなら会員を作る）")
    void returnsMemberIdOfLoggedInMember() {
        loginAs("subject-1");

        UUID id = currentMemberIds.require();

        assertThat(id).isEqualTo(members.get("subject-1"));
        assertThat(currentMemberIds.require()).as("2 回目も同じ会員").isEqualTo(id);
        assertThat(currentMemberIds.find()).contains(id);
    }

    @Test
    @DisplayName("ログインしていなければ、require は 401 の業務のエラー、find は空")
    void handlesAnonymous() {
        assertBusinessError(currentMemberIds::require, CommonErrorCode.UNAUTHENTICATED);
        assertThat(currentMemberIds.find()).isEmpty();
        assertThat(members).as("会員を作らない").isEmpty();
    }

    /**
     * 会員の token でログインしている状態にする。
     *
     * @param subject IdP のアカウントの ID
     */
    private static void loginAs(String subject) {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
