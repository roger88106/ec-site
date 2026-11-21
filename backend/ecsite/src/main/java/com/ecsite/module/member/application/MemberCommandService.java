package com.ecsite.module.member.application;

import com.ecsite.common.id.IdGenerator;
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
     * 識別子の生成
     */
    private final IdGenerator idGenerator;

    /**
     * IdP のアカウントの会員を作り、会員 ID を返す。
     *
     * <p>同じアカウントの初回のリクエストが同時に来ても、IdP のアカウントの ID の一意制約で 1 人だけ作られ、
     * 全てのリクエストが、その会員の ID を 1 つの文で受け取る。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID
     */
    public UUID create(@NonNull String idpSubject) {
        // 会員登録と会員 ID 取得 ※同じアカウントの会員がいれば作らず、その会員 ID を返す
        return memberRepository.createOrGetId(new Member(idGenerator.generate(), idpSubject, clock.instant()));
    }
}
