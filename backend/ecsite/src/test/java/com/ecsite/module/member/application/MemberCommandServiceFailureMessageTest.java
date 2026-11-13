package com.ecsite.module.member.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.member.application.port.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会員を作れないときの例外の単体テスト
 *
 * <p>IdP のアカウントの ID は利用者を指すので、メッセージに入れない。
 */
class MemberCommandServiceFailureMessageTest {

    @Test
    @DisplayName("会員を作れないとき、例外のメッセージに IdP のアカウントの ID が出ない")
    void hidesIdpSubject() {
        var idpSubject = UUID.randomUUID().toString();
        var memberRepository = mock(MemberRepository.class);
        when(memberRepository.findIdByIdpSubject(idpSubject)).thenReturn(Optional.empty());
        var service = new MemberCommandService(memberRepository, Clock.systemUTC());

        var thrown = catchThrowable(() -> service.create(idpSubject));

        assertThat(thrown).isInstanceOf(SystemException.class);
        assertThat(thrown.getMessage()).doesNotContain(idpSubject);
    }
}
