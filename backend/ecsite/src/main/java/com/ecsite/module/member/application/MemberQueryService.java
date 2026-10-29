package com.ecsite.module.member.application;

import com.ecsite.module.member.application.port.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 会員の参照のサービス（docs/03-機能仕様/member.md）。<br />
 * 会員の確認はログインしたすべての要求で動くので、会員がいるときは読み取り専用のトランザクションで済ませる。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

    /**
     * 会員のリポジトリ
     */
    private final MemberRepository memberRepository;

    /**
     * IdP のアカウントの ID で、会員 ID を探す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID。いなければ空
     */
    public Optional<UUID> findMemberId(String idpSubject) {
        return memberRepository.findIdByIdpSubject(idpSubject);
    }
}
