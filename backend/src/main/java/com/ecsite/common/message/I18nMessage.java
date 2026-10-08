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
     * @return {@link I18nMessage}
     */
    public static I18nMessage of(@NonNull String code) {
        return new I18nMessage(code, List.of());
    }

    /**
     * I18nメッセージを生成する
     *
     * @param prefix メッセージコードのプレフィックス
     * @param name メッセージコードの名前
     * @return {@link I18nMessage}
     */
    public static I18nMessage of(@NonNull String prefix, @NonNull String name) {
        return new I18nMessage(prefix + "." + name, List.of());
    }

    /**
     * I18nメッセージを生成する
     *
     * @param code メッセージコード
     * @param paramKeys メッセージパラメータリスト
     * @return {@link I18nMessage}
     */
    public static I18nMessage of(@NonNull String code, List<String> paramKeys) {
        if (paramKeys == null) {
            paramKeys = List.of();
        }
        return new I18nMessage(code, paramKeys);
    }



    /**
     * I18nメッセージを生成する
     *
     * @param prefix メッセージコードのプレフィックス
     * @param name メッセージコードの名前
     * @param paramKeys メッセージパラメータリスト
     * @return {@link I18nMessage}
     */
    public static I18nMessage of(@NonNull String prefix, @NonNull String name, List<String> paramKeys) {
        if (paramKeys == null) {
            paramKeys = List.of();
        }
        return new I18nMessage(prefix + "." + name, paramKeys);
    }
}
