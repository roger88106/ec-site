package com.ecsite.common.security;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.BoundedText;
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
     * 氏名の最大の文字数（code point）
     *
     * <p>運営者の氏名の写しを持つ列（在庫の調整の履歴の運営者の氏名、注文の判断した運営者の氏名とキャンセルした運営者の氏名）の
     * 長さ（{@code varchar(255)}）に合わせる。氏名は表示のための写しで、超える部分を落としても、運営者 ID で本人を特定できる。
     */
    static final int MAX_NAME_LENGTH = 255;

    /**
     * token の運営者を返す。
     *
     * <p>氏名は、token の姓と名を「姓 名」の順に並べ、{@value #MAX_NAME_LENGTH} 文字（code point）に切り詰める。
     * 列の長さを超えて保存に失敗しないため。
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
        return new Operator(UUID.fromString(jwt.getSubject()), BoundedText.truncate(name, MAX_NAME_LENGTH));
    }
}
