package com.ecsite.common.error;

import com.ecsite.common.message.I18nMessage;

import java.util.List;
import java.util.Locale;

/**
 * エラーコードプロバイダー。<br />
 * 各モジュールのエラーの定義（enum）が実装する。メッセージのキーは、モジュールの名前と定数の名前から決まる
 * （docs/rules/backend-coding.md 第 3.2 節）。
 */
public interface ErrorCodeProvider {

    /**
     * エラーコード取得
     *
     * @return {@link ErrorCode}
     */
    ErrorCode getCode();

    /**
     * エラーの定義の定数から、エラーコードを作る。<br />
     * メッセージのキーは {@code ecsite.<モジュール>.<定数の名前の小文字>}。
     *
     * @param module    モジュールの名前（モジュールでない共通のものは common）
     * @param name      定数の名前
     * @param type      エラーの種類
     * @param paramKeys 文に差し込む値の名前（文の {@code {名前}} と同じ順で、例外を投げるときに値を渡す）
     * @return エラーコード
     */
    static ErrorCode errorCode(String module, String name, ErrorType type, String... paramKeys) {
        var key = "ecsite.%s.%s".formatted(module, name.toLowerCase(Locale.ROOT));
        return ErrorCode.of(name, type, I18nMessage.of(key, List.of(paramKeys)));
    }
}
