package com.ecsite.common.model.value;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 文字の項目の判定（docs/rules/backend-coding.md 第 3.1 節）。<br />
 * 長さは code point で数える（サロゲートペアの文字を 1 文字とし、画面の見た目の文字数と合わせるため）。
 * 各ドメインは上限の定数とエラーの種類だけを持ち、判定はここに任せる。
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
     * 必須の項目として正しいか（空白だけでなく、上限以内か）を返す。
     *
     * @param text      文字列
     * @param maxLength 上限の文字の数
     * @return 正しければ true。null は false
     */
    public static boolean isFilledWithin(String text, int maxLength) {
        return text != null && !text.isBlank() && length(text) <= maxLength;
    }

    /**
     * 空白を許す項目として正しいか（上限以内か）を返す。
     *
     * @param text      文字列
     * @param maxLength 上限の文字の数
     * @return 正しければ true。null は false
     */
    public static boolean fitsWithin(String text, int maxLength) {
        return text != null && length(text) <= maxLength;
    }
}
