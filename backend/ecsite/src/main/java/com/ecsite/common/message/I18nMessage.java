package com.ecsite.common.message;

import com.google.errorprone.annotations.Immutable;
import lombok.NonNull;

import java.util.List;

/**
 * I18n メッセージ
 *
 * @param code メッセージコード
 * @param paramKeys メッセージパラメータキー
 */
// コンストラクターで List.copyOf にするので変更できない。Error Prone は List の型だけを見て判断するので抑える
@SuppressWarnings("Immutable")
@Immutable
public record I18nMessage(@NonNull String code, @NonNull List<String> paramKeys) {

    /**
     * 正規のコンストラクター。paramKeys を変更できない一覧にする
     */
    public I18nMessage {
        paramKeys = List.copyOf(paramKeys);
    }

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
