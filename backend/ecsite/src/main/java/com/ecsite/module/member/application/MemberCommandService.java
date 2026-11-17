package com.ecsite.module.member.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 会員の更新のサービス
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
     * IdP のアカウントの会員を作り、会員 ID を返す。
     *
     * <p>同じアカウントの初回のリクエストが同時に来たら、IdP のアカウントの ID の一意制約で 1 人だけ作られ、
     * 作れなかったリクエストは、先に作られた会員を読み直す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID
     */
    public UUID create(@NonNull String idpSubject) {
        // 会員登録 ※同じアカウントの会員がいれば作らない
        memberRepository.insertIfAbsent(new Member(UUID.randomUUID(), idpSubject, clock.instant()));
        // 会員 ID 取得 ※同時に作られたときは、先に作られた会員を読み直す
        return memberRepository.findIdByIdpSubject(idpSubject)
                .orElseThrow(() -> SystemException.of("会員作成失敗", null));
    }
}
