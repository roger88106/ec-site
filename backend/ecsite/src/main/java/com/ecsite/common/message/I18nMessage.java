package com.ecsite.common.message;

import lombok.NonNull;

import java.util.List;

/**
 * I18n メッセージ
 *
 * @param code メッセージコード
 * @param paramKeys メッセージパラメータキー
 */
public record I18nMessage(@NonNull String code, @NonNull List<String> paramKeys) {

    /**
     * I18nメッセージを生成する
     *
     * @param code メッセージコード
     * @param paramKeys メッセージパラメータリスト
     * @return {@link I18nMessage}
     */
    public static I18nMessage of(@NonNull String code, @NonNull List<String> paramKeys) {
        return new I18nMessage(code, List.copyOf(paramKeys));
    }
}
