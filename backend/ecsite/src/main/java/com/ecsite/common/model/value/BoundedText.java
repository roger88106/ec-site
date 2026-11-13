package com.ecsite.common.model.value;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 文字の項目の判定
 *
 * <p>長さは code point で数える（サロゲートペアの文字を 1 文字とし、画面の見た目の文字数と合わせるため）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BoundedText {

    /**
     * 文字の数を返す。
     *
     * @param text 文字列
     * @return code point の数
     */
    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * 必須の項目として正しいかを返す。
     *
     * <p>null・空・空白だけのどれでもなく、上限以内なら正しい。
     *
     * @param text      文字列。null のときは false を返す
     * @param maxLength 上限の文字の数
     * @return 正しければ true
     */
    public static boolean isFilledWithin(@Nullable String text, int maxLength) {
        return text != null && !text.isBlank() && length(text) <= maxLength;
    }

    /**
     * 空白を許す項目として正しいか（上限以内か）を返す。
     *
     * @param text      文字列。null のときは false を返す
     * @param maxLength 上限の文字の数
     * @return 正しければ true
     */
    public static boolean fitsWithin(@Nullable String text, int maxLength) {
        return text != null && length(text) <= maxLength;
    }
}
