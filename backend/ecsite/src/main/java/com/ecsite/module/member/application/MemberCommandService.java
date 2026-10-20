package com.ecsite.module.member.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 会員の更新のサービス（docs/backend/modules/member.md）。<br />
 * 会員は、会員の token で初めて EC の API を呼んだときに作る。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {

    /**
     * 会員のリポジトリ
     */
    private final MemberRepository memberRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * IdP のアカウントの会員を作り、会員 ID を返す。<br />
     * 同じアカウントの初回の要求が同時に来たら、IdP のアカウントの ID の一意制約で 1 人だけ作られ、
     * 作れなかった要求は、先に作られた会員を読み直す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID
     */
    public UUID create(String idpSubject) {
        memberRepository.insertIfAbsent(new Member(UUID.randomUUID(), idpSubject, clock.instant()));
        return memberRepository.findIdByIdpSubject(idpSubject)
                .orElseThrow(() -> SystemException.of("会員を作れない：" + idpSubject, null));
    }
}
