package com.ecsite.common.security;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Operator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 要求の token の運営者を取り出す。管理 API の presentation 層が使う。<br />
 * Spring Security の型は、ここより先（application 層）に出さない（constitution 10.2）。
 * 管理 API の filter chain は運営者の realm の token だけを受け付けるので、ここで取り出せるのは運営者だけ。
 */
@Component
public class CurrentOperator {

    /**
     * token の運営者を返す。氏名は、token の姓と名を「姓 名」の順に並べる（管理画面の表示と同じ）。
     *
     * @return 運営者
     * @throws BusinessException token がない（401。filter で認証を必須にしているが、念のため確かめる）
     */
    public Operator require() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            throw BusinessException.of(CommonErrorCode.UNAUTHENTICATED);
        }
        var jwt = token.getToken();
        // 氏名は「姓 名」。空の部分は飛ばす
        var name = Stream.of(jwt.getClaimAsString("family_name"), jwt.getClaimAsString("given_name"))
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
        return new Operator(UUID.fromString(jwt.getSubject()), name);
    }
}
