package com.ecsite.module.catalog.domain.product.vo;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * 品番の形式（AC-15、21）。商品品番とバリアント品番で共通。<br />
 * 半角の英大文字・数字とハイフンで 1〜32 文字。ハイフンは英数字の間にだけ置ける。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CodeFormat {

    /**
     * 最大の文字数
     */
    private static final int MAX_LENGTH = 32;

    /**
     * 英数字のかたまりを、ハイフン 1 つでつないだ形
     */
    private static final Pattern PATTERN = Pattern.compile("[A-Z0-9]+(-[A-Z0-9]+)*");

    /**
     * 形式に合うかを返す。
     *
     * @param value 品番
     * @return 形式に合えば true
     */
    static boolean matches(String value) {
        return value != null && value.length() <= MAX_LENGTH && PATTERN.matcher(value).matches();
    }
}
