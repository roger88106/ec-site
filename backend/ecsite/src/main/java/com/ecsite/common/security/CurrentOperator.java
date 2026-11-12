package com.ecsite.common.security;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Operator;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * リクエストの token の運営者の取り出し
 *
 * <p>Spring Security の型は外に出さない。
 * 管理 API の filter chain は運営者の realm の token だけを受け付けるので、ここで取り出せるのは運営者だけ。
 */
@Component
public class CurrentOperator {

    /**
     * token の運営者を返す。氏名は、token の姓と名を「姓 名」の順に並べる。
     *
     * @return 運営者
     * @throws BusinessException token がない（{@code UNAUTHENTICATED}）
     */
    public Operator require() {
        var jwt = CurrentToken.require();
        // 氏名は「姓 名」。空の部分は飛ばす
        var name = Stream.of(jwt.getClaimAsString("family_name"), jwt.getClaimAsString("given_name"))
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
        return new Operator(UUID.fromString(jwt.getSubject()), name);
    }
}
