package com.ecsite.common.message;

import com.ecsite.common.error.ErrorDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * メッセージヘルパー。<br />
 * メッセージのコードとパラメータから、画面にそのまま出せる文字列を組み立てる。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageHelper {

    /**
     * メッセージソース
     */
    private final MessageSource messageSource;

    /**
     * メッセージを取得する。
     *
     * @param message I18n メッセージ（パラメータなし）
     * @return メッセージ
     */
    public String getMessage(I18nMessage message) {
        return getMessageTemplate(message.code());
    }

    /**
     * エラー明細のメッセージを取得する。パラメータを埋め込む。
     *
     * @param errorDetail エラー明細
     * @return メッセージ
     */
    public String getMessage(ErrorDetail errorDetail) {
        ErrorMessage message = errorDetail.message();
        return formatMessage(getMessageTemplate(message.code()), message.params());
    }

    /**
     * キーの文を取り出し、値を名前で差し込む。<br />
     * 整数は 3 桁ごとにカンマで区切る（例：10,000）。
     *
     * @param code   メッセージのキー
     * @param params 差し込む値の名前 → 値
     * @return メッセージ
     */
    public String getMessage(String code, Map<String, ?> params) {
        Map<String, String> formatted = new LinkedHashMap<>();
        params.forEach((name, value) -> formatted.put(name, formatValue(value)));
        return formatMessage(getMessageTemplate(code), formatted);
    }

    /**
     * 差し込む値を文字にする。整数は 3 桁ごとにカンマで区切る。
     *
     * @param value 値
     * @return 文字
     */
    public static String formatValue(Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short) {
            return "%,d".formatted(((Number) value).longValue());
        }
        return value == null ? "" : value.toString();
    }

    // ============================== プライベートメソッド ==============================

    /**
     * メッセージをフォーマットする。
     *
     * @param template メッセージテンプレート
     * @param params   パラメータマップ
     * @return フォーマット済みメッセージ
     */
    private String formatMessage(String template, @Nullable Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return template;
        }
        // パラメータをテンプレートに置換
        for (Map.Entry<String, String> entry : params.entrySet()) {
            template = template.replace("{%s}".formatted(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return template;
    }

    /**
     * メッセージテンプレートを取得する。
     *
     * @param code メッセージコード
     * @return メッセージテンプレート。未設定の場合はコードをそのまま返す
     */
    private String getMessageTemplate(String code) {
        try {
            return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            log.error("メッセージ未設定。code={}", code);
            return code;
        }
    }
}
